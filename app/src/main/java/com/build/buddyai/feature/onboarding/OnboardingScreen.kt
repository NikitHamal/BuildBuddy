package com.build.buddyai.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.build.buddyai.R
import com.build.buddyai.core.designsystem.BuildBuddyCard
import com.build.buddyai.core.designsystem.NeoVedicTheme

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.splash_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.splash_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    val spacing = NeoVedicTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.xl, vertical = spacing.lg),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Text(
                text = stringResource(R.string.splash_title),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = stringResource(R.string.onboarding_headline),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            BuildBuddyCard {
                FeatureLine(index = "01", body = stringResource(R.string.onboarding_body_one))
                Spacer(modifier = Modifier.height(spacing.sm))
                FeatureLine(index = "02", body = stringResource(R.string.onboarding_body_two))
                Spacer(modifier = Modifier.height(spacing.sm))
                FeatureLine(index = "03", body = stringResource(R.string.onboarding_body_three))
            }
            Text(
                text = stringResource(R.string.onboarding_setup_optional),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            TextButton(
                modifier = Modifier.weight(1f),
                onClick = onSkip,
            ) {
                Text(stringResource(R.string.action_skip))
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = onContinue,
            ) {
                Text(stringResource(R.string.action_continue))
            }
        }
    }
}

@Composable
private fun FeatureLine(index: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(index, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
