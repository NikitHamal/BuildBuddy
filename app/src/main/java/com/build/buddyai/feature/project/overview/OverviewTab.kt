package com.build.buddyai.feature.project.overview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.R
import com.build.buddyai.core.designsystem.component.*
import com.build.buddyai.core.designsystem.theme.*
import com.build.buddyai.core.model.BuildStatus
import com.build.buddyai.feature.project.playground.PlaygroundTab
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun OverviewTab(
    projectId: String,
    onNavigateToTab: (PlaygroundTab) -> Unit,
    onNavigateToAgent: () -> Unit,
    viewModel: OverviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(projectId) { viewModel.loadProject(projectId) }

    val project = uiState.project
    if (project == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            NvLoadingIndicator()
        }
        return
    }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NvSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
    ) {
        // Project info card
        item {
            NvCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(NvSpacing.Md)) {
                    Text(project.name, style = MaterialTheme.typography.titleLarge)
                    Text(project.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (project.description.isNotBlank()) {
                        Spacer(Modifier.height(NvSpacing.Xs))
                        Text(project.description, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(NvSpacing.Sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
                        NvStatusChip(label = project.language.displayName, containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        NvStatusChip(label = project.uiFramework.displayName, containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        NvStatusChip(label = project.template.displayName, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                    }
                    Spacer(Modifier.height(NvSpacing.Xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Md)) {
                        Text("Min SDK: ${project.minSdk}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Target SDK: ${project.targetSdk}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Created: ${dateFormat.format(Date(project.createdAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Build status card
        item {
            NvCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(NvSpacing.Md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(NvSpacing.Xs))
                        Text(stringResource(R.string.build_last_build), style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(NvSpacing.Xs))
                    val buildStatusColor = when (project.lastBuildStatus) {
                        BuildStatus.SUCCESS -> BuildBuddyThemeExtended.colors.success
                        BuildStatus.FAILED -> MaterialTheme.colorScheme.error
                        BuildStatus.BUILDING -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(project.lastBuildStatus.displayName, style = MaterialTheme.typography.bodyMedium, color = buildStatusColor)
                    if (project.lastBuildAt != null) {
                        Text("Built: ${dateFormat.format(Date(project.lastBuildAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(NvSpacing.Sm))
                    NvFilledButton(
                        text = stringResource(R.string.build_start),
                        onClick = { onNavigateToTab(PlaygroundTab.BUILD) },
                        icon = Icons.Filled.PlayArrow
                    )
                }
            }
        }

        // Quick actions
        item {
            Text(stringResource(R.string.home_quick_actions), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = NvSpacing.Xs))
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
            ) {
                OverviewActionCard(
                    icon = Icons.Filled.Psychology,
                    label = "AI Agent",
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToAgent
                )
                OverviewActionCard(
                    icon = Icons.Filled.Code,
                    label = "Workspace",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToTab(PlaygroundTab.WORKSPACE) }
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
            ) {
                OverviewActionCard(
                    icon = Icons.Filled.Build,
                    label = stringResource(R.string.playground_build),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToTab(PlaygroundTab.BUILD) }
                )
            }
        }
    }
}

@Composable
private fun OverviewActionCard(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    NvCard(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier.padding(NvSpacing.Md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(NvSpacing.Xs))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
