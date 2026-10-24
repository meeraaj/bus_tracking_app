package com.campus.bustracker.ui.student

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.campus.bustracker.data.model.BusLocation
import com.campus.bustracker.data.repository.BusTrackingRepository
import com.campus.bustracker.data.repository.SessionRepository
import com.campus.bustracker.domain.BusPathInterpolator
import com.campus.bustracker.domain.NextStopCalculator
import com.campus.bustracker.domain.geo.PolylineSnapper
import com.campus.bustracker.map.OfflineTileManager
import com.campus.bustracker.ui.navigation.ScreenRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StudentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val busTrackingRepository: BusTrackingRepository,
    private val sessionRepository: SessionRepository,
    val offlineTileManager: OfflineTileManager
) : ViewModel() {

    // Safely extract routeId from type-safe navigation route or fallback
    val routeId: String = runCatching {
        savedStateHandle.toRoute<ScreenRoute.StudentMap>().routeId
    }.getOrElse {
        savedStateHandle.get<String>("routeId") ?: "route_85"
    }

    private val _uiState = MutableStateFlow(StudentUiState())
    val uiState: StateFlow<StudentUiState> = _uiState.asStateFlow()

    private var nextStopCalculator: NextStopCalculator? = null
    private var polylineSnapper: PolylineSnapper? = null
    private var animationJob: Job? = null
    private var lastReportedLocation: BusLocation? = null

    init {
        loadRouteDetails()
    }

    private fun loadRouteDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRoute = true, errorMessage = null) }
            try {
                val route = busTrackingRepository.getRouteDetails(routeId)
                nextStopCalculator = NextStopCalculator(route)
                polylineSnapper = PolylineSnapper(route.polylinePoints)

                _uiState.update {
                    it.copy(
                        routeData = route,
                        isLoadingRoute = false
                    )
                }

                startObservingBusLocation()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingRoute = false,
                        errorMessage = "Failed to load route: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    private fun startObservingBusLocation() {
        viewModelScope.launch {
            // Context Guardrail 4: Multi-bus listener targets /routes/{routeId}/live_location
            busTrackingRepository.observeBusLocation(routeId)
                .catch { e ->
                    _uiState.update { it.copy(errorMessage = "Location stream error: ${e.localizedMessage}") }
                }
                .collect { newLocation ->
                    handleIncomingLocation(newLocation)
                }
        }
    }

    private fun handleIncomingLocation(newLocation: BusLocation) {
        val calc = nextStopCalculator ?: return
        val stopInfo = calc.calculateApproachingStop(newLocation)
        val now = System.currentTimeMillis()
        val isStale = (now - newLocation.timestamp) > 60_000L || !newLocation.isActive

        val prevLocation = lastReportedLocation
        lastReportedLocation = newLocation

        _uiState.update {
            it.copy(
                busLocation = newLocation,
                approachingStop = stopInfo,
                isStaleOrOffline = isStale,
                lastUpdatedAgoText = calculateAgoText(newLocation.timestamp)
            )
        }

        animateBusMovement(prevLocation, newLocation)
    }

    private fun animateBusMovement(prev: BusLocation?, curr: BusLocation) {
        val snapper = polylineSnapper ?: return
        animationJob?.cancel()

        val startLat = prev?.latitude?.takeIf { it != 0.0 } ?: curr.latitude
        val startLon = prev?.longitude?.takeIf { it != 0.0 } ?: curr.longitude
        val endLat = curr.latitude
        val endLon = curr.longitude

        val subPath = BusPathInterpolator.createSubPath(startLat, startLon, endLat, endLon, snapper)

        animationJob = viewModelScope.launch {
            val totalDurationMs = 1800L
            val stepMs = 16L // ~60fps
            val steps = (totalDurationMs / stepMs).toInt()

            for (i in 0..steps) {
                if (!isActive) break
                val t = (i.toFloat() / steps).coerceIn(0f, 1f)
                val interpolated = BusPathInterpolator.interpolateAlongPoints(subPath, t)
                _uiState.update {
                    it.copy(animatedBusPosition = Pair(interpolated.latitude, interpolated.longitude))
                }
                delay(stepMs)
            }

            _uiState.update {
                it.copy(animatedBusPosition = Pair(endLat, endLon))
            }
        }
    }

    fun toggleFollowBus() {
        _uiState.update { it.copy(followBus = !it.followBus) }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            sessionRepository.clearSession()
            onSignedOut()
        }
    }

    private fun calculateAgoText(timestamp: Long): String {
        if (timestamp <= 0L) return "Unknown"
        val diffSec = (System.currentTimeMillis() - timestamp) / 1000
        return when {
            diffSec < 5 -> "Just now"
            diffSec < 60 -> "${diffSec}s ago"
            else -> "${diffSec / 60}m ago"
        }
    }
}
