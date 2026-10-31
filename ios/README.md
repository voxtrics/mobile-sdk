# Voxtrics iOS SDK

Shows Voxtrics in-app surveys in an iOS app: a sheet over the app's screen, opened when the app asks.
Swift, iOS 16.4+.

```
Package.swift            at the repository's root, so Swift Package Manager can add the repository by URL
ios/
  Sources/Voxtrics/      the library
  Tests/VoxtricsTests/   unit tests (XCTest)
  Demo/                  an app to try it with (VoxtricsDemo.xcodeproj), and UI tests that drive it
```

> **0.1 status:** built with Xcode 27.1 and tried against the local stack on the iOS 27 simulator (iPhone 17, iPad
> Air) - every case below - on iOS 17.0 (iPhone 15) through the UI tests, and on a real iPhone 16 Pro (iOS 26.6.2)
> against the Mac's stack over Wi-Fi. Not yet run on iOS 16.x: Xcode 27 offers no simulator older than 17.0, and the
> floor is 16.4.

## Using it

UIKit:

```swift
import Voxtrics

// Optional: another environment, or logging (os.Logger, subsystem "io.voxtrics.sdk").
Voxtrics.configure(VoxtricsConfig(logging: true))

Voxtrics.showSurvey(
    id: "YOUR_SURVEY_ID",                    // the survey's ID, from its embed code in the Voxtrics admin
    from: viewController,                    // on screen; the sheet opens over whatever it presents
    options: SurveyOptions(screen: "checkout", theme: .system)
) { event in
    switch event {
    case .shown: break
    case .submitted(let result):
        if (result.mainEvaluation ?? 0) >= 4 { askForStoreReview() }
    case .closed(let reason): break          // .user or .completed
    case .error(let error): break            // error.code, error.message
    }
}
```

SwiftUI:

```swift
@State private var showSurvey = false

Button("Feedback") { showSurvey = true }
    .voxtricsSurvey(id: "YOUR_SURVEY_ID", isPresented: $showSurvey, options: SurveyOptions(screen: "settings")) { event in
        // the same events
    }
```

- Call it on the main thread; every event arrives on it. One survey at a time.
- The page loads out of sight; the sheet appears only when the survey is ready to answer. A survey that cannot be
  shown reports `.error` and shows nothing: `surveyNotFound`, `notInAppSurvey`, `alreadySubmitted`, `offline`,
  `timeout` (the page didn't load in 20 s, or the survey didn't open 10 s after it did), `unknown`. The raw values are
  the codes both SDKs share (`SURVEY_NOT_FOUND`, …).
- The sheet takes the survey's height, up to 90% of the screen; the page scrolls past that. A swipe down from the
  page's top, a tap outside and the survey's close control close it (`.user`, or `.completed` after the thank-you
  screen). From iOS 26 the sheet floats a little in from the screen's edges, the system's own style; on iPad too it
  is a sheet along the bottom edge, as wide as the window.
- With the keyboard up the sheet rises above it and the page scrolls the field into view.
- `isPresented` goes back to false when the survey closes or could not be shown. Setting it to false yourself does
  not close an open survey.
- Links in the survey open in Safari (or the app for `mailto:` / `tel:`).
- The sheet has no colours of its own: the survey paints its theme's background, light or dark as `theme` says
  (`.system` is the presenting screen's interface style, with the app's override).

What the SDK adds to the app: nothing to declare in Info.plist - it loads `https://feedback.voxtrics.io` over HTTPS.
What it stores: one UUID per install (UserDefaults suite `io.voxtrics.sdk`), the session id the backend uses to spot a
second answer. What a response records about the app: its bundle id, version and build, the iOS version, the device
model, the SDK version and the `screen` name - nothing personal. The package ships
`PrivacyInfo.xcprivacy`: no tracking; UserDefaults read with reason CA92.1; collected data declared as other user
content (the answers), device ID (the install's UUID) and other diagnostic data (app and device details), none linked
to the user or used for tracking. That last part is a draft to review before the first release.

## Building

Needs Xcode 16 or later (the Command Line Tools alone do not build an iOS package); 0.1 was built with Xcode 27.1.

```bash
xcodebuild test -scheme Voxtrics -destination 'platform=iOS Simulator,name=iPhone 17'
```

Run it from the repository's root. The demo: open `ios/Demo/VoxtricsDemo.xcodeproj`, which takes the package from the
repository's root as a local package, and run the VoxtricsDemo scheme on a simulator. On a real device set a team in
Signing & Capabilities first.

The demo's UI tests (`ios/Demo/VoxtricsDemoUITests`) drive the demo the way a person would and check the events the
app gets: answer a survey and close it the three ways a person can, have the app take the sheet down itself (the
demo's `-takeDownAfterShown` launch argument), a survey that cannot be shown, a page that fails, never gets ready (the
timeout) or loads slowly and still opens, a link, and a frame inside the page trying to talk for it. They need no
backend: the survey page is `FakeSurveyPage`, served by the test runner on the loopback interface, speaking the real
page's bridge.

```bash
xcodebuild test -project ios/Demo/VoxtricsDemo.xcodeproj -scheme VoxtricsDemo -destination 'platform=iOS Simulator,name=iPhone 17'
```

An older iOS: download its simulator runtime from developer.apple.com (More Downloads, "Simulator Runtime"), add it
with `xcrun simctl runtime add <dmg>`, create a device on it and pass that device's ID as the destination.

## Trying it against the local stack

The demo opens the survey page on `http://localhost:3000` by default - the admin's dev server, whose widget calls the
backend on `http://localhost:4000`. The simulator shares the Mac's `localhost`, so nothing needs forwarding. Enter the
ID of a published in-app survey (its app added in the admin's Settings, Apps) and press Show survey. Every event is
logged on the screen, the bridge's traffic goes to the Xcode console (debug level, subsystem `io.voxtrics.sdk`), and the
page can be inspected from Safari's Develop menu (logging makes the WebView inspectable). A simulator that has just
booted can be slow enough starting WebKit and loading the dev server's page the first time to run into the timeouts; the
next try opens in about a second. The demo's Info.plist allows plain HTTP to local addresses only
(`NSAllowsLocalNetworking`); the library itself asks for no exception.

On a real iPhone `localhost` is the phone itself, so the demo reaches the Mac by its network address instead. Put the
iPhone on the Mac's Wi-Fi, find the address (`ipconfig getifaddr en0`, say `192.168.1.20`) and enter
`http://192.168.1.20:3000` as the base URL. The admin's dev server already listens on the network, the page's widget
calls the backend on the host it was opened from (port 4000), and a local backend takes in-app pages from a private
network address as it does from `localhost`. iOS asks once to allow the local network - allow it, then press Show
survey again if the first try ended in an error. Before the first run: turn on Developer Mode on the iPhone
(Settings > Privacy & Security), sign in to Xcode with an Apple ID (Settings > Accounts), pick its team in the
VoxtricsDemo target's Signing & Capabilities, and run on the iPhone; with a free team, trust the developer on the
iPhone once (Settings > General > VPN & Device Management).
