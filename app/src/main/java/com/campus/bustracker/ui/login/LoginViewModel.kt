package com.campus.bustracker.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.bustracker.data.repository.AuthRepository
import com.campus.bustracker.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _navEvents = Channel<LoginNavEvent>(Channel.BUFFERED)
    val navEvents = _navEvents.receiveAsFlow()

    fun onRoleSelected(role: AuthRole) {
        _uiState.update { it.copy(selectedRole = role, generalError = null) }
    }

    fun onStudentRollNumberChanged(value: String) {
        _uiState.update { it.copy(rollNumber = value, generalError = null) }
    }

    fun onRouteSelected(routeId: String) {
        _uiState.update { it.copy(selectedRouteId = routeId, driverRouteId = routeId) }
    }

    fun onDriverEmailChanged(value: String) {
        val error = if (value.isNotBlank() && !value.contains("@")) "Invalid email format" else null
        _uiState.update { it.copy(driverEmail = value, emailError = error, generalError = null) }
    }

    fun onDriverSecretKeyChanged(value: String) {
        val error = if (value.isNotBlank() && value.length < 4) "Key must be at least 4 characters" else null
        _uiState.update { it.copy(driverSecretKey = value, keyError = error, generalError = null) }
    }

    fun toggleKeyVisibility() {
        _uiState.update { it.copy(isKeyVisible = !it.isKeyVisible) }
    }

    fun submitStudentLogin(isGuest: Boolean = false) {
        val current = _uiState.value
        val roll = if (isGuest) "" else current.rollNumber
        val routeId = current.selectedRouteId

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, generalError = null) }
            val result = authRepository.authenticateStudent(roll)
            if (result.isSuccess) {
                val studentId = result.getOrNull().orEmpty()
                sessionRepository.saveStudentSession(studentId, routeId)
                _uiState.update { it.copy(isLoading = false) }
                _navEvents.send(LoginNavEvent.NavigateToStudent(routeId))
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        generalError = result.exceptionOrNull()?.localizedMessage ?: "Student login failed"
                    )
                }
            }
        }
    }

    fun submitDriverLogin() {
        val current = _uiState.value
        val email = current.driverEmail.trim()
        val key = current.driverSecretKey.trim()

        if (email.isEmpty() || !email.contains("@")) {
            _uiState.update { it.copy(emailError = "Please enter your registered driver email") }
            return
        }
        if (key.length < 4) {
            _uiState.update { it.copy(keyError = "Access key must be at least 4 digits") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, generalError = null) }
            val result = authRepository.authenticateDriver(email, key)
            if (result.isSuccess) {
                val driverId = result.getOrNull().orEmpty()
                val routeId = current.driverRouteId
                sessionRepository.saveDriverSession(driverId, routeId)
                _uiState.update { it.copy(isLoading = false) }
                _navEvents.send(LoginNavEvent.NavigateToDriver(routeId))
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        generalError = result.exceptionOrNull()?.localizedMessage ?: "Driver authentication failed"
                    )
                }
            }
        }
    }
}
