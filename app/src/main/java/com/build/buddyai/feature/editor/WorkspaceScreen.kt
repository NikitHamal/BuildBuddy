package com.build.buddyai.feature.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.R
import com.build.buddyai.core.designsystem.component.*
import com.build.buddyai.core.designsystem.theme.*
import com.build.buddyai.core.model.FileNode
import com.build.buddyai.core.model.FileType

@Composable
fun WorkspaceScreen(
    projectId: String,
    viewModel: WorkspaceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    var showFileTree by remember { mutableStateOf(true) }

    LaunchedEffect(projectId) { viewModel.initialize(projectId) }

    Row(modifier = Modifier.fillMaxSize()) {
        // File Tree Sidebar
        AnimatedVisibility(
            visible = showFileTree,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut()
        ) {
            Surface(
                modifier = Modifier.width(240.dp).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = NvElevation.Sm
            ) {
                FileTreePanel(
                    uiState = uiState,
                    onCreateFile = { showCreateFileDialog = true },
                    onCreateFolder = { showCreateFolderDialog = true },
                    onRefresh = { viewModel.refreshFiles() },
                    onToggleExpand = { viewModel.toggleExpand(it) },
                    onOpenFile = { path -> viewModel.openFile(path) },
                    onDelete = { showDeleteDialog = it },
                    onCollapse = { showFileTree = false }
                )
            }
        }

        // Editor Area
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // Top toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = NvSpacing.Xs, vertical = NvSpacing.Xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!showFileTree) {
                    IconButton(onClick = { showFileTree = true }) {
                        Icon(Icons.Filled.Folder, contentDescription = "Show File Tree", modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = { viewModel.undo() }, enabled = uiState.undoStack.isNotEmpty()) {
                    Icon(Icons.Filled.Undo, contentDescription = stringResource(R.string.editor_undo), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { viewModel.redo() }, enabled = uiState.redoStack.isNotEmpty()) {
                    Icon(Icons.Filled.Redo, contentDescription = stringResource(R.string.editor_redo), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                if (uiState.activeFile?.isModified == true) {
                    NvStatusChip(
                        label = stringResource(R.string.editor_modified),
                        containerColor = BuildBuddyThemeExtended.colors.warningContainer,
                        contentColor = BuildBuddyThemeExtended.colors.warning
                    )
                    Spacer(Modifier.width(NvSpacing.Xs))
                    IconButton(onClick = { viewModel.saveFile() }) {
                        Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.editor_save), modifier = Modifier.size(18.dp))
                    }
                }
            }

            // File tabs
            if (uiState.openFiles.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = maxOf(0, uiState.activeFileIndex),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    edgePadding = NvSpacing.Xs,
                    divider = {}
                ) {
                    uiState.openFiles.forEachIndexed { index, file ->
                        Tab(
                            selected = index == uiState.activeFileIndex,
                            onClick = { viewModel.setActiveFile(index) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (file.isModified) {
                                        Surface(
                                            modifier = Modifier.size(6.dp),
                                            shape = MaterialTheme.shapes.extraLarge,
                                            color = BuildBuddyThemeExtended.colors.warning
                                        ) {}
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(file.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(onClick = { viewModel.closeFile(index) }, modifier = Modifier.size(16.dp)) {
                                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Editor content
            if (uiState.activeFile == null) {
                NvEmptyState(
                    icon = Icons.Filled.Code,
                    title = stringResource(R.string.editor_no_file),
                    subtitle = "Select a file from the tree to start editing",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val activeFile = uiState.activeFile!!
                val extendedColors = BuildBuddyThemeExtended.colors
                val scrollState = rememberScrollState()

                Row(modifier = Modifier.fillMaxSize()) {
                    // Line numbers gutter
                    if (uiState.showLineNumbers) {
                        val lineCount = activeFile.content.count { it == '\n' } + 1
                        Column(
                            modifier = Modifier
                                .width(48.dp)
                                .fillMaxHeight()
                                .background(extendedColors.editorGutter)
                                .verticalScroll(scrollState)
                                .padding(end = NvSpacing.Xs, top = NvSpacing.Xs),
                            horizontalAlignment = Alignment.End
                        ) {
                            for (i in 1..lineCount) {
                                Text(
                                    text = i.toString(),
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = uiState.fontSize.sp,
                                        lineHeight = (uiState.fontSize + 6).sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }

                    // Code editor
                    var textFieldValue by remember(activeFile.path, activeFile.content) {
                        mutableStateOf(TextFieldValue(activeFile.content))
                    }

                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            textFieldValue = newValue
                            if (newValue.text != activeFile.content) {
                                viewModel.updateFileContent(newValue.text)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(extendedColors.editorBackground)
                            .verticalScroll(scrollState)
                            .padding(NvSpacing.Xs),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = uiState.fontSize.sp,
                            lineHeight = (uiState.fontSize + 6).sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }

    // Dialogs
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
private fun FileTreePanel(
    uiState: WorkspaceUiState,
    onCreateFile: () -> Unit,
    onCreateFolder: () -> Unit,
    onRefresh: () -> Unit,
    onToggleExpand: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCollapse: () -> Unit
) {
    Column {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = NvSpacing.Xs, vertical = NvSpacing.Xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Files", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onCreateFile) {
                Icon(Icons.Filled.NoteAdd, contentDescription = "New File", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onCreateFolder) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = "New Folder", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onCollapse) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Collapse", modifier = Modifier.size(16.dp))
            }
        }
        HorizontalDivider()

        // File tree
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else if (uiState.fileTree == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                uiState.fileTree?.children?.let { children ->
                    items(children, key = { it.path }) { node ->
                        FileTreeNode(
                            node = node,
                            depth = 0,
                            expandedPaths = uiState.expandedPaths,
                            onToggleExpand = onToggleExpand,
                            onOpenFile = onOpenFile,
                            onDelete = onDelete
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileTreeNode(
    node: FileNode,
    depth: Int,
    expandedPaths: Set<String>,
    onToggleExpand: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val isExpanded = expandedPaths.contains(node.path)
    var showMenu by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (node.isDirectory) onToggleExpand(node.path)
                    else onOpenFile(node.path)
                }
                .padding(
                    start = (depth * 12 + 8).dp,
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
                    modifier = Modifier.size(16.dp),
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
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(NvSpacing.Xxs))
            Text(
                text = node.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null, modifier = Modifier.size(12.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete(node.path) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }

        if (node.isDirectory && isExpanded) {
            node.children.forEach { child ->
                FileTreeNode(
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
