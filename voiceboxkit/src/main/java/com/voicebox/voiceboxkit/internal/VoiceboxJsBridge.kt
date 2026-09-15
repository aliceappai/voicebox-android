package com.voicebox.voiceboxkit

import android.os.Handler
import android.webkit.JavascriptInterface
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

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
    /** Page-initiated dismiss — the floating card's tap outside the card (no × shown). */
    private val onDismissRequest: () -> Unit = {},
) {
    @JavascriptInterface
    fun onMessage(handlerName: String, payload: String) {
        mainHandler.post {
            when (handlerName) {
                "voiceboxEvent" -> handleEvent(payload)
                "voiceboxBgColor" -> onBgColor(payload)
                "voiceboxContentHeight" -> payload.toFloatOrNull()?.let { onContentHeight(it) }
                "voiceboxSession" -> parseSessionPayload(payload)?.let { reportSessionId(it) }
            }
        }
    }

    // MARK: - Anonymous session id

    /** The last id actually handed to a listener by THIS presentation. */
    private var deliveredSessionId: String? = null

    /**
     * Hand [rawId] to the listener, once per id.
     *
     * Marked delivered only when a listener actually received it. Marking it on arrival
     * would let an id that turned up before a listener existed be swallowed, and every later
     * report of the same id (on submit, from the poll) would then be skipped as a duplicate —
     * the host would never learn it. That is exactly the bug VoiceboxKit iOS fixed in 1.1.4.
     *
     * Must be called on the main thread.
     *
     * @return `true` when the listener received the id.
     */
    internal fun reportSessionId(rawId: String): Boolean {
        val sessionId = rawId.trim()
        if (sessionId.isEmpty()) return false
        if (sessionId == deliveredSessionId) return false

        val listener = voiceboxView.listener ?: return false

        deliveredSessionId = sessionId
        listener.onAnonymousSessionId(voiceboxView, sessionId)
        return true
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
        if (type == "dismiss") {
            onDismissRequest()
            return
        }
        val listener = voiceboxView.listener ?: return
        when (type) {
            "recordingComplete" -> listener.onRecordingComplete(voiceboxView)
            "messageSubmitted" -> listener.onMessageSubmitted(voiceboxView)
        }
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
                        // Returns whether native actually received the message, so a caller
                        // that must not lose a value (SESSION_CAPTURE) can retry until it did.
                        postMessage:function(msg){
                            try{
                                if(window.VoiceboxBridge){
                                    window.VoiceboxBridge.onMessage(
                                        name,
                                        typeof msg==='string'?msg:JSON.stringify(msg)
                                    );
                                    return true;
                                }
                            }catch(e){}
                            return false;
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
                    // The recorder may write its anonymous session id around save/submit,
                    // so re-check it on every recorder event. Guarded: SESSION_CAPTURE may
                    // not have run (injection order, or no DOCUMENT_START_SCRIPT support).
                    try{
                        if(window.__voiceboxPostSessionId) window.__voiceboxPostSessionId();
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
         * ⚠️ Cross-repo contract with vbx-web (`app/javascript/profiles_session.js`,
         * `PROFILES_SESSION_STORAGE_KEY`) and with VoiceboxKit iOS. A rename on either side
         * silently stops capture with no compile error anywhere — a test pins this literal.
         */
        const val PROFILES_SESSION_STORAGE_KEY = "vbx_profiles_session_id"

        /**
         * Reports the recorder's anonymous session id to native via the `voiceboxSession`
         * handler. Injected at document-start after [WEBKIT_POLYFILL].
         *
         * The id is written lazily by the page, so one read is not enough. It tries:
         * 1. immediately, and again at DOMContentLoaded and `load`;
         * 2. on every recorder event ([EVENT_OBSERVER] calls `__voiceboxPostSessionId`);
         * 3. once a second for up to two minutes, stopping as soon as an id is delivered.
         *
         * An id counts as reported only once the bridge actually RECEIVED it (the polyfill's
         * `postMessage` returns `true`), never merely because it was read — otherwise a read
         * that could not be delivered would stop the poll and mark every later report a
         * duplicate. Top frame only: the recorder runs there, and an iframe has a different
         * origin's storage.
         */
        val SESSION_CAPTURE = """
            (function(){
                if(window.top !== window) return;
                if(window.__voiceboxPostSessionId) return;

                var KEY = '$PROFILES_SESSION_STORAGE_KEY';
                var delivered = null;

                function readSessionId(){
                    try{
                        return window.localStorage.getItem(KEY)
                            || window.sessionStorage.getItem(KEY);
                    }catch(e){ return null; }
                }

                function post(){
                    var id = readSessionId();
                    if(!id) return false;
                    if(id === delivered) return true;

                    var ok = false;
                    try{
                        var h = window.webkit
                            && window.webkit.messageHandlers
                            && window.webkit.messageHandlers.voiceboxSession;
                        if(h) ok = h.postMessage({ sessionId: id }) === true;
                    }catch(e){}

                    if(ok) delivered = id;
                    return ok;
                }

                window.__voiceboxPostSessionId = post;

                post();
                document.addEventListener('DOMContentLoaded', post);
                window.addEventListener('load', post);

                var tries = 0;
                var timer = setInterval(function(){
                    tries += 1;
                    if(post() || tries >= 120) clearInterval(timer);
                }, 1000);
            })();
        """.trimIndent()

        /**
         * Read the id straight out of storage, evaluated natively once a page finishes.
         *
         * A second path that does not depend on [SESSION_CAPTURE] at all: it still works on a
         * WebView without `DOCUMENT_START_SCRIPT` support (where no injected script runs), and
         * it bypasses the script's own "already delivered" state, so native dedupe is the
         * only gate. Returns the id, or `null`.
         */
        val SESSION_READ_SNIPPET = """
            (function(){
                try{
                    return window.localStorage.getItem('$PROFILES_SESSION_STORAGE_KEY')
                        || window.sessionStorage.getItem('$PROFILES_SESSION_STORAGE_KEY');
                }catch(e){ return null; }
            })();
        """.trimIndent()

        /** `{"sessionId": "..."}` → the id, or `null` when there is no usable id. */
        internal fun parseSessionPayload(payload: String?): String? {
            if (payload.isNullOrBlank()) return null
            return try {
                JSONObject(payload).optString("sessionId").trim().takeIf { it.isNotEmpty() }
            } catch (_: JSONException) {
                null
            }
        }

        /**
         * `evaluateJavascript` hands back a JSON literal: `"null"` for no value, `"\"abc\""`
         * for a string. Returns the unquoted, non-blank string, or `null`.
         */
        internal fun parseEvaluatedString(result: String?): String? {
            if (result.isNullOrBlank()) return null
            return try {
                (JSONTokener(result).nextValue() as? String)?.trim()?.takeIf { it.isNotEmpty() }
            } catch (_: JSONException) {
                null
            }
        }
    }
}
