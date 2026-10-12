package ltechnologies.onionphone.androwatch

import ltechnologies.onionphone.androwatch.collector.WEBVIEW_SKIP_VALUES
import ltechnologies.onionphone.androwatch.collector.WebViewFingerprintResult
import ltechnologies.onionphone.androwatch.collector.fallbackResult
import ltechnologies.onionphone.androwatch.collector.isWebViewFallbackSentinel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebViewFallbackMappingTest {

    @Test
    fun fallbackResult_writesReasonOnEveryField() {
        val result = fallbackResult("", "timeout")
        assertAllFields(result, "timeout")
    }

    @Test
    fun fallbackResult_parseError_writesReasonOnEveryField() {
        val result = fallbackResult("Mozilla/5.0", "parse_error")
        assertEquals("Mozilla/5.0", result.userAgent)
        assertEquals("parse_error", result.platform)
        assertEquals("parse_error", result.hardwareConcurrency)
        assertEquals("parse_error", result.deviceMemory)
        assertEquals("parse_error", result.canvasHash)
        assertEquals("parse_error", result.webglVendor)
        assertEquals("parse_error", result.webglRenderer)
        assertEquals("parse_error", result.webglHash)
        assertEquals("parse_error", result.timezone)
        assertEquals("parse_error", result.languages)
        assertEquals("parse_error", result.screenSize)
        assertEquals("parse_error", result.colorDepth)
        assertEquals("parse_error", result.pixelRatio)
        assertEquals("parse_error", result.maxTouchPoints)
        assertEquals("parse_error", result.audioContextHash)
    }

    @Test
    fun skipSetContainsKnownSentinels() {
        assertTrue("timeout" in WEBVIEW_SKIP_VALUES)
        assertTrue("parse_error" in WEBVIEW_SKIP_VALUES)
        assertTrue(isWebViewFallbackSentinel("timeout"))
        assertTrue(isWebViewFallbackSentinel("parse_error"))
        assertTrue(isWebViewFallbackSentinel("webview_settings_blocked"))
        assertTrue(isWebViewFallbackSentinel("webview_unavailable:RuntimeException"))
        assertTrue(isWebViewFallbackSentinel(""))
        assertFalse(isWebViewFallbackSentinel("Linux armv8l"))
        assertFalse(isWebViewFallbackSentinel("8"))
    }

    private fun assertAllFields(result: WebViewFingerprintResult, reason: String) {
        assertEquals(reason, result.userAgent)
        assertEquals(reason, result.platform)
        assertEquals(reason, result.hardwareConcurrency)
        assertEquals(reason, result.deviceMemory)
        assertEquals(reason, result.canvasHash)
        assertEquals(reason, result.webglVendor)
        assertEquals(reason, result.webglRenderer)
        assertEquals(reason, result.webglHash)
        assertEquals(reason, result.timezone)
        assertEquals(reason, result.languages)
        assertEquals(reason, result.screenSize)
        assertEquals(reason, result.colorDepth)
        assertEquals(reason, result.pixelRatio)
        assertEquals(reason, result.maxTouchPoints)
        assertEquals(reason, result.audioContextHash)
    }
}
