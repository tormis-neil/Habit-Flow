package com.habitflow.app.presentation.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitflow.app.R
import com.habitflow.app.presentation.viewmodel.AuthViewModel
import com.habitflow.app.presentation.viewmodel.SettingsViewModel
import com.habitflow.app.domain.model.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    authViewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
) {
    val darkModeEnabled by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.dailyRemindersEnabled.collectAsStateWithLifecycle()
    var weeklyReportEnabled by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    val isOffline = !authViewModel.isNetworkAvailable()

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(stringResource(R.string.settings_sign_out)) },
            text = {
                if (isOffline) {
                    Text("⚠️ You are offline. If you sign out now, you won't be able to sign back in until you reconnect to the internet. Are you sure?")
                } else {
                    Text(stringResource(R.string.settings_sign_out_confirm))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.signOut()
                    showSignOutDialog = false
                }) { Text(stringResource(R.string.settings_sign_out)) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Account
            SettingsSection(title = stringResource(R.string.settings_section_account)) {
                SettingsAccountRow(
                    profile = userProfile,
                    onSignOut = { showSignOutDialog = true },
                )
            }

            // Appearance
            SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
                SettingsSwitchRow(
                    icon = Icons.Default.DarkMode,
                    title = stringResource(R.string.settings_dark_mode),
                    subtitle = stringResource(R.string.settings_dark_mode_subtitle),
                    checked = darkModeEnabled,
                    onCheckedChange = viewModel::setDarkMode,
                )
            }

            // Notifications
            SettingsSection(title = stringResource(R.string.settings_section_notifications)) {
                SettingsSwitchRow(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.settings_notifications),
                    subtitle = stringResource(R.string.settings_notifications_subtitle),
                    checked = notificationsEnabled,
                    onCheckedChange = viewModel::setDailyRemindersEnabled,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SettingsSwitchRow(
                    icon = Icons.Default.BarChart,
                    title = stringResource(R.string.settings_weekly_report),
                    subtitle = stringResource(R.string.settings_weekly_report_subtitle),
                    checked = weeklyReportEnabled,
                    onCheckedChange = { weeklyReportEnabled = it },
                )
            }

            // Data
            SettingsSection(title = stringResource(R.string.settings_section_data)) {
                SettingsActionRow(
                    icon = Icons.Default.Upload,
                    title = stringResource(R.string.settings_export_data),
                    subtitle = stringResource(R.string.settings_export_data_subtitle),
                    onClick = { /* TODO */ },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SettingsActionRow(
                    icon = Icons.Default.CloudSync,
                    title = stringResource(R.string.settings_cloud_sync),
                    subtitle = stringResource(R.string.settings_cloud_sync_subtitle),
                    onClick = { /* TODO */ },
                    enabled = false,
                )
            }

            // About
            SettingsSection(title = stringResource(R.string.settings_section_about)) {
                SettingsInfoRow(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.settings_version),
                    value = stringResource(R.string.settings_version_value),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SettingsInfoRow(
                    icon = Icons.Default.Code,
                    title = stringResource(R.string.settings_open_source),
                    value = stringResource(R.string.settings_open_source_value),
                )
            }

            Text(
                text = stringResource(R.string.settings_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsAccountRow(
    profile: UserProfile?,
    onSignOut: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.AccountCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                profile?.username ?: "Not signed in",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                profile?.email ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(
            onClick = onSignOut,
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Text("Sign Out", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (enabled) {
            FilledTonalButton(onClick = onClick, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text(stringResource(R.string.action_go), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SettingsInfoRow(
    icon: ImageVector,
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
