package ltechnologies.onionphone.androwatch

import ltechnologies.onionphone.androwatch.collector.sha256Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CollectorSupportTest {
    @Test
    fun sha256HexIsDeterministic() {
        val a = sha256Hex("test-input")
        val b = sha256Hex("test-input")
        assertEquals(a, b)
        assertEquals(64, a.length)
    }

    @Test
    fun sha256HexDiffersForDifferentInput() {
        assertNotEquals(sha256Hex("a"), sha256Hex("b"))
    }
}
