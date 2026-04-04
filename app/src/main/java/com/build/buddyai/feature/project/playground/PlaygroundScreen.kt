package com.build.buddyai.feature.project.playground

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.R
import com.build.buddyai.core.designsystem.component.*
import com.build.buddyai.core.designsystem.theme.*
import com.build.buddyai.feature.build.BuildWorkspaceScreen
import com.build.buddyai.feature.editor.WorkspaceScreen
import com.build.buddyai.feature.project.overview.OverviewTab

enum class PlaygroundTab(val titleRes: Int, val icon: @Composable () -> Unit) {
    OVERVIEW(R.string.playground_overview, { Icon(Icons.Filled.Dashboard, contentDescription = null) }),
    WORKSPACE(R.string.playground_workspace, { Icon(Icons.Filled.Code, contentDescription = null) }),
    BUILD(R.string.playground_build, { Icon(Icons.Filled.Build, contentDescription = null) })
}

@Composable
fun PlaygroundScreen(
    projectId: String,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToAgent: () -> Unit
) {
    val viewModel: PlaygroundViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(PlaygroundTab.OVERVIEW) }

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    Scaffold(
        topBar = {
            NvTopBar(
                title = uiState.project?.name ?: stringResource(R.string.loading),
                subtitle = uiState.project?.packageName,
                navigationIcon = { NvBackButton(onBack) },
                actions = {
                    IconButton(onClick = onNavigateToAgent) {
                        Icon(Icons.Filled.Psychology, contentDescription = "AI Agent")
                    }
                    IconButton(onClick = onNavigateToModels) {
                        Icon(Icons.Filled.Key, contentDescription = "AI Providers")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = NvElevation.None
            ) {
                PlaygroundTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = tab.icon,
                        label = { Text(stringResource(tab.titleRes), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) { NvLoadingIndicator(message = stringResource(R.string.loading)) }
        } else {
            AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier.fillMaxSize().padding(padding),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tab"
            ) { tab ->
                when (tab) {
                    PlaygroundTab.OVERVIEW -> OverviewTab(
                        projectId = projectId,
                        onNavigateToTab = { selectedTab = it },
                        onNavigateToAgent = onNavigateToAgent
                    )
                    PlaygroundTab.WORKSPACE -> WorkspaceScreen(projectId = projectId)
                    PlaygroundTab.BUILD -> BuildWorkspaceScreen(
                        projectId = projectId,
                        onNavigateToAgent = onNavigateToAgent
                    )
                }
            }
        }
    }
}
