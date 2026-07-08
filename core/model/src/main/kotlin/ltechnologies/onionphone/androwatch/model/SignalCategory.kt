package ltechnologies.onionphone.androwatch.model

import ltechnologies.onionphone.androwatch.permission.PermissionKind

/**
 * Enumerates every fingerprintable data category AndroWatch probes on the device.
 *
 * Each entry couples a category with its [Sensitivity] tier (which governs whether
 * a runtime permission or explicit user opt-in is required) and, for permissioned
 * categories, the [PermissionKind] that must be granted before collection runs.
 *
 * Categories are grouped by tier:
 * - **Passive** — readable with no runtime permission (e.g. [SystemInfo], [Display]).
 * - **Permissioned** — gated behind a runtime permission (e.g. [Location], [Cameras]).
 * - **Advanced** — high-signal or intrusive probes requiring explicit opt-in
 *   (e.g. [InstalledAppsProbe], [WebViewFingerprint]).
 *
 * @property sensitivity Tier controlling access requirements and UI treatment.
 * @property permissionKind Runtime permission gate; `null` for passive/advanced categories.
 * @see Sensitivity
 * @see PermissionKind
 */
enum class SignalCategory(
    val sensitivity: Sensitivity,
    val permissionKind: PermissionKind? = null,
) {
    DeviceIdentity(Sensitivity.Passive),
    GoogleAccount(Sensitivity.Passive),
    SystemInfo(Sensitivity.Passive),
    Display(Sensitivity.Passive),
    Locale(Sensitivity.Passive),
    Accessibility(Sensitivity.Passive),
    DeviceMotion(Sensitivity.Passive),
    Battery(Sensitivity.Passive),
    Storage(Sensitivity.Passive),
    Network(Sensitivity.Passive),
    Fonts(Sensitivity.Passive),
    InstalledVoices(Sensitivity.Passive),
    AppInfo(Sensitivity.Passive),
    Pasteboard(Sensitivity.Passive),
    Audio(Sensitivity.Passive),
    Graphics(Sensitivity.Passive),
    Telephony(Sensitivity.Passive),

    Motion(Sensitivity.Permissioned, PermissionKind.Motion),
    Location(Sensitivity.Permissioned, PermissionKind.Location),
    Cameras(Sensitivity.Permissioned, PermissionKind.Camera),
    Bluetooth(Sensitivity.Permissioned, PermissionKind.Bluetooth),
    LocalNetwork(Sensitivity.Permissioned, PermissionKind.LocalNetwork),
    Contacts(Sensitivity.Permissioned, PermissionKind.Contacts),
    Photos(Sensitivity.Permissioned, PermissionKind.Photos),
    Calendar(Sensitivity.Permissioned, PermissionKind.Calendar),
    Reminders(Sensitivity.Permissioned, PermissionKind.Reminders),
    MusicLibrary(Sensitivity.Permissioned, PermissionKind.MusicLibrary),

    InstalledAppsProbe(Sensitivity.Advanced),
    WebViewFingerprint(Sensitivity.Advanced),
    PreviousInstallsLog(Sensitivity.Advanced),
}
