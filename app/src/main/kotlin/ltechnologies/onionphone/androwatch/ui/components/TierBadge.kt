package ltechnologies.onionphone.androwatch.ui.components

import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ltechnologies.onionphone.androwatch.R
import ltechnologies.onionphone.androwatch.model.Sensitivity
import ltechnologies.onionphone.androwatch.ui.theme.tierChipColors

/**
 * Non-interactive chip labeling a category's sensitivity tier, colored per [tierChipColors].
 *
 * @param tier Sensitivity tier to display.
 * @param modifier Modifier for layout/styling.
 */
@Composable
fun TierBadge(
    tier: Sensitivity,
    modifier: Modifier = Modifier,
) {
    val (container, content) = tierChipColors(tier)
    val label = when (tier) {
        Sensitivity.Passive -> R.string.tier_passive
        Sensitivity.Permissioned -> R.string.tier_permissioned
        Sensitivity.Advanced -> R.string.tier_advanced
    }
    AssistChip(
        onClick = {},
        enabled = false,
        modifier = modifier,
        label = { Text(stringResource(label)) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = container,
            labelColor = content,
            disabledContainerColor = container,
            disabledLabelColor = content,
        ),
        border = null,
    )
}

/**
 * Non-interactive chip showing the number of signals in a category.
 *
 * @param count Signal count to display.
 * @param modifier Modifier for layout/styling.
 */
@Composable
fun SignalCountChip(
    count: Int,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = {},
        enabled = false,
        modifier = modifier,
        label = {
            Text(
                stringResource(R.string.signal_count, count),
                style = MaterialTheme.typography.labelMedium,
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        border = null,
    )
}

/**
 * Non-interactive chip indicating a category is streaming live updates.
 *
 * @param modifier Modifier for layout/styling.
 */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    AssistChip(
        onClick = {},
        enabled = false,
        modifier = modifier,
        label = {
            Text(
                stringResource(R.string.live_badge),
                style = MaterialTheme.typography.labelMedium,
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = scheme.primaryContainer,
            disabledLabelColor = scheme.onPrimaryContainer,
        ),
        border = null,
    )
}
