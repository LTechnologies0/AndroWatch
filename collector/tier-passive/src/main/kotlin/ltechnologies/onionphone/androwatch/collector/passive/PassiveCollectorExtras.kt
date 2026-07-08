package ltechnologies.onionphone.androwatch.collector.passive

import android.app.UiModeManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.Display
import ltechnologies.onionphone.androwatch.collector.addOptional
import ltechnologies.onionphone.androwatch.collector.addSafe
import ltechnologies.onionphone.androwatch.collector.forEachProbe
import ltechnologies.onionphone.androwatch.collector.boundedSensorList
import ltechnologies.onionphone.androwatch.collector.probeAccessibilitySettings
import ltechnologies.onionphone.androwatch.collector.probeAudioExtended
import ltechnologies.onionphone.androwatch.collector.probeBatteryExtended
import ltechnologies.onionphone.androwatch.collector.probeBluetoothPassive
import ltechnologies.onionphone.androwatch.collector.probeCameraInventory
import ltechnologies.onionphone.androwatch.collector.probeCodecInventory
import ltechnologies.onionphone.androwatch.collector.probeDefaultApps
import ltechnologies.onionphone.androwatch.collector.probeDrmId
import ltechnologies.onionphone.androwatch.collector.probeDynamicSensorCount
import ltechnologies.onionphone.androwatch.collector.probeEnabledImeHash
import ltechnologies.onionphone.androwatch.collector.probeExtendedFeatureFlags
import ltechnologies.onionphone.androwatch.collector.probeExtendedSensorPresence
import ltechnologies.onionphone.androwatch.collector.probeGlobalSettingsProfile
import ltechnologies.onionphone.androwatch.collector.probeInputDevices
import ltechnologies.onionphone.androwatch.collector.probeMiscHardware
import ltechnologies.onionphone.androwatch.collector.probeNetworkExtended
import ltechnologies.onionphone.androwatch.collector.probeRuntimeMemory
import ltechnologies.onionphone.androwatch.collector.probeSdkExtensions
import ltechnologies.onionphone.androwatch.collector.probeSecureSettingsProfile
import ltechnologies.onionphone.androwatch.collector.probeSensorDelays
import ltechnologies.onionphone.androwatch.collector.probeSensorFifo
import ltechnologies.onionphone.androwatch.collector.probeSensorInventoryHash
import ltechnologies.onionphone.androwatch.collector.probeSensorPower
import ltechnologies.onionphone.androwatch.collector.probeSensorRanges
import ltechnologies.onionphone.androwatch.collector.probeSensorWakeReport
import ltechnologies.onionphone.androwatch.collector.probeSharedLibsHash
import ltechnologies.onionphone.androwatch.collector.probeSystemFeaturesHash
import ltechnologies.onionphone.androwatch.collector.probeSystemSettingsProfile
import ltechnologies.onionphone.androwatch.collector.probeTelephonyExtended
import ltechnologies.onionphone.androwatch.collector.probeVulkanFeatures
import ltechnologies.onionphone.androwatch.collector.probeWifiPassive
import ltechnologies.onionphone.androwatch.collector.collectGlesFingerprint
import ltechnologies.onionphone.androwatch.collector.readSecureString
import ltechnologies.onionphone.androwatch.collector.signal
import ltechnologies.onionphone.androwatch.model.FingerprintSignal
import ltechnologies.onionphone.androwatch.model.SignalCategory
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

private const val RATIONALE = "Passive device fingerprint signal."

internal fun MutableList<FingerprintSignal>.appendDeviceIdentityExtras(context: Context) {
    val c = SignalCategory.DeviceIdentity
    listOf(
        "buildId" to Build.ID,
        "buildDisplay" to Build.DISPLAY,
        "buildTags" to Build.TAGS,
        "buildType" to Build.TYPE,
        "buildHost" to Build.HOST,
        "buildUser" to Build.USER,
        "buildTime" to Build.TIME.toString(),
        "bootloader" to Build.BOOTLOADER,
        "radioVersion" to Build.getRadioVersion(),
        "codename" to Build.VERSION.CODENAME,
        "baseOs" to (Build.VERSION.BASE_OS ?: "n/a"),
        "previewSdk" to Build.VERSION.PREVIEW_SDK_INT.toString(),
    ).forEach { (key, value) ->
        addSafe(c, key, key) { signal(c, key, key, value ?: "n/a", RATIONALE) }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        addSafe(c, "sku", "SKU") { signal(c, "sku", "SKU", Build.SKU ?: "n/a", RATIONALE) }
        addSafe(c, "odmSku", "ODM SKU") { signal(c, "odmSku", "ODM SKU", Build.ODM_SKU ?: "n/a", RATIONALE) }
    }
    addSafe(c, "buildFlags", "Build flags") {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val emulator = Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.contains("emulator", ignoreCase = true)
            || Build.HARDWARE.contains("goldfish")
            || Build.HARDWARE.contains("ranchu")
        val userBuild = Build.TYPE == "user"
        signal(
            c,
            "buildFlags",
            "Build flags",
            "emulator=$emulator;debuggable=$debuggable;user=$userBuild",
            RATIONALE,
        )
    }
    addOptional(c, "enabledImeHash", "Enabled IME hash") {
        signal(c, "enabledImeHash", "Enabled IME hash", probeEnabledImeHash(context) ?: error("unreadable"), RATIONALE)
    }
    addOptional(c, "imeSubtype", "IME subtype") {
        signal(c, "imeSubtype", "IME subtype",
            readSecureString(context, Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE) ?: error("unreadable"), RATIONALE)
    }
    forEachProbe(c, RATIONALE) { probeInputDevices(context) }
    addOptional(c, "clearkeyDrmId", "ClearKey DRM ID") {
        signal(c, "clearkeyDrmId", "ClearKey DRM ID",
            probeDrmId(UUID.fromString("e2719d58-a985-4b7a-982e-04c497c69547")) ?: error("unavailable"), RATIONALE)
    }
    addOptional(c, "playreadyDrmId", "PlayReady DRM ID") {
        signal(c, "playreadyDrmId", "PlayReady DRM ID",
            probeDrmId(UUID.fromString("9a04f079-9840-4286-ab92-e65773bef90e")) ?: error("unavailable"), RATIONALE)
    }
}

internal fun MutableList<FingerprintSignal>.appendSystemInfoExtras(context: Context) {
    val c = SignalCategory.SystemInfo
    forEachProbe(c, RATIONALE) { probeGlobalSettingsProfile(context) }
    forEachProbe(c, RATIONALE) { probeSecureSettingsProfile(context) }
    forEachProbe(c, RATIONALE) { probeSystemSettingsProfile(context) }
    addSafe(c, "featureFlagsExtended", "Extended feature flags") {
        signal(c, "featureFlagsExtended", "Extended feature flags", probeExtendedFeatureFlags(context), RATIONALE)
    }
    addOptional(c, "systemFeaturesHash", "System features hash") {
        signal(c, "systemFeaturesHash", "System features hash", probeSystemFeaturesHash(context) ?: error("empty"), RATIONALE)
    }
    addOptional(c, "sharedLibsHash", "Shared libraries hash") {
        signal(c, "sharedLibsHash", "Shared libraries hash", probeSharedLibsHash(context) ?: error("empty"), RATIONALE)
    }
    forEachProbe(c, RATIONALE) { probeRuntimeMemory(context) }
    forEachProbe(c, RATIONALE) { probeSdkExtensions() }
    forEachProbe(c, RATIONALE) { probeMiscHardware(context) }
    forEachProbe(c, RATIONALE) { probeCameraInventory(context) ?: emptyMap() }
}

internal fun MutableList<FingerprintSignal>.appendAccessibilityExtras(context: Context) {
    val c = SignalCategory.Accessibility
    forEachProbe(c, RATIONALE) { probeAccessibilitySettings(context) }
}

internal fun MutableList<FingerprintSignal>.appendDisplayExtras(context: Context, config: Configuration, display: Display?) {
    val c = SignalCategory.Display
    val dm = context.resources.displayMetrics
    addSafe(c, "physicalDpi", "Physical DPI") {
        signal(c, "physicalDpi", "Physical DPI", "xdpi=${dm.xdpi}; ydpi=${dm.ydpi}", RATIONALE)
    }
    addSafe(c, "simMccMncConfig", "MCC/MNC config") {
        signal(c, "simMccMncConfig", "MCC/MNC config", "mcc=${config.mcc}; mnc=${config.mnc}", RATIONALE)
    }
    addSafe(c, "inputConfig", "Input config") {
        signal(c, "inputConfig", "Input config",
            "touch=${config.touchscreen}; keyboard=${config.keyboard}; nav=${config.navigation}", RATIONALE)
    }
    addSafe(c, "screenLayout", "Screen layout") {
        signal(c, "screenLayout", "Screen layout",
            "layout=${config.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK}; long=${config.screenLayout and Configuration.SCREENLAYOUT_LONG_MASK}",
            RATIONALE)
    }
    val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    addSafe(c, "uiNightMode", "UI night mode") {
        signal(c, "uiNightMode", "UI night mode", uiMode.nightMode.toString(), RATIONALE)
    }
    if (display != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            addOptional(c, "cutoutGeometry", "Cutout geometry") {
                val rects = display.cutout?.boundingRects?.joinToString { "${it.width()}x${it.height()}" } ?: "none"
                signal(c, "cutoutGeometry", "Cutout geometry", rects, RATIONALE)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            addOptional(c, "hdrTypes", "HDR types") {
                val types = display.hdrCapabilities?.supportedHdrTypes?.joinToString() ?: "none"
                signal(c, "hdrTypes", "HDR types", types, RATIONALE)
            }
            addSafe(c, "hdrActive", "HDR active") {
                signal(c, "hdrActive", "HDR active", display.isHdr.toString(), RATIONALE)
            }
            addSafe(c, "colorMode", "Color mode") {
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    display.mode.toString()
                } else {
                    "n/a"
                }
                signal(c, "colorMode", "Color mode", mode, RATIONALE)
            }
            addSafe(c, "wideColorGamut", "Wide color gamut") {
                signal(c, "wideColorGamut", "Wide color gamut",
                    "supported=${display.isWideColorGamut}; space=${display.preferredWideGamutColorSpace?.name ?: "n/a"}",
                    RATIONALE)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            addSafe(c, "displayFlags", "Display flags") {
                signal(c, "displayFlags", "Display flags", display.flags.toString(), RATIONALE)
            }
        }
    }
}

internal fun MutableList<FingerprintSignal>.appendDeviceMotionExtras(sm: SensorManager) {
    val c = SignalCategory.DeviceMotion
    addOptional(c, "sensorInventoryHash", "Sensor inventory hash") {
        signal(c, "sensorInventoryHash", "Sensor inventory hash", probeSensorInventoryHash(sm) ?: error("empty"), RATIONALE)
    }
    addSafe(c, "sensorRanges", "Sensor ranges") {
        signal(c, "sensorRanges", "Sensor ranges", probeSensorRanges(sm), RATIONALE)
    }
    addSafe(c, "sensorDelays", "Sensor delays") {
        signal(c, "sensorDelays", "Sensor delays", probeSensorDelays(sm), RATIONALE)
    }
    addSafe(c, "sensorPower", "Sensor power") {
        signal(c, "sensorPower", "Sensor power", probeSensorPower(sm), RATIONALE)
    }
    addSafe(c, "sensorFifo", "Sensor FIFO") {
        signal(c, "sensorFifo", "Sensor FIFO", probeSensorFifo(sm), RATIONALE)
    }
    addSafe(c, "sensorWakeReport", "Sensor wake/report") {
        signal(c, "sensorWakeReport", "Sensor wake/report", probeSensorWakeReport(sm), RATIONALE)
    }
    addSafe(c, "dynamicSensorCount", "Dynamic sensors") {
        signal(c, "dynamicSensorCount", "Dynamic sensors", probeDynamicSensorCount(sm), RATIONALE)
    }
    addSafe(c, "sensorPresenceExtended", "Extended sensor presence") {
        signal(c, "sensorPresenceExtended", "Extended sensor presence", probeExtendedSensorPresence(sm), RATIONALE)
    }
}

internal fun MutableList<FingerprintSignal>.appendTtsExtras(voices: List<String>) {
    val c = SignalCategory.InstalledVoices
    addOptional(c, "voiceInventoryHash", "Voice inventory hash") {
        if (voices.isEmpty()) error("no voices")
        signal(c, "voiceInventoryHash", "Voice inventory hash", ltechnologies.onionphone.androwatch.collector.sha256Hex(voices.sorted().joinToString("|")), RATIONALE)
    }
    addOptional(c, "voiceFeatures", "Voice features sample") {
        signal(c, "voiceFeatures", "Voice features sample", voices.take(8).joinToString(";"), RATIONALE)
    }
}

internal fun MutableList<FingerprintSignal>.appendBatteryExtras(context: Context, bm: BatteryManager, sticky: android.content.Intent?) {
    val c = SignalCategory.Battery
    forEachProbe(c, RATIONALE) { probeBatteryExtended(bm, sticky) }
    val present = sticky?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true) ?: true
    addSafe(c, "batteryPresent", "Battery present") {
        signal(c, "batteryPresent", "Battery present", present.toString(), RATIONALE)
    }
}

internal fun MutableList<FingerprintSignal>.appendStorageExtras(context: Context, stat: StatFs, volumes: List<android.os.storage.StorageVolume>) {
    val c = SignalCategory.Storage
    forEachProbe(c, RATIONALE) { ltechnologies.onionphone.androwatch.collector.probeStorageExtended(context, stat, volumes) }
}

internal fun MutableList<FingerprintSignal>.appendNetworkExtras(context: Context, cm: ConnectivityManager) {
    val c = SignalCategory.Network
    val network = cm.activeNetwork
    val caps = network?.let { cm.getNetworkCapabilities(it) }
    val link = network?.let { cm.getLinkProperties(it) }
    forEachProbe(c, RATIONALE) { probeNetworkExtended(cm, caps, link) }
    forEachProbe(c, RATIONALE) { probeWifiPassive(context) }
    forEachProbe(c, RATIONALE, keyPrefix = "bt_", namePrefix = "BT ") { probeBluetoothPassive(context) }
}

internal fun MutableList<FingerprintSignal>.appendAudioExtras(context: Context, outputs: Array<AudioDeviceInfo>) {
    val c = SignalCategory.Audio
    forEachProbe(c, RATIONALE) { probeAudioExtended(context, outputs) }
}

internal fun MutableList<FingerprintSignal>.appendTelephonyExtras(tm: TelephonyManager) {
    val c = SignalCategory.Telephony
    forEachProbe(c, RATIONALE) { probeTelephonyExtended(tm) }
}

internal fun MutableList<FingerprintSignal>.appendGraphicsExtras(context: Context, gles: ltechnologies.onionphone.androwatch.collector.GlesFingerprintResult?) {
    val c = SignalCategory.Graphics
    if (gles != null) {
        addSafe(c, "glVersionFull", "GL version full") {
            signal(c, "glVersionFull", "GL version full", "req=${gles.reqGlEsVersion}; ${gles.version}", RATIONALE)
        }
        addSafe(c, "eglExtensionsHash", "EGL extensions hash") {
            signal(c, "eglExtensionsHash", "EGL extensions hash", gles.eglExtensionsHash, RATIONALE)
        }
        addSafe(c, "glslVersion", "GLSL version") {
            signal(c, "glslVersion", "GLSL version", gles.glslVersion, RATIONALE)
        }
        addSafe(c, "glMaxTexture", "GL max texture") {
            signal(c, "glMaxTexture", "GL max texture", gles.glMaxTexture, RATIONALE)
        }
        addSafe(c, "glMaxViewport", "GL max viewport") {
            signal(c, "glMaxViewport", "GL max viewport", gles.glMaxViewport, RATIONALE)
        }
    }
    forEachProbe(c, RATIONALE) { probeCodecInventory() ?: emptyMap() }
    forEachProbe(c, RATIONALE) { probeVulkanFeatures(context) }
}

internal fun MutableList<FingerprintSignal>.appendAppInfoExtras(context: Context) {
    val c = SignalCategory.AppInfo
    forEachProbe(c, RATIONALE) { probeDefaultApps(context) }
}

internal fun MutableList<FingerprintSignal>.appendLocaleExtras(config: android.content.res.Configuration) {
    val c = SignalCategory.Locale
    val tz = TimeZone.getDefault()
    addSafe(c, "dstProfile", "DST profile") {
        signal(c, "dstProfile", "DST profile", "usesDst=${tz.useDaylightTime()}; savings=${tz.dstSavings}", RATIONALE)
    }
    addSafe(c, "localeCount", "Locale count") {
        signal(c, "localeCount", "Locale count", config.locales.size().toString(), RATIONALE)
    }
    addSafe(c, "defaultScript", "Default script") {
        signal(c, "defaultScript", "Default script", Locale.getDefault().script.ifBlank { "n/a" }, RATIONALE)
    }
}
