package com.campus.bustracker.data.repository.mock

import com.campus.bustracker.data.local.RouteAssetDataSource
import com.campus.bustracker.data.model.BusLocation
import com.campus.bustracker.data.model.RouteData
import com.campus.bustracker.data.repository.BusTrackingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Singleton
class MockBusTrackingRepository @Inject constructor(
    private val routeAssetDataSource: RouteAssetDataSource
) : BusTrackingRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Per-route location flows
    private val routeLocations = ConcurrentHashMap<String, MutableStateFlow<BusLocation>>()
    private val lastDriverUpdateMillis = ConcurrentHashMap<String, Long>()
    private val simulationStarted = ConcurrentHashMap<String, Boolean>()

    override suspend fun getRouteDetails(routeId: String): RouteData {
        return routeAssetDataSource.getRouteData(routeId)
    }

    override fun observeBusLocation(routeId: String): Flow<BusLocation> {
        val flow = routeLocations.computeIfAbsent(routeId) {
            MutableStateFlow(
                BusLocation(
                    latitude = 0.0,
                    longitude = 0.0,
                    bearing = 0f,
                    speedKmh = 0f,
                    timestamp = System.currentTimeMillis(),
                    status = BusLocation.STATUS_ACTIVE
                )
            )
        }

        ensureSimulationRunning(routeId, flow)
        return flow.asStateFlow()
    }

    override suspend fun updateDriverLocation(routeId: String, location: BusLocation): Result<Unit> {
        lastDriverUpdateMillis[routeId] = System.currentTimeMillis()
        val flow = routeLocations.computeIfAbsent(routeId) {
            MutableStateFlow(location)
        }
        flow.update { location }
        return Result.success(Unit)
    }

    override suspend fun setDriverStatus(routeId: String, status: String): Result<Unit> {
        lastDriverUpdateMillis[routeId] = System.currentTimeMillis()
        val flow = routeLocations[routeId]
        if (flow != null) {
            flow.update { current ->
                current.copy(status = status, timestamp = System.currentTimeMillis())
            }
        }
        return Result.success(Unit)
    }

    /**
     * Starts an autonomous simulation loop along the route's polyline points
     * if no real driver location broadcasts are active.
     */
    private fun ensureSimulationRunning(routeId: String, locationFlow: MutableStateFlow<BusLocation>) {
        if (simulationStarted.putIfAbsent(routeId, true) == true) {
            return
        }

        repositoryScope.launch {
            try {
                val routeData = routeAssetDataSource.getRouteData(routeId)
                val poly = routeData.polylinePoints
                if (poly.isEmpty()) return@launch

                var currentIndex = 0
                while (isActive) {
                    val lastDriverTs = lastDriverUpdateMillis[routeId] ?: 0L
                    val isDriverActive = (System.currentTimeMillis() - lastDriverTs) < 10_000L

                    if (!isDriverActive) {
                        val pt = poly[currentIndex]
                        val nextPt = poly[(currentIndex + 1) % poly.size]
                        val bearing = calculateBearing(pt.first, pt.second, nextPt.first, nextPt.second)

                        locationFlow.update {
                            BusLocation(
                                latitude = pt.first,
                                longitude = pt.second,
                                bearing = bearing,
                                speedKmh = 28f + (currentIndex % 3) * 2f,
                                timestamp = System.currentTimeMillis(),
                                status = BusLocation.STATUS_ACTIVE
                            )
                        }

                        currentIndex = (currentIndex + 1) % poly.size
                    }

                    delay(2500L) // 2.5 second telemetry tick
                }
            } catch (_: Exception) {
                // If route asset isn't ready or error occurs, simulation gracefully halts
            }
        }
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLonRad = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLonRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(deltaLonRad)
        val deg = Math.toDegrees(atan2(y, x)).toFloat()
        return (deg + 360f) % 360f
    }
}
