package ltechnologies.onionphone.androwatch.ui.components



import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.ColumnScope

import androidx.compose.foundation.layout.RowScope

import androidx.compose.foundation.layout.padding

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material3.ElevatedCard

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.Icon

import androidx.compose.material3.IconButton

import androidx.compose.material3.LargeTopAppBar

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Text

import androidx.compose.material3.TopAppBar

import androidx.compose.material3.TopAppBarDefaults

import androidx.compose.material3.TopAppBarScrollBehavior

import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier

import androidx.compose.ui.res.stringResource
import ltechnologies.onionphone.androwatch.R

import androidx.compose.ui.unit.Dp

import androidx.compose.ui.unit.dp



/**
 * Standard (small) top app bar with optional subtitle, back button and actions.
 *
 * @param title Primary title text.
 * @param modifier Modifier for layout/styling.
 * @param subtitle Optional secondary line under the title.
 * @param onBack Optional back-navigation callback; shows a back icon when set.
 * @param scrollBehavior Optional scroll behavior for collapse/color transitions.
 * @param actions Trailing action items.
 */
@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun AndroWatchTopBar(

    title: String,

    modifier: Modifier = Modifier,

    subtitle: String? = null,

    onBack: (() -> Unit)? = null,

    scrollBehavior: TopAppBarScrollBehavior? = null,

    actions: @Composable RowScope.() -> Unit = {},

) {

    TopAppBar(

        modifier = modifier,

        title = {

            if (subtitle != null) {

                Column {

                    Text(title, style = MaterialTheme.typography.titleLarge)

                    Text(

                        subtitle,

                        style = MaterialTheme.typography.bodySmall,

                        color = MaterialTheme.colorScheme.onSurfaceVariant,

                    )

                }

            } else {

                Text(title, style = MaterialTheme.typography.titleLarge)

            }

        },

        navigationIcon = {

            if (onBack != null) {

                IconButton(onClick = onBack) {

                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))

                }

            }

        },

        actions = actions,

        scrollBehavior = scrollBehavior,

        colors = TopAppBarDefaults.topAppBarColors(

            containerColor = MaterialTheme.colorScheme.surface,

            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,

        ),

    )

}



/**
 * Large (expanded) top app bar with optional subtitle, back button and actions.
 *
 * @param title Primary title text.
 * @param modifier Modifier for layout/styling.
 * @param subtitle Optional secondary line under the title.
 * @param onBack Optional back-navigation callback; shows a back icon when set.
 * @param scrollBehavior Optional scroll behavior for collapse/color transitions.
 * @param actions Trailing action items.
 */
@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun AndroWatchLargeTopBar(

    title: String,

    modifier: Modifier = Modifier,

    subtitle: String? = null,

    onBack: (() -> Unit)? = null,

    scrollBehavior: TopAppBarScrollBehavior? = null,

    actions: @Composable RowScope.() -> Unit = {},

) {

    LargeTopAppBar(

        modifier = modifier,

        title = {

            if (subtitle != null) {

                Column {

                    Text(title, style = MaterialTheme.typography.headlineMedium)

                    Text(

                        subtitle,

                        style = MaterialTheme.typography.bodyMedium,

                        color = MaterialTheme.colorScheme.onSurfaceVariant,

                    )

                }

            } else {

                Text(title, style = MaterialTheme.typography.headlineMedium)

            }

        },

        navigationIcon = {

            if (onBack != null) {

                IconButton(onClick = onBack) {

                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))

                }

            }

        },

        actions = actions,

        scrollBehavior = scrollBehavior,

        colors = TopAppBarDefaults.topAppBarColors(

            containerColor = MaterialTheme.colorScheme.surface,

            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,

        ),

    )

}



/**
 * Elevated card panel wrapping its content in a padded [Column], optionally clickable.
 *
 * @param modifier Modifier for layout/styling.
 * @param onClick Optional click handler; when set the card becomes interactive.
 * @param contentPadding Inner padding around the content.
 * @param content Column-scoped content.
 */
@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun M3ElevatedPanel(

    modifier: Modifier = Modifier,

    onClick: (() -> Unit)? = null,

    contentPadding: Dp = 16.dp,

    content: @Composable ColumnScope.() -> Unit,

) {

    if (onClick != null) {

        ElevatedCard(modifier = modifier, onClick = onClick) {

            Column(Modifier.padding(contentPadding), content = content)

        }

    } else {

        ElevatedCard(modifier = modifier) {

            Column(Modifier.padding(contentPadding), content = content)

        }

    }

}


