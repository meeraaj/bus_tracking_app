package com.campus.bustracker.data.repository

import android.content.Context
import com.campus.bustracker.BuildConfig
import com.campus.bustracker.data.repository.firebase.FirebaseAuthRepository
import com.campus.bustracker.data.repository.go.GoAuthRepository
import com.campus.bustracker.data.repository.mock.MockAuthRepository
import com.google.firebase.FirebaseApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Priority router for authentication:
 * 1. [GoAuthRepository] when `BuildConfig.BACKEND_BASE_URL` is configured.
 * 2. [FirebaseAuthRepository] when `google-services.json` is present.
 * 3. [MockAuthRepository] fallback when running locally without backend credentials.
 */
@Singleton
class SmartAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val goAuthRepository: GoAuthRepository,
    private val firebaseAuthRepository: FirebaseAuthRepository,
    private val mockAuthRepository: MockAuthRepository
) : AuthRepository {

    private val activeDelegate: AuthRepository
        get() = when {
            BuildConfig.BACKEND_BASE_URL.isNotBlank() -> goAuthRepository
            FirebaseApp.getApps(context).isNotEmpty() -> firebaseAuthRepository
            else -> mockAuthRepository
        }

    override suspend fun authenticateDriver(email: String, secretKey: String): Result<String> {
        return activeDelegate.authenticateDriver(email, secretKey)
    }

    override suspend fun authenticateStudent(rollNumber: String): Result<String> {
        return activeDelegate.authenticateStudent(rollNumber)
    }
}
