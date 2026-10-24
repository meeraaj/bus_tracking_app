package auth

import (
	"crypto/hmac"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/campus/bustracker-backend/internal/model"
)

var (
	ErrInvalidToken       = errors.New("invalid or malformed JWT token")
	ErrExpiredToken       = errors.New("JWT token has expired")
	ErrInvalidCredentials = errors.New("invalid driver credentials or unauthorized route")
)

type Claims struct {
	Sub     string `json:"sub"`
	Role    string `json:"role"`
	RouteID string `json:"routeId"`
	Iat     int64  `json:"iat"`
	Exp     int64  `json:"exp"`
}

type DriverAccount struct {
	DriverID      string
	Email         string
	SecretKey     string
	AssignedRoute string
	Active        bool
}

type Service struct {
	jwtSecret []byte
	drivers   map[string]DriverAccount
	tokenTTL  time.Duration
}

func NewService(jwtSecret string, accounts []DriverAccount, ttl time.Duration) *Service {
	driverMap := make(map[string]DriverAccount, len(accounts))
	for _, acc := range accounts {
		driverMap[strings.ToLower(strings.TrimSpace(acc.Email))] = acc
	}
	return &Service{
		jwtSecret: []byte(jwtSecret),
		drivers:   driverMap,
		tokenTTL:  ttl,
	}
}

func (s *Service) AuthenticateDriver(email, secretKey, requestedRouteID string, now time.Time) (*model.AuthResponse, error) {
	normalizedEmail := strings.ToLower(strings.TrimSpace(email))
	acc, exists := s.drivers[normalizedEmail]
	if !exists || !acc.Active {
		return nil, ErrInvalidCredentials
	}

	// Constant-time secret key comparison to prevent timing side-channel attacks
	if subtle.ConstantTimeCompare([]byte(acc.SecretKey), []byte(strings.TrimSpace(secretKey))) != 1 {
		return nil, ErrInvalidCredentials
	}

	routeID := strings.TrimSpace(requestedRouteID)
	if routeID == "" {
		routeID = acc.AssignedRoute
	}
	if routeID != acc.AssignedRoute {
		return nil, fmt.Errorf("%w: driver is pinned to %s", ErrInvalidCredentials, acc.AssignedRoute)
	}

	exp := now.Add(s.tokenTTL).Unix()
	claims := Claims{
		Sub:     acc.DriverID,
		Role:    model.RoleDriver,
		RouteID: routeID,
		Iat:     now.Unix(),
		Exp:     exp,
	}

	token, err := s.SignClaims(claims)
	if err != nil {
		return nil, err
	}

	return &model.AuthResponse{
		Token:     token,
		UserID:    acc.DriverID,
		Role:      model.RoleDriver,
		RouteID:   routeID,
		ExpiresAt: exp,
	}, nil
}

func (s *Service) AuthenticateStudent(rollNumber, routeID string, now time.Time) (*model.AuthResponse, error) {
	trimmedRoll := strings.TrimSpace(rollNumber)
	studentID := "guest_" + fmt.Sprintf("%d", now.UnixNano()%1_000_000)
	if trimmedRoll != "" {
		studentID = "student_" + strings.ToLower(trimmedRoll)
	}

	cleanRoute := strings.TrimSpace(routeID)
	if cleanRoute == "" {
		cleanRoute = "route_85"
	}

	exp := now.Add(s.tokenTTL).Unix()
	claims := Claims{
		Sub:     studentID,
		Role:    model.RoleStudent,
		RouteID: cleanRoute,
		Iat:     now.Unix(),
		Exp:     exp,
	}

	token, err := s.SignClaims(claims)
	if err != nil {
		return nil, err
	}

	return &model.AuthResponse{
		Token:     token,
		UserID:    studentID,
		Role:      model.RoleStudent,
		RouteID:   cleanRoute,
		ExpiresAt: exp,
	}, nil
}

func (s *Service) SignClaims(claims Claims) (string, error) {
	headerJSON := `{"alg":"HS256","typ":"JWT"}`
	payloadBytes, err := json.Marshal(claims)
	if err != nil {
		return "", err
	}

	headerB64 := base64.RawURLEncoding.EncodeToString([]byte(headerJSON))
	payloadB64 := base64.RawURLEncoding.EncodeToString(payloadBytes)
	signingInput := headerB64 + "." + payloadB64

	sig := s.computeHMAC(signingInput)
	sigB64 := base64.RawURLEncoding.EncodeToString(sig)

	return signingInput + "." + sigB64, nil
}

func (s *Service) VerifyToken(token string, now time.Time) (*Claims, error) {
	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		return nil, ErrInvalidToken
	}

	// Verify header strictly specifies HS256 (blocks "alg":"none" attacks)
	headerBytes, err := base64.RawURLEncoding.DecodeString(parts[0])
	if err != nil {
		return nil, ErrInvalidToken
	}
	var hdr struct {
		Alg string `json:"alg"`
	}
	if err := json.Unmarshal(headerBytes, &hdr); err != nil || hdr.Alg != "HS256" {
		return nil, ErrInvalidToken
	}

	// Verify HMAC-SHA256 signature in constant time
	signingInput := parts[0] + "." + parts[1]
	expectedSig := s.computeHMAC(signingInput)
	actualSig, err := base64.RawURLEncoding.DecodeString(parts[2])
	if err != nil || !hmac.Equal(expectedSig, actualSig) {
		return nil, ErrInvalidToken
	}

	payloadBytes, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil {
		return nil, ErrInvalidToken
	}

	var claims Claims
	if err := json.Unmarshal(payloadBytes, &claims); err != nil {
		return nil, ErrInvalidToken
	}

	if now.Unix() > claims.Exp {
		return nil, ErrExpiredToken
	}

	return &claims, nil
}

func (s *Service) computeHMAC(input string) []byte {
	mac := hmac.New(sha256.New, s.jwtSecret)
	mac.Write([]byte(input))
	return mac.Sum(nil)
}
