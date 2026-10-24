package com.campus.bustracker.data.repository

import com.campus.bustracker.data.model.UserSession
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    val sessionFlow: Flow<UserSession>
    suspend fun saveDriverSession(driverId: String, routeId: String)
    suspend fun saveStudentSession(identifier: String, routeId: String)
    suspend fun clearSession()
}
