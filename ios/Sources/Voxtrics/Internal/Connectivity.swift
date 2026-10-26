import Foundation
import Network

/// Whether a connection is up - not whether it reaches us, which the page finds out. NWPathMonitor answers
/// asynchronously, so until its first answer the device counts as online and a failed load reports OFFLINE instead.
final class Connectivity: @unchecked Sendable {
    static let shared = Connectivity()

    private let monitor = NWPathMonitor()
    private let lock = NSLock()
    private var satisfied: Bool?

    private init() {
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self else { return }
            self.lock.lock()
            self.satisfied = path.status == .satisfied
            self.lock.unlock()
        }
        monitor.start(queue: DispatchQueue(label: "io.voxtrics.sdk.connectivity"))
    }

    var isOffline: Bool {
        lock.lock()
        defer { lock.unlock() }
        return satisfied == false
    }
}
