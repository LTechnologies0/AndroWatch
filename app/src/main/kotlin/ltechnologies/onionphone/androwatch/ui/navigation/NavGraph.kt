package ltechnologies.onionphone.androwatch.ui.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ltechnologies.onionphone.androwatch.R
import ltechnologies.onionphone.androwatch.collector.CollectorRegistry
import ltechnologies.onionphone.androwatch.data.UserPreferences
import ltechnologies.onionphone.androwatch.export.ReportExporter
import ltechnologies.onionphone.androwatch.model.LoadState
import ltechnologies.onionphone.androwatch.model.signalCounts
import ltechnologies.onionphone.androwatch.model.SignalCategory
import ltechnologies.onionphone.androwatch.narrative.FingerprintNarrative
import ltechnologies.onionphone.androwatch.permission.PermissionCenter
import ltechnologies.onionphone.androwatch.presentation.CategoryViewModel
import ltechnologies.onionphone.androwatch.ui.about.AboutScreen
import ltechnologies.onionphone.androwatch.ui.category.CategoryDetailScreen
import ltechnologies.onionphone.androwatch.ui.components.AndroWatchLargeTopBar
import ltechnologies.onionphone.androwatch.ui.home.HomeScreen
import ltechnologies.onionphone.androwatch.ui.onboarding.OnboardingScreen
import ltechnologies.onionphone.androwatch.ui.summary.SummaryBottomSheet
import kotlinx.coroutines.launch

/** Top-level navigation-suite destinations (bottom bar / navigation rail). */
private enum class TopLevelDestination {
    Home,
    Summary,
    About,
}

/**
 * Root navigation scaffold hosting onboarding, home, category-detail and about screens.
 *
 * Wires the [CategoryViewModel] state into an adaptive [NavigationSuiteScaffold] (bottom bar on
 * compact widths, rail on ≥600dp), provides the top app bar with refresh/export actions and
 * manages the summary bottom sheet.
 *
 * @param viewModel Shared category view model driving collection/state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroWatchNavGraph(viewModel: CategoryViewModel = viewModel()) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { UserPreferences(context) }
    val onboardingDone by prefs.onboardingComplete.collectAsState(initial = false)
    val state by viewModel.state.collectAsState()
    val permissionCenter = PermissionCenter()
    val exportChooserTitle = stringResource(R.string.export_chooser_title)
    val exportError = stringResource(R.string.export_error)
    val exportEmpty = stringResource(R.string.export_empty)
    val snackbar = remember { SnackbarHostState() }
    var selectedDestination by rememberSaveable { mutableStateOf(TopLevelDestination.Home) }
    var showSummarySheet by rememberSaveable { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val navBackStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showNavigationSuite = currentRoute == "home" || currentRoute == "about"
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    val categoryStats = remember(state.signals) {
        state.signals.mapValues { (_, signals) -> signals.signalCounts() }
    }
    val signalCounts = remember(categoryStats) {
        categoryStats.mapValues { it.value.total }
    }
    val failedCounts = remember(categoryStats) {
        categoryStats.mapValues { it.value.failed }
    }
    val isRefreshing = remember(state.loadStates) {
        state.loadStates.values.any { it == LoadState.Loading }
    }

    val exportReport: () -> Unit = {
        scope.launch {
            runCatching {
                if (state.signals.isEmpty()) {
                    snackbar.showSnackbar(exportEmpty)
                    return@launch
                }
                ReportExporter(context).share(
                    viewModel.exportReportJson(),
                    exportChooserTitle,
                )
            }.onFailure {
                snackbar.showSnackbar(exportError)
            }
        }
    }

    if (showSummarySheet) {
        SummaryBottomSheet(
            lines = FingerprintNarrative.summarize(state.signals),
            onDismiss = {
                showSummarySheet = false
                if (selectedDestination == TopLevelDestination.Summary) {
                    selectedDestination = TopLevelDestination.Home
                }
            },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layoutType = if (maxWidth >= 600.dp) {
            NavigationSuiteType.NavigationRail
        } else {
            NavigationSuiteType.NavigationBar
        }

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (currentRoute == "home") {
                    AndroWatchLargeTopBar(
                        title = stringResource(R.string.home_title),
                        subtitle = stringResource(R.string.home_subtitle),
                        scrollBehavior = scrollBehavior,
                        actions = {
                            IconButton(onClick = { viewModel.refreshPassive() }) {
                                Icon(
                                    Icons.Outlined.Refresh,
                                    contentDescription = stringResource(R.string.refresh),
                                )
                            }
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(
                                    Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(R.string.more_actions),
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_report)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        exportReport()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.export_report))
                                    },
                                )
                            }
                        },
                    )
                }
            },
        ) { scaffoldPadding ->
            val navHost: @Composable (Modifier) -> Unit = { modifier ->
                AndroWatchNavHost(
                    nav = nav,
                    modifier = modifier,
                    onboardingDone = onboardingDone,
                    prefs = prefs,
                    scope = scope,
                    viewModel = viewModel,
                    permissionCenter = permissionCenter,
                    context = context,
                    signalCounts = signalCounts,
                    failedCounts = failedCounts,
                    loadStates = state.loadStates,
                    isRefreshing = isRefreshing,
                    errors = state.errors,
                    signals = state.signals,
                )
            }

            if (showNavigationSuite) {
                NavigationSuiteScaffold(
                    modifier = Modifier.padding(scaffoldPadding),
                    layoutType = layoutType,
                    navigationSuiteItems = {
                        item(
                            icon = {
                                Icon(
                                    Icons.Outlined.Home,
                                    contentDescription = stringResource(R.string.nav_home),
                                )
                            },
                            label = { Text(stringResource(R.string.nav_home)) },
                            selected = selectedDestination == TopLevelDestination.Home,
                            onClick = {
                                selectedDestination = TopLevelDestination.Home
                                showSummarySheet = false
                                nav.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                        )
                        item(
                            icon = {
                                Icon(
                                    Icons.Outlined.Summarize,
                                    contentDescription = stringResource(R.string.nav_summary),
                                )
                            },
                            label = { Text(stringResource(R.string.nav_summary)) },
                            selected = selectedDestination == TopLevelDestination.Summary,
                            onClick = {
                                selectedDestination = TopLevelDestination.Summary
                                showSummarySheet = true
                            },
                        )
                        item(
                            icon = {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = stringResource(R.string.nav_about),
                                )
                            },
                            label = { Text(stringResource(R.string.nav_about)) },
                            selected = selectedDestination == TopLevelDestination.About,
                            onClick = {
                                selectedDestination = TopLevelDestination.About
                                showSummarySheet = false
                                nav.navigate("about") {
                                    popUpTo("home")
                                    launchSingleTop = true
                                }
                            },
                        )
                    },
                ) {
                    navHost(Modifier.fillMaxSize())
                }
            } else {
                navHost(Modifier.fillMaxSize().padding(scaffoldPadding))
            }
        }
    }
}

/**
 * Hosts the [NavHost], choosing onboarding vs home as the start destination.
 *
 * Parameters mirror the state/callbacks threaded down to each route; see [androWatchRoutes].
 */
@Composable
private fun AndroWatchNavHost(
    nav: NavHostController,
    modifier: Modifier,
    onboardingDone: Boolean,
    prefs: UserPreferences,
    scope: kotlinx.coroutines.CoroutineScope,
    viewModel: CategoryViewModel,
    permissionCenter: PermissionCenter,
    context: android.content.Context,
    signalCounts: Map<SignalCategory, Int>,
    failedCounts: Map<SignalCategory, Int>,
    loadStates: Map<SignalCategory, LoadState>,
    isRefreshing: Boolean,
    errors: Map<SignalCategory, String?>,
    signals: Map<SignalCategory, List<ltechnologies.onionphone.androwatch.model.FingerprintSignal>>,
) {
    NavHost(
        navController = nav,
        startDestination = if (onboardingDone) "home" else "onboarding",
        modifier = modifier,
    ) {
        androWatchRoutes(
            nav = nav,
            prefs = prefs,
            scope = scope,
            viewModel = viewModel,
            permissionCenter = permissionCenter,
            context = context,
            signalCounts = signalCounts,
            failedCounts = failedCounts,
            loadStates = loadStates,
            isRefreshing = isRefreshing,
            errors = errors,
            signals = signals,
        )
    }
}

/**
 * Registers the app's navigation routes: `onboarding`, `home`, `category/{id}` and `about`.
 *
 * The category route parses the [SignalCategory] from its `id` argument, triggers permission
 * gating / collection and starts/stops live observation via the [CategoryViewModel].
 */
private fun NavGraphBuilder.androWatchRoutes(
    nav: NavHostController,
    prefs: UserPreferences,
    scope: kotlinx.coroutines.CoroutineScope,
    viewModel: CategoryViewModel,
    permissionCenter: PermissionCenter,
    context: android.content.Context,
    signalCounts: Map<SignalCategory, Int>,
    failedCounts: Map<SignalCategory, Int>,
    loadStates: Map<SignalCategory, LoadState>,
    isRefreshing: Boolean,
    errors: Map<SignalCategory, String?>,
    signals: Map<SignalCategory, List<ltechnologies.onionphone.androwatch.model.FingerprintSignal>>,
) {
    composable("onboarding") {
        OnboardingScreen(
            onContinue = {
                scope.launch { prefs.setOnboardingComplete() }
                nav.navigate("home") {
                    popUpTo("onboarding") { inclusive = true }
                }
            },
        )
    }
    composable("home") {
        HomeScreen(
            signalCounts = signalCounts,
            failedCounts = failedCounts,
            loadStates = loadStates,
            isRefreshing = isRefreshing,
            onCategoryClick = { nav.navigate("category/${it.name}") },
            onRefreshClick = { viewModel.refreshPassive() },
        )
    }
    composable(
        route = "category/{id}",
        arguments = listOf(navArgument("id") { type = NavType.StringType }),
    ) { backStack ->
        val category = SignalCategory.valueOf(
            backStack.arguments?.getString("id") ?: SignalCategory.DeviceIdentity.name,
        )
        val isLive = CollectorRegistry.liveCategories.contains(category)

        LaunchedEffect(category) {
            val permissionKind = category.permissionKind
            if (permissionKind != null &&
                !permissionCenter.isGranted(context, permissionKind)
            ) {
                viewModel.enableAndRefresh(category, granted = false)
            } else {
                viewModel.refreshCategory(category)
            }
            if (isLive) viewModel.observeLiveCategory(category)
        }
        DisposableEffect(category) {
            onDispose { viewModel.stopLiveObservation() }
        }
        CategoryDetailScreen(
            category = category,
            loadState = loadStates[category] ?: LoadState.Idle,
            signals = signals[category].orEmpty(),
            permissions = category.permissionKind?.let(permissionCenter::permissionsFor) ?: emptyArray(),
            onPermissionsResult = { granted -> viewModel.enableAndRefresh(category, granted = granted) },
            isLive = isLive,
            errorDetail = errors[category],
            onRetry = { viewModel.refreshCategory(category) },
            onBack = { nav.popBackStack() },
        )
    }
    composable("about") {
        AboutScreen()
    }
}
