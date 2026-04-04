package com.build.buddyai.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.build.buddyai.core.designsystem.theme.*

@Composable
fun NvCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        OutlinedCard(
            onClick = onClick,
            modifier = modifier,
            shape = NvShapes.medium,
            border = BorderStroke(NvBorder.Hairline, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            content = content
        )
    } else {
        OutlinedCard(
            modifier = modifier,
            shape = NvShapes.medium,
            border = BorderStroke(NvBorder.Hairline, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            content = content
        )
    }
}

@Composable
fun NvElevatedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        ElevatedCard(
            onClick = onClick,
            modifier = modifier,
            shape = NvShapes.medium,
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = NvElevation.Sm
            ),
            content = content
        )
    } else {
        ElevatedCard(
            modifier = modifier,
            shape = NvShapes.medium,
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = NvElevation.Sm
            ),
            content = content
        )
    }
}
