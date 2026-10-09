// Package config loads the server's settings from the environment.
//
// This server gets set up on a laptop at a noisy car show, usually in a hurry,
// so a misconfiguration must fail immediately with a message that names the
// variable and says what it is for — never with a nil pointer ten seconds later.
package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
)

// Config holds everything the server needs to run.
type Config struct {
	// DatabaseURL is a Postgres connection string, e.g.
	// postgres://user:pass@localhost:5432/pitstop?sslmode=disable
	DatabaseURL string

	// Port is the TCP port the HTTP server listens on.
	Port int

	// APIKey is the shared secret the mobile apps send in X-API-Key when
	// submitting a score.
	APIKey string

	// AdminKey unlocks the moderation view at /admin. Keep it different from
	// APIKey: the API key ships inside two app binaries, the admin key does not.
	AdminKey string

	// AllowedOrigins is the CORS allow-list for the browser leaderboard.
	// "*" permits any origin, which is fine for a read-only board on a LAN.
	AllowedOrigins string
}

// MissingError reports a required environment variable that was not set.
type MissingError struct {
	Key     string
	Purpose string
}

func (e *MissingError) Error() string {
	return fmt.Sprintf("%s is required (%s)", e.Key, e.Purpose)
}

// Load reads the configuration from the process environment.
func Load() (*Config, error) {
	c := &Config{
		DatabaseURL:    os.Getenv("DATABASE_URL"),
		APIKey:         os.Getenv("API_KEY"),
		AdminKey:       os.Getenv("ADMIN_KEY"),
		AllowedOrigins: envOr("ALLOWED_ORIGINS", "*"),
	}

	required := []struct{ key, value, purpose string }{
		{"DATABASE_URL", c.DatabaseURL, "the Postgres connection string"},
		{"API_KEY", c.APIKey, "the shared key the apps send when submitting scores"},
		{"ADMIN_KEY", c.AdminKey, "the key that unlocks the /admin moderation view"},
	}
	for _, r := range required {
		if strings.TrimSpace(r.value) == "" {
			return nil, &MissingError{Key: r.key, Purpose: r.purpose}
		}
	}

	port, err := strconv.Atoi(envOr("PORT", "8080"))
	if err != nil || port < 1 || port > 65535 {
		return nil, fmt.Errorf("PORT must be a number between 1 and 65535, got %q", os.Getenv("PORT"))
	}
	c.Port = port

	if c.APIKey == c.AdminKey {
		return nil, fmt.Errorf("API_KEY and ADMIN_KEY must differ: the API key is compiled into the apps, so sharing it would hand every tablet the power to delete scores")
	}

	return c, nil
}

// Addr is the listen address for net/http.
func (c *Config) Addr() string { return fmt.Sprintf(":%d", c.Port) }

func envOr(key, fallback string) string {
	if v := strings.TrimSpace(os.Getenv(key)); v != "" {
		return v
	}
	return fallback
}
