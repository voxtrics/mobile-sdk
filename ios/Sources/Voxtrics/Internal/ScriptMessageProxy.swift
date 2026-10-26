#if canImport(UIKit)
import WebKit

/// WKUserContentController holds its message handlers strongly; this one holds the session weakly, so the session -
/// which owns the WebView, which owns the controller - is not kept alive by its own handler.
final class ScriptMessageProxy: NSObject, WKScriptMessageHandler {
    weak var target: WKScriptMessageHandler?

    init(_ target: WKScriptMessageHandler) {
        self.target = target
    }

    func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
        target?.userContentController(controller, didReceive: message)
    }
}
#endif
