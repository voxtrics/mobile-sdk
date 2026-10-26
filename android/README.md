# Voxtrics Android SDK

Shows Voxtrics in-app surveys in an Android app: a bottom sheet over the app's screen, opened when the app asks.
Kotlin, minSdk 24.

```
android/
  voxtrics/   the library (io.voxtrics.sdk)
  demo/       an app to try it with
```

## Using it

```kotlin
// Optional: another environment, or Logcat output (tag "Voxtrics").
Voxtrics.configure(context, VoxtricsConfig(logging = BuildConfig.DEBUG))

Voxtrics.showSurvey(
    activity,                       // a FragmentActivity
    surveyId = "YOUR_SURVEY_ID",     // the survey's ID, from its embed code in the Voxtrics admin
    options = SurveyOptions(screen = "checkout", theme = Theme.SYSTEM),
    listener = object : SurveyListener {
        override fun onShown() {}
        override fun onSubmitted(result: SurveyResult) {
            if ((result.mainEvaluation ?: 0.0) >= 4.0) askForStoreReview()
        }
        override fun onClosed(reason: CloseReason) {}
        override fun onError(error: SurveyError) {}
    },
)
```

- Call it on the main thread. One survey at a time.
- The page loads out of sight; the sheet appears only when the survey is ready to answer. A survey that cannot be
  shown reports `onError` and shows nothing: `SURVEY_NOT_FOUND`, `NOT_IN_APP_SURVEY`, `ALREADY_SUBMITTED`, `OFFLINE`,
  `TIMEOUT` (no answer in 10 s), `WEBVIEW_OUTDATED` (Android System WebView before Chrome 80), `UNKNOWN`.
- The sheet takes the survey's height, up to 90% of the screen, and shrinks above the keyboard; the page scrolls past
  that. It is not draggable - Back, a tap outside and the survey's close control close it (`CloseReason.USER`, or
  `COMPLETED` after the thank-you screen).
- Links in the survey open in the system's browser.
- The sheet brings its own Material 3 theme, so the app's theme does not matter.

What the library adds to the app: the `INTERNET` and `ACCESS_NETWORK_STATE` permissions, and AndroidX Fragment,
Material Components and AndroidX WebKit. What it stores: one UUID per install (SharedPreferences `io.voxtrics.sdk`), the
session id the backend uses to spot a second answer. What a response records about the app: its applicationId,
versionName and versionCode, the Android version, the device model, the SDK version and the `screen` name - nothing
personal.

### Rotation and other configuration changes

The survey stays open when the activity is recreated - a rotation, dark mode switched, a new window size - loading or
on screen: it moves over to the new activity, with what was typed. The listener is kept for the whole survey, so it
must not hold on to the activity it was created in; write to a ViewModel, as the demo does. The page keeps the theme
it opened with.

### Edge to edge

From Android 15 an app targeting API 35 or later is drawn edge to edge, and so is the sheet in any app: it goes down
under the navigation bar, the survey's own background fills that strip, and its content ends above it; the bar's
icons follow the survey's light or dark theme. With the keyboard up the sheet sits on it. In an app that is not edge to
edge the sheet stays above the navigation bar. Both were tried on Android 16, keyboard included (`./gradlew
:demo:assembleDebug -PdemoTargetSdk=34` builds the demo as an app that is not edge to edge); on Android 13 the sheet
above the bar was, the keyboard there not since the last change.

- Proguard/R8: the consumer rules ship with the library; nothing to add.

## Building

The Android SDK path is in `local.properties` (`sdk.dir=…`), which is not committed. Gradle 8.14.3 through the wrapper.

```bash
./gradlew :voxtrics:testDebugUnitTest :demo:assembleDebug
```

## Trying it against the local stack

The demo opens the survey page on `http://localhost:3000` by default - the admin's dev server, whose widget calls the
backend on `http://localhost:4000`. Inside the emulator both are the emulator itself, so forward them to the Mac:

```bash
adb reverse tcp:3000 tcp:3000
```

```bash
adb reverse tcp:4000 tcp:4000
```

A real phone plugged in over USB (with USB debugging on) works the same way: the two `adb reverse` commands forward
its `localhost` to the Mac too.

Then install the demo, enter the ID of a published in-app survey (on an app domain in the admin) and press Show
survey. Every listener callback is logged on the screen, and with logging on the bridge's traffic goes to Logcat
(`adb logcat -s Voxtrics`). The demo allows plain HTTP to `localhost` and `10.0.2.2` only
(`demo/src/main/res/xml/network_security_config.xml`); the library itself asks for none.
