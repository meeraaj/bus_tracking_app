package com.campus.bustracker.domain.security

import com.campus.bustracker.data.model.BusLocation

/**
 * Enforces client-side telemetry security & kinematic sanity checks matching
 * `firebase/database.rules.json` before packets are transmitted or rendered.
 */
object LocationPayloadValidator {

    // Operating geofence envelope (Chennai / Tambaram campus corridor + surrounding metro region)
    const val MIN_LATITUDE = 12.5
    const val MAX_LATITUDE = 13.5
    const val MIN_LONGITUDE = 79.5
    const val MAX_LONGITUDE = 80.5

    const val MAX_SPEED_KMH = 120f
    const val MAX_CLOCK_SKEW_FUTURE_MS = 5_000L
    const val MAX_STALE_WINDOW_MS = 60_000L

    private val ROUTE_ID_REGEX = Regex("^[a-zA-Z0-9_-]{2,32}$")

    fun isValidRouteId(routeId: String): Boolean {
        return ROUTE_ID_REGEX.matches(routeId)
    }

    /**
     * Validates and sanitizes an outgoing [BusLocation] telemetry packet.
     * Returns [Result.failure] with a [SecurityException] if coordinates are spoofed/out-of-bounds,
     * values are NaN/Infinite, or timestamps violate the anti-replay window.
     */
    fun validateForBroadcast(
        location: BusLocation,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<BusLocation> {
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) {
            return Result.failure(SecurityException("Telemetry rejected: non-finite coordinates"))
        }
        if (!location.speedKmh.isFinite() || !location.bearing.isFinite()) {
            return Result.failure(SecurityException("Telemetry rejected: non-finite speed or bearing"))
        }

        if (location.latitude !in MIN_LATITUDE..MAX_LATITUDE ||
            location.longitude !in MIN_LONGITUDE..MAX_LONGITUDE
        ) {
            return Result.failure(
                SecurityException(
                    "Telemetry rejected: coordinates (${location.latitude}, ${location.longitude}) outside campus geofence"
                )
            )
        }

        if (location.speedKmh < 0f || location.speedKmh > MAX_SPEED_KMH) {
            return Result.failure(
                SecurityException("Telemetry rejected: implausible speed ${location.speedKmh} km/h")
            )
        }

        val normalizedStatus = location.status.uppercase()
        if (normalizedStatus != BusLocation.STATUS_ACTIVE && normalizedStatus != BusLocation.STATUS_OFFLINE) {
            return Result.failure(
                SecurityException("Telemetry rejected: invalid status '${location.status}'")
            )
        }

        val effectiveTs = if (location.timestamp <= 0L) nowMillis else location.timestamp
        if (effectiveTs > nowMillis + MAX_CLOCK_SKEW_FUTURE_MS ||
            effectiveTs < nowMillis - MAX_STALE_WINDOW_MS
        ) {
            return Result.failure(
                SecurityException("Telemetry rejected: timestamp outside anti-replay window")
            )
        }

        val normalizedBearing = ((location.bearing % 360f) + 360f) % 360f

        return Result.success(
            location.copy(
                bearing = normalizedBearing,
                speedKmh = location.speedKmh.coerceIn(0f, MAX_SPEED_KMH),
                timestamp = effectiveTs,
                status = normalizedStatus
            )
        )
    }
}
