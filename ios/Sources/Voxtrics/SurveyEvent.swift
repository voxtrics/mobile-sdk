import Foundation

/// What happens to a survey after `Voxtrics.showSurvey`, on the main thread.
///
/// A survey ends in exactly one of `.closed` and `.error`-before-`.shown`: either it was on screen and closed, or it
/// never opened. `.error` can also come after `.shown` (the response was refused as a second one), and the sheet then
/// stays until it is closed.
public enum SurveyEvent: Sendable, Equatable {
    /// The sheet is on screen.
    case shown
    /// The response was saved.
    case submitted(SurveyResult)
    /// The sheet was closed - by the person, or after the thank-you screen.
    case closed(CloseReason)
    case error(SurveyError)
}

public struct SurveyResult: Sendable, Equatable {
    public let surveyId: String
    /// The response's score as the Voxtrics admin shows it: the main evaluation question's answer, on the 1-5 scale
    /// when the question has another one. Nil when the survey has no such question or it was skipped. An app can, for
    /// one, ask for a store review after a 4 or a 5.
    public let mainEvaluation: Double?

    public init(surveyId: String, mainEvaluation: Double?) {
        self.surveyId = surveyId
        self.mainEvaluation = mainEvaluation
    }
}

public enum CloseReason: String, Sendable {
    /// Closed before the response was sent: the close control, a swipe down, or a tap outside the sheet. Also when the
    /// app itself takes down the screen the sheet was shown over.
    case user
    /// Closed after the thank-you screen.
    case completed
}

public struct SurveyError: Error, Sendable, Equatable {
    public let code: SurveyErrorCode
    public let message: String

    public init(code: SurveyErrorCode, message: String) {
        self.code = code
        self.message = message
    }
}

/// The codes both SDKs share.
public enum SurveyErrorCode: String, Sendable {
    /// No such survey, or it is not published, disabled, or its company's plan no longer includes the mobile SDK.
    case surveyNotFound = "SURVEY_NOT_FOUND"
    /// The survey is not an in-app one.
    case notInAppSurvey = "NOT_IN_APP_SURVEY"
    /// This install already answered it, and the survey takes one answer per person.
    case alreadySubmitted = "ALREADY_SUBMITTED"
    /// No connection. Nothing was shown.
    case offline = "OFFLINE"
    /// The survey did not open within 10 seconds.
    case timeout = "TIMEOUT"
    case unknown = "UNKNOWN"
}
