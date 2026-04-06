package com.build.buddyai.feature.agent.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.build.buddyai.core.designsystem.theme.NvShapes
import com.build.buddyai.core.designsystem.theme.NvSpacing
import com.build.buddyai.core.model.AgentAutonomyMode
import com.build.buddyai.core.model.AiProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentPromptBar(
    currentInput: String,
    attachedFiles: List<String>,
    autonomyMode: AgentAutonomyMode,
    modelName: String?,
    providerName: String?,
    allProviders: List<AiProvider>,
    supportsImageAttachments: Boolean,
    isStreaming: Boolean,
    onUpdateInput: (String) -> Unit,
    onSendMessage: () -> Unit,
    onCancelStream: () -> Unit,
    onAttachImage: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onUpdateMode: (AgentAutonomyMode) -> Unit,
    onSelectModel: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Ask anything..."
) {
    var showModeDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }

    if (showModeDialog) {
        ModeSelectionDialog(
            currentMode = autonomyMode,
            onModeSelected = { onUpdateMode(it); showModeDialog = false },
            onDismiss = { showModeDialog = false }
        )
    }

    if (showModelDialog) {
        ModelSelectionDialog(
            providers = allProviders,
            currentModelId = allProviders.find { it.name == providerName }?.selectedModelId,
            onModelSelected = { providerId, modelId -> onSelectModel(providerId, modelId); showModelDialog = false },
            onDismiss = { showModelDialog = false }
        )
    }

    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        shape = NvShapes.medium,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .imePadding()
    ) {
        Column(
            modifier = Modifier.padding(NvSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(NvSpacing.Xxs)
        ) {
            if (attachedFiles.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xxs),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    items(attachedFiles) { file ->
                        InputChip(
                            selected = true,
                            onClick = { onRemoveAttachment(file) },
                            label = { Text(file.substringAfterLast('/'), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = currentInput,
                onValueChange = onUpdateInput,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                maxLines = 6,
                shape = NvShapes.small,
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    errorBorderColor = Color.Transparent
                )
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Mode Selector
                TextButton(
                    onClick = { showModeDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = autonomyMode.displayName.substringBefore(" "),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                }

                // Model Selector
                TextButton(
                    onClick = { showModelDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp).weight(1f, fill = false)
                ) {
                    val normalizedModel = modelName?.substringAfterLast('/') ?: "Select Model"
                    Text(
                        text = normalizedModel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                }

                Spacer(Modifier.weight(1f))

                if (supportsImageAttachments) {
                    IconButton(
                        onClick = onAttachImage,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Attach image", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (isStreaming) {
                    FilledIconButton(
                        onClick = onCancelStream,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.size(36.dp)
                    ) { Icon(Icons.Filled.Stop, contentDescription = "Stop", modifier = Modifier.size(20.dp)) }
                } else {
                    FilledIconButton(
                        onClick = onSendMessage,
                        enabled = currentInput.isNotBlank() || attachedFiles.isNotEmpty(),
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp)
                    ) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Send", modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }
}
