package com.campus.bustracker.ui.driver

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.campus.bustracker.data.model.BusLocation
import com.campus.bustracker.data.repository.BusTrackingRepository
import com.campus.bustracker.data.repository.SessionRepository
import com.campus.bustracker.service.LocationServiceController
import com.campus.bustracker.service.TrackingStateHolder
import com.campus.bustracker.ui.navigation.ScreenRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DriverViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val busTrackingRepository: BusTrackingRepository,
    private val sessionRepository: SessionRepository,
    private val trackingStateHolder: TrackingStateHolder
) : ViewModel() {

    val routeId: String = runCatching {
        savedStateHandle.toRoute<ScreenRoute.DriverDashboard>().routeId
    }.getOrElse {
        savedStateHandle.get<String>("routeId") ?: "route_85"
    }

    private val _localState = MutableStateFlow(DriverUiState(routeId = routeId))

    val uiState: StateFlow<DriverUiState> = combine(
        _localState,
        trackingStateHolder.telemetry
    ) { local, telemetry ->
        local.copy(telemetry = telemetry)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DriverUiState(routeId = routeId)
    )

    init {
        loadRoute()
    }

    private fun loadRoute() {
        viewModelScope.launch {
            _localState.update { it.copy(isLoadingRoute = true) }
            try {
                val route = busTrackingRepository.getRouteDetails(routeId)
                _localState.update { it.copy(routeData = route, isLoadingRoute = false) }
            } catch (e: Exception) {
                _localState.update {
                    it.copy(
                        isLoadingRoute = false,
                        errorMessage = "Failed to load route: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun startBroadcast(context: Context) {
        LocationServiceController.startTracking(context, routeId)
    }

    fun stopBroadcast(context: Context) {
        LocationServiceController.stopTracking(context)
        viewModelScope.launch {
            busTrackingRepository.setDriverStatus(routeId, BusLocation.STATUS_OFFLINE)
        }
    }

    fun showPermissionRationale(message: String) {
        _localState.update { it.copy(showPermissionRationale = true, rationaleMessage = message) }
    }

    fun dismissPermissionRationale() {
        _localState.update { it.copy(showPermissionRationale = false) }
    }

    fun signOut(context: Context, onSignedOut: () -> Unit) {
        viewModelScope.launch {
            LocationServiceController.stopTracking(context)
            busTrackingRepository.setDriverStatus(routeId, BusLocation.STATUS_OFFLINE)
            sessionRepository.clearSession()
            onSignedOut()
        }
    }
}
