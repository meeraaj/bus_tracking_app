package com.campus.bustracker.data.repository

import android.content.Context
import com.campus.bustracker.BuildConfig
import com.campus.bustracker.data.model.BusLocation
import com.campus.bustracker.data.model.RouteData
import com.campus.bustracker.data.repository.firebase.FirebaseBusTrackingRepository
import com.campus.bustracker.data.repository.go.GoWebSocketBusTrackingRepository
import com.campus.bustracker.data.repository.mock.MockBusTrackingRepository
import com.google.firebase.FirebaseApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Priority router for bus telemetry:
 * 1. [GoWebSocketBusTrackingRepository] when `BuildConfig.BACKEND_BASE_URL` is configured.
 * 2. [FirebaseBusTrackingRepository] when `google-services.json` is present.
 * 3. [MockBusTrackingRepository] fallback for instant offline/emulator demos.
 */
@Singleton
class SmartBusTrackingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val goWebSocketRepository: GoWebSocketBusTrackingRepository,
    private val firebaseRepository: FirebaseBusTrackingRepository,
    private val mockRepository: MockBusTrackingRepository
) : BusTrackingRepository {

    val isGoBackendConfigured: Boolean
        get() = BuildConfig.BACKEND_BASE_URL.isNotBlank()

    val isFirebaseConfigured: Boolean
        get() = FirebaseApp.getApps(context).isNotEmpty()

    private val activeDelegate: BusTrackingRepository
        get() = when {
            isGoBackendConfigured -> goWebSocketRepository
            isFirebaseConfigured -> firebaseRepository
            else -> mockRepository
        }

    override suspend fun getRouteDetails(routeId: String): RouteData {
        return activeDelegate.getRouteDetails(routeId)
    }

    override fun observeBusLocation(routeId: String): Flow<BusLocation> {
        return activeDelegate.observeBusLocation(routeId)
    }

    override suspend fun updateDriverLocation(routeId: String, location: BusLocation): Result<Unit> {
        return activeDelegate.updateDriverLocation(routeId, location)
    }

    override suspend fun setDriverStatus(routeId: String, status: String): Result<Unit> {
        return activeDelegate.setDriverStatus(routeId, status)
    }
}
