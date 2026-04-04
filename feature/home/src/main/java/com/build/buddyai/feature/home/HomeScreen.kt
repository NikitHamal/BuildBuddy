package com.build.buddyai.feature.home

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.core.designsystem.component.*
import com.build.buddyai.core.designsystem.theme.NeoVedicSpacing
import com.build.buddyai.core.model.BuildStatus
import com.build.buddyai.core.ui.component.BuildBuddySearchBar
import com.build.buddyai.core.ui.component.ProjectCard
import com.build.buddyai.core.ui.component.formatRelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCreateProject: () -> Unit,
    onImportProject: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("BuildBuddy", style = MaterialTheme.typography.titleLarge)
                        if (state.projectCount > 0) {
                            Text(
                                "${state.projectCount} project${if (state.projectCount != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            if (state.viewMode == ViewMode.LIST) Icons.Default.GridView
                            else Icons.Default.ViewList,
                            contentDescription = "Toggle view"
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateProject,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Project") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                LoadingView(message = "Loading projects...")
            }
        } else if (state.projectCount == 0 && state.searchQuery.isBlank()) {
            HomeEmptyState(
                onCreateProject = onCreateProject,
                onImportProject = onImportProject,
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            )
        } else {
            HomeContent(
                state = state,
                onSearchQueryChange = viewModel::setSearchQuery,
                onSortModeChange = viewModel::setSortMode,
                onOpenProject = onOpenProject,
                onImportProject = onImportProject,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun HomeEmptyState(
    onCreateProject: () -> Unit,
    onImportProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EmptyStateView(
            icon = Icons.Outlined.RocketLaunch,
            title = "Welcome to BuildBuddy",
            description = "Create your first Android app project and start building with AI-powered vibe coding.",
            action = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(NeoVedicSpacing.SM)
                ) {
                    NeoVedicButton(
                        text = "Create New Project",
                        onClick = onCreateProject,
                        icon = Icons.Default.Add,
                        modifier = Modifier.fillMaxWidth(0.7f)
                    )
                    NeoVedicOutlinedButton(
                        text = "Import Project",
                        onClick = onImportProject,
                        icon = Icons.Default.FileOpen,
                        modifier = Modifier.fillMaxWidth(0.7f)
                    )
                }
            }
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeState,
    onSearchQueryChange: (String) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onOpenProject: (String) -> Unit,
    onImportProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(NeoVedicSpacing.SM)
    ) {
        item {
            BuildBuddySearchBar(
                query = state.searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier.padding(horizontal = NeoVedicSpacing.LG, vertical = NeoVedicSpacing.SM),
                placeholder = "Search projects..."
            )
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = NeoVedicSpacing.LG),
                horizontalArrangement = Arrangement.spacedBy(NeoVedicSpacing.SM)
            ) {
                items(SortMode.entries.toList()) { mode ->
                    NeoVedicChip(
                        label = mode.displayName,
                        selected = mode == state.sortMode,
                        onClick = { onSortModeChange(mode) }
                    )
                }
                item {
                    NeoVedicChip(
                        label = "Import",
                        onClick = onImportProject,
                        leadingIcon = Icons.Default.FileOpen
                    )
                }
            }
        }

        if (state.recentBuilds.isNotEmpty()) {
            item {
                SectionHeader(title = "Recent Builds")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = NeoVedicSpacing.LG),
                    horizontalArrangement = Arrangement.spacedBy(NeoVedicSpacing.SM)
                ) {
                    items(state.recentBuilds) { build ->
                        NeoVedicCard(
                            modifier = Modifier.width(200.dp),
                            onClick = { onOpenProject(build.projectId) }
                        ) {
                            Column(modifier = Modifier.padding(NeoVedicSpacing.MD)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StatusBadge(
                                        label = build.status.displayName,
                                        color = when (build.status) {
                                            BuildStatus.SUCCESS -> com.build.buddyai.core.designsystem.theme.NeoVedicColors.StatusSuccess
                                            BuildStatus.FAILED -> com.build.buddyai.core.designsystem.theme.NeoVedicColors.StatusError
                                            else -> com.build.buddyai.core.designsystem.theme.NeoVedicColors.StatusRunning
                                        }
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = formatRelativeTime(build.startedAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (build.durationMs != null) {
                                    Spacer(Modifier.height(NeoVedicSpacing.XS))
                                    Text(
                                        text = "${build.durationMs / 1000}s",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Projects")
        }

        if (state.projects.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(NeoVedicSpacing.XXL),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No projects match your search",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(state.projects, key = { it.id }) { project ->
                ProjectCard(
                    project = project,
                    onClick = { onOpenProject(project.id) },
                    modifier = Modifier.padding(horizontal = NeoVedicSpacing.LG)
                )
            }
        }
    }
}
