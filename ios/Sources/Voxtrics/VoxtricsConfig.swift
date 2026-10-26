import Foundation

/// How the SDK reaches Voxtrics. The defaults are production's; an app changes them only to try surveys against
/// another environment (a local stack on the simulator: `http://localhost:3000`).
public struct VoxtricsConfig: Sendable {
    public static let defaultBaseURL = URL(string: "https://feedback.voxtrics.io")!

    /// The admin app's origin - the survey opens on `{baseURL}/m/{surveyId}`.
    public var baseURL: URL
    /// Logs the bridge's traffic (os.Logger, subsystem `io.voxtrics.sdk`) and makes the WebView inspectable in
    /// Safari's Web Inspector.
    public var logging: Bool

    public init(baseURL: URL = VoxtricsConfig.defaultBaseURL, logging: Bool = false) {
        self.baseURL = baseURL
        self.logging = logging
    }
}
