package com.build.buddyai.feature.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.R
import com.build.buddyai.core.designsystem.BuildBuddyCard
import com.build.buddyai.core.designsystem.NeoVedicTheme
import com.build.buddyai.core.designsystem.StatusBadge
import com.build.buddyai.core.model.BuildStatus
import com.build.buddyai.core.model.Project
import com.build.buddyai.core.model.ProjectFilter
import com.build.buddyai.core.model.SortMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onCreateProject: () -> Unit,
    onModels: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = NeoVedicTheme.spacing
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(viewModel::importProject)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.home_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(300.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = state.search,
                        onValueChange = viewModel::updateSearch,
                        label = { Text(stringResource(R.string.home_search_hint)) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        AssistChip(
                            onClick = { viewModel.updateSort(SortMode.RECENT) },
                            label = { Text(stringResource(R.string.home_sort_recent)) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (state.sortMode == SortMode.RECENT) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surface,
                            ),
                        )
                        AssistChip(
                            onClick = { viewModel.updateSort(SortMode.NAME) },
                            label = { Text(stringResource(R.string.home_sort_name)) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (state.sortMode == SortMode.NAME) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surface,
                            ),
                        )
                        AssistChip(onClick = { viewModel.updateFilter(ProjectFilter.ALL) }, label = { Text(stringResource(R.string.home_filter_all)) })
                        AssistChip(onClick = { viewModel.updateFilter(ProjectFilter.ACTIVE) }, label = { Text(stringResource(R.string.home_filter_active)) })
                        AssistChip(onClick = { viewModel.updateFilter(ProjectFilter.ARCHIVED) }, label = { Text(stringResource(R.string.home_filter_archived)) })
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        QuickActionCard(
                            title = stringResource(R.string.action_new_project),
                            icon = Icons.Outlined.Add,
                            onClick = onCreateProject,
                        )
                        QuickActionCard(
                            title = stringResource(R.string.action_import),
                            icon = Icons.Outlined.FileDownload,
                            onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                        )
                        QuickActionCard(
                            title = stringResource(R.string.nav_models),
                            icon = Icons.Outlined.Api,
                            onClick = onModels,
                        )
                        QuickActionCard(
                            title = stringResource(R.string.nav_settings),
                            icon = Icons.Outlined.Settings,
                            onClick = onSettings,
                        )
                    }
                    if (state.projects.isEmpty()) {
                        BuildBuddyCard {
                            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleLarge)
                                Text(
                                    stringResource(R.string.home_empty_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            items(state.projects, key = { it.id }) { project ->
                ProjectCard(project = project, onOpen = { viewModel.openProject(project.id) })
            }
            if (state.recentBuilds.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.home_recent_builds),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = spacing.sm),
                    )
                }
                items(state.recentBuilds, key = { it.id }) { build ->
                    BuildBuddyCard {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            Text(build.summary, style = MaterialTheme.typography.titleMedium)
                            val statusColor = when (build.status) {
                                BuildStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                                BuildStatus.FAILED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.secondary
                            }
                            StatusBadge(label = build.status.name, color = statusColor)
                            Text(
                                build.rawLog.takeLast(220).ifBlank { build.summary },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    BuildBuddyCard(
        modifier = Modifier.clickable(onClick = onClick),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    onOpen: () -> Unit,
) {
    val spacing = NeoVedicTheme.spacing
    BuildBuddyCard(modifier = Modifier.clickable(onClick = onOpen)) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(project.name, style = MaterialTheme.typography.titleLarge)
            Text(project.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(project.language.name, MaterialTheme.colorScheme.secondary)
                StatusBadge(project.uiToolkit.name, MaterialTheme.colorScheme.primary)
            }
            Text(
                project.description.ifBlank { "No project brief yet." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
