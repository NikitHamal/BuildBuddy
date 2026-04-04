package com.build.buddyai.feature.build.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.core.designsystem.component.*
import com.build.buddyai.core.designsystem.theme.BuildBuddyTheme
import com.build.buddyai.core.designsystem.theme.NeoVedicSpacing
import com.build.buddyai.core.model.*
import com.build.buddyai.core.ui.component.BuildLogView

@Composable
fun BuildScreen(
    projectId: String,
    viewModel: BuildViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var activeSection by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Build action bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(NeoVedicSpacing.LG)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Build", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusBadge(
                                label = state.currentStatus.displayName,
                                color = when (state.currentStatus) {
                                    BuildStatus.SUCCESS -> BuildBuddyTheme.extendedColors.statusSuccess
                                    BuildStatus.FAILED -> BuildBuddyTheme.extendedColors.statusError
                                    BuildStatus.CANCELLED -> BuildBuddyTheme.extendedColors.statusWarning
                                    BuildStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> BuildBuddyTheme.extendedColors.statusRunning
                                }
                            )
                            if (state.progressPhase.isNotEmpty()) {
                                Spacer(Modifier.width(NeoVedicSpacing.SM))
                                Text(state.progressPhase, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(NeoVedicSpacing.SM)) {
                        if (state.isBuilding) {
                            NeoVedicOutlinedButton(text = "Cancel", onClick = { viewModel.cancelBuild() }, icon = Icons.Default.Close)
                        } else {
                            NeoVedicOutlinedButton(text = "Clean", onClick = { viewModel.cleanBuild() }, icon = Icons.Default.CleaningServices)
                            NeoVedicButton(text = "Build", onClick = { viewModel.startBuild() }, icon = Icons.Default.Build)
                        }
                    }
                }
                if (state.isBuilding) {
                    Spacer(Modifier.height(NeoVedicSpacing.SM))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                }
            }
        }

        // Tab row
        TabRow(
            selectedTabIndex = activeSection,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(selected = activeSection == 0, onClick = { activeSection = 0 }) {
                Text("Logs", modifier = Modifier.padding(NeoVedicSpacing.MD), style = MaterialTheme.typography.labelMedium)
            }
            Tab(selected = activeSection == 1, onClick = { activeSection = 1 }) {
                Text("Diagnostics", modifier = Modifier.padding(NeoVedicSpacing.MD), style = MaterialTheme.typography.labelMedium)
            }
            Tab(selected = activeSection == 2, onClick = { activeSection = 2 }) {
                Text("History", modifier = Modifier.padding(NeoVedicSpacing.MD), style = MaterialTheme.typography.labelMedium)
            }
        }

        when (activeSection) {
            0 -> {
                if (state.logEntries.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Outlined.Terminal,
                        title = "No Build Logs",
                        description = "Start a build to see logs here.",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    BuildLogView(
                        entries = state.logEntries,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            1 -> {
                if (state.diagnostics.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Outlined.CheckCircle,
                        title = "No Diagnostics",
                        description = "Build diagnostics will appear here.",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(NeoVedicSpacing.LG),
                        verticalArrangement = Arrangement.spacedBy(NeoVedicSpacing.SM)
                    ) {
                        items(state.diagnostics) { diagnostic ->
                            NeoVedicCard(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(NeoVedicSpacing.MD), verticalAlignment = Alignment.Top) {
                                    Icon(
                                        when (diagnostic.severity) {
                                            DiagnosticSeverity.ERROR -> Icons.Default.Error
                                            DiagnosticSeverity.WARNING -> Icons.Default.Warning
                                            DiagnosticSeverity.INFO -> Icons.Default.Info
                                            DiagnosticSeverity.HINT -> Icons.Default.Lightbulb
                                        },
                                        null,
                                        tint = when (diagnostic.severity) {
                                            DiagnosticSeverity.ERROR -> BuildBuddyTheme.extendedColors.statusError
                                            DiagnosticSeverity.WARNING -> BuildBuddyTheme.extendedColors.statusWarning
                                            DiagnosticSeverity.INFO -> BuildBuddyTheme.extendedColors.statusInfo
                                            DiagnosticSeverity.HINT -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(NeoVedicSpacing.SM))
                                    Column {
                                        Text(diagnostic.message, style = MaterialTheme.typography.bodySmall)
                                        if (diagnostic.filePath != null) {
                                            Text(
                                                "${diagnostic.filePath}${diagnostic.lineNumber?.let { ":$it" } ?: ""}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                if (state.buildHistory.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Outlined.History,
                        title = "No Build History",
                        description = "Previous builds will be listed here.",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(NeoVedicSpacing.LG),
                        verticalArrangement = Arrangement.spacedBy(NeoVedicSpacing.SM)
                    ) {
                        items(state.buildHistory, key = { it.id }) { record ->
                            NeoVedicCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(NeoVedicSpacing.MD),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        StatusBadge(
                                            label = record.status.displayName,
                                            color = when (record.status) {
                                                BuildStatus.SUCCESS -> BuildBuddyTheme.extendedColors.statusSuccess
                                                BuildStatus.FAILED -> BuildBuddyTheme.extendedColors.statusError
                                                else -> BuildBuddyTheme.extendedColors.statusRunning
                                            }
                                        )
                                        Spacer(Modifier.height(NeoVedicSpacing.XS))
                                        Text(
                                            "${record.variant} · ${record.durationMs?.let { "${it / 1000}s" } ?: "–"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        com.build.buddyai.core.ui.component.formatRelativeTime(record.startedAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (record.errorSummary != null) {
                                    Text(
                                        record.errorSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BuildBuddyTheme.extendedColors.statusError,
                                        modifier = Modifier.padding(start = NeoVedicSpacing.MD, end = NeoVedicSpacing.MD, bottom = NeoVedicSpacing.MD)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}