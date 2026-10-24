package com.campus.bustracker.domain.geo

class PolylineSnapper(
    val polyline: List<Pair<Double, Double>>
) {
    val cumulativeDistancesMeters: DoubleArray = DoubleArray(polyline.size)
    val totalDistanceMeters: Double

    init {
        var running = 0.0
        cumulativeDistancesMeters[0] = 0.0
        for (i in 1 until polyline.size) {
            val prev = polyline[i - 1]
            val curr = polyline[i]
            running += GeoMath.haversineDistanceMeters(prev.first, prev.second, curr.first, curr.second)
            cumulativeDistancesMeters[i] = running
        }
        totalDistanceMeters = running
    }

    data class SnapResult(
        val segmentIndex: Int, // Index of point A (segment is A -> B, i.e., polyline[segmentIndex] -> polyline[segmentIndex + 1])
        val fraction: Double,
        val snappedLat: Double,
        val snappedLon: Double,
        val distanceAlongRouteMeters: Double,
        val distanceFromPolylineMeters: Double
    )

    /**
     * Snaps a GPS coordinate to the closest point along the route polyline.
     * When [windowStartIndex] is provided, prioritizes forward-moving segment matching
     * to avoid backward snapping in case of loops or intersections.
     */
    fun snap(lat: Double, lon: Double, windowStartIndex: Int = 0): SnapResult {
        if (polyline.size < 2) {
            val single = polyline.firstOrNull() ?: Pair(lat, lon)
            return SnapResult(0, 0.0, single.first, single.second, 0.0, 0.0)
        }

        var bestSegment = 0
        var bestProjected: GeoMath.ProjectedPoint? = null
        var minDistance = Double.MAX_VALUE

        // Search in a window forward first if plausible, otherwise full scan
        val start = windowStartIndex.coerceIn(0, polyline.size - 2)
        val end = (start + 15).coerceAtMost(polyline.size - 2)

        for (i in start..end) {
            val a = polyline[i]
            val b = polyline[i + 1]
            val proj = GeoMath.projectOntoSegment(lat, lon, a.first, a.second, b.first, b.second)
            if (proj.distanceFromSegmentMeters < minDistance) {
                minDistance = proj.distanceFromSegmentMeters
                bestSegment = i
                bestProjected = proj
            }
        }

        // If distance is large (> 80m), perform global search
        if (minDistance > 80.0) {
            for (i in 0 until polyline.size - 1) {
                val a = polyline[i]
                val b = polyline[i + 1]
                val proj = GeoMath.projectOntoSegment(lat, lon, a.first, a.second, b.first, b.second)
                if (proj.distanceFromSegmentMeters < minDistance) {
                    minDistance = proj.distanceFromSegmentMeters
                    bestSegment = i
                    bestProjected = proj
                }
            }
        }

        val proj = bestProjected ?: GeoMath.ProjectedPoint(0.0, lat, lon, 0.0)
        val segStartDist = cumulativeDistancesMeters[bestSegment]
        val segEndDist = cumulativeDistancesMeters[bestSegment + 1]
        val alongDist = segStartDist + proj.fraction * (segEndDist - segStartDist)

        return SnapResult(
            segmentIndex = bestSegment,
            fraction = proj.fraction,
            snappedLat = proj.snappedLat,
            snappedLon = proj.snappedLon,
            distanceAlongRouteMeters = alongDist,
            distanceFromPolylineMeters = proj.distanceFromSegmentMeters
        )
    }

    /**
     * Finds the nearest polyline vertex index for a given coordinate (e.g. Stop location).
     */
    fun findNearestVertexIndex(lat: Double, lon: Double): Int {
        var bestIndex = 0
        var minDist = Double.MAX_VALUE
        for (i in polyline.indices) {
            val p = polyline[i]
            val d = GeoMath.haversineDistanceMeters(lat, lon, p.first, p.second)
            if (d < minDist) {
                minDist = d
                bestIndex = i
            }
        }
        return bestIndex
    }
}
