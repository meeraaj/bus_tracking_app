package com.campus.bustracker.ui.driver

import com.campus.bustracker.data.model.RouteData
import com.campus.bustracker.service.TrackingTelemetry

data class DriverUiState(
    val routeId: String = "route_85",
    val routeData: RouteData? = null,
    val telemetry: TrackingTelemetry = TrackingTelemetry(),
    val showPermissionRationale: Boolean = false,
    val rationaleMessage: String = "",
    val isLoadingRoute: Boolean = true,
    val errorMessage: String? = null
)
