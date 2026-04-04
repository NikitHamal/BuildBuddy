package com.build.buddyai.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.build.buddyai.feature.home.HomeScreen
import com.build.buddyai.feature.onboarding.OnboardingScreen
import com.build.buddyai.feature.onboarding.SplashScreen
import com.build.buddyai.feature.project.create.CreateProjectScreen
import com.build.buddyai.feature.project.playground.PlaygroundScreen
import com.build.buddyai.feature.settings.SettingsScreen

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CREATE_PROJECT = "create_project"
    const val PLAYGROUND = "playground/{projectId}"
    const val SETTINGS = "settings"

    fun playground(projectId: String) = "playground/$projectId"
}

@Composable
fun AppNavigation(isOnboarded: Boolean) {
    val navController = rememberNavController()

    val animDuration = 300

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(animDuration)
            )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(animDuration))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(animDuration))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(animDuration)
            )
        }
    ) {
        composable(
            route = Routes.SPLASH,
            enterTransition = { fadeIn(animationSpec = tween(0)) },
            exitTransition = { fadeOut(animationSpec = tween(animDuration)) }
        ) {
            SplashScreen(
                isOnboarded = isOnboarded,
                onSplashComplete = { onboarded ->
                    val destination = if (onboarded) Routes.HOME else Routes.ONBOARDING
                    navController.navigate(destination) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(route = Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(route = Routes.HOME) {
            HomeScreen(
                onCreateProject = {
                    navController.navigate(Routes.CREATE_PROJECT)
                },
                onImportProject = {
                    // TODO: implement import flow
                },
                onOpenProject = { projectId ->
                    navController.navigate(Routes.playground(projectId))
                },
                onOpenSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(route = Routes.CREATE_PROJECT) {
            CreateProjectScreen(
                onProjectCreated = { projectId ->
                    navController.navigate(Routes.playground(projectId)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PLAYGROUND,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            PlaygroundScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                onOpenSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(route = Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
