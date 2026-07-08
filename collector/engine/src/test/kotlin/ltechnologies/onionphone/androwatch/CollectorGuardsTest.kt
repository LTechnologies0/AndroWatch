package ltechnologies.onionphone.androwatch

import ltechnologies.onionphone.androwatch.collector.addOptional
import ltechnologies.onionphone.androwatch.collector.addSafe
import ltechnologies.onionphone.androwatch.collector.buildSignals
import ltechnologies.onionphone.androwatch.collector.forEachProbe
import ltechnologies.onionphone.androwatch.collector.signal
import ltechnologies.onionphone.androwatch.model.DisplayHint
import ltechnologies.onionphone.androwatch.model.SignalCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectorGuardsTest {
    @Test
    fun addSafe_keepsOkAndEmitsFailedSignal() {
        val signals = buildSignals(SignalCategory.SystemInfo) {
            addSafe(SignalCategory.SystemInfo, "ok", "OK") {
                signal(SignalCategory.SystemInfo, "ok", "OK", "yes", "test")
            }
            addSafe(SignalCategory.SystemInfo, "bad", "Bad") {
                error("boom")
                @Suppress("UNREACHABLE_CODE")
                signal(SignalCategory.SystemInfo, "bad", "Bad", "no", "test")
            }
            addSafe(SignalCategory.SystemInfo, "alsoOk", "Also OK") {
                signal(SignalCategory.SystemInfo, "alsoOk", "Also OK", "yes", "test")
            }
        }
        assertEquals(3, signals.size)
        assertTrue(signals.any { it.id.endsWith(".ok") && it.displayHint != DisplayHint.Error })
        assertTrue(signals.any { it.id.endsWith(".alsoOk") })
        val failed = signals.first { it.id.endsWith(".bad") }
        assertEquals(DisplayHint.Error, failed.displayHint)
        assertTrue(failed.rawValue?.contains("boom") == true)
    }

    @Test
    fun addOptional_skipsFailedProbe() {
        val signals = buildSignals(SignalCategory.SystemInfo) {
            addOptional(SignalCategory.SystemInfo, "ok", "OK") {
                signal(SignalCategory.SystemInfo, "ok", "OK", "yes", "test")
            }
            addOptional(SignalCategory.SystemInfo, "bad", "Bad") {
                error("nope")
                @Suppress("UNREACHABLE_CODE")
                signal(SignalCategory.SystemInfo, "bad", "Bad", "no", "test")
            }
        }
        assertEquals(1, signals.size)
        assertTrue(signals[0].id.endsWith(".ok"))
    }

    @Test
    fun buildSignals_preservesPartialOnUncaught() {
        val signals = buildSignals(SignalCategory.SystemInfo) {
            addSafe(SignalCategory.SystemInfo, "ok", "OK") {
                signal(SignalCategory.SystemInfo, "ok", "OK", "yes", "test")
            }
            error("boom after first")
            @Suppress("UNREACHABLE_CODE")
            addSafe(SignalCategory.SystemInfo, "never", "Never") {
                signal(SignalCategory.SystemInfo, "never", "Never", "no", "test")
            }
        }
        assertEquals(1, signals.size)
        assertTrue(signals[0].id.endsWith(".ok"))
    }

    @Test
    fun forEachProbe_skipsFailedBatchAndContinues() {
        val signals = buildSignals(SignalCategory.SystemInfo) {
            addSafe(SignalCategory.SystemInfo, "ok", "OK") {
                signal(SignalCategory.SystemInfo, "ok", "OK", "yes", "test")
            }
            forEachProbe(SignalCategory.SystemInfo) { error("batch failed") }
            addSafe(SignalCategory.SystemInfo, "alsoOk", "Also OK") {
                signal(SignalCategory.SystemInfo, "alsoOk", "Also OK", "yes", "test")
            }
        }
        assertEquals(2, signals.size)
        assertTrue(signals.any { it.id.endsWith(".ok") })
        assertTrue(signals.any { it.id.endsWith(".alsoOk") })
    }
}
