package ltechnologies.onionphone.androwatch.narrative

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ltechnologies.onionphone.androwatch.R
import ltechnologies.onionphone.androwatch.model.FingerprintSignal
import ltechnologies.onionphone.androwatch.model.SignalCategory

/**
 * Builds a short, human-readable narrative summarizing the most salient collected signals.
 *
 * Used by the summary UI to explain, in plain language, what a tracker could infer (locale,
 * device model, ANDROID_ID, battery, network, account ecosystem) plus a closing privacy note.
 */
object FingerprintNarrative {
    /**
     * Produces localized summary lines from a snapshot of collected signals.
     *
     * Missing values fall back to localized "unknown" placeholders so the summary is always
     * complete. Must be called from a composable scope (uses [stringResource]).
     *
     * @param signals Map of category to its collected signals.
     * @return Ordered list of localized narrative lines to display.
     */
    @Composable
    fun summarize(signals: Map<SignalCategory, List<FingerprintSignal>>): List<String> {
        val unknown = stringResource(R.string.value_unknown)
        val unknownLocale = stringResource(R.string.value_unknown_locale)
        val unknownDevice = stringResource(R.string.value_unknown_device)
        val unknownBattery = stringResource(R.string.value_unknown_battery)
        val unknownNetwork = stringResource(R.string.value_unknown_network)
        val none = stringResource(R.string.value_none)

        val locale = signals[SignalCategory.Locale].orEmpty().firstOrNull { it.id.endsWith(".locale") }?.value
            ?: unknownLocale
        val model = signals[SignalCategory.DeviceIdentity].orEmpty().firstOrNull { it.id.endsWith(".model") }?.value
            ?: unknownDevice
        val androidId = signals[SignalCategory.DeviceIdentity].orEmpty()
            .firstOrNull { it.id.endsWith(".androidId") }?.value
            ?: unknown
        val battery = signals[SignalCategory.Battery].orEmpty().firstOrNull { it.id.endsWith(".level") }?.value
            ?: unknownBattery
        val transports = signals[SignalCategory.Network].orEmpty().firstOrNull { it.id.endsWith(".transports") }?.value
            ?: unknownNetwork
        val authenticators = signals[SignalCategory.GoogleAccount].orEmpty()
            .firstOrNull { it.id.endsWith(".authenticatorTypes") }?.value ?: none

        return listOf(
            stringResource(R.string.summary_line_locale, locale),
            stringResource(R.string.summary_line_model, model),
            stringResource(R.string.summary_line_android_id, androidId),
            stringResource(R.string.summary_line_battery, battery),
            stringResource(R.string.summary_line_network, transports),
            stringResource(R.string.summary_line_authenticators, authenticators),
            stringResource(R.string.summary_line_privacy),
        )
    }
}
