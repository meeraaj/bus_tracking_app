package model

const (
	StatusActive  = "ACTIVE"
	StatusOffline = "OFFLINE"

	RoleDriver  = "DRIVER"
	RoleStudent = "STUDENT"
)

type BusLocation struct {
	Latitude  float64 `json:"latitude"`
	Longitude float64 `json:"longitude"`
	Bearing   float64 `json:"bearing"`
	SpeedKmh  float64 `json:"speedKmh"`
	Timestamp int64   `json:"timestamp"`
	Status    string  `json:"status"`
}

type RouteStop struct {
	StopID        string  `json:"stopId"`
	Name          string  `json:"name"`
	Latitude      float64 `json:"latitude"`
	Longitude     float64 `json:"longitude"`
	SequenceOrder int     `json:"sequenceOrder"`
}

type LatLng struct {
	Lat float64 `json:"lat"`
	Lng float64 `json:"lng"`
}

type RouteData struct {
	RouteID        string      `json:"routeId"`
	RouteName      string      `json:"routeName"`
	DistanceMeters float64     `json:"distanceMeters"`
	PolylinePoints []LatLng    `json:"polylinePoints"`
	Stops          []RouteStop `json:"stops"`
}

type DriverAuthRequest struct {
	Email     string `json:"email"`
	SecretKey string `json:"secretKey"`
	RouteID   string `json:"routeId"`
}

type StudentAuthRequest struct {
	RollNumber string `json:"rollNumber"`
	RouteID    string `json:"routeId"`
}

type AuthResponse struct {
	Token     string `json:"token"`
	UserID    string `json:"userId"`
	Role      string `json:"role"`
	RouteID   string `json:"routeId"`
	ExpiresAt int64  `json:"expiresAt"`
}
