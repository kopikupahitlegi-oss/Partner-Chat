package com.example.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "partner_bank_notification_prefs"
)

data class NotificationSettings(
    val chatNotificationsEnabled: Boolean = true,
    val callNotificationsEnabled: Boolean = true,
    val backgroundServiceEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val chatToneId: String = ToneCatalog.DEFAULT_CHAT_TONE_ID,
    val chatToneTitle: String = ToneCatalog.DEFAULT_CHAT_TONE_TITLE,
    val callToneId: String = ToneCatalog.DEFAULT_CALL_TONE_ID,
    val callToneTitle: String = ToneCatalog.DEFAULT_CALL_TONE_TITLE
) {
    val shouldRunBackgroundListener: Boolean
        get() = backgroundServiceEnabled && (chatNotificationsEnabled || callNotificationsEnabled)
}

class NotificationPreferences(private val context: Context) {

    val settingsFlow: Flow<NotificationSettings> = context.notificationDataStore.data.map { prefs ->
        NotificationSettings(
            chatNotificationsEnabled = prefs[KEY_CHAT_ENABLED] ?: true,
            callNotificationsEnabled = prefs[KEY_CALL_ENABLED] ?: true,
            backgroundServiceEnabled = prefs[KEY_BACKGROUND_SERVICE_ENABLED] ?: true,
            vibrationEnabled = prefs[KEY_VIBRATION_ENABLED] ?: true,
            chatToneId = prefs[KEY_CHAT_TONE_ID] ?: ToneCatalog.DEFAULT_CHAT_TONE_ID,
            chatToneTitle = prefs[KEY_CHAT_TONE_TITLE] ?: ToneCatalog.DEFAULT_CHAT_TONE_TITLE,
            callToneId = prefs[KEY_CALL_TONE_ID] ?: ToneCatalog.DEFAULT_CALL_TONE_ID,
            callToneTitle = prefs[KEY_CALL_TONE_TITLE] ?: ToneCatalog.DEFAULT_CALL_TONE_TITLE
        )
    }

    suspend fun setChatNotificationsEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { prefs ->
            prefs[KEY_CHAT_ENABLED] = enabled
        }
    }

    suspend fun setCallNotificationsEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { prefs ->
            prefs[KEY_CALL_ENABLED] = enabled
        }
    }

    suspend fun setBackgroundServiceEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { prefs ->
            prefs[KEY_BACKGROUND_SERVICE_ENABLED] = enabled
        }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { prefs ->
            prefs[KEY_VIBRATION_ENABLED] = enabled
        }
    }

    suspend fun setChatTone(toneId: String, title: String) {
        context.notificationDataStore.edit { prefs ->
            prefs[KEY_CHAT_TONE_ID] = toneId
            prefs[KEY_CHAT_TONE_TITLE] = title
        }
    }

    suspend fun setCallTone(toneId: String, title: String) {
        context.notificationDataStore.edit { prefs ->
            prefs[KEY_CALL_TONE_ID] = toneId
            prefs[KEY_CALL_TONE_TITLE] = title
        }
    }

    companion object {
        private val KEY_CHAT_ENABLED = booleanPreferencesKey("chat_notifications_enabled")
        private val KEY_CALL_ENABLED = booleanPreferencesKey("call_notifications_enabled")
        private val KEY_BACKGROUND_SERVICE_ENABLED = booleanPreferencesKey("background_service_enabled")
        private val KEY_VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        private val KEY_CHAT_TONE_ID = stringPreferencesKey("chat_tone_id")
        private val KEY_CHAT_TONE_TITLE = stringPreferencesKey("chat_tone_title")
        private val KEY_CALL_TONE_ID = stringPreferencesKey("call_tone_id")
        private val KEY_CALL_TONE_TITLE = stringPreferencesKey("call_tone_title")
    }
}
