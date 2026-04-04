package com.build.buddyai.core.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val NeoVedicDarkColorScheme = darkColorScheme(
    primary = NeoVedicColors.Primary70,
    onPrimary = NeoVedicColors.Primary10,
    primaryContainer = NeoVedicColors.Primary30,
    onPrimaryContainer = NeoVedicColors.Primary90,
    secondary = NeoVedicColors.Secondary70,
    onSecondary = NeoVedicColors.Secondary10,
    secondaryContainer = NeoVedicColors.Secondary30,
    onSecondaryContainer = NeoVedicColors.Secondary90,
    tertiary = NeoVedicColors.Tertiary70,
    onTertiary = NeoVedicColors.Tertiary10,
    tertiaryContainer = NeoVedicColors.Tertiary30,
    onTertiaryContainer = NeoVedicColors.Tertiary90,
    error = NeoVedicColors.Error70,
    onError = NeoVedicColors.Error10,
    errorContainer = NeoVedicColors.Error30,
    onErrorContainer = NeoVedicColors.Error90,
    background = NeoVedicColors.Neutral6,
    onBackground = NeoVedicColors.Neutral90,
    surface = NeoVedicColors.Neutral6,
    onSurface = NeoVedicColors.Neutral90,
    surfaceVariant = NeoVedicColors.Neutral17,
    onSurfaceVariant = NeoVedicColors.Neutral80,
    outline = NeoVedicColors.Neutral40,
    outlineVariant = NeoVedicColors.Neutral24,
    inverseSurface = NeoVedicColors.Neutral90,
    inverseOnSurface = NeoVedicColors.Neutral10,
    inversePrimary = NeoVedicColors.Primary40,
    surfaceDim = NeoVedicColors.Neutral4,
    surfaceBright = NeoVedicColors.Neutral22,
    surfaceContainerLowest = NeoVedicColors.Neutral4,
    surfaceContainerLow = NeoVedicColors.Neutral10,
    surfaceContainer = NeoVedicColors.Neutral12,
    surfaceContainerHigh = NeoVedicColors.Neutral17,
    surfaceContainerHighest = NeoVedicColors.Neutral22,
    scrim = NeoVedicColors.Neutral0
)

private val NeoVedicLightColorScheme = lightColorScheme(
    primary = NeoVedicColors.Primary40,
    onPrimary = NeoVedicColors.Neutral100,
    primaryContainer = NeoVedicColors.Primary90,
    onPrimaryContainer = NeoVedicColors.Primary10,
    secondary = NeoVedicColors.Secondary40,
    onSecondary = NeoVedicColors.Neutral100,
    secondaryContainer = NeoVedicColors.Secondary90,
    onSecondaryContainer = NeoVedicColors.Secondary10,
    tertiary = NeoVedicColors.Tertiary40,
    onTertiary = NeoVedicColors.Neutral100,
    tertiaryContainer = NeoVedicColors.Tertiary90,
    onTertiaryContainer = NeoVedicColors.Tertiary10,
    error = NeoVedicColors.Error40,
    onError = NeoVedicColors.Neutral100,
    errorContainer = NeoVedicColors.Error90,
    onErrorContainer = NeoVedicColors.Error10,
    background = NeoVedicColors.Neutral98,
    onBackground = NeoVedicColors.Neutral10,
    surface = NeoVedicColors.Neutral98,
    onSurface = NeoVedicColors.Neutral10,
    surfaceVariant = NeoVedicColors.Neutral92,
    onSurfaceVariant = NeoVedicColors.Neutral30,
    outline = NeoVedicColors.Neutral50,
    outlineVariant = NeoVedicColors.Neutral80,
    inverseSurface = NeoVedicColors.Neutral20,
    inverseOnSurface = NeoVedicColors.Neutral95,
    inversePrimary = NeoVedicColors.Primary80,
    surfaceDim = NeoVedicColors.Neutral87,
    surfaceBright = NeoVedicColors.Neutral98,
    surfaceContainerLowest = NeoVedicColors.Neutral100,
    surfaceContainerLow = NeoVedicColors.Neutral96,
    surfaceContainer = NeoVedicColors.Neutral94,
    surfaceContainerHigh = NeoVedicColors.Neutral92,
    surfaceContainerHighest = NeoVedicColors.Neutral90,
    scrim = NeoVedicColors.Neutral0
)

data class NeoVedicExtendedColors(
    val codeBackground: Color,
    val codeSurface: Color,
    val codeGutter: Color,
    val lineHighlight: Color,
    val statusSuccess: Color,
    val statusWarning: Color,
    val statusError: Color,
    val statusInfo: Color,
    val statusRunning: Color,
    val syntaxKeyword: Color,
    val syntaxString: Color,
    val syntaxNumber: Color,
    val syntaxComment: Color,
    val syntaxFunction: Color,
    val syntaxType: Color,
    val syntaxAnnotation: Color,
    val syntaxOperator: Color,
    val syntaxProperty: Color
)

val LocalNeoVedicColors = staticCompositionLocalOf {
    NeoVedicExtendedColors(
        codeBackground = Color.Unspecified,
        codeSurface = Color.Unspecified,
        codeGutter = Color.Unspecified,
        lineHighlight = Color.Unspecified,
        statusSuccess = Color.Unspecified,
        statusWarning = Color.Unspecified,
        statusError = Color.Unspecified,
        statusInfo = Color.Unspecified,
        statusRunning = Color.Unspecified,
        syntaxKeyword = Color.Unspecified,
        syntaxString = Color.Unspecified,
        syntaxNumber = Color.Unspecified,
        syntaxComment = Color.Unspecified,
        syntaxFunction = Color.Unspecified,
        syntaxType = Color.Unspecified,
        syntaxAnnotation = Color.Unspecified,
        syntaxOperator = Color.Unspecified,
        syntaxProperty = Color.Unspecified
    )
}

private val DarkExtendedColors = NeoVedicExtendedColors(
    codeBackground = NeoVedicColors.CodeBackground,
    codeSurface = NeoVedicColors.CodeSurface,
    codeGutter = NeoVedicColors.CodeGutter,
    lineHighlight = NeoVedicColors.LineHighlight,
    statusSuccess = NeoVedicColors.StatusSuccess,
    statusWarning = NeoVedicColors.StatusWarning,
    statusError = NeoVedicColors.StatusError,
    statusInfo = NeoVedicColors.StatusInfo,
    statusRunning = NeoVedicColors.StatusRunning,
    syntaxKeyword = NeoVedicColors.SyntaxKeyword,
    syntaxString = NeoVedicColors.SyntaxString,
    syntaxNumber = NeoVedicColors.SyntaxNumber,
    syntaxComment = NeoVedicColors.SyntaxComment,
    syntaxFunction = NeoVedicColors.SyntaxFunction,
    syntaxType = NeoVedicColors.SyntaxType,
    syntaxAnnotation = NeoVedicColors.SyntaxAnnotation,
    syntaxOperator = NeoVedicColors.SyntaxOperator,
    syntaxProperty = NeoVedicColors.SyntaxProperty
)

private val LightExtendedColors = NeoVedicExtendedColors(
    codeBackground = NeoVedicColors.CodeBackgroundLight,
    codeSurface = NeoVedicColors.CodeSurfaceLight,
    codeGutter = NeoVedicColors.CodeGutterLight,
    lineHighlight = NeoVedicColors.LineHighlightLight,
    statusSuccess = NeoVedicColors.StatusSuccess,
    statusWarning = NeoVedicColors.StatusWarning,
    statusError = NeoVedicColors.StatusError,
    statusInfo = NeoVedicColors.StatusInfo,
    statusRunning = NeoVedicColors.StatusRunning,
    syntaxKeyword = Color(0xFF6D3FC0),
    syntaxString = Color(0xFF006B52),
    syntaxNumber = Color(0xFF8C5B00),
    syntaxComment = Color(0xFF78767A),
    syntaxFunction = Color(0xFF2D6DB5),
    syntaxType = Color(0xFF8C5B00),
    syntaxAnnotation = Color(0xFF8C5B00),
    syntaxOperator = Color(0xFF474549),
    syntaxProperty = Color(0xFF006B52)
)

@Composable
fun BuildBuddyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NeoVedicDarkColorScheme else NeoVedicLightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalNeoVedicColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NeoVedicTypography,
            shapes = NeoVedicShapes,
            content = content
        )
    }
}

object BuildBuddyTheme {
    val extendedColors: NeoVedicExtendedColors
        @Composable
        get() = LocalNeoVedicColors.current
}
