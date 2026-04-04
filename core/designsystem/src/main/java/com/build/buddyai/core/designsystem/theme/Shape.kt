package com.build.buddyai.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val NeoVedicShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

object NeoVedicRadius {
    val None = RoundedCornerShape(0.dp)
    val XS = RoundedCornerShape(2.dp)
    val SM = RoundedCornerShape(4.dp)
    val MD = RoundedCornerShape(8.dp)
    val LG = RoundedCornerShape(12.dp)
    val XL = RoundedCornerShape(16.dp)
    val Full = RoundedCornerShape(50)
}
