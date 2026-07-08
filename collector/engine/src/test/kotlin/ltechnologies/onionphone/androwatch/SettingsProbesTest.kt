package ltechnologies.onionphone.androwatch

import ltechnologies.onionphone.androwatch.collector.sha256Hex
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsProbesTest {
    @Test
    fun secureHash_differsByInput() {
        val a = sha256Hex("com.foo/.BarService")
        val b = sha256Hex("com.baz/.QuxService")
        assertNotEquals(a, b)
        assertTrue(a.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
