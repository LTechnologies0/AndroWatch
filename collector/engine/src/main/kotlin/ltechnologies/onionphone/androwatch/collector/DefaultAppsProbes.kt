package ltechnologies.onionphone.androwatch.collector

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.WebView

/**
 * Probes the device's default apps via [PackageManager.resolveActivity] plus the
 * `<queries>` declarations in the app manifest. RoleManager is intentionally not used:
 * `getRoleHolders` is not a public compatibility contract and is empty/hidden on
 * many API 29–32 devices.
 *
 * Resolves default browser, launcher, dialer, SMS and assistant packages; reads the
 * current WebView provider package (Android 8+); and reports the installed versions
 * of Google Play Services and the Play Store (those packages are listed in `<queries>`).
 *
 * Privacy: the set of default apps and their versions is moderately identifying and helps
 * build a fingerprint, but no runtime permission is required to read this metadata.
 *
 * @param context Context used to access the package manager.
 * @return Map of probe keys to package/version values.
 */
fun probeDefaultApps(context: Context): Map<String, String> = buildMap {
    val pm = context.packageManager
    putSafe("defaultBrowser") {
        resolveDefault(pm, Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE))
    }
    putSafe("defaultLauncher") {
        resolveDefault(pm, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
    }
    putSafe("defaultDialer") {
        resolveDefault(pm, Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))
    }
    putSafe("defaultSms") {
        resolveDefault(pm, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
    }
    putSafe("defaultAssistant") {
        resolveDefault(pm, Intent(Intent.ACTION_ASSIST))
    }
    putSafe("homeResolve") {
        resolveDefault(pm, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        putSafe("webviewPackage") {
            WebView.getCurrentWebViewPackage()?.let { wv ->
                "${wv.packageName}@${wv.versionCode}"
            }
        }
    }
    listOf("com.google.android.gms" to "gmsVersion", "com.android.vending" to "playStoreVersion").forEach { (pkg, key) ->
        putSafe(key) {
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(pkg, 0)
            }
            val ver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pi.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pi.versionCode.toLong()
            }
            ver.toString()
        }
    }
}

private fun resolveDefault(pm: PackageManager, intent: Intent): String? =
    runCatching {
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()
