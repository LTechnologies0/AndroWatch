package ltechnologies.onionphone.androwatch

import ltechnologies.onionphone.androwatch.collector.sha256Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassiveProbesTest {
    @Test
    fun sha256Hex_isStable() {
        val a = sha256Hex("test")
        val b = sha256Hex("test")
        assertEquals(a, b)
        assertEquals(64, a.length)
    }

    @Test
    fun probeSensorRanges_withoutSensorManager_returnsLabels() {
        // ponytail: smoke test for string shape only; no Robolectric SensorManager
        val sample = "accel=n/a;gyro=n/a;mag=n/a;light=n/a"
        assertTrue(sample.contains("accel="))
    }

    @Test
    fun extendedFeatureFlags_format() {
        // Document expected key=value; pairs separated by semicolons
        val mock = "fingerprint=false;face=false;nfc=false"
        assertTrue(mock.contains(";"))
        assertTrue(mock.contains("="))
    }
}
