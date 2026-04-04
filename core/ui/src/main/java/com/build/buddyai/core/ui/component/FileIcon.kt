package com.build.buddyai.core.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.build.buddyai.core.designsystem.theme.NeoVedicColors
import com.build.buddyai.core.model.FileType

@Composable
fun FileIcon(
    extension: String,
    isDirectory: Boolean,
    modifier: Modifier = Modifier
) {
    if (isDirectory) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = "Folder",
            modifier = modifier,
            tint = NeoVedicColors.Secondary60
        )
        return
    }
    val (icon, tint) = when (FileType.fromExtension(extension)) {
        FileType.KOTLIN -> Icons.Default.Code to NeoVedicColors.Primary60
        FileType.JAVA -> Icons.Default.Code to NeoVedicColors.Secondary60
        FileType.XML -> Icons.Default.Code to NeoVedicColors.Tertiary60
        FileType.GRADLE, FileType.GRADLE_KTS -> Icons.Default.Build to NeoVedicColors.Tertiary60
        FileType.JSON -> Icons.Default.DataObject to NeoVedicColors.Secondary60
        FileType.MARKDOWN -> Icons.Default.Description to MaterialTheme.colorScheme.onSurfaceVariant
        FileType.PROPERTIES -> Icons.Default.Settings to MaterialTheme.colorScheme.onSurfaceVariant
        FileType.YAML -> Icons.Default.SettingsEthernet to MaterialTheme.colorScheme.onSurfaceVariant
        FileType.TEXT -> Icons.Default.TextSnippet to MaterialTheme.colorScheme.onSurfaceVariant
        FileType.UNKNOWN -> Icons.Default.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(
        imageVector = icon,
        contentDescription = extension,
        modifier = modifier,
        tint = tint
    )
}
