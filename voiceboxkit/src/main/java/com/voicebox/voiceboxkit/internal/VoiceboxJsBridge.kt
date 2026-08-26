package com.voicebox.voiceboxkit

import android.os.Handler
import android.webkit.JavascriptInterface
import org.json.JSONException
import org.json.JSONObject

/**
 * Exposes an Android JavaScript interface as `window.VoiceboxBridge`.
 *
 * The polyfill ([WEBKIT_POLYFILL]) shims `window.webkit.messageHandlers.X.postMessage()`
 * so the Voicebox web app can run unchanged — exactly the same JS as on iOS.
 *
 * All @JavascriptInterface methods are called on the WebView's background thread;
 * [mainHandler] marshals callbacks to the main thread before touching UI.
 */
internal class VoiceboxJsBridge(
    private val voiceboxView: VoiceboxView,
    private val onBgColor: (String) -> Unit,
    private val onContentHeight: (Float) -> Unit,
    private val mainHandler: Handler,
) {
    @JavascriptInterface
    fun onMessage(handlerName: String, payload: String) {
        mainHandler.post {
            when (handlerName) {
                "voiceboxEvent" -> handleEvent(payload)
                "voiceboxBgColor" -> onBgColor(payload)
                "voiceboxContentHeight" -> payload.toFloatOrNull()?.let { onContentHeight(it) }
                "voiceboxSession" -> handleSession(payload)
            }
        }
    }

    private fun handleEvent(payload: String) {
        // Events arrive either as a plain string ("recordingComplete") — matching
        // the iOS WKScriptMessage body — or as a JSON object {"type":"..."}.
        // Accept both so the event source (observer vs. direct web-app post)
        // doesn't matter.
        val type = try {
            JSONObject(payload).optString("type").ifEmpty { payload }
        } catch (_: JSONException) {
            payload
        }.trim()

        VoiceboxLog.d("voiceboxEvent: $type")
        val listener = voiceboxView.listener ?: return
        when (type) {
            "recordingComplete" -> listener.onRecordingComplete(voiceboxView)
            "messageSubmitted" -> listener.onMessageSubmitted(voiceboxView)
        }
    }

    /**
     * Last id handed to the listener. The capture script posts at most once per value per
     * frame and runs in subframes too, so without this an embedded recorder reports twice.
     */
    private var lastReportedSessionId: String? = null

    /**
     * Forwards the recorder's anonymous session id, once per distinct value.
     *
     * See [SESSION_CAPTURE] for where it comes from and why it arrives late, repeatedly,
     * or not at all.
     */
    private fun handleSession(payload: String) {
        val sessionId = try {
            JSONObject(payload).optString("sessionId")
        } catch (_: JSONException) {
            ""
        }.trim()

        if (sessionId.isEmpty() || sessionId == lastReportedSessionId) return
        lastReportedSessionId = sessionId

        val listener = voiceboxView.listener
        VoiceboxLog.d(
            "session id $sessionId" +
                if (listener == null) ", but NO listener is set" else " -> listener"
        )
        listener?.onAnonymousSessionId(voiceboxView, sessionId)
    }

    companion object {
        /**
         * Polyfill injected at document-start via [WebViewCompat.addDocumentStartJavaScript].
         *
         * Translates `window.webkit.messageHandlers.X.postMessage(msg)` calls
         * (the iOS WKWebView API the Voicebox web app uses) into
         * `window.VoiceboxBridge.onMessage(handlerName, jsonPayload)` calls
         * on this @JavascriptInterface — so the Voicebox web app needs no
         * platform-specific branching.
         */
        val WEBKIT_POLYFILL = """
            (function(){
                if(!window.webkit)window.webkit={};
                if(!window.webkit.messageHandlers)window.webkit.messageHandlers={};
                ['voiceboxEvent','voiceboxBgColor','voiceboxContentHeight','voiceboxSession'].forEach(function(name){
                    window.webkit.messageHandlers[name]={
                        postMessage:function(msg){
                            try{
                                if(window.VoiceboxBridge){
                                    window.VoiceboxBridge.onMessage(
                                        name,
                                        typeof msg==='string'?msg:JSON.stringify(msg)
                                    );
                                }
                            }catch(e){}
                        }
                    };
                });
            })();
        """.trimIndent()

        /**
         * Observes the recorder UI and bridges its events to `voiceboxEvent`
         * messages, so [VoiceboxListener.onRecordingComplete] and
         * [VoiceboxListener.onMessageSubmitted] fire on Android exactly as the
         * iOS delegate callbacks do.
         *
         * Ported 1:1 from the iOS `eventScript` (`VoiceboxViewController`):
         * - `window.postMessage({type:'voicebox:recordingComplete'|'voicebox:messageSubmitted'})`
         *   (used when the recorder is embedded in an iframe), and
         * - direct clicks on the Save button (`#record-btn-send`) and the edit-form
         *   Send button (`[data-recorder--recorder-target="editSubmitButton"]`)
         *   (used when the recorder runs directly in the WebView).
         *
         * Injected at document-start *after* [WEBKIT_POLYFILL] so the
         * `voiceboxEvent` handler already exists; the handler is also looked up
         * lazily inside `post()` to be robust against injection ordering.
         */
        val EVENT_OBSERVER = """
            (function(){
                function post(name){
                    try{
                        var h = window.webkit
                            && window.webkit.messageHandlers
                            && window.webkit.messageHandlers.voiceboxEvent;
                        if(h) h.postMessage(name);
                    }catch(e){}
                    // The recorder writes its anonymous id around submit time, so its own
                    // events are the best moment to look. Defined by SESSION_CAPTURE;
                    // guarded because that script is injected after this one.
                    try{
                        if(window.__voiceboxPostSessionId) window.__voiceboxPostSessionId('event');
                    }catch(e){}
                }

                // postMessage events (recorder embedded in an iframe)
                window.addEventListener('message', function(event){
                    if(!event.data || !event.data.type) return;
                    if(event.data.type === 'voicebox:recordingComplete') post('recordingComplete');
                    if(event.data.type === 'voicebox:messageSubmitted') post('messageSubmitted');
                });

                // Direct button clicks (recorder runs directly in the WebView)
                var observed = { save:false, send:false };
                function attachListeners(){
                    var saveBtn = document.getElementById('record-btn-send');
                    if(saveBtn && !observed.save){
                        observed.save = true;
                        saveBtn.addEventListener('click', function(){ post('recordingComplete'); });
                    }
                    var sendBtn = document.querySelector('[data-recorder--recorder-target="editSubmitButton"]');
                    if(sendBtn && !observed.send){
                        observed.send = true;
                        sendBtn.addEventListener('click', function(){ post('messageSubmitted'); });
                    }
                }
                if(document.documentElement){
                    attachListeners();
                    new MutationObserver(function(){ attachListeners(); })
                        .observe(document.documentElement, { childList:true, subtree:true });
                }
            })();
        """.trimIndent()

        /**
         * The recorder's anonymous-session key in the page's own storage.
         *
         * CROSS-REPO CONTRACT: owned by vbx-web (`app/javascript/profiles_session.js`,
         * `PROFILES_SESSION_STORAGE_KEY`). Every message the recorder submits is stamped
         * with the id stored under it, and the backend claims those messages by that id. A
         * rename on the web side silently stops capture here — nothing fails to compile —
         * so the two have to move together. `SessionCaptureTest` pins the literal.
         */
        const val PROFILES_SESSION_STORAGE_KEY = "vbx_profiles_session_id"

        /**
         * Reports the recorder's anonymous session id to native.
         *
         * Messages recorded while signed out are attributed to this id and to no account.
         * A host hands it back at sign-in so the backend can claim them; without it they
         * stay anonymous permanently.
         *
         * It is read off the live page rather than derived, because vbx-web owns it and
         * nothing here can compute it. It is written LAZILY — a first-time visitor has none
         * until the recorder's own controller connects — so one read at load is not enough.
         * Three triggers, matching iOS `VoiceboxWebScripts.sessionUserScript`:
         * document-start (this script's own first call), the recorder's own complete/submit
         * events (via [EVENT_OBSERVER]), and a 1s poll that stops the moment an id exists,
         * capped at two minutes — the recorder's own hard limit.
         */
        val SESSION_CAPTURE = """
            (function(){
                var KEY = '$PROFILES_SESSION_STORAGE_KEY';
                var lastSessionId = null;

                function readSessionId(){
                    try{
                        return window.localStorage.getItem(KEY)
                            || window.sessionStorage.getItem(KEY);
                    }catch(e){ return null; }
                }

                // Returns whether an id exists, which is what ends the poll — an
                // already-posted value still counts, so a repeat read does not keep it running.
                function post(reason){
                    var id = readSessionId();
                    if(!id) return false;

                    if(id !== lastSessionId){
                        lastSessionId = id;
                        try{
                            var h = window.webkit
                                && window.webkit.messageHandlers
                                && window.webkit.messageHandlers.voiceboxSession;
                            if(h) h.postMessage({
                                reason: reason,
                                url: String(window.location.href),
                                sessionId: id
                            });
                        }catch(e){}
                    }
                    return true;
                }

                // Let EVENT_OBSERVER trigger a read the instant the recorder reports
                // something, without duplicating any of this.
                window.__voiceboxPostSessionId = post;

                if(!post('load')){
                    var tries = 0;
                    var timer = setInterval(function(){
                        tries += 1;
                        if(post('poll') || tries >= 120) clearInterval(timer);
                    }, 1000);
                }
            })();
        """.trimIndent()
    }
}