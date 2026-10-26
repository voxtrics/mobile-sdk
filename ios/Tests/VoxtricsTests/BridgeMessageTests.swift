import XCTest
@testable import Voxtrics

// The messages exactly as the page sends them (utils-ui, features/inAppSurvey/nativeBridge.ts).
final class BridgeMessageTests: XCTestCase {
    func testEveryMessageOfTheContractParses() {
        XCTAssertEqual(
            BridgeMessage.parse(#"{"v":1,"type":"ready","payload":{"surveyId":"eb7e05ef","locale":"de"}}"#),
            .ready(surveyId: "eb7e05ef", locale: "de")
        )
        XCTAssertEqual(BridgeMessage.parse(#"{"v":1,"type":"resize","payload":{"height":601}}"#), .resize(height: 601))
        XCTAssertEqual(
            BridgeMessage.parse(#"{"v":1,"type":"submitted","payload":{"mainEvaluation":4.6}}"#),
            .submitted(mainEvaluation: 4.6)
        )
        XCTAssertEqual(BridgeMessage.parse(#"{"v":1,"type":"closed","payload":{"reason":"completed"}}"#), .closed(.completed))
        XCTAssertEqual(BridgeMessage.parse(#"{"v":1,"type":"closed","payload":{"reason":"user"}}"#), .closed(.user))
        XCTAssertEqual(
            BridgeMessage.parse(#"{"v":1,"type":"error","payload":{"code":"SURVEY_NOT_FOUND","message":"Not found"}}"#),
            .error(.surveyNotFound, message: "Not found")
        )
        XCTAssertEqual(
            BridgeMessage.parse(#"{"v":1,"type":"openUrl","payload":{"url":"https://feedback.voxtrics.io/"}}"#),
            .openURL(URL(string: "https://feedback.voxtrics.io/")!)
        )
    }

    func testASurveyWithoutAScoreSubmitsNil() {
        XCTAssertEqual(
            BridgeMessage.parse(#"{"v":1,"type":"submitted","payload":{"mainEvaluation":null}}"#),
            .submitted(mainEvaluation: nil)
        )
    }

    func testWhatThisVersionDoesNotKnowIsIgnoredNeverACrash() {
        XCTAssertEqual(BridgeMessage.parse(#"{"v":2,"type":"progress","payload":{}}"#), .unknown(type: "progress"))
        XCTAssertEqual(
            BridgeMessage.parse(#"{"v":1,"type":"error","payload":{"code":"QUOTA","message":"?"}}"#),
            .error(.unknown, message: "?")
        )
        XCTAssertNil(BridgeMessage.parse("not json"))
        XCTAssertNil(BridgeMessage.parse(#"{"v":1}"#))
        XCTAssertNil(BridgeMessage.parse(#"{"v":1,"type":"resize","payload":{"height":true}}"#))
        XCTAssertNil(BridgeMessage.parse(42))
    }
}
