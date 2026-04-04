package com.build.buddyai.feature.editor

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.core.designsystem.component.*
import com.build.buddyai.core.designsystem.theme.BuildBuddyTheme
import com.build.buddyai.core.designsystem.theme.NeoVedicSpacing
import com.build.buddyai.core.model.FileType

@Composable
fun EditorScreen(
    projectPath: String,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab bar
        if (state.tabs.isNotEmpty()) {
            EditorTabBar(
                tabs = state.tabs,
                activeIndex = state.activeTabIndex,
                onTabClick = { viewModel.setActiveTab(it) },
                onTabClose = { viewModel.closeTab(it) }
            )
        }

        // Toolbar
        EditorToolbar(
            canUndo = state.undoStack.isNotEmpty(),
            canRedo = state.redoStack.isNotEmpty(),
            isModified = state.activeTab?.isModified == true,
            onUndo = { viewModel.undo() },
            onRedo = { viewModel.redo() },
            onSave = { viewModel.save(projectPath) },
            onSearch = { viewModel.toggleSearch() }
        )

        // Search bar
        if (state.showSearch) {
            SearchReplaceBar(
                searchQuery = state.searchQuery,
                replaceText = state.replaceText,
                resultCount = state.searchResults.size,
                currentIndex = state.currentSearchIndex,
                onSearchChange = { viewModel.setSearchQuery(it) },
                onReplaceChange = { viewModel.setReplaceText(it) },
                onReplaceNext = { viewModel.replaceNext(projectPath) },
                onReplaceAll = { viewModel.replaceAll(projectPath) },
                onClose = { viewModel.toggleSearch() }
            )
        }

        // Editor content
        val activeTab = state.activeTab
        if (activeTab != null) {
            CodeEditorContent(
                tab = activeTab,
                settings = state.settings,
                onContentChange = { viewModel.updateContent(it) }
            )
        } else {
            EmptyStateView(
                icon = Icons.Default.Code,
                title = "No File Open",
                description = "Open a file from the Files tab to start editing.",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun EditorTabBar(
    tabs: List<OpenTab>,
    activeIndex: Int,
    onTabClick: (Int) -> Unit,
    onTabClose: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(tabs) { index, tab ->
            Surface(
                onClick = { onTabClick(index) },
                color = if (index == activeIndex) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.height(36.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${tab.fileName}${if (tab.isModified) " •" else ""}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = if (index == activeIndex) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { onTabClose(index) },
                        modifier = Modifier.size(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    isModified: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = NeoVedicSpacing.SM, vertical = NeoVedicSpacing.XXS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NeoVedicSpacing.XXS)
    ) {
        IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Undo, "Undo", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Redo, "Redo", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onSave, enabled = isModified, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Save, "Save", modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearch, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Search, "Search", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SearchReplaceBar(
    searchQuery: String,
    replaceText: String,
    resultCount: Int,
    currentIndex: Int,
    onSearchChange: (String) -> Unit,
    onReplaceChange: (String) -> Unit,
    onReplaceNext: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(NeoVedicSpacing.SM)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.weight(1f).height(40.dp),
                placeholder = { Text("Find", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                shape = MaterialTheme.shapes.extraSmall
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (resultCount > 0) "${currentIndex + 1}/$resultCount" else "0",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(48.dp)
            )
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Close", modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = replaceText,
                onValueChange = onReplaceChange,
                modifier = Modifier.weight(1f).height(40.dp),
                placeholder = { Text("Replace", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                shape = MaterialTheme.shapes.extraSmall
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onReplaceNext, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.FindReplace, "Replace", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onReplaceAll, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.DoneAll, "Replace All", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CodeEditorContent(
    tab: OpenTab,
    settings: com.build.buddyai.core.model.EditorSettings,
    onContentChange: (String) -> Unit
) {
    val extColors = BuildBuddyTheme.extendedColors
    val syntaxColors = SyntaxColors(
        keyword = extColors.syntaxKeyword,
        string = extColors.syntaxString,
        number = extColors.syntaxNumber,
        comment = extColors.syntaxComment,
        function = extColors.syntaxFunction,
        type = extColors.syntaxType,
        annotation = extColors.syntaxAnnotation,
        operator = extColors.syntaxOperator,
        property = extColors.syntaxProperty,
        plain = MaterialTheme.colorScheme.onSurface
    )

    val highlighted = remember(tab.content, tab.fileType) {
        SyntaxHighlighter.highlight(tab.content, tab.fileType, syntaxColors)
    }

    var textFieldValue by remember(tab.filePath) {
        mutableStateOf(TextFieldValue(tab.content))
    }

    LaunchedEffect(tab.content) {
        if (textFieldValue.text != tab.content) {
            textFieldValue = TextFieldValue(tab.content)
        }
    }

    val lines = tab.content.lines()
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(extColors.codeBackground)
    ) {
        // Line numbers
        if (settings.showLineNumbers) {
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .background(extColors.codeGutter)
                    .verticalScroll(scrollState)
                    .padding(end = NeoVedicSpacing.SM, top = NeoVedicSpacing.SM)
            ) {
                lines.forEachIndexed { index, _ ->
                    Text(
                        text = "${index + 1}",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = settings.fontSize.sp,
                            color = extColors.syntaxComment
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 4.dp),
                        maxLines = 1
                    )
                }
            }
        }

        // Editor
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                if (newValue.text != tab.content) {
                    onContentChange(newValue.text)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .horizontalScroll(rememberScrollState())
                .padding(NeoVedicSpacing.SM),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = settings.fontSize.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                innerTextField()
            }
        )
    }
}
