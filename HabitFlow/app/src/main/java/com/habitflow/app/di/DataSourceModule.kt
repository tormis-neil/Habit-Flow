package com.habitflow.app.di

import com.habitflow.app.data.remote.FirestoreHabitDataSource
import com.habitflow.app.data.remote.FirestoreLogDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Singleton

/**
 * Day 4 — Hilt module that provides the two Firestore data sources.
 *
 * Both sources are @Singleton — there should be exactly one instance of each
 * in the app, sharing the same FirebaseFirestore instance from NetworkModule.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataSourceModule {

    @Provides
    @Singleton
    fun provideFirestoreHabitDataSource(
        firestore: FirebaseFirestore,
    ): FirestoreHabitDataSource = FirestoreHabitDataSource(firestore)

    @Provides
    @Singleton
    fun provideFirestoreLogDataSource(
        firestore: FirebaseFirestore,
    ): FirestoreLogDataSource = FirestoreLogDataSource(firestore)
}
