package com.campus.bustracker.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.campus.bustracker.BuildConfig
import com.campus.bustracker.MainActivity
import com.campus.bustracker.R
import com.campus.bustracker.data.model.BusLocation
import com.campus.bustracker.data.repository.BusTrackingRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocationService : LifecycleService() {

    @Inject
    lateinit var fusedLocationClient: FusedLocationProviderClient

    @Inject
    lateinit var busTrackingRepository: BusTrackingRepository

    @Inject
    lateinit var trackingStateHolder: TrackingStateHolder

    private var activeRouteId: String = BuildConfig.DEFAULT_ROUTE_ID
    private var isTrackingActive: Boolean = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location: Location = result.lastLocation ?: return
            handleNewLocation(location)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                val routeId = intent.getStringExtra(EXTRA_ROUTE_ID) ?: BuildConfig.DEFAULT_ROUTE_ID
                startTrackingBroadcast(routeId)
            }
            ACTION_STOP -> {
                stopTrackingBroadcast()
            }
        }

        return START_REDELIVER_INTENT
    }

    private fun startTrackingBroadcast(routeId: String) {
        if (isTrackingActive && activeRouteId == routeId) return
        activeRouteId = routeId
        isTrackingActive = true

        createNotificationChannel()
        val initialNotification = buildNotification("Connecting to GPS…", 0f)

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            initialNotification,
            fgsType
        )

        trackingStateHolder.updateTelemetry {
            it.copy(
                isBroadcasting = true,
                transmissionIntervalMs = BuildConfig.LOCATION_INTERVAL_MS,
                connectionStatus = ConnectionStatus.CONNECTED
            )
        }

        requestFusedLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun requestFusedLocationUpdates() {
        val intervalMs = BuildConfig.LOCATION_INTERVAL_MS
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            trackingStateHolder.updateTelemetry {
                it.copy(
                    connectionStatus = ConnectionStatus.ERROR,
                    lastError = "Location permission revoked: ${e.localizedMessage}"
                )
            }
            stopTrackingBroadcast()
        }
    }

    private fun handleNewLocation(location: Location) {
        // Anti-spoofing guard: block mocked/fake GPS providers in production release builds
        val isMocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }
        if (!BuildConfig.DEBUG && isMocked) {
            trackingStateHolder.updateTelemetry {
                it.copy(
                    connectionStatus = ConnectionStatus.ERROR,
                    lastError = "Security alert: Mock/spoofed GPS provider rejected"
                )
            }
            return
        }

        val speedKmh = (location.speed * 3.6f).coerceAtLeast(0f)
        val bearing = if (location.hasBearing()) location.bearing else 0f
        val timestamp = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()

        val busLoc = BusLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            bearing = bearing,
            speedKmh = speedKmh,
            timestamp = timestamp,
            status = BusLocation.STATUS_ACTIVE
        )

        // Broadcast current coordinates to /routes/{routeId}/live_location
        lifecycleScope.launch {
            trackingStateHolder.updateTelemetry {
                it.copy(connectionStatus = ConnectionStatus.TRANSMITTING)
            }

            val result = busTrackingRepository.updateDriverLocation(activeRouteId, busLoc)
            if (result.isSuccess) {
                trackingStateHolder.updateTelemetry { current ->
                    current.copy(
                        currentSpeedKmh = speedKmh,
                        accuracyMeters = location.accuracy,
                        lastTransmittedMillis = timestamp,
                        packetsSentCount = current.packetsSentCount + 1,
                        connectionStatus = ConnectionStatus.CONNECTED,
                        lastError = null
                    )
                }
                updateForegroundNotification(speedKmh)
            } else {
                trackingStateHolder.updateTelemetry {
                    it.copy(
                        connectionStatus = ConnectionStatus.ERROR,
                        lastError = result.exceptionOrNull()?.localizedMessage
                    )
                }
            }
        }
    }

    private fun updateForegroundNotification(speedKmh: Float) {
        val intervalSec = (BuildConfig.LOCATION_INTERVAL_MS / 1000).toInt()
        val text = getString(R.string.tracking_notification_text, String.format("%.0f", speedKmh), intervalSec)
        val notification = buildNotification(text, speedKmh)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(contentText: String, speedKmh: Float): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bus)
            .setContentTitle(getString(R.string.tracking_notification_title, activeRouteId.uppercase()))
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_notification_bus,
                getString(R.string.tracking_notification_action_end),
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.tracking_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.tracking_channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopTrackingBroadcast() {
        if (!isTrackingActive) return
        isTrackingActive = false

        fusedLocationClient.removeLocationUpdates(locationCallback)

        // Mark driver status as OFFLINE on trip end or disconnect (Guardrail 1 & 6)
        lifecycleScope.launch {
            busTrackingRepository.setDriverStatus(activeRouteId, BusLocation.STATUS_OFFLINE)
        }

        trackingStateHolder.updateTelemetry {
            it.copy(
                isBroadcasting = false,
                connectionStatus = ConnectionStatus.IDLE
            )
        }

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopTrackingBroadcast()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.campus.bustracker.action.START_TRACKING"
        const val ACTION_STOP = "com.campus.bustracker.action.STOP_TRACKING"
        const val EXTRA_ROUTE_ID = "extra_route_id"
        private const val NOTIFICATION_ID = 4040
        private const val CHANNEL_ID = "bus_tracking_service_channel"
    }
}
