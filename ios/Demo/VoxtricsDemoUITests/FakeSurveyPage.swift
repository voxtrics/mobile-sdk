import Foundation
import Network

/// A stand-in for the survey page the SDK opens (`{baseURL}/m/{surveyId}`), served from the UI test runner over the
/// loopback interface, so the demo is tested without a backend. It speaks the same bridge as the real page -
/// `{"v":1,"type":…,"payload":{…}}` through `window.webkit.messageHandlers.voxtrics` - and the survey id names what it
/// does. Its controls send what a person's taps on the real page would: an answer, the score, the close after the
/// thank-you screen, a link.
final class FakeSurveyPage {
    enum Survey: String {
        /// Reports its height and is ready at once.
        case ready
        /// Answers SURVEY_NOT_FOUND, as the real page does for a missing survey.
        case notFound = "not-found"
        /// Never gets ready.
        case silent
        /// Takes longer to arrive than the SDK gives a loaded page to get ready, then is ready at once.
        case slow
        /// The page itself fails: HTTP 500.
        case serverError = "server-error"
        /// Ready, with a frame inside that then tries to close the survey and report a score - messages the bridge
        /// must not take. (A frame loads only once the sheet is up: WebKit leaves it alone while the page is parked.)
        case framed
    }

    private let listener: NWListener
    private let queue = DispatchQueue(label: "io.voxtrics.demo.uitests.fake-survey-page")

    private var port: UInt16 = 0
    /// How long `slow` takes to answer: past the SDK's readyTimeout (10 s), well within its pageLoadTimeout (20 s).
    static let slowPageDelay: TimeInterval = 12

    /// The demo's base URL. The simulator shares the Mac's loopback, so the app reaches the runner's port.
    var baseURL: String { "http://127.0.0.1:\(port)" }

    init() throws {
        let parameters = NWParameters.tcp
        parameters.requiredInterfaceType = .loopback
        listener = try NWListener(using: parameters)
        let ready = DispatchSemaphore(value: 0)
        listener.stateUpdateHandler = { state in
            if case .ready = state { ready.signal() }
        }
        listener.newConnectionHandler = { [weak self] connection in self?.serve(connection) }
        listener.start(queue: queue)
        guard ready.wait(timeout: .now() + 5) == .success, let port = listener.port?.rawValue else {
            throw URLError(.cannotConnectToHost)
        }
        self.port = port
    }

    func stop() {
        listener.cancel()
    }

    private func serve(_ connection: NWConnection) {
        connection.start(queue: queue)
        connection.receive(minimumIncompleteLength: 1, maximumLength: 64 * 1024) { [weak self] data, _, _, _ in
            guard let self, let data, let request = String(data: data, encoding: .utf8) else {
                connection.cancel()
                return
            }
            // "GET /m/ready?platform=IOS&… HTTP/1.1" - the path, without its query.
            let target = request.split(separator: " ").dropFirst().first.map(String.init) ?? "/"
            let path = target.split(separator: "?").first.map(String.init) ?? target
            let (status, body) = self.respond(to: path)
            let head = "HTTP/1.1 \(status)\r\nContent-Type: text/html; charset=utf-8\r\n"
                + "Content-Length: \(body.utf8.count)\r\nConnection: close\r\n\r\n"
            let delay = path == "/m/\(Survey.slow.rawValue)" ? Self.slowPageDelay : 0
            self.queue.asyncAfter(deadline: .now() + delay) {
                connection.send(content: Data((head + body).utf8), completion: .contentProcessed { _ in connection.cancel() })
            }
        }
    }

    private func respond(to path: String) -> (status: String, body: String) {
        switch path {
        case "/m/\(Survey.ready.rawValue)":
            return ("200 OK", page(onLoad: "fake.ready();"))
        case "/m/\(Survey.notFound.rawValue)":
            return ("200 OK", page(onLoad: "fake.post('error', {code: 'SURVEY_NOT_FOUND', message: 'No such survey'});"))
        case "/m/\(Survey.silent.rawValue)":
            return ("200 OK", page(onLoad: ""))
        case "/m/\(Survey.slow.rawValue)":
            return ("200 OK", page(onLoad: "fake.ready();"))
        case "/m/\(Survey.serverError.rawValue)":
            return ("500 Internal Server Error", "Server error")
        case "/m/\(Survey.framed.rawValue)":
            return ("200 OK", page(onLoad: "fake.ready();", top: #"<iframe src="/talking-frame" style="width:100%;height:80px;border:0"></iframe>"#))
        case "/talking-frame":
            return ("200 OK", page(onLoad: "fake.post('submitted', {mainEvaluation: 1}); fake.post('closed', {reason: 'completed'});"))
        default:
            return ("404 Not Found", "Not found")
        }
    }

    private func page(onLoad: String, top: String = "") -> String {
        """
        <!doctype html>
        <!-- As the real page: as tall as its window at least, and it reports its content's height, not the document's. -->
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1"><title>A fake survey</title></head>
        <body style="margin:0;padding:16px;box-sizing:border-box;min-height:100vh;font-family:-apple-system,sans-serif;background:#fff">
          \(top)
          <div id="content">
            <h1 style="font-size:20px;margin-top:0">A fake survey</h1>
            <p><button>9</button></p>
            <input aria-label="Why" type="text" enterkeyhint="done" onkeydown="if (event.key === 'Enter') this.blur()"
                   style="width:100%;box-sizing:border-box;font-size:16px;padding:8px">
            <p style="margin-bottom:0">
              <button onclick="fake.post('submitted', {mainEvaluation: 4.6})">Submit</button>
              <button onclick="fake.post('closed', {reason: 'completed'})">Close</button>
              <button onclick="fake.post('openUrl', {url: 'https://example.com/'})">Open link</button>
            </p>
          </div>
          <script>
            window.fake = {
              post: function (type, payload) {
                window.webkit.messageHandlers.voxtrics.postMessage(JSON.stringify({v: 1, type: type, payload: payload || {}}));
              },
              ready: function () {
                fake.post('resize', {height: document.getElementById('content').offsetHeight + 32});
                fake.post('ready', {surveyId: location.pathname.split('/').pop(), locale: 'en'});
              }
            };
            \(onLoad)
          </script>
        </body></html>
        """
    }
}
