package com.campus.bustracker.ui.navigation

import kotlinx.serialization.Serializable

sealed interface ScreenRoute {
    @Serializable
    data object Login : ScreenRoute

    @Serializable
    data class StudentMap(val routeId: String) : ScreenRoute

    @Serializable
    data class DriverDashboard(val routeId: String) : ScreenRoute
}
