package ltechnologies.onionphone.androwatch.collector

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Enumerates installed text-to-speech voices by briefly initializing a [TextToSpeech] engine.
 *
 * TTS init is asynchronous and must run on the main thread; this bridges the init callback
 * into a cancellable coroutine, caps results at 24 voices and always shuts the engine down.
 *
 * Privacy: the installed voice/locale set is a stable fingerprint that varies by OEM and
 * user installs. No permission is required.
 *
 * @param context Context used to construct the TTS engine (application context is used).
 * @param timeoutMs Maximum time to wait for engine init before returning empty.
 * @return List of `languageTag:voiceName` strings, or empty on failure/timeout.
 */
suspend fun probeTtsVoices(context: Context, timeoutMs: Long = 4_000L): List<String> =
    withContext(Dispatchers.Main.immediate) {
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context.applicationContext) { status ->
                if (!cont.isActive) {
                    tts?.shutdown()
                    return@TextToSpeech
                }
                val voices = if (status == TextToSpeech.SUCCESS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    runCatching {
                        tts?.voices
                            ?.take(24)
                            ?.map { v -> "${v.locale.toLanguageTag()}:${v.name}" }
                            ?: emptyList()
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                tts?.shutdown()
                cont.resume(voices)
            }
            cont.invokeOnCancellation { tts?.shutdown() }
            }
        } ?: emptyList()
    }

/**
 * Returns the device's sensor list, capped at [max] entries and failing soft to empty.
 *
 * Bounding the list prevents pathological work on unusual devices while keeping the sample
 * deterministic (first [max] sensors as reported by the platform).
 *
 * @param sm Sensor manager to enumerate.
 * @param max Maximum number of sensors to return.
 * @return A bounded list of [android.hardware.Sensor], possibly empty.
 */
fun boundedSensorList(
    sm: android.hardware.SensorManager,
    max: Int = 64,
): List<android.hardware.Sensor> = runCatching {
    val all = sm.getSensorList(android.hardware.Sensor.TYPE_ALL)
    when {
        all.isEmpty() -> emptyList()
        all.size <= max -> all
        else -> ArrayList<android.hardware.Sensor>(max).apply {
            for (i in 0 until max) add(all[i])
        }
    }
}.getOrDefault(emptyList())
