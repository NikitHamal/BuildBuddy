package com.build.buddyai.feature.playground

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.R
import com.build.buddyai.core.designsystem.BuildBuddyCard
import com.build.buddyai.core.designsystem.NeoVedicTheme
import com.build.buddyai.core.designsystem.StatusBadge
import com.build.buddyai.core.model.AgentMode
import com.build.buddyai.core.model.Artifact
import com.build.buddyai.core.model.BuildMode
import com.build.buddyai.core.model.BuildStatus
import com.build.buddyai.core.model.ChatMessage
import com.build.buddyai.feature.editor.CodeEditorPane

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaygroundScreen(
    viewModel: PlaygroundViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = NeoVedicTheme.spacing
    val wide = LocalConfiguration.current.screenWidthDp >= 900

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text(
                            state.project?.name.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        state.project?.packageName?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.startBuild(BuildMode.DEBUG) }) {
                        Text(stringResource(R.string.action_build))
                    }
                },
            )
        },
        bottomBar = {
            if (!wide) {
                PlaygroundBottomBar(selected = state.selectedTab, onSelected = viewModel::selectTab)
            }
        },
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (wide) {
                PlaygroundRail(selected = state.selectedTab, onSelected = viewModel::selectTab)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
            ) {
                when (state.selectedTab) {
                    PlaygroundTab.OVERVIEW -> OverviewPanel(state = state, onRestoreSnapshot = viewModel::restoreSnapshot)
                    PlaygroundTab.AGENT -> AgentPanel(
                        state = state,
                        onSend = { prompt, mode -> viewModel.askAi(prompt, mode, state.openTabs) },
                        onStop = viewModel::cancelAgent,
                        onApplyChanges = viewModel::applyChanges,
                    )
                    PlaygroundTab.EDITOR -> EditorPanel(
                        state = state,
                        onContentChange = viewModel::updateEditorContent,
                        onSearchReplaceChange = viewModel::updateSearchReplace,
                        onSave = viewModel::saveFile,
                        onUndo = viewModel::undo,
                        onRedo = viewModel::redo,
                        onReplaceAll = viewModel::replaceAll,
                    )
                    PlaygroundTab.FILES -> FilesPanel(
                        state = state,
                        onOpenFile = viewModel::openFile,
                        onCreateFile = viewModel::createFile,
                        onCreateFolder = viewModel::createFolder,
                        onRename = viewModel::renamePath,
                        onDelete = viewModel::deletePath,
                    )
                    PlaygroundTab.BUILD -> BuildPanel(
                        state = state,
                        onBuild = viewModel::startBuild,
                        onAskFix = viewModel::askAiToFixLatestBuild,
                    )
                    PlaygroundTab.ARTIFACTS -> ArtifactsPanel(
                        state = state,
                        onInstall = viewModel::installArtifact,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaygroundRail(selected: PlaygroundTab, onSelected: (PlaygroundTab) -> Unit) {
    NavigationRail {
        tabSpecs().forEach { spec ->
            NavigationRailItem(
                selected = selected == spec.tab,
                onClick = { onSelected(spec.tab) },
                icon = { androidx.compose.material3.Icon(spec.icon, contentDescription = spec.label) },
                label = { Text(spec.label) },
            )
        }
    }
}

@Composable
private fun PlaygroundBottomBar(selected: PlaygroundTab, onSelected: (PlaygroundTab) -> Unit) {
    NavigationBar {
        tabSpecs().forEach { spec ->
            NavigationBarItem(
                selected = selected == spec.tab,
                onClick = { onSelected(spec.tab) },
                icon = { androidx.compose.material3.Icon(spec.icon, contentDescription = spec.label) },
                label = { Text(spec.label) },
            )
        }
    }
}

@Composable
private fun OverviewPanel(
    state: PlaygroundUiState,
    onRestoreSnapshot: (com.build.buddyai.core.model.Snapshot) -> Unit,
) {
    val spacing = NeoVedicTheme.spacing
    LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        item {
            BuildBuddyCard {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(stringResource(R.string.project_overview_title), style = MaterialTheme.typography.titleLarge)
                    Text(state.project?.description?.ifBlank { stringResource(R.string.project_overview_empty) } ?: stringResource(R.string.project_overview_empty))
                    state.compatibility?.let {
                        StatusBadge(it.level.name, MaterialTheme.colorScheme.primary)
                        Text(it.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (state.snapshots.isNotEmpty()) {
            item { Text("Snapshots", style = MaterialTheme.typography.titleMedium) }
            items(state.snapshots, key = { it.id }) { snapshot ->
                BuildBuddyCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(snapshot.label, style = MaterialTheme.typography.titleMedium)
                            Text(snapshot.reason, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = { onRestoreSnapshot(snapshot) }) {
                            Text(stringResource(R.string.action_restore_snapshot))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentPanel(
    state: PlaygroundUiState,
    onSend: (String, AgentMode) -> Unit,
    onStop: () -> Unit,
    onApplyChanges: (ChatMessage) -> Unit,
) {
    val spacing = NeoVedicTheme.spacing
    var prompt by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(AgentMode.CHAT) }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            AgentMode.entries.forEach { entry ->
                FilterChip(selected = mode == entry, onClick = { mode = entry }, label = { Text(entry.name) })
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            items(state.messages, key = { it.id }) { message ->
                BuildBuddyCard {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(message.role.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(message.content, style = MaterialTheme.typography.bodyMedium)
                        if (message.proposedChanges.isNotEmpty()) {
                            Text("Proposed changes", style = MaterialTheme.typography.titleMedium)
                            message.proposedChanges.forEach { change ->
                                Text("${change.operation.name} ${change.path}", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = { onApplyChanges(message) }) {
                                Text(stringResource(R.string.action_apply_patch))
                            }
                        }
                    }
                }
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text(stringResource(R.string.project_agent_hint)) },
            minLines = 3,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Button(
                onClick = {
                    onSend(prompt, mode)
                    prompt = ""
                },
                enabled = prompt.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_send))
            }
            TextButton(onClick = onStop) {
                Text(stringResource(R.string.action_stop))
            }
        }
    }
}

@Composable
private fun EditorPanel(
    state: PlaygroundUiState,
    onContentChange: (String) -> Unit,
    onSearchReplaceChange: (String, String) -> Unit,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReplaceAll: () -> Unit,
) {
    val session = state.editorSession
    if (session == null) {
        BuildBuddyCard {
            Text(stringResource(R.string.project_editor_empty))
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.openTabs.forEach { tab ->
                StatusBadge(
                    label = tab.substringAfterLast('/'),
                    color = if (tab == session.path) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                )
            }
        }
        CodeEditorPane(
            session = session,
            preferences = state.preferences,
            onContentChange = onContentChange,
            onSearchReplaceChange = onSearchReplaceChange,
            onSave = onSave,
            onUndo = onUndo,
            onRedo = onRedo,
            onReplaceAll = onReplaceAll,
        )
    }
}

@Composable
private fun FilesPanel(
    state: PlaygroundUiState,
    onOpenFile: (String) -> Unit,
    onCreateFile: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val spacing = NeoVedicTheme.spacing
    var createFilePath by remember { mutableStateOf("") }
    var createFolderPath by remember { mutableStateOf("") }
    var renameFrom by remember { mutableStateOf("") }
    var renameTo by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = createFilePath,
                onValueChange = { createFilePath = it },
                label = { Text("New file path") },
            )
            Button(onClick = { if (createFilePath.isNotBlank()) onCreateFile(createFilePath) }) {
                Text(stringResource(R.string.action_create))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = createFolderPath,
                onValueChange = { createFolderPath = it },
                label = { Text("New folder path") },
            )
            Button(onClick = { if (createFolderPath.isNotBlank()) onCreateFolder(createFolderPath) }) {
                Text("Folder")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedTextField(modifier = Modifier.weight(1f), value = renameFrom, onValueChange = { renameFrom = it }, label = { Text("Rename from") })
            OutlinedTextField(modifier = Modifier.weight(1f), value = renameTo, onValueChange = { renameTo = it }, label = { Text("Rename to") })
            Button(onClick = { if (renameFrom.isNotBlank() && renameTo.isNotBlank()) onRename(renameFrom, renameTo) }) {
                Text(stringResource(R.string.action_rename))
            }
        }
        if (state.files.isEmpty()) {
            BuildBuddyCard { Text(stringResource(R.string.project_files_empty)) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                items(state.files, key = { it.path }) { file ->
                    BuildBuddyCard(modifier = Modifier.clickable {
                        if (!file.isDirectory) onOpenFile(file.path)
                    }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("${"  ".repeat(file.depth)}${file.name}", style = MaterialTheme.typography.bodyMedium)
                            if (!file.isDirectory) {
                                TextButton(onClick = { onDelete(file.path) }) {
                                    Text(stringResource(R.string.action_delete))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildPanel(
    state: PlaygroundUiState,
    onBuild: (BuildMode) -> Unit,
    onAskFix: () -> Unit,
) {
    val spacing = NeoVedicTheme.spacing
    LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        item {
            BuildBuddyCard {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(stringResource(R.string.project_build_status), style = MaterialTheme.typography.titleLarge)
                    state.compatibility?.let {
                        StatusBadge(it.level.name, MaterialTheme.colorScheme.primary)
                        Text(it.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Button(onClick = { onBuild(BuildMode.DEBUG) }) { Text("Debug") }
                        Button(onClick = { onBuild(BuildMode.RELEASE) }) { Text("Release") }
                        Button(onClick = onAskFix, enabled = state.builds.isNotEmpty()) { Text(stringResource(R.string.action_ask_ai_fix)) }
                    }
                }
            }
        }
        items(state.builds, key = { it.id }) { build ->
            BuildBuddyCard {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    val color = when (build.status) {
                        BuildStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                        BuildStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.secondary
                    }
                    StatusBadge(build.status.name, color)
                    Text(build.summary, style = MaterialTheme.typography.titleMedium)
                    if (build.diagnostics.isNotEmpty()) {
                        build.diagnostics.forEach { diagnostic ->
                            Text("• ${diagnostic.title}: ${diagnostic.detail}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(build.rawLog.takeLast(1200), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ArtifactsPanel(
    state: PlaygroundUiState,
    onInstall: (Artifact) -> Unit,
) {
    val spacing = NeoVedicTheme.spacing
    val context = LocalContext.current
    if (state.artifacts.isEmpty()) {
        BuildBuddyCard { Text(stringResource(R.string.project_artifacts_empty)) }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        items(state.artifacts, key = { it.id }) { artifact ->
            BuildBuddyCard {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(artifact.filePath.substringAfterLast('/'), style = MaterialTheme.typography.titleMedium)
                    Text("${artifact.packageName} • ${artifact.fileSizeBytes} bytes", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Button(onClick = { onInstall(artifact) }) {
                            Text(stringResource(R.string.action_install))
                        }
                        Button(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/vnd.android.package-archive"
                                putExtra(Intent.EXTRA_TEXT, artifact.filePath)
                            }
                            context.startActivity(Intent.createChooser(intent, artifact.filePath.substringAfterLast('/')))
                        }) {
                            Text(stringResource(R.string.action_share))
                        }
                    }
                }
            }
        }
    }
}

private data class TabSpec(
    val tab: PlaygroundTab,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
private fun tabSpecs(): List<TabSpec> = listOf(
    TabSpec(PlaygroundTab.OVERVIEW, stringResource(R.string.nav_overview), Icons.Outlined.Home),
    TabSpec(PlaygroundTab.AGENT, stringResource(R.string.nav_agent), Icons.Outlined.AutoAwesome),
    TabSpec(PlaygroundTab.EDITOR, stringResource(R.string.nav_editor), Icons.Outlined.Code),
    TabSpec(PlaygroundTab.FILES, stringResource(R.string.nav_files), Icons.Outlined.Folder),
    TabSpec(PlaygroundTab.BUILD, stringResource(R.string.nav_build), Icons.Outlined.Build),
    TabSpec(PlaygroundTab.ARTIFACTS, stringResource(R.string.nav_artifacts), Icons.Outlined.Inventory2),
)
