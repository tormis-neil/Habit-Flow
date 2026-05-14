package com.habitflow.app.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    companion object {
        const val EXTRA_HABIT_ID = "EXTRA_HABIT_ID"
        const val EXTRA_HABIT_TITLE = "EXTRA_HABIT_TITLE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L)
        val habitTitle = intent.getStringExtra(EXTRA_HABIT_TITLE) ?: "your habit"

        if (habitId != -1L) {
            notificationHelper.showHabitReminder(habitId, habitTitle)
        }
    }
}
