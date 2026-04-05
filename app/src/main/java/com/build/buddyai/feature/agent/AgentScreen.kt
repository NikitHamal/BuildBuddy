package com.build.buddyai.feature.agent

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.core.designsystem.component.NvBackButton
import com.build.buddyai.core.designsystem.component.NvTopBar

@Composable
fun AgentScreen(
    projectId: String,
    onBack: () -> Unit,
    onNavigateToModels: () -> Unit
) {
    val viewModel: AgentViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(projectId) { viewModel.initialize(projectId) }
    LaunchedEffect(uiState.messages.size, uiState.currentActions.size, uiState.recentDiffs.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            NvTopBar(
                title = "AI Agent",
                subtitle = "Planner, executor, validator",
                navigationIcon = { NvBackButton(onBack) },
                actions = {
                    IconButton(onClick = onNavigateToModels) {
                        Icon(Icons.Filled.Settings, contentDescription = "Configure AI Providers")
                    }
                }
            )
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AgentWorkspaceBody(
                uiState = uiState,
                listState = listState,
                onNavigateToModels = onNavigateToModels,
                onInputChanged = viewModel::updateInput,
                onSend = viewModel::sendMessage,
                onCancel = viewModel::cancelStream,
                onToggleAttachment = viewModel::toggleFileAttachment
            )
        }
    }
}
