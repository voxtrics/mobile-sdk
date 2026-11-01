import XCTest

// The demo driven the way a person would - open the survey, answer it, close it - and the events the app gets for it,
// against FakeSurveyPage served from this runner: no backend, no survey to set up. The page's controls stand for what a
// person does on the real page; the sheet, the swipe, the tap outside and the system are the real ones.
final class SurveyFlowUITests: XCTestCase {
    private var app: XCUIApplication!
    private var page: FakeSurveyPage!

    override func setUpWithError() throws {
        continueAfterFailure = false
        // A simulator left turned by an earlier session would put Show survey below the fold.
        XCUIDevice.shared.orientation = .portrait
        page = try FakeSurveyPage()
        app = XCUIApplication()
        launch(.ready)
    }

    override func tearDown() {
        // A test that failed mid-way leaves its survey open; the next one starts from a fresh launch anyway.
        app?.terminate()
        page?.stop()
    }

    func testAnsweringReportsTheScoreAndClosingAfterwardsReportsCompleted() {
        openSurvey()
        snapshot("1 sheet")

        survey.buttons["9"].tap()
        let reason = survey.textFields.firstMatch
        reason.tap()
        reason.typeText("Fast checkout")
        snapshot("2 keyboard")
        // Done typing (the field blurs on Return): the keyboard goes, and the sheet settles back before Submit - a tap
        // while the sheet is still moving down lands where the button was.
        reason.typeText("\n")
        XCTAssertTrue(app.keyboards.firstMatch.waitForNonExistence(timeout: 5), "The keyboard stayed up")
        tapOnceSettled(survey.buttons["Submit"])

        expectEvent("submitted mainEvaluation=4.6")
        survey.buttons["Close"].tap()
        expectEvent("closed completed")
    }

    func testDraggingTheSheetDownReportsClosedByUser() {
        openSurvey()

        // swipeDown() is too short a gesture: the sheet springs back. A drag from its top edge to the screen's bottom.
        let sheetTop = survey.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.03))
        let screenBottom = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.99))
        sheetTop.press(forDuration: 0.05, thenDragTo: screenBottom, withVelocity: .fast, thenHoldForDuration: 0)
        expectEvent("closed user")
    }

    func testTappingOutsideTheSheetReportsClosedByUser() {
        openSurvey()

        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.08)).tap()
        expectEvent("closed user")
    }

    func testTheAppTakingTheSheetDownReportsClosedByUserAndTheNextSurveyOpens() {
        launch(.ready, adding: ["-takeDownAfterShown", "2"])
        // Not openSurvey(): its wait for the sheet to settle can outlast the 2 s, and the sheet is gone under it -
        // which a CI runner is slow enough for. Nothing here drags the sheet.
        app.buttons["Show survey"].tap()
        expectEvent("shown")

        expectEvent("app took its screens down")
        expectEvent("closed user")
        // The survey let go: another one opens - and stays, the demo takes its screens down once. Seen on screen, not
        // counted in the event log: the log is a lazy list, newest first, and the first "shown" scrolls out of it.
        XCTAssertTrue(survey.waitForNonExistence(timeout: 5), "The first survey stayed on screen")
        app.buttons["Show survey"].tap()
        XCTAssertTrue(survey.buttons["9"].waitForExistence(timeout: 15), "The next survey never appeared")
    }

    func testASurveyThePageCannotShowReportsWhyAndShowsNothing() {
        launch(.notFound)
        app.buttons["Show survey"].tap()

        expectEvent(startingWith: "error SURVEY_NOT_FOUND")
        XCTAssertFalse(survey.exists)
    }

    func testAPageThatFailsToLoadIsReportedAtOnce() {
        launch(.serverError)
        app.buttons["Show survey"].tap()

        expectEvent(startingWith: "error UNKNOWN")
    }

    // The page's download has a budget of its own: a page that is slow to arrive still opens, however long a loaded page
    // is given to get ready. With one 10 s budget for both, this one timed out.
    func testASlowPageStillOpens() {
        launch(.slow)
        app.buttons["Show survey"].tap()

        expectEvent(startingWith: "shown", timeout: FakeSurveyPage.slowPageDelay + 10)
        XCTAssertFalse(event(startingWith: "error").exists)
    }

    func testAPageThatNeverGetsReadyTimesOut() {
        launch(.silent)
        app.buttons["Show survey"].tap()

        // The SDK gives a loaded page 10 s to be ready, so nothing comes before that. How long after it depends on how
        // fast the page loads: a cold simulator takes seconds to start WebKit, and the page itself has 20 s.
        XCTAssertFalse(event(startingWith: "error").waitForExistence(timeout: 8))
        expectEvent(startingWith: "error TIMEOUT", timeout: 20)
        XCTAssertFalse(survey.exists)
    }

    func testAFrameInsideThePageCannotTalkToTheApp() {
        launch(.framed)
        openSurvey()

        // The frame reports a score and closes the survey; only the page itself may.
        XCTAssertFalse(event(startingWith: "submitted").waitForExistence(timeout: 3))
        XCTAssertFalse(event(startingWith: "closed").exists)
        XCTAssertTrue(survey.exists)
    }

    func testALinkOpensOutsideAndTheSurveyStays() {
        openSurvey()

        survey.buttons["Open link"].tap()

        let safari = XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")
        XCTAssertTrue(safari.wait(for: .runningForeground, timeout: 10), "The link did not open in Safari")
        app.activate()
        XCTAssertTrue(survey.buttons["Submit"].waitForExistence(timeout: 5), "The survey was gone after the link")
    }

    private var survey: XCUIElement { app.webViews.firstMatch }

    // The demo keeps its fields in UserDefaults (@AppStorage); arguments override them for this launch.
    private func launch(_ survey: FakeSurveyPage.Survey, adding arguments: [String] = []) {
        if app.state != .notRunning { app.terminate() }
        app.launchArguments = ["-surveyId", survey.rawValue, "-baseURL", page.baseURL, "-screen", "checkout"] + arguments
        app.launch()
    }

    private func tapOnceSettled(_ element: XCUIElement) {
        var last = element.frame
        for _ in 0..<20 {
            Thread.sleep(forTimeInterval: 0.2)
            let now = element.frame
            if now == last && element.isHittable { break }
            last = now
        }
        element.tap()
    }

    // Kept in the result bundle, to look at how the sheet sits on each iOS version.
    private func snapshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func openSurvey() {
        app.buttons["Show survey"].tap()
        expectEvent("shown")
        XCTAssertTrue(survey.buttons["9"].waitForExistence(timeout: 10), "The survey never appeared")
        waitForTheSheetToSettle()
    }

    // The fake page is ready at once, while the sheet is still sliding up; a drag that starts mid-slide is lost.
    private func waitForTheSheetToSettle() {
        var last = survey.frame
        for _ in 0..<20 {
            Thread.sleep(forTimeInterval: 0.2)
            let now = survey.frame
            if now == last { return }
            last = now
        }
        XCTFail("The sheet never settled")
    }

    // The demo logs every event as "HH:mm:ss  <event>"; the sheet may cover the log, which is fine for a query.
    private func expectEvent(_ text: String, file: StaticString = #filePath, line: UInt = #line) {
        let entry = app.staticTexts.matching(NSPredicate(format: "label ENDSWITH %@", "  \(text)")).firstMatch
        XCTAssertTrue(entry.waitForExistence(timeout: 15), "No \"\(text)\" event", file: file, line: line)
    }

    // An error is logged with its message after the code ("error TIMEOUT: The survey did not open…").
    private func expectEvent(startingWith text: String, timeout: TimeInterval = 15, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(event(startingWith: text).waitForExistence(timeout: timeout), "No \"\(text)…\" event", file: file, line: line)
    }

    private func event(startingWith text: String) -> XCUIElement {
        app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "  \(text)")).firstMatch
    }
}
