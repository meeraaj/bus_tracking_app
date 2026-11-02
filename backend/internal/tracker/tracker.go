package tracker

import (
	"encoding/json"
	"fmt"
	"log"
	"math"
	"os"
	"sync"
	"time"

	"github.com/campus/bustracker-backend/internal/model"
	"github.com/campus/bustracker-backend/internal/routes"
)

const (
	EarthRadiusMeters   = 6371000.0
	ArrivalRadiusMeters = 80.0 // 80m detection radius for road corridor bus stops
)

type StopArrivalRecord struct {
	RouteID       string  `json:"routeId"`
	DriverID      string  `json:"driverId"`
	StopID        string  `json:"stopId"`
	StopName      string  `json:"stopName"`
	SequenceOrder int     `json:"sequenceOrder"`
	DistanceM     float64 `json:"distanceMeters"`
	SpeedKmh      float64 `json:"speedKmh"`
	Latitude      float64 `json:"latitude"`
	Longitude     float64 `json:"longitude"`
	Timestamp     int64   `json:"timestamp"`
	FormattedTime string  `json:"formattedTime"`
}

type ServerStopTracker struct {
	mu            sync.RWMutex
	routeStore    *routes.Store
	crossedStops  map[string]map[string]bool // routeId -> stopId -> bool
	arrivalEvents []StopArrivalRecord
	logFilePath   string
}

func NewServerStopTracker(routeStore *routes.Store, logFilePath string) *ServerStopTracker {
	return &ServerStopTracker{
		routeStore:    routeStore,
		crossedStops:  make(map[string]map[string]bool),
		arrivalEvents: make([]StopArrivalRecord, 0),
		logFilePath:   logFilePath,
	}
}

func (t *ServerStopTracker) ProcessDriverTelemetry(routeID, driverID string, loc model.BusLocation, now time.Time) {
	if loc.Status != model.StatusActive {
		return
	}

	routeData, err := t.routeStore.Get(routeID)
	if err != nil {
		return
	}

	t.mu.Lock()
	defer t.mu.Unlock()

	if _, exists := t.crossedStops[routeID]; !exists {
		t.crossedStops[routeID] = make(map[string]bool)
	}

	for _, stop := range routeData.Stops {
		if t.crossedStops[routeID][stop.StopID] {
			continue
		}

		dist := haversineDistanceMeters(loc.Latitude, loc.Longitude, stop.Latitude, stop.Longitude)
		if dist <= ArrivalRadiusMeters {
			t.crossedStops[routeID][stop.StopID] = true

			record := StopArrivalRecord{
				RouteID:       routeID,
				DriverID:      driverID,
				StopID:        stop.StopID,
				StopName:      stop.Name,
				SequenceOrder: stop.SequenceOrder,
				DistanceM:     math.Round(dist*10) / 10,
				SpeedKmh:      math.Round(loc.SpeedKmh*10) / 10,
				Latitude:      loc.Latitude,
				Longitude:     loc.Longitude,
				Timestamp:     now.UnixMilli(),
				FormattedTime: now.Format("2006-01-02 15:04:05 MST"),
			}

			t.arrivalEvents = append(t.arrivalEvents, record)

			// 1. High-visibility stdout server log (visible in `kubectl logs` and terminal)
			logMsg := fmt.Sprintf("🚌 [SERVER PROOF] STOP CROSSED! Route=%s Stop #%d \"%s\" | Dist=%.1fm | Speed=%.1f km/h | Driver=%s | GPS=(%.6f, %.6f) at %s",
				routeID, stop.SequenceOrder, stop.Name, dist, loc.SpeedKmh, driverID, loc.Latitude, loc.Longitude, record.FormattedTime)
			log.Println(logMsg)

			// 2. Append to persistent server-side file
			t.writeToDisk(record, logMsg)
		}
	}
}

func (t *ServerStopTracker) GetEvents(routeID string) []StopArrivalRecord {
	t.mu.RLock()
	defer t.mu.RUnlock()

	var filtered []StopArrivalRecord
	for _, ev := range t.arrivalEvents {
		if routeID == "" || ev.RouteID == routeID {
			filtered = append(filtered, ev)
		}
	}
	return filtered
}

func (t *ServerStopTracker) ResetRoute(routeID string) {
	t.mu.Lock()
	defer t.mu.Unlock()
	delete(t.crossedStops, routeID)
}

func (t *ServerStopTracker) writeToDisk(record StopArrivalRecord, logMsg string) {
	if t.logFilePath == "" {
		return
	}

	f, err := os.OpenFile(t.logFilePath, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	if err != nil {
		log.Printf("[SERVER ERROR] Failed to write stop arrival log to %s: %v", t.logFilePath, err)
		return
	}
	defer f.Close()

	bytes, _ := json.Marshal(record)
	_, _ = f.WriteString(fmt.Sprintf("%s | JSON: %s\n", logMsg, string(bytes)))
}

func haversineDistanceMeters(lat1, lon1, lat2, lon2 float64) float64 {
	dLat := (lat2 - lat1) * (math.Pi / 180.0)
	dLon := (lon2 - lon1) * (math.Pi / 180.0)
	rLat1 := lat1 * (math.Pi / 180.0)
	rLat2 := lat2 * (math.Pi / 180.0)

	a := math.Sin(dLat/2)*math.Sin(dLat/2) +
		math.Cos(rLat1)*math.Cos(rLat2)*
			math.Sin(dLon/2)*math.Sin(dLon/2)
	c := 2 * math.Atan2(math.Sqrt(a), math.Sqrt(1-a))
	return EarthRadiusMeters * c
}
