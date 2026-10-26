#if canImport(UIKit)
import UIKit
import WebKit

/// The sheet a survey is shown in: the system's page sheet with one detent, the survey's own height, which follows the
/// page's `resize`. A swipe down (from the top of the page) or a tap outside closes it, and so does the app taking down
/// the screen it was shown over. iPadOS 27 shows it along the bottom edge like an iPhone; where iPadOS centres a sheet
/// instead (regular width on older versions - not yet seen), the preferred content size sizes it.
@MainActor
final class SurveySheetController: UIViewController, UIAdaptivePresentationControllerDelegate {
    private static let detent = UISheetPresentationController.Detent.Identifier("io.voxtrics.sdk.content")
    private static let formSheetWidth: CGFloat = 540

    private weak var session: SurveySession?
    private let webView: WKWebView
    private var contentHeight: CGFloat

    init(session: SurveySession) {
        self.session = session
        self.webView = session.webView
        self.contentHeight = session.sheetHeight
        super.init(nibName: nil, bundle: nil)
        modalPresentationStyle = .pageSheet
        presentationController?.delegate = self
        preferredContentSize = CGSize(width: Self.formSheetWidth, height: contentHeight)
        if let sheet = sheetPresentationController {
            sheet.detents = [.custom(identifier: Self.detent) { [weak self] context in
                MainActor.assumeIsolated {
                    min(self?.contentHeight ?? context.maximumDetentValue, context.maximumDetentValue)
                }
            }]
            sheet.selectedDetentIdentifier = Self.detent
            sheet.preferredCornerRadius = 16
            sheet.prefersGrabberVisible = false
            // The page scrolls inside the sheet; reaching its top must not grow the sheet past the survey.
            sheet.prefersScrollingExpandsWhenScrolledToEdge = false
        }
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    override func loadView() {
        let container = UIView()
        // No background of the sheet's own: the survey page paints its theme's.
        container.backgroundColor = .clear
        webView.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(webView)
        NSLayoutConstraint.activate([
            webView.topAnchor.constraint(equalTo: container.topAnchor),
            webView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            webView.bottomAnchor.constraint(equalTo: container.bottomAnchor),
        ])
        view = container
    }

    /// The survey's height, already capped by the session - the page scrolls past it.
    func setContentHeight(_ height: CGFloat) {
        guard height != contentHeight else { return }
        contentHeight = height
        preferredContentSize = CGSize(width: Self.formSheetWidth, height: height)
        guard let sheet = sheetPresentationController else { return }
        sheet.animateChanges {
            sheet.invalidateDetents()
        }
    }

    // A swipe down or a tap outside. Only the person's own dismissals arrive here - never the app dismissing the
    // screen under the sheet, which takes the sheet with it; viewDidDisappear catches that one.
    func presentationControllerDidDismiss(_ presentationController: UIPresentationController) {
        session?.sheetGone()
    }

    // Gone, not covered: something the page presents over the sheet (a picker, a share sheet) is the sheet's own
    // presented controller, and the sheet is still there under it.
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if presentedViewController == nil { session?.sheetGone() }
    }
}
#endif
