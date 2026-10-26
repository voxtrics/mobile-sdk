import Foundation

/// The page a survey opens on, `{baseURL}/m/{surveyId}?…`.
enum SurveyURL {
    // A survey id is a UUID. Anything else is not one of ours, and must not become part of a path.
    private static let surveyIdPattern = #"^[A-Za-z0-9-]{1,64}$"#

    static func isValidSurveyId(_ surveyId: String) -> Bool {
        surveyId.range(of: surveyIdPattern, options: .regularExpression) != nil
    }

    /// Blank values are left out, so the page reads them as not sent. Nil for a base URL that is not http(s).
    static func build(baseURL: URL, surveyId: String, query: [(String, String?)]) -> URL? {
        guard isValidSurveyId(surveyId), origin(of: baseURL) != nil,
              var components = URLComponents(url: baseURL, resolvingAgainstBaseURL: false)
        else { return nil }
        let basePath = components.path.hasSuffix("/") ? String(components.path.dropLast()) : components.path
        components.path = "\(basePath)/m/\(surveyId)"
        let items = query.compactMap { name, value -> URLQueryItem? in
            guard let value = value?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else { return nil }
            return URLQueryItem(name: name, value: value)
        }
        components.queryItems = items.isEmpty ? nil : items
        // URLComponents leaves "+" as it is, and the page's URLSearchParams reads a bare "+" as a space.
        components.percentEncodedQuery = components.percentEncodedQuery?.replacingOccurrences(of: "+", with: "%2B")
        return components.url
    }

    /// Scheme, host and port - what the bridge accepts messages from, and navigation may stay on.
    struct Origin: Equatable {
        let scheme: String
        let host: String
        let port: Int
    }

    static func origin(of url: URL) -> Origin? {
        guard let scheme = url.scheme?.lowercased(), scheme == "http" || scheme == "https",
              let host = url.host?.lowercased(), !host.isEmpty
        else { return nil }
        return Origin(scheme: scheme, host: host, port: url.port ?? (scheme == "https" ? 443 : 80))
    }
}
