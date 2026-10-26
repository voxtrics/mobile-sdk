import XCTest
@testable import Voxtrics

final class SurveyURLTests: XCTestCase {
    private let id = "eb7e05ef-8ca2-45ad-b1ac-175d2501dd82"

    func testThePageIsTheBaseURLsMPathWithTheContractsQuery() {
        let url = SurveyURL.build(
            baseURL: URL(string: "https://feedback.voxtrics.io/")!,
            surveyId: id,
            query: [("platform", "IOS"), ("appId", "com.example.shop"), ("screen", "checkout")]
        )
        XCTAssertEqual(url?.absoluteString, "https://feedback.voxtrics.io/m/\(id)?platform=IOS&appId=com.example.shop&screen=checkout")
    }

    func testBlankValuesAreLeftOutAndEveryValueIsEncoded() {
        let url = SurveyURL.build(
            baseURL: URL(string: "http://localhost:3000")!,
            surveyId: id,
            query: [("screen", "Cart & checkout"), ("appVersion", nil), ("appBuild", "  "), ("x", "a+b")]
        )
        // "+" too: the page's URLSearchParams reads a bare one as a space.
        XCTAssertEqual(url?.absoluteString, "http://localhost:3000/m/\(id)?screen=Cart%20%26%20checkout&x=a%2Bb")
    }

    func testOnlyASurveyIdBecomesPartOfThePath() {
        XCTAssertTrue(SurveyURL.isValidSurveyId(id))
        XCTAssertFalse(SurveyURL.isValidSurveyId("../admin"))
        XCTAssertFalse(SurveyURL.isValidSurveyId("abc?x=1"))
        XCTAssertFalse(SurveyURL.isValidSurveyId(""))
        XCTAssertNil(SurveyURL.build(baseURL: URL(string: "https://feedback.voxtrics.io")!, surveyId: "../admin", query: []))
    }

    func testTheOriginIsSchemeHostAndPort() {
        XCTAssertEqual(
            SurveyURL.origin(of: URL(string: "http://LOCALHOST:3000/m/x")!),
            SurveyURL.Origin(scheme: "http", host: "localhost", port: 3000)
        )
        XCTAssertEqual(
            SurveyURL.origin(of: URL(string: "https://feedback.voxtrics.io")!),
            SurveyURL.Origin(scheme: "https", host: "feedback.voxtrics.io", port: 443)
        )
        XCTAssertNil(SurveyURL.origin(of: URL(string: "file:///tmp/page.html")!))
    }
}
