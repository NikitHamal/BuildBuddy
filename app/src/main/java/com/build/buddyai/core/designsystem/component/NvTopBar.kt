package com.build.buddyai.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import com.build.buddyai.core.designsystem.theme.NvBorder

@Composable
fun NvTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    subtitle: String? = null
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    
    Surface(
        modifier = modifier.drawBehind {
            val strokeWidth = NvBorder.Hairline.toPx()
            val y = size.height - strokeWidth / 2
            drawLine(
                color = borderColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokeWidth
            )
        },
        color = MaterialTheme.colorScheme.surface
    ) {
        TopAppBar(
            title = {
                if (subtitle != null) {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = navigationIcon ?: {},
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}


@Composable
fun NvBackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back"
        )
    }
}
