package ltechnologies.onionphone.androwatch.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ltechnologies.onionphone.androwatch.R
import ltechnologies.onionphone.androwatch.i18n.localizedSubtitle
import ltechnologies.onionphone.androwatch.i18n.localizedTitle
import ltechnologies.onionphone.androwatch.model.LoadState
import ltechnologies.onionphone.androwatch.model.Sensitivity
import ltechnologies.onionphone.androwatch.model.SignalCategory
import ltechnologies.onionphone.androwatch.ui.components.TierBadge

/**
 * Home screen listing categories grouped by sensitivity tier with search and pull-to-refresh.
 *
 * Categories are tabbed by [Sensitivity] tier and filtered by the search query (matching
 * localized title/subtitle). Each visible category renders a [CategoryCard].
 *
 * @param signalCounts Total signal count per category.
 * @param failedCounts Failed-signal count per category.
 * @param loadStates Per-category load state.
 * @param isRefreshing Whether a passive refresh is in progress (drives pull-to-refresh).
 * @param onCategoryClick Invoked when a category card is tapped.
 * @param onRefreshClick Invoked on pull-to-refresh.
 * @param modifier Modifier for layout/styling.
 * @param contentPadding Outer content padding (e.g. from the scaffold).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    signalCounts: Map<SignalCategory, Int>,
    failedCounts: Map<SignalCategory, Int> = emptyMap(),
    loadStates: Map<SignalCategory, LoadState>,
    isRefreshing: Boolean,
    onCategoryClick: (SignalCategory) -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val categoriesByTier = remember { SignalCategory.entries.groupBy { it.sensitivity } }
    val tiers = remember { Sensitivity.entries.toList() }
    val tierTitles = listOf(
        stringResource(R.string.tier_passive),
        stringResource(R.string.tier_permissioned),
        stringResource(R.string.tier_advanced),
    )
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val selectedTier = tiers[selectedTabIndex]
    val tierCategories = categoriesByTier[selectedTier].orEmpty()
    val visibleCategories = tierCategories.filter { category ->
        searchQuery.isBlank() ||
            category.localizedTitle().contains(searchQuery, ignoreCase = true) ||
            category.localizedSubtitle().contains(searchQuery, ignoreCase = true)
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefreshClick,
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "search") {
                DockedSearchBar(
                    expanded = searchExpanded,
                    onExpandedChange = { searchExpanded = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = { searchExpanded = false },
                            expanded = searchExpanded,
                            onExpandedChange = { searchExpanded = it },
                            placeholder = { Text(stringResource(R.string.search_categories)) },
                        )
                    },
                ) {}
            }
            item(key = "tier-tabs") {
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    tierTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) },
                        )
                    }
                }
            }
            item(key = "header-$selectedTier") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        text = tierTitles[selectedTabIndex],
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TierBadge(tier = selectedTier)
                }
            }
            if (visibleCategories.isEmpty()) {
                item(key = "empty-search") {
                    Text(
                        text = stringResource(R.string.search_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                items(visibleCategories, key = { it.name }) { category ->
                    CategoryCard(
                        category = category,
                        signalCount = signalCounts[category] ?: 0,
                        failedCount = failedCounts[category] ?: 0,
                        loadState = loadStates[category] ?: LoadState.Idle,
                        onClick = { onCategoryClick(category) },
                    )
                }
            }
        }
    }
}
