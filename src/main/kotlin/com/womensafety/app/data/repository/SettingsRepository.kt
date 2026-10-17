package com.womensafety.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_settings")

class SettingsRepository(private val context: Context) {
    companion object {
        private val CANCEL_DELAY_KEY = intPreferencesKey("cancel_delay_seconds")
        private val REPEAT_ALERT_COUNT_KEY = intPreferencesKey("repeat_alert_count")
        private val ONBOARDING_COMPLETE_KEY = booleanPreferencesKey("onboarding_complete")
        private val SMS_PERMISSION_KEY = booleanPreferencesKey("sms_permission_granted")
    }

    val cancelDelaySeconds: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[CANCEL_DELAY_KEY] ?: 10
    }

    val repeatAlertCount: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[REPEAT_ALERT_COUNT_KEY] ?: 0
    }

    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETE_KEY] ?: false
    }

    val smsPermissionGranted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SMS_PERMISSION_KEY] ?: false
    }

    suspend fun setCancelDelay(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[CANCEL_DELAY_KEY] = seconds
        }
    }

    suspend fun setRepeatAlertCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[REPEAT_ALERT_COUNT_KEY] = count
        }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETE_KEY] = complete
        }
    }

    suspend fun setSmsPermissionGranted(granted: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SMS_PERMISSION_KEY] = granted
        }
    }
}
