package ltechnologies.onionphone.androwatch

import ltechnologies.onionphone.androwatch.collector.RomCompatibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RomCompatibilityTest {
    @Test
    fun detectRomProfileReturnsNonBlankFamily() {
        val profile = RomCompatibility.detectRomProfile()
        assertTrue(profile.family.isNotBlank())
        assertTrue(profile.confidence in listOf("low", "medium", "high"))
        assertTrue(profile.buildIntegrity.isNotBlank())
    }

    @Test
    fun readPropertyReturnsFallbackOnJvm() {
        val value = RomCompatibility.readProperty("ro.nonexistent.test.key", "fallback")
        assertEquals("fallback", value)
    }
}
