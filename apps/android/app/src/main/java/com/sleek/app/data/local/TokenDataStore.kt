package com.sleek.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sleek_prefs")

@Singleton
class TokenDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_TOKEN     = stringPreferencesKey("auth_token")
        private val KEY_USER_ID   = stringPreferencesKey("user_id")
        private val KEY_USERNAME  = stringPreferencesKey("username")
        private val KEY_EMAIL     = stringPreferencesKey("user_email")
        private val KEY_AVATAR    = stringPreferencesKey("user_avatar")   // ← NEW
        private val KEY_TAG       = stringPreferencesKey("user_tag")      // ← NEW
    }

    val token:     Flow<String?> = context.dataStore.data.map { it[KEY_TOKEN] }
    val userId:    Flow<String?> = context.dataStore.data.map { it[KEY_USER_ID] }
    val username:  Flow<String?> = context.dataStore.data.map { it[KEY_USERNAME] }
    val email:     Flow<String?> = context.dataStore.data.map { it[KEY_EMAIL] }
    val avatarUrl: Flow<String?> = context.dataStore.data.map { it[KEY_AVATAR] }
    val tag:       Flow<String?> = context.dataStore.data.map { it[KEY_TAG] }

    suspend fun save(token: String, userId: String, username: String?, email: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TOKEN]    = token
            prefs[KEY_USER_ID]  = userId
            username?.let { prefs[KEY_USERNAME] = it }
            email?.let    { prefs[KEY_EMAIL]    = it }
        }
    }

    /** Call after every successful getMe() to keep profile data fresh in cache */
    suspend fun saveProfile(avatarUrl: String?, tag: String?, username: String?, email: String?) {
        context.dataStore.edit { prefs ->
            avatarUrl?.let { prefs[KEY_AVATAR]   = it }
            tag?.let       { prefs[KEY_TAG]       = it }
            username?.let  { prefs[KEY_USERNAME]  = it }
            email?.let     { prefs[KEY_EMAIL]     = it }
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
