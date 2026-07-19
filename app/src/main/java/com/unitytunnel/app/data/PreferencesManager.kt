package com.unitytunnel.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "unity_tunnel_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_BALANCE_SECONDS = longPreferencesKey("balance_seconds")
        val KEY_ADS_TODAY = intPreferencesKey("ads_today")
        val KEY_LAST_AD_RESET_DAY = stringPreferencesKey("last_ad_reset_day")
        val KEY_SELECTED_SERVER_ID = stringPreferencesKey("selected_server_id")
        val KEY_LAST_OPEN_AD_TIME = longPreferencesKey("last_open_ad_time")
        
        // Settings Toggles
        val KEY_AUTO_PROTOCOL = booleanPreferencesKey("auto_protocol")
        val KEY_CONNECT_ON_LAUNCH = booleanPreferencesKey("connect_on_launch")
        val KEY_LOW_DATA_MODE = booleanPreferencesKey("low_data_mode")
        val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val balanceSeconds: Flow<Long> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { prefs ->
            // Default 15-minute free baseline balance for new users = 900 seconds
            prefs[KEY_BALANCE_SECONDS] ?: 900L
        }

    val adsToday: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { prefs ->
            prefs[KEY_ADS_TODAY] ?: 0
        }

    val lastAdResetDay: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { prefs ->
            prefs[KEY_LAST_AD_RESET_DAY]
        }

    val selectedServerId: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { prefs ->
            prefs[KEY_SELECTED_SERVER_ID]
        }

    val lastOpenAdTime: Flow<Long> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { prefs ->
            prefs[KEY_LAST_OPEN_AD_TIME] ?: 0L
        }

    // Settings flows
    val autoProtocol: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { prefs ->
            prefs[KEY_AUTO_PROTOCOL] ?: true
        }

    val connectOnLaunch: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { prefs ->
            prefs[KEY_CONNECT_ON_LAUNCH] ?: false
        }

    val lowDataMode: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { prefs ->
            prefs[KEY_LOW_DATA_MODE] ?: false
        }

    val darkMode: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { prefs ->
            prefs[KEY_DARK_MODE] ?: true
        }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] ?: false
        }

    suspend fun saveBalanceSeconds(seconds: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BALANCE_SECONDS] = seconds
        }
    }

    suspend fun saveAdsToday(count: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ADS_TODAY] = count
        }
    }

    suspend fun saveLastAdResetDay(dayString: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_AD_RESET_DAY] = dayString
        }
    }

    suspend fun saveSelectedServerId(serverId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SELECTED_SERVER_ID] = serverId
        }
    }

    suspend fun saveLastOpenAdTime(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_OPEN_AD_TIME] = timestamp
        }
    }

    suspend fun setAutoProtocol(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_PROTOCOL] = enabled
        }
    }

    suspend fun setConnectOnLaunch(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CONNECT_ON_LAUNCH] = enabled
        }
    }

    suspend fun setLowDataMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOW_DATA_MODE] = enabled
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DARK_MODE] = enabled
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = completed
        }
    }
}
