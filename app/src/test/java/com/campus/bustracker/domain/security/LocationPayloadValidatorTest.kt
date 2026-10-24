package com.campus.bustracker.domain.security

import com.campus.bustracker.data.model.BusLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPayloadValidatorTest {

    private val fixedNow = 1_761_297_600_000L

    @Test
    fun `valid telemetry inside Chennai geofence passes and normalizes`() {
        val raw = BusLocation(
            latitude = 12.923444,
            longitude = 80.159860,
            bearing = -45f, // Should normalize to 315f
            speedKmh = 32.5f,
            timestamp = fixedNow - 1_000L,
            status = "active" // Should normalize to "ACTIVE"
        )

        val result = LocationPayloadValidator.validateForBroadcast(raw, nowMillis = fixedNow)
        assertTrue(result.isSuccess)
        val sanitized = result.getOrThrow()
        assertEquals(315f, sanitized.bearing, 0.01f)
        assertEquals(BusLocation.STATUS_ACTIVE, sanitized.status)
    }

    @Test
    fun `coordinates outside campus geofence are rejected`() {
        val spoofed = BusLocation(
            latitude = 0.0,
            longitude = 0.0,
            bearing = 90f,
            speedKmh = 20f,
            timestamp = fixedNow,
            status = BusLocation.STATUS_ACTIVE
        )

        val result = LocationPayloadValidator.validateForBroadcast(spoofed, nowMillis = fixedNow)
        assertTrue("Out-of-geofence coordinates must be rejected", result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun `implausible speed above 120 kmh is rejected`() {
        val speeding = BusLocation(
            latitude = 12.923444,
            longitude = 80.159860,
            bearing = 90f,
            speedKmh = 250f,
            timestamp = fixedNow,
            status = BusLocation.STATUS_ACTIVE
        )

        val result = LocationPayloadValidator.validateForBroadcast(speeding, nowMillis = fixedNow)
        assertTrue(result.isFailure)
    }

    @Test
    fun `stale or future replay timestamps are rejected`() {
        val stale = BusLocation(
            latitude = 12.923444,
            longitude = 80.159860,
            bearing = 90f,
            speedKmh = 25f,
            timestamp = fixedNow - 120_000L, // 2 minutes old (> 60s window)
            status = BusLocation.STATUS_ACTIVE
        )
        val future = stale.copy(timestamp = fixedNow + 30_000L) // 30s in future (> 5s skew)

        assertTrue(LocationPayloadValidator.validateForBroadcast(stale, nowMillis = fixedNow).isFailure)
        assertTrue(LocationPayloadValidator.validateForBroadcast(future, nowMillis = fixedNow).isFailure)
    }

    @Test
    fun `routeId path traversal attempts are blocked`() {
        assertTrue(LocationPayloadValidator.isValidRouteId("route_85"))
        assertTrue(LocationPayloadValidator.isValidRouteId("route-86A"))
        assertFalse(LocationPayloadValidator.isValidRouteId("../drivers"))
        assertFalse(LocationPayloadValidator.isValidRouteId("route/live_location"))
        assertFalse(LocationPayloadValidator.isValidRouteId(""))
    }
}
