package com.build.buddyai.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val NvShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

object NvRadius {
    val None = 0.dp
    val Xs = 2.dp
    val Sm = 4.dp
    val Md = 8.dp
    val Lg = 12.dp
    val Xl = 16.dp
    val Xxl = 24.dp
    val Full = 100.dp
}

object NvElevation {
    val None = 0.dp
    val Xs = 1.dp
    val Sm = 2.dp
    val Md = 4.dp
    val Lg = 8.dp
}

object NvSpacing {
    val Xxxs = 2.dp
    val Xxs = 4.dp
    val Xs = 8.dp
    val Sm = 12.dp
    val Md = 16.dp
    val Lg = 20.dp
    val Xl = 24.dp
    val Xxl = 32.dp
    val Xxxl = 40.dp
    val Xxxxl = 48.dp
}

object NvBorder {
    val Hairline = 0.5.dp
    val Thin = 1.dp
    val Medium = 1.5.dp
}
