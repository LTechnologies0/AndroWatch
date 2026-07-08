package ltechnologies.onionphone.androwatch.collector

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import ltechnologies.onionphone.androwatch.model.FingerprintSignal
import ltechnologies.onionphone.androwatch.model.SignalCategory
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.sample
import kotlin.time.Duration.Companion.milliseconds

/**
 * Emits live [SignalCategory.Battery] signals from the `ACTION_BATTERY_CHANGED` broadcast.
 *
 * Registers a broadcast receiver and pushes updated level/charging/status/plug signals,
 * sampled to at most one update per second and de-duplicated. The receiver is unregistered
 * when the flow is cancelled (`awaitClose`).
 *
 * Privacy: live battery level can correlate app sessions over time. No permission required.
 *
 * @param context Context used to register the battery broadcast receiver.
 * @return Cold flow of battery signal snapshots.
 * @see LiveSignalCollector
 */
fun batterySignalsFlow(context: Context): Flow<List<FingerprintSignal>> = callbackFlow {
    val category = SignalCategory.Battery
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (scale > 0) level * 100 / scale else bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            trySend(
                listOf(
                    signal(category, "level", "Battery level", "$pct%", "Live battery level can correlate sessions."),
                    signal(category, "charging", "Charging", bm.isCharging.toString(), "Charging state changes in real time."),
                    signal(category, "status", "Status code", status.toString(), "Battery status updates frequently."),
                    signal(category, "plugged", "Plug type", plugged.toString(), "Power source type is a usage signal."),
                ),
            )
        }
    }
    context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    awaitClose { runCatching { context.unregisterReceiver(receiver) } }
}.buffer(capacity = 4).flowOn(CollectorDispatchers.default).sample(1_000.milliseconds).distinctUntilChanged()

/**
 * Emits live [SignalCategory.DeviceMotion] signals from the accelerometer.
 *
 * Registers a [SensorEventListener] at UI delay and pushes formatted x/y/z readings plus
 * sensor accuracy, sampled to every 500ms and de-duplicated. Falls back to a single
 * `unavailable` signal when no accelerometer exists. The listener is unregistered on
 * cancellation.
 *
 * Privacy: live accelerometer readings are high-entropy session signals. Reading the
 * accelerometer itself requires no runtime permission.
 *
 * @param context Context used to obtain [SensorManager].
 * @return Cold flow of motion signal snapshots.
 * @see LiveSignalCollector
 */
fun motionSignalsFlow(context: Context): Flow<List<FingerprintSignal>> = callbackFlow {
    val category = SignalCategory.DeviceMotion
    val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    if (accel == null) {
        trySend(listOf(signal(category, "live", "Live motion", "unavailable", "No accelerometer.")))
        awaitClose {}
        return@callbackFlow
    }
    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event ?: return
            val x = String.format("%.2f", event.values.getOrElse(0) { 0f })
            val y = String.format("%.2f", event.values.getOrElse(1) { 0f })
            val z = String.format("%.2f", event.values.getOrElse(2) { 0f })
            trySend(
                listOf(
                    signal(category, "accel", "Accelerometer (live)", "$x, $y, $z",
                        "Live accelerometer readings are high-entropy session signals."),
                    signal(category, "accuracy", "Sensor accuracy", event.accuracy.toString(),
                        "Accuracy changes with calibration and environment."),
                ),
            )
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
    sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
    awaitClose { sm.unregisterListener(listener) }
}.buffer(capacity = 4).flowOn(CollectorDispatchers.default).sample(500.milliseconds).distinctUntilChanged()

/**
 * Emits live [SignalCategory.Audio] signals as audio routing changes.
 *
 * Emits an initial snapshot then registers an [AudioDeviceCallback] to re-emit output
 * device count/types and music-active state whenever accessories connect or disconnect.
 * De-duplicated; the callback is unregistered on cancellation.
 *
 * Privacy: connected audio devices can reveal user-owned accessories. No permission required.
 *
 * @param context Context used to obtain [AudioManager].
 * @return Cold flow of audio-route signal snapshots.
 * @see LiveSignalCollector
 */
fun audioSignalsFlow(context: Context): Flow<List<FingerprintSignal>> = callbackFlow {
    val category = SignalCategory.Audio
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    fun emitDevices() {
        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val summary = outputs.joinToString { deviceTypeLabel(it.type) }
        trySend(
            listOf(
                signal(category, "outputCount", "Output devices (live)", outputs.size.toString(),
                    "Audio route changes when accessories connect."),
                signal(category, "outputs", "Output types (live)", summary.ifBlank { "none" },
                    "Connected audio devices can reveal user-owned accessories."),
                signal(category, "musicActive", "Music active (live)", am.isMusicActive.toString(),
                    "Playback state is a session signal."),
            ),
        )
    }
    emitDevices()
    val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = emitDevices()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = emitDevices()
    }
    am.registerAudioDeviceCallback(callback, null)
    awaitClose { am.unregisterAudioDeviceCallback(callback) }
}.buffer(capacity = 4).flowOn(CollectorDispatchers.default).distinctUntilChanged()

/** Maps an [AudioDeviceInfo] type constant to a short human-readable label. */
private fun deviceTypeLabel(type: Int): String = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bt-a2dp"
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bt-sco"
    AudioDeviceInfo.TYPE_USB_DEVICE -> "usb"
    else -> "type-$type"
}
