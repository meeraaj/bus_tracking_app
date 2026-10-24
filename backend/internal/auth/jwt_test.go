package auth

import (
	"testing"
	"time"
)

func TestDriverAuthAndJWTVerification(t *testing.T) {
	svc := NewService("super-secret-test-key-32-bytes!!", []DriverAccount{
		{
			DriverID:      "driver_01",
			Email:         "driver@campus.edu",
			SecretKey:     "1234",
			AssignedRoute: "route_85",
			Active:        true,
		},
	}, 1*time.Hour)

	now := time.Unix(1761297600, 0)

	// 1. Valid driver login succeeds
	resp, err := svc.AuthenticateDriver("driver@campus.edu", "1234", "route_85", now)
	if err != nil {
		t.Fatalf("expected valid driver auth to succeed, got: %v", err)
	}

	// 2. Verify signed JWT claims
	claims, err := svc.VerifyToken(resp.Token, now.Add(10*time.Minute))
	if err != nil {
		t.Fatalf("expected token verification to succeed, got: %v", err)
	}
	if claims.Role != "DRIVER" || claims.RouteID != "route_85" {
		t.Fatalf("unexpected claims: %+v", claims)
	}

	// 3. Expired token is rejected
	if _, err := svc.VerifyToken(resp.Token, now.Add(2*time.Hour)); err != ErrExpiredToken {
		t.Fatalf("expected ErrExpiredToken, got: %v", err)
	}

	// 4. Wrong route pinning is rejected
	if _, err := svc.AuthenticateDriver("driver@campus.edu", "1234", "route_99", now); err == nil {
		t.Fatalf("expected cross-route login to be rejected")
	}

	// 5. Wrong PIN is rejected
	if _, err := svc.AuthenticateDriver("driver@campus.edu", "9999", "route_85", now); err == nil {
		t.Fatalf("expected invalid PIN to be rejected")
	}
}
