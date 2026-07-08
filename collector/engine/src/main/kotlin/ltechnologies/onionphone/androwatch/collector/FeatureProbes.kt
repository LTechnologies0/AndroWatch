package ltechnologies.onionphone.androwatch.collector

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build

/** Hardware/system features queried by [probeExtendedFeatureFlags], each paired with its short label. */
private val EXTENDED_FEATURES = listOf(
    PackageManager.FEATURE_FINGERPRINT to "fingerprint",
    "android.hardware.biometrics.face" to "face",
    "android.hardware.biometrics.iris" to "iris",
    PackageManager.FEATURE_NFC to "nfc",
    PackageManager.FEATURE_NFC_HOST_CARD_EMULATION to "nfc_hce",
    PackageManager.FEATURE_BLUETOOTH_LE to "bt_le",
    PackageManager.FEATURE_WIFI_DIRECT to "wifi_direct",
    PackageManager.FEATURE_USB_HOST to "usb_host",
    PackageManager.FEATURE_USB_ACCESSORY to "usb_accessory",
    "android.hardware.sensor.hifi_sensors" to "hifi_sensors",
    PackageManager.FEATURE_STRONGBOX_KEYSTORE to "strongbox",
    PackageManager.FEATURE_LIVE_WALLPAPER to "live_wallpaper",
    PackageManager.FEATURE_MICROPHONE to "microphone",
    PackageManager.FEATURE_CAMERA_ANY to "camera_any",
)

/**
 * Hashes the sorted list of all system features reported by `PackageManager`.
 *
 * The full feature set is highly device-model specific; hashing it yields a compact,
 * comparable fingerprint without listing every feature.
 *
 * Privacy: no permission required; contributes to hardware fingerprinting.
 *
 * @param context Context providing the package manager.
 * @return SHA-256 hex of the feature set, or `null` if no features are available.
 */
fun probeSystemFeaturesHash(context: Context): String? = probeOrNull {
    val pm = context.packageManager
    @Suppress("DEPRECATION")
    val features = pm.getSystemAvailableFeatures()
    if (features.isEmpty()) error("no features")
    val names = ArrayList<String>(features.size)
    for (feature in features) {
        feature.name?.let(names::add)
    }
    if (names.isEmpty()) error("no features")
    names.sort()
    sha256Hex(names.joinToString("|"))
}

/**
 * Hashes the sorted list of system shared library names.
 *
 * Privacy: no permission required; the shared-library set is device/ROM specific and aids
 * fingerprinting.
 *
 * @param context Context providing the package manager.
 * @return SHA-256 hex of the shared-library names, or `null` if none are available.
 */
fun probeSharedLibsHash(context: Context): String? = probeOrNull {
    val libs = context.packageManager.systemSharedLibraryNames?.sorted() ?: error("no libs")
    sha256Hex(libs.joinToString("|"))
}

/**
 * Builds a `label=bool` string reporting presence of each feature in [EXTENDED_FEATURES]
 * (fingerprint, face/iris biometrics, NFC, BLE, USB host, StrongBox, etc.).
 *
 * Privacy: `PackageManager.hasSystemFeature` requires no permission; the combined flags
 * form part of the hardware fingerprint.
 *
 * @param context Context providing the package manager.
 * @return Semicolon-separated `label=true/false` capability string.
 */
fun probeExtendedFeatureFlags(context: Context): String {
    val pm = context.packageManager
    return EXTENDED_FEATURES.joinToString(";") { (feat, label) ->
        "$label=${pm.hasSystemFeature(feat)}"
    }
}

/**
 * Probes passive Wi-Fi capabilities via [WifiManager] without scanning networks.
 *
 * Reports Wi-Fi enabled/state, supported bands (5/6/60 GHz depending on API level),
 * Wi-Fi Direct support and (Android 12+) the driver country code.
 *
 * Privacy: reads only radio *capabilities* and state, not SSIDs or scan results, so no
 * location/nearby-devices permission is required. Contributes to hardware fingerprinting.
 *
 * @param context Context used to obtain the application-scoped [WifiManager].
 * @return Map of probe keys to Wi-Fi capability values.
 */
fun probeWifiPassive(context: Context): Map<String, String> = buildMap {
    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return@buildMap
    put("wifiEnabled", wm.isWifiEnabled.toString())
    @Suppress("DEPRECATION")
    put("wifiState", wm.wifiState.toString())
    put("wifi5g", wm.is5GHzBandSupported.toString())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        put("wifi6g", wm.is6GHzBandSupported.toString())
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        put("wifi60g", wm.is60GHzBandSupported.toString())
    }
    put("wifiDirect", context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT).toString())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching { put("wifiCountry", wm.javaClass.getMethod("getCountryCode").invoke(wm) as String) }
    }
}

/**
 * Probes passive Bluetooth adapter capabilities.
 *
 * Reports adapter presence and enabled state plus LE capability flags (multi-advertisement,
 * LE 2M / coded PHY, offloaded filtering) without scanning for devices.
 *
 * Privacy: reads only adapter capabilities/state, not paired or nearby devices, so no
 * Bluetooth runtime permission is required here. Contributes to hardware fingerprinting.
 *
 * @param context Context used to obtain the [BluetoothManager]/adapter.
 * @return Map of probe keys to Bluetooth capability values.
 */
fun probeBluetoothPassive(context: Context): Map<String, String> = buildMap {
    val adapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        context.getSystemService(BluetoothManager::class.java)?.adapter
    } else {
        @Suppress("DEPRECATION")
        BluetoothAdapter.getDefaultAdapter()
    }
    put("btAdapter", (adapter != null).toString())
    if (adapter == null) return@buildMap
    put("btEnabled", adapter.isEnabled.toString())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        put("btMultiAdv", adapter.isMultipleAdvertisementSupported.toString())
        put("btLe2m", adapter.isLe2MPhySupported.toString())
        put("btLeCoded", adapter.isLeCodedPhySupported.toString())
        put("btOffloadFilter", adapter.isOffloadedFilteringSupported.toString())
    }
}

/**
 * Probes presence of Vulkan graphics feature declarations.
 *
 * Privacy: no permission required; Vulkan level/version support is part of the GPU
 * fingerprint.
 *
 * @param context Context providing the package manager.
 * @return Map with `vulkanLevel` and `vulkanVersion` boolean flags.
 */
fun probeVulkanFeatures(context: Context): Map<String, String> = buildMap {
    val pm = context.packageManager
    put("vulkanLevel", pm.hasSystemFeature("android.hardware.vulkan.level").toString())
    put("vulkanVersion", pm.hasSystemFeature("android.hardware.vulkan.version").toString())
}
