package com.campus.bustracker.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.campus.bustracker.data.model.UserSession
import com.campus.bustracker.data.remote.TokenManager
import com.campus.bustracker.data.repository.SessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_session_prefs")

@Singleton
class DataStoreSessionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: TokenManager
) : SessionRepository {

    private object Keys {
        val ROLE = stringPreferencesKey("session_role")
        val ID = stringPreferencesKey("session_id")
        val ROUTE_ID = stringPreferencesKey("session_route_id")
    }

    override val sessionFlow: Flow<UserSession> = context.sessionDataStore.data.map { prefs ->
        val role = prefs[Keys.ROLE]
        val id = prefs[Keys.ID]
        val routeId = prefs[Keys.ROUTE_ID]

        when (role) {
            "DRIVER" -> if (id != null && routeId != null) UserSession.Driver(id, routeId) else UserSession.None
            "STUDENT" -> if (id != null && routeId != null) UserSession.Student(id, routeId) else UserSession.None
            else -> UserSession.None
        }
    }

    override suspend fun saveDriverSession(driverId: String, routeId: String) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.ROLE] = "DRIVER"
            prefs[Keys.ID] = driverId
            prefs[Keys.ROUTE_ID] = routeId
        }
    }

    override suspend fun saveStudentSession(identifier: String, routeId: String) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.ROLE] = "STUDENT"
            prefs[Keys.ID] = identifier
            prefs[Keys.ROUTE_ID] = routeId
        }
    }

    override suspend fun clearSession() {
        tokenManager.clearToken()
        context.sessionDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
