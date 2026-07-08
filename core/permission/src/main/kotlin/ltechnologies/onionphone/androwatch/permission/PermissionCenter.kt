package ltechnologies.onionphone.androwatch.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Central resolver mapping a [PermissionKind] to concrete Android runtime permissions
 * and evaluating whether those permissions are currently granted.
 *
 * Keeping this logic in one place isolates API-level differences (e.g. `NEARBY_WIFI_DEVICES`
 * introduced in Android 13 / Tiramisu) from category definitions and collectors.
 *
 * @see PermissionKind
 */
class PermissionCenter {
    /**
     * Returns the concrete `Manifest.permission` strings that back the given [kind].
     *
     * @param kind Logical permission group.
     * @return Array of platform permission identifiers to request for [kind].
     */
    fun permissionsFor(kind: PermissionKind): Array<String> = when (kind) {
        PermissionKind.Motion -> arrayOf(Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.BODY_SENSORS)
        PermissionKind.Location -> arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        PermissionKind.Camera -> arrayOf(Manifest.permission.CAMERA)
        PermissionKind.Bluetooth -> arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        PermissionKind.LocalNetwork -> arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        PermissionKind.Contacts -> arrayOf(Manifest.permission.READ_CONTACTS)
        PermissionKind.Photos -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        PermissionKind.Calendar, PermissionKind.Reminders -> arrayOf(Manifest.permission.READ_CALENDAR)
        PermissionKind.MusicLibrary -> arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
    }

    /**
     * Checks whether the permission group backing [kind] is currently granted.
     *
     * Handles special cases where "granted" means *any* of several permissions:
     * - [PermissionKind.Location] is satisfied by fine **or** coarse location.
     * - [PermissionKind.LocalNetwork] uses `NEARBY_WIFI_DEVICES` on Android 13+ and
     *   falls back to location permission on older releases.
     * All other kinds require *all* of their backing permissions.
     *
     * @param context Context used to check self permissions.
     * @param kind Logical permission group to evaluate.
     * @return `true` if the app currently holds sufficient permission for [kind].
     */
    fun isGranted(context: Context, kind: PermissionKind): Boolean = when (kind) {
        PermissionKind.Location ->
            hasAnyPermission(context, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        PermissionKind.LocalNetwork ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                hasPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                hasAnyPermission(context, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        else ->
            permissionsFor(kind).all { hasPermission(context, it) }
    }

    /** Returns `true` if the single [permission] is granted to this app. */
    private fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Returns `true` if *any* of the supplied [permissions] is granted. */
    private fun hasAnyPermission(context: Context, vararg permissions: String): Boolean =
        permissions.any { hasPermission(context, it) }
}
