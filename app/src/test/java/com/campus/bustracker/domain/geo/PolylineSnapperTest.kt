package com.campus.bustracker.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolylineSnapperTest {

    @Test
    fun `cumulative distances increase monotonically`() {
        val points = listOf(
            Pair(12.923789, 80.156028),
            Pair(12.923444, 80.159860),
            Pair(12.922650, 80.162502),
            Pair(12.921098, 80.167407),
            Pair(12.918524, 80.176407)
        )
        val snapper = PolylineSnapper(points)

        assertTrue("Total distance must be positive", snapper.totalDistanceMeters > 2000.0)
        assertEquals(0.0, snapper.cumulativeDistancesMeters[0], 0.001)

        for (i in 1 until snapper.cumulativeDistancesMeters.size) {
            assertTrue(
                "Cumulative distance at $i must be strictly greater than ${i - 1}",
                snapper.cumulativeDistancesMeters[i] > snapper.cumulativeDistancesMeters[i - 1]
            )
        }
    }

    @Test
    fun `snap projects coordinate onto correct segment and clamps fraction`() {
        val points = listOf(
            Pair(12.9200, 80.1500),
            Pair(12.9200, 80.1600)
        )
        val snapper = PolylineSnapper(points)

        // Point right in the middle
        val mid = snapper.snap(12.9200, 80.1550)
        assertEquals(0, mid.segmentIndex)
        assertEquals(0.5, mid.fraction, 0.05)
        assertEquals(snapper.totalDistanceMeters * 0.5, mid.distanceAlongRouteMeters, 5.0)

        // Point before start
        val before = snapper.snap(12.9200, 80.1400)
        assertEquals(0.0, before.fraction, 0.001)

        // Point past end
        val past = snapper.snap(12.9200, 80.1700)
        assertEquals(1.0, past.fraction, 0.001)
    }
}
