package com.build.buddyai.core.agent

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class TextDiffEngine @Inject constructor() {
    companion object {
        private const val DP_CELL_LIMIT = 140_000L
        private const val PREVIEW_CELL_LIMIT = 1_000_000L
    }

    data class DiffLine(
        val type: Type,
        val text: String,
        val oldLineNumber: Int? = null,
        val newLineNumber: Int? = null
    ) {
        enum class Type { CONTEXT, ADDED, REMOVED }
    }

    data class DiffHunk(
        val id: String,
        val header: String,
        val lines: List<DiffLine>
    )

    fun createHunks(oldText: String, newText: String, contextLines: Int = 2): List<DiffHunk> {
        val oldLines = oldText.split('\n')
        val newLines = newText.split('\n')
        val cellCount = oldLines.size.toLong() * newLines.size.toLong()
        if (cellCount > PREVIEW_CELL_LIMIT) {
            return listOf(buildPreviewHunk(oldLines, newLines))
        }

        val ops = if (cellCount > DP_CELL_LIMIT) {
            buildGreedyDiff(oldLines, newLines)
        } else {
            buildDynamicProgrammingDiff(oldLines, newLines)
        }

        val changedIndexes = ops.mapIndexedNotNull { index, line -> index.takeIf { line.type != DiffLine.Type.CONTEXT } }
        if (changedIndexes.isEmpty()) return emptyList()

        val ranges = mutableListOf<IntRange>()
        changedIndexes.forEach { index ->
            val start = max(0, index - contextLines)
            val end = min(ops.lastIndex, index + contextLines)
            if (ranges.isEmpty() || start > ranges.last().last + 1) {
                ranges += start..end
            } else {
                ranges[ranges.lastIndex] = ranges.last().first..max(ranges.last().last, end)
            }
        }

        return ranges.mapIndexed { hunkIndex, range ->
            val lines = ops.subList(range.first, range.last + 1)
            val oldStart = lines.firstNotNullOfOrNull { it.oldLineNumber } ?: 0
            val newStart = lines.firstNotNullOfOrNull { it.newLineNumber } ?: 0
            val oldCount = lines.count { it.type != DiffLine.Type.ADDED }.coerceAtLeast(1)
            val newCount = lines.count { it.type != DiffLine.Type.REMOVED }.coerceAtLeast(1)
            DiffHunk(
                id = "hunk-$hunkIndex-${oldStart}-${newStart}",
                header = "@@ -$oldStart,$oldCount +$newStart,$newCount @@",
                lines = lines
            )
        }
    }

    private fun buildPreviewHunk(oldLines: List<String>, newLines: List<String>): DiffHunk {
        return DiffHunk(
            id = "fallback-0",
            header = "Large change preview",
            lines = buildList {
                oldLines.take(12).forEachIndexed { index, line ->
                    add(DiffLine(DiffLine.Type.REMOVED, line, oldLineNumber = index + 1))
                }
                newLines.take(12).forEachIndexed { index, line ->
                    add(DiffLine(DiffLine.Type.ADDED, line, newLineNumber = index + 1))
                }
            }
        )
    }

    private fun buildDynamicProgrammingDiff(oldLines: List<String>, newLines: List<String>): MutableList<DiffLine> {
        val dp = Array(oldLines.size + 1) { IntArray(newLines.size + 1) }
        for (i in oldLines.indices.reversed()) {
            for (j in newLines.indices.reversed()) {
                dp[i][j] = if (oldLines[i] == newLines[j]) dp[i + 1][j + 1] + 1 else max(dp[i + 1][j], dp[i][j + 1])
            }
        }
        return buildDiffFromTraversal(oldLines, newLines) { i, j -> dp[i + 1][j] >= dp[i][j + 1] }
    }

    private fun buildGreedyDiff(oldLines: List<String>, newLines: List<String>): MutableList<DiffLine> {
        return buildDiffFromTraversal(oldLines, newLines) { i, j ->
            val removeLooksBetter = oldLines.getOrNull(i + 1) == newLines[j]
            val addLooksBetter = oldLines[i] == newLines.getOrNull(j + 1)
            when {
                removeLooksBetter && !addLooksBetter -> true
                !removeLooksBetter && addLooksBetter -> false
                else -> true
            }
        }
    }

    private fun buildDiffFromTraversal(
        oldLines: List<String>,
        newLines: List<String>,
        shouldRemove: (oldIndex: Int, newIndex: Int) -> Boolean
    ): MutableList<DiffLine> {
        val ops = mutableListOf<DiffLine>()
        var i = 0
        var j = 0
        var oldLine = 1
        var newLine = 1
        while (i < oldLines.size && j < newLines.size) {
            when {
                oldLines[i] == newLines[j] -> {
                    ops += DiffLine(DiffLine.Type.CONTEXT, oldLines[i], oldLineNumber = oldLine, newLineNumber = newLine)
                    i++; j++; oldLine++; newLine++
                }
                shouldRemove(i, j) -> {
                    ops += DiffLine(DiffLine.Type.REMOVED, oldLines[i], oldLineNumber = oldLine)
                    i++; oldLine++
                }
                else -> {
                    ops += DiffLine(DiffLine.Type.ADDED, newLines[j], newLineNumber = newLine)
                    j++; newLine++
                }
            }
        }
        while (i < oldLines.size) {
            ops += DiffLine(DiffLine.Type.REMOVED, oldLines[i], oldLineNumber = oldLine)
            i++; oldLine++
        }
        while (j < newLines.size) {
            ops += DiffLine(DiffLine.Type.ADDED, newLines[j], newLineNumber = newLine)
            j++; newLine++
        }
        return ops
    }
}
