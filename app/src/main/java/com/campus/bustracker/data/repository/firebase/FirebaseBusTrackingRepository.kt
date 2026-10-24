package com.campus.bustracker.data.repository.firebase

import com.campus.bustracker.data.local.RouteAssetDataSource
import com.campus.bustracker.data.model.BusLocation
import com.campus.bustracker.data.model.RouteData
import com.campus.bustracker.data.remote.RtdbPaths
import com.campus.bustracker.data.repository.BusTrackingRepository
import com.campus.bustracker.domain.security.LocationPayloadValidator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production Firebase Realtime Database implementation with Zero-Trust validation.
 *
 * Enforces:
 * 1. Route ID sanitization (`LocationPayloadValidator.isValidRouteId`).
 * 2. Client-side geofence & kinematic sanitization before every write (`LocationPayloadValidator.validateForBroadcast`).
 * 3. Strict 6-key payload matching `firebase/database.rules.json` (`$other: false`).
 * 4. Server-side `onDisconnect()` dead-man's switch to mark bus `"OFFLINE"` if driver loses signal.
 */
@Singleton
class FirebaseBusTrackingRepository @Inject constructor(
    private val routeAssetDataSource: RouteAssetDataSource
) : BusTrackingRepository {

    private val disconnectHookRegistered = ConcurrentHashMap<String, Boolean>()

    override suspend fun getRouteDetails(routeId: String): RouteData {
        require(LocationPayloadValidator.isValidRouteId(routeId)) {
            "Invalid routeId format: $routeId"
        }
        // Guardrail 3: Always prefer the local road-snapped route asset first
        return routeAssetDataSource.getRouteData(routeId)
    }

    override fun observeBusLocation(routeId: String): Flow<BusLocation> = callbackFlow {
        if (!LocationPayloadValidator.isValidRouteId(routeId)) {
            close(SecurityException("Invalid routeId: $routeId"))
            return@callbackFlow
        }

        val ref = FirebaseDatabase.getInstance().getReference(RtdbPaths.liveLocation(routeId))
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                val parsed = parseSnapshotSafely(snapshot) ?: return
                trySend(parsed)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose {
            ref.removeEventListener(listener)
        }
    }

    override suspend fun updateDriverLocation(routeId: String, location: BusLocation): Result<Unit> {
        return runCatching {
            if (!LocationPayloadValidator.isValidRouteId(routeId)) {
                throw SecurityException("Invalid routeId: $routeId")
            }

            val currentUser = FirebaseAuth.getInstance().currentUser
                ?: throw SecurityException("Unauthenticated driver write blocked")

            // Validate and sanitize telemetry against geofence, speed, and anti-replay rules
            val sanitized = LocationPayloadValidator.validateForBroadcast(location).getOrThrow()

            val ref = FirebaseDatabase.getInstance().getReference(RtdbPaths.liveLocation(routeId))

            // Register server-side onDisconnect dead-man's switch once per active route broadcast
            if (disconnectHookRegistered.putIfAbsent("${currentUser.uid}_$routeId", true) != true) {
                ref.onDisconnect().updateChildren(
                    mapOf(
                        "status" to BusLocation.STATUS_OFFLINE,
                        "speedKmh" to 0.0,
                        "timestamp" to ServerValue.TIMESTAMP
                    )
                ).await()
            }

            // Construct exact 6-property map matching database.rules.json ($other: false)
            val strictPayload = mapOf(
                "latitude" to sanitized.latitude,
                "longitude" to sanitized.longitude,
                "bearing" to sanitized.bearing.toDouble(),
                "speedKmh" to sanitized.speedKmh.toDouble(),
                "timestamp" to sanitized.timestamp,
                "status" to sanitized.status
            )

            ref.setValue(strictPayload).await()
        }
    }

    override suspend fun setDriverStatus(routeId: String, status: String): Result<Unit> {
        return runCatching {
            if (!LocationPayloadValidator.isValidRouteId(routeId)) {
                throw SecurityException("Invalid routeId: $routeId")
            }
            val normalizedStatus = status.uppercase()
            if (normalizedStatus != BusLocation.STATUS_ACTIVE && normalizedStatus != BusLocation.STATUS_OFFLINE) {
                throw SecurityException("Invalid driver status: $status")
            }

            val ref = FirebaseDatabase.getInstance().getReference(RtdbPaths.liveLocation(routeId))
            if (normalizedStatus == BusLocation.STATUS_OFFLINE) {
                // Cancel pending disconnect hook on clean trip end and atomically mark OFFLINE
                ref.onDisconnect().cancel().await()
                disconnectHookRegistered.clear()
            }

            ref.updateChildren(
                mapOf(
                    "status" to normalizedStatus,
                    "speedKmh" to 0.0,
                    "timestamp" to System.currentTimeMillis()
                )
            ).await()
        }
    }

    private fun parseSnapshotSafely(snapshot: DataSnapshot): BusLocation? {
        val lat = snapshot.child("latitude").getValue(Double::class.java) ?: return null
        val lon = snapshot.child("longitude").getValue(Double::class.java) ?: return null
        val bearing = snapshot.child("bearing").getValue(Double::class.java)?.toFloat() ?: 0f
        val speed = snapshot.child("speedKmh").getValue(Double::class.java)?.toFloat() ?: 0f
        val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
        val status = snapshot.child("status").getValue(String::class.java) ?: BusLocation.STATUS_OFFLINE

        if (!lat.isFinite() || !lon.isFinite()) return null

        return BusLocation(
            latitude = lat,
            longitude = lon,
            bearing = bearing.takeIf { it.isFinite() } ?: 0f,
            speedKmh = speed.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f,
            timestamp = timestamp,
            status = status
        )
    }
}
