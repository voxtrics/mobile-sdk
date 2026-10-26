package io.voxtrics.sdk.internal

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat

/**
 * What a response records about the app it was given in - none of it personal: the app's ids and versions, the OS
 * version and the device model.
 */
internal data class AppInfo(
    val appId: String,
    val appVersion: String?,
    val appBuild: String?,
    val osVersion: String,
    val deviceModel: String,
) {
    companion object {
        fun of(context: Context): AppInfo {
            val packageName = context.packageName
            val packageInfo = try {
                if (Build.VERSION.SDK_INT >= 33) {
                    context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(packageName, 0)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
            return AppInfo(
                appId = packageName,
                appVersion = packageInfo?.versionName,
                appBuild = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it).toString() },
                osVersion = Build.VERSION.RELEASE,
                deviceModel = Build.MODEL,
            )
        }
    }
}
