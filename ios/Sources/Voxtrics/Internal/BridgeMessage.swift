import Foundation

/// One message from the survey page, `{"v":1,"type":…,"payload":{…}}`. A type this version does not know is `.unknown`
/// and ignored, as each side does with the other's unknowns - so a newer page still works with an older SDK.
enum BridgeMessage: Equatable {
    case ready(surveyId: String, locale: String)
    case resize(height: Double)
    case submitted(mainEvaluation: Double?)
    case closed(CloseReason)
    case error(SurveyErrorCode, message: String)
    case openURL(URL)
    case unknown(type: String)

    /// The page posts a JSON string. Nil for anything that is not a bridge message at all.
    static func parse(_ body: Any) -> BridgeMessage? {
        guard let json = body as? String,
              let data = json.data(using: .utf8),
              let message = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let type = message["type"] as? String, !type.isEmpty
        else { return nil }
        let payload = message["payload"] as? [String: Any] ?? [:]
        switch type {
        case "ready":
            return .ready(surveyId: payload["surveyId"] as? String ?? "", locale: payload["locale"] as? String ?? "")
        case "resize":
            guard let height = number(payload["height"]), height >= 0 else { return nil }
            return .resize(height: height)
        case "submitted":
            return .submitted(mainEvaluation: number(payload["mainEvaluation"]))
        case "closed":
            return .closed(payload["reason"] as? String == "completed" ? .completed : .user)
        case "error":
            // A code a later page sends that this version does not know is still an error.
            let code = (payload["code"] as? String).flatMap(SurveyErrorCode.init(rawValue:)) ?? .unknown
            return .error(code, message: payload["message"] as? String ?? "")
        case "openUrl":
            guard let string = payload["url"] as? String, let url = URL(string: string) else { return nil }
            return .openURL(url)
        default:
            return .unknown(type: type)
        }
    }

    // JSONSerialization hands numbers over as NSNumber, booleans included - a boolean is no height and no score.
    private static func number(_ value: Any?) -> Double? {
        guard let number = value as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID() else { return nil }
        let double = number.doubleValue
        return double.isFinite ? double : nil
    }
}
