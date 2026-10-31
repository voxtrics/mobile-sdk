#if canImport(UIKit)
import UIKit
import WebKit

/// One survey, from showSurvey to its end: the page loads in a WebView parked out of sight in the presenter's window,
/// and only the page's `ready` - sent after its first `resize`, so the height is known - moves that WebView into the
/// sheet. A survey that cannot be shown (missing, already answered, offline, a page not loaded in 20 s or not `ready`
/// 10 s after loading) ends with `.error` and nothing on screen.
@MainActor
final class SurveySession: NSObject {
    private enum State { case loading, shown, ended }

    static private(set) var active: SurveySession?
    // Two budgets, so a slow network does not eat the page's own time: the page has pageLoadTimeout to load - its HTML,
    // scripts and fonts, into a WebView that may have nothing cached, over a phone's network - and then readyTimeout to
    // fetch its survey and report `ready`. One 10 s budget for both was mostly spent on the download on a first open
    // (8 s seen against production).
    static let pageLoadTimeout: TimeInterval = 20
    static let readyTimeout: TimeInterval = 10
    private static let bridgeName = "voxtrics"
    private static let maxHeightShare: CGFloat = 0.9
    private static let externalSchemes: Set<String> = ["http", "https", "mailto", "tel"]

    let surveyId: String
    private let config: VoxtricsConfig
    private let origin: SurveyURL.Origin
    private weak var presenter: UIViewController?
    private let onEvent: @MainActor (SurveyEvent) -> Void
    private let log: Log
    private var state = State.loading
    private var timeout: DispatchWorkItem?
    private var pageLoaded = false
    private var sheet: SurveySheetController?
    // A 1-point clipping box in the window: the WebView inside it is in the window, unhidden and full-sized - WebKit
    // renders and measures nothing for a hidden or zero-sized one - yet nothing of it shows or takes touches.
    private let parking = UIView()

    /// The page's content height in points, from its last `resize`.
    private(set) var contentHeight: CGFloat = 0

    let webView: WKWebView

    private init(
        presenter: UIViewController,
        config: VoxtricsConfig,
        origin: SurveyURL.Origin,
        surveyId: String,
        onEvent: @escaping @MainActor (SurveyEvent) -> Void
    ) {
        self.presenter = presenter
        self.config = config
        self.origin = origin
        self.surveyId = surveyId
        self.onEvent = onEvent
        self.log = Log(enabled: config.logging)

        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        configuration.allowsInlineMediaPlayback = true
        webView = WKWebView(frame: .zero, configuration: configuration)
        // Transparent until the page paints its theme's background, so no white flashes in a dark app.
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        webView.isInspectable = config.logging
        super.init()
        configuration.userContentController.add(ScriptMessageProxy(self), name: Self.bridgeName)
        webView.navigationDelegate = self
        webView.uiDelegate = self
    }

    static func start(
        presenter: UIViewController,
        config: VoxtricsConfig,
        surveyId: String,
        options: SurveyOptions,
        onEvent: @escaping @MainActor (SurveyEvent) -> Void
    ) {
        func refuse(_ code: SurveyErrorCode, _ message: String) {
            onEvent(.error(SurveyError(code: code, message: message)))
        }
        guard active == nil else { return refuse(.unknown, "Another survey is already open") }
        guard SurveyURL.isValidSurveyId(surveyId) else { return refuse(.surveyNotFound, "Not a survey id: \(surveyId)") }
        guard let origin = SurveyURL.origin(of: config.baseURL) else {
            return refuse(.unknown, "VoxtricsConfig.baseURL is not an http(s) URL")
        }
        guard !Connectivity.shared.isOffline else { return refuse(.offline, "No connection") }
        guard presenter.viewIfLoaded?.window != nil else {
            return refuse(.unknown, "The view controller to present from is not on screen")
        }
        let session = SurveySession(presenter: presenter, config: config, origin: origin, surveyId: surveyId, onEvent: onEvent)
        active = session
        session.load(options)
    }

    var maxHeight: CGFloat {
        let screenHeight = presenter?.view.window?.bounds.height ?? UIScreen.main.bounds.height
        return (screenHeight * Self.maxHeightShare).rounded()
    }

    var sheetHeight: CGFloat { min(contentHeight, maxHeight) }

    private func load(_ options: SurveyOptions) {
        guard let presenter, let window = presenter.view.window else {
            return fail(.unknown, "The screen the survey was opened from is not showing")
        }
        guard let url = pageURL(options, traits: presenter.traitCollection) else {
            return fail(.unknown, "The survey's address could not be built")
        }
        park(in: window)
        startTimeout(after: Self.pageLoadTimeout, "The survey page did not load within \(Int(Self.pageLoadTimeout)) seconds")
        log.debug("Opening \(url.absoluteString)")
        webView.load(URLRequest(url: url))
    }

    private func park(in window: UIWindow) {
        parking.frame = CGRect(x: window.bounds.maxX - 1, y: window.bounds.maxY - 1, width: 1, height: 1)
        parking.clipsToBounds = true
        parking.isUserInteractionEnabled = false
        parking.accessibilityElementsHidden = true
        webView.frame = CGRect(x: 0, y: 0, width: window.bounds.width, height: maxHeight)
        parking.addSubview(webView)
        window.addSubview(parking)
    }

    private func pageURL(_ options: SurveyOptions, traits: UITraitCollection) -> URL? {
        let app = AppInfo.current()
        let locale = options.locale ?? Locale.preferredLanguages.first ?? Locale.current.identifier
        return SurveyURL.build(baseURL: config.baseURL, surveyId: surveyId, query: [
            ("platform", "IOS"),
            ("appId", app.appId),
            ("appVersion", app.appVersion),
            ("appBuild", app.appBuild),
            ("osVersion", app.osVersion),
            ("deviceModel", app.deviceModel),
            ("sdkVersion", Voxtrics.sdkVersion),
            ("screen", options.screen),
            ("theme", Self.theme(options.theme, traits: traits)),
            ("locale", locale),
            ("sessionId", SessionStore.sessionId()),
        ])
    }

    // .system is the screen the sheet opens over, which carries the app's own override too.
    private static func theme(_ theme: Theme, traits: UITraitCollection) -> String {
        switch theme {
        case .light: return "light"
        case .dark: return "dark"
        case .system: return traits.userInterfaceStyle == .dark ? "dark" : "light"
        }
    }

    private func handle(_ message: BridgeMessage) {
        guard state != .ended else { return }
        switch message {
        case .unknown:
            break
        case .resize(let height):
            contentHeight = CGFloat(height)
            sheet?.setContentHeight(sheetHeight)
        case .ready:
            if state == .loading { show() }
        case .submitted(let mainEvaluation):
            onEvent(.submitted(SurveyResult(surveyId: surveyId, mainEvaluation: mainEvaluation)))
        case .closed(let reason):
            close(reason)
        case .error(let code, let message):
            // Before the sheet: the survey cannot be shown. After it (a response refused as a second one): the page
            // shows its thank-you screen all the same, and the sheet stays.
            if state == .loading { fail(code, message) } else { onEvent(.error(SurveyError(code: code, message: message))) }
        case .openURL(let url):
            openExternally(url)
        }
    }

    // One timer at a time: the page's load, then its getting ready.
    private func startTimeout(after seconds: TimeInterval, _ message: String) {
        timeout?.cancel()
        let timeout = DispatchWorkItem { [weak self] in self?.fail(.timeout, message) }
        self.timeout = timeout
        DispatchQueue.main.asyncAfter(deadline: .now() + seconds, execute: timeout)
    }

    private func show() {
        timeout?.cancel()
        guard var top = presenter, top.viewIfLoaded?.window != nil else {
            return fail(.unknown, "The screen the survey was opened from is not showing")
        }
        while let presented = top.presentedViewController, !presented.isBeingDismissed { top = presented }
        // The app is closing a screen of its own this very moment. UIKit refuses to present from a controller still
        // presenting one, so the sheet goes up once that screen is gone.
        if let leaving = top.presentedViewController, let transition = leaving.transitionCoordinator {
            transition.animate(alongsideTransition: nil) { [weak self] _ in
                MainActor.assumeIsolated { self?.show() }
            }
            return
        }
        state = .shown
        webView.removeFromSuperview()
        parking.removeFromSuperview()
        let sheet = SurveySheetController(session: self)
        self.sheet = sheet
        top.present(sheet, animated: true)
        // A presentation UIKit cannot make is only logged, and leaves the sheet unattached and off screen.
        guard sheet.presentingViewController != nil else {
            return fail(.unknown, "The survey's sheet could not be presented over the app's screen")
        }
        onEvent(.shown)
    }

    /// The sheet went away without the survey closing it: a swipe down, a tap outside, or the app taking down the
    /// screen the sheet was shown over (a deep link, a logout).
    func sheetGone() {
        if state == .shown { close(.user, dismissSheet: false) }
    }

    private func close(_ reason: CloseReason, dismissSheet: Bool = true) {
        guard state != .ended else { return }
        state = .ended
        if dismissSheet { sheet?.dismiss(animated: true) }
        release()
        onEvent(.closed(reason))
    }

    private func fail(_ code: SurveyErrorCode, _ message: String) {
        guard state != .ended else { return }
        let wasShown = state == .shown
        state = .ended
        log.warn("\(code.rawValue): \(message)")
        if wasShown { sheet?.dismiss(animated: true) }
        release()
        onEvent(.error(SurveyError(code: code, message: message)))
    }

    private func release() {
        timeout?.cancel()
        if Self.active === self { Self.active = nil }
        sheet = nil
        webView.stopLoading()
        webView.configuration.userContentController.removeScriptMessageHandler(forName: Self.bridgeName)
        parking.removeFromSuperview()
    }

    private func isOwnPage(_ url: URL?) -> Bool {
        url.flatMap(SurveyURL.origin(of:)) == origin
    }

    // Links in the survey open in the system's browser or app for them, never in the sheet's WebView.
    private func openExternally(_ url: URL) {
        guard let scheme = url.scheme?.lowercased(), Self.externalSchemes.contains(scheme) else { return }
        UIApplication.shared.open(url)
    }

    private static func isOffline(_ error: Error) -> Bool {
        let offline: Set<Int> = [
            NSURLErrorNotConnectedToInternet, NSURLErrorNetworkConnectionLost, NSURLErrorCannotFindHost,
            NSURLErrorCannotConnectToHost, NSURLErrorTimedOut, NSURLErrorDataNotAllowed,
        ]
        let nsError = error as NSError
        return nsError.domain == NSURLErrorDomain && offline.contains(nsError.code)
    }
}

extension SurveySession: WKScriptMessageHandler {
    nonisolated func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
        MainActor.assumeIsolated {
            // Messages only from the survey's own origin, top frame - whatever else might ever load in the WebView.
            let source = message.frameInfo.securityOrigin
            let sameOrigin = source.protocol.lowercased() == origin.scheme && source.host.lowercased() == origin.host
                && (source.port == 0 ? (origin.scheme == "https" ? 443 : 80) : source.port) == origin.port
            guard message.frameInfo.isMainFrame, sameOrigin else { return }
            log.debug("Bridge: \(String(describing: message.body))")
            if let parsed = BridgeMessage.parse(message.body) { handle(parsed) }
        }
    }
}

extension SurveySession: WKNavigationDelegate {
    // The survey's own page only: anything else the page might navigate to opens outside. The async forms: their
    // completion-handler twins changed attributes between SDKs, and a near miss is silently never called.
    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction) async -> WKNavigationActionPolicy {
        let mainFrame = action.targetFrame?.isMainFrame ?? true
        if !mainFrame || isOwnPage(action.request.url) { return .allow }
        if let url = action.request.url { openExternally(url) }
        return .cancel
    }

    func webView(_ webView: WKWebView, decidePolicyFor response: WKNavigationResponse) async -> WKNavigationResponsePolicy {
        if response.isForMainFrame, state == .loading, let http = response.response as? HTTPURLResponse, http.statusCode >= 400 {
            fail(.unknown, "The survey page answered \(http.statusCode)")
            return .cancel
        }
        return .allow
    }

    // The page is in, scripts included: from here it has its own time to get ready. A failed load does not get here as
    // loading - didFail ended the survey first.
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        guard state == .loading, !pageLoaded, isOwnPage(webView.url) else { return }
        pageLoaded = true
        startTimeout(after: Self.readyTimeout, "The survey did not open within \(Int(Self.readyTimeout)) seconds of its page loading")
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        guard state == .loading else { return }
        fail(Self.isOffline(error) ? .offline : .unknown, error.localizedDescription)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        guard state == .loading else { return }
        fail(Self.isOffline(error) ? .offline : .unknown, error.localizedDescription)
    }

    // The page's process was killed (memory pressure, a crash): nothing is left to show.
    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        if state == .shown { close(.user) } else { fail(.unknown, "The survey page stopped") }
    }
}

extension SurveySession: WKUIDelegate {
    // target="_blank" asks for a new web view; the link opens outside instead.
    func webView(
        _ webView: WKWebView,
        createWebViewWith configuration: WKWebViewConfiguration,
        for action: WKNavigationAction,
        windowFeatures: WKWindowFeatures
    ) -> WKWebView? {
        if let url = action.request.url { openExternally(url) }
        return nil
    }
}
#endif
