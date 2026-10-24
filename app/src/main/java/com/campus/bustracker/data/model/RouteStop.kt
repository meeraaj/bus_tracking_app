package com.campus.bustracker.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RouteStop(
    val stopId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val sequenceOrder: Int
)
