package io.voxtrics.sdk

import android.content.Context
import android.os.Looper
import androidx.fragment.app.FragmentActivity
import io.voxtrics.sdk.internal.SurveySession

/**
 * Shows Voxtrics in-app surveys: a bottom sheet over the app's screen, opened when the app asks.
 *
 * ```
 * Voxtrics.showSurvey(activity, surveyId = "…", options = SurveyOptions(screen = "checkout"), listener = …)
 * ```
 *
 * Every call is made on the main thread.
 */
public object Voxtrics {
    public const val SDK_VERSION: String = BuildConfig.SDK_VERSION

    @Volatile
    internal var config: VoxtricsConfig = VoxtricsConfig()
        private set

    /**
     * Optional: another environment, or logging. The defaults are production's. The context is not read yet; it is in
     * the signature so a later version can read the app's own settings without breaking this call.
     */
    @JvmStatic
    @JvmOverloads
    @Suppress("UNUSED_PARAMETER")
    public fun configure(context: Context, config: VoxtricsConfig = VoxtricsConfig()) {
        this.config = config
    }

    /**
     * Opens the survey [surveyId] over [activity]. It loads out of sight first, and the sheet appears only once the
     * survey is ready to answer - a survey that cannot be shown ([SurveyErrorCode]) reports [SurveyListener.onError]
     * and shows nothing. One survey at a time: a call while another is open reports [SurveyErrorCode.UNKNOWN].
     */
    @JvmStatic
    @JvmOverloads
    public fun showSurvey(
        activity: FragmentActivity,
        surveyId: String,
        options: SurveyOptions = SurveyOptions(),
        listener: SurveyListener? = null,
    ) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Voxtrics.showSurvey must be called on the main thread" }
        SurveySession.start(activity, config, surveyId, options, listener ?: object : SurveyListener {})
    }
}
