package com.build.buddyai.feature.agent.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.build.buddyai.core.designsystem.theme.NvShapes
import com.build.buddyai.core.designsystem.theme.NvSpacing
import com.build.buddyai.core.model.AgentAutonomyMode
import com.build.buddyai.core.model.AiModel
import com.build.buddyai.core.model.AiProvider

@Composable
fun ModeSelectionDialog(
    currentMode: AgentAutonomyMode,
    onModeSelected: (AgentAutonomyMode) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = NvShapes.medium,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(NvSpacing.Md), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Autonomy Mode",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                AgentAutonomyMode.entries.forEach { mode ->
                    val isSelected = mode == currentMode
                    Surface(
                        onClick = { onModeSelected(mode) },
                        shape = NvShapes.small,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(mode.displayName, style = MaterialTheme.typography.labelLarge, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                            Text(mode.description, style = MaterialTheme.typography.bodySmall, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModelSelectionDialog(
    providers: List<AiProvider>,
    currentModelId: String?,
    onModelSelected: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = NvShapes.medium,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(NvSpacing.Md), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Select AI Model",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.heightIn(max = 400.dp)) {
                    val configuredProviders = providers.filter { it.isConfigured }
                    if (configuredProviders.isEmpty()) {
                        item {
                           Text(
                               "No AI providers configured. Add an API key in settings to continue.",
                               style = MaterialTheme.typography.bodySmall,
                               color = MaterialTheme.colorScheme.onSurfaceVariant,
                               modifier = Modifier.padding(16.dp),
                               textAlign = TextAlign.Center
                           )
                        }
                    } else {
                        configuredProviders.forEach { provider ->
                            item {
                                Text(
                                    provider.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                                )
                            }
                            items(provider.models) { model ->
                                val isSelected = model.id == currentModelId
                                Surface(
                                    onClick = { onModelSelected(provider.id, model.id) },
                                    shape = NvShapes.small,
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Filled.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            model.name.substringAfterLast('/'),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isSelected) {
                                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
