package com.habitflow.app.presentation.ui.screens.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitflow.app.R
import com.habitflow.app.presentation.ui.components.SectionHeader
import com.habitflow.app.presentation.ui.components.StreakBadge
import com.habitflow.app.presentation.ui.components.WeeklyDotRow
import com.habitflow.app.presentation.viewmodel.HabitDetailViewModel
import java.time.format.DateTimeFormatter

/**
 * FEATURE C — Jetpack Compose UI
 *
 * HabitDetailScreen is the most information-rich screen in the app.
 * It displays everything about a single habit:
 *  - Hero card (name, description, frequency, current streak)
 *  - "Mark Complete" button (toggles today's completion)
 *  - Statistics (current streak, best streak, total completions)
 *  - Weekly dot row (which days this week the habit was done)
 *  - Recent completion history (last 7 dates)
 *  - Enable/disable toggle and start date
 *
 * The screen also handles DELETE via a confirmation dialog before acting.
 * When a habit is deleted, `uiState.isDeleted` becomes true and `LaunchedEffect`
 * automatically navigates the user back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    viewModel: HabitDetailViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onDeleted: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Local UI state for the delete confirmation dialog — not persisted, just controls visibility
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Navigate back automatically once the ViewModel confirms the habit was deleted
    LaunchedEffect(uiState.isDeleted) { if (uiState.isDeleted) onDeleted() }

    val habit = uiState.habit

    // Parse the habit's hex color once and cache it — avoids re-parsing on every recompose
    val habitColor = remember(habit?.color) {
        habit?.color?.let {
            runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrDefault(Color(0xFF6750A4))
        } ?: Color(0xFF6750A4)
    }

    // ── Delete confirmation dialog ────────────────────────────────────────────
    // Only rendered when the user taps the delete icon — not always in the tree
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_habit_title)) },
            text  = { Text(stringResource(R.string.delete_habit_message, habit?.title ?: "")) },
            confirmButton = {
                // Red confirm button — calls ViewModel to delete, then dismisses dialog
                TextButton(onClick = { showDeleteDialog = false; viewModel.onDeleteHabit() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(habit?.title ?: stringResource(R.string.title_habit_detail)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) }
                },
                actions = {
                    // Edit and Delete buttons only appear once the habit data has loaded
                    habit?.let {
                        IconButton(onClick = { onNavigateToEdit(it.id) }) { Icon(Icons.Default.Edit, stringResource(R.string.cd_edit)) }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.cd_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
    ) { innerPadding ->

        // Loading state — show a spinner while the habit data is being loaded from the database
        if (habit == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Hero card ─────────────────────────────────────────────────────
            // Coloured card at the top showing the habit's identity at a glance
            ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = habitColor.copy(alpha = 0.1f))) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Circle avatar showing the first letter of the habit title
                    Box(Modifier.size(64.dp).clip(CircleShape).background(habitColor.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Text(habit.title.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = habitColor, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(habit.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (habit.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(habit.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AssistChip(onClick = {}, label = { Text(habit.frequency.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            leadingIcon = { Icon(Icons.Default.Repeat, null, Modifier.size(16.dp)) })
                        // Streak badge only shown when the user has an active streak
                        if (habit.currentStreak > 0) StreakBadge(streak = habit.currentStreak)
                    }
                }
            }

            // ── Complete button ───────────────────────────────────────────────
            // Changes appearance based on whether the habit is done today or not
            Button(
                onClick = viewModel::onToggleToday,
                modifier = Modifier.fillMaxWidth(),
                colors = if (uiState.isCompletedToday)
                    ButtonDefaults.buttonColors(containerColor = habitColor.copy(alpha = 0.15f), contentColor = habitColor)
                else ButtonDefaults.buttonColors(containerColor = habitColor),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(if (uiState.isCompletedToday) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null)
                Spacer(Modifier.width(8.dp))
                Text(if (uiState.isCompletedToday) stringResource(R.string.completed_today_label) else stringResource(R.string.mark_complete_label))
            }

            // ── Statistics row ────────────────────────────────────────────────
            SectionHeader(title = stringResource(R.string.section_statistics))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(stringResource(R.string.stat_current_streak), "${habit.currentStreak}", stringResource(R.string.stat_unit_days), Icons.Default.LocalFireDepartment, Color(0xFFFF6D00), Modifier.weight(1f))
                StatCard(stringResource(R.string.stat_best_streak),    "${habit.longestStreak}",  stringResource(R.string.stat_unit_days), Icons.Default.EmojiEvents,           Color(0xFFFDD835), Modifier.weight(1f))
                StatCard(stringResource(R.string.stat_total),          "${habit.totalCompletions}", stringResource(R.string.stat_unit_done), Icons.Default.CheckCircle,           Color(0xFF4CAF50), Modifier.weight(1f))
            }

            // ── This week ─────────────────────────────────────────────────────
            // 7 dots (Mon–Sun) filled for days the habit was completed this week
            SectionHeader(title = stringResource(R.string.section_this_week))
            ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                WeeklyDotRow(weekProgress = uiState.weeklyProgress, modifier = Modifier.fillMaxWidth().padding(16.dp), accentColor = habitColor)
            }

            // ── Recent completion history ─────────────────────────────────────
            // Shows the last 7 completed dates; hidden entirely if no history exists
            if (uiState.completedDates.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.section_recent_completions))
                ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        uiState.completedDates.takeLast(7).reversed().forEach { date ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = habitColor, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // ── Enable / disable toggle ───────────────────────────────────────
            // Pausing a habit hides it from the daily list without deleting it
            SectionHeader(title = stringResource(R.string.section_settings))
            ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(stringResource(R.string.setting_habit_active), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.setting_habit_active_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = habit.isEnabled, onCheckedChange = { viewModel.onToggleEnabled() })
                }
            }

            // Start date text — formatted once and cached with `remember`
            val startedDateText = remember(habit.startDate) {
                habit.startDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
            }
            Text(
                stringResource(R.string.detail_started_on, startedDateText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** A compact card displaying one statistic (icon + value + unit + label). */
@Composable
private fun StatCard(label: String, value: String, unit: String, icon: ImageVector, iconTint: Color, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
