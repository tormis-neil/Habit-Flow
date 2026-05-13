package com.habitflow.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_prefs")

/**
 * Day 6 — Sync Preferences
 *
 * Manages the `lastSyncTimestamp` using Jetpack DataStore.
 * We store this timestamp so that when we pull updates from Firestore,
 * we only query for documents updated *after* this time, saving bandwidth.
 */
@Singleton
class SyncPreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.syncDataStore

    companion object {
        private val LAST_SYNC_TIME_KEY = longPreferencesKey("last_sync_time")
    }

    /** Stream of the last sync time (default: 0 if never synced) */
    val lastSyncTime: Flow<Long> = dataStore.data.map { preferences ->
        preferences[LAST_SYNC_TIME_KEY] ?: 0L
    }

    /** Convenience method to get the value directly */
    suspend fun getLastSyncTime(): Long = lastSyncTime.first()

    /** Updates the last sync timestamp */
    suspend fun updateLastSyncTime(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_SYNC_TIME_KEY] = timestamp
        }
    }
}
