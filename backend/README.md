# Campus Bus Tracker — Go WebSocket & REST Backend

A lightweight, zero-dependency Go 1.22 real-time telemetry server with RFC 6455 WebSockets, in-memory Goroutine Pub/Sub (`RouteHub`), and a 6-layer security pipeline.

---

## 1. Architecture & Endpoints

| Method | Path | Auth | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/healthz` | Public | Cloud liveness probe (`{"status":"ok"}`) |
| `POST` | `/api/v1/auth/driver` | Public (Rate-limited) | Verifies `{routeId, pin}` in constant-time and issues an 8-hour HMAC-SHA256 JWT (`role: "driver"`, `routeId`) |
| `POST` | `/api/v1/auth/student` | Public (Rate-limited) | Issues a 24-hour read-only HMAC-SHA256 JWT (`role: "student"`, `routeId`) |
| `GET` | `/api/v1/routes/{routeId}` | Bearer JWT | Returns static road-snapped polyline & stops |
| `GET` | `/api/v1/routes/{routeId}/location` | Bearer JWT | Returns latest cached `BusLocation` snapshot |
| `WS` | `/ws/routes/{routeId}/driver` | Bearer JWT (`driver`) | Driver 3s GPS ingestion stream + 15s dead-man's switch |
| `WS` | `/ws/routes/{routeId}/subscribe` | Bearer JWT (`student`/`driver`) | Real-time sub-50ms fan-out broadcast to all students |

---

## 2. Cloud Hosting Options (One-Click / CLI)

Because the backend uses **100% pure Go standard library** (zero external dependencies) and includes a multi-stage `Dockerfile`, you do **not** even need Go installed locally to deploy to the cloud.

### Option A: Render.com (Simplest — Free Tier Available)
1. Push this repository (`bus_tracking_app`) to GitHub.
2. Go to [dashboard.render.com](https://dashboard.render.com/) -> **New +** -> **Blueprint**.
3. Connect your GitHub repository. Render will automatically detect `render.yaml` at the root of the repo.
4. Click **Apply**. Render will build `backend/Dockerfile`, auto-generate a cryptographic `JWT_SECRET`, and give you an HTTPS URL like:
   - HTTPS Base URL: `https://campus-bustracker-go.onrender.com`
   - WSS Base URL: `wss://campus-bustracker-go.onrender.com`

### Option B: Railway.app (Instant GitHub Deploy)
1. Push this repository to GitHub.
2. Go to [railway.app](https://railway.app/) -> **New Project** -> **Deploy from GitHub repo**.
3. Set the **Root Directory** to `/backend` (Railway automatically detects `backend/railway.json` and `backend/Dockerfile`).
4. In the **Variables** tab, add:
   - `JWT_SECRET` = *(any random 32+ character secret)*
   - `DRIVER_PIN_85` = `8585`
5. Generate a Public Domain under **Settings -> Networking**.

### Option C: Fly.io (Chennai `maa` Edge Region — Lowest Latency)
1. Install `flyctl` (`powershell -Command "iwr https://fly.io/install.ps1 -useb | iex"`).
2. Run from `c:\Users\meera\bus_tracking_app\backend`:
   ```powershell
   cd c:\Users\meera\bus_tracking_app\backend
   fly auth login
   fly launch --copy-config --no-deploy
   fly secrets set JWT_SECRET="replace-with-strong-secret-key-32-bytes" DRIVER_PIN_85="8585"
   fly deploy
   ```
3. Your server will be live at `https://campus-bustracker-go.fly.dev` (`wss://campus-bustracker-go.fly.dev`).

---

## 3. Connecting the Android App to Your Cloud URL

Open `app/build.gradle.kts` and set `BACKEND_BASE_URL` and `BACKEND_WS_URL` in `defaultConfig` (or in `local.properties`):

```kotlin
buildConfigField("String", "BACKEND_BASE_URL", "\"https://your-app-name.onrender.com\"")
buildConfigField("String", "BACKEND_WS_URL", "\"wss://your-app-name.onrender.com\"")
```

When `BACKEND_BASE_URL` is non-empty, `SmartAuthRepository` and `SmartBusTrackingRepository` automatically switch from the local Mock simulator to the live Go WebSocket backend (`GoAuthRepository` + `GoWebSocketBusTrackingRepository`).
