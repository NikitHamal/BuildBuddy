package com.build.buddyai.core.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val NvLightColorScheme = lightColorScheme(
    primary = NvPrimary,
    onPrimary = NvOnPrimary,
    primaryContainer = NvPrimaryContainer,
    onPrimaryContainer = NvOnPrimaryContainer,
    inversePrimary = NvPrimaryDark,
    secondary = NvSecondary,
    onSecondary = NvOnSecondary,
    secondaryContainer = NvSecondaryContainer,
    onSecondaryContainer = NvOnSecondaryContainer,
    tertiary = NvTertiary,
    onTertiary = NvOnTertiary,
    tertiaryContainer = NvTertiaryContainer,
    onTertiaryContainer = NvOnTertiaryContainer,
    background = NvBackground,
    onBackground = NvOnBackground,
    surface = NvSurface,
    onSurface = NvOnSurface,
    surfaceVariant = NvSurfaceContainerHigh,
    onSurfaceVariant = NvOnSurfaceVariant,
    surfaceTint = NvPrimary,
    inverseSurface = NvInverseSurface,
    inverseOnSurface = NvInverseOnSurface,
    error = NvError,
    onError = NvOnError,
    errorContainer = NvErrorContainer,
    onErrorContainer = NvOnErrorContainer,
    outline = NvOutline,
    outlineVariant = NvOutlineVariant,
    scrim = NvScrim,
    surfaceBright = NvSurfaceBright,
    surfaceDim = NvSurfaceDim,
    surfaceContainer = NvSurfaceContainer,
    surfaceContainerHigh = NvSurfaceContainerHigh,
    surfaceContainerHighest = NvSurfaceContainerHighest,
    surfaceContainerLow = NvSurfaceContainerLow,
    surfaceContainerLowest = NvSurfaceContainerLowest
)

private val NvDarkColorScheme = darkColorScheme(
    primary = NvPrimaryDark,
    onPrimary = NvOnPrimaryDark,
    primaryContainer = NvPrimaryContainerDark,
    onPrimaryContainer = NvOnPrimaryContainerDark,
    inversePrimary = NvPrimary,
    secondary = NvSecondaryDark,
    onSecondary = NvOnSecondaryDark,
    secondaryContainer = NvSecondaryContainerDark,
    onSecondaryContainer = NvOnSecondaryContainerDark,
    tertiary = NvTertiaryDark,
    onTertiary = NvOnTertiaryDark,
    tertiaryContainer = NvTertiaryContainerDark,
    onTertiaryContainer = NvOnTertiaryContainerDark,
    background = NvBackgroundDark,
    onBackground = NvOnBackgroundDark,
    surface = NvSurfaceDark,
    onSurface = NvOnSurfaceDark,
    surfaceVariant = NvSurfaceContainerHighDark,
    onSurfaceVariant = NvOnSurfaceVariantDark,
    surfaceTint = NvPrimaryDark,
    inverseSurface = NvInverseSurfaceDark,
    inverseOnSurface = NvInverseOnSurfaceDark,
    error = NvErrorDark,
    onError = NvOnErrorDark,
    errorContainer = NvErrorContainerDark,
    onErrorContainer = NvOnErrorContainerDark,
    outline = NvOutlineDark,
    outlineVariant = NvOutlineVariantDark,
    scrim = NvScrim,
    surfaceBright = NvSurfaceBrightDark,
    surfaceDim = NvSurfaceDimDark,
    surfaceContainer = NvSurfaceContainerDark,
    surfaceContainerHigh = NvSurfaceContainerHighDark,
    surfaceContainerHighest = NvSurfaceContainerHighestDark,
    surfaceContainerLow = NvSurfaceContainerLowDark,
    surfaceContainerLowest = NvSurfaceContainerLowestDark
)

data class NvExtendedColors(
    val success: Color,
    val successContainer: Color,
    val onSuccess: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarning: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfo: Color,
    val editorBackground: Color,
    val editorLineHighlight: Color,
    val editorGutter: Color,
    val editorSelection: Color,
    val syntaxKeyword: Color,
    val syntaxString: Color,
    val syntaxComment: Color,
    val syntaxNumber: Color,
    val syntaxFunction: Color,
    val syntaxType: Color,
    val syntaxAnnotation: Color,
    val syntaxOperator: Color
)

val LocalNvExtendedColors = staticCompositionLocalOf {
    NvExtendedColors(
        success = NvSuccess, successContainer = NvSuccessContainer, onSuccess = NvOnSuccess,
        warning = NvWarning, warningContainer = NvWarningContainer, onWarning = NvOnWarning,
        info = NvInfo, infoContainer = NvInfoContainer, onInfo = NvOnInfo,
        editorBackground = NvEditorBackground, editorLineHighlight = NvEditorLineHighlight,
        editorGutter = NvEditorGutter, editorSelection = NvEditorSelection,
        syntaxKeyword = NvSyntaxKeyword, syntaxString = NvSyntaxString,
        syntaxComment = NvSyntaxComment, syntaxNumber = NvSyntaxNumber,
        syntaxFunction = NvSyntaxFunction, syntaxType = NvSyntaxType,
        syntaxAnnotation = NvSyntaxAnnotation, syntaxOperator = NvSyntaxOperator
    )
}

private val LightExtendedColors = NvExtendedColors(
    success = NvSuccess, successContainer = NvSuccessContainer, onSuccess = NvOnSuccess,
    warning = NvWarning, warningContainer = NvWarningContainer, onWarning = NvOnWarning,
    info = NvInfo, infoContainer = NvInfoContainer, onInfo = NvOnInfo,
    editorBackground = NvEditorBackground, editorLineHighlight = NvEditorLineHighlight,
    editorGutter = NvEditorGutter, editorSelection = NvEditorSelection,
    syntaxKeyword = NvSyntaxKeyword, syntaxString = NvSyntaxString,
    syntaxComment = NvSyntaxComment, syntaxNumber = NvSyntaxNumber,
    syntaxFunction = NvSyntaxFunction, syntaxType = NvSyntaxType,
    syntaxAnnotation = NvSyntaxAnnotation, syntaxOperator = NvSyntaxOperator
)

private val DarkExtendedColors = NvExtendedColors(
    success = NvSuccessDark, successContainer = NvSuccessContainerDark, onSuccess = NvOnSuccessDark,
    warning = NvWarningDark, warningContainer = NvWarningContainerDark, onWarning = NvOnWarningDark,
    info = NvInfoDark, infoContainer = NvInfoContainerDark, onInfo = NvOnInfoDark,
    editorBackground = NvEditorBackgroundDark, editorLineHighlight = NvEditorLineHighlightDark,
    editorGutter = NvEditorGutterDark, editorSelection = NvEditorSelectionDark,
    syntaxKeyword = NvSyntaxKeywordDark, syntaxString = NvSyntaxStringDark,
    syntaxComment = NvSyntaxCommentDark, syntaxNumber = NvSyntaxNumberDark,
    syntaxFunction = NvSyntaxFunctionDark, syntaxType = NvSyntaxTypeDark,
    syntaxAnnotation = NvSyntaxAnnotationDark, syntaxOperator = NvSyntaxOperatorDark
)

@Composable
fun BuildBuddyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NvDarkColorScheme else NvLightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalNvExtendedColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NvTypography,
            shapes = NvShapes,
            content = content
        )
    }
}

object BuildBuddyThemeExtended {
    val colors: NvExtendedColors
        @Composable
        get() = LocalNvExtendedColors.current
}
