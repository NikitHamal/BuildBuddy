package com.build.buddyai.feature.editor

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    projectId: String
) {
    val viewModel: WorkspaceViewModel = hiltViewModel()
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
                modifier = Modifier.width(260.dp).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(NvBorder.Hairline, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
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
            // Toolbar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(NvBorder.Hairline, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NvSpacing.Xs, vertical = NvSpacing.Xxs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!showFileTree) {
                        IconButton(onClick = { showFileTree = true }) {
                            Icon(Icons.Filled.MenuOpen, contentDescription = "Show Files", modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(onClick = { viewModel.undo() }, enabled = uiState.undoStack.isNotEmpty()) {
                        Icon(Icons.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { viewModel.redo() }, enabled = uiState.redoStack.isNotEmpty()) {
                        Icon(Icons.Filled.Redo, contentDescription = "Redo", modifier = Modifier.size(20.dp))
                    }
                    
                    Spacer(Modifier.weight(1f))
                    
                    if (uiState.activeFile?.isModified == true) {
                        NvChip(
                            label = "Modified",
                            variant = NvChipVariant.WARNING,
                            icon = Icons.Filled.Edit
                        )
                        Spacer(Modifier.width(NvSpacing.Sm))
                        IconButton(onClick = { viewModel.saveFile() }) {
                            Icon(Icons.Filled.Save, contentDescription = "Save", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Tabs
            if (uiState.openFiles.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = maxOf(0, uiState.activeFileIndex),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    edgePadding = 0.dp,
                    divider = {},
                    indicator = { tabPositions ->
                        if (uiState.activeFileIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[uiState.activeFileIndex]),
                                color = MaterialTheme.colorScheme.primary,
                                height = 2.dp
                            )
                        }
                    }
                ) {
                    uiState.openFiles.forEachIndexed { index, file ->
                        Tab(
                            selected = index == uiState.activeFileIndex,
                            onClick = { viewModel.setActiveFile(index) },
                            modifier = Modifier.height(40.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = NvSpacing.Sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (file.isModified) {
                                    Surface(
                                        modifier = Modifier.size(6.dp),
                                        shape = MaterialTheme.shapes.extraLarge,
                                        color = BuildBuddyThemeExtended.colors.warning
                                    ) {}
                                    Spacer(Modifier.width(NvSpacing.Xxs))
                                }
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    color = if (index == uiState.activeFileIndex) MaterialTheme.colorScheme.primary 
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(NvSpacing.Xxs))
                                IconButton(
                                    onClick = { viewModel.closeFile(index) },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Editor
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (uiState.activeFile == null) {
                    NvEmptyState(
                        icon = Icons.Filled.Code,
                        title = "No file open",
                        subtitle = "Select a file from the explorer to start coding",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val activeFile = uiState.activeFile!!
                    val extendedColors = BuildBuddyThemeExtended.colors
                    val scrollState = rememberScrollState()

                    Row(modifier = Modifier.fillMaxSize().background(extendedColors.editorBackground)) {
                        // Gutter
                        if (uiState.showLineNumbers) {
                            val lineCount = activeFile.content.count { it == '\n' } + 1
                            Column(
                                modifier = Modifier
                                    .width(44.dp)
                                    .fillMaxHeight()
                                    .background(extendedColors.editorGutter)
                                    .verticalScroll(scrollState)
                                    .padding(vertical = NvSpacing.Sm, horizontal = NvSpacing.Xxs),
                                horizontalAlignment = Alignment.End
                            ) {
                                for (i in 1..lineCount) {
                                    Text(
                                        text = i.toString(),
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = (uiState.fontSize - 2).sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                    )
                                }
                            }
                        }

                        // Text Field
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
                                .verticalScroll(scrollState)
                                .padding(NvSpacing.Sm),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = uiState.fontSize.sp,
                                lineHeight = (uiState.fontSize * 1.5).sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showCreateFileDialog) {
        CreateNameDialog(
            title = "New File",
            onConfirm = { name -> viewModel.createFile(name); showCreateFileDialog = false },
            onDismiss = { showCreateFileDialog = false }
        )
    }

    if (showCreateFolderDialog) {
        CreateNameDialog(
            title = "New Folder",
            onConfirm = { name -> viewModel.createFolder(name); showCreateFolderDialog = false },
            onDismiss = { showCreateFolderDialog = false }
        )
    }

    showDeleteDialog?.let { path ->
        NvAlertDialog(
            title = "Delete item",
            message = "Are you sure you want to delete ${path.substringAfterLast("/")}?",
            confirmText = "Delete",
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(NvSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("EXPLORER", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            IconButton(onClick = onCreateFile, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onCreateFolder, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onCollapse, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        
        HorizontalDivider(modifier = Modifier.alpha(0.3f))

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
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
                .padding(start = (depth * 12 + 12).dp, end = NvSpacing.Xs, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (node.isDirectory) {
                    if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight
                } else {
                    when (node.fileType) {
                        FileType.KOTLIN, FileType.JAVA -> Icons.Filled.Code
                        FileType.XML, FileType.JSON -> Icons.Filled.DataObject
                        FileType.MARKDOWN -> Icons.Filled.Description
                        else -> Icons.Filled.InsertDriveFile
                    }
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (node.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
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
                        text = { Text("Delete") },
                        onClick = { showMenu = false; onDelete(node.path) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error, leadingIconColor = MaterialTheme.colorScheme.error)
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
private fun CreateNameDialog(
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            NvTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Name",
                singleLine = true,
                modifier = Modifier.imePadding()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
