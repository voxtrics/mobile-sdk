package io.voxtrics.sdk.internal

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

internal object Network {
    /** Whether a connection is up - not whether it reaches us, which the page finds out. */
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
