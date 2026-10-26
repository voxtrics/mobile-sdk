import SwiftUI
import Voxtrics

// One screen to try the SDK with: where Voxtrics is, which survey, from which screen, in which theme - and every event
// the survey sends, newest first. The fields are kept between launches. On the simulator the local stack is the Mac's
// own http://localhost:3000.
struct ContentView: View {
    @AppStorage("baseURL") private var baseURL = "http://localhost:3000"
    @AppStorage("surveyId") private var surveyId = ""
    @AppStorage("screen") private var screen = "checkout"
    @State private var theme = DemoTheme.system
    @State private var showingSurvey = false
    @State private var log: [String] = []

    var body: some View {
        NavigationStack {
            Form {
                Section("Voxtrics") {
                    TextField("Base URL", text: $baseURL)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
                Section("Survey") {
                    TextField("Survey ID", text: $surveyId)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField("Screen", text: $screen)
                        .textInputAutocapitalization(.never)
                    Picker("Theme", selection: $theme) {
                        ForEach(DemoTheme.allCases) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)
                }
                Section {
                    Button("Show survey") {
                        // The keyboard would stay up over the event log, and under the survey's sheet.
                        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                        if let url = URL(string: baseURL.trimmingCharacters(in: .whitespaces)) {
                            Voxtrics.configure(VoxtricsConfig(baseURL: url, logging: true))
                        }
                        append("showSurvey")
                        showingSurvey = true
                    }
                    .disabled(surveyId.trimmingCharacters(in: .whitespaces).isEmpty)
                }
                Section("Events") {
                    ForEach(Array(log.enumerated()), id: \.offset) { _, line in
                        Text(line).font(.system(.footnote, design: .monospaced))
                    }
                    if !log.isEmpty {
                        Button("Clear", role: .destructive) { log.removeAll() }
                    }
                }
            }
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle("Voxtrics SDK demo")
        }
        .voxtricsSurvey(
            id: surveyId.trimmingCharacters(in: .whitespaces),
            isPresented: $showingSurvey,
            options: SurveyOptions(screen: screen.isEmpty ? nil : screen, theme: theme.sdkTheme)
        ) { event in
            append(describe(event))
            if case .shown = event { takeDownScreensLater() }
        }
    }

    // For the UI tests (-takeDownAfterShown <seconds>): the app dismissing what is presented over it, as a deep link
    // or a logout would - which takes the survey's sheet down with it.
    private func takeDownScreensLater() {
        let delay = UserDefaults.standard.double(forKey: "takeDownAfterShown")
        guard delay > 0 else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
            let window = UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first
            append("app took its screens down")
            window?.rootViewController?.dismiss(animated: true)
        }
    }

    private func append(_ line: String) {
        log.insert("\(Date().formatted(date: .omitted, time: .standard))  \(line)", at: 0)
    }

    private func describe(_ event: SurveyEvent) -> String {
        switch event {
        case .shown: return "shown"
        case .submitted(let result): return "submitted mainEvaluation=\(result.mainEvaluation.map { "\($0)" } ?? "nil")"
        case .closed(let reason): return "closed \(reason.rawValue)"
        case .error(let error): return "error \(error.code.rawValue): \(error.message)"
        }
    }
}

enum DemoTheme: String, CaseIterable, Identifiable {
    case system, light, dark

    var id: String { rawValue }
    var label: String { rawValue.capitalized }
    var sdkTheme: Theme {
        switch self {
        case .system: return .system
        case .light: return .light
        case .dark: return .dark
        }
    }
}
