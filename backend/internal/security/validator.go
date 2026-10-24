package security

import (
	"errors"
	"fmt"
	"math"
	"regexp"
	"strings"
	"time"

	"github.com/campus/bustracker-backend/internal/model"
)

const (
	MinLatitude  = 12.5
	MaxLatitude  = 13.5
	MinLongitude = 79.5
	MaxLongitude = 80.5

	MaxSpeedKmh        = 120.0
	MaxFutureSkewMs    = 5_000
	MaxStaleWindowMs   = 60_000
	MaxWebSocketFrameB = 2048
)

var routeIDRegex = regexp.MustCompile(`^[a-zA-Z0-9_-]{2,32}$`)

func IsValidRouteID(routeID string) bool {
	return routeIDRegex.MatchString(routeID)
}

func ValidateAndSanitizeLocation(loc model.BusLocation, now time.Time) (model.BusLocation, error) {
	if math.IsNaN(loc.Latitude) || math.IsInf(loc.Latitude, 0) ||
		math.IsNaN(loc.Longitude) || math.IsInf(loc.Longitude, 0) {
		return loc, errors.New("non-finite latitude or longitude")
	}
	if math.IsNaN(loc.SpeedKmh) || math.IsInf(loc.SpeedKmh, 0) ||
		math.IsNaN(loc.Bearing) || math.IsInf(loc.Bearing, 0) {
		return loc, errors.New("non-finite speed or bearing")
	}

	status := strings.ToUpper(strings.TrimSpace(loc.Status))
	if status != model.StatusActive && status != model.StatusOffline {
		return loc, fmt.Errorf("invalid status '%s'", loc.Status)
	}

	// Only enforce geofence when status is ACTIVE (an OFFLINE status update may have 0,0 coords)
	if status == model.StatusActive {
		if loc.Latitude < MinLatitude || loc.Latitude > MaxLatitude ||
			loc.Longitude < MinLongitude || loc.Longitude > MaxLongitude {
			return loc, fmt.Errorf("coordinates (%.6f, %.6f) outside campus geofence", loc.Latitude, loc.Longitude)
		}
	}

	if loc.SpeedKmh < 0 || loc.SpeedKmh > MaxSpeedKmh {
		return loc, fmt.Errorf("implausible speed %.2f km/h", loc.SpeedKmh)
	}

	nowMs := now.UnixMilli()
	ts := loc.Timestamp
	if ts <= 0 {
		ts = nowMs
	}
	if ts > nowMs+MaxFutureSkewMs || ts < nowMs-MaxStaleWindowMs {
		return loc, errors.New("timestamp outside anti-replay window")
	}

	bearing := math.Mod(math.Mod(loc.Bearing, 360.0)+360.0, 360.0)

	return model.BusLocation{
		Latitude:  loc.Latitude,
		Longitude: loc.Longitude,
		Bearing:   bearing,
		SpeedKmh:  loc.SpeedKmh,
		Timestamp: ts,
		Status:    status,
	}, nil
}
