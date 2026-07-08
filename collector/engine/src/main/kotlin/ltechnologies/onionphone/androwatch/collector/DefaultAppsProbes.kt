package ltechnologies.onionphone.androwatch.collector

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.WebView

/**
 * Probes the device's default/role-holder apps and key platform package versions.
 *
 * Uses [RoleManager] (Android 10+, role-holder reflection on Android 13+) to read the
 * default browser, launcher, dialer, SMS and assistant packages; resolves the home
 * activity; reads the current WebView provider package (Android 8+); and reports the
 * installed versions of Google Play Services and the Play Store.
 *
 * Privacy: the set of default apps and their versions is moderately identifying and helps
 * build a fingerprint, but no runtime permission is required to read this metadata.
 *
 * @param context Context used to access [RoleManager] and the package manager.
 * @return Map of probe keys to package/version values.
 */
fun probeDefaultApps(context: Context): Map<String, String> = buildMap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val rm = context.getSystemService(RoleManager::class.java) ?: return@buildMap
        fun rolePkg(role: String): String? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                rm.javaClass.getMethod("getRoleHolders", String::class.java)
                    .invoke(rm, role) as List<*>
            } else {
                emptyList<Any>()
            }
        }.getOrNull()?.firstOrNull()?.toString()
        rolePkg(RoleManager.ROLE_BROWSER)?.let { put("defaultBrowser", it) }
        rolePkg(RoleManager.ROLE_HOME)?.let { put("defaultLauncher", it) }
        rolePkg(RoleManager.ROLE_DIALER)?.let { put("defaultDialer", it) }
        rolePkg(RoleManager.ROLE_SMS)?.let { put("defaultSms", it) }
        rolePkg(RoleManager.ROLE_ASSISTANT)?.let { put("defaultAssistant", it) }
    }
    val home = context.packageManager.resolveActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
        PackageManager.MATCH_DEFAULT_ONLY,
    )
    home?.activityInfo?.packageName?.let { put("homeResolve", it) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WebView.getCurrentWebViewPackage()?.let { wv ->
            put("webviewPackage", "${wv.packageName}@${wv.versionCode}")
        }
    }
    listOf("com.google.android.gms", "com.android.vending").forEach { pkg ->
        val ver = runCatching {
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(pkg, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
        }.getOrNull()
        if (ver != null) put(if (pkg.contains("gms")) "gmsVersion" else "playStoreVersion", ver.toString())
    }
}
