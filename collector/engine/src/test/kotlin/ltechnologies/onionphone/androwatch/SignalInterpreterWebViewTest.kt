package ltechnologies.onionphone.androwatch

import ltechnologies.onionphone.androwatch.collector.SignalInterpreter
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalInterpreterWebViewTest {
    @Test
    fun webViewUserAgent_mentionsChrome() {
        val ua = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile"
        assertTrue(SignalInterpreter.webViewUserAgent(ua).contains("Chrome 120"))
    }

    @Test
    fun canvasFingerprint_truncatesLongHash() {
        val hash = "a".repeat(64)
        assertTrue(SignalInterpreter.canvasFingerprint(hash).contains("…"))
    }

    @Test
    fun maxTouchPoints_phoneClass() {
        assertTrue(SignalInterpreter.maxTouchPoints("1").contains("phone"))
    }
}
