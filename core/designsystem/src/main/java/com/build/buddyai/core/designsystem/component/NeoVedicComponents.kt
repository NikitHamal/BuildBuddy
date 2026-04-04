package com.build.buddyai.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.build.buddyai.core.designsystem.theme.*

@Composable
fun NeoVedicCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        OutlinedCard(
            onClick = onClick,
            modifier = modifier,
            shape = NeoVedicRadius.MD,
            border = BorderStroke(NeoVedicSpacing.HairlineBorder, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            content = content
        )
    } else {
        OutlinedCard(
            modifier = modifier,
            shape = NeoVedicRadius.MD,
            border = BorderStroke(NeoVedicSpacing.HairlineBorder, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            content = content
        )
    }
}

@Composable
fun NeoVedicSurface(
    modifier: Modifier = Modifier,
    tonalElevation: Int = 0,
    content: @Composable () -> Unit
) {
    val containerColor = when (tonalElevation) {
        0 -> MaterialTheme.colorScheme.surface
        1 -> MaterialTheme.colorScheme.surfaceContainerLow
        2 -> MaterialTheme.colorScheme.surfaceContainer
        3 -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = NeoVedicRadius.MD,
        content = content
    )
}

@Composable
fun NeoVedicButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = NeoVedicSpacing.TouchTarget),
        enabled = enabled,
        shape = NeoVedicRadius.SM,
        contentPadding = PaddingValues(horizontal = NeoVedicSpacing.LG, vertical = NeoVedicSpacing.SM)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(NeoVedicSpacing.IconSizeMD)
            )
            Spacer(Modifier.width(NeoVedicSpacing.SM))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun NeoVedicOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = NeoVedicSpacing.TouchTarget),
        enabled = enabled,
        shape = NeoVedicRadius.SM,
        border = BorderStroke(NeoVedicSpacing.HairlineBorder, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = NeoVedicSpacing.LG, vertical = NeoVedicSpacing.SM)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(NeoVedicSpacing.IconSizeMD)
            )
            Spacer(Modifier.width(NeoVedicSpacing.SM))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun NeoVedicTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = NeoVedicSpacing.TouchTarget),
        enabled = enabled,
        shape = NeoVedicRadius.SM,
        contentPadding = PaddingValues(horizontal = NeoVedicSpacing.MD, vertical = NeoVedicSpacing.SM)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(NeoVedicSpacing.IconSizeMD)
            )
            Spacer(Modifier.width(NeoVedicSpacing.SM))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun NeoVedicChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    containerColor: Color = Color.Unspecified
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            containerColor != Color.Unspecified -> containerColor
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(200),
        label = "chipBg"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "chipContent"
    )

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.height(32.dp),
            shape = NeoVedicRadius.Full,
            color = bgColor,
            contentColor = contentColor
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else {
        Surface(
            modifier = modifier.height(32.dp),
            shape = NeoVedicRadius.Full,
            color = bgColor,
            contentColor = contentColor
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StatusBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = NeoVedicRadius.XS,
        color = color.copy(alpha = 0.12f),
        contentColor = color
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NeoVedicSpacing.LG, vertical = NeoVedicSpacing.SM),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        action?.invoke()
    }
}

@Composable
fun NeoVedicDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = NeoVedicSpacing.HairlineBorder,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(NeoVedicSpacing.XXXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(NeoVedicSpacing.LG))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(NeoVedicSpacing.SM))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (action != null) {
            Spacer(Modifier.height(NeoVedicSpacing.XL))
            action()
        }
    }
}

@Composable
fun LoadingView(
    modifier: Modifier = Modifier,
    message: String? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        if (message != null) {
            Spacer(Modifier.height(NeoVedicSpacing.MD))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
