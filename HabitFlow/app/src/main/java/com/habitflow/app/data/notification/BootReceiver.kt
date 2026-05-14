package com.habitflow.app.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.habitflow.app.data.local.dao.HabitDao
import com.habitflow.app.data.local.entity.toHabit
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var dao: HabitDao

    @Inject
    lateinit var scheduler: ReminderScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            scope.launch {
                // Fetch all habits that have reminders enabled
                val habits = dao.getAllHabitsSync()
                habits.forEach { entity ->
                    if (entity.reminderEnabled && entity.reminderTime != null) {
                        scheduler.scheduleReminder(entity.toHabit())
                    }
                }
            }
        }
    }
}
