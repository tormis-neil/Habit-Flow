package com.habitflow.app.presentation.ui.screens.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitflow.app.R
import com.habitflow.app.domain.model.HabitFrequency
import com.habitflow.app.presentation.ui.theme.habitColorOptions
import com.habitflow.app.presentation.viewmodel.HabitFormViewModel

/**
 * FEATURE C — Jetpack Compose UI
 *
 * HabitFormScreen serves DUAL PURPOSE — it is used both for:
 *  - Creating a new habit (no habitId in the route)
 *  - Editing an existing habit (habitId passed in the route)
 *
 * The ViewModel detects which mode it's in and pre-fills the form if editing.
 * The screen itself doesn't need to know — it just reacts to `uiState`.
 *
 * When the user saves successfully, `uiState.isSaved` flips to true,
 * which triggers `LaunchedEffect` to call `onSaved()` and go back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitFormScreen(
    viewModel: HabitFormViewModel,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
) {
    // Collect live state from the ViewModel — UI rebuilds automatically on changes
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // LaunchedEffect runs once when `isSaved` becomes true — triggers navigation back
    LaunchedEffect(uiState.isSaved) { if (uiState.isSaved) onSaved() }

    Scaffold(
        topBar = {
            TopAppBar(
                // Title changes dynamically — "New Habit" or "Edit Habit"
                title = { Text(if (uiState.isEditing) stringResource(R.string.title_edit_habit) else stringResource(R.string.title_new_habit)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) }
                },
                actions = {
                    // Save button in the top bar — triggers validation and database write
                    TextButton(onClick = viewModel::onSave) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
                .verticalScroll(rememberScrollState()) // Scrollable form for small screens
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ── Title field ───────────────────────────────────────────────────
            // `isError` turns the field red when validation fails (title left blank)
            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::onTitleChange, // Updates ViewModel state on every keystroke
                label = { Text(stringResource(R.string.habit_title_label)) },
                placeholder = { Text(stringResource(R.string.habit_title_hint)) },
                isError = uiState.titleError != null,
                // Shows the error message below the field only when validation fails
                supportingText = uiState.titleError?.let { errorRes -> { Text(stringResource(errorRes), color = MaterialTheme.colorScheme.error) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            // ── Description field ─────────────────────────────────────────────
            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text(stringResource(R.string.habit_description_label)) },
                placeholder = { Text(stringResource(R.string.habit_description_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
            )

            // ── Frequency chips (Daily / Weekly) ────────────────────────────
            Text(stringResource(R.string.label_frequency), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HabitFrequency.entries.forEach { freq ->
                    val selected = uiState.frequency == freq
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.onFrequencyChange(freq) },
                        label = { Text(freq.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        // Checkmark icon only visible when this frequency is selected
                        leadingIcon = if (selected) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null,
                    )
                }
            }

            // ── Color picker ──────────────────────────────────────────────────
            Text(stringResource(R.string.label_color), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                habitColorOptions.forEach { (hex, name) ->
                    // `remember(hex)` caches the Color object — avoids re-parsing the hex on every recompose
                    val color = remember(hex) {
                        runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF6750A4))
                    }
                    val isSelected = uiState.color == hex
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(color)
                            // Add a border ring around the currently selected color
                            .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { viewModel.onColorChange(hex) },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Checkmark overlay on the selected color circle
                        if (isSelected) Icon(Icons.Default.Check, name, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // ── Tips card ─────────────────────────────────────────────────────
            // A static informational card with habit-building tips for the user
            ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.tips_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.tips_body),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
