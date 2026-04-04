package com.build.buddyai.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.build.buddyai.core.designsystem.BuildBuddyCard
import com.build.buddyai.core.designsystem.CodeFont
import com.build.buddyai.core.designsystem.NeoVedicTheme
import com.build.buddyai.core.model.AppPreferences
import com.build.buddyai.core.model.EditorSession

@Composable
fun CodeEditorPane(
    session: EditorSession,
    preferences: AppPreferences,
    onContentChange: (String) -> Unit,
    onSearchReplaceChange: (String, String) -> Unit,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReplaceAll: () -> Unit,
) {
    var text by remember(session.path, session.content) { mutableStateOf(session.content) }
    val spacing = NeoVedicTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = session.searchQuery,
                onValueChange = { onSearchReplaceChange(it, session.replaceQuery) },
                label = { Text("Find") },
            )
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = session.replaceQuery,
                onValueChange = { onSearchReplaceChange(session.searchQuery, it) },
                label = { Text("Replace") },
            )
            Button(onClick = onUndo) { Text("Undo") }
            Button(onClick = onRedo) { Text("Redo") }
            Button(onClick = onReplaceAll) { Text("Replace all") }
            Button(onClick = onSave) { Text(if (session.isDirty) "Save" else "Saved") }
        }
        BuildBuddyCard(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .heightIn(min = 360.dp),
            ) {
                val lineCount = text.lines().size.coerceAtLeast(1)
                Column(
                    modifier = Modifier
                        .padding(end = spacing.sm)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                ) {
                    repeat(lineCount) { index ->
                        Text(
                            text = (index + 1).toString(),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = CodeFont),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    BasicTextField(
                        modifier = Modifier.fillMaxSize(),
                        value = text,
                        onValueChange = {
                            text = it
                            onContentChange(it)
                        },
                        textStyle = TextStyle(
                            fontFamily = CodeFont,
                            fontSize = preferences.editorFontScaleSp.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = (preferences.editorFontScaleSp + 6).sp,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = remember(session.languageHint, session.searchQuery) {
                            SyntaxHighlightTransformation(
                                extension = session.languageHint,
                                searchQuery = session.searchQuery,
                            )
                        },
                    )
                }
            }
        }
    }
}

private class SyntaxHighlightTransformation(
    private val extension: String,
    private val searchQuery: String,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text)
        val keywords = when (extension.lowercase()) {
            "kt", "kts" -> listOf("fun", "val", "var", "class", "object", "if", "else", "when", "return", "import", "package")
            "java" -> listOf("public", "class", "void", "private", "protected", "return", "import", "package", "new")
            "xml" -> listOf("android:", "<", "</", "/>")
            "json" -> listOf("{", "}", "[", "]", ":")
            "md" -> listOf("#", "##", "###")
            else -> emptyList()
        }
        keywords.forEach { keyword ->
            Regex(Regex.escape(keyword)).findAll(text.text).forEach { match ->
                builder.addStyle(
                    SpanStyle(color = androidx.compose.ui.graphics.Color(0xFFB07A2C)),
                    match.range.first,
                    match.range.last + 1,
                )
            }
        }
        Regex("\"[^\"]*\"").findAll(text.text).forEach { match ->
            builder.addStyle(
                SpanStyle(color = androidx.compose.ui.graphics.Color(0xFF5B8C5A)),
                match.range.first,
                match.range.last + 1,
            )
        }
        Regex("//.*|/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL).findAll(text.text).forEach { match ->
            builder.addStyle(
                SpanStyle(color = androidx.compose.ui.graphics.Color(0xFF7D8A97)),
                match.range.first,
                match.range.last + 1,
            )
        }
        if (searchQuery.isNotBlank()) {
            Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE).findAll(text.text).forEach { match ->
                builder.addStyle(
                    SpanStyle(background = androidx.compose.ui.graphics.Color(0x44E0BE83)),
                    match.range.first,
                    match.range.last + 1,
                )
            }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
