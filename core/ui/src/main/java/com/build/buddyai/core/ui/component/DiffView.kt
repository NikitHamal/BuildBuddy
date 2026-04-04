package com.build.buddyai.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.build.buddyai.core.designsystem.theme.BuildBuddyTheme
import com.build.buddyai.core.designsystem.theme.NeoVedicSpacing
import com.build.buddyai.core.model.CodeDiff
import com.build.buddyai.core.model.DiffHunk

@Composable
fun DiffView(
    diff: CodeDiff,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(BuildBuddyTheme.extendedColors.codeBackground)
            .padding(NeoVedicSpacing.SM)
    ) {
        Text(
            text = diff.filePath,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = NeoVedicSpacing.SM)
        )
        diff.hunks.forEach { hunk ->
            DiffHunkView(hunk)
            Spacer(Modifier.height(NeoVedicSpacing.XS))
        }
    }
}

@Composable
private fun DiffHunkView(hunk: DiffHunk) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.horizontalScroll(scrollState)) {
        hunk.oldContent.lines().forEach { line ->
            Row(modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x20FF5449))
                .padding(horizontal = NeoVedicSpacing.SM, vertical = 1.dp)
            ) {
                Text(
                    text = "- $line",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    color = BuildBuddyTheme.extendedColors.statusError
                )
            }
        }
        hunk.newContent.lines().forEach { line ->
            Row(modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x204CAF50))
                .padding(horizontal = NeoVedicSpacing.SM, vertical = 1.dp)
            ) {
                Text(
                    text = "+ $line",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    color = BuildBuddyTheme.extendedColors.statusSuccess
                )
            }
        }
    }
}
