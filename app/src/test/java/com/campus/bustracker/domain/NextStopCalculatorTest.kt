package com.campus.bustracker.domain

import com.campus.bustracker.data.model.BusLocation
import com.campus.bustracker.data.model.RouteData
import com.campus.bustracker.data.model.RouteStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NextStopCalculatorTest {

    private lateinit var testRoute: RouteData
    private lateinit var calculator: NextStopCalculator

    @Before
    fun setUp() {
        // A straight route running East from 80.150 to 80.160 along latitude 12.920
        // Total ~1.08 km, 5 vertices, 3 stops
        val points = listOf(
            Pair(12.9200, 80.1500), // Vertex 0
            Pair(12.9200, 80.1525), // Vertex 1
            Pair(12.9200, 80.1550), // Vertex 2
            Pair(12.9200, 80.1575), // Vertex 3
            Pair(12.9200, 80.1600)  // Vertex 4
        )

        val stops = listOf(
            RouteStop(stopId = "stop_1", name = "Stop 1", latitude = 12.9200, longitude = 80.1525, sequenceOrder = 1),
            RouteStop(stopId = "stop_2", name = "Stop 2", latitude = 12.9200, longitude = 80.1550, sequenceOrder = 2),
            RouteStop(stopId = "stop_3", name = "Stop 3", latitude = 12.9200, longitude = 80.1600, sequenceOrder = 3)
        )

        testRoute = RouteData(
            routeId = "test_route",
            routeName = "Test Route",
            polylinePoints = points,
            stops = stops
        )
        calculator = NextStopCalculator(testRoute)
    }

    @Test
    fun `bus at start of route targets Stop 1 as approaching`() {
        val busLoc = BusLocation(
            latitude = 12.9200,
            longitude = 80.1505,
            speedKmh = 30f
        )

        val nextStop = calculator.calculateApproachingStop(busLoc)

        assertNotNull(nextStop)
        assertEquals("stop_1", nextStop!!.currentStop.stopId)
        assertFalse("Bus is ~217m away, so should not be arriving yet", nextStop.isArriving)
    }

    @Test
    fun `bus within 20m of Stop 1 triggers isArriving true`() {
        // 0.0001 deg lon is ~10.8m at this latitude
        val busLoc = BusLocation(
            latitude = 12.9200,
            longitude = 80.15235, // ~16m before stop 1
            speedKmh = 15f
        )

        val nextStop = calculator.calculateApproachingStop(busLoc)

        assertNotNull(nextStop)
        assertEquals("stop_1", nextStop!!.currentStop.stopId)
        assertTrue("Bus within 25m radius must trigger isArriving", nextStop.isArriving)
    }

    @Test
    fun `bus past Stop 1 immediately advances to Stop 2 preventing UI sticking`() {
        // Bus has moved past Stop 1 (vertex 1, lon 80.1525) to lon 80.1535 (segment 1 -> 2)
        // Guardrail 7: stopIndices[0] (vertex 1) is NOT > busPolyIndex (1), and dist > 25m.
        // Therefore Stop 1 is discarded and Stop 2 (vertex 2) is chosen.
        val busLoc = BusLocation(
            latitude = 12.9200,
            longitude = 80.1535,
            speedKmh = 25f
        )

        val nextStop = calculator.calculateApproachingStop(busLoc)

        assertNotNull(nextStop)
        assertEquals("UI must immediately advance to Stop 2 without sticking", "stop_2", nextStop!!.currentStop.stopId)
        assertFalse(nextStop.isArriving)
    }

    @Test
    fun `stationary bus at red light maintains minimum 18 kmh ETA floor`() {
        // Bus is stopped (speed 0 km/h) 250m before Stop 1
        val busLoc = BusLocation(
            latitude = 12.9200,
            longitude = 80.1500,
            speedKmh = 0f
        )

        val nextStop = calculator.calculateApproachingStop(busLoc)

        assertNotNull(nextStop)
        // With floor 18 km/h (5 m/s), ETA for ~271m must be approximately 54 seconds, NOT 0 or infinite
        assertTrue("ETA must be non-zero and realistic: ${nextStop!!.etaSeconds}s", nextStop.etaSeconds in 40..70)
    }
}
