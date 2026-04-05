package com.build.buddyai.feature.agent

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.addImageAttachment(uri)
    }

    LaunchedEffect(projectId) { viewModel.initialize(projectId) }
    LaunchedEffect(uiState.messages.size, uiState.pendingReview != null) {
        val itemCount = uiState.messages.size + if (uiState.pendingReview != null) 1 else 0
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Scaffold(
        topBar = {
            NvTopBar(
                title = "AI Agent",
                subtitle = "Project-aware planner · repairer · validator",
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
                    if (uiState.currentPlanGoal != null || uiState.currentPlanSteps.isNotEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Plan", style = MaterialTheme.typography.titleSmall)
                                    uiState.currentPlanGoal?.let {
                                        Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
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
                                    uiState.currentActions.forEach { action -> ActionTimelineItem(action = action) }
                                }
                            }
                        }
                    }

                    uiState.pendingReview?.let { pending ->
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Review staged changes", style = MaterialTheme.typography.titleSmall)
                                    Text(pending.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (pending.reasons.isNotEmpty()) {
                                        pending.reasons.forEach { reason ->
                                            Text("• $reason", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    pending.hunks.take(12).forEach { hunk ->
                                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(hunk.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    AssistChip(
                                                        onClick = { viewModel.toggleReviewHunkAcceptance(hunk.id) },
                                                        label = { Text(if (hunk.accepted) "Included" else "Skipped") },
                                                        leadingIcon = { Icon(if (hunk.accepted) Icons.Filled.Check else Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                    )
                                                }
                                                Text(hunk.filePath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                Text(
                                                    hunk.preview.ifBlank { "No preview available." },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 8,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = viewModel::rejectPendingReview, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Filled.Close, contentDescription = null)
                                            Spacer(Modifier.size(8.dp))
                                            Text("Reject")
                                        }
                                        OutlinedButton(onClick = viewModel::approvePendingReview, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Filled.Check, contentDescription = null)
                                            Spacer(Modifier.size(8.dp))
                                            Text("Apply")
                                        }
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
                                    uiState.recentChangeSets.take(5).forEach { changeSet ->
                                        Text(changeSet.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                                    Text(latestArtifact.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                subtitle = "Ask for audits, refactors, fixes, feature work, or build repairs. The agent now stages sensitive changes for review and can install artifacts from chat.",
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
                    Column(modifier = Modifier.padding(NvSpacing.Sm), verticalArrangement = Arrangement.spacedBy(NvSpacing.Xxs)) {
                        if (uiState.attachedFiles.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xxs)) {
                                items(uiState.attachedFiles) { file ->
                                    InputChip(
                                        selected = true,
                                        onClick = { viewModel.removeAttachment(file) },
                                        label = { Text(file.substringAfterLast('/'), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (uiState.supportsImageAttachments) {
                                FilledIconButton(
                                    onClick = { imagePicker.launch("image/*") },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                                ) {
                                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Attach image")
                                }
                            }
                            OutlinedTextField(
                                value = uiState.currentInput,
                                onValueChange = viewModel::updateInput,
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                placeholder = { Text(stringResource(R.string.agent_input_hint), style = MaterialTheme.typography.bodySmall) },
                                maxLines = 5,
                                shape = NvShapes.small,
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
                            if (uiState.isStreaming) {
                                FilledIconButton(
                                    onClick = viewModel::cancelStream,
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) { Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.agent_stop)) }
                            } else {
                                FilledIconButton(
                                    onClick = viewModel::sendMessage,
                                    enabled = uiState.currentInput.isNotBlank() || uiState.attachedFiles.isNotEmpty()
                                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.agent_send)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
