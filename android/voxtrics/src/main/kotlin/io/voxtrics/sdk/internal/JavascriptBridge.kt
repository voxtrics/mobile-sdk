package io.voxtrics.sdk.internal

import android.webkit.JavascriptInterface

/**
 * `window.VoxtricsAndroid` on a WebView too old for WebViewCompat.addWebMessageListener (before WebView 82). Called on
 * a WebView thread, not the main one - the receiver posts. It has no origin check of its own, which is why navigation
 * never leaves the survey's origin (SurveySession's WebViewClient).
 */
internal class JavascriptBridge(private val receive: (String) -> Unit) {
    @JavascriptInterface
    fun postMessage(message: String) {
        receive(message)
    }
}
