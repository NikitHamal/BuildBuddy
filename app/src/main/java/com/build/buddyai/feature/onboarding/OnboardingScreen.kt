package com.build.buddyai.feature.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.build.buddyai.R
import com.build.buddyai.core.designsystem.component.NvFilledButton
import com.build.buddyai.core.designsystem.component.NvTextButton
import com.build.buddyai.core.designsystem.theme.NvSpacing
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onNavigateToModels: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()

    val pages = remember {
        listOf(
            OnboardingPage(Icons.Filled.AutoAwesome, R.string.onboarding_welcome_title, R.string.onboarding_welcome_subtitle),
            OnboardingPage(Icons.Filled.CreateNewFolder, R.string.onboarding_create_title, R.string.onboarding_create_subtitle),
            OnboardingPage(Icons.Filled.Psychology, R.string.onboarding_ai_title, R.string.onboarding_ai_subtitle),
            OnboardingPage(Icons.Filled.Build, R.string.onboarding_build_title, R.string.onboarding_build_subtitle),
            OnboardingPage(Icons.Filled.Key, R.string.onboarding_setup_title, R.string.onboarding_setup_subtitle)
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Skip button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NvSpacing.Md, vertical = NvSpacing.Xs),
                horizontalArrangement = Arrangement.End
            ) {
                if (pagerState.currentPage < pages.size - 1) {
                    NvTextButton(
                        text = stringResource(R.string.onboarding_skip),
                        onClick = {
                            viewModel.completeOnboarding()
                            onComplete()
                        }
                    )
                }
            }

            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                OnboardingPageContent(page = pages[page])
            }

            // Page indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(NvSpacing.Md),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (isSelected) 10.dp else 6.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    ) {}
                }
            }

            // Bottom actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NvSpacing.Xl, vertical = NvSpacing.Lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > 0) {
                    NvTextButton(
                        text = stringResource(R.string.action_back),
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }
                    )
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                if (pagerState.currentPage == pages.size - 1) {
                    Column(horizontalAlignment = Alignment.End) {
                        NvFilledButton(
                            text = stringResource(R.string.onboarding_finish),
                            onClick = {
                                viewModel.completeOnboarding()
                                onComplete()
                            },
                            icon = Icons.Filled.RocketLaunch
                        )
                    }
                } else {
                    NvFilledButton(
                        text = stringResource(R.string.onboarding_next),
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = NvSpacing.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(NvSpacing.Lg)
                    .fillMaxSize(),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(Modifier.height(NvSpacing.Xxl))

        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(NvSpacing.Sm))

        Text(
            text = stringResource(page.subtitleRes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp)
        )
    }
}

private data class OnboardingPage(
    val icon: ImageVector,
    val titleRes: Int,
    val subtitleRes: Int
)
