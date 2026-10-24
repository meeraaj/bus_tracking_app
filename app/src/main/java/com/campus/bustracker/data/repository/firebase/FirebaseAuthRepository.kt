package com.campus.bustracker.data.repository.firebase

import com.campus.bustracker.data.remote.RtdbPaths
import com.campus.bustracker.data.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production Firebase Authentication + RBAC Driver Allow-List verification.
 *
 * - Drivers authenticate with Email + Secret Key (Password) and must have an active
 *   record under `/drivers/{uid}` (`active == true`).
 * - Students authenticate via Firebase Anonymous Auth (`auth != null`), granting read-only
 *   access to `/routes/{routeId}` per `firebase/database.rules.json`.
 */
@Singleton
class FirebaseAuthRepository @Inject constructor() : AuthRepository {

    private val rollNumberRegex = Regex("^[a-zA-Z0-9_-]{0,24}$")

    override suspend fun authenticateDriver(email: String, secretKey: String): Result<String> {
        val trimmedEmail = email.trim()
        val trimmedKey = secretKey.trim()

        if (trimmedEmail.isEmpty() || !trimmedEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Please enter a valid driver email"))
        }
        if (trimmedKey.length < 6) {
            return Result.failure(
                IllegalArgumentException("Firebase driver access key/password must be at least 6 characters")
            )
        }

        return runCatching {
            val auth = FirebaseAuth.getInstance()
            val authResult = auth.signInWithEmailAndPassword(trimmedEmail, trimmedKey).await()
            val uid = authResult.user?.uid
                ?: throw SecurityException("Driver authentication failed: no UID returned")

            // RBAC Verification: Confirm driver is allow-listed and active in /drivers/{uid}
            val driverSnap = FirebaseDatabase.getInstance()
                .getReference(RtdbPaths.driverProfile(uid))
                .get()
                .await()

            if (!driverSnap.exists()) {
                auth.signOut()
                throw SecurityException(
                    "Access denied: UID '$uid' is not registered in /drivers allow-list"
                )
            }

            val isActive = driverSnap.child("active").getValue(Boolean::class.java) ?: false
            if (!isActive) {
                auth.signOut()
                throw SecurityException("Access denied: Driver account is marked inactive")
            }

            uid
        }
    }

    override suspend fun authenticateStudent(rollNumber: String): Result<String> {
        val trimmed = rollNumber.trim()
        if (!rollNumberRegex.matches(trimmed)) {
            return Result.failure(
                IllegalArgumentException("Roll number may only contain letters, digits, '-' or '_'")
            )
        }

        return runCatching {
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser ?: auth.signInAnonymously().await().user
            val uid = user?.uid ?: throw SecurityException("Student anonymous session failed")
            if (trimmed.isNotEmpty()) "${uid}_$trimmed" else uid
        }
    }
}
