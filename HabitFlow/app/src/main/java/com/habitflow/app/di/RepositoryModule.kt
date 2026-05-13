package com.habitflow.app.di

import com.habitflow.app.data.repository.AuthRepositoryImpl
import com.habitflow.app.data.repository.HabitRepositoryImpl
import com.habitflow.app.data.repository.UserRepositoryImpl
import com.habitflow.app.domain.repository.AuthRepository
import com.habitflow.app.domain.repository.HabitRepository
import com.habitflow.app.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    // Day 5: HabitRepositoryImpl replaces RoomHabitRepository.
    // All ViewModels keep using HabitRepository (the interface) — no changes needed there.
    @Binds @Singleton
    abstract fun bindHabitRepository(impl: HabitRepositoryImpl): HabitRepository

    @Binds @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository
}

