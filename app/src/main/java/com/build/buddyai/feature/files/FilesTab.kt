package com.build.buddyai.feature.files

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.R
import com.build.buddyai.core.designsystem.component.*
import com.build.buddyai.core.designsystem.theme.*
import com.build.buddyai.core.model.FileNode
import com.build.buddyai.core.model.FileType

@Composable
fun FilesTab(
    projectId: String,
    onOpenFile: (String) -> Unit,
    viewModel: FilesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(projectId) { viewModel.loadFiles(projectId) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = NvSpacing.Sm, vertical = NvSpacing.Xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.files_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { showCreateFileDialog = true }) {
                Icon(Icons.Filled.NoteAdd, contentDescription = stringResource(R.string.files_new_file), modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { showCreateFolderDialog = true }) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = stringResource(R.string.files_new_folder), modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { viewModel.refreshFiles() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
            }
        }

        if (uiState.isLoading) {
            NvLoadingIndicator(modifier = Modifier.fillMaxSize())
        } else if (uiState.fileTree == null) {
            NvEmptyState(
                icon = Icons.Filled.FolderOff,
                title = "No files",
                subtitle = "Create files to get started",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = NvSpacing.Xs)
            ) {
                uiState.fileTree?.children?.let { children ->
                    items(children, key = { it.path }) { node ->
                        FileTreeItem(
                            node = node,
                            depth = 0,
                            expandedPaths = uiState.expandedPaths,
                            onToggleExpand = { viewModel.toggleExpand(it) },
                            onOpenFile = onOpenFile,
                            onDelete = { showDeleteDialog = it }
                        )
                    }
                }
            }
        }
    }

    if (showCreateFileDialog) {
        CreateFileDialog(
            title = stringResource(R.string.files_create_title),
            placeholder = stringResource(R.string.files_name_hint),
            onConfirm = { name -> viewModel.createFile(name); showCreateFileDialog = false },
            onDismiss = { showCreateFileDialog = false }
        )
    }

    if (showCreateFolderDialog) {
        CreateFileDialog(
            title = stringResource(R.string.files_create_folder_title),
            placeholder = stringResource(R.string.files_folder_name_hint),
            onConfirm = { name -> viewModel.createFolder(name); showCreateFolderDialog = false },
            onDismiss = { showCreateFolderDialog = false }
        )
    }

    showDeleteDialog?.let { path ->
        NvAlertDialog(
            title = stringResource(R.string.files_delete),
            message = stringResource(R.string.files_delete_confirm, path.substringAfterLast("/")),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = { viewModel.deleteFile(path); showDeleteDialog = null },
            onDismiss = { showDeleteDialog = null },
            isDestructive = true
        )
    }
}

@Composable
private fun FileTreeItem(
    node: FileNode,
    depth: Int,
    expandedPaths: Set<String>,
    onToggleExpand: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val isExpanded = expandedPaths.contains(node.path)
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (node.isDirectory) onToggleExpand(node.path)
                else onOpenFile(node.path)
            }
            .padding(
                start = (depth * 16 + 8).dp,
                top = NvSpacing.Xxs,
                bottom = NvSpacing.Xxs,
                end = NvSpacing.Xs
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (node.isDirectory) {
            Icon(
                if (isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            val icon = when (node.fileType) {
                FileType.KOTLIN -> Icons.Filled.Code
                FileType.JAVA -> Icons.Filled.Code
                FileType.XML -> Icons.Filled.DataObject
                FileType.GRADLE, FileType.GRADLE_KTS -> Icons.Filled.Build
                FileType.JSON -> Icons.Filled.DataObject
                FileType.MARKDOWN -> Icons.Filled.Description
                else -> Icons.Filled.InsertDriveFile
            }
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(NvSpacing.Xs))
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.files_delete)) },
                    onClick = { showMenu = false; onDelete(node.path) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) }
                )
            }
        }
    }

    if (node.isDirectory && isExpanded) {
        node.children.forEach { child ->
            FileTreeItem(
                node = child,
                depth = depth + 1,
                expandedPaths = expandedPaths,
                onToggleExpand = onToggleExpand,
                onOpenFile = onOpenFile,
                onDelete = onDelete
            )
        }
    }
}

@Composable
private fun CreateFileDialog(
    title: String,
    placeholder: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            NvTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = placeholder,
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
