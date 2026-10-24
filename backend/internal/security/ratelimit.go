package security

import (
	"sync"
	"time"
)

type bucket struct {
	tokens     float64
	lastRefill time.Time
}

// TokenBucketLimiter enforces per-key (IP or UID) rate limits in memory.
type TokenBucketLimiter struct {
	mu         sync.Mutex
	buckets    map[string]*bucket
	ratePerSec float64
	burst      float64
}

func NewTokenBucketLimiter(ratePerSec, burst float64) *TokenBucketLimiter {
	return &TokenBucketLimiter{
		buckets:    make(map[string]*bucket),
		ratePerSec: ratePerSec,
		burst:      burst,
	}
}

func (l *TokenBucketLimiter) Allow(key string, now time.Time) bool {
	l.mu.Lock()
	defer l.mu.Unlock()

	b, exists := l.buckets[key]
	if !exists {
		l.buckets[key] = &bucket{
			tokens:     l.burst - 1.0,
			lastRefill: now,
		}
		return true
	}

	elapsed := now.Sub(b.lastRefill).Seconds()
	if elapsed > 0 {
		b.tokens += elapsed * l.ratePerSec
		if b.tokens > l.burst {
			b.tokens = l.burst
		}
		b.lastRefill = now
	}

	if b.tokens >= 1.0 {
		b.tokens -= 1.0
		return true
	}
	return false
}
