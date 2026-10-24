package com.campus.bustracker.data.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface UserSession {
    val routeId: String

    @Serializable
    data class Driver(
        val driverId: String,
        override val routeId: String
    ) : UserSession

    @Serializable
    data class Student(
        val identifier: String,
        override val routeId: String
    ) : UserSession

    @Serializable
    data object None : UserSession {
        override val routeId: String = ""
    }
}
