package ltechnologies.onionphone.androwatch.collector.advanced

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import ltechnologies.onionphone.androwatch.collector.addSafe
import ltechnologies.onionphone.androwatch.collector.addOptional
import ltechnologies.onionphone.androwatch.collector.buildSignals
import ltechnologies.onionphone.androwatch.collector.collectWebViewFingerprint
import ltechnologies.onionphone.androwatch.collector.failedSignal
import ltechnologies.onionphone.androwatch.collector.interpretedSignal
import ltechnologies.onionphone.androwatch.collector.probeInstalledPackages
import ltechnologies.onionphone.androwatch.collector.SignalInterpreter
import ltechnologies.onionphone.androwatch.collector.signal
import ltechnologies.onionphone.androwatch.collector.tagsSignal
import ltechnologies.onionphone.androwatch.collector.SignalCollector
import ltechnologies.onionphone.androwatch.model.SignalCategory

/** WebView probe values that indicate a failed/blocked JS read and should be skipped. */
private val WEBVIEW_SKIP_VALUES = setOf("timeout", "parse_error")

/**
 * Advanced (opt-in) collector for [SignalCategory.InstalledAppsProbe].
 *
 * Infers installed apps by probing well-known URI schemes with
 * `PackageManager.resolveActivity` and combines this with aggregate installed-package counts
 * ([probeInstalledPackages]). Package visibility restrictions (Android 11+) limit results.
 *
 * Privacy: **high** — detecting installed apps (messaging, finance, dating, etc.) is strongly
 * identifying and can reveal sensitive user attributes, hence its Advanced tier.
 *
 * APIs probed: `PackageManager.resolveActivity`, `PackageManager.getInstalledApplications`.
 *
 * @see SignalCollector
 */
class InstalledAppsProbeCollector : SignalCollector {
    override val category = SignalCategory.InstalledAppsProbe
    private val schemes = listOf(
        "whatsapp", "tg", "discord", "slack", "fb", "twitter", "instagram",
        "spotify", "venmo", "cashapp", "snapchat", "linkedin", "reddit",
        "signal", "zoom", "teams", "tiktok", "uber", "lyft", "viber",
        "line", "wechat", "paypal", "amazon", "ebay", "twitch", "telegram",
    )
    /**
     * Probes URI-scheme handlers and package counts, returning the app-inference signals.
     *
     * @param context Context providing the package manager.
     * @return Signals for detected schemes and package-count statistics.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val pm = context.packageManager
        val present = schemes.filter { scheme ->
            runCatching {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("$scheme://"),
                )
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
            }.getOrDefault(false)
        }
        val packageStats = probeInstalledPackages(context)
        return buildSignals(category) {
                        addSafe(category, "schemesProbed", "Schemes probed") {
                signal(category, "schemesProbed", "Schemes probed", schemes.size.toString(),
                    "Number of URI schemes tested for installed app handlers.")
            }
                        addSafe(category, "hits", "Detected schemes") {
                tagsSignal(category, "hits", "Detected schemes", present.ifEmpty { listOf("none") },
                    "Installed app patterns can strongly identify users.")
            }
                        addSafe(category, "hitCount", "Hit count") {
                signal(category, "hitCount", "Hit count", present.size.toString(),
                    "Count of detected apps narrows user profile.")
            }
            if (packageStats != null) {
                val stats = packageStats
                        addOptional(category, "visiblePackageCount", "Visible package count") {
                    signal(category, "visiblePackageCount", "Visible package count", stats.visibleCount.toString(),
                        "Package visibility (API 30+) limits enumeration; count is still a cohort signal.")
                }
                        addOptional(category, "systemPackageCount", "System package count") {
                    signal(category, "systemPackageCount", "System package count", stats.systemCount.toString(),
                        "System vs user package split reflects ROM footprint.")
                }
                        addOptional(category, "userPackageCount", "User package count") {
                    signal(category, "userPackageCount", "User package count", stats.userCount.toString(),
                        "User-installed package count narrows app ecosystem.")
                }
            }
        }
    }
}

/**
 * Advanced (opt-in) collector for [SignalCategory.WebViewFingerprint].
 *
 * WebView fingerprinting is intentionally on-demand only (Advanced tier); never run during
 * passive refresh. Delegates to [collectWebViewFingerprint] to reproduce browser-side
 * fingerprinting (user agent, canvas, WebGL, audio, navigator/screen properties), then maps
 * each field to an interpreted signal.
 *
 * Privacy: **high** — demonstrates exactly what a web tracker could fingerprint via WebView;
 * canvas/WebGL/audio hashes are strongly identifying. Hash values are truncated for display.
 *
 * APIs probed: `WebSettings.getDefaultUserAgent`, offscreen `WebView` + JS
 * (canvas 2D, WebGL `WEBGL_debug_renderer_info`, OfflineAudioContext, `navigator`/`screen`).
 *
 * @see SignalCollector
 * @see collectWebViewFingerprint
 */
class WebViewFingerprintCollector : SignalCollector {
    override val category = SignalCategory.WebViewFingerprint

    /**
     * Runs the WebView fingerprint probe and maps its result to interpreted signals.
     *
     * @param context Context used to build the offscreen WebView.
     * @return Interpreted WebView fingerprint signals; skips fields whose probe failed.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val fp = runCatching { collectWebViewFingerprint(context) }.getOrNull()
        return buildSignals(category) {
            addOptional(category, "ua", "WebView user agent") {
                val ua = android.webkit.WebSettings.getDefaultUserAgent(context.applicationContext)
                interpretedSignal(
                    category, "ua", "WebView user agent",
                    SignalInterpreter.webViewUserAgent(ua),
                    ua,
                    "User agent and rendering stack are common web fingerprint components.",
                )
            }
            if (fp == null) return@buildSignals
            addOptional(category, "platform", "Navigator platform") {
                if (fp.platform in WEBVIEW_SKIP_VALUES) error("skipped")
                interpretedSignal(
                    category, "platform", "Navigator platform",
                    SignalInterpreter.webViewPlatform(fp.platform),
                    fp.platform,
                    "JS platform string complements user agent.",
                )
            }
            addOptional(category, "hardwareConcurrency", "Hardware concurrency") {
                interpretedSignal(
                    category, "hardwareConcurrency", "Hardware concurrency",
                    SignalInterpreter.hardwareConcurrency(fp.hardwareConcurrency),
                    fp.hardwareConcurrency,
                    "Reported CPU core count is a browser fingerprint signal.",
                )
            }
            addOptional(category, "deviceMemory", "Device memory (GB)") {
                interpretedSignal(
                    category, "deviceMemory", "Device memory (GB)",
                    SignalInterpreter.deviceMemoryGb(fp.deviceMemory),
                    fp.deviceMemory,
                    "navigator.deviceMemory reveals device RAM tier.",
                )
            }
            addOptional(category, "canvasHash", "Canvas SHA-256") {
                if (fp.canvasHash in WEBVIEW_SKIP_VALUES) error("skipped")
                interpretedSignal(
                    category, "canvasHash", "Canvas SHA-256",
                    SignalInterpreter.canvasFingerprint(fp.canvasHash),
                    SignalInterpreter.truncateHash(fp.canvasHash),
                    "Canvas rendering hash is a classic web fingerprint.",
                )
            }
            addOptional(category, "webglVendor", "WebGL vendor") {
                if (fp.webglVendor in WEBVIEW_SKIP_VALUES) error("skipped")
                interpretedSignal(
                    category, "webglVendor", "WebGL vendor",
                    SignalInterpreter.webglVendor(fp.webglVendor),
                    fp.webglVendor,
                    "Unmasked WebGL vendor reveals GPU stack.",
                )
            }
            addOptional(category, "webglRenderer", "WebGL renderer") {
                if (fp.webglRenderer in WEBVIEW_SKIP_VALUES) error("skipped")
                interpretedSignal(
                    category, "webglRenderer", "WebGL renderer",
                    SignalInterpreter.webglRenderer(fp.webglRenderer),
                    fp.webglRenderer,
                    "Unmasked WebGL renderer is highly identifying.",
                )
            }
            addOptional(category, "webglHash", "WebGL hash SHA-256") {
                if (fp.webglHash in WEBVIEW_SKIP_VALUES) error("skipped")
                interpretedSignal(
                    category, "webglHash", "WebGL hash SHA-256",
                    SignalInterpreter.webglHash(fp.webglHash),
                    SignalInterpreter.truncateHash(fp.webglHash),
                    "Hashed WebGL vendor/renderer pair for comparison.",
                )
            }
            addOptional(category, "timezone", "JS timezone") {
                if (fp.timezone in WEBVIEW_SKIP_VALUES) error("skipped")
                interpretedSignal(
                    category, "timezone", "JS timezone",
                    SignalInterpreter.jsTimezone(fp.timezone),
                    fp.timezone,
                    "JS-reported timezone can differ from system timezone.",
                )
            }
            addOptional(category, "languages", "Navigator languages") {
                interpretedSignal(
                    category, "languages", "Navigator languages",
                    SignalInterpreter.navigatorLanguages(fp.languages),
                    fp.languages,
                    "Browser language list is a persistent preference signal.",
                )
            }
            addOptional(category, "screenSize", "Screen size") {
                if (fp.screenSize in WEBVIEW_SKIP_VALUES) error("skipped")
                interpretedSignal(
                    category, "screenSize", "Screen size",
                    SignalInterpreter.jsScreenSize(fp.screenSize),
                    fp.screenSize,
                    "JS screen dimensions complement display metrics.",
                )
            }
            addOptional(category, "colorDepth", "Color depth") {
                interpretedSignal(
                    category, "colorDepth", "Color depth",
                    SignalInterpreter.colorDepth(fp.colorDepth),
                    fp.colorDepth,
                    "screen.colorDepth is a browser fingerprint component.",
                )
            }
            addOptional(category, "pixelRatio", "Device pixel ratio") {
                interpretedSignal(
                    category, "pixelRatio", "Device pixel ratio",
                    SignalInterpreter.devicePixelRatio(fp.pixelRatio),
                    fp.pixelRatio,
                    "devicePixelRatio links CSS pixels to physical density.",
                )
            }
            addOptional(category, "maxTouchPoints", "Max touch points") {
                interpretedSignal(
                    category, "maxTouchPoints", "Max touch points",
                    SignalInterpreter.maxTouchPoints(fp.maxTouchPoints),
                    fp.maxTouchPoints,
                    "Touch point count distinguishes phone, tablet, and desktop.",
                )
            }
            addOptional(category, "audioContextHash", "AudioContext hash") {
                if (fp.audioContextHash.isBlank() || fp.audioContextHash in WEBVIEW_SKIP_VALUES) error("skipped")
                interpretedSignal(
                    category, "audioContextHash", "AudioContext hash",
                    SignalInterpreter.audioContextFingerprint(fp.audioContextHash),
                    SignalInterpreter.truncateHash(fp.audioContextHash),
                    "OfflineAudioContext render sum is a classic audio fingerprint.",
                )
            }
        }
    }
}

/**
 * Advanced (opt-in) collector for [SignalCategory.PreviousInstallsLog].
 *
 * Demonstrates how an app can persist re-identification state across sessions by storing a
 * first-seen timestamp and an incrementing launch counter in (preferably encrypted) shared
 * preferences. Reading these on later runs shows how local persistence enables tracking.
 *
 * Privacy: **high** — a stable, self-created local identifier survives app restarts (and,
 * depending on backup settings, potentially reinstalls), acting as a re-identification aid.
 *
 * APIs used: `EncryptedSharedPreferences` (falling back to plain `SharedPreferences`).
 *
 * @see SignalCollector
 */
class PreviousInstallsLogCollector : SignalCollector {
    override val category = SignalCategory.PreviousInstallsLog

    /**
     * Reads/updates persisted install metadata and returns the resulting signals.
     *
     * On first run, seeds the first-seen timestamp; every run increments the launch counter.
     *
     * @param context Context used to open the (encrypted) shared preferences.
     * @return Signals for first-seen time, launch count and storage mechanism.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> =
        buildSignals(category) {
            val opened = runCatching { openInstallLogPrefs(context) }
            val openedPair = opened.getOrNull()
            if (openedPair == null) {
                add(
                    failedSignal(
                        category, "installLog", "Install log",
                        opened.exceptionOrNull() ?: IllegalStateException("unavailable"),
                        "Persisted install metadata can survive app lifecycle changes.",
                    ),
                )
                return@buildSignals
            }
            val (encrypted, prefs) = openedPair
                        addSafe(category, "firstSeen", "First seen timestamp") {
                val firstSeen = prefs.getLong("first_seen", 0L).let {
                    if (it == 0L) {
                        val now = System.currentTimeMillis()
                        prefs.edit().putLong("first_seen", now).apply()
                        now
                    } else {
                        it
                    }
                }
                signal(category, "firstSeen", "First seen timestamp", firstSeen.toString(),
                    "Persisted install metadata can survive app lifecycle changes.")
            }
                        addSafe(category, "launches", "Launch count") {
                val launches = prefs.getInt("launch_count", 0) + 1
                prefs.edit().putInt("launch_count", launches).apply()
                signal(category, "launches", "Launch count", launches.toString(),
                    "A stable local counter can become a re-identification aid.")
            }
                        addSafe(category, "storage", "Storage mechanism") {
                signal(category, "storage", "Storage mechanism",
                    if (encrypted) "encrypted_shared_prefs" else "shared_prefs",
                    "Persistence mechanism affects reinstall survival.")
            }
        }

    /**
     * Opens the install-log preferences, preferring [EncryptedSharedPreferences].
     *
     * Falls back to plain private [SharedPreferences] if the encrypted store cannot be
     * created (e.g. keystore issues).
     *
     * @param context Context used to build the master key and preferences.
     * @return Pair of (whether encryption is active, the preferences instance).
     */
    private fun openInstallLogPrefs(context: Context): Pair<Boolean, SharedPreferences> {
        var encrypted = false
        val prefs = runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            encrypted = true
            EncryptedSharedPreferences.create(
                context,
                "androwatch_install_log",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse {
            context.getSharedPreferences("androwatch_install_log", Context.MODE_PRIVATE)
        }
        return encrypted to prefs
    }
}
