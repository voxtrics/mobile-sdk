#if canImport(UIKit)
import UIKit

/// Shows Voxtrics in-app surveys: a sheet over the app's screen, opened when the app asks.
///
/// ```swift
/// Voxtrics.showSurvey(id: "…", from: viewController, options: .init(screen: "checkout")) { event in … }
/// ```
///
/// Every call is made on the main thread, and every event arrives on it.
@MainActor
public enum Voxtrics {
    public static let sdkVersion = "0.1.1"

    static private(set) var config = VoxtricsConfig()

    /// Optional: another environment, or logging. The defaults are production's.
    public static func configure(_ config: VoxtricsConfig = VoxtricsConfig()) {
        self.config = config
        _ = Connectivity.shared
    }

    /// Opens the survey `id` over `viewController` (or whatever it is presenting). It loads out of sight first, and the
    /// sheet appears only once the survey is ready to answer - a survey that cannot be shown reports `.error` and shows
    /// nothing. One survey at a time: a call while another is open reports `.error(.unknown)`.
    public static func showSurvey(
        id: String,
        from viewController: UIViewController,
        options: SurveyOptions = SurveyOptions(),
        onEvent: @escaping @MainActor (SurveyEvent) -> Void = { _ in }
    ) {
        SurveySession.start(presenter: viewController, config: config, surveyId: id, options: options, onEvent: onEvent)
    }
}
#endif
