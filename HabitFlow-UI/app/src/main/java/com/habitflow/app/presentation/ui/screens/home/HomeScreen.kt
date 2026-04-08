package com.habitflow.app.presentation.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitflow.app.R
import com.habitflow.app.presentation.ui.components.*
import com.habitflow.app.presentation.viewmodel.HomeViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * FEATURE C — Jetpack Compose UI
 *
 * HomeScreen is the MAIN SCREEN of the app — the first thing the user sees.
 * It shows all habits scheduled for the selected date, daily progress, and a
 * way to mark habits as complete.
 *
 * This is a purely visual function (Composable). It does NOT perform any
 * database logic — all data comes from HomeViewModel via uiState.
 *
 * Navigation callbacks (onNavigateTo…) are passed in so this screen can
 * trigger navigation without knowing anything about the NavGraph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToCreate: () -> Unit,
    onNavigateToProgress: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    // Collect the ViewModel's state — the UI automatically rebuilds whenever this changes.
    // `collectAsStateWithLifecycle` stops collecting when the screen is in the background (saves battery).
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // `remember` caches this value — `today` won't be recalculated on every recomposition
    val today = remember { LocalDate.now() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        // App name pulled from strings.xml (not hardcoded) for localization support
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        // Today's date is cached with `remember` so it's formatted only once
                        Text(
                            text = remember { today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    // Icon buttons in the top-right navigate to other screens
                    IconButton(onClick = onNavigateToProgress) {
                        Icon(Icons.Default.BarChart, contentDescription = stringResource(R.string.cd_progress))
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        // Floating button in the bottom-right to create a new habit
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToCreate,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_fab_new_habit)) },
            )
        },
    ) { innerPadding ->
        // LazyColumn only renders items that are visible on screen (efficient for long lists)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Daily summary card — shows "X of Y habits done" with a progress bar
            item {
                DailySummaryCard(
                    completed = uiState.dailyProgress.completedHabits,
                    total = uiState.dailyProgress.totalHabits,
                )
            }

            // Horizontal row of date chips — lets the user browse habits for ±3 days
            item {
                DateChipRow(
                    selectedDate = uiState.selectedDate,
                    onDateSelected = viewModel::onDateSelected,
                )
            }

            // EMPTY STATE — shown when there are no habits for the selected date
            if (uiState.habits.isEmpty()) {
                item {
                    EmptyStateMessage(
                        icon = Icons.Default.CheckCircleOutline,
                        title = stringResource(R.string.home_no_habits_title),
                        subtitle = stringResource(R.string.home_no_habits_subtitle),
                        modifier = Modifier.padding(top = 48.dp),
                        action = {
                            FilledTonalButton(onClick = onNavigateToCreate) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_create_habit))
                            }
                        }
                    )
                }
            } else {
                item {
                    val suffix = if (uiState.habits.size != 1) "s" else ""
                    SectionHeader(title = stringResource(R.string.home_habits_count, uiState.habits.size, suffix))
                }
                // Render each habit as a card — `key` helps Compose animate list changes efficiently
                items(uiState.habits, key = { it.id }) { habit ->
                    // AnimatedVisibility adds a fade+slide animation when the card first appears
                    AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically()) {
                        HabitCard(
                            habit = habit,
                            isCompleted = habit.id in uiState.completedHabitIds, // Is it done today?
                            onToggle = { viewModel.onToggleHabit(habit.id) },    // Mark/unmark complete
                            onClick  = { onNavigateToDetail(habit.id) },          // Open detail screen
                        )
                    }
                }
                item { Spacer(Modifier.height(80.dp)) } // Bottom padding so FAB doesn't overlap last card
            }
        }
    }
}

// ── Private Composables ───────────────────────────────────────────────────────

/**
 * The card at the top of the Home screen showing daily completion status.
 * Changes color and message when all habits for the day are done.
 */
@Composable
private fun DailySummaryCard(completed: Int, total: Int) {
    val allDone = total > 0 && completed >= total
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            // Green-tinted when all done, neutral otherwise
            containerColor = if (allDone) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (allDone) {
                    Icon(Icons.Default.EmojiEvents, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_all_done), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    Text(
                        if (total == 0) stringResource(R.string.home_no_habits_today) else stringResource(R.string.home_keep_going),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            if (total > 0) {
                Spacer(Modifier.height(12.dp))
                // The horizontal progress bar showing completed / total ratio
                DailyProgressBar(completed = completed, total = total)
            }
        }
    }
}

/**
 * A scrollable horizontal row of date chips (3 days before and 3 days after today).
 * Tapping a chip changes the selected date, filtering habits shown in the list.
 */
@Composable
private fun DateChipRow(selectedDate: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    val today = remember { LocalDate.now() }
    val dates = remember(today) { (-3..3).map { today.plusDays(it.toLong()) } }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(dates) { date ->
            FilterChip(
                selected = date == selectedDate, // Highlights the currently selected date
                onClick = { onDateSelected(date) },
                label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.labelSmall)
                        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal)
                    }
                },
            )
        }
    }
}
