package com.sleek.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences>
        by preferencesDataStore(name = "sleek_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_DARK_THEME    = booleanPreferencesKey("dark_theme")
        private val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        private val KEY_SLEEP_MODE    = booleanPreferencesKey("sleep_mode")
    }

    val isDarkTheme: Flow<Boolean> =
        context.settingsStore.data.map { it[KEY_DARK_THEME] ?: true }

    val notificationsEnabled: Flow<Boolean> =
        context.settingsStore.data.map { it[KEY_NOTIFICATIONS] ?: true }

    val sleepModeEnabled: Flow<Boolean> =
        context.settingsStore.data.map { it[KEY_SLEEP_MODE] ?: false }

    suspend fun setDarkTheme(dark: Boolean) {
        context.settingsStore.edit { it[KEY_DARK_THEME] = dark }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[KEY_NOTIFICATIONS] = enabled }
    }

    suspend fun setSleepModeEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[KEY_SLEEP_MODE] = enabled }
    }
}
