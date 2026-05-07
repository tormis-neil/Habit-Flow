package com.habitflow.app.presentation.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.habitflow.app.domain.model.AuthState
import com.habitflow.app.presentation.ui.screens.auth.SignInScreen
import com.habitflow.app.presentation.ui.screens.auth.SignUpScreen
import com.habitflow.app.presentation.ui.screens.habits.HabitDetailScreen
import com.habitflow.app.presentation.ui.screens.habits.HabitFormScreen
import com.habitflow.app.presentation.ui.screens.home.HomeScreen
import com.habitflow.app.presentation.ui.screens.progress.ProgressScreen
import com.habitflow.app.presentation.ui.screens.settings.SettingsScreen
import com.habitflow.app.presentation.viewmodel.AuthViewModel

sealed class Screen(val route: String) {
    data object SignIn    : Screen("auth/signin")
    data object SignUp    : Screen("auth/signup")
    data object Home     : Screen("home")
    data object Progress : Screen("progress")
    data object Settings : Screen("settings")
    data object HabitCreate : Screen("habit/create")

    data object HabitEdit : Screen("habit/{habitId}/edit") {
        fun createRoute(habitId: Long) = "habit/$habitId/edit"
    }
    data object HabitDetail : Screen("habit/{habitId}/detail") {
        fun createRoute(habitId: Long) = "habit/$habitId/detail"
    }
}

private const val ANIM_DURATION = 300

@Composable
fun HabitFlowNavGraph(navController: NavHostController) {
    // Auth state is observed at the graph level so it survives screen navigation.
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()

    // Show a loading spinner while Firebase checks the stored token.
    // Returning early prevents NavHost from flashing the wrong start destination.
    if (authState is AuthState.Loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val startDestination = if (authState is AuthState.Authenticated)
        Screen.Home.route else Screen.SignIn.route

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(ANIM_DURATION),
            )
        },
        exitTransition = { fadeOut(tween(ANIM_DURATION)) },
        popEnterTransition = { fadeIn(tween(ANIM_DURATION)) },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(ANIM_DURATION),
            )
        },
    ) {
        // ── Auth ──────────────────────────────────────────────────────────────
        composable(Screen.SignIn.route) {
            SignInScreen(
                viewModel = authViewModel,
                onNavigateToSignUp = { navController.navigate(Screen.SignUp.route) },
            )
        }
        composable(Screen.SignUp.route) {
            SignUpScreen(
                viewModel = authViewModel,
                onNavigateToSignIn = { navController.popBackStack() },
            )
        }

        // ── Home ──────────────────────────────────────────────────────────────
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = hiltViewModel(),
                onNavigateToDetail   = { id -> navController.navigate(Screen.HabitDetail.createRoute(id)) },
                onNavigateToCreate   = { navController.navigate(Screen.HabitCreate.route) },
                onNavigateToProgress = { navController.navigate(Screen.Progress.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
            )
        }

        // ── Progress ─────────────────────────────────────────────────────────
        composable(Screen.Progress.route) {
            ProgressScreen(
                viewModel = hiltViewModel(),
                onNavigateBack     = { navController.popBackStack() },
                onNavigateToDetail = { id -> navController.navigate(Screen.HabitDetail.createRoute(id)) },
            )
        }

        // ── Settings ─────────────────────────────────────────────────────────
        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // ── Create Habit ─────────────────────────────────────────────────────
        composable(Screen.HabitCreate.route) {
            HabitFormScreen(
                viewModel      = hiltViewModel(),
                onNavigateBack = { navController.popBackStack() },
                onSaved        = { navController.popBackStack() },
            )
        }

        // ── Edit Habit ───────────────────────────────────────────────────────
        composable(
            route = Screen.HabitEdit.route,
            arguments = listOf(navArgument("habitId") { type = NavType.LongType }),
        ) {
            HabitFormScreen(
                viewModel      = hiltViewModel(),
                onNavigateBack = { navController.popBackStack() },
                onSaved        = { navController.popBackStack() },
            )
        }

        // ── Habit Detail ─────────────────────────────────────────────────────
        composable(
            route = Screen.HabitDetail.route,
            arguments = listOf(navArgument("habitId") { type = NavType.LongType }),
        ) {
            HabitDetailScreen(
                viewModel        = hiltViewModel(),
                onNavigateBack   = { navController.popBackStack() },
                onNavigateToEdit = { id -> navController.navigate(Screen.HabitEdit.createRoute(id)) },
                onDeleted        = { navController.popBackStack() },
            )
        }
    }

    // React to auth state changes after the NavHost is first rendered.
    // Guard against re-navigating when already at the target destination.
    LaunchedEffect(authState) {
        val current = navController.currentDestination?.route
        when (authState) {
            is AuthState.Authenticated -> {
                if (current == Screen.SignIn.route || current == Screen.SignUp.route) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            AuthState.Unauthenticated -> {
                if (current != Screen.SignIn.route) {
                    navController.navigate(Screen.SignIn.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            else -> {}
        }
    }
}
