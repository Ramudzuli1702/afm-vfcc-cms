package com.afmvfcc.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "afm_prefs")

class Prefs(private val context: Context) {

    companion object {
        val KEY_SERVER_IP   = stringPreferencesKey("server_ip")
        val KEY_SERVER_PORT = stringPreferencesKey("server_port")
        val KEY_TOKEN       = stringPreferencesKey("token")
        val KEY_USER_NAME   = stringPreferencesKey("user_name")
        val KEY_DEVICE_ID   = stringPreferencesKey("device_id")
        val KEY_GUESTS_JSON = stringPreferencesKey("guests_json")
    }

    val serverIp: Flow<String>   = context.dataStore.data.map { it[KEY_SERVER_IP]   ?: "" }
    val serverPort: Flow<String> = context.dataStore.data.map { it[KEY_SERVER_PORT] ?: "8080" }
    val token: Flow<String>      = context.dataStore.data.map { it[KEY_TOKEN]       ?: "" }
    val userName: Flow<String>   = context.dataStore.data.map { it[KEY_USER_NAME]   ?: "" }
    val deviceId: Flow<String>   = context.dataStore.data.map { it[KEY_DEVICE_ID]   ?: generateDeviceId() }
    val guestsJson: Flow<String> = context.dataStore.data.map { it[KEY_GUESTS_JSON] ?: "[]" }

    suspend fun saveServerAddress(ip: String, port: String) {
        context.dataStore.edit {
            it[KEY_SERVER_IP]   = ip.trim()
            it[KEY_SERVER_PORT] = port.trim().ifEmpty { "8080" }
        }
    }

    suspend fun saveAuth(token: String, name: String) {
        context.dataStore.edit {
            it[KEY_TOKEN]     = token
            it[KEY_USER_NAME] = name
        }
    }

    suspend fun logout() {
        context.dataStore.edit {
            it[KEY_TOKEN]     = ""
            it[KEY_USER_NAME] = ""
        }
    }

    suspend fun saveGuestsJson(json: String) {
        context.dataStore.edit {
            it[KEY_GUESTS_JSON] = json
        }
    }

    suspend fun ensureDeviceId() {
        context.dataStore.edit { prefs ->
            if (prefs[KEY_DEVICE_ID].isNullOrEmpty()) {
                prefs[KEY_DEVICE_ID] = generateDeviceId()
            }
        }
    }

    private fun generateDeviceId(): String =
        "android_" + java.util.UUID.randomUUID().toString().take(12)
}