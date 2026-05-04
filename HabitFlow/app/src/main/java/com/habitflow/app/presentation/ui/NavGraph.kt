package com.habitflow.app.presentation.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.habitflow.app.presentation.ui.screens.habits.HabitDetailScreen
import com.habitflow.app.presentation.ui.screens.habits.HabitFormScreen
import com.habitflow.app.presentation.ui.screens.home.HomeScreen
import com.habitflow.app.presentation.ui.screens.progress.ProgressScreen
import com.habitflow.app.presentation.ui.screens.settings.SettingsScreen

/**
 * FEATURE C — Jetpack Compose UI
 *
 * Screen is a sealed class that defines all the ROUTES (pages) in the app.
 * Think of each route like a web URL — the app navigates between them.
 *
 * Sealed class means no other file can add new screens accidentally —
 * all possible destinations are listed here in one place.
 */
sealed class Screen(val route: String) {
    data object Home       : Screen("home")             // The main habit list
    data object Progress   : Screen("progress")          // Progress & stats overview
    data object Settings   : Screen("settings")          // Theme settings
    data object HabitCreate: Screen("habit/create")      // Create a new habit

    // Edit and Detail carry a habitId in the route (like a URL parameter)
    data object HabitEdit  : Screen("habit/{habitId}/edit") {
        fun createRoute(habitId: Long) = "habit/$habitId/edit"
    }
    data object HabitDetail: Screen("habit/{habitId}/detail") {
        fun createRoute(habitId: Long) = "habit/$habitId/detail"
    }
}

// Animation duration for all screen transitions (in milliseconds)
private const val ANIM_DURATION = 300

/**
 * HabitFlowNavGraph is the app's NAVIGATION MAP — it defines which screen shows
 * for each route and how screens animate in and out.
 *
 * Every composable() block below is one screen destination. The NavHost starts
 * at the Home screen and handles back-stack (going back to previous screens).
 *
 * Each screen receives its ViewModel via Hilt, which injects the
 * dependencies automatically.
 */
@Composable
fun HabitFlowNavGraph(
    navController: NavHostController,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route, // The app always opens on Home
        // Slide in from the right when navigating forward
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(ANIM_DURATION),
            )
        },
        exitTransition = { fadeOut(tween(ANIM_DURATION)) },    // Fade out when leaving
        popEnterTransition = { fadeIn(tween(ANIM_DURATION)) }, // Fade in when going back
        // Slide out to the right when pressing back
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(ANIM_DURATION),
            )
        },
    ) {
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
                onSaved        = { navController.popBackStack() }, // Go back after saving
            )
        }

        // ── Edit Habit — requires a habitId in the route ──────────────────────
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

        // ── Habit Detail — requires a habitId in the route ────────────────────
        composable(
            route = Screen.HabitDetail.route,
            arguments = listOf(navArgument("habitId") { type = NavType.LongType }),
        ) {
            HabitDetailScreen(
                viewModel       = hiltViewModel(),
                onNavigateBack  = { navController.popBackStack() },
                onNavigateToEdit = { id -> navController.navigate(Screen.HabitEdit.createRoute(id)) },
                onDeleted        = { navController.popBackStack() }, // Go back after deleting
            )
        }
    }
}

