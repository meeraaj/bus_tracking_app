package security

import (
	"testing"
	"time"

	"github.com/campus/bustracker-backend/internal/model"
)

func TestValidateAndSanitizeLocation(t *testing.T) {
	now := time.UnixMilli(1761297600000)

	valid := model.BusLocation{
		Latitude:  12.923444,
		Longitude: 80.159860,
		Bearing:   -90,
		SpeedKmh:  30.0,
		Timestamp: now.UnixMilli() - 1000,
		Status:    "active",
	}

	sanitized, err := ValidateAndSanitizeLocation(valid, now)
	if err != nil {
		t.Fatalf("expected valid payload, got error: %v", err)
	}
	if sanitized.Bearing != 270.0 || sanitized.Status != model.StatusActive {
		t.Fatalf("unexpected sanitized result: %+v", sanitized)
	}

	// Out of geofence
	spoofed := valid
	spoofed.Latitude = 40.7128
	if _, err := ValidateAndSanitizeLocation(spoofed, now); err == nil {
		t.Fatalf("expected out-of-geofence coordinate to fail")
	}

	// Replay attack
	stale := valid
	stale.Timestamp = now.UnixMilli() - 120_000
	if _, err := ValidateAndSanitizeLocation(stale, now); err == nil {
		t.Fatalf("expected stale replay timestamp to fail")
	}
}
