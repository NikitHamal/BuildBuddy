package com.build.buddyai.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.build.buddyai.core.designsystem.BuildBuddyTheme
import com.build.buddyai.core.model.ThemeMode
import com.build.buddyai.feature.home.HomeScreen
import com.build.buddyai.feature.home.HomeViewModel
import com.build.buddyai.feature.models.ModelsScreen
import com.build.buddyai.feature.models.ModelsViewModel
import com.build.buddyai.feature.onboarding.OnboardingScreen
import com.build.buddyai.feature.playground.PlaygroundScreen
import com.build.buddyai.feature.playground.PlaygroundViewModel
import com.build.buddyai.feature.project.create.CreateProjectScreen
import com.build.buddyai.feature.project.create.CreateProjectViewModel
import com.build.buddyai.feature.root.RootViewModel
import com.build.buddyai.feature.settings.SettingsScreen
import com.build.buddyai.feature.settings.SettingsViewModel
import kotlinx.coroutines.flow.collect

private object Routes {
    const val Splash = "splash"
    const val Onboarding = "onboarding"
    const val Home = "home"
    const val Create = "create"
    const val Models = "models"
    const val Settings = "settings"
    const val Playground = "playground/{projectId}"
}

@Composable
fun BuildBuddyApp(
    rootViewModel: RootViewModel = hiltViewModel(),
) {
    val rootState by rootViewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val configuration = LocalConfiguration.current
    val darkTheme = when (rootState.preferences.themeMode) {
        ThemeMode.SYSTEM -> configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    BuildBuddyTheme(darkTheme = darkTheme) {
        LaunchedEffect(rootState.loading, rootState.preferences.onboardingComplete) {
            if (!rootState.loading) {
                navController.navigate(if (rootState.preferences.onboardingComplete) Routes.Home else Routes.Onboarding) {
                    popUpTo(Routes.Splash) { inclusive = true }
                }
            }
        }
        NavHost(navController = navController, startDestination = Routes.Splash) {
            composable(Routes.Splash) {
                com.build.buddyai.feature.onboarding.SplashScreen()
            }
            composable(Routes.Onboarding) {
                val settingsViewModel: SettingsViewModel = hiltViewModel()
                OnboardingScreen(
                    onContinue = {
                        settingsViewModel.markOnboardingComplete()
                        navController.navigate(Routes.Home) {
                            popUpTo(Routes.Onboarding) { inclusive = true }
                        }
                    },
                    onSkip = {
                        settingsViewModel.markOnboardingComplete()
                        navController.navigate(Routes.Home) {
                            popUpTo(Routes.Onboarding) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.Home) {
                val viewModel: HomeViewModel = hiltViewModel()
                LaunchedEffect(viewModel) {
                    viewModel.openProjectEvents.collect { projectId ->
                        navController.navigate("playground/$projectId")
                    }
                }
                HomeScreen(
                    viewModel = viewModel,
                    onCreateProject = { navController.navigate(Routes.Create) },
                    onModels = { navController.navigate(Routes.Models) },
                    onSettings = { navController.navigate(Routes.Settings) },
                )
            }
            composable(Routes.Create) {
                val viewModel: CreateProjectViewModel = hiltViewModel()
                LaunchedEffect(viewModel) {
                    viewModel.createdProjectEvents.collect { projectId ->
                        navController.navigate("playground/$projectId") {
                            popUpTo(Routes.Create) { inclusive = true }
                        }
                    }
                }
                CreateProjectScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.Models) {
                val viewModel: ModelsViewModel = hiltViewModel()
                ModelsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.Settings) {
                val viewModel: SettingsViewModel = hiltViewModel()
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.Playground,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
            ) {
                val viewModel: PlaygroundViewModel = hiltViewModel()
                PlaygroundScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
