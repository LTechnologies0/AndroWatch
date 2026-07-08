package ltechnologies.onionphone.androwatch.collector.permissioned

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import ltechnologies.onionphone.androwatch.collector.countQuery
import ltechnologies.onionphone.androwatch.collector.addSafe
import ltechnologies.onionphone.androwatch.collector.buildSignals
import ltechnologies.onionphone.androwatch.collector.discoverLocalNetworkServices
import ltechnologies.onionphone.androwatch.collector.signal
import ltechnologies.onionphone.androwatch.collector.SignalCollector
import ltechnologies.onionphone.androwatch.model.SignalCategory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Permissioned collector for [SignalCategory.Motion].
 *
 * Reports activity-recognition / body-sensors grant state, heart-rate sensor presence and a
 * coarse step-count *bucket* via [SensorManager]. The step read requires
 * `ACTIVITY_RECOGNITION`; without it the bucket is reported as `permission_required`.
 *
 * Privacy: activity/step data is behaviorally identifying and health-adjacent; only a coarse
 * bucket (never the raw step stream) is emitted. Gated behind
 * [PermissionKind.Motion][ltechnologies.onionphone.androwatch.permission.PermissionKind.Motion].
 *
 * @see SignalCollector
 */
class MotionCollector : SignalCollector {
    override val category = SignalCategory.Motion

    /**
     * Collects activity/motion signals for this category.
     *
     * @param context Context used to obtain [SensorManager] and check permissions.
     * @return The motion signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val heartRatePresent = sm.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null
        val stepBucket = if (hasPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)) {
            readStepBucket(sm)
        } else {
            "permission_required"
        }
        return buildSignals(category) {
                        addSafe(category, "activityRecognition", "Activity recognition granted") {
                signal(category, "activityRecognition", "Activity recognition granted",
                    hasPermission(context, Manifest.permission.ACTIVITY_RECOGNITION).toString(),
                    "Activity recognition enables step and motion profiling.")
            }
                        addSafe(category, "bodySensors", "Body sensors granted") {
                signal(category, "bodySensors", "Body sensors granted",
                    hasPermission(context, Manifest.permission.BODY_SENSORS).toString(),
                    "Body sensors unlock heart rate and biometric reads.")
            }
                        addSafe(category, "heartRateSensor", "Heart rate sensor present") {
                signal(category, "heartRateSensor", "Heart rate sensor present", heartRatePresent.toString(),
                    "Heart rate hardware presence is device-specific.")
            }
                        addSafe(category, "stepsBucket", "Step counter bucket") {
                signal(category, "stepsBucket", "Step counter bucket", stepBucket,
                    "Step count bucket reveals activity level without raw stream.")
            }
        }
    }

    /**
     * Reads the step-counter sensor briefly and returns a coarse activity bucket.
     *
     * Registers a listener for up to 2 seconds, then unregisters. Never exposes the raw count.
     *
     * @param sm Sensor manager used to obtain and observe the step counter.
     * @return One of `no_sensor`, `timeout`, `0`, `1-1k`, `1k-10k`, `10k+`.
     */
    private suspend fun readStepBucket(sm: SensorManager): String {
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return "no_sensor"
        val steps = withTimeoutOrNull(2000L) {
            suspendCancellableCoroutine<Long> { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        val value = event?.values?.firstOrNull()?.toLong() ?: 0L
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(value)
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sm.unregisterListener(listener) }
            }
        } ?: return "timeout"
        return when {
            steps <= 0 -> "0"
            steps < 1_000 -> "1-1k"
            steps < 10_000 -> "1k-10k"
            else -> "10k+"
        }
    }
}

/**
 * Permissioned collector for [SignalCategory.Location].
 *
 * Reports enabled providers, GPS-enabled state, a coarse last-fix accuracy *bucket*, fix age
 * and altitude availability via [LocationManager]. It never emits raw coordinates.
 *
 * Privacy: location is highly sensitive; only bucketed accuracy/recency is exposed, not
 * latitude/longitude. Gated behind
 * [PermissionKind.Location][ltechnologies.onionphone.androwatch.permission.PermissionKind.Location].
 *
 * @see SignalCollector
 */
class LocationCollector : SignalCollector {
    override val category = SignalCategory.Location

    /**
     * Collects location-configuration signals for this category.
     *
     * @param context Context used to obtain [LocationManager].
     * @return The location signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = lm.getProviders(true)
        val lastGps = runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
        val lastNetwork = runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
        val best = lastGps ?: lastNetwork
        val accuracyBucket = best?.accuracy?.let { acc ->
            when {
                acc < 100f -> "<100m"
                acc < 1000f -> "100m-1km"
                else -> ">1km"
            }
        } ?: "no_fix"
        val fixAge = best?.let { ((System.currentTimeMillis() - it.time) / 1000).toString() + "s" } ?: "n/a"
        return buildSignals(category) {
                        addSafe(category, "providers", "Enabled providers") {
                signal(category, "providers", "Enabled providers", providers.joinToString(),
                    "Available location providers reveal device capabilities.")
            }
                        addSafe(category, "gpsEnabled", "GPS enabled") {
                signal(category, "gpsEnabled", "GPS enabled",
                    runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false).toString(),
                    "GPS enabled state is a configuration signal.")
            }
                        addSafe(category, "accuracyBucket", "Last fix accuracy bucket") {
                signal(category, "accuracyBucket", "Last fix accuracy bucket", accuracyBucket,
                    "Location accuracy bucket narrows geographic area without exposing coordinates.")
            }
                        addSafe(category, "fixAge", "Last fix age") {
                signal(category, "fixAge", "Last fix age", fixAge,
                    "Fix recency indicates how recently location was accessed.")
            }
                        addSafe(category, "hasAltitude", "Has altitude") {
                signal(category, "hasAltitude", "Has altitude", (best?.hasAltitude() == true).toString(),
                    "Altitude availability increases location entropy.")
            }
        }
    }
}

/**
 * Permissioned collector for [SignalCategory.Cameras].
 *
 * Reports camera count, front/back/external facing distribution, flash-capable camera count
 * and the number of permission-gated characteristic keys via [CameraManager] /
 * [CameraCharacteristics].
 *
 * Privacy: camera hardware topology is a device-model fingerprint; no images are captured.
 * Gated behind [PermissionKind.Camera][ltechnologies.onionphone.androwatch.permission.PermissionKind.Camera].
 *
 * @see SignalCollector
 */
class CamerasCollector : SignalCollector {
    override val category = SignalCategory.Cameras

    /**
     * Collects camera-inventory signals for this category.
     *
     * @param context Context used to obtain [CameraManager].
     * @return The camera signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ids = runCatching { cm.cameraIdList }.getOrDefault(emptyArray())
        var front = 0
        var back = 0
        var external = 0
        var flash = 0
        ids.forEach { id ->
            runCatching {
                val chars = cm.getCameraCharacteristics(id)
                when (chars.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_FRONT -> front++
                    CameraCharacteristics.LENS_FACING_BACK -> back++
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> external++
                }
                if (chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) flash++
            }
        }
        val gatedKeys = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ids.isNotEmpty()) {
            runCatching { cm.getCameraCharacteristics(ids[0]).keysNeedingPermission.size }.getOrDefault(0)
        } else {
            0
        }
        return buildSignals(category) {
                        addSafe(category, "cameraCount", "Camera count") {
                signal(category, "cameraCount", "Camera count", ids.size.toString(),
                    "Camera count is device-specific hardware signal.")
            }
                        addSafe(category, "facing", "Facing distribution") {
                signal(category, "facing", "Facing distribution", "front=$front back=$back external=$external",
                    "Camera facing mix identifies device form factor.")
            }
                        addSafe(category, "flashCount", "Cameras with flash") {
                signal(category, "flashCount", "Cameras with flash", flash.toString(),
                    "Flash capability distribution is hardware-specific.")
            }
                        addSafe(category, "gatedKeys", "Permission-gated metadata keys") {
                signal(category, "gatedKeys", "Permission-gated metadata keys", gatedKeys.toString(),
                    "Number of camera characteristics requiring CAMERA permission.")
            }
        }
    }
}

/**
 * Permissioned collector for [SignalCategory.Bluetooth].
 *
 * Reports Bluetooth availability/enabled state, bonded-device count and paired-device class
 * distribution via [BluetoothManager]. The bonded list requires `BLUETOOTH_CONNECT`; without
 * it counts are reported as zero.
 *
 * Privacy: the set of paired accessories is identifying; device names/addresses are never
 * exported, only class counts. Gated behind
 * [PermissionKind.Bluetooth][ltechnologies.onionphone.androwatch.permission.PermissionKind.Bluetooth].
 *
 * @see SignalCollector
 */
class BluetoothCollector : SignalCollector {
    override val category = SignalCategory.Bluetooth

    /**
     * Collects Bluetooth-adapter/pairing signals for this category.
     *
     * @param context Context used to obtain [BluetoothManager] and check permissions.
     * @return The Bluetooth signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter
        if (adapter == null) {
            return buildSignals(category) {
                                addSafe(category, "available", "Bluetooth available") {
                    signal(category, "available", "Bluetooth available", "false",
                        "Bluetooth hardware absence is a device trait.")
                }
            }
        }
        val bonded = if (hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)) {
            runCatching { adapter.bondedDevices }.getOrDefault(emptySet())
        } else {
            emptySet()
        }
        val classCounts = bonded.groupBy { it.bluetoothClass?.majorDeviceClass ?: 0 }
            .map { (cls, devices) -> "$cls=${devices.size}" }
            .joinToString()
        return buildSignals(category) {
                        addSafe(category, "enabled", "Bluetooth enabled") {
                signal(category, "enabled", "Bluetooth enabled",
                    runCatching { adapter.isEnabled }.getOrDefault(false).toString(),
                    "Bluetooth on/off is a user preference signal.")
            }
                        addSafe(category, "bondedCount", "Bonded devices") {
                signal(category, "bondedCount", "Bonded devices", bonded.size.toString(),
                    "Number of paired devices reveals accessory ecosystem.")
            }
                        addSafe(category, "deviceClasses", "Device class distribution") {
                signal(category, "deviceClasses", "Device class distribution", classCounts.ifBlank { "none" },
                    "Paired device types (audio, phone, etc.) are identifying.")
            }
        }
    }
}

/**
 * Permissioned collector for [SignalCategory.LocalNetwork].
 *
 * Reports Wi-Fi enabled state, visible AP count, band and security-protocol distribution
 * (via [WifiManager] scan results) and mDNS/NSD service discovery
 * ([discoverLocalNetworkServices]). Scanning requires `NEARBY_WIFI_DEVICES` (Android 13+) or
 * fine location on older releases.
 *
 * Privacy: nearby APs and local services characterize the physical network environment;
 * SSIDs and hostnames are never exported, only aggregate counts/types. Gated behind
 * [PermissionKind.LocalNetwork][ltechnologies.onionphone.androwatch.permission.PermissionKind.LocalNetwork].
 *
 * @see SignalCollector
 */
class LocalNetworkCollector : SignalCollector {
    override val category = SignalCategory.LocalNetwork

    /**
     * Collects local-network environment signals for this category.
     *
     * @param context Context used to obtain [WifiManager] and run mDNS discovery.
     * @return The local-network signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val canScan = hasPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) ||
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
                hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION))
        if (canScan) {
            runCatching { wifi.startScan() }
        }
        val scanResults = if (canScan) {
            runCatching { wifi.scanResults }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val band2g = scanResults.count { it.frequency in 2400..2500 }
        val band5g = scanResults.count { it.frequency in 4900..5900 }
        val band6g = scanResults.count { it.frequency in 5925..7125 }
        val security = scanResults.groupBy { result ->
            when {
                result.capabilities.contains("WPA3") -> "wpa3"
                result.capabilities.contains("WPA2") -> "wpa2"
                result.capabilities.contains("WEP") -> "wep"
                else -> "open"
            }
        }.map { (k, v) -> "$k=${v.size}" }.joinToString()
        val nsd = runCatching { discoverLocalNetworkServices(context) }.getOrNull()
        return buildSignals(category) {
                        addSafe(category, "wifiEnabled", "Wi-Fi enabled") {
                signal(category, "wifiEnabled", "Wi-Fi enabled",
                    runCatching { wifi.isWifiEnabled }.getOrDefault(false).toString(),
                    "Wi-Fi state reveals connectivity preferences.")
            }
                        addSafe(category, "apCount", "Visible AP count") {
                signal(category, "apCount", "Visible AP count", scanResults.size.toString(),
                    "Nearby access point count characterizes local network environment.")
            }
                        addSafe(category, "bands", "Band distribution") {
                signal(category, "bands", "Band distribution", "2.4g=$band2g 5g=$band5g 6g=$band6g",
                    "Wi-Fi band mix is environment-specific (no SSIDs exported).")
            }
                        addSafe(category, "security", "Security distribution") {
                signal(category, "security", "Security distribution", security.ifBlank { "none" },
                    "Security protocol mix narrows network environment.")
            }
                        addSafe(category, "mdnsServices", "mDNS services found") {
                signal(category, "mdnsServices", "mDNS services found",
                    (nsd?.servicesFound ?: 0).toString(),
                    "Local mDNS discovery reveals nearby smart devices and services.")
            }
                        addSafe(category, "mdnsTypes", "mDNS service types") {
                signal(category, "mdnsTypes", "mDNS service types",
                    nsd?.serviceTypes?.entries?.joinToString { "${it.key}=${it.value}" }?.ifBlank { "none" } ?: "none",
                    "Service type taxonomy characterizes home/office network (no hostnames).")
            }
                        addSafe(category, "mdnsSuccess", "mDNS discovery success") {
                signal(category, "mdnsSuccess", "mDNS discovery success",
                    (nsd?.discoverySuccess ?: false).toString(),
                    "Whether local network discovery completed successfully.")
            }
        }
    }
}

/**
 * Permissioned collector for [SignalCategory.Contacts].
 *
 * Reports contact count, account-type distribution and phone-label distribution via the
 * Contacts content provider. Only aggregate counts are read — never names, numbers or emails.
 *
 * Privacy: contact-database size and structure are strongly behaviorally identifying; no
 * personal contact data leaves the device. Gated behind
 * [PermissionKind.Contacts][ltechnologies.onionphone.androwatch.permission.PermissionKind.Contacts].
 *
 * @see SignalCollector
 */
class ContactsCollector : SignalCollector {
    override val category = SignalCategory.Contacts

    /**
     * Collects aggregate contact-database signals for this category.
     *
     * @param context Context providing the content resolver.
     * @return The contacts signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val contactCount = countQuery(context, ContactsContract.Contacts.CONTENT_URI)
        val accountTypes = mutableMapOf<String, Int>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.ACCOUNT_TYPE),
                null, null, null,
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
                while (cursor.moveToNext()) {
                    val type = cursor.getString(idx) ?: "unknown"
                    accountTypes[type] = (accountTypes[type] ?: 0) + 1
                }
            }
        }
        return buildSignals(category) {
                        addSafe(category, "contactCount", "Contact count") {
                signal(category, "contactCount", "Contact count", contactCount.toString(),
                    "Contact database size is a strong behavioral fingerprint.")
            }
                        addSafe(category, "accountTypes", "Account type distribution") {
                signal(category, "accountTypes", "Account type distribution",
                    accountTypes.entries.joinToString { "${it.key}=${it.value}" }.ifBlank { "none" },
                    "Contact account sources reveal Google/enterprise profile.")
            }
                        addSafe(category, "phoneLabels", "Phone label distribution") {
                val labelCounts = mutableMapOf<String, Int>()
                runCatching {
                    context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.TYPE),
                        null, null, null,
                    )?.use { cursor ->
                        val idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                        while (cursor.moveToNext()) {
                            val label = phoneTypeLabel(cursor.getInt(idx))
                            labelCounts[label] = (labelCounts[label] ?: 0) + 1
                        }
                    }
                }
                signal(category, "phoneLabels", "Phone label distribution",
                    labelCounts.entries.joinToString { "${it.key}=${it.value}" }.ifBlank { "none" },
                    "Home/work/mobile label mix is a behavioral fingerprint.")
            }
        }
    }

    /** Maps a `ContactsContract.CommonDataKinds.Phone.TYPE_*` constant to a short label. */
    private fun phoneTypeLabel(type: Int) = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "home"
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "mobile"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "work"
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> "fax_work"
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> "fax_home"
        ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "other"
        else -> "type-$type"
    }
}

/**
 * Permissioned collector for [SignalCategory.Photos].
 *
 * Reports image/video counts, capture-year distribution and access mode (full vs Android 14+
 * partial selection) via the [MediaStore] content provider. Only counts and year buckets are
 * read — never filenames, paths or image data.
 *
 * Privacy: media-library size and timeline are behaviorally identifying; no media content is
 * accessed. Gated behind
 * [PermissionKind.Photos][ltechnologies.onionphone.androwatch.permission.PermissionKind.Photos].
 *
 * @see SignalCollector
 */
class PhotosCollector : SignalCollector {
    override val category = SignalCategory.Photos

    /**
     * Collects aggregate media-library signals for this category.
     *
     * @param context Context providing the content resolver.
     * @return The photos signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val imageCount = countQuery(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        val videoCount = countQuery(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        val yearCounts = mutableMapOf<Int, Int>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media.DATE_TAKEN),
                null, null, null,
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                if (idx >= 0) {
                    while (cursor.moveToNext()) {
                        val taken = cursor.getLong(idx)
                        if (taken > 0L) {
                            val year = java.util.Calendar.getInstance().apply { timeInMillis = taken }
                                .get(java.util.Calendar.YEAR)
                            yearCounts[year] = (yearCounts[year] ?: 0) + 1
                        }
                    }
                }
            }
        }
        return buildSignals(category) {
                        addSafe(category, "imageCount", "Image count") {
                signal(category, "imageCount", "Image count", imageCount.toString(),
                    "Photo library size reveals user media habits.")
            }
                        addSafe(category, "videoCount", "Video count") {
                signal(category, "videoCount", "Video count", videoCount.toString(),
                    "Video library size complements photo fingerprint.")
            }
                        addSafe(category, "captureYears", "Capture year distribution") {
                signal(category, "captureYears", "Capture year distribution",
                    yearCounts.entries.sortedByDescending { it.key }.joinToString { "${it.key}=${it.value}" }.ifBlank { "none" },
                    "Year distribution of photos narrows timeline without exposing filenames.")
            }
                        addSafe(category, "accessMode", "Access mode") {
                signal(category, "accessMode", "Access mode",
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                        hasPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                    ) "partial_or_full" else "full_or_denied",
                    "Android 14+ partial gallery access limits visible subset.")
            }
        }
    }
}

/**
 * Permissioned collector for [SignalCategory.Calendar].
 *
 * Reports calendar count and event count via the [CalendarContract] content provider. Only
 * aggregate counts are read — never event titles, times or attendees.
 *
 * Privacy: scheduling intensity is behaviorally identifying; no event content is accessed.
 * Gated behind [PermissionKind.Calendar][ltechnologies.onionphone.androwatch.permission.PermissionKind.Calendar].
 *
 * @see SignalCollector
 */
class CalendarCollector : SignalCollector {
    override val category = SignalCategory.Calendar

    /**
     * Collects aggregate calendar signals for this category.
     *
     * @param context Context providing the content resolver.
     * @return The calendar signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val calendarCount = countQuery(context, CalendarContract.Calendars.CONTENT_URI)
        val eventCount = countQuery(context, CalendarContract.Events.CONTENT_URI)
        return buildSignals(category) {
                        addSafe(category, "calendarCount", "Calendar count") {
                signal(category, "calendarCount", "Calendar count", calendarCount.toString(),
                    "Number of calendars reveals account and work/personal split.")
            }
                        addSafe(category, "eventCount", "Event count") {
                signal(category, "eventCount", "Event count", eventCount.toString(),
                    "Event count indicates scheduling intensity.")
            }
        }
    }
}

/**
 * Permissioned collector for [SignalCategory.Reminders].
 *
 * Reports the reminder count via the [CalendarContract] content provider. Only the aggregate
 * count is read — never reminder content.
 *
 * Privacy: reminder count hints at task-management habits; no reminder content is accessed.
 * Gated behind [PermissionKind.Reminders][ltechnologies.onionphone.androwatch.permission.PermissionKind.Reminders].
 *
 * @see SignalCollector
 */
class RemindersCollector : SignalCollector {
    override val category = SignalCategory.Reminders

    /**
     * Collects the aggregate reminder-count signal for this category.
     *
     * @param context Context providing the content resolver.
     * @return The reminders signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val reminderCount = countQuery(context, CalendarContract.Reminders.CONTENT_URI)
        return buildSignals(category) {
                        addSafe(category, "reminderCount", "Reminder count") {
                signal(category, "reminderCount", "Reminder count", reminderCount.toString(),
                    "Reminder count reveals task management habits.")
            }
        }
    }
}

/**
 * Permissioned collector for [SignalCategory.MusicLibrary].
 *
 * Reports the on-device audio-file count via the [MediaStore] content provider. Only the
 * aggregate count is read — never track titles or file paths.
 *
 * Privacy: music-library size is a media-consumption fingerprint; no audio content is
 * accessed. Gated behind
 * [PermissionKind.MusicLibrary][ltechnologies.onionphone.androwatch.permission.PermissionKind.MusicLibrary].
 *
 * @see SignalCollector
 */
class MusicLibraryCollector : SignalCollector {
    override val category = SignalCategory.MusicLibrary

    /**
     * Collects the aggregate audio-file-count signal for this category.
     *
     * @param context Context providing the content resolver.
     * @return The music-library signals.
     */
    override suspend fun collect(context: Context): List<ltechnologies.onionphone.androwatch.model.FingerprintSignal> {
        val audioCount = countQuery(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        return buildSignals(category) {
                        addSafe(category, "audioCount", "Audio file count") {
                signal(category, "audioCount", "Audio file count", audioCount.toString(),
                    "Music library size is a media consumption fingerprint.")
            }
        }
    }
}

/**
 * Returns `true` if [permission] is currently granted to this app.
 *
 * @param context Context used to check self permissions.
 * @param permission A `Manifest.permission` string.
 * @return `true` when the permission is granted.
 */
private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
