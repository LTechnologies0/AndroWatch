package ltechnologies.onionphone.androwatch.collector.passive

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.telephony.TelephonyManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.accessibility.AccessibilityManager
import android.app.KeyguardManager
import android.app.UiModeManager
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.hardware.biometrics.BiometricManager
import ltechnologies.onionphone.androwatch.collector.LiveSignalCollector
import ltechnologies.onionphone.androwatch.collector.collectGlesFingerprint
import ltechnologies.onionphone.androwatch.collector.motionSignalsFlow
import ltechnologies.onionphone.androwatch.collector.batterySignalsFlow
import ltechnologies.onionphone.androwatch.collector.audioSignalsFlow
import kotlinx.coroutines.flow.Flow
import ltechnologies.onionphone.androwatch.collector.sha256Hex
import ltechnologies.onionphone.androwatch.collector.boundedSensorList
import ltechnologies.onionphone.androwatch.collector.probeTtsVoices
import ltechnologies.onionphone.androwatch.collector.SignalCollector
import ltechnologies.onionphone.androwatch.collector.addSafe
import ltechnologies.onionphone.androwatch.collector.addOptional
import ltechnologies.onionphone.androwatch.collector.probeAdvertisingId
import ltechnologies.onionphone.androwatch.collector.probeAccountNameHash
import ltechnologies.onionphone.androwatch.collector.probeEnabledInputMethods
import ltechnologies.onionphone.androwatch.collector.probeNfcAvailable
import ltechnologies.onionphone.androwatch.collector.probeNotificationSoundUri
import ltechnologies.onionphone.androwatch.collector.probeRingtoneUri
import ltechnologies.onionphone.androwatch.collector.probeWallpaperHash
import ltechnologies.onionphone.androwatch.collector.buildSignals
import ltechnologies.onionphone.androwatch.collector.CollectorRuntime
import ltechnologies.onionphone.androwatch.collector.RomCompatibility
import ltechnologies.onionphone.androwatch.collector.safeInt
import ltechnologies.onionphone.androwatch.collector.safeString
import ltechnologies.onionphone.androwatch.collector.interpretedSignal
import ltechnologies.onionphone.androwatch.collector.SignalInterpreter
import ltechnologies.onionphone.androwatch.collector.signal
import ltechnologies.onionphone.androwatch.collector.tagsSignal
import ltechnologies.onionphone.androwatch.model.SignalCategory
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Passive collector for [SignalCategory.DeviceIdentity].
 *
 * Gathers stable device/identity signals: device profile ([Build] fields), `ANDROID_ID`,
 * OS build fingerprint, hardware/ABI, Bluetooth name, default IME, Widevine DRM id, hardware
 * serial, product, Google Advertising ID (hashed) and enabled-IME count.
 *
 * Privacy: **high** for this category — it includes some of the strongest cross-session
 * identifiers (ANDROID_ID, Widevine id, GAID). Sensitive values are hashed and restricted
 * identifiers degrade to `blocked`/`n/a`. All reads are passive (no runtime permission).
 *
 * APIs probed: `Settings.Secure`, `Build`, `MediaDrm`, Play Services advertising id.
 *
 * @see SignalCollector
 */
class DeviceIdentityCollector : SignalCollector {
    override val category = SignalCategory.DeviceIdentity

    /**
     * Collects device-identity signals for this category.
     *
     * @param context Context used to read settings and system identifiers.
     * @return The device-identity signals.
     */
    override suspend fun collect(context: Context) = buildSignals(category) {
                addSafe(category, "profile", "Device profile") {
            val abis = Build.SUPPORTED_ABIS.toList()
            interpretedSignal(
                category, "profile", "Device profile",
                SignalInterpreter.deviceProfile(Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.DEVICE),
                "manufacturer=${Build.MANUFACTURER}; brand=${Build.BRAND}; model=${Build.MODEL}; device=${Build.DEVICE}",
                "Manufacturer + model shrink the global anonymity set to a single hardware SKU.",
                displayHint = ltechnologies.onionphone.androwatch.model.DisplayHint.KeyValue,
                entries = listOf(
                    ltechnologies.onionphone.androwatch.model.SignalEntry("Manufacturer", Build.MANUFACTURER),
                    ltechnologies.onionphone.androwatch.model.SignalEntry("Brand", Build.BRAND),
                    ltechnologies.onionphone.androwatch.model.SignalEntry("Model", Build.MODEL),
                    ltechnologies.onionphone.androwatch.model.SignalEntry("Codename", Build.DEVICE),
                ),
            )
        }
                addSafe(category, "androidId", "ANDROID_ID") {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            interpretedSignal(
                category, "androidId", "ANDROID_ID",
                SignalInterpreter.androidId(androidId),
                androidId ?: "n/a",
                "Scoped stable identifier per app signing key and user.",
            )
        }
                addSafe(category, "fingerprint", "OS build fingerprint") {
            interpretedSignal(
                category, "fingerprint", "OS build fingerprint",
                SignalInterpreter.buildFingerprint(Build.FINGERPRINT),
                Build.FINGERPRINT,
                "Full build fingerprint is a high-entropy device class signal.",
            )
        }
                addSafe(category, "hardware", "Hardware platform") {
            val abis = Build.SUPPORTED_ABIS.toList()
            interpretedSignal(
                category, "hardware", "Hardware platform",
                SignalInterpreter.hardwarePlatform(Build.HARDWARE, abis),
                "hardware=${Build.HARDWARE}; abis=${abis.joinToString()}",
                "SoC/board string plus CPU ABI list distinguish ARM variants.",
                displayHint = ltechnologies.onionphone.androwatch.model.DisplayHint.KeyValue,
                entries = abis.map { ltechnologies.onionphone.androwatch.model.SignalEntry("ABI", it) },
            )
        }
                addSafe(category, "bluetoothName", "Bluetooth name") {
            val btName = CollectorRuntime.safeSecureString(context, "bluetooth_name")
            interpretedSignal(
                category, "bluetoothName", "Bluetooth name",
                SignalInterpreter.bluetoothName(btName),
                btName ?: "n/a",
                "User-visible device name exposed via Bluetooth settings.",
            )
        }
                addSafe(category, "defaultIme", "Default keyboard") {
            interpretedSignal(
                category, "defaultIme", "Default keyboard",
                SignalInterpreter.defaultIme(
                    Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD),
                ),
                Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: "n/a",
                "Keyboard choice is a stable user preference signal.",
            )
        }
                addSafe(category, "mediaDrmId", "Widevine device ID") {
            val drmRaw = readMediaDrmId()
            interpretedSignal(
                category, "mediaDrmId", "Widevine device ID",
                SignalInterpreter.mediaDrmId(drmRaw),
                SignalInterpreter.truncateHash(drmRaw),
                "Widevine device ID persists across reinstalls without permission.",
            )
        }
                addSafe(category, "serial", "Hardware serial") {
            val serial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching { Build.getSerial() }.getOrElse { "blocked" }
            } else {
                @Suppress("DEPRECATION") Build.SERIAL
            }
            interpretedSignal(
                category, "serial", "Hardware serial",
                if (serial == "blocked" || serial == "unknown") "Serial blocked for third-party apps (API 29+)."
                else "Serial readable (unusual on retail devices).",
                serial,
                "Hardware serial is a persistent device identifier when exposed.",
            )
        }
                addSafe(category, "product", "Product name") {
            signal(category, "product", "Product name", Build.PRODUCT,
                "Build product string distinguishes OEM SKU variants.")
        }
                addOptional(category, "gaidHash", "Google Advertising ID hash") {
            val (hash, limited) = probeAdvertisingId(context)
                ?: error("Play Services advertising ID unavailable")
            signal(category, "gaidHash", "Google Advertising ID hash", hash,
                "GAID persists across apps; limitAdTracking=$limited.")
        }
                addOptional(category, "gaidLimited", "Limit ad tracking") {
            val (_, limited) = probeAdvertisingId(context)
                ?: error("Play Services advertising ID unavailable")
            signal(category, "gaidLimited", "Limit ad tracking", limited.toString(),
                "LAT flag indicates user opt-out of ad personalization.")
        }
                addOptional(category, "enabledImeCount", "Enabled keyboards count") {
            signal(category, "enabledImeCount", "Enabled keyboards count",
                probeEnabledInputMethods(context).toString(),
                "Number of enabled input methods is a user preference signal.")
        }
        appendDeviceIdentityExtras(context)
    }

    /**
     * Reads the Widevine device-unique id (hex) via [android.media.MediaDrm].
     *
     * Privacy: **high** — this is a hardware-backed identifier that can survive reinstalls;
     * returns `"unavailable"` when the DRM stack is missing or the property is blocked.
     *
     * @return Hex-encoded Widevine device id, or `"unavailable"`.
     */
    private fun readMediaDrmId(): String = runCatching {
        val widevineUuid = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)
        android.media.MediaDrm(widevineUuid).use { drm ->
            val id = drm.getPropertyByteArray(android.media.MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
            bytesToHex(id)
        }
    }.getOrElse { "unavailable" }

    /** Encodes a byte array as a lowercase hex string. */
    private fun bytesToHex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}

/**
 * Passive collector for [SignalCategory.GoogleAccount].
 *
 * Reports account authenticator types, Google-account presence, visible account count and
 * (when readable) a hash of account names, all via [AccountManager] without `GET_ACCOUNTS`.
 *
 * Privacy: account names are directly identifying and therefore hashed; without
 * `GET_ACCOUNTS` most accounts are hidden, so counts largely reflect grant state.
 *
 * APIs probed: `AccountManager.getAuthenticatorTypes`/`getAccountsByType`/`accounts`.
 *
 * @see SignalCollector
 */
class GoogleAccountCollector : SignalCollector {
    override val category = SignalCategory.GoogleAccount

    /**
     * Collects account-ecosystem signals for this category.
     *
     * @param context Context used to obtain [AccountManager].
     * @return The Google-account signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> =
        buildSignals(category) {
            val am = AccountManager.get(context)
            val types = runCatching { am.authenticatorTypes.map { it.type } }.getOrDefault(emptyList())
                        addSafe(category, "authenticatorTypes", "Authenticator types") {
                signal(category, "authenticatorTypes", "Authenticator types", types.joinToString().ifBlank { "none" },
                    "Installed account types reveal Google/enterprise account ecosystem.")
            }
                        addSafe(category, "googleAuthenticator", "Google authenticator present") {
                signal(category, "googleAuthenticator", "Google authenticator present",
                    types.any { it == "com.google" }.toString(),
                    "Google account support is a common Android profile signal.")
            }
                        addSafe(category, "accountCount", "Visible account count") {
                val count = types.sumOf { type ->
                    runCatching { am.getAccountsByType(type).size }.getOrDefault(0)
                }
                signal(category, "accountCount", "Visible account count", count.toString(),
                    "getAccountsByType is filtered without GET_ACCOUNTS; count reveals grant state.")
            }
                        addOptional(category, "accountNamesHash", "Account names hash") {
                signal(category, "accountNamesHash", "Account names hash",
                    probeAccountNameHash(context) ?: error("no accounts visible"),
                    "Hashed account names when AccountManager.accounts is readable.")
            }
        }
}

/**
 * Passive collector for [SignalCategory.SystemInfo].
 *
 * Reports OS version/patch, boot recency, device name, ADB/developer-options flags, lock
 * screen & biometric availability, form-factor flags, ROM family/spoofing hints, and
 * personalization signals (ringtone, notification sound, wallpaper hash, NFC state).
 *
 * Privacy: mostly aggregate OS/config metadata; the device name may identify the owner and
 * the wallpaper is hashed. All reads are passive (no runtime permission).
 *
 * APIs probed: `Build.VERSION`, `Settings.Global`, `KeyguardManager`, `BiometricManager`,
 * `PackageManager` features, [RomCompatibility].
 *
 * @see SignalCollector
 */
class SystemInfoCollector : SignalCollector {
    override val category = SignalCategory.SystemInfo

    /**
     * Collects OS/system-configuration signals for this category.
     *
     * @param context Context used to read settings and system services.
     * @return The system-info signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> = buildSignals(category) {
                addSafe(category, "os", "Android version") {
            interpretedSignal(
                category, "os", "Android version",
                SignalInterpreter.osVersion(Build.VERSION.SDK_INT, Build.VERSION.RELEASE, Build.VERSION.SECURITY_PATCH),
                "SDK=${Build.VERSION.SDK_INT}; release=${Build.VERSION.RELEASE}; patch=${Build.VERSION.SECURITY_PATCH}",
                "OS version narrows compatible device cohort.",
                displayHint = ltechnologies.onionphone.androwatch.model.DisplayHint.KeyValue,
                entries = listOf(
                    ltechnologies.onionphone.androwatch.model.SignalEntry("API level", Build.VERSION.SDK_INT.toString()),
                    ltechnologies.onionphone.androwatch.model.SignalEntry("Release", Build.VERSION.RELEASE ?: "n/a"),
                    ltechnologies.onionphone.androwatch.model.SignalEntry("Security patch", Build.VERSION.SECURITY_PATCH),
                    ltechnologies.onionphone.androwatch.model.SignalEntry("Incremental", Build.VERSION.INCREMENTAL),
                ),
            )
        }
                addSafe(category, "uptime", "Boot recency") {
            val uptimeMs = SystemClock.elapsedRealtime()
            interpretedSignal(
                category, "uptime", "Boot recency",
                SignalInterpreter.uptime(uptimeMs),
                formatUptime(uptimeMs),
                "Boot recency is a high-entropy session signal.",
            )
        }
                addSafe(category, "deviceName", "Device name") {
            val deviceName = CollectorRuntime.safeGlobalString(context, Settings.Global.DEVICE_NAME)
            interpretedSignal(
                category, "deviceName", "Device name",
                SignalInterpreter.deviceName(deviceName),
                deviceName ?: "n/a",
                "User-assigned device name can directly identify owner.",
            )
        }
                addSafe(category, "adbEnabled", "ADB / USB debugging") {
            val adbRaw = safeInt { Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED) }
            interpretedSignal(
                category, "adbEnabled", "ADB / USB debugging",
                SignalInterpreter.boolSetting(
                    "ADB",
                    adbRaw,
                    "USB debugging enabled — typical of developer devices.",
                    "USB debugging disabled.",
                ),
                adbRaw,
                "Developer settings reveal power-user profile.",
            )
        }
                addSafe(category, "devOptions", "Developer options") {
            val devRaw = safeInt {
                Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
            }
            interpretedSignal(
                category, "devOptions", "Developer options",
                SignalInterpreter.boolSetting(
                    "Developer options",
                    devRaw,
                    "Developer options menu enabled.",
                    "Developer options hidden/disabled.",
                ),
                devRaw,
                "Developer options enabled is a profiling signal.",
            )
        }
                addSafe(category, "deviceSecure", "Lock screen") {
            val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            interpretedSignal(
                category, "deviceSecure", "Lock screen",
                if (keyguard.isDeviceSecure) {
                    "Screen lock configured (PIN/pattern/password/biometric)."
                } else {
                    "No secure screen lock configured."
                },
                keyguard.isDeviceSecure.toString(),
                "Lock screen security configuration is a user profile signal.",
            )
        }
                addSafe(category, "biometricStrong", "Strong biometrics") {
            val strongBio = biometricStatus(context, BiometricManager.Authenticators.BIOMETRIC_STRONG)
            interpretedSignal(
                category, "biometricStrong", "Strong biometrics",
                SignalInterpreter.biometricStatus("Strong", strongBio),
                strongBio,
                "Strong biometric availability indicates hardware class.",
            )
        }
                addSafe(category, "biometricWeak", "Weak biometrics") {
            val weakBio = biometricStatus(context, BiometricManager.Authenticators.BIOMETRIC_WEAK)
            interpretedSignal(
                category, "biometricWeak", "Weak biometrics",
                SignalInterpreter.biometricStatus("Weak", weakBio),
                weakBio,
                "Weak biometric sensors may still be used for profiling.",
            )
        }
                addSafe(category, "formFactor", "Form factor flags") {
            interpretedSignal(
                category, "formFactor", "Form factor flags",
                listOf(
                    SignalInterpreter.featureFlag(
                        "telephony",
                        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY),
                    ),
                    SignalInterpreter.featureFlag(
                        "watch",
                        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH),
                    ),
                ).joinToString(" "),
                "telephony=${context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)}; " +
                    "watch=${context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)}",
                "Hardware feature flags distinguish phone, tablet, watch.",
            )
        }
                addSafe(category, "formFactorExtended", "Extended form factors") {
            val pm = context.packageManager
            interpretedSignal(
                category, "formFactorExtended", "Extended form factors",
                listOf(
                    SignalInterpreter.featureFlag("leanback_tv", pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)),
                    SignalInterpreter.featureFlag("automotive", pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)),
                    SignalInterpreter.featureFlag("pc", pm.hasSystemFeature(PackageManager.FEATURE_PC)),
                ).joinToString(" "),
                "leanback=${pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)}; " +
                    "automotive=${pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)}; " +
                    "pc=${pm.hasSystemFeature(PackageManager.FEATURE_PC)}",
                "TV, automotive, and desktop form factors narrow device class.",
            )
        }
                addSafe(category, "romFamily", "ROM / build family") {
            val rom = RomCompatibility.detectRomProfile()
            interpretedSignal(
                category, "romFamily", "ROM / build family",
                "${rom.family} (${rom.confidence} confidence) — ${rom.buildIntegrity}",
                "family=${rom.family}; variant=${rom.variant}; confidence=${rom.confidence}",
                "Custom ROM detection and build integrity hints for fingerprinting.",
                displayHint = ltechnologies.onionphone.androwatch.model.DisplayHint.KeyValue,
                entries = rom.propertyHints.entries.map {
                    ltechnologies.onionphone.androwatch.model.SignalEntry(it.key, it.value)
                },
            )
        }
                addSafe(category, "romSpoofing", "ROM spoofing hints") {
            val hints = RomCompatibility.detectRomProfile().spoofingHints
            if (hints.isEmpty()) error("no spoofing hints")
            signal(
                category, "romSpoofing", "ROM spoofing hints",
                hints.joinToString(" | "),
                "Heuristic indicators of property spoofing or non-stock builds.",
            )
        }
                addOptional(category, "ringtoneUri", "Ringtone URI") {
            signal(category, "ringtoneUri", "Ringtone URI",
                probeRingtoneUri(context) ?: error("unreadable"),
                "Default ringtone URI reveals personalization and installed sound packs.")
        }
                addOptional(category, "notificationSoundUri", "Notification sound URI") {
            signal(category, "notificationSoundUri", "Notification sound URI",
                probeNotificationSoundUri(context) ?: error("unreadable"),
                "Notification sound URI is a stable user preference signal.")
        }
                addOptional(category, "wallpaperHash", "System wallpaper hash") {
            signal(category, "wallpaperHash", "System wallpaper hash",
                probeWallpaperHash(context) ?: error("unreadable"),
                "Hashed wallpaper sample fingerprints home-screen personalization.")
        }
                addOptional(category, "nfcEnabled", "NFC enabled") {
            signal(category, "nfcEnabled", "NFC enabled",
                probeNfcAvailable(context).toString(),
                "NFC availability and state distinguish device class and usage.")
        }
        appendSystemInfoExtras(context)
    }

    /**
     * Maps [BiometricManager.canAuthenticate] to a normalized status token.
     *
     * @param context Context used to obtain [BiometricManager].
     * @param authenticators Authenticator strength bitmask (strong/weak).
     * @return Status token (`available`, `not_enrolled`, `no_hardware`, `permission_denied`, ...).
     */
    private fun biometricStatus(context: Context, authenticators: Int): String = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@runCatching "unavailable (API < 29)"
        val bm = context.getSystemService(BiometricManager::class.java) ?: return@runCatching "unavailable"
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            bm.canAuthenticate(authenticators)
        } else {
            @Suppress("DEPRECATION")
            bm.canAuthenticate()
        }
        when (result) {
            BiometricManager.BIOMETRIC_SUCCESS -> "available"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "not_enrolled"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "no_hardware"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "hw_unavailable"
            else -> "unavailable"
        }
    }.getOrElse { e ->
        when (e) {
            is SecurityException -> "permission_denied"
            else -> "unavailable"
        }
    }

    /**
     * Formats an elapsed-milliseconds uptime as `Hh Mm Ss`.
     *
     * @param ms Elapsed real time since boot in milliseconds.
     * @return Formatted uptime string.
     */
    private fun formatUptime(ms: Long): String {
        val sec = ms / 1000
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return "${h}h ${m}m ${s}s"
    }
}

/**
 * Passive collector for [SignalCategory.Display].
 *
 * Reports screen geometry (resolution, density, refresh rate), layout class, orientation,
 * night mode, display cutout/modes/rotation and system UI mode via display metrics,
 * [Configuration], [DisplayManager] and [UiModeManager].
 *
 * Privacy: display geometry is a classic fingerprint axis; no runtime permission required.
 *
 * @see SignalCollector
 */
class DisplayCollector : SignalCollector {
    override val category = SignalCategory.Display

    /**
     * Collects display/screen configuration signals for this category.
     *
     * @param context Context used to read display metrics and services.
     * @return The display signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val dm = context.resources.displayMetrics
        val config = context.resources.configuration
        val display = runCatching {
            context.getSystemService(DisplayManager::class.java)
                ?.getDisplay(Display.DEFAULT_DISPLAY)
        }.getOrNull()
        val refreshHz = display?.refreshRate
        val orientation = if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            "landscape"
        } else {
            "portrait"
        }
        val night = if ((config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
            "on"
        } else {
            "off"
        }
        val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return buildSignals(category) {
                        addSafe(category, "screen", "Screen profile") {
                interpretedSignal(
                    category, "screen", "Screen profile",
                    SignalInterpreter.displayProfile(dm.widthPixels, dm.heightPixels, dm.densityDpi, refreshHz),
                    "${dm.widthPixels}x${dm.heightPixels} @ ${dm.densityDpi} dpi",
                    "Resolution and density contribute to uniqueness.",
                    displayHint = ltechnologies.onionphone.androwatch.model.DisplayHint.KeyValue,
                    entries = listOf(
                        ltechnologies.onionphone.androwatch.model.SignalEntry("Width px", dm.widthPixels.toString()),
                        ltechnologies.onionphone.androwatch.model.SignalEntry("Height px", dm.heightPixels.toString()),
                        ltechnologies.onionphone.androwatch.model.SignalEntry("Density dpi", dm.densityDpi.toString()),
                        ltechnologies.onionphone.androwatch.model.SignalEntry("Density scale", dm.density.toString()),
                        ltechnologies.onionphone.androwatch.model.SignalEntry("Refresh Hz", refreshHz?.toString() ?: "n/a"),
                    ),
                )
            }
                        addSafe(category, "layout", "Layout class") {
                interpretedSignal(
                    category, "layout", "Layout class",
                    "Smallest width ${config.smallestScreenWidthDp} dp — " +
                        if (config.smallestScreenWidthDp >= 600) "tablet-style layout bucket." else "phone layout bucket.",
                    "smallestWidthDp=${config.smallestScreenWidthDp}",
                    "Smallest-width dp classifies tablet vs phone layout.",
                )
            }
                        addSafe(category, "orientation", "Orientation") {
                interpretedSignal(
                    category, "orientation", "Orientation",
                    SignalInterpreter.orientation(orientation),
                    orientation,
                    "Orientation preference can be session-stable.",
                )
            }
                        addSafe(category, "nightMode", "Theme") {
                interpretedSignal(
                    category, "nightMode", "Theme",
                    SignalInterpreter.nightMode(night),
                    night,
                    "Dark mode is a persistent UI preference.",
                )
            }
            if (display != null) {
                                addSafe(category, "displayCutout", "Display cutout") {
                    val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        display.cutout?.boundingRects?.size ?: 0
                    } else {
                        0
                    }
                    interpretedSignal(
                        category, "displayCutout", "Display cutout",
                        if (cutout > 0) "$cutout cutout region(s) reported." else "No display cutout reported.",
                        cutout.toString(),
                        "Notch/cutout geometry is hardware-specific.",
                    )
                }
                                addSafe(category, "displayModes", "Display modes") {
                    interpretedSignal(
                        category, "displayModes", "Display modes",
                        "${display.supportedModes.size} hardware display mode(s) exposed.",
                        display.supportedModes.joinToString { "${it.physicalWidth}x${it.physicalHeight}@${it.refreshRate}" },
                        "Number of display modes is hardware-specific.",
                    )
                }
                                addSafe(category, "rotation", "Rotation") {
                    interpretedSignal(
                        category, "rotation", "Rotation",
                        when (display.rotation) {
                            android.view.Surface.ROTATION_0 -> "Natural orientation (0°)."
                            android.view.Surface.ROTATION_90 -> "Rotated 90° clockwise."
                            android.view.Surface.ROTATION_180 -> "Upside down (180°)."
                            android.view.Surface.ROTATION_270 -> "Rotated 270° clockwise."
                            else -> "Rotation code ${display.rotation}."
                        },
                        display.rotation.toString(),
                        "Current display rotation.",
                    )
                }
            }
                        addSafe(category, "uiMode", "System UI mode") {
                interpretedSignal(
                    category, "uiMode", "System UI mode",
                    SignalInterpreter.uiModeType(uiMode.currentModeType),
                    uiMode.currentModeType.toString(),
                    "Car/TV/desk mode reveals device usage context.",
                )
            }
            appendDisplayExtras(context, config, display)
        }
    }
}

/**
 * Passive collector for [SignalCategory.Locale].
 *
 * Reports the default locale, full locale list, timezone and UTC offset via
 * [Configuration.getLocales], [Locale] and [TimeZone].
 *
 * Privacy: language/region and timezone form a regional fingerprint; no permission required.
 *
 * @see SignalCollector
 */
class LocaleCollector : SignalCollector {
    override val category = SignalCategory.Locale

    /**
     * Collects locale/timezone signals for this category.
     *
     * @param context Context used to read configured locales.
     * @return The locale signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val locales = context.resources.configuration.locales
        val config = context.resources.configuration
        val tags = (0 until locales.size()).map { locales[it].toLanguageTag() }
        return buildSignals(category) {
                        addSafe(category, "locale", "Default locale") {
                signal(category, "locale", "Default locale", Locale.getDefault().toLanguageTag(),
                    "Language and region are persistent behavior hints.")
            }
                        addSafe(category, "locales", "Locale list") {
                signal(category, "locales", "Locale list", tags.joinToString(),
                    "Multiple locales narrow cultural and regional profile.")
            }
                        addSafe(category, "timezone", "Time zone") {
                signal(category, "timezone", "Time zone", TimeZone.getDefault().id,
                    "Timezone narrows geographic profile.")
            }
                        addSafe(category, "utcOffset", "UTC offset") {
                signal(category, "utcOffset", "UTC offset", TimeZone.getDefault().rawOffset.toString(),
                    "UTC offset complements timezone fingerprint.")
            }
            appendLocaleExtras(config)
        }
    }
}

/**
 * Passive collector for [SignalCategory.Accessibility].
 *
 * Reports font scale, accessibility-enabled/touch-exploration flags, enabled-service count,
 * display inversion and color-correction (daltonizer) via [AccessibilityManager] and
 * `Settings.Secure`.
 *
 * Privacy: accessibility usage can reveal assistive-technology needs (health-adjacent) and
 * is behaviorally identifying; the enabled-service list is counted, not exposed. No
 * runtime permission required.
 *
 * @see SignalCollector
 */
class AccessibilityCollector : SignalCollector {
    override val category = SignalCategory.Accessibility

    /**
     * Collects accessibility-configuration signals for this category.
     *
     * @param context Context used to obtain [AccessibilityManager] and secure settings.
     * @return The accessibility signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = CollectorRuntime.safeSecureString(
            context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: ""
        return buildSignals(category) {
                        addSafe(category, "fontScale", "Font scale") {
                signal(category, "fontScale", "Font scale", context.resources.configuration.fontScale.toString(),
                    "Accessibility font scaling is a stable user trait.")
            }
                        addSafe(category, "enabled", "Accessibility enabled") {
                signal(category, "enabled", "Accessibility enabled", am.isEnabled.toString(),
                    "Accessibility usage is a behavioral fingerprint.")
            }
                        addSafe(category, "touchExploration", "Touch exploration") {
                signal(category, "touchExploration", "Touch exploration", am.isTouchExplorationEnabled.toString(),
                    "TalkBack-like exploration mode is highly identifying.")
            }
                        addSafe(category, "enabledServices", "Enabled services count") {
                signal(category, "enabledServices", "Enabled services count",
                    enabledServices.split(':').filter { it.isNotBlank() }.size.toString(),
                    "Number and type of accessibility services reveal user needs.")
            }
                        addSafe(category, "displayInversion", "Display inversion") {
                signal(category, "displayInversion", "Display inversion",
                    safeInt { Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED) },
                    "Color inversion is an accessibility preference signal.")
            }
                        addSafe(category, "daltonizer", "Color correction") {
                signal(category, "daltonizer", "Color correction",
                    safeInt { Settings.Secure.getInt(context.contentResolver, "accessibility_display_daltonizer_enabled") },
                    "Color-blind correction mode is an accessibility preference signal.")
            }
            appendAccessibilityExtras(context)
        }
    }
}

/**
 * Passive live collector for [SignalCategory.DeviceMotion].
 *
 * Snapshot [collect] reports the sensor inventory (count, presence of accelerometer/gyro/
 * magnetometer/step-counter, type histogram, sample names, accelerometer resolution) via
 * [SensorManager]. [liveFlow] streams live accelerometer readings ([motionSignalsFlow]).
 *
 * Privacy: sensor *metadata* is a hardware fingerprint; live readings are high-entropy
 * session signals. No runtime permission is required for these sensors.
 *
 * APIs probed: `SensorManager.getSensorList`/`getDefaultSensor`, `SensorEventListener`.
 *
 * @see LiveSignalCollector
 */
class DeviceMotionCollector : LiveSignalCollector {
    override val category = SignalCategory.DeviceMotion

    /**
     * Collects the static sensor-inventory signals for this category.
     *
     * @param context Context used to obtain [SensorManager].
     * @return The device-motion inventory signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = boundedSensorList(sm)
        val typeCounts = sensors.groupBy { it.type }.mapValues { it.value.size }
        val names = sensors.take(8).joinToString { "${it.name} (${it.vendor})" }
        return buildSignals(category) {
                        addSafe(category, "sensorCount", "Sensor count") {
                signal(category, "sensorCount", "Sensor count", sensors.size.toString(),
                    "Sensor inventory differs by device SKU.")
            }
                        addSafe(category, "accelerometer", "Accelerometer") {
                signal(category, "accelerometer", "Accelerometer",
                    (sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null).toString(),
                    "Motion sensor presence is hardware-specific.")
            }
                        addSafe(category, "gyroscope", "Gyroscope") {
                signal(category, "gyroscope", "Gyroscope",
                    (sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null).toString(),
                    "Gyroscope presence varies by price tier.")
            }
                        addSafe(category, "magnetometer", "Magnetometer") {
                signal(category, "magnetometer", "Magnetometer",
                    (sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null).toString(),
                    "Magnetometer availability is a hardware signal.")
            }
                        addSafe(category, "stepCounter", "Step counter present") {
                signal(category, "stepCounter", "Step counter present",
                    (sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null).toString(),
                    "Step counter hardware indicates fitness-capable device.")
            }
                        addSafe(category, "typeCounts", "Sensor type counts") {
                signal(category, "typeCounts", "Sensor type counts", typeCounts.entries.joinToString { "${it.key}=${it.value}" },
                    "Sensor type distribution is a hardware fingerprint.")
            }
                        addSafe(category, "sample", "Sensor sample") {
                signal(category, "sample", "Sensor sample", names.ifBlank { "none" },
                    "Sensor vendor strings can be device-unique.")
            }
                        addSafe(category, "accelResolution", "Accelerometer resolution") {
                val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                signal(category, "accelResolution", "Accelerometer resolution",
                    accel?.resolution?.toString() ?: "n/a",
                    "Sensor resolution and range are hardware fingerprint components.")
            }
            appendDeviceMotionExtras(sm)
        }
    }

    /**
     * Streams live accelerometer signals.
     *
     * @param context Context used to register the sensor listener.
     * @return Flow of live motion signal snapshots.
     */
    override fun liveFlow(context: Context): Flow<List<ltechnologies.onionphone.androwatch.model.FingerprintSignal>> =
        motionSignalsFlow(context)
}

/**
 * Passive live collector for [SignalCategory.Battery].
 *
 * Snapshot [collect] reports level, charging/status, temperature, health, voltage, power-save
 * and thermal status via [BatteryManager], the sticky battery intent and [PowerManager].
 * [liveFlow] streams live battery updates ([batterySignalsFlow]).
 *
 * Privacy: battery level/temperature drift can correlate sessions across apps; no runtime
 * permission is required.
 *
 * APIs probed: `ACTION_BATTERY_CHANGED` sticky intent, `BatteryManager`, `PowerManager`.
 *
 * @see LiveSignalCollector
 */
class BatteryCollector : LiveSignalCollector {
    override val category = SignalCategory.Battery

    /**
     * Collects the static battery-state signals for this category.
     *
     * @param context Context used to read the battery intent and services.
     * @return The battery signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val temp = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val health = sticky?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val voltageMv = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val pct = if (scale > 0) (level * 100 / scale) else bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return buildSignals(category) {
                        addSafe(category, "level", "Battery level") { signal(category, "level", "Battery level", "$pct%", "Battery level and charging state can be used for short-term tracking.") }
                        addSafe(category, "charging", "Charging") { signal(category, "charging", "Charging", bm.isCharging.toString(), "Charging state is a session-level signal.") }
                        addSafe(category, "status", "Status code") { signal(category, "status", "Status code", status.toString(), "Battery status code reveals charging/ discharging state.") }
                        addSafe(category, "temperature", "Temperature") { signal(category, "temperature", "Temperature", "${temp / 10.0}°C", "Battery temperature drifts with usage and environment.") }
                        addSafe(category, "health", "Battery health") {
                signal(category, "health", "Battery health", batteryHealthName(health),
                    "Battery health code reflects cell degradation state.")
            }
                        addSafe(category, "voltage", "Voltage") {
                signal(category, "voltage", "Voltage",
                    if (voltageMv > 0) "${voltageMv / 1000.0}V" else "n/a",
                    "Battery voltage drifts with charge level and temperature.")
            }
                        addSafe(category, "powerSave", "Power save mode") { signal(category, "powerSave", "Power save mode", pm.isPowerSaveMode.toString(), "Battery saver mode reveals usage patterns.") }
                        addSafe(category, "thermal", "Thermal status") {
                signal(category, "thermal", "Thermal status",
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) pm.currentThermalStatus.toString() else "n/a",
                    "Thermal throttling state correlates with workload.")
            }
            appendBatteryExtras(context, bm, sticky)
        }
    }

    /**
     * Streams live battery signals.
     *
     * @param context Context used to register the battery receiver.
     * @return Flow of live battery signal snapshots.
     */
    override fun liveFlow(context: Context): Flow<List<ltechnologies.onionphone.androwatch.model.FingerprintSignal>> =
        batterySignalsFlow(context)

    /** Maps a `BatteryManager.BATTERY_HEALTH_*` constant to a short label. */
    private fun batteryHealthName(health: Int) = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "cold"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
        else -> if (health < 0) "n/a" else "code-$health"
    }
}

/**
 * Passive collector for [SignalCategory.Storage].
 *
 * Reports total/free storage, volume count and removable-volume count via [StatFs] and
 * [StorageManager].
 *
 * Privacy: storage tier and free-space ratio narrow device cohort and usage habits; no
 * runtime permission required.
 *
 * @see SignalCollector
 */
class StorageCollector : SignalCollector {
    override val category = SignalCategory.Storage

    /**
     * Collects storage-capacity/volume signals for this category.
     *
     * @param context Context used to obtain [StorageManager].
     * @return The storage signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalGb = stat.totalBytes / (1024.0 * 1024.0 * 1024.0)
        val freeGb = stat.availableBytes / (1024.0 * 1024.0 * 1024.0)
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val volumes = sm.storageVolumes
        return buildSignals(category) {
                        addSafe(category, "total", "Total storage") { signal(category, "total", "Total storage", String.format("%.1f GB", totalGb), "Storage size can reveal device tier.") }
                        addSafe(category, "free", "Free storage") { signal(category, "free", "Free storage", String.format("%.1f GB", freeGb), "Free space drifts with user behavior.") }
                        addSafe(category, "volumeCount", "Storage volumes") { signal(category, "volumeCount", "Storage volumes", volumes.size.toString(), "Number of storage volumes indicates SD card presence.") }
                        addSafe(category, "removable", "Removable volumes") {
                signal(category, "removable", "Removable volumes", volumes.count { it.isRemovable }.toString(),
                    "Removable storage configuration is device-specific.")
            }
            appendStorageExtras(context, stat, volumes)
        }
    }
}

/**
 * Passive collector for [SignalCategory.Network].
 *
 * Reports active transports, internet-validated/metered flags, interface name, DNS server
 * count, local addresses, transport info (Wi-Fi metadata) and private-DNS state via
 * [ConnectivityManager] link properties/capabilities.
 *
 * Privacy: local IPs and Wi-Fi metadata reveal the network environment; readable with only
 * `ACCESS_NETWORK_STATE` (a normal permission), with MAC addresses redacted since Android 10.
 *
 * @see SignalCollector
 */
class NetworkCollector : SignalCollector {
    override val category = SignalCategory.Network

    /**
     * Collects active-network characteristic signals for this category.
     *
     * @param context Context used to obtain [ConnectivityManager].
     * @return The network signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val link = network?.let { cm.getLinkProperties(it) }
        val transports = buildList {
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) add("wifi")
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) add("cellular")
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) add("vpn")
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) add("ethernet")
        }
        return buildSignals(category) {
                        addSafe(category, "transports", "Active transports") { signal(category, "transports", "Active transports", transports.joinToString().ifBlank { "none" }, "Network transport mix can correlate sessions.") }
                        addSafe(category, "validated", "Internet validated") {
                signal(category, "validated", "Internet validated",
                    (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true).toString(),
                    "Validated internet access is a connectivity state signal.")
            }
                        addSafe(category, "metered", "Metered") {
                signal(category, "metered", "Metered",
                    runCatching { cm.isActiveNetworkMetered }.getOrDefault(false).toString(),
                    "Metered connection indicates mobile or capped Wi-Fi.")
            }
                        addSafe(category, "interface", "Interface") { signal(category, "interface", "Interface", link?.interfaceName ?: "n/a", "Network interface name reveals connection type.") }
                        addSafe(category, "dnsCount", "DNS servers") { signal(category, "dnsCount", "DNS servers", (link?.dnsServers?.size ?: 0).toString(), "DNS server count and choice can be environment-specific.") }
                        addSafe(category, "localAddresses", "Local addresses") {
                signal(category, "localAddresses", "Local addresses",
                    link?.linkAddresses?.joinToString { it.address.hostAddress ?: "?" } ?: "none",
                    "Local IP addresses can identify network environment.")
            }
                        addSafe(category, "transportInfo", "Transport info") {
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) caps?.transportInfo else null
                val summary = when (info) {
                    is WifiInfo -> "wifi; freq=${info.frequency}MHz; rssi=${info.rssi}"
                    null -> "none"
                    else -> info.javaClass.simpleName
                }
                signal(category, "transportInfo", "Transport info", summary,
                    "NetworkCapabilities transport info exposes Wi-Fi metadata (MAC redacted API 29+).")
            }
                        addSafe(category, "privateDns", "Private DNS") {
                val active = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    link?.isPrivateDnsActive == true
                } else {
                    false
                }
                signal(category, "privateDns", "Private DNS", active.toString(),
                    "Private DNS configuration is a network preference signal.")
            }
            appendNetworkExtras(context, cm)
        }
    }
}

/**
 * Passive collector for [SignalCategory.Fonts].
 *
 * Reports the system font count (Android 10+), a hashed sample of font file names, generic
 * typeface probes and default-typeface mapping via [android.graphics.fonts.SystemFonts] and
 * [Typeface].
 *
 * Privacy: the system font inventory is a web-style fingerprint vector; font file names are
 * hashed. No runtime permission required.
 *
 * @see SignalCollector
 */
class FontsCollector : SignalCollector {
    override val category = SignalCategory.Fonts

    /**
     * Collects font-inventory signals for this category.
     *
     * @param context Context (used only for API-level-gated system font access).
     * @return The font signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val systemFontCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { android.graphics.fonts.SystemFonts.getAvailableFonts().size }.getOrDefault(-1)
        } else {
            -1
        }
        val fontNamesHash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                android.graphics.fonts.SystemFonts.getAvailableFonts()
                    .take(12)
                    .joinToString { it.file?.name ?: it.toString() }
                    .let { sha256Hex(it) }
            }.getOrElse { "unavailable" }
        } else {
            "unavailable (API < 29)"
        }
        val typefaceProbe = listOf("sans-serif", "serif", "monospace", "casual", "cursive")
            .joinToString { fam ->
                val tf = Typeface.create(fam, Typeface.NORMAL)
                "$fam=${tf == Typeface.DEFAULT}"
            }
        return buildSignals(category) {
                        addSafe(category, "systemFontCount", "System font count") {
                signal(category, "systemFontCount", "System font count",
                    if (systemFontCount >= 0) systemFontCount.toString() else "unavailable (API < 29)",
                    "System font inventory is a web-style fingerprint vector.")
            }
                        addSafe(category, "probes", "Typeface probes") {
                tagsSignal(category, "probes", "Typeface probes",
                    listOf("sans-serif", "serif", "monospace", "casual", "cursive"),
                    "Font fallback behavior varies by OEM customization.")
            }
                        addSafe(category, "fontNamesHash", "System font names hash") {
                signal(category, "fontNamesHash", "System font names hash", fontNamesHash,
                    "Hashed sample of system font file names is OEM-specific.")
            }
                        addSafe(category, "typefaceProbe", "Typeface default mapping") {
                signal(category, "typefaceProbe", "Typeface default mapping", typefaceProbe,
                    "Whether generic families map to Typeface.DEFAULT reveals OEM font stack.")
            }
        }
    }
}

/**
 * Passive collector for [SignalCategory.InstalledVoices].
 *
 * Reports the installed TTS engine count and labels, the default engine and the set of
 * installed voice locales via `PackageManager.queryIntentServices`, `Settings.Secure` and a
 * short-lived [TextToSpeech] probe.
 *
 * Privacy: the installed-voice/engine inventory expands the speech fingerprint surface;
 * individual voice names are not enumerated. No runtime permission required.
 *
 * @see SignalCollector
 */
class TtsVoicesCollector : SignalCollector {
    override val category = SignalCategory.InstalledVoices

    /**
     * Collects text-to-speech engine/voice signals for this category.
     *
     * @param context Context used to query TTS services and initialize the probe engine.
     * @return The installed-voices signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val engines = context.packageManager.queryIntentServices(
            Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        val labels = engines.joinToString { info ->
            "${info.loadLabel(context.packageManager)} (${info.serviceInfo.packageName})"
        }
        val defaultEngine = CollectorRuntime.safeSecureString(context, "tts_default_synth") ?: "none"
        val voices = try {
            probeTtsVoices(context)
        } catch (_: Exception) {
            emptyList()
        }
        val locales = voices.mapNotNull { entry ->
            entry.substringBefore(':', missingDelimiterValue = entry).takeIf { it.isNotBlank() }
        }.distinct().sorted()
        return buildSignals(category) {
                        addSafe(category, "engineCount", "TTS engine count") { signal(category, "engineCount", "TTS engine count", engines.size.toString(), "Number of speech engines varies by OEM and user installs.") }
                        addSafe(category, "engines", "TTS engines") { signal(category, "engines", "TTS engines", labels.ifBlank { "none" }, "Voice engine set may vary by OEM and user choices.") }
                        addSafe(category, "defaultEngine", "Default engine") { signal(category, "defaultEngine", "Default engine", defaultEngine, "Default TTS engine is a stable preference.") }
                        addSafe(category, "voiceLocaleCount", "Voice locale count") {
                signal(category, "voiceLocaleCount", "Voice locale count",
                    if (voices.isEmpty()) "unavailable" else locales.size.toString(),
                    "Installed TTS voice locales expand speech fingerprint surface.")
            }
                        addSafe(category, "voiceLocales", "Voice locale sample") {
                signal(category, "voiceLocales", "Voice locale sample",
                    if (locales.isEmpty()) "unavailable" else locales.take(8).joinToString(),
                    "Sample of TTS voice locales without enumerating every voice name.")
            }
            appendTtsExtras(voices)
        }
    }
}

/**
 * Passive collector for [SignalCategory.AppInfo].
 *
 * Reports this app's own version name/code, target SDK, first-install/last-update timestamps
 * and install source via `PackageManager.getPackageInfo`/`getInstallSourceInfo`.
 *
 * Privacy: install timestamps can act as long-lived per-install markers and the install
 * source distinguishes Play Store vs sideload; all reads concern the app itself and need no
 * runtime permission.
 *
 * @see SignalCollector
 */
class AppInfoCollector : SignalCollector {
    override val category = SignalCategory.AppInfo

    /**
     * Collects this app's package-metadata signals for this category.
     *
     * @param context Context used to read package info and install source.
     * @return The app-info signals (empty if package info is unavailable).
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val pkgInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            ?: return buildSignals(category) { }
        val pkg = pkgInfo
        val appInfo = context.applicationInfo
        val installSource = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            }.getOrNull()
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
        return buildSignals(category) {
                        addSafe(category, "versionName", "Version name") { signal(category, "versionName", "Version name", pkg.versionName ?: "n/a", "App build differences can split users.") }
                        addSafe(category, "versionCode", "Version code") {
                signal(category, "versionCode", "Version code",
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkg.longVersionCode.toString()
                    else @Suppress("DEPRECATION") pkg.versionCode.toString(),
                    "Version code tracks build iteration.")
            }
                        addSafe(category, "targetSdk", "Target SDK") { signal(category, "targetSdk", "Target SDK", appInfo.targetSdkVersion.toString(), "Target SDK reveals app compatibility posture.") }
                        addSafe(category, "firstInstall", "First install") { signal(category, "firstInstall", "First install", pkg.firstInstallTime.toString(), "Install timestamp can act as a long-lived marker.") }
                        addSafe(category, "lastUpdate", "Last update") { signal(category, "lastUpdate", "Last update", pkg.lastUpdateTime.toString(), "Update timestamp tracks app maintenance.") }
                        addSafe(category, "installer", "Installer package") { signal(category, "installer", "Installer package", installSource ?: "unknown", "Install source reveals Play Store vs sideload profile.") }
            appendAppInfoExtras(context)
        }
    }
}

/**
 * Passive collector for [SignalCategory.Pasteboard].
 *
 * Reports only clipboard *metadata* — whether a primary clip exists, its MIME types, its
 * label and the sensitive-content flag (Android 13+) — via [android.content.ClipboardManager]
 * and [android.content.ClipDescription]. It never reads clipboard contents.
 *
 * Privacy: clip presence/timing and MIME types hint at user workflow; the actual clipboard
 * text is intentionally not accessed. No runtime permission required.
 *
 * @see SignalCollector
 */
class ClipboardCollector : SignalCollector {
    override val category = SignalCategory.Pasteboard

    /**
     * Collects clipboard-metadata signals for this category.
     *
     * @param context Context used to obtain [android.content.ClipboardManager].
     * @return The clipboard-metadata signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val hasClip = runCatching { clipboard.hasPrimaryClip() }.getOrDefault(false)
        val description = runCatching { clipboard.primaryClipDescription }.getOrNull()
        val mimeTypes = description?.let { desc ->
            (0 until desc.mimeTypeCount).map { desc.getMimeType(it) }
        } ?: emptyList()
        val sensitive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && description != null) {
            runCatching {
                description.extras?.getBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE) == true
            }.getOrDefault(false)
        } else {
            false
        }
        return buildSignals(category) {
                        addSafe(category, "hasClip", "Has primary clip") { signal(category, "hasClip", "Has primary clip", hasClip.toString(), "Clipboard presence reveals copy activity timing.") }
                        addSafe(category, "mimeTypes", "MIME types") { signal(category, "mimeTypes", "MIME types", mimeTypes.joinToString().ifBlank { "none" }, "Clipboard content types reveal workflow without reading content.") }
                        addSafe(category, "label", "Clip label") { signal(category, "label", "Clip label", description?.label?.toString() ?: "none", "Clip labels can hint at copied content category.") }
                        addSafe(category, "isSensitive", "Sensitive clip flag") {
                signal(category, "isSensitive", "Sensitive clip flag", sensitive.toString(),
                    "EXTRA_IS_SENSITIVE (API 33+) marks clipboard content as sensitive.")
            }
        }
    }
}

/**
 * Passive live collector for [SignalCategory.Audio].
 *
 * Snapshot [collect] reports output/input device counts and types, ringer mode, music-active
 * state, speakerphone/Bluetooth-SCO routing and per-stream volumes via [AudioManager].
 * [liveFlow] streams audio-routing changes ([audioSignalsFlow]).
 *
 * Privacy: audio device types can reveal connected accessories and volume/ringer settings are
 * user preferences; no runtime permission is required for this metadata.
 *
 * @see LiveSignalCollector
 */
class AudioRouteCollector : LiveSignalCollector {
    override val category = SignalCategory.Audio

    /**
     * Collects the static audio-routing/state signals for this category.
     *
     * @param context Context used to obtain [AudioManager].
     * @return The audio signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val inputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val deviceSummary = outputs.joinToString { deviceTypeName(it.type) }
        return buildSignals(category) {
                        addSafe(category, "outputCount", "Output devices") { signal(category, "outputCount", "Output devices", outputs.size.toString(), "Number of audio outputs varies by connections.") }
                        addSafe(category, "inputCount", "Input devices") {
                signal(category, "inputCount", "Input devices", inputs.size.toString(),
                    "Microphone/input device count is hardware-specific.")
            }
                        addSafe(category, "outputs", "Output types") { signal(category, "outputs", "Output types", deviceSummary.ifBlank { "none" }, "Connected audio device types can reveal accessories.") }
                        addSafe(category, "ringerMode", "Ringer mode") { signal(category, "ringerMode", "Ringer mode", ringerModeName(am.ringerMode), "Ringer mode is a user preference signal.") }
                        addSafe(category, "musicActive", "Music active") { signal(category, "musicActive", "Music active", am.isMusicActive.toString(), "Media playback state is a session signal.") }
                        addSafe(category, "speakerphone", "Speakerphone") { signal(category, "speakerphone", "Speakerphone", am.isSpeakerphoneOn.toString(), "Speakerphone state reveals call/audio routing.") }
                        addSafe(category, "btSco", "Bluetooth SCO") { signal(category, "btSco", "Bluetooth SCO", am.isBluetoothScoOn.toString(), "Bluetooth SCO indicates headset call routing.") }
                        addSafe(category, "musicVolume", "Music stream volume") {
                val musicVol = runCatching { am.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrNull()
                signal(category, "musicVolume", "Music stream volume",
                    musicVol?.toString() ?: "unavailable",
                    "Volume levels per stream are user preference signals.")
            }
                        addSafe(category, "ringVolume", "Ring stream volume") {
                signal(category, "ringVolume", "Ring stream volume",
                    runCatching { am.getStreamVolume(AudioManager.STREAM_RING) }.getOrNull()?.toString() ?: "unavailable",
                    "Ring volume is a user preference signal.")
            }
                        addSafe(category, "alarmVolume", "Alarm stream volume") {
                signal(category, "alarmVolume", "Alarm stream volume",
                    runCatching { am.getStreamVolume(AudioManager.STREAM_ALARM) }.getOrNull()?.toString() ?: "unavailable",
                    "Alarm volume is a user preference signal.")
            }
                        addSafe(category, "notificationVolume", "Notification stream volume") {
                signal(category, "notificationVolume", "Notification stream volume",
                    runCatching { am.getStreamVolume(AudioManager.STREAM_NOTIFICATION) }.getOrNull()?.toString()
                        ?: "unavailable",
                    "Notification volume is a user preference signal.")
            }
            appendAudioExtras(context, outputs)
        }
    }

    /** Maps an [AudioDeviceInfo] type constant to a short device label. */
    private fun deviceTypeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bt-a2dp"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bt-sco"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "usb"
        else -> "type-$type"
    }

    /** Maps an [AudioManager] ringer-mode constant to a short label. */
    private fun ringerModeName(mode: Int) = when (mode) {
        AudioManager.RINGER_MODE_SILENT -> "silent"
        AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
        else -> "normal"
    }

    /**
     * Streams live audio-routing signals.
     *
     * @param context Context used to observe audio device changes.
     * @return Flow of live audio signal snapshots.
     */
    override fun liveFlow(context: Context): Flow<List<ltechnologies.onionphone.androwatch.model.FingerprintSignal>> =
        audioSignalsFlow(context)
}

/**
 * Passive collector for [SignalCategory.Graphics].
 *
 * Reports the graphics hardware platform, board, SoC manufacturer/model ([Build]) and the
 * OpenGL ES version, GL renderer/vendor and hashed GL extension list via
 * [collectGlesFingerprint].
 *
 * Privacy: GPU renderer/vendor strings are among the strongest passive fingerprint signals;
 * no runtime permission is required to read them.
 *
 * @see SignalCollector
 */
class GpuCollector : SignalCollector {
    override val category = SignalCategory.Graphics

    /**
     * Collects graphics/GPU signals for this category.
     *
     * @param context Context used to obtain the GL context for [collectGlesFingerprint].
     * @return The graphics signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> =
        buildSignals(category) {
            val gles = runCatching { collectGlesFingerprint(context) }.getOrNull()
                        addSafe(category, "hardware", "Hardware platform") { signal(category, "hardware", "Hardware platform", Build.HARDWARE, "Graphics hardware platform string is high-entropy.") }
                        addSafe(category, "board", "Board") { signal(category, "board", "Board", Build.BOARD, "Board name identifies SoC platform.") }
                        addSafe(category, "socManufacturer", "SoC manufacturer") {
                signal(category, "socManufacturer", "SoC manufacturer",
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER else "n/a",
                    "SoC vendor is a hardware fingerprint component.")
            }
                        addSafe(category, "socModel", "SoC model") {
                signal(category, "socModel", "SoC model",
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else "n/a",
                    "SoC model precisely identifies chipset.")
            }
                        addSafe(category, "glEsVersion", "OpenGL ES version") {
                val g = gles ?: error("gles unavailable")
                signal(category, "glEsVersion", "OpenGL ES version", g.glEsVersion, "GLES version is part of the graphics fingerprint.")
            }
                        addSafe(category, "glRenderer", "GL renderer") {
                val g = gles ?: error("gles unavailable")
                signal(category, "glRenderer", "GL renderer", g.renderer, "GPU renderer string is a classic web/GL fingerprint.")
            }
                        addSafe(category, "glVendor", "GL vendor") {
                val g = gles ?: error("gles unavailable")
                signal(category, "glVendor", "GL vendor", g.vendor, "GPU vendor narrows graphics hardware.")
            }
                        addSafe(category, "glExtensionsHash", "GL extensions hash") {
                val g = gles ?: error("gles unavailable")
                signal(category, "glExtensionsHash", "GL extensions hash", g.extensionsHash, "Extension list hash captures GPU capability surface.")
            }
            appendGraphicsExtras(context, gles)
        }
}

/**
 * Passive collector for [SignalCategory.Telephony].
 *
 * Reports network/SIM operator names and country codes, SIM state, phone type, data network
 * type, active modem count and whether the IMEI is readable via [TelephonyManager].
 *
 * Privacy: carrier/SIM operator and country codes are moderately identifying. Restricted
 * identifiers (IMEI) surface only as `readable`/`blocked` rather than the raw value, and no
 * `READ_PHONE_STATE` permission is used.
 *
 * @see SignalCollector
 */
class TelephonyCollector : SignalCollector {
    override val category = SignalCategory.Telephony

    /**
     * Collects telephony/carrier signals for this category.
     *
     * @param context Context used to obtain [TelephonyManager].
     * @return The telephony signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return buildSignals(category) {
                        addSafe(category, "operator", "Network operator") { signal(category, "operator", "Network operator", tm.networkOperatorName ?: "n/a", "Carrier name narrows region and plan type.") }
                        addSafe(category, "simOperator", "SIM operator") { signal(category, "simOperator", "SIM operator", tm.simOperatorName ?: "n/a", "SIM operator can differ from network operator.") }
                        addSafe(category, "simCountry", "SIM country") { signal(category, "simCountry", "SIM country", tm.simCountryIso ?: "n/a", "SIM country ISO narrows geographic profile.") }
                        addSafe(category, "networkCountry", "Network country") { signal(category, "networkCountry", "Network country", tm.networkCountryIso ?: "n/a", "Network country from cell registration.") }
                        addSafe(category, "simState", "SIM state") { signal(category, "simState", "SIM state", simStateName(tm.simState), "SIM state reveals device telephony configuration.") }
                        addSafe(category, "phoneType", "Phone type") { signal(category, "phoneType", "Phone type", tm.phoneType.toString(), "Phone type (GSM/CDMA/none) is hardware-specific.") }
                        addSafe(category, "dataNetwork", "Data network type") { signal(category, "dataNetwork", "Data network type", safeString { tm.dataNetworkType.toString() }, "Radio access technology is a session fingerprint.") }
                        addSafe(category, "modemCount", "Active modem count") {
                signal(category, "modemCount", "Active modem count",
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) tm.activeModemCount.toString() else "n/a",
                    "Dual-SIM capability is a device trait.")
            }
                        addSafe(category, "imeiStatus", "IMEI access") {
                val imei = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    runCatching { tm.imei }.getOrElse { "blocked" }
                } else {
                    @Suppress("DEPRECATION")
                    runCatching { tm.deviceId }.getOrElse { "blocked" }
                }
                signal(category, "imeiStatus", "IMEI access",
                    if (imei == "blocked" || imei.isNullOrBlank()) "blocked" else "readable",
                    "IMEI is blocked for third-party apps API 29+; readability is a privilege signal.")
            }
            appendTelephonyExtras(tm)
        }
    }

    /** Maps a `TelephonyManager.SIM_STATE_*` constant to a short label. */
    private fun simStateName(state: Int) = when (state) {
        TelephonyManager.SIM_STATE_ABSENT -> "absent"
        TelephonyManager.SIM_STATE_READY -> "ready"
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> "pin_required"
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> "puk_required"
        else -> "state-$state"
    }
}
