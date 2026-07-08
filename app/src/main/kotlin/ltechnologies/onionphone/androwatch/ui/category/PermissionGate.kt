package ltechnologies.onionphone.androwatch.ui.category

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ltechnologies.onionphone.androwatch.R

/**
 * Full-screen prompt shown when a category's runtime permission has not been granted.
 *
 * Presents a rationale, a grant button and an expandable "learn more" section.
 *
 * @param onGrant Invoked when the user taps the grant button (launches the permission request).
 * @param modifier Modifier for layout/styling.
 */
@Composable
fun PermissionGate(
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var learnMoreExpanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = stringResource(R.string.cd_permission_icon),
                modifier = Modifier
                    .size(56.dp)
                    .padding(bottom = 16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.permission_required),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.permission_rationale),
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onGrant) {
                Text(stringResource(R.string.grant_permission))
            }
            FilledTonalButton(
                onClick = { learnMoreExpanded = !learnMoreExpanded },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.learn_more))
            }
            AnimatedVisibility(
                visible = learnMoreExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Text(
                    text = stringResource(R.string.permission_learn_more),
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
