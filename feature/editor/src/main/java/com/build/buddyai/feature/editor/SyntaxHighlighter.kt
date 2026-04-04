package com.build.buddyai.feature.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.build.buddyai.core.designsystem.theme.BuildBuddyTheme
import com.build.buddyai.core.model.FileType

data class SyntaxColors(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val function: Color,
    val type: Color,
    val annotation: Color,
    val operator: Color,
    val property: Color,
    val plain: Color
)

object SyntaxHighlighter {

    private val kotlinKeywords = setOf(
        "abstract", "actual", "annotation", "as", "break", "by", "catch", "class",
        "companion", "const", "constructor", "continue", "crossinline", "data",
        "delegate", "do", "else", "enum", "expect", "external", "false", "field",
        "final", "finally", "for", "fun", "get", "if", "import", "in", "infix",
        "init", "inline", "inner", "interface", "internal", "is", "lateinit",
        "noinline", "null", "object", "open", "operator", "out", "override",
        "package", "private", "protected", "public", "reified", "return", "sealed",
        "set", "super", "suspend", "this", "throw", "true", "try", "typealias",
        "val", "var", "vararg", "when", "where", "while"
    )

    private val javaKeywords = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "continue", "default", "do", "double", "else", "enum", "extends",
        "final", "finally", "float", "for", "if", "implements", "import",
        "instanceof", "int", "interface", "long", "native", "new", "null",
        "package", "private", "protected", "public", "return", "short", "static",
        "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "void", "volatile", "while", "true", "false"
    )

    private val xmlKeywords = setOf(
        "xmlns", "android", "app", "tools"
    )

    fun highlight(
        text: String,
        fileType: FileType,
        colors: SyntaxColors
    ): AnnotatedString = buildAnnotatedString {
        append(text)
        when (fileType) {
            FileType.KOTLIN -> highlightKotlin(text, colors)
            FileType.JAVA -> highlightJava(text, colors)
            FileType.XML -> highlightXml(text, colors)
            FileType.GRADLE_KTS, FileType.GRADLE -> highlightKotlin(text, colors)
            FileType.JSON -> highlightJson(text, colors)
            else -> { /* No highlighting */ }
        }.forEach { (range, style) ->
            if (range.first >= 0 && range.last < text.length && range.first <= range.last) {
                addStyle(style, range.first, range.last + 1)
            }
        }
    }

    private fun highlightKotlin(text: String, colors: SyntaxColors): List<Pair<IntRange, SpanStyle>> {
        val spans = mutableListOf<Pair<IntRange, SpanStyle>>()
        highlightComments(text, colors, spans)
        highlightStrings(text, colors, spans)
        highlightKeywords(text, kotlinKeywords, colors, spans)
        highlightAnnotations(text, colors, spans)
        highlightNumbers(text, colors, spans)
        return spans
    }

    private fun highlightJava(text: String, colors: SyntaxColors): List<Pair<IntRange, SpanStyle>> {
        val spans = mutableListOf<Pair<IntRange, SpanStyle>>()
        highlightComments(text, colors, spans)
        highlightStrings(text, colors, spans)
        highlightKeywords(text, javaKeywords, colors, spans)
        highlightAnnotations(text, colors, spans)
        highlightNumbers(text, colors, spans)
        return spans
    }

    private fun highlightXml(text: String, colors: SyntaxColors): List<Pair<IntRange, SpanStyle>> {
        val spans = mutableListOf<Pair<IntRange, SpanStyle>>()
        // XML comments
        Regex("<!--[\\s\\S]*?-->").findAll(text).forEach {
            spans.add(it.range to SpanStyle(color = colors.comment))
        }
        // XML tags
        Regex("</?[a-zA-Z][a-zA-Z0-9._:-]*").findAll(text).forEach {
            spans.add(it.range to SpanStyle(color = colors.keyword))
        }
        // Attribute values
        Regex("\"[^\"]*\"").findAll(text).forEach {
            spans.add(it.range to SpanStyle(color = colors.string))
        }
        // Attribute names
        Regex("\\b[a-zA-Z][a-zA-Z0-9_]*(?=\\s*=)").findAll(text).forEach {
            spans.add(it.range to SpanStyle(color = colors.property))
        }
        return spans
    }

    private fun highlightJson(text: String, colors: SyntaxColors): List<Pair<IntRange, SpanStyle>> {
        val spans = mutableListOf<Pair<IntRange, SpanStyle>>()
        Regex("\"[^\"\\\\]*(\\\\.[^\"\\\\]*)*\"(?=\\s*:)").findAll(text).forEach {
            spans.add(it.range to SpanStyle(color = colors.property))
        }
        Regex("(?<=:\\s*)\"[^\"\\\\]*(\\\\.[^\"\\\\]*)*\"").findAll(text).forEach {
            spans.add(it.range to SpanStyle(color = colors.string))
        }
        highlightNumbers(text, colors, spans)
        Regex("\\b(true|false|null)\\b").findAll(text).forEach {
            spans.add(it.range to SpanStyle(color = colors.keyword))
        }
        return spans
    }

    private fun highlightComments(
        text: String, colors: SyntaxColors,
        spans: MutableList<Pair<IntRange, SpanStyle>>
    ) {
        Regex("//[^\n]*").findAll(text).forEach {
            spans.add(it.range to SpanStyle(color = colors.comment))
        }
        Regex("/\\*[\\s\\S]*?\\*/").findAll(text).forEach {
            spans.add(it.range to SpanStyle(color = colors.comment))
        }
    }

    private fun highlightStrings(
        text: String, colors: SyntaxColors,
        spans: MutableList<Pair<IntRange, SpanStyle>>
    ) {
        Regex("\"\"\"[\\s\\S]*?\"\"\"").findAll(text).forEach {
            spans.add(it.range to SpanStyle(color = colors.string))
        }
        Regex("\"[^\"\\\\]*(\\\\.[^\"\\\\]*)*\"").findAll(text).forEach {
            spans.add(it.range to SpanStyle(color = colors.string))
        }
        Regex("'[^'\\\\]*(\\\\.[^'\\\\]*)*'").findAll(text).forEach {
            spans.add(it.range to SpanStyle(color = colors.string))
        }
    }

    private fun highlightKeywords(
        text: String, keywords: Set<String>, colors: SyntaxColors,
        spans: MutableList<Pair<IntRange, SpanStyle>>
    ) {
        Regex("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b").findAll(text).forEach { match ->
            if (match.value in keywords) {
                spans.add(match.range to SpanStyle(color = colors.keyword))
            }
        }
    }

    private fun highlightAnnotations(
        text: String, colors: SyntaxColors,
        spans: MutableList<Pair<IntRange, SpanStyle>>
    ) {
        Regex("@[a-zA-Z][a-zA-Z0-9_]*").findAll(text).forEach {
            spans.add(it.range to SpanStyle(color = colors.annotation))
        }
    }

    private fun highlightNumbers(
        text: String, colors: SyntaxColors,
        spans: MutableList<Pair<IntRange, SpanStyle>>
    ) {
        Regex("\\b\\d+(\\.\\d+)?[fFlLdD]?\\b").findAll(text).forEach {
            spans.add(it.range to SpanStyle(color = colors.number))
        }
    }
}
