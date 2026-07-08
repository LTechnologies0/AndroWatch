package ltechnologies.onionphone.androwatch.permission

/**
 * Logical grouping of Android runtime permissions required by permissioned categories.
 *
 * Each kind maps to one or more concrete `Manifest.permission` entries via
 * [PermissionCenter.permissionsFor]. Grouping decouples category definitions from
 * the exact platform permission strings, which vary by API level.
 *
 * @see PermissionCenter
 * @see ltechnologies.onionphone.androwatch.model.SignalCategory
 */
enum class PermissionKind {
    Motion,
    Location,
    Camera,
    Bluetooth,
    LocalNetwork,
    Contacts,
    Photos,
    Calendar,
    Reminders,
    MusicLibrary,
}
