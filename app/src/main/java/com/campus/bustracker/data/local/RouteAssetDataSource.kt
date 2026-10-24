package com.campus.bustracker.data.local

import android.content.Context
import com.campus.bustracker.data.model.RouteData
import com.campus.bustracker.data.model.RouteStop
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class LatLngDto(
    val lat: Double,
    val lng: Double
)

@Serializable
private data class RouteAssetDto(
    val routeId: String,
    val routeName: String,
    val polylinePoints: List<LatLngDto> = emptyList(),
    val stops: List<RouteStop> = emptyList()
)

@Singleton
class RouteAssetDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val memoryCache = ConcurrentHashMap<String, RouteData>()
    private val mutex = Mutex()
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Loads route details from local assets (app/src/main/assets/routes/<routeId>.json).
     * Read once and cached in memory. Strictly avoids any remote routing / OSRM API calls (Guardrail 3).
     */
    suspend fun getRouteData(routeId: String): RouteData {
        memoryCache[routeId]?.let { return it }

        return mutex.withLock {
            memoryCache[routeId]?.let { return@withLock it }

            val assetPath = "routes/$routeId.json"
            val jsonString = try {
                context.assets.open(assetPath).bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                throw IllegalStateException("Route asset not found for routeId: $routeId at assets/$assetPath", e)
            }

            val dto = jsonParser.decodeFromString<RouteAssetDto>(jsonString)
            val routeData = RouteData(
                routeId = dto.routeId,
                routeName = dto.routeName,
                polylinePoints = dto.polylinePoints.map { Pair(it.lat, it.lng) },
                stops = dto.stops.sortedBy { it.sequenceOrder }
            )
            memoryCache[routeId] = routeData
            routeData
        }
    }
}
