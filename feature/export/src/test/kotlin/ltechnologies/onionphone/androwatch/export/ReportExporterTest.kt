package ltechnologies.onionphone.androwatch.export

import ltechnologies.onionphone.androwatch.model.DisplayHint
import ltechnologies.onionphone.androwatch.model.FingerprintSignal
import ltechnologies.onionphone.androwatch.model.Sensitivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportExporterTest {

    @Test
    fun sanitize_hashesIdentifierRawValue() {
        val signal = FingerprintSignal(
            id = "DeviceIdentity.androidId",
            name = "Android ID",
            value = "shown",
            rationale = "id",
            sensitivity = Sensitivity.Passive,
            displayHint = DisplayHint.Plain,
            rawValue = "9774d56d682e549c",
        )
        val sanitized = ReportExporter.sanitizeSignal(signal)
        assertEquals(64, sanitized.rawValue?.length)
        assertNotEquals("9774d56d682e549c", sanitized.rawValue)
        assertTrue(ReportExporter.isIdentifierSignal(signal.id))
    }

    @Test
    fun sanitize_leavesNonIdentifierRawValue() {
        val signal = FingerprintSignal(
            id = "Battery.level",
            name = "Level",
            value = "80%",
            rationale = "battery",
            sensitivity = Sensitivity.Passive,
            rawValue = "80",
        )
        val sanitized = ReportExporter.sanitizeSignal(signal)
        assertEquals("80", sanitized.rawValue)
    }

    @Test
    fun sanitize_hashesImeiAndMacHints() {
        val imei = FingerprintSignal(
            id = "Telephony.imei",
            name = "IMEI",
            value = "shown",
            rationale = "id",
            sensitivity = Sensitivity.Passive,
            displayHint = DisplayHint.Plain,
            rawValue = "490154203237518",
        )
        val mac = FingerprintSignal(
            id = "Wifi.macAddress",
            name = "MAC",
            value = "shown",
            rationale = "id",
            sensitivity = Sensitivity.Passive,
            displayHint = DisplayHint.Plain,
            rawValue = "02:00:00:00:00:00",
        )
        assertEquals(64, ReportExporter.sanitizeSignal(imei).rawValue?.length)
        assertEquals(64, ReportExporter.sanitizeSignal(mac).rawValue?.length)
        assertTrue(ReportExporter.isIdentifierSignal(imei.id))
        assertTrue(ReportExporter.isIdentifierSignal(mac.id))
    }

    @Test
    fun sanitizeSignals_isIdempotentOnAlreadyHashed() {
        val signal = FingerprintSignal(
            id = "DeviceIdentity.androidId",
            name = "Android ID",
            value = "shown",
            rationale = "id",
            sensitivity = Sensitivity.Passive,
            displayHint = DisplayHint.Plain,
            rawValue = "9774d56d682e549c",
        )
        val once = ReportExporter.sanitizeSignals(listOf(signal)).single()
        val twice = ReportExporter.sanitizeSignals(listOf(once)).single()
        assertEquals(once.rawValue, twice.rawValue)
    }
}
