# Campus Bus Tracker — Android Frontend Client

A production-grade Android bus tracking client built with **Kotlin** and **Jetpack Compose**, designed with an offline-first architecture for university campus fleets.

---

## Architecture & Contract Overview

### 1. Data Layer Contracts
The application is decoupled from backend infrastructure via repository interfaces:

```kotlin
data class BusLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val bearing: Float = 0f,
    val speedKmh: Float = 0f,
    val timestamp: Long = 0L,
    val status: String = "ACTIVE" // "ACTIVE" or "OFFLINE"
)

data class RouteStop(
    val stopId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val sequenceOrder: Int
)

data class RouteData(
    val routeId: String,
    val routeName: String,
    val polylinePoints: List<Pair<Double, Double>>,
    val stops: List<RouteStop>
)
```

### 2. Backend Integration (Go WebSocket Cloud Backend + Firebase Fallback)

The app includes a **Smart Priority Router** (`SmartBusTrackingRepository` and `SmartAuthRepository`):
1. **Go WebSocket & REST Backend (`backend/`)**: Used automatically when `BACKEND_BASE_URL` in `app/build.gradle.kts` is set to your cloud server URL (Render, Railway, Fly.io, or Docker).
   - `POST /api/v1/auth/driver` & `POST /api/v1/auth/student` -> Issues HMAC-SHA256 JWTs (`GoAuthRepository`).
   - `WSS /ws/routes/{routeId}/driver` -> Driver streams 3-second GPS frames over a persistent WebSocket (`GoWebSocketBusTrackingRepository`).
   - `WSS /ws/routes/{routeId}/subscribe` -> Students receive sub-50ms Goroutine Pub/Sub broadcasts (`RouteHub`) with exponential-backoff auto-reconnect.
   - See `backend/README.md`, `backend/Dockerfile`, `render.yaml`, `backend/fly.toml`, and `backend/railway.json` for one-click cloud deployment.
2. **Firebase Realtime Database (`FirebaseBusTrackingRepository`)**: Used automatically if `app/google-services.json` is dropped in and `BACKEND_BASE_URL` is blank.
3. **Mock Simulator (`MockBusTrackingRepository`)**: Default out-of-the-box mode so the app runs immediately in Android Studio without external credentials.

---

## Architectural Guardrails Verified

- **Offline-First Map Tile Strategy**: `OfflineTileManager` checks `assets/tiles/` for bundled `.sqlite` / `.mbtiles` archives, configuring `mapView.setUseDataConnection(false)` so map tiles are rendered strictly offline without triggering OSM 403 Rate Limits.
- **Route Polyline Optimization**: Polylines are pre-computed once using `tools/generate-route-polyline.ps1` and stored in `app/src/main/assets/routes/route_85.json`. Zero calls to `router.project-osrm.org` from client devices.
- **Multi-Bus Fleet Hierarchy**: All telemetry is scoped to `/routes/{routeId}/live_location`.
- **Destination-Scoped ViewModels**: `StudentViewModel` and `DriverViewModel` are instantiated via `hiltViewModel()` strictly inside their respective navigation composable destinations (`MainActivity.kt` contains zero ViewModel instantiations).
- **Background & Lifecycle Hygiene**:
  - Pressing the system back button minimizes the app naturally (`moveTaskToBack(true)`) without ending driver broadcast or signing the user out.
  - Driver foreground service declares `foregroundServiceType="location"` and checks `Manifest.permission.POST_NOTIFICATIONS` for Android 13+ (API 33+).
- **Next Stop Matching Logic**:
  Evaluated in `NextStopCalculator`:
  $$\text{isApproaching} = (\text{stopIndex} > \text{busPolyIndex}) \lor (\text{distanceToStop} \le 25\text{ m})$$
  Ensures approaching stops advance forward immediately without sticking to past stops.

---

## Building & Testing

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug
```
