package com.campus.bustracker.domain

import com.campus.bustracker.data.model.BusLocation
import com.campus.bustracker.data.model.RouteData
import com.campus.bustracker.data.model.RouteStop
import com.campus.bustracker.domain.geo.GeoMath
import com.campus.bustracker.domain.geo.PolylineSnapper
import kotlin.math.roundToInt

data class ApproachingStopInfo(
    val currentStop: RouteStop,
    val nextStopIndex: Int,
    val distanceToStopMeters: Double,
    val distanceAlongRouteMeters: Double,
    val etaSeconds: Int,
    val progressFraction: Float, // 0.0 to 1.0 progress towards this stop
    val isArriving: Boolean // true if within 25m radius
)

class NextStopCalculator(
    val routeData: RouteData
) {
    val snapper = PolylineSnapper(routeData.polylinePoints)

    // Precomputed vertex index on polyline for each stop (sorted by sequenceOrder)
    val stopIndices: IntArray
    val stopAlongDistances: DoubleArray

    init {
        val stops = routeData.stops
        stopIndices = IntArray(stops.size)
        stopAlongDistances = DoubleArray(stops.size)

        var lastVertex = 0
        for (i in stops.indices) {
            val stop = stops[i]
            // Ensure monotonic or closest vertex mapping
            val vertex = snapper.findNearestVertexIndex(stop.latitude, stop.longitude)
                .coerceAtLeast(lastVertex)
            stopIndices[i] = vertex
            stopAlongDistances[i] = snapper.cumulativeDistancesMeters[vertex]
            lastVertex = vertex
        }
    }

    /**
     * Calculates the approaching stop using the non-sticking rule:
     * isApproaching = (stopIndices[i] > busPolyIndex) || (distToStop <= 25.0)
     */
    fun calculateApproachingStop(busLocation: BusLocation): ApproachingStopInfo? {
        val stops = routeData.stops
        if (stops.isEmpty()) return null

        val snap = snapper.snap(busLocation.latitude, busLocation.longitude)
        val busPolyIndex = snap.segmentIndex
        val busAlongDist = snap.distanceAlongRouteMeters

        // Iterate through stops in sequential order to find the first stop that satisfies the approaching condition
        var approachingStopIndex = -1
        var distToApproachingStop = Double.MAX_VALUE
        var isWithin25m = false

        for (i in stops.indices) {
            val stop = stops[i]
            val distDirect = GeoMath.haversineDistanceMeters(
                busLocation.latitude, busLocation.longitude,
                stop.latitude, stop.longitude
            )

            val stopVertex = stopIndices[i]
            val isAheadOnRoute = stopVertex > busPolyIndex
            val isAtOrWithinRadius = distDirect <= 25.0

            // Context Guardrail 7: Non-sticking next stop matching logic
            if (isAheadOnRoute || isAtOrWithinRadius) {
                approachingStopIndex = i
                distToApproachingStop = distDirect
                isWithin25m = isAtOrWithinRadius
                break
            }
        }

        // If bus is past all stops, latch to the final terminal stop
        if (approachingStopIndex == -1) {
            val lastIdx = stops.size - 1
            val lastStop = stops[lastIdx]
            val dist = GeoMath.haversineDistanceMeters(
                busLocation.latitude, busLocation.longitude,
                lastStop.latitude, lastStop.longitude
            )
            return ApproachingStopInfo(
                currentStop = lastStop,
                nextStopIndex = lastIdx,
                distanceToStopMeters = dist,
                distanceAlongRouteMeters = 0.0,
                etaSeconds = 0,
                progressFraction = 1.0f,
                isArriving = dist <= 25.0
            )
        }

        val targetStop = stops[approachingStopIndex]
        val stopAlongDist = stopAlongDistances[approachingStopIndex]
        val remainingAlongDist = (stopAlongDist - busAlongDist).coerceAtLeast(0.0)

        // Effective speed: minimum 18 km/h (5.0 m/s) floor to prevent infinite ETA during signal stops
        val currentSpeedMps = (busLocation.speedKmh / 3.6f).toDouble()
        val effectiveSpeedMps = currentSpeedMps.coerceAtLeast(5.0)
        val etaSeconds = (remainingAlongDist / effectiveSpeedMps).roundToInt()

        // Calculate progress fraction between previous stop and this approaching stop
        val prevStopAlongDist = if (approachingStopIndex > 0) {
            stopAlongDistances[approachingStopIndex - 1]
        } else {
            0.0
        }
        val legDistance = (stopAlongDist - prevStopAlongDist).coerceAtLeast(1.0)
        val progressFraction = ((busAlongDist - prevStopAlongDist) / legDistance)
            .coerceIn(0.0, 1.0)
            .toFloat()

        return ApproachingStopInfo(
            currentStop = targetStop,
            nextStopIndex = approachingStopIndex,
            distanceToStopMeters = distToApproachingStop,
            distanceAlongRouteMeters = remainingAlongDist,
            etaSeconds = etaSeconds,
            progressFraction = progressFraction,
            isArriving = isWithin25m
        )
    }
}
