package ltechnologies.onionphone.androwatch.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Central resolver mapping a [PermissionKind] to concrete Android runtime permissions
 * and evaluating whether those permissions are currently granted.
 *
 * Keeping this logic in one place isolates API-level differences (e.g. `NEARBY_WIFI_DEVICES`
 * introduced in Android 13 / Tiramisu, `READ_MEDIA_VISUAL_USER_SELECTED` on Android 14)
 * from category definitions and collectors.
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
        PermissionKind.Motion -> arrayOf(Manifest.permission.ACTIVITY_RECOGNITION)
        PermissionKind.Location -> arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        PermissionKind.Camera -> arrayOf(Manifest.permission.CAMERA)
        PermissionKind.Bluetooth -> bluetoothPermissions()
        PermissionKind.LocalNetwork -> localNetworkPermissions()
        PermissionKind.Contacts -> arrayOf(Manifest.permission.READ_CONTACTS)
        PermissionKind.Photos -> photosPermissions()
        PermissionKind.Calendar, PermissionKind.Reminders -> arrayOf(Manifest.permission.READ_CALENDAR)
        PermissionKind.MusicLibrary -> musicPermissions()
    }

    /**
     * Checks whether the permission group backing [kind] is currently granted.
     *
     * Handles special cases where "granted" means *any* of several permissions:
     * - [PermissionKind.Location] is satisfied by fine **or** coarse location.
     * - [PermissionKind.Photos] is satisfied by images, video, or Android 14+
     *   `READ_MEDIA_VISUAL_USER_SELECTED` (partial gallery grant).
     * - [PermissionKind.Motion] requires only `ACTIVITY_RECOGNITION`; body sensors
     *   remain optional and are not part of the gate.
     * - [PermissionKind.LocalNetwork] uses `NEARBY_WIFI_DEVICES` on Android 13+ and
     *   falls back to location permission on older releases.
     * All other kinds require *all* of their backing permissions.
     *
     * @param context Context used to check self permissions.
     * @param kind Logical permission group to evaluate.
     * @return `true` if the app currently holds sufficient permission for [kind].
     */
    fun isGranted(context: Context, kind: PermissionKind): Boolean = when (kind) {
        PermissionKind.Motion ->
            hasPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
        PermissionKind.Location ->
            hasAnyPermission(context, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        PermissionKind.Photos -> photosGranted(context)
        PermissionKind.LocalNetwork -> localNetworkGranted(context)
        else ->
            permissionsFor(kind).all { hasPermission(context, it) }
    }

    /** Returns `true` if the single [permission] is granted to this app. */
    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Returns `true` if *any* of the supplied [permissions] is granted. */
    fun hasAnyPermission(context: Context, vararg permissions: String): Boolean =
        permissions.any { hasPermission(context, it) }

    private fun photosPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    private fun photosGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val visualSelected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                hasPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            return visualSelected ||
                hasAnyPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                )
        }
        return hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun bluetoothPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH)
        }

    private fun localNetworkPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun localNetworkGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            hasAnyPermission(context, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }

    private fun musicPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
}
