package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "partner_bank_theme_prefs"
)

class ThemePreferences(private val context: Context) {

    val darkModeOverrideFlow: Flow<Boolean?> = context.themeDataStore.data.map { prefs ->
        prefs[KEY_DARK_MODE]
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.themeDataStore.edit { prefs ->
            prefs[KEY_DARK_MODE] = enabled
        }
    }

    companion object {
        private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode_enabled")
    }
}
