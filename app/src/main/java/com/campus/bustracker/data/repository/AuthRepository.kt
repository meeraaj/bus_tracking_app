package com.campus.bustracker.data.repository

interface AuthRepository {
    suspend fun authenticateDriver(email: String, secretKey: String): Result<String>
    suspend fun authenticateStudent(rollNumber: String): Result<String>
}
