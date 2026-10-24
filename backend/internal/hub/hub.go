package hub

import (
	"encoding/json"
	"sync"
	"time"

	"github.com/campus/bustracker-backend/internal/model"
)

type Subscriber struct {
	ID   string
	Send chan []byte
}

type RouteHub struct {
	routeID      string
	mu           sync.RWMutex
	subscribers  map[*Subscriber]struct{}
	lastLocation *model.BusLocation
}

func NewRouteHub(routeID string) *RouteHub {
	return &RouteHub{
		routeID:     routeID,
		subscribers: make(map[*Subscriber]struct{}),
	}
}

func (h *RouteHub) Subscribe(subID string) (*Subscriber, *model.BusLocation) {
	sub := &Subscriber{
		ID:   subID,
		Send: make(chan []byte, 16),
	}

	h.mu.Lock()
	h.subscribers[sub] = struct{}{}
	snapshot := h.lastLocation
	h.mu.Unlock()

	return sub, snapshot
}

func (h *RouteHub) Unsubscribe(sub *Subscriber) {
	h.mu.Lock()
	if _, exists := h.subscribers[sub]; exists {
		delete(h.subscribers, sub)
		close(sub.Send)
	}
	h.mu.Unlock()
}

func (h *RouteHub) Publish(loc model.BusLocation) {
	payload, err := json.Marshal(loc)
	if err != nil {
		return
	}

	h.mu.Lock()
	locCopy := loc
	h.lastLocation = &locCopy

	for sub := range h.subscribers {
		select {
		case sub.Send <- payload:
		default:
			// Non-blocking fan-out: drop frame for slow client rather than stalling the hub
		}
	}
	h.mu.Unlock()
}

// MarkOffline is triggered by the driver goroutine's defer block on socket drop or 15s timeout.
func (h *RouteHub) MarkOffline(now time.Time) {
	h.mu.RLock()
	prev := h.lastLocation
	h.mu.RUnlock()

	offlineLoc := model.BusLocation{
		Latitude:  0,
		Longitude: 0,
		Bearing:   0,
		SpeedKmh:  0,
		Timestamp: now.UnixMilli(),
		Status:    model.StatusOffline,
	}
	if prev != nil {
		offlineLoc.Latitude = prev.Latitude
		offlineLoc.Longitude = prev.Longitude
		offlineLoc.Bearing = prev.Bearing
	}

	h.Publish(offlineLoc)
}

type Manager struct {
	mu   sync.RWMutex
	hubs map[string]*RouteHub
}

func NewManager() *Manager {
	return &Manager{
		hubs: make(map[string]*RouteHub),
	}
}

func (m *Manager) GetOrCreate(routeID string) *RouteHub {
	m.mu.RLock()
	h, exists := m.hubs[routeID]
	m.mu.RUnlock()
	if exists {
		return h
	}

	m.mu.Lock()
	defer m.mu.Unlock()
	if h, exists = m.hubs[routeID]; exists {
		return h
	}
	h = NewRouteHub(routeID)
	m.hubs[routeID] = h
	return h
}
