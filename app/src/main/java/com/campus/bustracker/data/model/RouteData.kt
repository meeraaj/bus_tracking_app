package com.campus.bustracker.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RouteData(
    val routeId: String,
    val routeName: String,
    val polylinePoints: List<Pair<Double, Double>>,
    val stops: List<RouteStop>
)
