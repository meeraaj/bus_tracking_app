package com.campus.bustracker.ui.student

import com.campus.bustracker.data.model.BusLocation
import com.campus.bustracker.data.model.RouteData
import com.campus.bustracker.domain.ApproachingStopInfo

data class StudentUiState(
    val routeData: RouteData? = null,
    val busLocation: BusLocation? = null,
    val animatedBusPosition: Pair<Double, Double>? = null,
    val approachingStop: ApproachingStopInfo? = null,
    val followBus: Boolean = true,
    val isLoadingRoute: Boolean = true,
    val isStaleOrOffline: Boolean = false,
    val lastUpdatedAgoText: String = "",
    val errorMessage: String? = null
)
