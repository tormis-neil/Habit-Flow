package com.habitflow.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * FEATURE A — Architecture & Structure
 *
 * HabitFlowApplication is the entry point of the app.
 * Android creates one instance of this class when the app starts,
 * and it lives for the entire lifetime of the app.
 */
@HiltAndroidApp
class HabitFlowApplication : Application()
