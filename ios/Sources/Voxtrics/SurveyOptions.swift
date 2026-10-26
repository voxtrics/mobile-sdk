import Foundation

/// How one survey is shown.
public struct SurveyOptions: Sendable {
    /// A name for the screen the survey is opened from ("checkout"). It is stored with the response, and the response
    /// links to `app://{bundleId}/{screen}`.
    public var screen: String?
    /// Light or dark. `.system` follows the presenting screen's own interface style.
    public var theme: Theme
    /// A language code ("de"). Unset is the device's language; a survey without that language opens in its first one.
    public var locale: String?

    public init(screen: String? = nil, theme: Theme = .system, locale: String? = nil) {
        self.screen = screen
        self.theme = theme
        self.locale = locale
    }
}

public enum Theme: Sendable {
    case system, light, dark
}
