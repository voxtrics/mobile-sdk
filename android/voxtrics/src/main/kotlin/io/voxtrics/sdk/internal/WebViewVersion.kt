package io.voxtrics.sdk.internal

import android.content.Context
import androidx.webkit.WebViewCompat

/**
 * The oldest WebView the survey page can run in at all. Below Chrome 80 its scripts do not even parse - optional
 * chaining and `??` are its first syntax errors (seen on the API 28 emulator's WebView 66) - so the page stays blank
 * and the survey could only time out; checked before anything loads, to say so at once.
 *
 * Not Next.js's own browser target, Chrome 111 (the admin app sets no browserslist): that is what the build promises,
 * not where it stops working - the page ran in the API 33 emulator's WebView 101. Between 80 and the target a survey is
 * tried, and the worst it does is time out.
 */
internal object WebViewVersion {
    const val MIN_MAJOR = 80

    /** The WebView's Chrome major version, or null when the device does not say - then the page is tried anyway. */
    fun major(context: Context): Int? {
        val versionName = try {
            WebViewCompat.getCurrentWebViewPackage(context)?.versionName
        } catch (e: RuntimeException) {
            null
        }
        return parseMajor(versionName)
    }

    fun parseMajor(versionName: String?): Int? = versionName?.substringBefore('.')?.toIntOrNull()
}
