package com.build.buddyai.feature.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.build.buddyai.core.designsystem.theme.NeoVedicSpacing
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashComplete: (isOnboarded: Boolean) -> Unit,
    isOnboarded: Boolean
) {
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(800),
        label = "alpha"
    )
    val scaleAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.8f,
        animationSpec = tween(800, easing = EaseOutCubic),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(1200)
        onSplashComplete(isOnboarded)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(alphaAnim.value)
                .scale(scaleAnim.value)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.large
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "B",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp
                    ),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(Modifier.height(NeoVedicSpacing.LG))
            Text(
                text = "BuildBuddy",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(NeoVedicSpacing.XS))
            Text(
                text = "Vibe Code. Build. Ship.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
