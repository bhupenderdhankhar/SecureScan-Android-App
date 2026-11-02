package com.example.securescan.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val AUTO_ENHANCE = booleanPreferencesKey("auto_enhance")
        val DEFAULT_PASSWORD_ENABLED = booleanPreferencesKey("default_password_enabled")
        val DEFAULT_PASSWORD_VALUE = stringPreferencesKey("default_password_value")
        val ALWAYS_PASSWORD = booleanPreferencesKey("always_password")
        val HIGH_QUALITY = booleanPreferencesKey("high_quality")
        val SHOW_IMAGES = booleanPreferencesKey("show_images")
    }

    val darkMode: Flow<Boolean> = context.dataStore.data.map { it[DARK_MODE] ?: false }
    val autoEnhance: Flow<Boolean> = context.dataStore.data.map { it[AUTO_ENHANCE] ?: true }
    val defaultPassword: Flow<String> = context.dataStore.data.map { it[DEFAULT_PASSWORD_VALUE] ?: "" }
    val defaultPasswordEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[DEFAULT_PASSWORD_ENABLED] ?: false }
    val alwaysPassword: Flow<Boolean> = context.dataStore.data.map { it[ALWAYS_PASSWORD] ?: false }
    val highQuality: Flow<Boolean> = context.dataStore.data.map { it[HIGH_QUALITY] ?: true }
    val showImages: Flow<Boolean> = context.dataStore.data.map { it[SHOW_IMAGES] ?: false }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[DARK_MODE] = enabled }
    }

    suspend fun setAutoEnhance(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_ENHANCE] = enabled }
    }

    suspend fun setDefaultPassword(password: String) {
        context.dataStore.edit {
            it[DEFAULT_PASSWORD_VALUE] = password
            it[DEFAULT_PASSWORD_ENABLED] = password.isNotBlank()
        }
    }

    suspend fun setDefaultPasswordEnabled(enabled: Boolean) {
        context.dataStore.edit { it[DEFAULT_PASSWORD_ENABLED] = enabled }
    }

    suspend fun setAlwaysPassword(enabled: Boolean) {
        context.dataStore.edit { it[ALWAYS_PASSWORD] = enabled }
    }

    suspend fun setHighQuality(enabled: Boolean) {
        context.dataStore.edit { it[HIGH_QUALITY] = enabled }
    }

    suspend fun setShowImages(enabled: Boolean) {
        context.dataStore.edit { it[SHOW_IMAGES] = enabled }
    }
}
