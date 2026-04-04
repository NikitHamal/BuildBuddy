package com.build.buddyai.feature.project.overview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewTab(
    projectId: String,
    onNavigateToTab: (PlaygroundTab) -> Unit,
    onNavigateToAgent: () -> Unit
) {
    val viewModel: OverviewViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(projectId) { viewModel.loadProject(projectId) }

    val project = uiState.project
    if (project == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            NvLoadingIndicator()
        }
        return
    }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NvSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(NvSpacing.Md)
    ) {
        // Project Identity Card
        item {
            NvCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(NvSpacing.Md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = NvShapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Icon(
                                Icons.Filled.RocketLaunch,
                                contentDescription = null,
                                modifier = Modifier.padding(NvSpacing.Sm),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(NvSpacing.Md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(project.name, style = MaterialTheme.typography.headlineSmall)
                            Text(project.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    
                    if (project.description.isNotBlank()) {
                        Spacer(Modifier.height(NvSpacing.Md))
                        Text(
                            project.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(NvSpacing.Md))
                    
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm),
                        verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
                    ) {
                        NvChip(label = project.language.displayName, variant = NvChipVariant.PRIMARY)
                        NvChip(label = project.uiFramework.displayName, variant = NvChipVariant.SECONDARY)
                        NvChip(label = project.template.displayName, variant = NvChipVariant.TERTIARY)
                    }
                }
            }
        }

        // Build & Runtime Info
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NvSpacing.Md)
            ) {
                InfoTile(
                    title = "Target SDK",
                    value = project.targetSdk.toString(),
                    icon = Icons.Filled.Android,
                    modifier = Modifier.weight(1f)
                )
                InfoTile(
                    title = "Min SDK",
                    value = project.minSdk.toString(),
                    icon = Icons.Filled.SettingsSystemDaydream,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Build status card
        item {
            NvCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigateToTab(PlaygroundTab.BUILD) }
            ) {
                Column(modifier = Modifier.padding(NvSpacing.Md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val buildStatusColor = when (project.lastBuildStatus) {
                            BuildStatus.SUCCESS -> BuildBuddyThemeExtended.colors.success
                            BuildStatus.FAILED -> MaterialTheme.colorScheme.error
                            BuildStatus.BUILDING -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(buildStatusColor, shape = MaterialTheme.shapes.extraLarge)
                        )
                        Spacer(Modifier.width(NvSpacing.Sm))
                        Text(
                            "LAST BUILD STATUS",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                    
                    Spacer(Modifier.height(NvSpacing.Sm))
                    
                    Text(
                        project.lastBuildStatus.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = when (project.lastBuildStatus) {
                            BuildStatus.SUCCESS -> BuildBuddyThemeExtended.colors.success
                            BuildStatus.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    
                    if (project.lastBuildAt != null) {
                        Text(
                            "Finished ${dateFormat.format(Date(project.lastBuildAt))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "No builds yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Quick actions
        item {
            Text(
                "QUICK ACTIONS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = NvSpacing.Sm)
            )
        }
        
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NvSpacing.Md)
            ) {
                OverviewActionCard(
                    icon = Icons.Filled.Psychology,
                    label = "AI Agent",
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToAgent
                )
                OverviewActionCard(
                    icon = Icons.Filled.Code,
                    label = "Editor",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToTab(PlaygroundTab.WORKSPACE) }
                )
                OverviewActionCard(
                    icon = Icons.Filled.Build,
                    label = "Build",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToTab(PlaygroundTab.BUILD) }
                )
            }
        }
        
        item {
            Text(
                "Created on ${SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(project.createdAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun InfoTile(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    NvCard(modifier = modifier) {
        Column(modifier = Modifier.padding(NvSpacing.Md)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(NvSpacing.Sm))
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
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
    NvCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(NvSpacing.Md).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(NvSpacing.Sm))
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
