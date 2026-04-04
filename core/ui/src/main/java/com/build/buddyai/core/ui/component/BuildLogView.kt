package com.build.buddyai.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.build.buddyai.core.designsystem.theme.BuildBuddyTheme
import com.build.buddyai.core.designsystem.theme.NeoVedicSpacing
import com.build.buddyai.core.model.BuildLogEntry
import com.build.buddyai.core.model.LogLevel

@Composable
fun BuildLogView(
    entries: List<BuildLogEntry>,
    modifier: Modifier = Modifier,
    autoScroll: Boolean = true
) {
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (autoScroll && entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .background(BuildBuddyTheme.extendedColors.codeBackground)
            .padding(NeoVedicSpacing.SM),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        items(entries, key = { "${it.timestamp}-${it.message.hashCode()}" }) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 1.dp)
            ) {
                Text(
                    text = formatTimestamp(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = BuildBuddyTheme.extendedColors.syntaxComment,
                    modifier = Modifier.width(80.dp)
                )
                Text(
                    text = entry.level.name.first().toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = when (entry.level) {
                        LogLevel.ERROR -> BuildBuddyTheme.extendedColors.statusError
                        LogLevel.WARNING -> BuildBuddyTheme.extendedColors.statusWarning
                        LogLevel.INFO -> BuildBuddyTheme.extendedColors.statusInfo
                        LogLevel.DEBUG -> BuildBuddyTheme.extendedColors.syntaxComment
                    },
                    modifier = Modifier.width(16.dp)
                )
                Text(
                    text = entry.message,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    return String.format(
        "%02d:%02d:%02d",
        calendar.get(java.util.Calendar.HOUR_OF_DAY),
        calendar.get(java.util.Calendar.MINUTE),
        calendar.get(java.util.Calendar.SECOND)
    )
}
