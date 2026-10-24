package com.campus.bustracker.ui.login

enum class AuthRole {
    STUDENT,
    DRIVER
}

data class RouteOption(
    val routeId: String,
    val displayName: String
)

data class LoginUiState(
    val selectedRole: AuthRole = AuthRole.STUDENT,

    // Student fields
    val rollNumber: String = "",
    val selectedRouteId: String = "route_85",
    val availableRoutes: List<RouteOption> = listOf(
        RouteOption("route_85", "Route 85 — Tambaram Corridor")
    ),

    // Driver fields
    val driverEmail: String = "",
    val driverSecretKey: String = "",
    val driverRouteId: String = "route_85",
    val isKeyVisible: Boolean = false,
    val emailError: String? = null,
    val keyError: String? = null,

    // Status
    val isLoading: Boolean = false,
    val generalError: String? = null
)

sealed interface LoginNavEvent {
    data class NavigateToDriver(val routeId: String) : LoginNavEvent
    data class NavigateToStudent(val routeId: String) : LoginNavEvent
}
