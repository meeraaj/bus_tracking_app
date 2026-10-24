package com.campus.bustracker.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object LocationServiceController {

    fun startTracking(context: Context, routeId: String) {
        val intent = Intent(context, LocationService::class.java).apply {
            action = LocationService.ACTION_START
            putExtra(LocationService.EXTRA_ROUTE_ID, routeId)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopTracking(context: Context) {
        val intent = Intent(context, LocationService::class.java).apply {
            action = LocationService.ACTION_STOP
        }
        context.startService(intent)
    }
}
