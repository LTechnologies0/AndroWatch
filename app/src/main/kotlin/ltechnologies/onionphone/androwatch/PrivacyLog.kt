package ltechnologies.onionphone.androwatch

import android.util.Log
import ltechnologies.onionphone.androwatch.BuildConfig

/**
 * Privacy-safe logging: emits only a sanitized event name plus a boolean outcome, never any
 * collected values or PII.
 *
 * Keeping logs to `event=true/false` pairs ensures no fingerprintable data leaks into logcat.
 */
object PrivacyLog {
    private const val TAG = "OpPrivacy"

    /**
     * Logs a boolean outcome for a named event.
     *
     * @param event Event identifier; sanitized to `[a-zA-Z0-9._-]` and truncated to 64 chars.
     * @param ok Outcome flag logged as `true`/`false`.
     */
    fun flag(event: String, ok: Boolean) {
        Log.i(TAG, sanitizeEvent(event) + "=" + if (ok) "true" else "false")
    }

    /**
     * Strips disallowed characters and caps length so log lines cannot carry arbitrary data.
     *
     * @param event Raw event name.
     * @return Sanitized event name (max 64 chars).
     */
    private fun sanitizeEvent(event: String): String =
        event.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(64)
}
