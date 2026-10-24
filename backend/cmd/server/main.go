package main

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/campus/bustracker-backend/internal/auth"
	"github.com/campus/bustracker-backend/internal/hub"
	"github.com/campus/bustracker-backend/internal/model"
	"github.com/campus/bustracker-backend/internal/routes"
	"github.com/campus/bustracker-backend/internal/security"
	"github.com/campus/bustracker-backend/internal/ws"
)

type Server struct {
	authService   *auth.Service
	hubManager    *hub.Manager
	routeStore    *routes.Store
	authLimiter   *security.TokenBucketLimiter
	driverLimiter *security.TokenBucketLimiter
}

func main() {
	port := envOrDefault("PORT", "8080")
	jwtSecret := os.Getenv("JWT_SECRET")
	if len(jwtSecret) < 32 {
		randomBytes := make([]byte, 32)
		_, _ = rand.Read(randomBytes)
		jwtSecret = hex.EncodeToString(randomBytes)
		log.Println("[SECURITY] JWT_SECRET not set or <32 chars; generated ephemeral 256-bit key for this instance")
	}

	driverEmail := envOrDefault("DRIVER_EMAIL", "driver@campus.edu")
	driverKey := envOrDefault("DRIVER_SECRET_KEY", "1234")
	driverRoute := envOrDefault("DRIVER_ROUTE_ID", "route_85")

	driverPin85 := envOrDefault("DRIVER_PIN_85", "8585")
	driverPinDefault := envOrDefault("DRIVER_PIN_DEFAULT", "1234")

	authService := auth.NewService(jwtSecret, []auth.DriverAccount{
		{
			DriverID:      "driver_route_85",
			Email:         "driver85@campus.edu",
			SecretKey:     driverPin85,
			AssignedRoute: "route_85",
			Active:        true,
		},
		{
			DriverID:      "driver_route_85_alt",
			Email:         driverEmail,
			SecretKey:     driverKey,
			AssignedRoute: driverRoute,
			Active:        true,
		},
		{
			DriverID:      "driver_generic",
			Email:         "driver@campus.edu",
			SecretKey:     driverPinDefault,
			AssignedRoute: "route_85",
			Active:        true,
		},
	}, 12*time.Hour)

	routeStore, err := routes.NewStore()
	if err != nil {
		log.Fatalf("failed to load embedded route store: %v", err)
	}

	srv := &Server{
		authService:   authService,
		hubManager:    hub.NewManager(),
		routeStore:    routeStore,
		authLimiter:   security.NewTokenBucketLimiter(0.2, 6.0), // ~12 req/min with burst of 6 per IP
		driverLimiter: security.NewTokenBucketLimiter(2.0, 4.0), // max 2 GPS frames/sec per driver
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", srv.handleHealthz)
	mux.HandleFunc("/api/v1/auth/driver", srv.handleDriverAuth)
	mux.HandleFunc("/api/v1/auth/student", srv.handleStudentAuth)
	mux.HandleFunc("/api/v1/routes/", srv.handleRoutesSubtree)
	mux.HandleFunc("/ws/routes/", srv.handleWebSocketSubtree)

	httpServer := &http.Server{
		Addr:              ":" + port,
		Handler:           securityHeadersMiddleware(mux),
		ReadHeaderTimeout: 5 * time.Second,
	}

	go func() {
		log.Printf("[SERVER] Go Bus Tracker WebSocket & REST Backend listening on :%s", port)
		if err := httpServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("server error: %v", err)
		}
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)
	<-stop

	log.Println("[SERVER] Shutting down gracefully...")
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	_ = httpServer.Shutdown(ctx)
}

func (s *Server) handleHealthz(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{
		"status":  "ok",
		"service": "campus-bus-tracker-go",
	})
}

func (s *Server) handleDriverAuth(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	clientIP := extractClientIP(r)
	if !s.authLimiter.Allow("driver_auth_"+clientIP, time.Now()) {
		writeError(w, http.StatusTooManyRequests, "too many authentication attempts; try again shortly")
		return
	}

	r.Body = http.MaxBytesReader(w, r.Body, 4096)
	var req model.DriverAuthRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}

	resp, err := s.authService.AuthenticateDriver(req.Email, req.SecretKey, req.RouteID, time.Now())
	if err != nil {
		writeError(w, http.StatusUnauthorized, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, resp)
}

func (s *Server) handleStudentAuth(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	clientIP := extractClientIP(r)
	if !s.authLimiter.Allow("student_auth_"+clientIP, time.Now()) {
		writeError(w, http.StatusTooManyRequests, "too many requests")
		return
	}

	r.Body = http.MaxBytesReader(w, r.Body, 4096)
	var req model.StudentAuthRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}

	resp, err := s.authService.AuthenticateStudent(req.RollNumber, req.RouteID, time.Now())
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, resp)
}

// Handles:
// - GET /api/v1/routes/{routeId}
// - PUT /api/v1/routes/{routeId}/location
func (s *Server) handleRoutesSubtree(w http.ResponseWriter, r *http.Request) {
	claims, err := s.authenticateRequest(r)
	if err != nil {
		writeError(w, http.StatusUnauthorized, err.Error())
		return
	}

	trimmed := strings.TrimPrefix(r.URL.Path, "/api/v1/routes/")
	parts := strings.Split(strings.Trim(trimmed, "/"), "/")
	if len(parts) == 0 || !security.IsValidRouteID(parts[0]) {
		writeError(w, http.StatusBadRequest, "invalid routeId")
		return
	}
	routeID := parts[0]

	// GET /api/v1/routes/{routeId}
	if len(parts) == 1 && r.Method == http.MethodGet {
		routeData, err := s.routeStore.Get(routeID)
		if err != nil {
			writeError(w, http.StatusNotFound, "route not found")
			return
		}
		writeJSON(w, http.StatusOK, routeData)
		return
	}

	// GET /api/v1/routes/{routeId}/location
	if len(parts) == 2 && parts[1] == "location" && r.Method == http.MethodGet {
		routeHub := s.hubManager.GetOrCreate(routeID)
		writeJSON(w, http.StatusOK, routeHub.Latest())
		return
	}

	// PUT /api/v1/routes/{routeId}/location
	if len(parts) == 2 && parts[1] == "location" && r.Method == http.MethodPut {
		if claims.Role != model.RoleDriver || claims.RouteID != routeID {
			writeError(w, http.StatusForbidden, "forbidden: driver token is not authorized for this route")
			return
		}

		if !s.driverLimiter.Allow(claims.Sub, time.Now()) {
			writeError(w, http.StatusTooManyRequests, "telemetry rate limit exceeded")
			return
		}

		r.Body = http.MaxBytesReader(w, r.Body, security.MaxWebSocketFrameB)
		var loc model.BusLocation
		if err := json.NewDecoder(r.Body).Decode(&loc); err != nil {
			writeError(w, http.StatusBadRequest, "invalid location JSON")
			return
		}

		sanitized, err := security.ValidateAndSanitizeLocation(loc, time.Now())
		if err != nil {
			writeError(w, http.StatusUnprocessableEntity, err.Error())
			return
		}

		routeHub := s.hubManager.GetOrCreate(routeID)
		routeHub.Publish(sanitized)
		writeJSON(w, http.StatusOK, map[string]string{"status": "published"})
		return
	}

	writeError(w, http.StatusNotFound, "endpoint not found")
}

// Handles:
// - WS /ws/routes/{routeId}/driver
// - WS /ws/routes/{routeId}/subscribe
func (s *Server) handleWebSocketSubtree(w http.ResponseWriter, r *http.Request) {
	claims, err := s.authenticateRequest(r)
	if err != nil {
		writeError(w, http.StatusUnauthorized, err.Error())
		return
	}

	trimmed := strings.TrimPrefix(r.URL.Path, "/ws/routes/")
	parts := strings.Split(strings.Trim(trimmed, "/"), "/")
	if len(parts) != 2 || !security.IsValidRouteID(parts[0]) {
		writeError(w, http.StatusBadRequest, "expected /ws/routes/{routeId}/driver or /subscribe")
		return
	}

	routeID := parts[0]
	mode := parts[1]

	switch mode {
	case "driver":
		if claims.Role != model.RoleDriver || claims.RouteID != routeID {
			writeError(w, http.StatusForbidden, "forbidden: requires driver role pinned to "+routeID)
			return
		}
		s.serveDriverWebSocket(w, r, routeID, claims.Sub)

	case "subscribe":
		s.serveStudentWebSocket(w, r, routeID, claims.Sub)

	default:
		writeError(w, http.StatusNotFound, "unknown websocket endpoint")
	}
}

func (s *Server) serveDriverWebSocket(w http.ResponseWriter, r *http.Request, routeID, driverID string) {
	conn, err := ws.Upgrade(w, r, security.MaxWebSocketFrameB, 15*time.Second)
	if err != nil {
		return
	}

	routeHub := s.hubManager.GetOrCreate(routeID)
	log.Printf("[WS-DRIVER] Connected driver=%s route=%s", driverID, routeID)

	done := make(chan struct{})
	// Ping keep-alive ticker every 10 seconds
	go func() {
		ticker := time.NewTicker(10 * time.Second)
		defer ticker.Stop()
		for {
			select {
			case <-done:
				return
			case <-ticker.C:
				if err := conn.WritePing(); err != nil {
					_ = conn.Close()
					return
				}
			}
		}
	}()

	// Dead-man's switch: automatically broadcast OFFLINE when driver socket closes or times out
	defer func() {
		close(done)
		_ = conn.Close()
		routeHub.MarkOffline(time.Now())
		log.Printf("[WS-DRIVER] Disconnected driver=%s route=%s -> broadcast OFFLINE", driverID, routeID)
	}()

	for {
		rawFrame, err := conn.ReadText()
		if err != nil {
			return
		}

		if !s.driverLimiter.Allow(driverID, time.Now()) {
			continue
		}

		var incoming model.BusLocation
		if err := json.Unmarshal(rawFrame, &incoming); err != nil {
			continue
		}

		sanitized, err := security.ValidateAndSanitizeLocation(incoming, time.Now())
		if err != nil {
			log.Printf("[SECURITY] Rejected driver=%s frame: %v", driverID, err)
			continue
		}

		routeHub.Publish(sanitized)
	}
}

func (s *Server) serveStudentWebSocket(w http.ResponseWriter, r *http.Request, routeID, subID string) {
	conn, err := ws.Upgrade(w, r, 1024, 60*time.Second)
	if err != nil {
		return
	}

	routeHub := s.hubManager.GetOrCreate(routeID)
	sub, initialSnapshot := routeHub.Subscribe(subID)
	log.Printf("[WS-STUDENT] Subscribed sub=%s route=%s", subID, routeID)

	done := make(chan struct{})
	defer func() {
		close(done)
		routeHub.Unsubscribe(sub)
		_ = conn.Close()
		log.Printf("[WS-STUDENT] Unsubscribed sub=%s route=%s", subID, routeID)
	}()

	// Immediately send the most recent BusLocation snapshot if available
	if initialSnapshot != nil {
		if initBytes, err := json.Marshal(initialSnapshot); err == nil {
			_ = conn.WriteText(initBytes)
		}
	}

	// Reader goroutine to process client Pings/Pongs and detect socket close
	go func() {
		for {
			if _, err := conn.ReadText(); err != nil {
				_ = conn.Close()
				return
			}
		}
	}()

	ticker := time.NewTicker(15 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case msg, ok := <-sub.Send:
			if !ok {
				return
			}
			if err := conn.WriteText(msg); err != nil {
				return
			}
		case <-ticker.C:
			if err := conn.WritePing(); err != nil {
				return
			}
		}
	}
}

func (s *Server) authenticateRequest(r *http.Request) (*auth.Claims, error) {
	authHeader := strings.TrimSpace(r.Header.Get("Authorization"))
	var token string
	if strings.HasPrefix(strings.ToLower(authHeader), "bearer ") {
		token = strings.TrimSpace(authHeader[7:])
	} else {
		token = strings.TrimSpace(r.URL.Query().Get("token"))
	}

	if token == "" {
		return nil, errors.New("missing Authorization Bearer token")
	}
	return s.authService.VerifyToken(token, time.Now())
}

func securityHeadersMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("X-Frame-Options", "DENY")
		w.Header().Set("Strict-Transport-Security", "max-age=63072000; includeSubDomains")
		w.Header().Set("Cache-Control", "no-store")
		next.ServeHTTP(w, r)
	})
}

func extractClientIP(r *http.Request) string {
	if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
		parts := strings.Split(xff, ",")
		return strings.TrimSpace(parts[0])
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}

func writeJSON(w http.ResponseWriter, status int, data any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(data)
}

func writeError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]string{"error": msg})
}

func envOrDefault(key, fallback string) string {
	if v := strings.TrimSpace(os.Getenv(key)); v != "" {
		return v
	}
	return fallback
}
