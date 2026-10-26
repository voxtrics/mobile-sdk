import Foundation
import os

/// os.Logger, subsystem `io.voxtrics.sdk`, only while VoxtricsConfig.logging is on - the SDK says nothing in an app's
/// log otherwise.
struct Log {
    private static let logger = Logger(subsystem: "io.voxtrics.sdk", category: "survey")
    let enabled: Bool

    func debug(_ message: @autoclosure () -> String) {
        guard enabled else { return }
        let text = message()
        Log.logger.debug("\(text, privacy: .public)")
    }

    func warn(_ message: @autoclosure () -> String) {
        guard enabled else { return }
        let text = message()
        Log.logger.warning("\(text, privacy: .public)")
    }
}
