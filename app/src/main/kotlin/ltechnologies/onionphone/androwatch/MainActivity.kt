package ltechnologies.onionphone.androwatch

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewmodel.compose.viewModel
import ltechnologies.onionphone.androwatch.model.LoadState
import ltechnologies.onionphone.androwatch.presentation.CategoryViewModel
import ltechnologies.onionphone.androwatch.ui.navigation.AndroWatchNavGraph
import ltechnologies.onionphone.androwatch.ui.theme.AndroWatchTheme

/**
 * Single-activity host for the Compose UI.
 *
 * Sets [WindowManager.LayoutParams.FLAG_SECURE] to block screenshots/screen recording of
 * fingerprint data, enables edge-to-edge, hosts the navigation graph and kicks off passive
 * collection. It reports fully-drawn once no category is still loading (for startup metrics).
 *
 * @see CategoryViewModel
 * @see AndroWatchNavGraph
 */
class MainActivity : ComponentActivity() {
    /**
     * Configures the secure window, edge-to-edge display and Compose content tree.
     *
     * @param savedInstanceState Standard saved instance state, or `null` on first creation.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            val vm: CategoryViewModel = viewModel()
            LaunchedEffect(vm) {
                vm.refreshPassive()
                snapshotFlow { vm.state.value.loadStates }
                    .collect { loads ->
                        if (loads.values.none { it == LoadState.Loading }) {
                            reportFullyDrawn()
                        }
                    }
            }
            AndroWatchTheme {
                AndroWatchNavGraph(vm)
            }
        }
    }
}
