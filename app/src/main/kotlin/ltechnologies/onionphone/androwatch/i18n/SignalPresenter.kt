package ltechnologies.onionphone.androwatch.i18n

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ltechnologies.onionphone.androwatch.R

/**
 * Localizes common sentinel tokens produced by collectors (e.g. `unavailable`, `none`,
 * `n/a`, `true`/`false`, `collection_failed`) into user-facing strings.
 *
 * Any value that is not a recognized token is returned unchanged.
 *
 * @param value Raw collector value or sentinel token.
 * @return The localized string for known tokens, otherwise [value] verbatim.
 */
@Composable
fun localizeCollectorToken(value: String): String = when (value.trim().lowercase()) {
    "unavailable" -> stringResource(R.string.value_unavailable)
    "none" -> stringResource(R.string.value_none)
    "n/a", "na" -> stringResource(R.string.value_na)
    "true" -> stringResource(R.string.value_true)
    "false" -> stringResource(R.string.value_false)
    "collection_failed" -> stringResource(R.string.value_collection_failed)
    else -> value
}
