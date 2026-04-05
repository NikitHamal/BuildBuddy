package com.build.buddyai.feature.agent

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.R
import com.build.buddyai.core.designsystem.component.NvBackButton
import com.build.buddyai.core.designsystem.component.NvEmptyState
import com.build.buddyai.core.designsystem.component.NvFilledButton
import com.build.buddyai.core.designsystem.component.NvTopBar
import com.build.buddyai.core.designsystem.theme.NvElevation
import com.build.buddyai.core.designsystem.theme.NvShapes
import com.build.buddyai.core.designsystem.theme.NvSpacing
import com.build.buddyai.core.model.ProblemSeverity

@Composable
fun AgentScreen(
    projectId: String,
    onBack: () -> Unit,
    onNavigateToModels: () -> Unit
) {
    val viewModel: AgentViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val latestArtifact = uiState.latestArtifact

    LaunchedEffect(projectId) { viewModel.initialize(projectId) }
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size - 1)
    }

    Scaffold(
        topBar = {
            NvTopBar(
                title = "AI Agent",
                subtitle = "Planner • executor • validator",
                navigationIcon = { NvBackButton(onBack) },
                actions = {
                    IconButton(onClick = onNavigateToModels) {
                        Icon(Icons.Filled.Settings, contentDescription = "Configure AI Providers")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AgentStatusHeader(
                modelName = uiState.modelName,
                providerName = uiState.providerName
            )

            if (!uiState.hasProvider) {
                NvEmptyState(
                    icon = Icons.Filled.Key,
                    title = stringResource(R.string.agent_no_provider),
                    subtitle = "Add an API key to start using AI features",
                    modifier = Modifier.weight(1f),
                    action = {
                        NvFilledButton(text = stringResource(R.string.agent_configure_provider), onClick = onNavigateToModels)
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = NvSpacing.Sm, vertical = NvSpacing.Xs),
                    verticalArrangement = Arrangement.spacedBy(NvSpacing.Xs)
                ) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Autonomous workflow", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "The agent now plans, executes, validates integrity, runs a build, remembers failures, and stores rollbackable change sets.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AssistChip(onClick = {}, label = { Text("Planner") }, leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) })
                                    AssistChip(onClick = {}, label = { Text("Change sets") }, leadingIcon = { Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp)) })
                                    AssistChip(onClick = {}, label = { Text("Validation") }, leadingIcon = { Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(16.dp)) })
                                }
                            }
                        }
                    }

                    if (uiState.currentPlanGoal != null || uiState.currentPlanSteps.isNotEmpty()) {
                        item {
                            Card {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Plan", style = MaterialTheme.typography.titleSmall)
                                    uiState.currentPlanGoal?.let {
                                        Text(it, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    uiState.currentPlanSteps.forEachIndexed { index, step ->
                                        Text("${index + 1}. $step", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.currentActions.isNotEmpty()) {
                        item {
                            Card {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Execution", style = MaterialTheme.typography.titleSmall)
                                    uiState.currentActions.forEach { action ->
                                        ActionTimelineItem(action = action)
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.recentChangeSets.isNotEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Change sets", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                        OutlinedButton(onClick = viewModel::rollbackLatestChangeSet) {
                                            Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.size(8.dp))
                                            Text("Rollback latest")
                                        }
                                    }
                                    uiState.recentChangeSets.forEach { changeSet ->
                                        Text(changeSet.summary, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.recentDiffs.isNotEmpty()) {
                        item { RecentChangesCard(diffs = uiState.recentDiffs) }
                    }

                    if (uiState.problems.isNotEmpty()) {
                        item {
                            Card {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Problems", style = MaterialTheme.typography.titleSmall)
                                    uiState.problems.take(10).forEach { problem ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                            Icon(
                                                imageVector = when (problem.severity) {
                                                    ProblemSeverity.ERROR -> Icons.Filled.Build
                                                    ProblemSeverity.WARNING -> Icons.Filled.Settings
                                                    ProblemSeverity.INFO -> Icons.Filled.AutoAwesome
                                                },
                                                contentDescription = null,
                                                tint = when (problem.severity) {
                                                    ProblemSeverity.ERROR -> MaterialTheme.colorScheme.error
                                                    ProblemSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
                                                    ProblemSeverity.INFO -> MaterialTheme.colorScheme.primary
                                                },
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Column {
                                                Text(problem.title, style = MaterialTheme.typography.bodyMedium)
                                                Text(problem.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.lastBuildStatus != null || uiState.lastBuildSummary != null) {
                        item {
                            BuildValidationCard(
                                status = uiState.lastBuildStatus,
                                summary = uiState.lastBuildSummary
                            )
                        }
                    }

                    if (latestArtifact != null) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Artifact from chat", style = MaterialTheme.typography.titleSmall)
                                    Text(latestArtifact.fileName, style = MaterialTheme.typography.bodyMedium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = {
                                            when (val result = viewModel.installLatestArtifact(context)) {
                                                is com.build.buddyai.core.common.BuildArtifactInstaller.InstallResult.Started -> Toast.makeText(context, "Installer opened", Toast.LENGTH_SHORT).show()
                                                is com.build.buddyai.core.common.BuildArtifactInstaller.InstallResult.PermissionRequired -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                                is com.build.buddyai.core.common.BuildArtifactInstaller.InstallResult.Error -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                            }
                                        }) {
                                            Icon(Icons.Filled.InstallMobile, contentDescription = null)
                                            Spacer(Modifier.size(8.dp))
                                            Text("Install")
                                        }
                                        OutlinedButton(onClick = {
                                            viewModel.shareLatestArtifact(context).onFailure {
                                                Toast.makeText(context, it.message ?: "Unable to share artifact", Toast.LENGTH_LONG).show()
                                            }
                                        }) {
                                            Icon(Icons.Filled.Share, contentDescription = null)
                                            Spacer(Modifier.size(8.dp))
                                            Text("Share")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.messages.isEmpty()) {
                        item {
                            NvEmptyState(
                                icon = Icons.Filled.AutoAwesome,
                                title = stringResource(R.string.agent_empty_title),
                                subtitle = "Ask for production fixes, full feature work, audits, or refactors. The agent now validates and can install built artifacts from this screen.",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        items(uiState.messages, key = { it.id }) { message ->
                            ChatMessageItem(message = message)
                        }
                    }
                }

                Surface(
                    tonalElevation = NvElevation.Sm,
                    modifier = Modifier.fillMaxWidth().imePadding()
                ) {
                    Column(modifier = Modifier.padding(NvSpacing.Sm)) {
                        if (uiState.attachedFiles.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xxs),
                                modifier = Modifier.padding(bottom = NvSpacing.Xxs)
                            ) {
                                items(uiState.attachedFiles) { file ->
                                    InputChip(
                                        selected = true,
                                        onClick = { viewModel.toggleFileAttachment(file) },
                                        label = { Text(file.substringAfterLast("/"), style = MaterialTheme.typography.labelSmall) },
                                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            OutlinedTextField(
                                value = uiState.currentInput,
                                onValueChange = viewModel::updateInput,
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(stringResource(R.string.agent_input_hint), style = MaterialTheme.typography.bodySmall) },
                                maxLines = 5,
                                shape = NvShapes.small,
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.size(8.dp))
                            if (uiState.isStreaming) {
                                FilledIconButton(
                                    onClick = viewModel::cancelStream,
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) { Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.agent_stop)) }
                            } else {
                                FilledIconButton(
                                    onClick = viewModel::sendMessage,
                                    enabled = uiState.currentInput.isNotBlank()
                                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.agent_send)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
