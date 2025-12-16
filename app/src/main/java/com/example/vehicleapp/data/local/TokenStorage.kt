package com.example.vehicleapp.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.vehicleapp.data.model.AuthTokens
import com.example.vehicleapp.data.remote.AuthInterceptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "auth_prefs"
)

class TokenStorage(
    private val context: Context
) : AuthInterceptor.TokenProvider {

    private val accessTokenKey = stringPreferencesKey("access_token")
    private val refreshTokenKey = stringPreferencesKey("refresh_token")

    val tokensFlow: Flow<AuthTokens?> = context.authDataStore.data.map { preferences ->
        val access = preferences[accessTokenKey]
        val refresh = preferences[refreshTokenKey]
        if (access != null && refresh != null) {
            AuthTokens(access, refresh)
        } else {
            null
        }
    }

    override suspend fun accessToken(): String? = tokensFlow.firstOrNull()?.accessToken

    suspend fun saveTokens(tokens: AuthTokens) {
        context.authDataStore.edit { preferences ->
            preferences[accessTokenKey] = tokens.accessToken
            preferences[refreshTokenKey] = tokens.refreshToken
        }
    }

    suspend fun clear() {
        context.authDataStore.edit { preferences ->
            preferences.remove(accessTokenKey)
            preferences.remove(refreshTokenKey)
        }
    }
}
