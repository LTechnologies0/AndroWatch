package ltechnologies.onionphone.androwatch.model

import kotlinx.serialization.Serializable

/**
 * Access/privacy tier for a [SignalCategory] and its collected signals.
 *
 * - [Passive] — collectable with no runtime permission and minimal privacy impact.
 * - [Permissioned] — requires a granted runtime permission (see [SignalCategory.permissionKind]).
 * - [Advanced] — intrusive or high-signal probes requiring explicit user opt-in.
 *
 * @see SignalCategory
 */
@Serializable
enum class Sensitivity {
    Passive,
    Permissioned,
    Advanced,
}
