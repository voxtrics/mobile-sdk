package io.voxtrics.sdk.internal

import android.util.Log

/** Logcat, tag `Voxtrics`, only while VoxtricsConfig.logging is on - the SDK says nothing in an app's log otherwise. */
internal class Logger(private val enabled: Boolean) {
    fun debug(message: String) {
        if (enabled) Log.d(TAG, message)
    }

    fun warn(message: String) {
        if (enabled) Log.w(TAG, message)
    }

    private companion object {
        const val TAG = "Voxtrics"
    }
}
