package com.campus.bustracker.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerStopCrossingRecord(
    val routeId: String = "",
    val driverId: String = "",
    val stopId: String = "",
    val stopName: String = "",
    val sequenceOrder: Int = 0,
    val distanceMeters: Double = 0.0,
    val speedKmh: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = 0L,
    val formattedTime: String = ""
)

@Serializable
data class ServerStopCrossingsResponse(
    val routeId: String = "",
    val total: Int = 0,
    val events: List<ServerStopCrossingRecord>? = null
)
