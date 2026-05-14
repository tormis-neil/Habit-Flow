package com.habitflow.app.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.habitflow.app.domain.model.Habit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

interface ReminderScheduler {
    fun scheduleReminder(habit: Habit)
    fun cancelReminder(habitId: Long)
}

@Singleton
class ReminderSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ReminderScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun scheduleReminder(habit: Habit) {
        // If reminders are disabled, no time is set, or the habit is paused, cancel any existing alarms.
        if (!habit.isEnabled || !habit.reminderEnabled || habit.reminderTime == null) {
            cancelReminder(habit.id)
            return
        }

        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val reminderTime = try {
            LocalTime.parse(habit.reminderTime, timeFormatter)
        } catch (e: Exception) {
            cancelReminder(habit.id)
            return
        }

        val now = LocalDateTime.now()
        var alarmTime = LocalDateTime.of(now.toLocalDate(), reminderTime)

        // If the time has already passed today, schedule for tomorrow
        if (alarmTime.isBefore(now) || alarmTime.isEqual(now)) {
            alarmTime = alarmTime.plusDays(1)
        }

        val alarmMillis = alarmTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_HABIT_ID, habit.id)
            putExtra(ReminderReceiver.EXTRA_HABIT_TITLE, habit.title)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            habit.id.toInt(), // Use habit ID as the request code to uniquely identify the PendingIntent
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use inexact repeating for battery efficiency, as per guidelines
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            alarmMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    override fun cancelReminder(habitId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            habitId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
