package com.kairo.reader.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.ui.tutorial.StartingTutorialTargetIds
import com.kairo.reader.ui.tutorial.startingTutorialTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryExpressiveScaffold(
    selectedTab: LibraryTab,
    navigationSuiteType: NavigationSuiteType,
    compactLandscape: Boolean,
    importEnabled: Boolean,
    tutorialTargets: MutableMap<String, Rect>,
    onTabSelected: (LibraryTab) -> Unit,
    onImport: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navigationItemBounds = remember { mutableMapOf<LibraryTab, Rect>() }
    NavigationSuiteScaffold(
        modifier = Modifier.fillMaxSize(),
        navigationSuiteType = navigationSuiteType,
        navigationItems = {
            LibraryNavigationItem(
                tab = LibraryTab.Books,
                selectedTab = selectedTab,
                navigationSuiteType = navigationSuiteType,
                onClick = onTabSelected,
                modifier =
                Modifier.onGloballyPositioned { coordinates ->
                    navigationItemBounds[LibraryTab.Books] = coordinates.boundsInRoot()
                    tutorialTargets[StartingTutorialTargetIds.LIBRARY_TABS] =
                        navigationItemBounds.unionBounds()
                },
            )
            LibraryNavigationItem(
                tab = LibraryTab.Saved,
                selectedTab = selectedTab,
                navigationSuiteType = navigationSuiteType,
                onClick = onTabSelected,
                modifier =
                Modifier.onGloballyPositioned { coordinates ->
                    navigationItemBounds[LibraryTab.Saved] = coordinates.boundsInRoot()
                    tutorialTargets[StartingTutorialTargetIds.LIBRARY_TABS] =
                        navigationItemBounds.unionBounds()
                },
            )
            LibraryNavigationItem(
                tab = LibraryTab.Momentum,
                selectedTab = selectedTab,
                navigationSuiteType = navigationSuiteType,
                onClick = onTabSelected,
                modifier =
                Modifier.onGloballyPositioned { coordinates ->
                    navigationItemBounds[LibraryTab.Momentum] = coordinates.boundsInRoot()
                    tutorialTargets[StartingTutorialTargetIds.LIBRARY_TABS] =
                        navigationItemBounds.unionBounds()
                },
            )
        },
        navigationItemVerticalArrangement = Arrangement.Center,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        val scaffoldModifier =
            if (compactLandscape) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
            }
        Scaffold(
            modifier = scaffoldModifier,
            containerColor = MaterialTheme.colorScheme.surface,
            floatingActionButton = {
                if (selectedTab == LibraryTab.Books && importEnabled && !compactLandscape) {
                    ExtendedFloatingActionButton(
                        onClick = onImport,
                        modifier =
                        Modifier.startingTutorialTarget(StartingTutorialTargetIds.LIBRARY_IMPORT) {
                                targetId,
                                bounds,
                            ->
                            tutorialTargets[targetId] = bounds
                        },
                        expanded =
                        !compactLandscape &&
                            navigationSuiteType != NavigationSuiteType.WideNavigationRailCollapsed,
                        icon = {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.library_import_button),
                            )
                        },
                        text = { Text(stringResource(R.string.library_import_button)) },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            },
            topBar = {
                if (compactLandscape) {
                    TopAppBar(
                        title = {
                            Text(
                                stringResource(
                                    when (selectedTab) {
                                        LibraryTab.Books -> R.string.library_tab_books
                                        LibraryTab.Saved -> R.string.saved_title
                                        LibraryTab.Momentum -> R.string.library_tab_momentum
                                    }
                                )
                            )
                        },
                        actions = { LibraryHeaderActions(onSearch, onSettings, tutorialTargets) },
                        colors = libraryTopAppBarColors(),
                    )
                } else {
                    LargeFlexibleTopAppBar(
                        title = { Text(stringResource(R.string.library_title)) },
                        subtitle = { Text(stringResource(R.string.library_subtitle)) },
                        actions = { LibraryHeaderActions(onSearch, onSettings, tutorialTargets) },
                        colors = libraryTopAppBarColors(),
                        scrollBehavior = scrollBehavior,
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(
                        horizontal = if (compactLandscape) 12.dp else 16.dp,
                        vertical = if (compactLandscape) 8.dp else 12.dp,
                    ),
            ) {
                Box(
                    modifier = Modifier.widthIn(max = 1000.dp).fillMaxSize().align(Alignment.TopCenter),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun LibraryNavigationItem(
    tab: LibraryTab,
    selectedTab: LibraryTab,
    navigationSuiteType: NavigationSuiteType,
    onClick: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationSuiteItem(
        selected = tab == selectedTab,
        onClick = { onClick(tab) },
        icon = {
            Icon(
                imageVector =
                when (tab) {
                    LibraryTab.Books -> Icons.Default.Book
                    LibraryTab.Saved -> Icons.Default.Bookmark
                    LibraryTab.Momentum -> Icons.AutoMirrored.Filled.TrendingUp
                },
                contentDescription = null,
            )
        },
        label = {
            Text(
                stringResource(
                    when (tab) {
                        LibraryTab.Books -> R.string.library_tab_books
                        LibraryTab.Saved -> R.string.library_tab_saved
                        LibraryTab.Momentum -> R.string.library_tab_momentum
                    }
                ),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
        navigationSuiteType = navigationSuiteType,
    )
}

@Composable
private fun LibraryHeaderActions(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    tutorialTargets: MutableMap<String, Rect>,
) {
    FilledTonalIconButton(onClick = onSearch) {
        Icon(
            Icons.Default.Search,
            contentDescription = stringResource(R.string.content_desc_search),
        )
    }
    FilledTonalIconButton(
        onClick = onSettings,
        modifier =
        Modifier.startingTutorialTarget(StartingTutorialTargetIds.LIBRARY_SETTINGS) {
                targetId,
                bounds,
            ->
            tutorialTargets[targetId] = bounds
        },
    ) {
        Icon(
            Icons.Default.Settings,
            contentDescription = stringResource(R.string.content_desc_settings),
        )
    }
}

@Composable
private fun libraryTopAppBarColors() =
    TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    )

private fun Map<LibraryTab, Rect>.unionBounds(): Rect {
    val bounds = values
    return Rect(
        left = bounds.minOf { it.left },
        top = bounds.minOf { it.top },
        right = bounds.maxOf { it.right },
        bottom = bounds.maxOf { it.bottom },
    )
}
