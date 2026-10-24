package com.billbox.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private val appLockKey = booleanPreferencesKey("app_lock")
    private val sentKey = stringSetPreferencesKey("sent_reminders")
    private val onboardedKey = booleanPreferencesKey("onboarded")

    val appLock: Flow<Boolean> = context.dataStore.data.map { it[appLockKey] ?: false }
    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[onboardedKey] ?: false }

    suspend fun setAppLock(enabled: Boolean) = context.dataStore.edit { it[appLockKey] = enabled }
    suspend fun setOnboarded() = context.dataStore.edit { it[onboardedKey] = true }

    suspend fun sentReminderKeys(): Set<String> = context.dataStore.data.first()[sentKey] ?: emptySet()

    /** Keeps the newest 500 keys; older reminders can no longer fire anyway. */
    suspend fun markRemindersSent(keys: List<String>) = context.dataStore.edit {
        it[sentKey] = ((it[sentKey] ?: emptySet()) + keys).toList().takeLast(500).toSet()
    }
}
