package ltechnologies.onionphone.androwatch.model

import kotlinx.serialization.Serializable

/**
 * A single key/value pair used by signals rendered with [DisplayHint.KeyValue].
 *
 * @property key Human-readable label for the sub-field.
 * @property value Stringified value for the sub-field.
 */
@Serializable
data class SignalEntry(
    val key: String,
    val value: String,
)

/**
 * One fingerprintable data point collected from the device.
 *
 * This is the core domain type produced by every [SignalCollector] and consumed by
 * the UI and the report exporter. Instances are immutable and serializable so they
 * can be persisted or exported.
 *
 * @property id Stable unique identifier for the signal (typically `category.probeKey`).
 * @property name Human-readable display name.
 * @property value Presented value (may be truncated/normalized for display).
 * @property rationale Short explanation of why this signal is fingerprintable / its privacy relevance.
 * @property sensitivity Tier of the owning category.
 * @property displayHint How the UI should render [value] / [entries].
 * @property entries Optional key/value sub-fields for [DisplayHint.KeyValue] rows.
 * @property rawValue Optional untruncated/original value retained for export or debugging.
 * @see SignalCategory
 * @see DisplayHint
 */
@Serializable
data class FingerprintSignal(
    val id: String,
    val name: String,
    val value: String,
    val rationale: String,
    val sensitivity: Sensitivity,
    val displayHint: DisplayHint = DisplayHint.Plain,
    val entries: List<SignalEntry> = emptyList(),
    val rawValue: String? = null,
)
