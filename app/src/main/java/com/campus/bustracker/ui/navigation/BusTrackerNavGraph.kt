package com.campus.bustracker.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.campus.bustracker.data.model.UserSession
import com.campus.bustracker.data.repository.SessionRepository
import com.campus.bustracker.ui.driver.DriverDashboardScreen
import com.campus.bustracker.ui.driver.DriverViewModel
import com.campus.bustracker.ui.login.LoginScreen
import com.campus.bustracker.ui.login.LoginViewModel
import com.campus.bustracker.ui.student.StudentMapScreen
import com.campus.bustracker.ui.student.StudentViewModel
import com.campus.bustracker.ui.theme.AmberPrimary

@Composable
fun BusTrackerNavGraph(
    sessionRepository: SessionRepository,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    val sessionState by sessionRepository.sessionFlow.collectAsStateWithLifecycle(initialValue = null)

    val currentSession = sessionState
    if (currentSession == null) {
        // Splash / Session resolving state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = AmberPrimary)
        }
        return
    }

    val startDestination: ScreenRoute = when (currentSession) {
        is UserSession.Driver -> ScreenRoute.DriverDashboard(currentSession.routeId)
        is UserSession.Student -> ScreenRoute.StudentMap(currentSession.routeId)
        UserSession.None -> ScreenRoute.Login
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // Login Destination
        composable<ScreenRoute.Login> {
            val loginViewModel: LoginViewModel = hiltViewModel()
            LoginScreen(
                viewModel = loginViewModel,
                onNavigateToDriver = { routeId ->
                    navController.navigate(ScreenRoute.DriverDashboard(routeId)) {
                        popUpTo<ScreenRoute.Login> { inclusive = true }
                    }
                },
                onNavigateToStudent = { routeId ->
                    navController.navigate(ScreenRoute.StudentMap(routeId)) {
                        popUpTo<ScreenRoute.Login> { inclusive = true }
                    }
                }
            )
        }

        // Student Map Destination
        // Guardrail 5: StudentViewModel strictly scoped to this destination
        composable<ScreenRoute.StudentMap> {
            val studentViewModel: StudentViewModel = hiltViewModel()
            StudentMapScreen(
                viewModel = studentViewModel,
                onSignOut = {
                    navController.navigate(ScreenRoute.Login) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Driver Dashboard Destination
        // Guardrail 5: DriverViewModel strictly scoped to this destination
        composable<ScreenRoute.DriverDashboard> {
            val driverViewModel: DriverViewModel = hiltViewModel()
            DriverDashboardScreen(
                viewModel = driverViewModel,
                onSignOut = {
                    navController.navigate(ScreenRoute.Login) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
