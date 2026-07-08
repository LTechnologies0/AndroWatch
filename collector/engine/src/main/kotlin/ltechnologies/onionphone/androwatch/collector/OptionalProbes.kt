package ltechnologies.onionphone.androwatch.collector

import android.accounts.AccountManager
import android.app.WallpaperManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Settings

/**
 * Runs [block], returning its result or `null` if it throws.
 *
 * ponytail: `null` = probe failed or unavailable; callers skip via `addOptional`.
 *
 * @param block Probe lambda that may fail or be unsupported.
 * @return The probe result, or `null` on failure.
 */
inline fun <T> probeOrNull(block: () -> T): T? = runCatching(block).getOrNull()

/**
 * Probes the Google Advertising ID (GAID) via reflection into Play Services.
 *
 * Privacy: **high** — the advertising id is a user-resettable but cross-app tracking
 * identifier. The raw id is SHA-256 hashed before being surfaced; the limit-ad-tracking
 * flag is also reported. Uses reflection so the app does not hard-depend on Play Services.
 *
 * @param context Context used to query [AdvertisingIdClient][com.google.android.gms.ads.identifier].
 * @return Pair of (hashed advertising id, limit-ad-tracking enabled), or `null` if unavailable.
 */
fun probeAdvertisingId(context: Context): Pair<String, Boolean>? = probeOrNull {
    val clazz = Class.forName("com.google.android.gms.ads.identifier.AdvertisingIdClient")
    val info = clazz.getMethod("getAdvertisingIdInfo", Context::class.java)
        .invoke(null, context.applicationContext)
    val id = info.javaClass.getMethod("getId").invoke(info) as String
    val limited = info.javaClass.getMethod("isLimitAdTrackingEnabled").invoke(info) as Boolean
    sha256Hex(id) to limited
}

/**
 * Aggregate counts describing installed applications, without listing package names.
 *
 * @property visibleCount Total apps visible to this app (subject to package-visibility rules).
 * @property systemCount Number of system (pre-installed) apps.
 * @property userCount Number of user-installed apps (`visibleCount - systemCount`).
 */
data class InstalledPackageProbe(
    val visibleCount: Int,
    val systemCount: Int,
    val userCount: Int,
)

/**
 * Probes installed-application counts via `PackageManager.getInstalledApplications`.
 *
 * Privacy: **high** — the installed-app inventory is strongly identifying. This probe
 * returns only aggregate counts (total/system/user), never package names, and backs the
 * advanced InstalledAppsProbe category. Package visibility on Android 11+ limits results.
 *
 * @param context Context providing the package manager.
 * @return An [InstalledPackageProbe] with counts, or `null` on failure.
 */
fun probeInstalledPackages(context: Context): InstalledPackageProbe? = probeOrNull {
    val pm = context.packageManager
    // ponytail: ApplicationInfo is much lighter than PackageInfo; API still returns full list
    val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.getInstalledApplications(0)
    }
    var system = 0
    apps.forEach { app ->
        if (app.flags and ApplicationInfo.FLAG_SYSTEM != 0) system++
    }
    val visible = apps.size
    InstalledPackageProbe(
        visibleCount = visible,
        systemCount = system,
        userCount = visible - system,
    )
}

/**
 * Hashes the sorted set of visible account names via [AccountManager].
 *
 * Privacy: **high** — account names (often email addresses) are directly identifying, so
 * they are SHA-256 hashed rather than exposed. Visible accounts depend on the app's
 * authenticator access; on modern Android most accounts are hidden.
 *
 * @param context Context used to obtain [AccountManager].
 * @return SHA-256 hex of the account names, or `null` if none are visible.
 */
fun probeAccountNameHash(context: Context): String? = probeOrNull {
    val accounts = AccountManager.get(context).accounts
    if (accounts.isEmpty()) error("no visible accounts")
    sha256Hex(accounts.sortedBy { it.name }.joinToString("|") { it.name })
}

/**
 * Reads the configured ringtone URI from `Settings.System`.
 *
 * Privacy: a custom ringtone selection is a low-entropy personalization signal. No
 * permission required to read the setting.
 *
 * @param context Context providing the content resolver.
 * @return Ringtone URI string, or `null` if unset/unreadable.
 */
fun probeRingtoneUri(context: Context): String? = probeOrNull {
    Settings.System.getString(context.contentResolver, Settings.System.RINGTONE)
        ?.takeIf { it.isNotBlank() }
}

/**
 * Reads the configured notification-sound URI from `Settings.System`.
 *
 * Privacy: personalization signal only; no permission required.
 *
 * @param context Context providing the content resolver.
 * @return Notification sound URI string, or `null` if unset/unreadable.
 */
fun probeNotificationSoundUri(context: Context): String? = probeOrNull {
    Settings.System.getString(context.contentResolver, Settings.System.NOTIFICATION_SOUND)
        ?.takeIf { it.isNotBlank() }
}

/**
 * Hashes a sample of the current system wallpaper (Android 7+).
 *
 * Reads up to the first 4 KB of the wallpaper file and SHA-256 hashes it, giving a stable
 * fingerprint of the user's wallpaper without exposing the image.
 *
 * Privacy: the wallpaper is personal content; only a hash of a byte sample is retained. On
 * newer Android versions reading the wallpaper file may require `READ_EXTERNAL_STORAGE`,
 * hence this is an optional probe that fails soft.
 *
 * @param context Context used to obtain [WallpaperManager].
 * @return SHA-256 hex of the wallpaper sample, or `null` if unavailable.
 */
fun probeWallpaperHash(context: Context): String? = probeOrNull {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) error("API < 24")
    WallpaperManager.getInstance(context).getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { pfd ->
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { stream ->
            val sample = ByteArray(4096)
            val read = stream.read(sample)
            if (read <= 0) error("empty wallpaper")
            sha256Hex(bytesToHex(sample.copyOf(read)))
        }
    } ?: error("no wallpaper file")
}

/**
 * Counts enabled input methods from `Settings.Secure.ENABLED_INPUT_METHODS`.
 *
 * Privacy: the number of enabled keyboards is a low-entropy signal; no permission required.
 *
 * @param context Context providing the content resolver.
 * @return Count of enabled IMEs, or `null` if the setting is unreadable.
 */
fun probeEnabledInputMethods(context: Context): Int? = probeOrNull {
    val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS)
        ?: error("unreadable")
    raw.split(':').count { it.isNotBlank() }
}

/**
 * Reports whether NFC is present and enabled via [android.nfc.NfcAdapter].
 *
 * Privacy: hardware/state metadata only; no permission required.
 *
 * @param context Context used to obtain the default NFC adapter.
 * @return `true`/`false` for enabled state, `false` if no adapter, or `null` on failure.
 */
fun probeNfcAvailable(context: Context): Boolean? = probeOrNull {
    val adapter = android.nfc.NfcAdapter.getDefaultAdapter(context) ?: return@probeOrNull false
    adapter.isEnabled
}
