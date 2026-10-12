package ltechnologies.onionphone.androwatch

import android.net.Uri
import ltechnologies.onionphone.androwatch.collector.countQuery
import ltechnologies.onionphone.androwatch.collector.sha256Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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

    @Test
    fun countQuery_returnsNullOnFailure() {
        val ctx = RuntimeEnvironment.getApplication()
        val result = countQuery(ctx, Uri.parse("content://not.a.real.provider/items"))
        assertNull(result)
    }
}
