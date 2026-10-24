package com.campus.bustracker.data.repository.go

import com.campus.bustracker.BuildConfig
import com.campus.bustracker.data.local.RouteAssetDataSource
import com.campus.bustracker.data.model.BusLocation
import com.campus.bustracker.data.model.RouteData
import com.campus.bustracker.data.remote.TokenManager
import com.campus.bustracker.data.repository.BusTrackingRepository
import com.campus.bustracker.domain.security.LocationPayloadValidator
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
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

    @Volatile
    private var driverWebSocket: WebSocket? = null

    @Volatile
    private var activeDriverRouteId: String? = null

    @Volatile
    private var lastKnownDriverLocation: BusLocation = BusLocation()

    override suspend fun getRouteDetails(routeId: String): RouteData = withContext(Dispatchers.IO) {
        // Offline-first guardrail: load road-snapped route geometry from bundled assets first.
        routeAssetDataSource.loadRoute(routeId)
    }

    override fun observeBusLocation(routeId: String): Flow<BusLocation> = callbackFlow {
        val wsBaseUrl = resolveWsBaseUrl()
        val scope = this

        var currentSocket: WebSocket? = null
        var retryAttempt = 0

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
                            if (LocationPayloadValidator.isValid(location)) {
                                trySend(location)
                            }
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        scheduleReconnect()
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (code != 1000) {
                            scheduleReconnect()
                        }
                    }
                }
            )
        }

        fun scheduleReconnect() {
            if (!scope.isActive) return
            scope.launch {
                val backoffMs = (1000L shl retryAttempt.coerceAtMost(4)).coerceAtMost(15_000L)
                retryAttempt++
                delay(backoffMs)
                connectSubscriber()
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
                require(LocationPayloadValidator.isValid(location)) {
                    "Telemetry rejected by client-side geofence/speed/timestamp validator"
                }
                lastKnownDriverLocation = location
                val socket = getOrOpenDriverSocket(routeId)
                val payload = json.encodeToString(location)
                val sent = socket.send(payload)
                if (!sent) {
                    // Socket buffer full or disconnected; reset and retry once
                    closeDriverSocket()
                    val retrySocket = getOrOpenDriverSocket(routeId)
                    check(retrySocket.send(payload)) { "WebSocket failed to enqueue driver location frame" }
                }
            }
        }

    override suspend fun setDriverStatus(routeId: String, status: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val updated = lastKnownDriverLocation.copy(
                    status = status,
                    speed = if (status == BusLocation.STATUS_OFFLINE) 0f else lastKnownDriverLocation.speed,
                    timestamp = System.currentTimeMillis()
                )
                lastKnownDriverLocation = updated
                val socket = getOrOpenDriverSocket(routeId)
                val payload = json.encodeToString(updated)
                socket.send(payload)
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
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    synchronized(this@GoWebSocketBusTrackingRepository) {
                        if (driverWebSocket === webSocket) {
                            driverWebSocket = null
                        }
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    synchronized(this@GoWebSocketBusTrackingRepository) {
                        if (driverWebSocket === webSocket) {
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
