package com.habitflow.app.presentation.ui.screens.progress

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitflow.app.R
import com.habitflow.app.domain.model.Habit
import com.habitflow.app.domain.util.HabitConstants
import com.habitflow.app.presentation.ui.components.EmptyStateMessage
import com.habitflow.app.presentation.ui.components.SectionHeader
import com.habitflow.app.presentation.ui.components.StreakBadge
import com.habitflow.app.presentation.viewmodel.ProgressUiState
import com.habitflow.app.presentation.viewmodel.ProgressViewModel

/**
 * FEATURE C — Jetpack Compose UI
 *
 * ProgressScreen shows the user's overall performance across ALL habits.
 * It is divided into two areas:
 *  1. Overview Section — high-level stats (total habits, streak days, weekly rate)
 *  2. Habit Breakdown — per-habit progress cards with weekly progress bars
 *
 * All data is supplied by ProgressViewModel — this screen only renders it.
 * Tapping a habit card navigates to that habit's detail screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    viewModel: ProgressViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
) {
    // Collect live state — UI automatically updates when any habit or log changes
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_progress)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Overview summary card ─────────────────────────────────────────
            item {
                OverviewSection(
                    totalHabits  = uiState.habits.size,
                    activeHabits = uiState.habits.count { it.isEnabled },
                    totalStreakDays = uiState.totalStreakDays,
                    weeklyRate     = uiState.weeklyCompletionRate,
                )
            }

            // EMPTY STATE — shown when there are no habits to display
            if (uiState.habits.isEmpty()) {
                item {
                    EmptyStateMessage(
                        icon = Icons.Default.BarChart,
                        title = stringResource(R.string.progress_no_habits_title),
                        subtitle = stringResource(R.string.progress_no_habits_subtitle),
                        modifier = Modifier.padding(top = 32.dp),
                    )
                }
            } else {
                item { SectionHeader(title = stringResource(R.string.section_habit_breakdown)) }
                // One card per habit — `key` enables efficient animated list updates
                items(uiState.habits, key = { it.id }) { habit ->
                    HabitProgressCard(
                        habit   = habit,
                        uiState = uiState,
                        onClick = { onNavigateToDetail(habit.id) },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/**
 * The top summary card — shows three key stats side by side:
 * total/active habits, combined streak days, and weekly completion rate.
 */
@Composable
private fun OverviewSection(totalHabits: Int, activeHabits: Int, totalStreakDays: Int, weeklyRate: Float) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.progress_your_overview), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                OverviewStat(stringResource(R.string.progress_stat_habits), stringResource(R.string.progress_habits_fraction, activeHabits, totalHabits), Icons.Default.List)
                OverviewStat(stringResource(R.string.progress_stat_streak_days), "$totalStreakDays", Icons.Default.LocalFireDepartment, Color(0xFFFF6D00))
                // weeklyRate is 0.0–1.0; multiply by 100 and truncate for a clean percentage
                OverviewStat(stringResource(R.string.progress_stat_week_rate), "${(weeklyRate * 100).toInt()}%", Icons.Default.TrendingUp, Color(0xFF4CAF50))
            }
        }
    }
}

/** A single icon + value + label column inside the overview card. */
@Composable
private fun OverviewStat(label: String, value: String, icon: ImageVector, iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}

/**
 * A card for each habit showing its name, streak badge, and weekly progress bar.
 * Tapping the card opens that habit's detail screen for a deeper breakdown.
 */
@Composable
private fun HabitProgressCard(habit: Habit, uiState: ProgressUiState, onClick: () -> Unit) {
    // Parse the hex color string once and cache it — avoids re-parsing on every recompose
    val habitColor = remember(habit.color) {
        runCatching { Color(android.graphics.Color.parseColor(habit.color)) }.getOrDefault(Color(0xFF6750A4))
    }
    // How many days this week the habit was completed out of the target (7 for daily, 1 for weekly)
    val weekDone   = uiState.weeklyProgressMap[habit.id]?.count { it } ?: 0
    val weekTarget = HabitConstants.weeklyTarget(habit.frequency)
    val weeklyRate = weekDone.toFloat() / weekTarget // 0.0 to 1.0 fraction for the progress bar

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(size = 16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(habit.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(habit.frequency.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Streak badge (🔥 N days) only shown when the habit has an active streak
                if (habit.currentStreak > 0) StreakBadge(streak = habit.currentStreak)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // The coloured weekly progress bar — fills up as the user completes the habit
                LinearProgressIndicator(
                    progress = { weeklyRate },
                    modifier = Modifier.weight(1f).height(8.dp),
                    color = habitColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                )
                Text(stringResource(R.string.progress_week_summary, weekDone, weekTarget), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            // Summary chips: best streak, total completions, and "Paused" if disabled
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoChip(stringResource(R.string.chip_best_streak, habit.longestStreak))
                InfoChip(stringResource(R.string.chip_total_completions, habit.totalCompletions))
                if (!habit.isEnabled) InfoChip(stringResource(R.string.chip_paused))
            }
        }
    }
}

/** A small pill-shaped label used for secondary stats at the bottom of a habit card. */
@Composable
private fun InfoChip(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
