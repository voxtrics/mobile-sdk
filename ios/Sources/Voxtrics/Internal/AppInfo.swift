import Foundation
#if canImport(UIKit)
import UIKit
#endif

/// What a response records about the app it was given in - none of it personal: the app's ids and versions, the OS
/// version and the device model.
struct AppInfo: Equatable {
    let appId: String
    let appVersion: String?
    let appBuild: String?
    let osVersion: String
    let deviceModel: String

    @MainActor
    static func current(bundle: Bundle = .main) -> AppInfo {
        AppInfo(
            appId: bundle.bundleIdentifier ?? "unknown",
            appVersion: bundle.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String,
            appBuild: bundle.object(forInfoDictionaryKey: "CFBundleVersion") as? String,
            osVersion: osVersion(),
            deviceModel: deviceModel()
        )
    }

    @MainActor
    private static func osVersion() -> String {
        #if canImport(UIKit)
        return UIDevice.current.systemVersion
        #else
        let version = ProcessInfo.processInfo.operatingSystemVersion
        return "\(version.majorVersion).\(version.minorVersion).\(version.patchVersion)"
        #endif
    }

    /// The hardware identifier ("iPhone15,2"), the simulated one on a simulator - UIDevice.model only says "iPhone".
    static func deviceModel(environment: [String: String] = ProcessInfo.processInfo.environment) -> String {
        if let simulated = environment["SIMULATOR_MODEL_IDENTIFIER"] { return simulated }
        var system = utsname()
        uname(&system)
        return withUnsafeBytes(of: &system.machine) { raw in
            String(decoding: raw.prefix(while: { $0 != 0 }), as: UTF8.self)
        }
    }
}
