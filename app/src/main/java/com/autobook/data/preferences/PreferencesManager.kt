package com.openloud.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {

    companion object {
        private val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        private val SKIP_FOOTNOTES = booleanPreferencesKey("skip_footnotes")
        private val SKIP_PAGE_NUMBERS = booleanPreferencesKey("skip_page_numbers")
        private val PAUSE_BETWEEN_CHAPTERS_MS = intPreferencesKey("pause_between_chapters_ms")
        private val THEME_MODE = intPreferencesKey("theme_mode") // 0: System, 1: Light, 2: Dark
    }

    val playbackSpeed: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[PLAYBACK_SPEED] ?: 1.0f
    }

    val skipFootnotes: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SKIP_FOOTNOTES] ?: true
    }

    val skipPageNumbers: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SKIP_PAGE_NUMBERS] ?: true
    }

    val pauseBetweenChaptersMs: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PAUSE_BETWEEN_CHAPTERS_MS] ?: 2000
    }

    val themeMode: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: 0
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[PLAYBACK_SPEED] = speed
        }
    }

    suspend fun setSkipFootnotes(skip: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SKIP_FOOTNOTES] = skip
        }
    }

    suspend fun setSkipPageNumbers(skip: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SKIP_PAGE_NUMBERS] = skip
        }
    }

    suspend fun setPauseBetweenChaptersMs(pauseMs: Int) {
        context.dataStore.edit { preferences ->
            preferences[PAUSE_BETWEEN_CHAPTERS_MS] = pauseMs
        }
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }
}
