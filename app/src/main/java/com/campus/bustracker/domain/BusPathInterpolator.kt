package com.campus.bustracker.domain

import com.campus.bustracker.domain.geo.GeoMath
import com.campus.bustracker.domain.geo.PolylineSnapper

data class LatLngPoint(val latitude: Double, val longitude: Double)

object BusPathInterpolator {

    /**
     * Extracts road-snapped intermediate vertices between [startLat], [startLon] and [endLat], [endLon].
     * If the distance is small (< 5m), returns just [endLat], [endLon].
     * Otherwise returns [snappedStart, ...intermediate road vertices..., snappedEnd].
     */
    fun createSubPath(
        startLat: Double, startLon: Double,
        endLat: Double, endLon: Double,
        snapper: PolylineSnapper
    ): List<LatLngPoint> {
        val totalDirectDist = GeoMath.haversineDistanceMeters(startLat, startLon, endLat, endLon)
        if (totalDirectDist < 5.0) {
            return listOf(LatLngPoint(endLat, endLon))
        }

        val startSnap = snapper.snap(startLat, startLon)
        val endSnap = snapper.snap(endLat, endLon, windowStartIndex = startSnap.segmentIndex)

        val points = mutableListOf<LatLngPoint>()
        points.add(LatLngPoint(startSnap.snappedLat, startSnap.snappedLon))

        if (endSnap.segmentIndex > startSnap.segmentIndex) {
            // Append intermediate polyline vertices along the road
            for (i in (startSnap.segmentIndex + 1)..endSnap.segmentIndex) {
                if (i < snapper.polyline.size) {
                    val pt = snapper.polyline[i]
                    points.add(LatLngPoint(pt.first, pt.second))
                }
            }
        }

        points.add(LatLngPoint(endSnap.snappedLat, endSnap.snappedLon))
        return points
    }

    /**
     * Interpolates along a sequence of path points given progress fraction t in [0.0, 1.0].
     */
    fun interpolateAlongPoints(points: List<LatLngPoint>, t: Float): LatLngPoint {
        if (points.isEmpty()) return LatLngPoint(0.0, 0.0)
        if (points.size == 1 || t <= 0f) return points.first()
        if (t >= 1f) return points.last()

        val distances = DoubleArray(points.size)
        var totalDist = 0.0
        for (i in 1 until points.size) {
            val d = GeoMath.haversineDistanceMeters(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude
            )
            totalDist += d
            distances[i] = totalDist
        }

        if (totalDist == 0.0) return points.first()

        val targetDist = t * totalDist
        for (i in 1 until points.size) {
            if (distances[i] >= targetDist) {
                val segStartDist = distances[i - 1]
                val segLength = distances[i] - segStartDist
                val segT = if (segLength > 0.0) ((targetDist - segStartDist) / segLength).coerceIn(0.0, 1.0) else 0.0
                val p1 = points[i - 1]
                val p2 = points[i]
                return LatLngPoint(
                    latitude = p1.latitude + segT * (p2.latitude - p1.latitude),
                    longitude = p1.longitude + segT * (p2.longitude - p1.longitude)
                )
            }
        }
        return points.last()
    }
}
