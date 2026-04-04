package com.build.buddyai.feature.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.R
import com.build.buddyai.core.designsystem.component.*
import com.build.buddyai.core.designsystem.theme.*
import com.build.buddyai.core.model.FileType

@Composable
fun EditorTab(
    projectId: String,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(projectId) { viewModel.initialize(projectId) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = NvSpacing.Xs, vertical = NvSpacing.Xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.undo() }, enabled = uiState.undoStack.isNotEmpty()) {
                Icon(Icons.Filled.Undo, contentDescription = stringResource(R.string.editor_undo), modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { viewModel.redo() }, enabled = uiState.redoStack.isNotEmpty()) {
                Icon(Icons.Filled.Redo, contentDescription = stringResource(R.string.editor_redo), modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { viewModel.toggleSearch() }) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.editor_search), modifier = Modifier.size(18.dp))
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

        // Search bar
        AnimatedVisibility(visible = uiState.showSearch) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(NvSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    modifier = Modifier.weight(1f).height(40.dp),
                    placeholder = { Text(stringResource(R.string.editor_search_hint), style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = NvShapes.extraSmall
                )
                Spacer(Modifier.width(NvSpacing.Xxs))
                OutlinedTextField(
                    value = uiState.replaceQuery,
                    onValueChange = viewModel::updateReplaceQuery,
                    modifier = Modifier.weight(1f).height(40.dp),
                    placeholder = { Text(stringResource(R.string.editor_replace_hint), style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = NvShapes.extraSmall
                )
                IconButton(onClick = { viewModel.replaceAll() }) {
                    Icon(Icons.Filled.FindReplace, contentDescription = stringResource(R.string.editor_replace_all), modifier = Modifier.size(16.dp))
                }
                if (uiState.searchResults.isNotEmpty()) {
                    Text("${uiState.searchResults.size}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Editor content
        if (uiState.activeFile == null) {
            NvEmptyState(
                icon = Icons.Filled.Code,
                title = stringResource(R.string.editor_no_file),
                subtitle = stringResource(R.string.editor_open_file),
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
