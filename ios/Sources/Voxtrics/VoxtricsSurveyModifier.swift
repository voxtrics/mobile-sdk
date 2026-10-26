#if canImport(UIKit)
import SwiftUI
import UIKit

public extension View {
    /// Shows the survey `id` while `isPresented` is true, the SwiftUI way to call `Voxtrics.showSurvey`. `isPresented`
    /// goes back to false when the survey closes, and when it could not be shown (`.error` before `.shown`).
    func voxtricsSurvey(
        id: String,
        isPresented: Binding<Bool>,
        options: SurveyOptions = SurveyOptions(),
        onEvent: @escaping @MainActor (SurveyEvent) -> Void = { _ in }
    ) -> some View {
        background(VoxtricsSurveyPresenter(id: id, isPresented: isPresented, options: options, onEvent: onEvent))
    }
}

/// An empty view controller in the SwiftUI hierarchy, to present the sheet from.
private struct VoxtricsSurveyPresenter: UIViewControllerRepresentable {
    let id: String
    @Binding var isPresented: Bool
    let options: SurveyOptions
    let onEvent: @MainActor (SurveyEvent) -> Void

    final class Coordinator {
        var isShowing = false
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIViewController(context: Context) -> UIViewController {
        let controller = UIViewController()
        controller.view.isUserInteractionEnabled = false
        controller.view.backgroundColor = .clear
        return controller
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {
        let coordinator = context.coordinator
        guard isPresented, !coordinator.isShowing else { return }
        coordinator.isShowing = true
        let binding = $isPresented
        // After this update: the controller is in a window only once SwiftUI has laid it out.
        DispatchQueue.main.async { MainActor.assumeIsolated {
            var shown = false
            Voxtrics.showSurvey(id: id, from: controller, options: options) { event in
                onEvent(event)
                switch event {
                case .shown:
                    shown = true
                case .closed:
                    coordinator.isShowing = false
                    binding.wrappedValue = false
                case .error where !shown:
                    coordinator.isShowing = false
                    binding.wrappedValue = false
                case .submitted, .error:
                    break
                }
            }
        } }
    }
}
#endif
