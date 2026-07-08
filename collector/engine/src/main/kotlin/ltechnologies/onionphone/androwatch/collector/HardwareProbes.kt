package ltechnologies.onionphone.androwatch.collector

import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.usb.UsbManager
import android.media.MediaCodecList
import android.media.MediaDrm
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.Settings
import android.view.InputDevice
import java.util.UUID

/**
 * Hashes the (bounded) list of sensors reported by [SensorManager].
 *
 * The vendor/name/type/id tuple of each sensor is joined and SHA-256 hashed, yielding a
 * compact fingerprint of the device's sensor inventory. Reads via
 * [SensorManager.getSensorList], which requires no permission.
 *
 * @param sm Sensor manager to enumerate.
 * @param max Maximum number of sensors to include (bounds work on unusual devices).
 * @return SHA-256 hex of the sensor inventory, or `null` if no sensors are present.
 */
fun probeSensorInventoryHash(sm: SensorManager, max: Int = 64): String? = probeOrNull {
    val sensors = boundedSensorList(sm, max)
    if (sensors.isEmpty()) error("no sensors")
    val payload = sensors.joinToString("|") { s ->
        "${s.vendor}:${s.name}:${s.type}:${s.id}"
    }
    sha256Hex(payload)
}

/**
 * Reports the maximum range of the default accelerometer, gyroscope, magnetometer and
 * light sensors.
 *
 * Privacy: sensor hardware ranges are model-specific and aid fingerprinting; no permission
 * is required to read the [Sensor] metadata (as opposed to live readings).
 *
 * @param sm Sensor manager used to look up default sensors.
 * @return Semicolon-separated `label=maxRange` string (`n/a` when a sensor is absent).
 */
fun probeSensorRanges(sm: SensorManager): String {
    val types = listOf(
        Sensor.TYPE_ACCELEROMETER to "accel",
        Sensor.TYPE_GYROSCOPE to "gyro",
        Sensor.TYPE_MAGNETIC_FIELD to "mag",
        Sensor.TYPE_LIGHT to "light",
    )
    return types.joinToString(";") { (type, label) ->
        val s = sm.getDefaultSensor(type)
        "$label=${s?.maximumRange ?: "n/a"}"
    }
}

/**
 * Reports the min/max reporting delay of the default accelerometer.
 *
 * Privacy: metadata-only; contributes to sensor fingerprinting. No permission required.
 *
 * @param sm Sensor manager used to look up the accelerometer.
 * @return `min=<us>;max=<us>` string (`n/a` if the sensor is absent).
 */
fun probeSensorDelays(sm: SensorManager): String {
    val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    return "min=${accel?.minDelay ?: "n/a"};max=${accel?.maxDelay ?: "n/a"}"
}

/**
 * Reports the power draw of a bounded sample of sensors.
 *
 * Privacy: metadata-only; contributes to sensor fingerprinting. No permission required.
 *
 * @param sm Sensor manager to sample.
 * @return Semicolon-separated `type=power` string.
 */
fun probeSensorPower(sm: SensorManager): String {
    val sample = boundedSensorList(sm, 8)
    return sample.joinToString(";") { "${it.type}=${it.power}" }
}

/**
 * Reports the accelerometer's hardware FIFO max event count.
 *
 * Privacy: metadata-only; contributes to sensor fingerprinting. No permission required.
 *
 * @param sm Sensor manager used to look up the accelerometer.
 * @return FIFO capacity as a string, or `n/a` if unavailable.
 */
fun probeSensorFifo(sm: SensorManager): String {
    val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    return accel?.fifoMaxEventCount?.toString() ?: "n/a"
}

/**
 * Reports the accelerometer's wake-up capability and reporting mode (Android 5+).
 *
 * Privacy: metadata-only; contributes to sensor fingerprinting. No permission required.
 *
 * @param sm Sensor manager used to look up the accelerometer.
 * @return `wake=<bool>;mode=<int>` string, or `n/a` if the sensor is absent.
 */
fun probeSensorWakeReport(sm: SensorManager): String {
    val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return "n/a"
    val wake = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) accel.isWakeUpSensor else false
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) accel.reportingMode else -1
    return "wake=$wake;mode=$mode"
}

/**
 * Reports the count of dynamically-connected sensors (Android 7+).
 *
 * Privacy: metadata-only; no permission required.
 *
 * @param sm Sensor manager used to query dynamic sensors.
 * @return Dynamic sensor count, or `n/a` on older platforms.
 */
fun probeDynamicSensorCount(sm: SensorManager): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        sm.getDynamicSensorList(Sensor.TYPE_ALL).size.toString()
    } else {
        "n/a"
    }

/**
 * Reports presence of environmental/extended sensors (light, proximity, pressure,
 * humidity, ambient temperature, and hinge angle on Android 11+).
 *
 * Privacy: presence flags are model-specific fingerprint signals; no permission required.
 *
 * @param sm Sensor manager used to check default sensors.
 * @return Semicolon-separated `label=present` string.
 */
fun probeExtendedSensorPresence(sm: SensorManager): String {
    val types = mapOf(
        Sensor.TYPE_LIGHT to "light",
        Sensor.TYPE_PROXIMITY to "prox",
        Sensor.TYPE_PRESSURE to "pressure",
        Sensor.TYPE_RELATIVE_HUMIDITY to "humidity",
        Sensor.TYPE_AMBIENT_TEMPERATURE to "ambient_temp",
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val hinge = runCatching { Sensor.TYPE_HINGE_ANGLE }.getOrNull()
        if (hinge != null) {
            return (types + (hinge to "hinge")).entries.joinToString(";") { (t, l) ->
                "$l=${sm.getDefaultSensor(t) != null}"
            }
        }
    }
    return types.entries.joinToString(";") { (t, l) -> "$l=${sm.getDefaultSensor(t) != null}" }
}

/**
 * Probes CPU/memory characteristics via [Runtime] and [ActivityManager].
 *
 * Reports CPU count, JVM max/total heap, total/available RAM, the low-RAM device flag,
 * and required keyboard/touchscreen/navigation configuration.
 *
 * Privacy: coarse hardware specs; no permission required. Contributes to fingerprinting.
 *
 * @param context Context used to obtain [ActivityManager].
 * @return Map of probe keys to memory/CPU values.
 */
fun probeRuntimeMemory(context: Context): Map<String, String> = buildMap {
    put("cpuCount", Runtime.getRuntime().availableProcessors().toString())
    put("jvmMaxMb", (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toString())
    put("jvmTotalMb", (Runtime.getRuntime().totalMemory() / (1024 * 1024)).toString())
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mi = ActivityManager.MemoryInfo()
    am.getMemoryInfo(mi)
    put("ramTotalMb", (mi.totalMem / (1024 * 1024)).toString())
    put("ramAvailMb", (mi.availMem / (1024 * 1024)).toString())
    put("lowRam", am.isLowRamDevice.toString())
    val dci = am.deviceConfigurationInfo
    put("reqKeyboard", dci.reqKeyboardType.toString())
    put("reqTouchscreen", dci.reqTouchScreen.toString())
    put("reqNavigation", dci.reqNavigation.toString())
}

/**
 * Probes extended storage characteristics from [Environment], [StatFs] and storage volumes.
 *
 * Reports external storage state/emulation, filesystem block size, a hash of volume UUIDs
 * (Android 7+), and per-volume mount states.
 *
 * Privacy: volume UUIDs are hashed rather than exposed; no permission required for these
 * aggregate storage properties.
 *
 * @param context Context (reserved for future use / API symmetry).
 * @param stat Pre-created [StatFs] for the primary storage path.
 * @param volumes Storage volumes previously enumerated by the caller.
 * @return Map of probe keys to storage values.
 */
fun probeStorageExtended(context: Context, stat: StatFs, volumes: List<StorageVolume>): Map<String, String> =
    buildMap {
        put("externalState", Environment.getExternalStorageState())
        put("storageEmulated", Environment.isExternalStorageEmulated().toString())
        put("blockSize", stat.blockSizeLong.toString())
        val uuids = volumes.mapNotNull { vol ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) vol.uuid else null
        }
        if (uuids.isNotEmpty()) put("volumeUuidHash", sha256Hex(uuids.sorted().joinToString("|")))
        put("volumeStates", volumes.joinToString { it.state })
    }

/**
 * Probes the camera inventory via [CameraManager] without opening any camera.
 *
 * Reports camera count, concurrent-camera set count (Android 11+) and a hash of lens
 * facings. Reading [CameraCharacteristics] does not require the `CAMERA` permission.
 *
 * Privacy: metadata-only (no capture); contributes to hardware fingerprinting. This is the
 * passive counterpart to the permissioned Cameras collector.
 *
 * @param context Context used to obtain [CameraManager].
 * @return Map of probe keys to camera-inventory values, or `null` on failure.
 */
fun probeCameraInventory(context: Context): Map<String, String>? = probeOrNull {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val ids = cm.cameraIdList
    val map = mutableMapOf<String, String>()
    map["cameraCount"] = ids.size.toString()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        map["concurrentSets"] = cm.concurrentCameraIds.size.toString()
    }
    val facings = ids.mapNotNull { id ->
        runCatching {
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)?.toString()
        }.getOrNull()
    }
    if (facings.isNotEmpty()) map["facingHash"] = sha256Hex(facings.sorted().joinToString("|"))
    map
}

/**
 * Probes connected input devices and installed input methods.
 *
 * Reports input-device count, a hash of device descriptors, a source-type histogram, the
 * IME list count and a hash of enabled IME package names (via [InputDevice] and
 * `InputMethodManager`).
 *
 * Privacy: descriptors are hashed; the enabled-IME set can reveal installed keyboards.
 * No runtime permission is required.
 *
 * @param context Context used to obtain the input-method service.
 * @return Map of probe keys to input-device values.
 */
fun probeInputDevices(context: Context): Map<String, String> = buildMap {
    val ids = InputDevice.getDeviceIds().toList()
    put("inputDeviceCount", ids.size.toString())
    val descriptors = ids.take(16).mapNotNull { id ->
        InputDevice.getDevice(id)?.let { d -> "${d.name}:${d.vendorId}:${d.productId}:${d.sources}" }
    }
    if (descriptors.isNotEmpty()) put("inputDevicesHash", sha256Hex(descriptors.sorted().joinToString("|")))
    val sources = ids.mapNotNull { InputDevice.getDevice(it)?.sources }
    put("inputSources", sources.groupBy { it }.map { (k, v) -> "$k=${v.size}" }.joinToString(";"))
    val im = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
    put("imeListCount", im.inputMethodList.size.toString())
    val enabled = im.enabledInputMethodList.joinToString { it.packageName }
    if (enabled.isNotBlank()) put("enabledImeListHash", sha256Hex(enabled))
}

/**
 * Probes assorted hardware/system properties in one pass.
 *
 * Reports vibrator presence and haptic primitive support (Android 12+), USB device count,
 * selected spell checker, notifications-enabled flag, user profile count / managed-profile
 * status, and eSIM availability (Android 9+).
 *
 * Privacy: aggregate device/config metadata; no runtime permission is required. Managed
 * profile and enabled-service hints can indicate enterprise management.
 *
 * @param context Context used to obtain vibrator/USB/notification/user services.
 * @return Map of probe keys to miscellaneous hardware values.
 */
fun probeMiscHardware(context: Context): Map<String, String> = buildMap {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    put("hasVibrator", (vibrator?.hasVibrator() == true).toString())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibrator != null) {
        val supported = vibrator.arePrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_TICK,
        )
        put("hapticPrimitives", supported.count { it }.toString())
    }
    val usb = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    put("usbDevices", (usb?.deviceList?.size ?: 0).toString())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        readSecureString(context, "selected_spell_checker")?.let {
            put("spellChecker", it.substringBefore('/'))
        }
    }
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    put("notificationsEnabled", nm.areNotificationsEnabled().toString())
    val um = context.getSystemService(Context.USER_SERVICE) as android.os.UserManager
    put("userCount", um.userProfiles.size.toString())
    put("managedProfile", um.isManagedProfile.toString())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val euicc = context.getSystemService(Context.EUICC_SERVICE) as? android.telephony.euicc.EuiccManager
        put("esimEnabled", (euicc?.isEnabled == true).toString())
    }
}

/**
 * Reads a DRM device-unique id (e.g. Widevine) via [MediaDrm] and returns it hashed as hex.
 *
 * Privacy: **high** — `PROPERTY_DEVICE_UNIQUE_ID` is a stable, hardware-backed identifier
 * that can persistently track a device. The value is hex-encoded here; callers surface it
 * as a strong fingerprint signal. No runtime permission gates this API, which is precisely
 * why it is privacy-sensitive.
 *
 * @param uuid DRM scheme UUID (e.g. Widevine) to open.
 * @return Hex-encoded device id, or `null` if the scheme/property is unavailable.
 */
fun probeDrmId(uuid: UUID): String? = probeOrNull {
    MediaDrm(uuid).use { drm ->
        val id = drm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
        bytesToHex(id)
    }
}

/**
 * Probes the media codec inventory via [MediaCodecList] (Android 5+).
 *
 * Reports a hash of all codec names, a hardware/software codec ratio, and presence flags
 * for HEVC/VP9/AV1 profiles.
 *
 * Privacy: codec inventory is model/ROM specific and aids fingerprinting; no permission
 * required.
 *
 * @return Map of probe keys to codec values, or `null` on older platforms/failure.
 */
fun probeCodecInventory(): Map<String, String>? = probeOrNull {
    val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        MediaCodecList(MediaCodecList.ALL_CODECS)
    } else {
        error("API < 21")
    }
    val infos = list.codecInfos
    val names = infos.map { it.name }.sorted()
    val hw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        infos.count { it.isHardwareAccelerated }
    } else {
        infos.count { !it.name.startsWith("OMX.google") && !it.name.startsWith("c2.android") }
    }
    val sw = infos.size - hw
    mapOf(
        "codecListHash" to sha256Hex(names.joinToString("|")),
        "codecHwSwRatio" to "hw=$hw;sw=$sw",
        "codecProfiles" to listOf("hevc", "vp9", "av01").joinToString(";") { codec ->
            "$codec=${names.any { it.contains(codec, ignoreCase = true) }}"
        },
    )
}

/**
 * Reports installed SDK extension versions for R/S/T/U (Android 11+ via [android.os.ext.SdkExtensions]).
 *
 * Privacy: version metadata only; contributes to OS-build fingerprinting. No permission required.
 *
 * @return Map of extension keys (`extR`, `extS`, ...) to version numbers for supported levels.
 */
fun probeSdkExtensions(): Map<String, String> = buildMap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        put("extR", android.os.ext.SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R).toString())
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        put("extS", android.os.ext.SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S).toString())
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        put("extT", android.os.ext.SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU).toString())
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        put("extU", android.os.ext.SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE).toString())
    }
}
