package io.voxtrics.sdk.internal

import android.content.Context
import java.util.UUID

/**
 * The install's session id: one UUID, made the first time and kept. The backend matches it alone - not the IP, which
 * changes as a phone moves between networks - against a survey's earlier answers, which is how a survey that takes one
 * answer per person knows this install already gave its own. The WebView's storage is not where it lives - an app can
 * clear that; this is the SDK's own.
 */
internal object SessionStore {
    private const val PREFS = "io.voxtrics.sdk"
    private const val KEY_SESSION_ID = "session_id"

    fun sessionId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_SESSION_ID, null)?.let { return it }
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_SESSION_ID, created).apply()
        return created
    }
}
