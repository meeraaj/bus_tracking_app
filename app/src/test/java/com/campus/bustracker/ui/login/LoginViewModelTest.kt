package com.campus.bustracker.ui.login

import com.campus.bustracker.data.model.UserSession
import com.campus.bustracker.data.repository.AuthRepository
import com.campus.bustracker.data.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeAuthRepository = object : AuthRepository {
        override suspend fun authenticateDriver(email: String, secretKey: String): Result<String> {
            return if (email.contains("@") && secretKey.length >= 4) {
                Result.success("driver_uid_123")
            } else {
                Result.failure(IllegalArgumentException("Invalid credentials"))
            }
        }

        override suspend fun authenticateStudent(rollNumber: String): Result<String> {
            return Result.success("student_uid_456")
        }
    }

    private val fakeSessionRepository = object : SessionRepository {
        var savedDriverId: String? = null
        var savedStudentId: String? = null
        var savedRouteId: String? = null

        override val sessionFlow: Flow<UserSession> = flowOf(UserSession.None)

        override suspend fun saveDriverSession(driverId: String, routeId: String) {
            savedDriverId = driverId
            savedRouteId = routeId
        }

        override suspend fun saveStudentSession(identifier: String, routeId: String) {
            savedStudentId = identifier
            savedRouteId = routeId
        }

        override suspend fun clearSession() {
            savedDriverId = null
            savedStudentId = null
            savedRouteId = null
        }
    }

    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = LoginViewModel(fakeAuthRepository, fakeSessionRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `driver login with invalid email sets emailError`() {
        viewModel.onDriverEmailChanged("invalid-email")
        viewModel.onDriverSecretKeyChanged("1234")
        viewModel.submitDriverLogin()

        assertEquals("Invalid email format", viewModel.uiState.value.emailError)
        assertNull(fakeSessionRepository.savedDriverId)
    }

    @Test
    fun `driver login with valid credentials saves session and completes`() = runTest {
        viewModel.onDriverEmailChanged("driver@campus.edu")
        viewModel.onDriverSecretKeyChanged("9876")
        viewModel.onRouteSelected("route_85")

        viewModel.submitDriverLogin()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.emailError)
        assertNull(viewModel.uiState.value.keyError)
        assertEquals("driver_uid_123", fakeSessionRepository.savedDriverId)
        assertEquals("route_85", fakeSessionRepository.savedRouteId)
    }

    @Test
    fun `student guest login succeeds without roll number`() = runTest {
        viewModel.onRouteSelected("route_85")
        viewModel.submitStudentLogin(isGuest = true)
        advanceUntilIdle()

        assertNotNull(fakeSessionRepository.savedStudentId)
        assertEquals("route_85", fakeSessionRepository.savedRouteId)
    }
}
