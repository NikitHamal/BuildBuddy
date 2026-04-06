package com.build.buddyai.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.build.buddyai.core.designsystem.theme.*

enum class NvChipVariant {
    PRIMARY, SECONDARY, TERTIARY, ERROR, SUCCESS, WARNING, INFO
}

@Composable
fun NvChip(
    label: String,
    modifier: Modifier = Modifier,
    variant: NvChipVariant = NvChipVariant.PRIMARY,
    icon: ImageVector? = null
) {
    val containerColor = when (variant) {
        NvChipVariant.PRIMARY -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        NvChipVariant.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        NvChipVariant.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        NvChipVariant.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        NvChipVariant.SUCCESS -> BuildBuddyThemeExtended.colors.successContainer.copy(alpha = 0.5f)
        NvChipVariant.WARNING -> BuildBuddyThemeExtended.colors.warningContainer.copy(alpha = 0.5f)
        NvChipVariant.INFO -> BuildBuddyThemeExtended.colors.infoContainer.copy(alpha = 0.5f)
    }
    
    val contentColor = when (variant) {
        NvChipVariant.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
        NvChipVariant.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
        NvChipVariant.TERTIARY -> MaterialTheme.colorScheme.onTertiaryContainer
        NvChipVariant.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        NvChipVariant.SUCCESS -> BuildBuddyThemeExtended.colors.success
        NvChipVariant.WARNING -> BuildBuddyThemeExtended.colors.warning
        NvChipVariant.INFO -> BuildBuddyThemeExtended.colors.info
    }

    Surface(
        modifier = modifier,
        shape = NvShapes.extraSmall,
        color = containerColor,
        border = BorderStroke(NvBorder.Hairline, contentColor.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = contentColor
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor
            )
        }
    }
}

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
        shape = NvShapes.small,
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            borderWidth = NvBorder.Hairline,
            selectedBorderWidth = NvBorder.Thin
        )
    )
}

@Composable
fun NvStatusChip(
    label: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    icon: ImageVector? = null
) {
    Surface(
        modifier = modifier,
        shape = NvShapes.extraSmall,
        color = containerColor.copy(alpha = 0.8f),
        border = BorderStroke(NvBorder.Hairline, contentColor.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    tint = contentColor
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontSize = 10.sp
            )
        }
    }
}
