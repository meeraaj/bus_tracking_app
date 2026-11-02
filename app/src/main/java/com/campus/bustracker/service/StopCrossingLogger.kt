package com.campus.bustracker.service

import android.content.Context
import android.util.Log
import com.campus.bustracker.data.local.RouteAssetDataSource
import com.campus.bustracker.data.model.RouteStop
import com.campus.bustracker.domain.geo.GeoMath
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Event recorded when the bus crosses or arrives within the arrival radius of a pick-up point / stop.
 */
data class StopCrossingEvent(
    val stopId: String,
    val stopName: String,
    val sequenceOrder: Int,
    val arrivalTimeMillis: Long,
    val distanceMeters: Double,
    val speedKmh: Float,
    val latitude: Double,
    val longitude: Double,
    val formattedTime: String
)

/**
 * Monitors live driver GPS coordinates against the stops for the active route.
 * Detects stop arrivals, writes persistent log entries to disk (internal storage + Logcat),
 * and exposes live crossing events via StateFlow to the UI.
 */
@Singleton
class StopCrossingLogger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val routeAssetDataSource: RouteAssetDataSource
) {
    private val tag = "StopCrossingLogger"
    private val arrivalRadiusMeters = 80.0 // 80m corridor threshold matching server-side detection

    private val crossedStopIds = mutableSetOf<String>()
    private var cachedStops: List<RouteStop> = emptyList()
    private var activeRouteId: String = ""

    private val _crossingEvents = MutableStateFlow<List<StopCrossingEvent>>(emptyList())
    val crossingEvents: StateFlow<List<StopCrossingEvent>> = _crossingEvents.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val logDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private val logFile: File by lazy {
        File(context.filesDir, "stop_crossings.log")
    }

    suspend fun prepareForRoute(routeId: String) {
        if (activeRouteId != routeId || cachedStops.isEmpty()) {
            activeRouteId = routeId
            crossedStopIds.clear()
            runCatching {
                val route = routeAssetDataSource.getRouteData(routeId)
                cachedStops = route.stops.sortedBy { it.sequenceOrder }
                writeDiskLog("=== START TRACKING SESSION: Route $routeId (${cachedStops.size} stops registered) ===")
            }
        }
    }

    fun onLocationUpdate(latitude: Double, longitude: Double, speedKmh: Float, timestamp: Long) {
        if (cachedStops.isEmpty()) return

        for (stop in cachedStops) {
            if (crossedStopIds.contains(stop.stopId)) continue

            val distance = GeoMath.haversineDistanceMeters(
                latitude, longitude,
                stop.latitude, stop.longitude
            )

            if (distance <= arrivalRadiusMeters) {
                crossedStopIds.add(stop.stopId)

                val formattedTime = timeFormat.format(Date(timestamp))
                val logDate = logDateFormat.format(Date(timestamp))

                val event = StopCrossingEvent(
                    stopId = stop.stopId,
                    stopName = stop.name,
                    sequenceOrder = stop.sequenceOrder,
                    arrivalTimeMillis = timestamp,
                    distanceMeters = distance,
                    speedKmh = speedKmh,
                    latitude = latitude,
                    longitude = longitude,
                    formattedTime = formattedTime
                )

                _crossingEvents.update { current -> listOf(event) + current }

                val logMessage = "[STOP CROSSED] Stop #${stop.sequenceOrder}: \"${stop.name}\" | Dist: ${String.format(Locale.US, "%.1f", distance)}m | Speed: ${String.format(Locale.US, "%.1f", speedKmh)} km/h | Coords: ($latitude, $longitude) at $formattedTime"
                Log.i(tag, logMessage)
                writeDiskLog("$logDate - $logMessage")
            }
        }
    }

    fun getCrossedStopsCount(): Int = crossedStopIds.size
    fun getTotalStopsCount(): Int = cachedStops.size

    fun getLogFilePath(): String = logFile.absolutePath

    fun readLogFileText(): String {
        return if (logFile.exists()) logFile.readText() else "No logs recorded yet."
    }

    fun clearLog() {
        crossedStopIds.clear()
        _crossingEvents.value = emptyList()
        if (logFile.exists()) logFile.delete()
        writeDiskLog("=== LOG CLEARED AT ${logDateFormat.format(Date())} ===")
    }

    private fun writeDiskLog(entry: String) {
        runCatching {
            FileWriter(logFile, true).use { writer ->
                writer.appendLine(entry)
            }
        }.onFailure {
            Log.e(tag, "Failed to write disk log: ${it.message}")
        }
    }
}
