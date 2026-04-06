package com.build.buddyai.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.build.buddyai.feature.agent.AgentScreen
import com.build.buddyai.feature.dependencies.DependenciesScreen
import com.build.buddyai.feature.home.HomeScreen
import com.build.buddyai.feature.models.ModelsScreen
import com.build.buddyai.feature.onboarding.OnboardingScreen
import com.build.buddyai.feature.project.creation.CreateProjectScreen
import com.build.buddyai.feature.project.playground.PlaygroundScreen
import com.build.buddyai.feature.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CREATE_PROJECT = "create_project"
    const val PLAYGROUND = "playground/{projectId}"
    const val AGENT = "agent/{projectId}"
    const val SETTINGS = "settings"
    const val MODELS = "models"
    const val DEPENDENCIES = "dependencies/{projectId}"

    fun playground(projectId: String) = "playground/$projectId"
    fun agent(projectId: String) = "agent/$projectId"
    fun dependencies(projectId: String) = "dependencies/$projectId"
}

@Composable
fun BuildBuddyNavHost(
    onboardingCompleted: Boolean,
    navController: NavHostController = rememberNavController()
) {
    val startDestination = if (onboardingCompleted) Routes.HOME else Routes.ONBOARDING

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 4 } },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(200)) + slideInHorizontally(tween(200)) { -it / 4 } },
        popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it / 4 } }
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onNavigateToModels = {
                    navController.navigate(Routes.MODELS)
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToCreateProject = { navController.navigate(Routes.CREATE_PROJECT) },
                onNavigateToProject = { projectId -> navController.navigate(Routes.playground(projectId)) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToModels = { navController.navigate(Routes.MODELS) }
            )
        }

        composable(Routes.CREATE_PROJECT) {
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
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            PlaygroundScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToModels = { navController.navigate(Routes.MODELS) },
                onNavigateToAgent = { navController.navigate(Routes.agent(projectId)) },
                onNavigateToDependencies = { navController.navigate(Routes.dependencies(projectId)) }
            )
        }

        composable(
            route = Routes.AGENT,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            AgentScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                onNavigateToModels = { navController.navigate(Routes.MODELS) }
            )
        }

        composable(
            route = Routes.DEPENDENCIES,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            DependenciesScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToModels = { navController.navigate(Routes.MODELS) }
            )
        }

        composable(Routes.MODELS) {
            ModelsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
