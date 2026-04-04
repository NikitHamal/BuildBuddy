package com.build.buddyai.feature.project.playground

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.core.designsystem.component.LoadingView
import com.build.buddyai.core.designsystem.theme.NeoVedicSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaygroundScreen(
    projectId: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: PlaygroundViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val project = state.project

    if (state.isLoading || project == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingView(message = "Loading project...")
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            project.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            project.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Project Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = NeoVedicSpacing.XXS
            ) {
                PlaygroundTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = state.currentTab == tab,
                        onClick = { viewModel.setTab(tab) },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    PlaygroundTab.OVERVIEW -> Icons.Outlined.Dashboard
                                    PlaygroundTab.AGENT -> Icons.Outlined.AutoAwesome
                                    PlaygroundTab.EDITOR -> Icons.Outlined.Code
                                    PlaygroundTab.FILES -> Icons.Outlined.FolderOpen
                                    PlaygroundTab.BUILD -> Icons.Outlined.Build
                                    PlaygroundTab.ARTIFACTS -> Icons.Outlined.Inventory2
                                },
                                contentDescription = tab.title
                            )
                        },
                        label = { Text(tab.title, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (state.currentTab) {
                PlaygroundTab.OVERVIEW -> OverviewContent(project = project)
                PlaygroundTab.AGENT -> AgentPlaceholderContent(projectId = projectId)
                PlaygroundTab.EDITOR -> EditorPlaceholderContent(projectId = projectId)
                PlaygroundTab.FILES -> FilesPlaceholderContent(projectId = projectId)
                PlaygroundTab.BUILD -> BuildPlaceholderContent(projectId = projectId)
                PlaygroundTab.ARTIFACTS -> ArtifactsPlaceholderContent(projectId = projectId)
            }
        }
    }
}

@Composable
private fun OverviewContent(project: com.build.buddyai.core.model.Project) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NeoVedicSpacing.LG),
        verticalArrangement = Arrangement.spacedBy(NeoVedicSpacing.MD)
    ) {
        com.build.buddyai.core.designsystem.component.NeoVedicCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(NeoVedicSpacing.LG)) {
                Text("Project Info", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(NeoVedicSpacing.SM))
                InfoRow("Name", project.name)
                InfoRow("Package", project.packageName)
                InfoRow("Language", project.language.displayName)
                InfoRow("UI", project.uiFramework.displayName)
                InfoRow("Template", project.template.displayName)
                InfoRow("Min SDK", project.minSdk.toString())
                InfoRow("Target SDK", project.targetSdk.toString())
                if (project.description.isNotBlank()) {
                    InfoRow("Description", project.description)
                }
            }
        }

        com.build.buddyai.core.designsystem.component.NeoVedicCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(NeoVedicSpacing.LG)) {
                Text("Quick Actions", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(NeoVedicSpacing.SM))
                Row(horizontalArrangement = Arrangement.spacedBy(NeoVedicSpacing.SM)) {
                    com.build.buddyai.core.designsystem.component.NeoVedicChip(
                        label = "Build",
                        onClick = {},
                        leadingIcon = Icons.Default.Build
                    )
                    com.build.buddyai.core.designsystem.component.NeoVedicChip(
                        label = "AI Chat",
                        onClick = {},
                        leadingIcon = Icons.Default.AutoAwesome
                    )
                    com.build.buddyai.core.designsystem.component.NeoVedicChip(
                        label = "Edit Code",
                        onClick = {},
                        leadingIcon = Icons.Default.Code
                    )
                }
            }
        }

        project.lastBuildStatus?.let { status ->
            com.build.buddyai.core.designsystem.component.NeoVedicCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(NeoVedicSpacing.LG)) {
                    Text("Last Build", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(NeoVedicSpacing.SM))
                    com.build.buddyai.core.designsystem.component.StatusBadge(
                        label = status.displayName,
                        color = when (status) {
                            com.build.buddyai.core.model.BuildStatus.SUCCESS -> com.build.buddyai.core.designsystem.theme.NeoVedicColors.StatusSuccess
                            com.build.buddyai.core.model.BuildStatus.FAILED -> com.build.buddyai.core.designsystem.theme.NeoVedicColors.StatusError
                            else -> com.build.buddyai.core.designsystem.theme.NeoVedicColors.StatusRunning
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NeoVedicSpacing.XXS),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AgentPlaceholderContent(projectId: String) {
    // Integrated from feature/agent module
    com.build.buddyai.core.designsystem.component.EmptyStateView(
        icon = Icons.Outlined.AutoAwesome,
        title = "AI Agent",
        description = "Chat with the AI to build, modify, and fix your app code.",
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun EditorPlaceholderContent(projectId: String) {
    com.build.buddyai.core.designsystem.component.EmptyStateView(
        icon = Icons.Outlined.Code,
        title = "Code Editor",
        description = "Open files from the Files tab to start editing.",
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun FilesPlaceholderContent(projectId: String) {
    com.build.buddyai.core.designsystem.component.EmptyStateView(
        icon = Icons.Outlined.FolderOpen,
        title = "Project Files",
        description = "Browse and manage your project files.",
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun BuildPlaceholderContent(projectId: String) {
    com.build.buddyai.core.designsystem.component.EmptyStateView(
        icon = Icons.Outlined.Build,
        title = "Build",
        description = "Build your project and view logs.",
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun ArtifactsPlaceholderContent(projectId: String) {
    com.build.buddyai.core.designsystem.component.EmptyStateView(
        icon = Icons.Outlined.Inventory2,
        title = "Artifacts",
        description = "Generated APKs will appear here after a successful build.",
        modifier = Modifier.fillMaxSize()
    )
}