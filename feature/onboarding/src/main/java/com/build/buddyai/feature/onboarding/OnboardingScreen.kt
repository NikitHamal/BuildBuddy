package com.build.buddyai.feature.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.core.designsystem.component.NeoVedicButton
import com.build.buddyai.core.designsystem.component.NeoVedicTextButton
import com.build.buddyai.core.designsystem.theme.NeoVedicSpacing
import kotlinx.coroutines.launch

data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val description: String
)

private val onboardingPages = listOf(
    OnboardingPage(
        icon = Icons.Outlined.PhoneAndroid,
        title = "Create Android Apps",
        subtitle = "On-Device Project Creation",
        description = "Create complete Android app projects directly on your device. Choose from Kotlin or Java, Jetpack Compose or XML Views, and start building instantly."
    ),
    OnboardingPage(
        icon = Icons.Outlined.AutoAwesome,
        title = "AI-Powered Coding",
        subtitle = "Vibe Coding with Intelligence",
        description = "Describe what you want to build in plain language. The AI assistant will plan, generate, modify, and refactor code for you — while you stay in control."
    ),
    OnboardingPage(
        icon = Icons.Outlined.Build,
        title = "Build & Install",
        subtitle = "On-Device Build System",
        description = "Build installable APKs directly on your device. View build logs, fix errors with AI assistance, and install your apps with a single tap."
    ),
    OnboardingPage(
        icon = Icons.Outlined.Key,
        title = "Bring Your Own AI",
        subtitle = "Flexible Model Providers",
        description = "Connect your preferred AI provider — NVIDIA, OpenRouter, or Gemini. Use your own API keys for full control over model selection and costs."
    )
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.isCompleted) {
        if (state.isCompleted) onComplete()
    }

    LaunchedEffect(state.currentPage) {
        pagerState.animateScrollToPage(state.currentPage)
    }

    LaunchedEffect(pagerState.currentPage) {
        viewModel.goToPage(pagerState.currentPage)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NeoVedicSpacing.LG, vertical = NeoVedicSpacing.SM),
            horizontalArrangement = Arrangement.End
        ) {
            NeoVedicTextButton(
                text = "Skip",
                onClick = { viewModel.completeOnboarding() }
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            OnboardingPageContent(onboardingPages[page])
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NeoVedicSpacing.XL),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = NeoVedicSpacing.XL)
            ) {
                repeat(onboardingPages.size) { index ->
                    val isSelected = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isSelected) 24.dp else 8.dp, 8.dp)
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.extraSmall
                            )
                    )
                }
            }

            val isLastPage = pagerState.currentPage == onboardingPages.size - 1
            NeoVedicButton(
                text = if (isLastPage) "Get Started" else "Continue",
                onClick = {
                    if (isLastPage) {
                        viewModel.completeOnboarding()
                    } else {
                        scope.launch {
                            viewModel.nextPage()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = if (isLastPage) Icons.Default.ArrowForward else null
            )
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = NeoVedicSpacing.XXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(Modifier.height(NeoVedicSpacing.XXL))
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(NeoVedicSpacing.SM))
        Text(
            text = page.subtitle,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(NeoVedicSpacing.LG))
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
