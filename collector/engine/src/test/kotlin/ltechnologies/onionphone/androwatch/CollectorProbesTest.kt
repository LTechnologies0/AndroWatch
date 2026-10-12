package ltechnologies.onionphone.androwatch

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import ltechnologies.onionphone.androwatch.collector.probeBatteryExtended
import ltechnologies.onionphone.androwatch.collector.putSafe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CollectorProbesTest {

    @Test
    fun cycleCount_readsExtraOnApi34() {
        val map = probeBatteryExtended(batteryManager(), stickyIntent(cycleCount = 17))
        assertEquals("17", map["cycleCount"])
        assertEquals("Li-ion", map["technology"])
        assertEquals(BatteryManager.BATTERY_PLUGGED_USB.toString(), map["plugged"])
    }

    @Test
    fun cycleCount_omitsSentinelMinValue() {
        val map = probeBatteryExtended(batteryManager(), stickyIntent(cycleCount = Int.MIN_VALUE))
        assertFalse(map.containsKey("cycleCount"))
    }

    @Test
    fun putSafe_keepsSuccessAndSkipsThrowOrBlank() {
        val map = buildMap<String, String> {
            putSafe("ok") { "yes" }
            putSafe("bad") { error("nope") }
            putSafe("blank") { "   " }
            putSafe("nullish") { null }
        }
        assertEquals(mapOf("ok" to "yes"), map)
    }

    private fun batteryManager(): BatteryManager =
        RuntimeEnvironment.getApplication().getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    private fun stickyIntent(cycleCount: Int): Intent =
        Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_CYCLE_COUNT, cycleCount)
            .putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
            .putExtra(BatteryManager.EXTRA_TECHNOLOGY, "Li-ion")
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class CollectorProbesLegacyApiTest {

    @Test
    fun cycleCount_absentBelowApi34() {
        val bm = RuntimeEnvironment.getApplication()
            .getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_CYCLE_COUNT, 9)
        val map = probeBatteryExtended(bm, intent)
        assertFalse(map.containsKey("cycleCount"))
    }
}
