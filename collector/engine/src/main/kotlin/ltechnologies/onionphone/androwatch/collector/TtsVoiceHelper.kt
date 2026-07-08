package ltechnologies.onionphone.androwatch.collector

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Summary of installed TTS voice locales.
 *
 * @property localeCount Number of distinct voice locales.
 * @property localeSample Comma-joined sample of up to 8 locale tags (or a status string).
 */
data class TtsVoiceSummary(
    val localeCount: Int,
    val localeSample: String,
)

/**
 * Collects a summary of installed TTS voice locales via a short-lived [TextToSpeech] engine.
 *
 * Like [probeTtsVoices], initializes the engine on the main thread, reads distinct sorted
 * voice locales, then shuts the engine down. Returns an `unavailable` summary below API 21.
 *
 * Privacy: voice-locale inventory is a stable fingerprint; no permission required.
 *
 * @param context Context used to construct the TTS engine (application context is used).
 * @return A [TtsVoiceSummary] with locale count and sample.
 */
suspend fun collectTtsVoiceSummary(context: Context): TtsVoiceSummary {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
        return TtsVoiceSummary(0, "unavailable (API < 21)")
    }
    return withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { cont ->
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context.applicationContext) { status ->
                val summary = if (status == TextToSpeech.SUCCESS) {
                    val locales = tts?.voices.orEmpty()
                        .mapNotNull { it.locale?.toLanguageTag() }
                        .distinct()
                        .sorted()
                    TtsVoiceSummary(
                        localeCount = locales.size,
                        localeSample = locales.take(8).joinToString().ifBlank { "none" },
                    )
                } else {
                    TtsVoiceSummary(0, "init_failed")
                }
                tts?.shutdown()
                if (cont.isActive) cont.resume(summary)
            }
            cont.invokeOnCancellation { tts?.shutdown() }
        }
    }
}
