package com.campus.bustracker.map

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.campus.bustracker.R
import com.campus.bustracker.data.model.BusLocation
import com.campus.bustracker.data.model.RouteData
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private class MapOverlayHolder(val mapView: MapView) {
    var polyline: Polyline? = null
    var polylinePointsSize: Int = -1
    val stopMarkers = mutableListOf<Marker>()
    var busMarker: Marker? = null
}

@Composable
fun OsmMapView(
    routeData: RouteData,
    busLocation: BusLocation?,
    animatedBusPosition: Pair<Double, Double>?,
    approachingStopId: String?,
    followBus: Boolean,
    offlineTileManager: OfflineTileManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
        }
    }

    val overlayHolder = remember(mapView) { MapOverlayHolder(mapView) }

    // Configure offline mode / corridor cache
    LaunchedEffect(routeData.routeId) {
        offlineTileManager.configureOfflineMap(mapView, routeData)
        if (routeData.polylinePoints.isNotEmpty()) {
            val center = routeData.polylinePoints[routeData.polylinePoints.size / 2]
            mapView.post {
                mapView.controller.setZoom(15.5)
                mapView.controller.setCenter(GeoPoint(center.first, center.second))
            }
        }
    }

    // Lifecycle hygiene
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDetach()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            renderOverlays(
                context = context,
                mapView = map,
                holder = overlayHolder,
                routeData = routeData,
                busLocation = busLocation,
                animatedBusPosition = animatedBusPosition,
                approachingStopId = approachingStopId,
                followBus = followBus
            )
        }
    )
}

private fun renderOverlays(
    context: Context,
    mapView: MapView,
    holder: MapOverlayHolder,
    routeData: RouteData,
    busLocation: BusLocation?,
    animatedBusPosition: Pair<Double, Double>?,
    approachingStopId: String?,
    followBus: Boolean
) {
    // 1. Route Polyline
    if (holder.polyline == null || holder.polylinePointsSize != routeData.polylinePoints.size) {
        holder.polyline?.let { mapView.overlays.remove(it) }
        val polyline = Polyline(mapView).apply {
            outlinePaint.color = Color.parseColor("#0D9488") // Deep teal
            outlinePaint.strokeWidth = 14f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND

            val geoPoints = routeData.polylinePoints.map { GeoPoint(it.first, it.second) }
            setPoints(geoPoints)
        }
        mapView.overlays.add(0, polyline)
        holder.polyline = polyline
        holder.polylinePointsSize = routeData.polylinePoints.size
    }

    // 2. Stop Markers
    if (holder.stopMarkers.size != routeData.stops.size) {
        holder.stopMarkers.forEach { mapView.overlays.remove(it) }
        holder.stopMarkers.clear()

        val defaultPin = ContextCompat.getDrawable(context, R.drawable.ic_stop_pin)
        routeData.stops.forEach { stop ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(stop.latitude, stop.longitude)
                title = stop.name
                subDescription = "Stop #${stop.sequenceOrder}"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = defaultPin
            }
            mapView.overlays.add(marker)
            holder.stopMarkers.add(marker)
        }
    }

    // Update stop pin highlights based on approachingStopId
    val nextPin = ContextCompat.getDrawable(context, R.drawable.ic_stop_pin_next)
    val defaultPin = ContextCompat.getDrawable(context, R.drawable.ic_stop_pin)
    routeData.stops.forEachIndexed { index, stop ->
        if (index < holder.stopMarkers.size) {
            val marker = holder.stopMarkers[index]
            val isNext = stop.stopId == approachingStopId
            marker.icon = if (isNext) nextPin else defaultPin
        }
    }

    // 3. Animated Bus Marker
    val displayLat = animatedBusPosition?.first ?: busLocation?.latitude ?: 0.0
    val displayLon = animatedBusPosition?.second ?: busLocation?.longitude ?: 0.0

    if (displayLat != 0.0 && displayLon != 0.0) {
        val busGeoPoint = GeoPoint(displayLat, displayLon)

        if (holder.busMarker == null) {
            val marker = Marker(mapView).apply {
                title = "Bus Location"
                icon = ContextCompat.getDrawable(context, R.drawable.ic_bus_marker)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            mapView.overlays.add(marker)
            holder.busMarker = marker
        }

        holder.busMarker?.apply {
            position = busGeoPoint
            rotation = busLocation?.bearing ?: 0f
            subDescription = "Speed: ${busLocation?.speedKmh ?: 0f} km/h • ${busLocation?.status}"
        }

        if (followBus) {
            mapView.controller.animateTo(busGeoPoint)
        }
    }

    mapView.invalidate()
}
