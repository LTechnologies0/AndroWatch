package ltechnologies.onionphone.androwatch.permission

import android.Manifest
import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PermissionCenterTest {

    private val center = PermissionCenter()
    private val context: Application = RuntimeEnvironment.getApplication()

    private fun grant(vararg permissions: String) {
        Shadows.shadowOf(context).grantPermissions(*permissions)
    }

    @Test
    fun photosGranted_ifImagesOnly() {
        grant(Manifest.permission.READ_MEDIA_IMAGES)
        assertTrue(center.isGranted(context, PermissionKind.Photos))
    }

    @Test
    fun photosGranted_ifVideoOnly() {
        grant(Manifest.permission.READ_MEDIA_VIDEO)
        assertTrue(center.isGranted(context, PermissionKind.Photos))
    }

    @Test
    fun photosGranted_ifVisualUserSelectedOnly() {
        grant(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        assertTrue(center.isGranted(context, PermissionKind.Photos))
    }

    @Test
    fun photosDenied_whenNoneGranted() {
        assertFalse(center.isGranted(context, PermissionKind.Photos))
    }

    @Test
    fun photosRequestIncludesVisualUserSelectedOnApi34() {
        val requested = center.permissionsFor(PermissionKind.Photos).toSet()
        assertTrue(requested.contains(Manifest.permission.READ_MEDIA_IMAGES))
        assertTrue(requested.contains(Manifest.permission.READ_MEDIA_VIDEO))
        assertTrue(requested.contains(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
    }

    @Test
    fun motionGranted_withActivityRecognitionOnly() {
        grant(Manifest.permission.ACTIVITY_RECOGNITION)
        assertTrue(center.isGranted(context, PermissionKind.Motion))
        assertFalse(center.permissionsFor(PermissionKind.Motion).contains(Manifest.permission.BODY_SENSORS))
    }

    @Test
    fun motionDenied_withoutActivityRecognition() {
        grant(Manifest.permission.BODY_SENSORS)
        assertFalse(center.isGranted(context, PermissionKind.Motion))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class PermissionCenterApi32Test {

    private val center = PermissionCenter()

    @Test
    fun photosRequestsReadExternalStorageBelow33() {
        val requested = center.permissionsFor(PermissionKind.Photos).toList()
        org.junit.Assert.assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), requested)
    }

    @Test
    fun bluetoothRequestsScanConnectFromApi31() {
        val requested = center.permissionsFor(PermissionKind.Bluetooth).toSet()
        assertTrue(requested.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(requested.contains(Manifest.permission.BLUETOOTH_CONNECT))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PermissionCenterApi30Test {

    private val center = PermissionCenter()

    @Test
    fun bluetoothRequestsLegacyPermissionBelow31() {
        val requested = center.permissionsFor(PermissionKind.Bluetooth).toList()
        org.junit.Assert.assertEquals(listOf(Manifest.permission.BLUETOOTH), requested)
    }

    @Test
    fun localNetworkFallsBackToFineLocation() {
        val requested = center.permissionsFor(PermissionKind.LocalNetwork).toList()
        org.junit.Assert.assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), requested)
    }

    @Test
    fun musicRequestsReadExternalStorage() {
        val requested = center.permissionsFor(PermissionKind.MusicLibrary).toList()
        org.junit.Assert.assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), requested)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PermissionCenterApi33Test {

    private val center = PermissionCenter()

    @Test
    fun photosOmitsVisualUserSelected() {
        val requested = center.permissionsFor(PermissionKind.Photos).toSet()
        assertTrue(requested.contains(Manifest.permission.READ_MEDIA_IMAGES))
        assertTrue(requested.contains(Manifest.permission.READ_MEDIA_VIDEO))
        assertFalse(requested.contains(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
    }

    @Test
    fun localNetworkRequestsNearbyWifi() {
        val requested = center.permissionsFor(PermissionKind.LocalNetwork).toList()
        org.junit.Assert.assertEquals(listOf(Manifest.permission.NEARBY_WIFI_DEVICES), requested)
    }

    @Test
    fun musicRequestsReadMediaAudio() {
        val requested = center.permissionsFor(PermissionKind.MusicLibrary).toList()
        org.junit.Assert.assertEquals(listOf(Manifest.permission.READ_MEDIA_AUDIO), requested)
    }
}
