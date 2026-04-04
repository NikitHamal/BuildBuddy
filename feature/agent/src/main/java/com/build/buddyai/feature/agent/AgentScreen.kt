package com.build.buddyai.feature.agent

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.core.designsystem.component.*
import com.build.buddyai.core.designsystem.theme.BuildBuddyTheme
import com.build.buddyai.core.designsystem.theme.NeoVedicSpacing
import com.build.buddyai.core.model.*
import kotlinx.coroutines.launch

@Composable
fun AgentScreen(
    projectId: String,
    viewModel: AgentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.messages.size, state.streamingContent) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Mode selector
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = NeoVedicSpacing.MD, vertical = NeoVedicSpacing.SM),
            horizontalArrangement = Arrangement.spacedBy(NeoVedicSpacing.SM)
        ) {
            items(AgentMode.entries.toList()) { mode ->
                NeoVedicChip(
                    label = mode.displayName,
                    selected = state.agentMode == mode,
                    onClick = { viewModel.setAgentMode(mode) }
                )
            }
        }

        // Provider/model indicator
        if (state.currentProvider != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = NeoVedicSpacing.LG, vertical = NeoVedicSpacing.XS),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NeoVedicSpacing.SM)
            ) {
                Icon(Icons.Default.SmartToy, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "${state.currentProvider?.name} / ${state.currentModel?.name ?: "No model"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!state.hasApiKey) {
                    StatusBadge(label = "No API Key", color = BuildBuddyTheme.extendedColors.statusWarning)
                }
            }
        }

        NeoVedicDivider()

        // Error banner
        state.errorMessage?.let { error ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier.padding(NeoVedicSpacing.MD),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(NeoVedicSpacing.SM))
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(NeoVedicSpacing.LG),
            verticalArrangement = Arrangement.spacedBy(NeoVedicSpacing.MD)
        ) {
            if (state.messages.isEmpty() && !state.isStreaming) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = NeoVedicSpacing.XXXL),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(NeoVedicSpacing.LG))
                        Text(
                            "How can I help you build?",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(NeoVedicSpacing.SM))
                        Text(
                            "Describe a feature, ask a question, or paste an error to get started.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            items(state.messages, key = { it.id }) { message ->
                MessageBubble(message = message, onDelete = { viewModel.deleteMessage(message.id) })
            }

            if (state.isStreaming && state.streamingContent.isNotEmpty()) {
                item {
                    StreamingBubble(content = state.streamingContent)
                }
            }
        }

        NeoVedicDivider()

        // Attached files
        if (state.attachedFiles.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.padding(horizontal = NeoVedicSpacing.LG, vertical = NeoVedicSpacing.XS),
                horizontalArrangement = Arrangement.spacedBy(NeoVedicSpacing.XS)
            ) {
                items(state.attachedFiles) { file ->
                    NeoVedicChip(
                        label = file.substringAfterLast('/'),
                        onClick = { viewModel.removeAttachment(file) },
                        leadingIcon = Icons.Default.AttachFile
                    )
                }
            }
        }

        // Input area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(NeoVedicSpacing.MD),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = state.inputText,
                onValueChange = { viewModel.updateInput(it) },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        when (state.agentMode) {
                            AgentMode.ASK -> "Ask about your code..."
                            AgentMode.PLAN -> "Describe what to implement..."
                            AgentMode.APPLY -> "Describe the changes..."
                            AgentMode.AUTO -> "What should I build?"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                maxLines = 5,
                shape = MaterialTheme.shapes.small,
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            Spacer(Modifier.width(NeoVedicSpacing.SM))
            if (state.isStreaming) {
                IconButton(
                    onClick = { viewModel.cancelStream() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Stop, "Stop", tint = MaterialTheme.colorScheme.onError)
                }
            } else {
                IconButton(
                    onClick = { viewModel.sendMessage() },
                    enabled = state.inputText.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                ) {
                    Icon(
                        Icons.Default.Send, "Send",
                        tint = if (state.inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, onDelete: () -> Unit) {
    val isUser = message.role == MessageRole.USER

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(NeoVedicSpacing.MD)) {
                if (!isUser) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("BuildBuddy AI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(NeoVedicSpacing.XS))
                }

                if (message.status == MessageStatus.ERROR) {
                    Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.height(4.dp))
                }

                Text(
                    text = message.content,
                    style = if (!isUser) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Default)
                    else MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )

                if (message.tokenCount != null) {
                    Spacer(Modifier.height(NeoVedicSpacing.XS))
                    Text(
                        "${message.tokenCount} tokens",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingBubble(content: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(NeoVedicSpacing.MD)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(NeoVedicSpacing.SM))
                    Text("Generating...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(NeoVedicSpacing.XS))
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}