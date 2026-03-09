package com.rgbpos.bigs.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "bigs_prefs")

object TokenStore {
    private val TOKEN_KEY = stringPreferencesKey("auth_token")
    private val USER_NAME_KEY = stringPreferencesKey("user_name")
    private val USER_ROLE_KEY = stringPreferencesKey("user_role")
    private val PRINTER_MAC_KEY = stringPreferencesKey("printer_mac")
    private val PRINTER_NAME_KEY = stringPreferencesKey("printer_name")

    suspend fun saveLogin(context: Context, token: String, userName: String, role: String) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
            prefs[USER_NAME_KEY] = userName
            prefs[USER_ROLE_KEY] = role
        }
    }

    suspend fun getToken(context: Context): String? {
        return context.dataStore.data.map { it[TOKEN_KEY] }.first()
    }

    suspend fun getUserName(context: Context): String? {
        return context.dataStore.data.map { it[USER_NAME_KEY] }.first()
    }

    suspend fun savePrinter(context: Context, mac: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[PRINTER_MAC_KEY] = mac
            prefs[PRINTER_NAME_KEY] = name
        }
    }

    suspend fun getPrinterMac(context: Context): String? {
        return context.dataStore.data.map { it[PRINTER_MAC_KEY] }.first()
    }

    suspend fun getPrinterName(context: Context): String? {
        return context.dataStore.data.map { it[PRINTER_NAME_KEY] }.first()
    }

    suspend fun clear(context: Context) {
        context.dataStore.edit { it.clear() }
    }
}
