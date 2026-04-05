package com.build.buddyai.feature.dependencies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.core.dependency.ProjectDependencyManager
import com.build.buddyai.core.designsystem.component.NvBackButton
import com.build.buddyai.core.designsystem.component.NvTopBar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DependenciesScreen(
    projectId: String,
    onBack: () -> Unit,
    viewModel: DependenciesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(projectId) { viewModel.initialize(projectId) }

    Scaffold(
        topBar = {
            NvTopBar(
                title = "Dependencies",
                subtitle = uiState.packageName.ifBlank { uiState.projectName },
                navigationIcon = { NvBackButton(onBack) },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh dependencies")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Dependency manager", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Manage Gradle dependencies and repositories per project. Add custom coordinates, disable entries without deleting them, and review dependency risks before touching build files.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        uiState.message?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        uiState.error?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Add dependency", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = uiState.dependencyNotation,
                            onValueChange = viewModel::updateDependencyNotation,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Dependency notation") },
                            placeholder = { Text("\"com.squareup.retrofit2:retrofit:2.11.0\" or libs.androidx.core.ktx") },
                            singleLine = true
                        )
                        Text(
                            "Wrap Maven coordinates in quotes for Kotlin DSL. Version-catalog aliases such as libs.androidx.core.ktx also work.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("implementation", "api", "compileOnly", "runtimeOnly", "testImplementation", "androidTestImplementation", "ksp", "kapt").forEach { configuration ->
                                FilterChip(
                                    selected = uiState.configuration == configuration,
                                    onClick = { viewModel.updateConfiguration(configuration) },
                                    label = { Text(configuration) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = uiState.repositoryUrl,
                            onValueChange = viewModel::updateRepositoryUrl,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Repository URL (optional)") },
                            placeholder = { Text("https://jitpack.io") },
                            singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = viewModel::addDependency, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Add dependency")
                            }
                            OutlinedButton(onClick = viewModel::addRepository, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Link, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Add repository")
                            }
                        }
                    }
                }
            }

            if (uiState.warnings.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Dependency risks", style = MaterialTheme.typography.titleMedium)
                            uiState.warnings.forEach { warning ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = when (warning.severity) {
                                            ProjectDependencyManager.Severity.ERROR -> MaterialTheme.colorScheme.error
                                            ProjectDependencyManager.Severity.WARNING -> MaterialTheme.colorScheme.tertiary
                                            ProjectDependencyManager.Severity.INFO -> MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    Column {
                                        Text(warning.title, style = MaterialTheme.typography.bodyMedium)
                                        Text(warning.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("Dependencies", style = MaterialTheme.typography.titleMedium)
            }

            if (uiState.dependencies.isEmpty()) {
                item {
                    Text("No parsed dependencies yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(uiState.dependencies, key = { it.id }) { dependency ->
                    Card {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(dependency.configuration, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        dependency.notation,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(dependency.sourceFile, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = dependency.enabled,
                                    onCheckedChange = { viewModel.toggleDependency(dependency, it) }
                                )
                                IconButton(onClick = { viewModel.deleteDependency(dependency) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete dependency")
                                }
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Repositories", style = MaterialTheme.typography.titleMedium)
            }

            if (uiState.repositories.isEmpty()) {
                item {
                    Text("No parsed repositories yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(uiState.repositories, key = { it.id }) { repository ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    repository.declaration,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(repository.sourceFile, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = repository.enabled,
                                onCheckedChange = { viewModel.toggleRepository(repository, it) }
                            )
                            IconButton(onClick = { viewModel.deleteRepository(repository) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete repository")
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
