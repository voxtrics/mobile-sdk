import XCTest
@testable import Voxtrics

final class AppInfoTests: XCTestCase {
    func testASimulatorReportsTheModelItSimulates() {
        XCTAssertEqual(AppInfo.deviceModel(environment: ["SIMULATOR_MODEL_IDENTIFIER": "iPhone15,2"]), "iPhone15,2")
        XCTAssertFalse(AppInfo.deviceModel(environment: [:]).isEmpty)
    }

    func testTheSessionIdIsMadeOnceAndKept() throws {
        let suite = "io.voxtrics.sdk.tests"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defaults.removePersistentDomain(forName: suite)
        defer { defaults.removePersistentDomain(forName: suite) }

        let first = SessionStore.sessionId(defaults: defaults)
        XCTAssertNotNil(UUID(uuidString: first))
        XCTAssertEqual(SessionStore.sessionId(defaults: defaults), first)
    }
}
