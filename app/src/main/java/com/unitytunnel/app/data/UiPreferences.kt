package com.unitytunnel.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UiPreferences(private val context: Context) {
    companion object {
        val KEY_SERVERS_JSON = stringPreferencesKey("servers_json")
        val KEY_CUSTOM_DNS = stringPreferencesKey("custom_dns")
    }

    val customDns: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_DNS] ?: "ISP Default"
    }

    suspend fun setCustomDns(dns: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_DNS] = dns
        }
    }

    val serversJson: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_SERVERS_JSON]
    }

    suspend fun setServersJson(json: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVERS_JSON] = json
        }
    }
}
