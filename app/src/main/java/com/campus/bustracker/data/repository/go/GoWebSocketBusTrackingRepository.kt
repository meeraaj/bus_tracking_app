package com.campus.bustracker.data.repository.go

import com.campus.bustracker.BuildConfig
import com.campus.bustracker.data.local.RouteAssetDataSource
import com.campus.bustracker.data.model.BusLocation
import com.campus.bustracker.data.model.RouteData
import com.campus.bustracker.data.model.ServerStopCrossingRecord
import com.campus.bustracker.data.model.ServerStopCrossingsResponse
import com.campus.bustracker.data.remote.TokenManager
import com.campus.bustracker.data.repository.BusTrackingRepository
import com.campus.bustracker.domain.security.LocationPayloadValidator
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time WebSocket implementation of [BusTrackingRepository] powered by the Go backend:
 * - Students subscribe via `WSS /ws/routes/{routeId}/subscribe` with automatic exponential-backoff reconnect.
 * - Drivers stream 3-second GPS frames over a persistent `WSS /ws/routes/{routeId}/driver` connection.
 * - Every incoming and outgoing frame is verified against [LocationPayloadValidator].
 */
@Singleton
class GoWebSocketBusTrackingRepository @Inject constructor(
    private val routeAssetDataSource: RouteAssetDataSource,
    private val tokenManager: TokenManager
) : BusTrackingRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val wsClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Persistent WebSocket stream
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val restClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var driverWebSocket: WebSocket? = null

    @Volatile
    private var isDriverWsConnected: Boolean = false

    @Volatile
    private var activeDriverRouteId: String? = null

    @Volatile
    private var lastKnownDriverLocation: BusLocation = BusLocation()

    override suspend fun getRouteDetails(routeId: String): RouteData = withContext(Dispatchers.IO) {
        // Offline-first guardrail: load road-snapped route geometry from bundled assets first.
        routeAssetDataSource.getRouteData(routeId)
    }

    override fun observeBusLocation(routeId: String): Flow<BusLocation> = callbackFlow {
        val wsBaseUrl = resolveWsBaseUrl()
        val scope = this

        var currentSocket: WebSocket? = null
        var retryAttempt = 0

        var scheduleReconnect: (() -> Unit)? = null

        fun connectSubscriber() {
            if (!scope.isActive) return
            val token = tokenManager.getToken().orEmpty()
            val requestBuilder = Request.Builder()
                .url("$wsBaseUrl/ws/routes/$routeId/subscribe")
            if (token.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            currentSocket = wsClient.newWebSocket(
                requestBuilder.build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        retryAttempt = 0
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        runCatching {
                            json.decodeFromString<BusLocation>(text)
                        }.onSuccess { location ->
                            if (LocationPayloadValidator.validateForBroadcast(location).isSuccess) {
                                trySend(location)
                            }
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        scheduleReconnect?.invoke()
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (code != 1000) {
                            scheduleReconnect?.invoke()
                        }
                    }
                }
            )
        }

        scheduleReconnect = {
            if (scope.isActive) {
                scope.launch {
                    val backoffMs = (1000L shl retryAttempt.coerceAtMost(4)).coerceAtMost(15_000L)
                    retryAttempt++
                    delay(backoffMs)
                    connectSubscriber()
                }
            }
        }

        connectSubscriber()

        awaitClose {
            currentSocket?.close(1000, "Student unsubscribed")
            currentSocket = null
        }
    }

    override suspend fun updateDriverLocation(routeId: String, location: BusLocation): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val validation = LocationPayloadValidator.validateForBroadcast(location)
                require(validation.isSuccess) {
                    val ex = validation.exceptionOrNull()
                    "Telemetry rejected: ${ex?.message ?: "invalid payload"}"
                }
                val sanitized = validation.getOrThrow()
                lastKnownDriverLocation = sanitized

                // 1. Attempt WebSocket transmission first (low-latency streaming)
                val ws = synchronized(this@GoWebSocketBusTrackingRepository) {
                    if (driverWebSocket == null || activeDriverRouteId != routeId) {
                        getOrOpenDriverSocket(routeId)
                    }
                    driverWebSocket
                }

                val payload = json.encodeToString(sanitized)
                var sentOverWs = false
                if (ws != null && isDriverWsConnected) {
                    sentOverWs = ws.send(payload)
                }

                // 2. If WebSocket is not connected or send failed, FALLBACK TO HTTP REST PUT!
                // Guarantees delivery even if mobile carrier drops/blocks raw WS frames on port 8080.
                if (!sentOverWs) {
                    val restResult = sendLocationViaRest(routeId, sanitized)
                    if (restResult.isFailure) {
                        throw restResult.exceptionOrNull()
                            ?: IOException("Failed to transmit telemetry via both WebSocket and REST fallback")
                    }
                }
            }
        }

    suspend fun sendLocationViaRest(routeId: String, location: BusLocation): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val httpBase = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
                require(httpBase.isNotBlank()) { "BACKEND_BASE_URL is not configured" }

                val token = tokenManager.getToken().orEmpty()
                require(token.isNotBlank()) { "Missing driver authorization token. Please log in again." }

                val payload = json.encodeToString(location)
                val request = Request.Builder()
                    .url("$httpBase/api/v1/routes/$routeId/location")
                    .header("Authorization", "Bearer $token")
                    .put(payload.toRequestBody(jsonMediaType))
                    .build()

                restClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        throw IOException("Server rejected telemetry (HTTP ${response.code}): $body")
                    }
                }
            }
        }

    override suspend fun fetchServerCrossings(routeId: String): Result<List<ServerStopCrossingRecord>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val httpBase = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
                if (httpBase.isBlank()) return@runCatching emptyList()

                val url = if (routeId.isNotBlank()) {
                    "$httpBase/api/v1/logs/crossings?routeId=$routeId"
                } else {
                    "$httpBase/api/v1/logs/crossings"
                }

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                restClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Failed to fetch server crossings (HTTP ${response.code})")
                    }
                    val body = response.body?.string().orEmpty()
                    val parsed = json.decodeFromString<ServerStopCrossingsResponse>(body)
                    parsed.events ?: emptyList()
                }
            }
        }

    override suspend fun setDriverStatus(routeId: String, status: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val updated = lastKnownDriverLocation.copy(
                    status = status,
                    speedKmh = if (status == BusLocation.STATUS_OFFLINE) 0f else lastKnownDriverLocation.speedKmh,
                    timestamp = System.currentTimeMillis()
                )
                lastKnownDriverLocation = updated

                // Try WebSocket if open
                val ws = synchronized(this@GoWebSocketBusTrackingRepository) { driverWebSocket }
                val payload = json.encodeToString(updated)
                var sent = false
                if (ws != null && isDriverWsConnected) {
                    sent = ws.send(payload)
                }

                // If offline or websocket unavailable, also send via REST PUT
                if (!sent || status == BusLocation.STATUS_OFFLINE) {
                    sendLocationViaRest(routeId, updated)
                }

                if (status == BusLocation.STATUS_OFFLINE) {
                    closeDriverSocket()
                }
            }
        }

    @Synchronized
    private fun getOrOpenDriverSocket(routeId: String): WebSocket {
        val existing = driverWebSocket
        if (existing != null && activeDriverRouteId == routeId) {
            return existing
        }
        existing?.close(1000, "Switching route")

        val wsBaseUrl = resolveWsBaseUrl()
        val token = tokenManager.getToken().orEmpty()
        val requestBuilder = Request.Builder()
            .url("$wsBaseUrl/ws/routes/$routeId/driver")
        if (token.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val created = wsClient.newWebSocket(
            requestBuilder.build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.i("GoWebSocketBusTracking", "Driver WebSocket connected to $wsBaseUrl for $routeId")
                    synchronized(this@GoWebSocketBusTrackingRepository) {
                        if (driverWebSocket === webSocket) {
                            isDriverWsConnected = true
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w("GoWebSocketBusTracking", "Driver WebSocket failure: ${t.message} (HTTP ${response?.code})")
                    synchronized(this@GoWebSocketBusTrackingRepository) {
                        if (driverWebSocket === webSocket) {
                            isDriverWsConnected = false
                            driverWebSocket = null
                        }
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.i("GoWebSocketBusTracking", "Driver WebSocket closed: $reason (code $code)")
                    synchronized(this@GoWebSocketBusTrackingRepository) {
                        if (driverWebSocket === webSocket) {
                            isDriverWsConnected = false
                            driverWebSocket = null
                        }
                    }
                }
            }
        )
        driverWebSocket = created
        activeDriverRouteId = routeId
        return created
    }

    @Synchronized
    private fun closeDriverSocket() {
        isDriverWsConnected = false
        driverWebSocket?.close(1000, "Driver shift ended")
        driverWebSocket = null
        activeDriverRouteId = null
    }

    private fun resolveWsBaseUrl(): String {
        val explicitWs = BuildConfig.BACKEND_WS_URL.trimEnd('/')
        if (explicitWs.isNotEmpty()) return explicitWs

        val httpBase = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        return when {
            httpBase.startsWith("https://") -> "wss://" + httpBase.removePrefix("https://")
            httpBase.startsWith("http://") -> "ws://" + httpBase.removePrefix("http://")
            else -> httpBase
        }
    }
}
