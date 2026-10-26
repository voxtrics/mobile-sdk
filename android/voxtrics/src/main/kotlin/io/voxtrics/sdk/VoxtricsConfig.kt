package io.voxtrics.sdk

/**
 * How the SDK reaches Voxtrics. The defaults are production's; an app changes them only to try surveys against
 * another environment (a local stack: `http://localhost:3000` through `adb reverse`).
 *
 * @property baseUrl The admin app's origin - the survey opens on `{baseUrl}/m/{surveyId}`. No trailing slash needed.
 * @property logging Writes the bridge's traffic and the WebView's console to Logcat (tag `Voxtrics`).
 */
public data class VoxtricsConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val logging: Boolean = false,
) {
    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://feedback.voxtrics.io"
    }
}
