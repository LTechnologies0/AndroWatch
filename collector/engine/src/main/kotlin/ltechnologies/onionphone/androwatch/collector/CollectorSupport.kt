package ltechnologies.onionphone.androwatch.collector

import android.content.Context
import ltechnologies.onionphone.androwatch.model.DisplayHint
import ltechnologies.onionphone.androwatch.model.FingerprintSignal
import ltechnologies.onionphone.androwatch.model.SignalCategory
import ltechnologies.onionphone.androwatch.model.SignalEntry
import java.security.MessageDigest

/**
 * Builds a [FingerprintSignal] that carries both a human-friendly [summary] and the
 * original [raw] value (retained in [FingerprintSignal.rawValue]).
 *
 * @param category Owning category (supplies id prefix and sensitivity).
 * @param key Probe key, combined with the category name to form the signal id.
 * @param name Display name.
 * @param summary Interpreted/presented value.
 * @param raw Original untruncated value kept for export/debugging.
 * @param rationale Why this signal is fingerprintable.
 * @param displayHint How the UI should render the value.
 * @param entries Optional key/value sub-fields.
 * @return The assembled signal.
 */
fun interpretedSignal(
    category: SignalCategory,
    key: String,
    name: String,
    summary: String,
    raw: String,
    rationale: String,
    displayHint: DisplayHint = DisplayHint.Plain,
    entries: List<SignalEntry> = emptyList(),
): FingerprintSignal = FingerprintSignal(
    id = "${category.name}.$key",
    name = name,
    value = summary,
    rationale = rationale,
    sensitivity = category.sensitivity,
    displayHint = displayHint,
    entries = entries,
    rawValue = raw,
)

/**
 * Builds a plain [FingerprintSignal] for a single [value].
 *
 * @param category Owning category (supplies id prefix and sensitivity).
 * @param key Probe key, combined with the category name to form the signal id.
 * @param name Display name.
 * @param value Presented value.
 * @param rationale Why this signal is fingerprintable.
 * @param displayHint How the UI should render the value.
 * @param entries Optional key/value sub-fields.
 * @return The assembled signal.
 */
fun signal(
    category: SignalCategory,
    key: String,
    name: String,
    value: String,
    rationale: String,
    displayHint: DisplayHint = DisplayHint.Plain,
    entries: List<SignalEntry> = emptyList(),
): FingerprintSignal = FingerprintSignal(
    id = "${category.name}.$key",
    name = name,
    value = value,
    rationale = rationale,
    sensitivity = category.sensitivity,
    displayHint = displayHint,
    entries = entries,
)

/** Sentinel [FingerprintSignal.value] marking a probe that failed to collect. */
const val COLLECTION_FAILED = "collection_failed"

/**
 * Builds an error [FingerprintSignal] ([DisplayHint.Error]) representing a failed probe.
 *
 * The exception class and message are stored in [FingerprintSignal.rawValue] for diagnostics.
 *
 * @param category Owning category.
 * @param key Probe key.
 * @param name Display name.
 * @param error The throwable that caused the failure.
 * @param rationale Why this signal is fingerprintable.
 * @return An error signal counted as failed by [ltechnologies.onionphone.androwatch.model.failedCount].
 */
fun failedSignal(
    category: SignalCategory,
    key: String,
    name: String,
    error: Throwable,
    rationale: String = "Device API fingerprint signal.",
): FingerprintSignal = FingerprintSignal(
    id = "${category.name}.$key",
    name = name,
    value = COLLECTION_FAILED,
    rationale = rationale,
    sensitivity = category.sensitivity,
    displayHint = DisplayHint.Error,
    rawValue = "${error.javaClass.simpleName}: ${error.message ?: "unknown"}",
)

/**
 * Builds a [DisplayHint.Tags] signal whose value is a comma-joined list of [tags].
 *
 * @param category Owning category.
 * @param key Probe key.
 * @param name Display name.
 * @param tags Tokens rendered as chips in the UI.
 * @param rationale Why this signal is fingerprintable.
 * @return The assembled tags signal.
 */
fun tagsSignal(
    category: SignalCategory,
    key: String,
    name: String,
    tags: List<String>,
    rationale: String,
): FingerprintSignal = signal(
    category = category,
    key = key,
    name = name,
    value = tags.joinToString(", "),
    rationale = rationale,
    displayHint = DisplayHint.Tags,
)

/**
 * Builds a [DisplayHint.Compound] signal from key/value [entries]; the flat value is a
 * newline-joined `key: value` rendering for exports.
 *
 * @param category Owning category.
 * @param key Probe key.
 * @param name Display name.
 * @param entries Sub-fields rendered as a key/value table.
 * @param rationale Why this signal is fingerprintable.
 * @return The assembled compound signal.
 */
fun compoundSignal(
    category: SignalCategory,
    key: String,
    name: String,
    entries: List<SignalEntry>,
    rationale: String,
): FingerprintSignal = signal(
    category = category,
    key = key,
    name = name,
    value = entries.joinToString("\n") { "${it.key}: ${it.value}" },
    rationale = rationale,
    displayHint = DisplayHint.Compound,
    entries = entries,
)

/**
 * Runs [block], returning its string result or `"unavailable"` if it throws.
 *
 * @param block Value-producing lambda that may fail.
 * @return The produced string, or `"unavailable"`.
 */
fun safeString(block: () -> String): String = runCatching(block).getOrElse { "unavailable" }

/**
 * Runs [block] and stringifies its int result, returning `"unavailable"` if it throws.
 *
 * @param block Int-producing lambda that may fail.
 * @return The int as a string, or `"unavailable"`.
 */
fun safeInt(block: () -> Int): String = runCatching { block().toString() }.getOrElse { "unavailable" }

/**
 * Computes the lowercase hex SHA-256 digest of [input].
 *
 * Used to hash identifying values (e.g. hardware names) so signals remain comparable for
 * fingerprint uniqueness without exposing the raw identifier.
 *
 * @param input UTF-8 string to hash.
 * @return 64-character lowercase hex digest.
 */
fun sha256Hex(input: String): String =
    bytesToHex(MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)))

private val HEX_CHARS = "0123456789abcdef".toCharArray()

/**
 * Converts a byte array to a lowercase hex string.
 *
 * @param bytes Bytes to encode.
 * @return Hex representation, or an empty string when [bytes] is empty.
 */
fun bytesToHex(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""
    val out = CharArray(bytes.size shl 1)
    var o = 0
    for (b in bytes) {
        val v = b.toInt() and 0xff
        out[o++] = HEX_CHARS[v ushr 4]
        out[o++] = HEX_CHARS[v and 0x0f]
    }
    return String(out)
}

/**
 * Counts the rows a content-provider query returns without exposing their contents.
 *
 * Queries only the `_id` column so the collector learns a count (e.g. number of contacts)
 * without reading actual records; any failure yields `0`.
 *
 * Privacy: returns only an aggregate count, never row data. The caller is responsible for
 * having the permission the [uri] requires.
 *
 * @param context Context providing the content resolver.
 * @param uri Content URI to query.
 * @param selection Optional SQL-style selection filter.
 * @return Number of matching rows, or `0` on failure.
 */
fun countQuery(context: Context, uri: android.net.Uri, selection: String? = null): Int =
    runCatching {
        context.contentResolver.query(uri, arrayOf("_id"), selection, null, null)?.use { cursor ->
            cursor.count
        } ?: 0
    }.getOrDefault(0)
