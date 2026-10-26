import Foundation

/// The install's session id: one UUID, made the first time and kept. The backend matches it alone - not the IP, which
/// changes as a phone moves between networks - against a survey's earlier answers, which is how a survey that takes
/// one answer per person knows this install already gave its own. Kept in the SDK's own UserDefaults suite, not the
/// WebView's storage, which is the app's to clear - and the one reason PrivacyInfo.xcprivacy declares UserDefaults
/// (CA92.1: only the app itself reads it).
enum SessionStore {
    private static let suiteName = "io.voxtrics.sdk"
    private static let key = "sessionId"

    static func sessionId(defaults: UserDefaults? = UserDefaults(suiteName: suiteName)) -> String {
        let store = defaults ?? .standard
        if let existing = store.string(forKey: key) { return existing }
        let created = UUID().uuidString.lowercased()
        store.set(created, forKey: key)
        return created
    }
}
