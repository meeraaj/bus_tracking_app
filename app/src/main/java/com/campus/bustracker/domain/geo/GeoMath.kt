package com.campus.bustracker.domain.geo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates the great-circle distance between two points in meters using the Haversine formula.
     */
    fun haversineDistanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(rLat1) * cos(rLat2) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Calculates initial bearing from point 1 to point 2 in degrees (0..360).
     */
    fun initialBearing(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLonRad = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLonRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(deltaLonRad)
        val deg = Math.toDegrees(atan2(y, x)).toFloat()
        return (deg + 360f) % 360f
    }

    /**
     * Projects point P onto segment AB (flat Earth approximation suitable for small segments < 5km).
     * Returns fraction t clamped to [0.0, 1.0], snapped point (lat, lon), and distance in meters.
     */
    fun projectOntoSegment(
        pLat: Double, pLon: Double,
        aLat: Double, aLon: Double,
        bLat: Double, bLon: Double
    ): ProjectedPoint {
        val midLat = (aLat + bLat) / 2.0
        val cosMid = cos(Math.toRadians(midLat))

        // Convert lat/lon deltas to approximate local metric coordinates
        val metersPerDegLat = 111132.92
        val metersPerDegLon = 111412.84 * cosMid

        val ax = 0.0
        val ay = 0.0
        val bx = (bLon - aLon) * metersPerDegLon
        val by = (bLat - aLat) * metersPerDegLat
        val px = (pLon - aLon) * metersPerDegLon
        val py = (pLat - aLat) * metersPerDegLat

        val segLenSq = bx * bx + by * by
        val t = if (segLenSq > 0.0001) {
            ((px * bx + py * by) / segLenSq).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        val snappedLat = aLat + t * (bLat - aLat)
        val snappedLon = aLon + t * (bLon - aLon)
        val distMeters = haversineDistanceMeters(pLat, pLon, snappedLat, snappedLon)

        return ProjectedPoint(
            fraction = t,
            snappedLat = snappedLat,
            snappedLon = snappedLon,
            distanceFromSegmentMeters = distMeters
        )
    }

    data class ProjectedPoint(
        val fraction: Double,
        val snappedLat: Double,
        val snappedLon: Double,
        val distanceFromSegmentMeters: Double
    )
}
