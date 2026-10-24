package com.campus.bustracker.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BusLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val bearing: Float = 0f,
    val speedKmh: Float = 0f,
    val timestamp: Long = 0L,
    val status: String = STATUS_ACTIVE // "ACTIVE" or "OFFLINE"
) {
    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_OFFLINE = "OFFLINE"
    }

    val isActive: Boolean
        get() = status.equals(STATUS_ACTIVE, ignoreCase = true)
}
