package ltechnologies.onionphone.androwatch.ui.signal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ltechnologies.onionphone.androwatch.R
import ltechnologies.onionphone.androwatch.i18n.localizeCollectorToken
import ltechnologies.onionphone.androwatch.model.DisplayHint
import ltechnologies.onionphone.androwatch.model.FingerprintSignal
import ltechnologies.onionphone.androwatch.ui.theme.MonospaceFamily

/**
 * Card rendering a single [FingerprintSignal] with an OK/failed badge and expandable rationale.
 *
 * The value area is rendered according to the signal's [DisplayHint] (plain, tags, key/value,
 * compound or error), showing the interpreted value plus an optional raw-value block.
 *
 * @param signal Signal to display.
 * @param modifier Modifier for layout/styling.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignalRow(
    signal: FingerprintSignal,
    modifier: Modifier = Modifier,
) {
    var rationaleExpanded by rememberSaveable(signal.id) { mutableStateOf(false) }
    val isFailed = signal.displayHint == DisplayHint.Error

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = if (isFailed) {
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
            )
        } else {
            CardDefaults.elevatedCardColors()
        },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = signal.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(
                            stringResource(
                                if (isFailed) R.string.signal_failed_badge else R.string.signal_ok_badge,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = if (isFailed) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        disabledLabelColor = if (isFailed) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                    ),
                    border = null,
                )
            }

            when (signal.displayHint) {
                DisplayHint.Error -> {
                    InterpretationText(signal.value)
                    signal.rawValue?.let { RawValueBlock(it) }
                }
                DisplayHint.Tags -> {
                    InterpretationText(signal.value)
                    if (signal.entries.isNotEmpty()) {
                        KeyValueContent(signal, summaryAlreadyShown = true)
                    } else {
                        TagsValue(signal.value)
                    }
                    signal.rawValue?.let { RawValueBlock(it) }
                }
                DisplayHint.KeyValue -> {
                    InterpretationText(signal.value)
                    if (signal.entries.isNotEmpty()) KeyValueContent(signal, summaryAlreadyShown = true)
                    signal.rawValue?.let { RawValueBlock(it) }
                }
                DisplayHint.Compound -> CompoundContent(signal)
                DisplayHint.Plain -> {
                    InterpretationText(signal.value)
                    signal.rawValue?.let { RawValueBlock(it) }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { rationaleExpanded = !rationaleExpanded }
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(
                        if (rationaleExpanded) R.string.hide_rationale else R.string.show_rationale,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = if (rationaleExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(R.string.cd_expand_signal),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            AnimatedVisibility(
                visible = rationaleExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Text(
                        text = signal.rationale,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Highlighted panel showing the human-readable interpretation of a signal value. */
@Composable
private fun InterpretationText(value: String) {
    val text = localizeCollectorToken(value)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Labeled monospace block showing a signal's raw/original value. */
@Composable
private fun RawValueBlock(raw: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.raw_value),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ValueContainer(raw)
    }
}

/** Renders a plain scalar value in a monospace container. */
@Composable
private fun PlainValue(value: String) {
    ValueContainer(value)
}

/** Monospace surface container used to render a single value string (localizing tokens). */
@Composable
private fun ValueContainer(value: String) {
    val text = localizeCollectorToken(value)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonospaceFamily),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Renders a comma/semicolon-delimited value as a flow of chips, falling back to plain text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsValue(value: String) {
    val tags = value.split(',', ';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (tags.isEmpty()) {
        PlainValue(value)
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { tag ->
            SuggestionChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
                    )
                },
            )
        }
    }
}

/**
 * Renders a signal's key/value entries as rows, deriving them from `value` lines if [entries]
 * is empty.
 *
 * @param signal Signal whose entries/value are rendered.
 * @param summaryAlreadyShown When `true`, suppresses the plain-value fallback (the summary was
 *   already displayed above).
 */
@Composable
private fun KeyValueContent(signal: FingerprintSignal, summaryAlreadyShown: Boolean = false) {
    val rows = signal.entries.ifEmpty {
        signal.value.lines()
            .mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx > 0) {
                    ltechnologies.onionphone.androwatch.model.SignalEntry(
                        line.substring(0, idx).trim(),
                        line.substring(idx + 1).trim(),
                    )
                } else {
                    null
                }
            }
    }
    if (rows.isEmpty()) {
        if (!summaryAlreadyShown) PlainValue(signal.value)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { entry ->
            KeyValueRow(entry.key, entry.value)
        }
    }
}

/** Renders a compound signal's entries as labeled value blocks stacked vertically. */
@Composable
private fun CompoundContent(signal: FingerprintSignal) {
    if (signal.entries.isEmpty()) {
        KeyValueContent(signal)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        signal.entries.forEach { entry ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = entry.key,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                ValueContainer(entry.value)
            }
        }
    }
}

/** Single key/value row rendered as a card with the value in monospace. */
@Composable
private fun KeyValueRow(key: String, value: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Text(
                    text = key,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
        )
    }
}
