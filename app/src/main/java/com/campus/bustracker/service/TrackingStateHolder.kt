package com.campus.bustracker.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectionStatus {
    IDLE,
    CONNECTED,
    TRANSMITTING,
    ERROR
}

data class TrackingTelemetry(
    val isBroadcasting: Boolean = false,
    val currentSpeedKmh: Float = 0f,
    val accuracyMeters: Float = 0f,
    val lastTransmittedMillis: Long = 0L,
    val packetsSentCount: Long = 0L,
    val transmissionIntervalMs: Long = 3000L,
    val connectionStatus: ConnectionStatus = ConnectionStatus.IDLE,
    val lastError: String? = null
)

@Singleton
class TrackingStateHolder @Inject constructor() {
    private val _telemetry = MutableStateFlow(TrackingTelemetry())
    val telemetry: StateFlow<TrackingTelemetry> = _telemetry.asStateFlow()

    fun updateTelemetry(transform: (TrackingTelemetry) -> TrackingTelemetry) {
        _telemetry.update(transform)
    }

    fun reset() {
        _telemetry.value = TrackingTelemetry()
    }
}
