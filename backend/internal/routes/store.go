package routes

import (
	_ "embed"
	"encoding/json"
	"errors"
	"sync"

	"github.com/campus/bustracker-backend/internal/model"
)

//go:embed route_85.json
var route85Bytes []byte

var ErrRouteNotFound = errors.New("route not found")

type Store struct {
	mu     sync.RWMutex
	routes map[string]model.RouteData
}

func NewStore() (*Store, error) {
	s := &Store{
		routes: make(map[string]model.RouteData),
	}

	var r85 model.RouteData
	if err := json.Unmarshal(route85Bytes, &r85); err != nil {
		return nil, err
	}
	s.routes[r85.RouteID] = r85
	return s, nil
}

func (s *Store) Get(routeID string) (model.RouteData, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	r, ok := s.routes[routeID]
	if !ok {
		return model.RouteData{}, ErrRouteNotFound
	}
	return r, nil
}
