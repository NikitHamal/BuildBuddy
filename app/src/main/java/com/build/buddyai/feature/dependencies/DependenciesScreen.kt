package com.build.buddyai.feature.dependencies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.core.designsystem.component.NvEmptyState
import com.build.buddyai.core.designsystem.component.NvLoadingIndicator

@Composable
fun DependenciesScreen(projectId: String) {
    val viewModel: DependenciesViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(projectId) { viewModel.initialize(projectId) }

    if (uiState.isLoading) {
        NvLoadingIndicator(message = "Loading dependencies…", modifier = Modifier.fillMaxSize())
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Dependencies manager", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Manage Gradle dependencies, repositories, version-catalog backed entries, and local custom coordinates per project. Toggle entries without deleting them, add repositories, and review dependency risks before the agent touches build files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    uiState.message?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    OutlinedTextField(
                        value = uiState.dependencyNotation,
                        onValueChange = viewModel::updateNotation,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Dependency notation") },
                        placeholder = { Text("\"androidx.core:core-ktx:1.13.1\" or libs.androidx.core.ktx") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uiState.dependencyConfiguration,
                            onValueChange = viewModel::updateConfiguration,
                            modifier = Modifier.weight(1f),
                            label = { Text("Configuration") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = uiState.repositoryUrl,
                            onValueChange = viewModel::updateRepositoryUrl,
                            modifier = Modifier.weight(1f),
                            label = { Text("Repository URL (optional)") },
                            singleLine = true
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::addDependency) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Add dependency")
                        }
                        OutlinedButton(onClick = viewModel::addRepository) {
                            Icon(Icons.Filled.Link, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Add repository")
                        }
                        OutlinedButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                        }
                    }
                }
            }
        }

        if (uiState.problems.isNotEmpty()) {
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Dependency safeguards", style = MaterialTheme.typography.titleMedium)
                        uiState.problems.forEach { problem ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(problem.title, style = MaterialTheme.typography.bodyMedium)
                                Text(problem.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        if (uiState.dependencies.isEmpty() && uiState.repositories.isEmpty()) {
            item {
                NvEmptyState(
                    icon = Icons.Filled.Link,
                    title = "No dependencies indexed",
                    subtitle = "Use the controls above to add custom coordinates or refresh the project’s Gradle model."
                )
            }
        }

        if (uiState.dependencies.isNotEmpty()) {
            item { Text("Dependencies", style = MaterialTheme.typography.titleMedium) }
            items(uiState.dependencies, key = { it.id }) { entry ->
                Card {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.configuration, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(8.dp))
                            AssistChip(onClick = { viewModel.toggleDependency(entry) }, label = { Text(if (entry.enabled) "Enabled" else "Disabled") })
                        }
                        Text(entry.notation, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${entry.sourceFile}:${entry.lineNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.toggleDependency(entry) }) {
                                Icon(if (entry.enabled) Icons.Filled.ToggleOff else Icons.Filled.ToggleOn, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text(if (entry.enabled) "Disable" else "Enable")
                            }
                            TextButton(onClick = { viewModel.deleteDependency(entry) }) {
                                Icon(Icons.Filled.Delete, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }

        if (uiState.repositories.isNotEmpty()) {
            item { Text("Repositories", style = MaterialTheme.typography.titleMedium) }
            items(uiState.repositories, key = { it.id }) { entry ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.declaration, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            AssistChip(onClick = { viewModel.toggleRepository(entry) }, label = { Text(if (entry.enabled) "Enabled" else "Disabled") })
                        }
                        Text("${entry.sourceFile}:${entry.lineNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.toggleRepository(entry) }) {
                                Icon(if (entry.enabled) Icons.Filled.ToggleOff else Icons.Filled.ToggleOn, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text(if (entry.enabled) "Disable" else "Enable")
                            }
                            TextButton(onClick = { viewModel.deleteRepository(entry) }) {
                                Icon(Icons.Filled.Delete, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}
