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
    private val onDismissRequested: () -> Unit,
    private val mainHandler: Handler,
) {
    @JavascriptInterface
    fun onMessage(handlerName: String, payload: String) {
        mainHandler.post {
            when (handlerName) {
                "voiceboxEvent" -> handleEvent(payload)
                "voiceboxBgColor" -> onBgColor(payload)
                "voiceboxContentHeight" -> payload.toFloatOrNull()?.let { onContentHeight(it) }
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

        // Dismiss is handled before the listener guard: it's a presentation command from the
        // injected tap-outside handler, not a business callback, so it must still work when
        // the caller attached no listener.
        if (type == VoiceboxPageChrome.DISMISS_EVENT) {
            onDismissRequested()
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
                ['voiceboxEvent','voiceboxBgColor','voiceboxContentHeight'].forEach(function(name){
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
    }
}
