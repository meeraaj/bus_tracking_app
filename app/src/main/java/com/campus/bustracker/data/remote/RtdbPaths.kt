package com.campus.bustracker.data.remote

/**
 * Single source of truth for Firebase Realtime Database schema paths.
 *
 * NOTE: Multi-Bus Fleet Hierarchy (Guardrail 4):
 * Never use monolithic `/current_bus_location`. All paths are scoped under `/routes/{routeId}/...`.
 */
object RtdbPaths {
    private const val ROOT_ROUTES = "routes"
    private const val ROOT_DRIVERS = "drivers"

    fun route(routeId: String): String = "$ROOT_ROUTES/$routeId"

    fun liveLocation(routeId: String): String = "$ROOT_ROUTES/$routeId/live_location"

    fun status(routeId: String): String = "$ROOT_ROUTES/$routeId/live_location/status"

    fun polyline(routeId: String): String = "$ROOT_ROUTES/$routeId/polyline"

    fun stops(routeId: String): String = "$ROOT_ROUTES/$routeId/stops"

    fun driverProfile(uid: String): String = "$ROOT_DRIVERS/$uid"
}
