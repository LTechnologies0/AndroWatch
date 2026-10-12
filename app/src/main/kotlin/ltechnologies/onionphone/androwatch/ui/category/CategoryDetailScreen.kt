package ltechnologies.onionphone.androwatch.ui.category

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import ltechnologies.onionphone.androwatch.R
import ltechnologies.onionphone.androwatch.i18n.localizedSubtitle
import ltechnologies.onionphone.androwatch.i18n.localizedTitle
import ltechnologies.onionphone.androwatch.model.FingerprintSignal
import ltechnologies.onionphone.androwatch.model.LoadState
import ltechnologies.onionphone.androwatch.model.signalCounts
import ltechnologies.onionphone.androwatch.model.SignalCategory
import androidx.compose.material.icons.outlined.Inbox
import ltechnologies.onionphone.androwatch.ui.components.EmptyStateView
import ltechnologies.onionphone.androwatch.ui.components.AndroWatchTopBar
import ltechnologies.onionphone.androwatch.ui.components.LiveBadge
import ltechnologies.onionphone.androwatch.ui.components.LoadingView
import ltechnologies.onionphone.androwatch.ui.components.ErrorView
import ltechnologies.onionphone.androwatch.ui.signal.SignalRow

/**
 * Detail screen for a single [SignalCategory], rendering its signals or an appropriate state.
 *
 * Shows a permission gate ([LoadState.Denied]), loading, error, empty or a scrollable list of
 * [SignalRow]s with a count summary. Launches the runtime permission request when the gate's
 * grant button is tapped and forwards the outcome via [onPermissionsResult].
 *
 * @param category Category being displayed.
 * @param loadState Current load state selecting which content to render.
 * @param signals Collected signals for the category.
 * @param permissions Runtime permissions to request for this category (may be empty).
 * @param onPermissionsResult Callback with `true` if all requested permissions were granted.
 * @param isLive Whether the category is streaming live (shows a live badge).
 * @param errorDetail Optional error detail shown in the error state.
 * @param onRetry Retry/refresh callback for error/empty states.
 * @param onBack Back-navigation callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    category: SignalCategory,
    loadState: LoadState,
    signals: List<FingerprintSignal>,
    permissions: Array<String>,
    onPermissionsResult: (Boolean) -> Unit,
    isLive: Boolean = false,
    errorDetail: String? = null,
    onRetry: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val anyGranted = result.values.any { it }
        if (!anyGranted && askedOnce && activity != null) {
            permanentlyDenied = permissions.none { permission ->
                ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            }
        }
        onPermissionsResult(anyGranted)
    }
    val launchRequest = remember(permissions, permanentlyDenied) {
        {
            if (permanentlyDenied) {
                val settings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settings)
            } else {
                askedOnce = true
                launcher.launch(permissions)
            }
        }
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AndroWatchTopBar(
                title = category.localizedTitle(),
                subtitle = category.localizedSubtitle(),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    if (isLive) {
                        LiveBadge(modifier = Modifier.padding(end = 12.dp))
                    }
                },
            )
        },
    ) { padding ->
        when (loadState) {
            LoadState.Denied -> PermissionGate(
                onGrant = launchRequest,
                permanentlyDenied = permanentlyDenied,
                modifier = Modifier.padding(padding),
            )
            LoadState.Loading -> LoadingView(modifier = Modifier.padding(padding))
            LoadState.Error -> ErrorView(
                detail = errorDetail,
                onRetry = onRetry,
                modifier = Modifier.padding(padding),
            )
            else -> {
                if (signals.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Outlined.Inbox,
                        message = stringResource(R.string.no_signals),
                        actionLabel = stringResource(R.string.refresh),
                        onAction = onRetry,
                        modifier = Modifier.padding(padding),
                    )
                } else {
                    val counts = remember(signals) { signals.signalCounts() }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "summary") {
                            Text(
                                stringResource(R.string.category_signal_summary, counts.ok, counts.failed),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        items(signals, key = { it.id }, contentType = { "signal" }) { signal ->
                            SignalRow(signal = signal)
                        }
                    }
                }
            }
        }
    }
}
