package com.habitflow.app.presentation.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.habitflow.app.data.local.ThemePreferences
import com.habitflow.app.data.notification.NotificationHelper
import com.habitflow.app.presentation.ui.theme.HabitFlowTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themePreferences: ThemePreferences

    @Inject
    lateinit var notificationHelper: NotificationHelper

    // Request permission launcher for Android 13+ Notifications
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "Notification permission granted.")
            // Fire the Day 7 debug test notification
            notificationHelper.showTestNotification()
        } else {
            Log.d("MainActivity", "Notification permission denied.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ask for POST_NOTIFICATIONS on Android 13+ (API 33)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // For Android 12 and below, permission is granted at install time
            // We fire the debug test notification immediately for scaffolding
            notificationHelper.showTestNotification()
        }

        setContent {
            val isDarkMode by themePreferences.isDarkMode.collectAsState()
            val effectiveDarkMode = isDarkMode || isSystemInDarkTheme()

            HabitFlowTheme(darkTheme = effectiveDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    HabitFlowNavGraph(
                        navController = navController,
                    )
                }
            }
        }
    }
}
