package io.voxtrics.sdk

/**
 * What happens to a survey after [Voxtrics.showSurvey], on the main thread. Every method has a default, so an app
 * overrides only the ones it needs.
 *
 * A survey ends in exactly one of [onClosed] and [onError]-before-[onShown]: either it was on screen and closed, or it
 * never opened. [onError] can also come after [onShown] (the response was refused as a second one), and the sheet then
 * stays until it is closed.
 *
 * The listener is kept for the whole survey, across a configuration change that replaces the activity it was created
 * in - so it should not hold on to that activity: write to a ViewModel, or look the current screen up when called.
 */
public interface SurveyListener {
    /** The sheet is on screen. */
    public fun onShown() {}

    /** The response was saved. */
    public fun onSubmitted(result: SurveyResult) {}

    /** The sheet was closed - by the person, or after the thank-you screen. */
    public fun onClosed(reason: CloseReason) {}

    public fun onError(error: SurveyError) {}
}

/**
 * @property mainEvaluation The response's score as the Voxtrics admin shows it: the main evaluation question's answer,
 *   on the 1-5 scale when the question has another one. Null when the survey has no such question or it was skipped.
 *   An app can, for one, ask for a store review after a 4 or a 5.
 */
public data class SurveyResult(
    val surveyId: String,
    val mainEvaluation: Double?,
)

public enum class CloseReason {
    /**
     * Closed before the response was sent: the close control, Back, or a tap outside the sheet. Also when the activity
     * it was shown over goes away for good.
     */
    USER,

    /** Closed after the thank-you screen. */
    COMPLETED,
}

public data class SurveyError(
    val code: SurveyErrorCode,
    val message: String,
)

public enum class SurveyErrorCode {
    /** No such survey, or it is not published, disabled, or its company's plan no longer includes the mobile SDK. */
    SURVEY_NOT_FOUND,

    /** The survey is not an in-app one. */
    NOT_IN_APP_SURVEY,

    /** This install already answered it, and the survey takes one answer per person. */
    ALREADY_SUBMITTED,

    /** No connection. Nothing was shown. */
    OFFLINE,

    /** The survey's page did not load within 20 seconds, or the survey did not open within 10 seconds after it did. */
    TIMEOUT,

    /**
     * The device's Android System WebView is too old to talk to the survey page (before version 82, 2020). Nothing was
     * loaded; updating "Android System WebView" from the Play Store fixes it.
     */
    WEBVIEW_OUTDATED,

    UNKNOWN,
}
