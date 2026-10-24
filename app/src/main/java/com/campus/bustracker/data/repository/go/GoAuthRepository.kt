package com.campus.bustracker.data.repository.go

import com.campus.bustracker.BuildConfig
import com.campus.bustracker.data.remote.TokenManager
import com.campus.bustracker.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class DriverAuthReq(
    val routeId: String,
    val pin: String
)

@Serializable
private data class StudentAuthReq(
    val routeId: String,
    val rollNumber: String
)

@Serializable
private data class AuthResp(
    val token: String,
    val role: String,
    val routeId: String,
    val expiresAt: Long
)

/**
 * Authenticates drivers and students against the Go backend (`POST /api/v1/auth/driver`
 * and `POST /api/v1/auth/student`) and caches the signed HMAC-SHA256 JWT in [TokenManager].
 */
@Singleton
class GoAuthRepository @Inject constructor(
    private val tokenManager: TokenManager
) : AuthRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun authenticateDriver(email: String, secretKey: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
                require(baseUrl.isNotEmpty()) { "BACKEND_BASE_URL is not configured" }

                val routeId = extractRouteId(email)
                val payload = json.encodeToString(DriverAuthReq(routeId = routeId, pin = secretKey.trim()))
                val request = Request.Builder()
                    .url("$baseUrl/api/v1/auth/driver")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalArgumentException("Driver authentication failed (HTTP ${response.code})")
                    }
                    val body = response.body?.string()
                        ?: throw IllegalStateException("Empty auth response from Go backend")
                    val parsed = json.decodeFromString<AuthResp>(body)
                    tokenManager.saveToken(parsed.token)
                    parsed.routeId
                }
            }
        }

    override suspend fun authenticateStudent(rollNumber: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
                require(baseUrl.isNotEmpty()) { "BACKEND_BASE_URL is not configured" }

                val routeId = BuildConfig.DEFAULT_ROUTE_ID
                val payload = json.encodeToString(
                    StudentAuthReq(routeId = routeId, rollNumber = rollNumber.trim())
                )
                val request = Request.Builder()
                    .url("$baseUrl/api/v1/auth/student")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalArgumentException("Student authentication failed (HTTP ${response.code})")
                    }
                    val body = response.body?.string()
                        ?: throw IllegalStateException("Empty auth response from Go backend")
                    val parsed = json.decodeFromString<AuthResp>(body)
                    tokenManager.saveToken(parsed.token)
                    parsed.routeId
                }
            }
        }

    private fun extractRouteId(input: String): String {
        val digits = Regex("(\\d+)").find(input)?.groupValues?.get(1)
        return if (!digits.isNullOrBlank()) "route_$digits" else BuildConfig.DEFAULT_ROUTE_ID
    }
}
