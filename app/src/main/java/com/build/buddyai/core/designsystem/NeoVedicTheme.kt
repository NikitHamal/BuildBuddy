package com.build.buddyai.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.build.buddyai.R

private val LightScheme = lightColorScheme(
    primary = Color(0xFF9C6B2F),
    onPrimary = Color(0xFFFFFBF2),
    secondary = Color(0xFF355A63),
    onSecondary = Color(0xFFF8FBFC),
    tertiary = Color(0xFF4E5E8C),
    background = Color(0xFFF7F1E3),
    onBackground = Color(0xFF151A20),
    surface = Color(0xFFFFFBF4),
    onSurface = Color(0xFF171C22),
    surfaceVariant = Color(0xFFF0E4CF),
    onSurfaceVariant = Color(0xFF554A3B),
    outline = Color(0x33282C31),
    error = Color(0xFFB3261E),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFE0BE83),
    onPrimary = Color(0xFF2D1D06),
    secondary = Color(0xFF9FC3CD),
    onSecondary = Color(0xFF102027),
    tertiary = Color(0xFFC1C8F4),
    background = Color(0xFF10151A),
    onBackground = Color(0xFFEAE1D0),
    surface = Color(0xFF141B21),
    onSurface = Color(0xFFF5ECDC),
    surfaceVariant = Color(0xFF212B34),
    onSurfaceVariant = Color(0xFFCDBEAA),
    outline = Color(0x66D2C5B4),
    error = Color(0xFFF2B8B5),
)

private val DisplayFont = FontFamily(Font(R.font.display_font, FontWeight.Medium))
private val BodyFont = FontFamily(Font(R.font.body_font, FontWeight.Normal))
val CodeFont = FontFamily(Font(R.font.code_font, FontWeight.Normal))

private val BuildBuddyTypography = androidx.compose.material3.Typography(
    headlineLarge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

@Immutable
data class NeoVedicSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 24.dp,
)

object NeoVedicTheme {
    val spacing = NeoVedicSpacing()
    val shape2 = RoundedCornerShape(2.dp)
    val shape8 = RoundedCornerShape(8.dp)
    val shape12 = RoundedCornerShape(12.dp)
    val shape16 = RoundedCornerShape(16.dp)
}

@Composable
fun BuildBuddyTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = BuildBuddyTypography,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = NeoVedicTheme.shape2,
            small = NeoVedicTheme.shape8,
            medium = NeoVedicTheme.shape12,
            large = NeoVedicTheme.shape16,
            extraLarge = NeoVedicTheme.shape16,
        ),
        content = content,
    )
}

@Composable
fun BuildBuddyCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(NeoVedicTheme.spacing.md),
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = NeoVedicTheme.shape12,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
fun StatusBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.defaultMinSize(minHeight = 28.dp),
        shape = NeoVedicTheme.shape8,
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.36f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}
