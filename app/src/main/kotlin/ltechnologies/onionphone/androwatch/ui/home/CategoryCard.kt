package ltechnologies.onionphone.androwatch.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ltechnologies.onionphone.androwatch.R
import ltechnologies.onionphone.androwatch.i18n.localizedSubtitle
import ltechnologies.onionphone.androwatch.i18n.localizedTitle
import ltechnologies.onionphone.androwatch.model.LoadState
import ltechnologies.onionphone.androwatch.model.SignalCategory
import ltechnologies.onionphone.androwatch.ui.components.SignalCountChip

/**
 * Home-screen list card summarizing one [SignalCategory].
 *
 * Shows the category icon, localized title/subtitle, signal-count chips (with a failure chip
 * when applicable) and a trailing loading/error/chevron indicator based on [loadState].
 *
 * @param category Category represented by this card.
 * @param signalCount Total collected signals for the category.
 * @param failedCount Number of failed signals; adds an error summary chip when > 0.
 * @param loadState Current load state driving the trailing indicator.
 * @param onClick Invoked when the card is tapped.
 * @param modifier Modifier for layout/styling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryCard(
    category: SignalCategory,
    signalCount: Int,
    failedCount: Int = 0,
    loadState: LoadState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick,
    ) {
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = categoryIcon(category),
                    contentDescription = stringResource(R.string.cd_category_icon),
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            headlineContent = {
                Text(
                    text = category.localizedTitle(),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            supportingContent = {
                Text(
                    text = category.localizedSubtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (signalCount > 0) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SignalCountChip(count = signalCount)
                        if (failedCount > 0) {
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = {
                                    Text(
                                        stringResource(R.string.category_signal_summary, signalCount - failedCount, failedCount),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    disabledContainerColor = MaterialTheme.colorScheme.errorContainer,
                                    disabledLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                                border = null,
                            )
                        }
                    }
                }
            },
            trailingContent = {
                when (loadState) {
                    LoadState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    LoadState.Error -> Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = stringResource(R.string.cd_category_icon),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    else -> Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = stringResource(R.string.cd_category_icon),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

/**
 * Maps a [SignalCategory] to its representative Material icon.
 *
 * @param category Category to look up.
 * @return The icon to display for [category].
 */
private fun categoryIcon(category: SignalCategory): ImageVector = when (category) {
    SignalCategory.DeviceIdentity -> Icons.Outlined.Fingerprint
    SignalCategory.GoogleAccount -> Icons.Outlined.Devices
    SignalCategory.SystemInfo -> Icons.Outlined.Settings
    SignalCategory.Display -> Icons.Outlined.PhoneAndroid
    SignalCategory.Locale -> Icons.Outlined.Language
    SignalCategory.Accessibility -> Icons.Outlined.Visibility
    SignalCategory.DeviceMotion -> Icons.Outlined.Sensors
    SignalCategory.Battery -> Icons.Outlined.BatteryFull
    SignalCategory.Storage -> Icons.Outlined.Storage
    SignalCategory.Network -> Icons.Outlined.NetworkCheck
    SignalCategory.Fonts -> Icons.Outlined.FontDownload
    SignalCategory.InstalledVoices -> Icons.Outlined.GraphicEq
    SignalCategory.AppInfo -> Icons.Outlined.Apps
    SignalCategory.Pasteboard -> Icons.Outlined.History
    SignalCategory.Audio -> Icons.Outlined.GraphicEq
    SignalCategory.Graphics -> Icons.Outlined.Devices
    SignalCategory.Telephony -> Icons.Outlined.PhoneAndroid
    SignalCategory.Motion -> Icons.Outlined.Sensors
    SignalCategory.Location -> Icons.Outlined.LocationOn
    SignalCategory.Cameras -> Icons.Outlined.CameraAlt
    SignalCategory.Bluetooth -> Icons.Outlined.Bluetooth
    SignalCategory.LocalNetwork -> Icons.Outlined.Wifi
    SignalCategory.Contacts -> Icons.Outlined.Contacts
    SignalCategory.Photos -> Icons.Outlined.PhotoLibrary
    SignalCategory.Calendar -> Icons.Outlined.CalendarMonth
    SignalCategory.Reminders -> Icons.Outlined.CalendarMonth
    SignalCategory.MusicLibrary -> Icons.Outlined.MusicNote
    SignalCategory.InstalledAppsProbe -> Icons.Outlined.Apps
    SignalCategory.WebViewFingerprint -> Icons.Outlined.Language
    SignalCategory.PreviousInstallsLog -> Icons.Outlined.History
}

