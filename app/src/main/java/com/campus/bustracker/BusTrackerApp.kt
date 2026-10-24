package com.campus.bustracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration
import java.io.File

@HiltAndroidApp
class BusTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize osmdroid configuration
        val osmConfig = Configuration.getInstance()
        osmConfig.load(this, getSharedPreferences("osmdroid_prefs", MODE_PRIVATE))
        // OpenStreetMap tile servers require a descriptive, unique User-Agent to prevent HTTP 403 blocks
        osmConfig.userAgentValue = "CampusBusTracker/1.0 (Android; com.campus.bustracker)"

        val basePath = File(filesDir, "osmdroid")
        val tileCache = File(cacheDir, "osmdroid_tiles")
        if (!basePath.exists()) basePath.mkdirs()
        if (!tileCache.exists()) tileCache.mkdirs()

        osmConfig.osmdroidBasePath = basePath
        osmConfig.osmdroidTileCache = tileCache
    }
}
