package ltechnologies.onionphone.androwatch.model

import kotlinx.serialization.Serializable

/**
 * Rendering hint that tells the UI how a [FingerprintSignal]'s value should be presented.
 *
 * - [Plain] — a single scalar value shown as-is.
 * - [KeyValue] — the signal carries [SignalEntry] pairs rendered as a key/value table.
 * - [Tags] — the value is a set of short tokens rendered as chips/tags.
 * - [Compound] — a composite value combining multiple sub-fields.
 * - [Error] — the probe failed; the row is rendered in an error style and counts as failed.
 *
 * @see FingerprintSignal
 */
@Serializable
enum class DisplayHint {
    Plain,
    KeyValue,
    Tags,
    Compound,
    Error,
}
