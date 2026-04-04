package com.build.buddyai.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.build.buddyai.core.designsystem.theme.*

@Composable
fun NvFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        modifier = modifier,
        enabled = enabled,
        leadingIcon = if (icon != null && selected) {
            { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else null,
        shape = NvShapes.small
    )
}

@Composable
fun NvStatusChip(
    label: String,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSecondaryContainer,
    icon: ImageVector? = null
) {
    SuggestionChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor) },
        modifier = modifier,
        icon = if (icon != null) {
            { Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = contentColor) }
        } else null,
        shape = NvShapes.extraSmall,
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = containerColor,
            labelColor = contentColor
        )
    )
}
