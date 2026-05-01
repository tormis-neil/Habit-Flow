package com.habitflow.app.presentation.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.habitflow.app.HabitFlowApplication
import com.habitflow.app.presentation.ui.theme.HabitFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as HabitFlowApplication
        val repository = app.repository
        val themePreferences = app.themePreferences

        setContent {
            val isDarkMode by themePreferences.isDarkMode.collectAsState()
            val effectiveDarkMode = isDarkMode || isSystemInDarkTheme()

            HabitFlowTheme(darkTheme = effectiveDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    HabitFlowNavGraph(
                        navController = navController,
                        repository = repository,
                        themePreferences = themePreferences,
                    )
                }
            }
        }
    }
}

