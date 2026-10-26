package io.voxtrics.sdk.internal

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.MutableContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.voxtrics.sdk.BuildConfig
import io.voxtrics.sdk.CloseReason
import io.voxtrics.sdk.SurveyError
import io.voxtrics.sdk.SurveyErrorCode
import io.voxtrics.sdk.SurveyListener
import io.voxtrics.sdk.SurveyOptions
import io.voxtrics.sdk.SurveyResult
import io.voxtrics.sdk.Theme
import io.voxtrics.sdk.VoxtricsConfig
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One survey, from showSurvey to its end: the page loads in a WebView parked out of sight over the activity, and only
 * the page's `ready` - sent after its first `resize`, so the height is known - moves that WebView into the sheet. A
 * survey that cannot be shown (missing, already answered, offline, no `ready` in 10 s) ends with onError and nothing
 * on screen. A configuration change (a rotation, dark mode, a new window size) replaces the activity, and the survey
 * moves over to the new one. Every message and callback is on the main thread.
 */
internal class SurveySession private constructor(
    activity: FragmentActivity,
    private val config: VoxtricsConfig,
    private val origin: String,
    val surveyId: String,
    private val listener: SurveyListener,
) {
    private enum class State { LOADING, SHOWN, ENDED }

    private val log = Logger(config.logging)
    private val handler = Handler(Looper.getMainLooper())
    private var state = State.LOADING
    private var sheet: SurveySheetFragment? = null

    /** The activity the survey is over now - the one it was opened from, or what replaced it. */
    private var activity: FragmentActivity = activity

    // Set from the old activity's destruction by a configuration change until its replacement starts.
    private var replacing: Replacement? = null
    private var readyWhileReplacing = false

    // The WebView outlives a configuration change; this lets it move to the new activity instead of holding the
    // destroyed one, while still having an activity to show its own dialogs (a select's options) from.
    private val webViewContext = MutableContextWrapper(activity)

    private val density: Float get() = activity.resources.displayMetrics.density

    /** The page's content height in device pixels, from its last `resize`. */
    var contentHeight: Int = 0
        private set

    /** At most 90% of the screen as it is now (a rotation changes it); a taller survey scrolls inside the sheet. */
    val maxHeight: Int get() = (activity.resources.displayMetrics.heightPixels * MAX_HEIGHT_SHARE).roundToInt()

    val isShown: Boolean get() = state == State.SHOWN

    /** The page's theme as asked for at load, which the system bars over it follow. */
    var isDarkPage: Boolean = false
        private set

    @SuppressLint("SetJavaScriptEnabled")
    val webView: WebView = WebView(webViewContext).apply {
        // Transparent until the page paints its theme's background, so no white flashes in a dark app.
        setBackgroundColor(Color.TRANSPARENT)
        overScrollMode = View.OVER_SCROLL_NEVER
        settings.javaScriptEnabled = true
        // The widget keeps a little per-visit state in storage.
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportMultipleWindows(false)
        // Android's own focus highlight would grey out the whole survey whenever a hardware key moves focus; the page
        // draws its own focus rings.
        if (Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
        webViewClient = Client()
        webChromeClient = ChromeClient()
    }

    private val timeout = Runnable { fail(SurveyErrorCode.TIMEOUT, "The survey did not open within ${READY_TIMEOUT_MS / 1000} seconds") }

    // The activity going away ends the survey - unless a configuration change is replacing it: then the survey waits
    // for the new one.
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            if (state != State.ENDED && activity.isChangingConfigurations) {
                awaitReplacement()
                return
            }
            when (state) {
                State.LOADING -> fail(SurveyErrorCode.UNKNOWN, "The screen the survey was opened from was closed")
                State.SHOWN -> close(CloseReason.USER)
                State.ENDED -> Unit
            }
        }
    }

    private fun load(options: SurveyOptions) {
        installBridge()
        activity.lifecycle.addObserver(lifecycleObserver)
        park()
        handler.postDelayed(timeout, READY_TIMEOUT_MS)
        val url = pageUrl(options)
        log.debug("Opening $url")
        webView.loadUrl(url)
    }

    private fun installBridge() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            // Messages only from the survey's own origin, top frame - whatever else might ever load in the WebView.
            WebViewCompat.addWebMessageListener(webView, BRIDGE_NAME, setOf(origin)) { _, message, _, isMainFrame, _ ->
                if (isMainFrame) message.data?.let(::onMessage)
            }
        } else {
            @SuppressLint("JavascriptInterface")
            val bridge = JavascriptBridge { json -> handler.post { onMessage(json) } }
            webView.addJavascriptInterface(bridge, BRIDGE_NAME)
        }
    }

    // Laid out at the sheet's width and fully visible to Chromium - a hidden or zero-sized WebView renders nothing, and
    // the page measures nothing - but transparent, below the screen's edge, and out of reach of touch and TalkBack.
    private fun park() {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        webView.alpha = 0f
        webView.translationY = (activity.resources.displayMetrics.heightPixels + maxHeight).toFloat()
        webView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        webView.isFocusable = false
        root.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight))
    }

    private fun pageUrl(options: SurveyOptions): String {
        val app = AppInfo.of(activity)
        return SurveyUrl.build(
            config.baseUrl,
            surveyId,
            listOf(
                "platform" to "ANDROID",
                "appId" to app.appId,
                "appVersion" to app.appVersion,
                "appBuild" to app.appBuild,
                "osVersion" to app.osVersion,
                "deviceModel" to app.deviceModel,
                "sdkVersion" to BuildConfig.SDK_VERSION,
                "screen" to options.screen,
                "theme" to themeOf(options.theme).also { isDarkPage = it == "dark" },
                "locale" to (options.locale ?: Locale.getDefault().toLanguageTag()),
                "sessionId" to SessionStore.sessionId(activity),
            ),
        )
    }

    // SYSTEM is the app's own night mode - the activity's configuration carries AppCompat's override too.
    private fun themeOf(theme: Theme): String = when (theme) {
        Theme.LIGHT -> "light"
        Theme.DARK -> "dark"
        Theme.SYSTEM -> {
            val night = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (night == Configuration.UI_MODE_NIGHT_YES) "dark" else "light"
        }
    }

    private fun onMessage(json: String) {
        if (state == State.ENDED) return
        val message = BridgeMessage.parse(json)
        log.debug("Bridge: $json")
        when (message) {
            null, is BridgeMessage.Unknown -> Unit
            is BridgeMessage.Resize -> {
                contentHeight = (message.height * density).roundToInt()
                sheet?.setContentHeight(sheetHeight())
            }
            is BridgeMessage.Ready -> if (state == State.LOADING) {
                if (replacing != null) readyWhileReplacing = true else show()
            }
            is BridgeMessage.Submitted -> listener.onSubmitted(SurveyResult(surveyId, message.mainEvaluation))
            is BridgeMessage.Closed -> close(message.reason)
            is BridgeMessage.Error ->
                // Before the sheet: the survey cannot be shown. After it (a response refused as a second one): the
                // page shows its thank-you screen all the same, and the sheet stays.
                if (state == State.LOADING) fail(message.code, message.message)
                else listener.onError(SurveyError(message.code, message.message))
            is BridgeMessage.OpenUrl -> openExternally(message.url)
        }
    }

    fun sheetHeight(): Int = min(contentHeight, maxHeight)

    private fun show() {
        handler.removeCallbacks(timeout)
        val fragments = activity.supportFragmentManager
        if (activity.isFinishing || activity.isDestroyed || fragments.isStateSaved) {
            fail(SurveyErrorCode.UNKNOWN, "The screen the survey was opened from is not showing")
            return
        }
        state = State.SHOWN
        detachWebView()
        webView.alpha = 1f
        webView.translationY = 0f
        webView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        // Both: setting isFocusable false also cleared focusableInTouchMode, and without it a tap on a text field
        // gives the WebView no focus - no soft keyboard opens, and a hardware key lands on the page's first control.
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        SurveySheetFragment().show(fragments, SurveySheetFragment.TAG)
        listener.onShown()
    }

    fun onSheetCreated(fragment: SurveySheetFragment) {
        sheet = fragment
    }

    /** A sheet's view going away - dismissed, or taken down with an activity a configuration change replaces. */
    fun onSheetDestroyed(fragment: SurveySheetFragment) {
        if (sheet === fragment) sheet = null
    }

    private fun awaitReplacement() {
        activity.lifecycle.removeObserver(lifecycleObserver)
        detachWebView()
        replacing = Replacement(activity.javaClass, activity.taskId)
        activity.application.registerActivityLifecycleCallbacks(replacementCallbacks)
        handler.postDelayed(replacementTimeout, REPLACEMENT_TIMEOUT_MS)
    }

    /**
     * The survey moves to [newActivity], the one that replaced its activity after a configuration change. Called by
     * the sheet the system recreates over it and, for a survey still loading, when that activity starts - whichever
     * comes first.
     */
    fun adopt(newActivity: FragmentActivity) {
        if (state == State.ENDED || replacing == null || newActivity === activity) return
        stopAwaitingReplacement()
        activity = newActivity
        webViewContext.baseContext = newActivity
        newActivity.lifecycle.addObserver(lifecycleObserver)
        when {
            state == State.SHOWN ->
                // The system brings the old sheet back over the new activity; should it not, a new one takes over.
                if (newActivity.supportFragmentManager.findFragmentByTag(SurveySheetFragment.TAG) == null) {
                    SurveySheetFragment().show(newActivity.supportFragmentManager, SurveySheetFragment.TAG)
                }
            readyWhileReplacing -> show()
            else -> park()
        }
    }

    private fun stopAwaitingReplacement() {
        if (replacing == null) return
        replacing = null
        activity.application.unregisterActivityLifecycleCallbacks(replacementCallbacks)
        handler.removeCallbacks(replacementTimeout)
    }

    private fun detachWebView() {
        (webView.parent as? ViewGroup)?.removeView(webView)
    }

    /** Back, a tap outside, or the sheet going away by itself. */
    fun onSheetDismissed() {
        if (state == State.SHOWN) close(CloseReason.USER, dismissSheet = false)
    }

    private fun close(reason: CloseReason, dismissSheet: Boolean = true) {
        if (state == State.ENDED) return
        state = State.ENDED
        if (dismissSheet) sheet?.dismissAllowingStateLoss()
        release()
        listener.onClosed(reason)
    }

    private fun fail(code: SurveyErrorCode, message: String) {
        if (state == State.ENDED) return
        val wasShown = state == State.SHOWN
        state = State.ENDED
        log.warn("$code: $message")
        if (wasShown) sheet?.dismissAllowingStateLoss()
        release()
        listener.onError(SurveyError(code, message))
    }

    private fun release() {
        handler.removeCallbacks(timeout)
        stopAwaitingReplacement()
        activity.lifecycle.removeObserver(lifecycleObserver)
        if (active === this) active = null
        val hadSheet = sheet != null
        sheet = null
        webView.stopLoading()
        // A sheet being dismissed still shows the page while it slides away; the WebView goes once it is gone.
        handler.postDelayed({
            detachWebView()
            webView.destroy()
        }, if (hadSheet) SHEET_EXIT_MS else 0L)
    }

    private fun isOwnPage(url: Uri): Boolean =
        runCatching { SurveyUrl.originOf(url.toString()) }.getOrNull() == origin

    // Links in the survey open in the system's browser or app for them, never in the sheet's WebView.
    private fun openExternally(url: String) {
        val uri = Uri.parse(url)
        if (uri.scheme?.lowercase() !in EXTERNAL_SCHEMES) return
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            log.warn("Nothing on the device opens $url")
        }
    }

    private inner class Client : WebViewClient() {
        // The survey's own page only: anything else the page might navigate to opens outside.
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (request.isForMainFrame && isOwnPage(request.url)) return false
            openExternally(request.url.toString())
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            log.debug("Loading $url")
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame || state != State.LOADING) return
            val offline = error.errorCode in OFFLINE_ERRORS
            fail(if (offline) SurveyErrorCode.OFFLINE else SurveyErrorCode.UNKNOWN, error.description.toString())
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            if (!request.isForMainFrame || state != State.LOADING) return
            fail(SurveyErrorCode.UNKNOWN, "The survey page answered ${response.statusCode}")
        }

        // Without this a crashed or killed renderer takes the whole app down with it.
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            if (state == State.SHOWN) close(CloseReason.USER) else fail(SurveyErrorCode.UNKNOWN, "The survey page stopped")
            return true
        }
    }

    private class Replacement(val activityClass: Class<*>, val taskId: Int)

    // The replacement is the next activity of the same class to start in the same task.
    private val replacementCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(started: Activity) {
            val wanted = replacing ?: return
            if (started is FragmentActivity && started.javaClass == wanted.activityClass && started.taskId == wanted.taskId) {
                adopt(started)
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    // A configuration change whose new activity never starts: the app went away instead.
    private val replacementTimeout = Runnable {
        if (state == State.SHOWN) close(CloseReason.USER)
        else fail(SurveyErrorCode.UNKNOWN, "The screen the survey was opened from was closed")
    }

    private inner class ChromeClient : WebChromeClient() {
        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            log.debug("Console: ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
            return true
        }
    }

    companion object {
        const val READY_TIMEOUT_MS = 10_000L
        private const val REPLACEMENT_TIMEOUT_MS = 5_000L
        private const val SHEET_EXIT_MS = 400L
        private const val MAX_HEIGHT_SHARE = 0.9
        private const val BRIDGE_NAME = "VoxtricsAndroid"
        private val EXTERNAL_SCHEMES = setOf("http", "https", "mailto", "tel")
        private val OFFLINE_ERRORS = setOf(
            WebViewClient.ERROR_HOST_LOOKUP,
            WebViewClient.ERROR_CONNECT,
            WebViewClient.ERROR_TIMEOUT,
        )

        /** The one survey open now, which the sheet fragment attaches to. */
        var active: SurveySession? = null
            private set

        fun start(
            activity: FragmentActivity,
            config: VoxtricsConfig,
            surveyId: String,
            options: SurveyOptions,
            listener: SurveyListener,
        ) {
            val webViewMajor = WebViewVersion.major(activity)
            val error = when {
                active != null -> SurveyError(SurveyErrorCode.UNKNOWN, "Another survey is already open")
                !SurveyUrl.isValidSurveyId(surveyId) -> SurveyError(SurveyErrorCode.SURVEY_NOT_FOUND, "Not a survey id: $surveyId")
                webViewMajor != null && webViewMajor < WebViewVersion.MIN_MAJOR -> SurveyError(
                    SurveyErrorCode.WEBVIEW_OUTDATED,
                    "Android System WebView $webViewMajor is older than ${WebViewVersion.MIN_MAJOR}",
                )
                !Network.isOnline(activity) -> SurveyError(SurveyErrorCode.OFFLINE, "No connection")
                activity.isFinishing || activity.isDestroyed -> SurveyError(SurveyErrorCode.UNKNOWN, "The screen is closing")
                else -> null
            }
            if (error != null) {
                listener.onError(error)
                return
            }
            val origin = try {
                SurveyUrl.originOf(config.baseUrl)
            } catch (e: IllegalArgumentException) {
                listener.onError(SurveyError(SurveyErrorCode.UNKNOWN, "VoxtricsConfig.baseUrl is not an http(s) URL"))
                return
            }
            SurveySession(activity, config, origin, surveyId, listener).also { active = it }.load(options)
        }
    }
}
