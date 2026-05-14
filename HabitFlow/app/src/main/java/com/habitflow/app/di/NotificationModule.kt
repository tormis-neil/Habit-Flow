package com.habitflow.app.di

import com.habitflow.app.data.notification.ReminderScheduler
import com.habitflow.app.data.notification.ReminderSchedulerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {

    @Binds
    abstract fun bindReminderScheduler(
        reminderSchedulerImpl: ReminderSchedulerImpl
    ): ReminderScheduler
}
