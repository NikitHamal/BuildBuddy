package com.build.buddyai.feature.home

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.R
import com.build.buddyai.core.common.BuildBuddyAppIcon
import com.build.buddyai.core.designsystem.component.*
import com.build.buddyai.core.designsystem.theme.*
import com.build.buddyai.core.model.BuildStatus
import com.build.buddyai.core.model.Project
import com.build.buddyai.core.model.ProjectLanguage
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    onNavigateToCreateProject: () -> Unit,
    onNavigateToProject: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToModels: () -> Unit
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            NvTopBar(
                title = stringResource(R.string.home_title),
                navigationIcon = { BuildBuddyAppIcon(modifier = Modifier.size(36.dp)) },
                actions = {
                    IconButton(onClick = onNavigateToModels) {
                        Icon(Icons.Filled.Psychology, contentDescription = stringResource(R.string.models_title))
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.projectCount > 0) {
                FloatingActionButton(
                    onClick = onNavigateToCreateProject,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.home_create_project))
                }
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { NvLoadingIndicator(message = stringResource(R.string.loading)) }
        } else if (uiState.projectCount == 0) {
            HomeEmptyState(
                modifier = Modifier.fillMaxSize().padding(padding),
                onCreateProject = onNavigateToCreateProject
            )
        } else {
            HomeContent(
                uiState = uiState,
                modifier = Modifier.fillMaxSize().padding(padding),
                onProjectClick = onNavigateToProject,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onSortModeChange = viewModel::updateSortMode,
                onDeleteProject = { showDeleteDialog = it },
                onDuplicateProject = viewModel::duplicateProject,
                onCreateProject = onNavigateToCreateProject
            )
        }
    }

    showDeleteDialog?.let { projectId ->
        NvAlertDialog(
            title = stringResource(R.string.action_delete),
            message = stringResource(R.string.files_delete_confirm, "project"),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = {
                viewModel.deleteProject(projectId)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null },
            isDestructive = true
        )
    }
}

@Composable
private fun HomeEmptyState(
    modifier: Modifier = Modifier,
    onCreateProject: () -> Unit
) {
    NvEmptyState(
        icon = Icons.Filled.Code,
        title = stringResource(R.string.home_empty_title),
        subtitle = stringResource(R.string.home_empty_subtitle),
        modifier = modifier,
        action = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NvFilledButton(
                    text = stringResource(R.string.home_create_project),
                    onClick = onCreateProject,
                    icon = Icons.Filled.Add
                )
                Spacer(Modifier.height(NvSpacing.Sm))
                NvOutlinedButton(
                    text = stringResource(R.string.home_import_project),
                    onClick = {},
                    icon = Icons.Filled.FileOpen
                )
            }
        }
    )
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    modifier: Modifier = Modifier,
    onProjectClick: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onDeleteProject: (String) -> Unit,
    onDuplicateProject: (String) -> Unit,
    onCreateProject: () -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        // Search bar
        item {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NvSpacing.Md, vertical = NvSpacing.Xs)
                    .imePadding(),
                placeholder = { Text(stringResource(R.string.home_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (uiState.searchQuery.isNotEmpty()) {
                    { IconButton(onClick = { onSearchQueryChange("") }) { Icon(Icons.Filled.Clear, contentDescription = null) } }
                } else null,
                singleLine = true,
                shape = NvShapes.small
            )
        }

        // Sort chips
        item {
            LazyRow(
                modifier = Modifier.padding(horizontal = NvSpacing.Md, vertical = NvSpacing.Xs),
                horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)
            ) {
                item {
                    NvFilterChip(
                        label = stringResource(R.string.home_sort_recent),
                        selected = uiState.sortMode == SortMode.RECENT,
                        onClick = { onSortModeChange(SortMode.RECENT) }
                    )
                }
                item {
                    NvFilterChip(
                        label = stringResource(R.string.home_sort_name),
                        selected = uiState.sortMode == SortMode.NAME,
                        onClick = { onSortModeChange(SortMode.NAME) }
                    )
                }
                item {
                    NvFilterChip(
                        label = stringResource(R.string.home_sort_created),
                        selected = uiState.sortMode == SortMode.CREATED,
                        onClick = { onSortModeChange(SortMode.CREATED) }
                    )
                }
            }
        }

        // Quick Actions
        if (uiState.searchQuery.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.home_quick_actions),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = NvSpacing.Md, vertical = NvSpacing.Xs)
                )
            }
            item {
                LazyRow(
                    modifier = Modifier.padding(horizontal = NvSpacing.Md),
                    horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
                ) {
                    item {
                        QuickActionCard(
                            icon = Icons.Filled.Add,
                            label = stringResource(R.string.home_create_project),
                            onClick = onCreateProject
                        )
                    }
                    item {
                        QuickActionCard(
                            icon = Icons.Filled.FileOpen,
                            label = stringResource(R.string.home_import_project),
                            onClick = {}
                        )
                    }
                }
            }
        }

        // Projects header
        item {
            Text(
                text = if (uiState.searchQuery.isNotEmpty()) "Results (${uiState.projects.size})"
                else stringResource(R.string.home_all_projects),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = NvSpacing.Md, top = NvSpacing.Md, bottom = NvSpacing.Xs)
            )
        }

        // Project list
        items(uiState.projects, key = { it.id }) { project ->
            ProjectListItem(
                project = project,
                onClick = { onProjectClick(project.id) },
                onDelete = { onDeleteProject(project.id) },
                onDuplicate = { onDuplicateProject(project.id) }
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    NvCard(onClick = onClick) {
        Row(
            modifier = Modifier.padding(NvSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ProjectListItem(
    project: Project,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    NvCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NvSpacing.Md, vertical = NvSpacing.Xs),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(NvSpacing.Md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Icon
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = NvShapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    border = BorderStroke(NvBorder.Hairline, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        modifier = Modifier.padding(NvSpacing.Sm),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(Modifier.width(NvSpacing.Md))

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = project.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Menu
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.action_more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        shape = NvShapes.medium,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            onClick = { showMenu = false; onDuplicate() },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.error,
                                leadingIconColor = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(NvSpacing.Md))

            // Metadata & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NvChip(
                        label = project.language.displayName,
                        icon = if (project.language == ProjectLanguage.KOTLIN) Icons.Filled.Code else Icons.Filled.Code
                    )
                    NvChip(
                        label = project.uiFramework.displayName,
                        variant = NvChipVariant.SECONDARY
                    )
                }

                if (project.lastBuildStatus != BuildStatus.NONE) {
                    val (statusColor, statusIcon) = when (project.lastBuildStatus) {
                        BuildStatus.SUCCESS -> BuildBuddyThemeExtended.colors.success to Icons.Filled.CheckCircle
                        BuildStatus.FAILED -> MaterialTheme.colorScheme.error to Icons.Filled.Error
                        BuildStatus.BUILDING -> MaterialTheme.colorScheme.primary to Icons.Filled.HourglassTop
                        else -> MaterialTheme.colorScheme.onSurfaceVariant to Icons.Filled.PlayCircle
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            statusIcon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = statusColor
                        )
                        Spacer(Modifier.width(NvSpacing.Xxs))
                        Text(
                            text = project.lastBuildStatus.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                    }
                }
            }
        }
    }
}

