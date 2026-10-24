package com.campus.bustracker.data.remote

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe persistent holder for the HMAC-SHA256 JWT issued by the Go backend
 * (`POST /api/v1/auth/driver` or `POST /api/v1/auth/student`).
 * Persists across process restarts so foreground services and resumed student sessions
 * always have their Bearer token ready.
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("go_backend_jwt_prefs", Context.MODE_PRIVATE)

    @Volatile
    private var cachedToken: String? = prefs.getString(KEY_JWT, null)

    fun saveToken(token: String) {
        cachedToken = token
        prefs.edit().putString(KEY_JWT, token).apply()
    }

    fun getToken(): String? = cachedToken

    fun clearToken() {
        cachedToken = null
        prefs.edit().remove(KEY_JWT).apply()
    }

    companion object {
        private const val KEY_JWT = "jwt_bearer_token"
    }
}
