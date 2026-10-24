package com.campus.bustracker.data.repository

import com.campus.bustracker.data.model.BusLocation
import com.campus.bustracker.data.model.RouteData
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for bus tracking operations.
 *
 * Firebase RTDB Mapping Guide:
 * - observeBusLocation(routeId)    -> /routes/{routeId}/live_location (ValueEventListener)
 * - updateDriverLocation(...)     -> /routes/{routeId}/live_location (setValue / updateChildren)
 * - setDriverStatus(...)          -> /routes/{routeId}/live_location/status (setValue)
 * - getRouteDetails(routeId)      -> /routes/{routeId}/polyline (Read once / fallback to bundled asset)
 */
interface BusTrackingRepository {
    /**
     * Read route polyline & stops (Local cache / preloaded asset fallback).
     */
    suspend fun getRouteDetails(routeId: String): RouteData

    /**
     * Student: Observe real-time bus movement (maps to /routes/{routeId}/live_location).
     */
    fun observeBusLocation(routeId: String): Flow<BusLocation>

    /**
     * Driver: Broadcast current coordinates (maps to /routes/{routeId}/live_location).
     */
    suspend fun updateDriverLocation(routeId: String, location: BusLocation): Result<Unit>

    /**
     * Driver: Set status to OFFLINE on trip end or disconnect (maps to /routes/{routeId}/live_location/status).
     */
    suspend fun setDriverStatus(routeId: String, status: String): Result<Unit>
}
