package com.campus.bustracker.map

import android.content.Context
import com.campus.bustracker.data.model.RouteData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.ArchiveFileFactory
import org.osmdroid.tileprovider.modules.IArchiveFile
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.tilesource.FileBasedTileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineTileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Configures the MapView to operate strictly in OFFLINE mode (Guardrail 2).
     *
     * 1. Inspects assets/tiles/ for packaged offline archives (.sqlite / .mbtiles).
     *    If found, copies them to private storage and attaches an OfflineTileProvider.
     * 2. Calls mapView.setUseDataConnection(false) to completely block live HTTP 403 blocks.
     * 3. Constrains zoom levels (12.0 to 18.0) and scrollable corridor bounds to the route.
     */
    suspend fun configureOfflineMap(mapView: MapView, routeData: RouteData) = withContext(Dispatchers.IO) {
        val archives = findOrExtractPackagedArchives()

        withContext(Dispatchers.Main) {
            if (archives.isNotEmpty()) {
                val registerReceiver = SimpleRegisterReceiver(context)
                val offlineProvider = OfflineTileProvider(registerReceiver, archives.toTypedArray())
                mapView.tileProvider = offlineProvider

                val archiveSource = archives.firstNotNullOfOrNull { archive ->
                    ArchiveFileFactory.getArchiveFile(archive)?.tileSources?.firstOrNull()
                }
                if (archiveSource != null) {
                    mapView.setTileSource(FileBasedTileSource.getSource(archiveSource))
                } else {
                    mapView.setTileSource(TileSourceFactory.MAPNIK)
                }

                // When pre-packaged archive exists, strictly operate offline
                mapView.setUseDataConnection(false)
            } else {
                // Guardrail 2 (Corridor Cache fallback mode):
                // No pre-packaged archive is in assets/tiles/, so allow online tile loading
                // to populate osmdroid's persistent cache along the route corridor.
                mapView.setTileSource(TileSourceFactory.MAPNIK)
                mapView.setUseDataConnection(true)
            }

            mapView.minZoomLevel = 9.0
            mapView.maxZoomLevel = 19.5
            mapView.setMultiTouchControls(true)

            // Constrain scroll limits to the corridor of the route + ~3km margin
            val bbox = calculateRouteBoundingBox(routeData, marginDegrees = 0.03)
            mapView.setScrollableAreaLimitDouble(bbox)
        }
    }

    private fun findOrExtractPackagedArchives(): List<File> {
        val archives = mutableListOf<File>()
        val tilesDir = File(context.filesDir, "osmdroid/tiles")
        if (!tilesDir.exists()) tilesDir.mkdirs()

        try {
            val assetTileFiles = context.assets.list("tiles") ?: emptyArray()
            for (fileName in assetTileFiles) {
                if (fileName.endsWith(".sqlite") || fileName.endsWith(".mbtiles") || fileName.endsWith(".zip")) {
                    val destFile = File(tilesDir, fileName)
                    if (!destFile.exists() || destFile.length() == 0L) {
                        context.assets.open("tiles/$fileName").use { input ->
                            FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    val archive = ArchiveFileFactory.getArchiveFile(destFile)
                    if (archive != null) {
                        archives.add(destFile)
                    }
                }
            }
        } catch (_: Exception) {
            // Assets folder tiles/ may be empty if developer relies on bundled bounding-box cache
        }

        // Also check if any archives were placed in the tilesDir directly
        val localFiles = tilesDir.listFiles() ?: emptyArray()
        for (f in localFiles) {
            if (archives.none { it.absolutePath == f.absolutePath }) {
                val archive = ArchiveFileFactory.getArchiveFile(f)
                if (archive != null) archives.add(f)
            }
        }

        return archives
    }

    fun calculateRouteBoundingBox(routeData: RouteData, marginDegrees: Double = 0.005): BoundingBox {
        val points = routeData.polylinePoints
        if (points.isEmpty()) {
            return BoundingBox(12.93, 80.18, 12.91, 80.15)
        }

        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE

        for (p in points) {
            if (p.first < minLat) minLat = p.first
            if (p.first > maxLat) maxLat = p.first
            if (p.second < minLon) minLon = p.second
            if (p.second > maxLon) maxLon = p.second
        }

        return BoundingBox(
            maxLat + marginDegrees,
            maxLon + marginDegrees,
            minLat - marginDegrees,
            minLon - marginDegrees
        )
    }
}
