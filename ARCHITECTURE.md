# Technical Architecture & Guardrail Validation

## 1. Directory Structure

```text
bus_tracking_app/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/
│   │   │   │   ├── routes/
│   │   │   │   │   └── route_85.json           # Road-snapped static route asset
│   │   │   │   └── tiles/
│   │   │   │       └── README.md               # Guide for .sqlite / .mbtiles drop-in
│   │   │   ├── java/com/campus/bustracker/
│   │   │   │   ├── BusTrackerApp.kt            # @HiltAndroidApp + osmdroid base init
│   │   │   │   ├── MainActivity.kt             # Single entry; 0 ViewModels (Guardrail 5)
│   │   │   │   ├── data/
│   │   │   │   │   ├── local/
│   │   │   │   │   │   ├── DataStoreSessionRepository.kt
│   │   │   │   │   │   └── RouteAssetDataSource.kt  # Read once & memory-cached
│   │   │   │   │   ├── model/
│   │   │   │   │   │   ├── BusLocation.kt
│   │   │   │   │   │   ├── RouteData.kt
│   │   │   │   │   │   ├── RouteStop.kt
│   │   │   │   │   │   └── UserSession.kt
│   │   │   │   │   ├── remote/
│   │   │   │   │   │   └── RtdbPaths.kt            # /routes/{routeId}/live_location
│   │   │   │   │   └── repository/
│   │   │   │   │       ├── AuthRepository.kt
│   │   │   │   │       ├── BusTrackingRepository.kt
│   │   │   │   │       ├── SessionRepository.kt
│   │   │   │   │       ├── firebase/
│   │   │   │   │       │   └── FirebaseBusTrackingRepository.kt
│   │   │   │   │       └── mock/
│   │   │   │   │           ├── MockAuthRepository.kt
│   │   │   │   │           └── MockBusTrackingRepository.kt
│   │   │   │   ├── di/
│   │   │   │   │   ├── AppModule.kt
│   │   │   │   │   └── RepositoryModule.kt
│   │   │   │   ├── domain/
│   │   │   │   │   ├── BusPathInterpolator.kt  # Sub-path curvature interpolation
│   │   │   │   │   ├── NextStopCalculator.kt   # Non-sticking next stop algorithm
│   │   │   │   │   └── geo/
│   │   │   │   │       ├── GeoMath.kt
│   │   │   │   │       └── PolylineSnapper.kt
│   │   │   │   ├── map/
│   │   │   │   │   ├── OfflineTileManager.kt   # setUseDataConnection(false)
│   │   │   │   │   └── OsmMapView.kt           # Lifecycle-aware Compose wrapper
│   │   │   │   ├── service/
│   │   │   │   │   ├── LocationService.kt      # Foreground service (location type)
│   │   │   │   │   ├── LocationServiceController.kt
│   │   │   │   │   └── TrackingStateHolder.kt
│   │   │   │   └── ui/
│   │   │   │       ├── common/
│   │   │   │       │   └── Components.kt       # GlassCard, StatusPill, TelemetryTile
│   │   │   │       ├── driver/
│   │   │   │       │   ├── DriverDashboardScreen.kt
│   │   │   │       │   ├── DriverUiState.kt
│   │   │   │       │   └── DriverViewModel.kt
│   │   │   │       ├── login/
│   │   │   │       │   ├── LoginScreen.kt
│   │   │   │       │   ├── LoginUiState.kt
│   │   │   │       │   └── LoginViewModel.kt
│   │   │   │       ├── navigation/
│   │   │   │       │   ├── BusTrackerNavGraph.kt
│   │   │   │       │   └── Routes.kt
│   │   │   │       ├── student/
│   │   │   │       │   ├── StudentMapScreen.kt
│   │   │   │       │   ├── StudentUiState.kt
│   │   │   │       │   ├── StudentViewModel.kt
│   │   │   │       │   └── components/
│   │   │   │       │       └── ApproachingStopBanner.kt
│   │   │   │       └── theme/
│   │   │   │           ├── Color.kt
│   │   │   │           ├── Theme.kt
│   │   │   │           └── Type.kt
│   │   │   └── res/
│   │   │       ├── drawable/
│   │   │       │   ├── ic_bus_marker.xml       # Heading-aligned bus marker
│   │   │       │   ├── ic_notification_bus.xml
│   │   │       │   ├── ic_stop_pin.xml
│   │   │       │   └── ic_stop_pin_next.xml
│   │   │       └── values/
│   │   │           ├── colors.xml
│   │   │           ├── strings.xml
│   │   │           └── themes.xml
│   │   └── test/java/com/campus/bustracker/
│   │       ├── domain/
│   │       │   ├── NextStopCalculatorTest.kt
│   │       │   └── geo/PolylineSnapperTest.kt
│   │       └── ui/login/LoginViewModelTest.kt
│   └── build.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── tools/
│   └── generate-route-polyline.ps1             # Developer tool (OSRM ran once here)
├── README.md
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── settings.gradle.kts
```

---

## 2. Mathematical Rigor & Next Stop Matching

Next stop evaluation strictly enforces the non-sticking rule:
$$\text{isApproaching} = (\text{stopIndices}[i] > \text{busPolyIndex}) \lor (\text{distToStop} \le 25.0\text{ m})$$

### Monotonic Stop Progression
1. **Pre-computation**: Stops are mapped monotonically to the closest vertex index `stopIndices[i]` along the road polyline.
2. **Evaluation**: When a bus passes stop $k$ (`busPolyIndex >= stopIndices[k]` and `distToStop > 25.0`), condition $(\text{stopIndices}[k] > \text{busPolyIndex})$ becomes `false`. The algorithm immediately skips past stop $k$ and selects stop $k+1$.
3. **Arrival Window**: When $\text{distToStop} \le 25\text{ m}$, `isArriving` evaluates to `true`, displaying the pulsing arrival banner and notifying students that the bus has pulled up.
4. **ETA Calculation**:
   $$\text{ETA} = \frac{\text{Distance Along Route (m)}}{\max(\text{Speed (m/s)}, 5.0\text{ m/s})}$$
   The $5.0\text{ m/s}$ ($18\text{ km/h}$) floor guarantees that traffic stops and red lights do not blow up the ETA.
