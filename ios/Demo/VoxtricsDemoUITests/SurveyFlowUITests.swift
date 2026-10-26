import XCTest

// The demo against a running stack, driven the way a person would: open the survey, answer it, close it - and the
// events the app gets for it. It needs the admin's dev server on http://localhost:3000 (the backend behind it) and
// the ID of a published in-app survey whose first question is a 0-10 scale and second a text box, passed to the test
// runner as VOXTRICS_SURVEY_ID (`TEST_RUNNER_VOXTRICS_SURVEY_ID=… xcodebuild test …`). Without it the tests skip.
final class SurveyFlowUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        guard let surveyId = ProcessInfo.processInfo.environment["VOXTRICS_SURVEY_ID"], !surveyId.isEmpty else {
            throw XCTSkip("VOXTRICS_SURVEY_ID is not set: these tests need a running stack and a survey to open")
        }
        // A simulator left turned by an earlier session would put Show survey below the fold.
        XCUIDevice.shared.orientation = .portrait
        app = XCUIApplication()
        // The demo keeps its fields in UserDefaults (@AppStorage); arguments override them for this launch.
        app.launchArguments = ["-surveyId", surveyId, "-baseURL", "http://localhost:3000", "-screen", "checkout"]
        app.launch()
    }

    override func tearDown() {
        // A test that failed mid-way leaves its survey open; the next one starts from a fresh launch anyway.
        app?.terminate()
    }

    func testAnsweringReportsTheScoreAndClosingAfterwardsReportsCompleted() {
        openSurvey()
        snapshot("1 sheet")

        survey.buttons["9"].tap()
        let reason = survey.textViews.firstMatch
        reason.tap()
        reason.typeText("Fast checkout")
        snapshot("2 keyboard")
        survey.buttons["Submit"].tap()

        expectEvent("submitted mainEvaluation=4.6")
        snapshot("3 thank you")
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
        relaunch(adding: ["-takeDownAfterShown", "2"])
        openSurvey()

        expectEvent("app took its screens down")
        expectEvent("closed user")
        // The survey let go: another one opens.
        app.buttons["Show survey"].tap()
        let shown = app.staticTexts.matching(NSPredicate(format: "label ENDSWITH %@", "  shown"))
        wait(for: [expectation(for: NSPredicate(format: "count == 2"), evaluatedWith: shown)], timeout: 15)
    }

    private var survey: XCUIElement { app.webViews.firstMatch }

    private func relaunch(adding arguments: [String]) {
        app.terminate()
        app.launchArguments += arguments
        app.launch()
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
        XCTAssertTrue(survey.buttons["9"].waitForExistence(timeout: 10), "The survey's scale never appeared")
    }

    // The demo logs every event as "HH:mm:ss  <event>"; the sheet may cover the log, which is fine for a query.
    private func expectEvent(_ text: String, file: StaticString = #filePath, line: UInt = #line) {
        let entry = app.staticTexts.matching(NSPredicate(format: "label ENDSWITH %@", "  \(text)")).firstMatch
        XCTAssertTrue(entry.waitForExistence(timeout: 15), "No \"\(text)\" event", file: file, line: line)
    }
}
