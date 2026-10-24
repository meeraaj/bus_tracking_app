package com.campus.bustracker.data.repository.mock

import com.campus.bustracker.data.repository.AuthRepository
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockAuthRepository @Inject constructor() : AuthRepository {

    override suspend fun authenticateDriver(email: String, secretKey: String): Result<String> {
        delay(600) // Simulate network roundtrip
        val trimmedEmail = email.trim()
        val trimmedKey = secretKey.trim()

        if (trimmedEmail.isEmpty() || !trimmedEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Please enter a valid driver email"))
        }
        if (trimmedKey.length < 4) {
            return Result.failure(IllegalArgumentException("Access key must be at least 4 digits"))
        }

        // Mock acceptance: accept any validly formatted credentials
        val driverUid = "driver_" + trimmedEmail.substringBefore("@").replace(".", "_")
        return Result.success(driverUid)
    }

    override suspend fun authenticateStudent(rollNumber: String): Result<String> {
        delay(400) // Simulate fast check
        val trimmed = rollNumber.trim()
        val studentUid = if (trimmed.isEmpty()) {
            "guest_" + System.currentTimeMillis().toString().takeLast(6)
        } else {
            "student_" + trimmed.lowercase()
        }
        return Result.success(studentUid)
    }
}
