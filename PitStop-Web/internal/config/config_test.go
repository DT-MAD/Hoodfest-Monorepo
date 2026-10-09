package config

import (
	"errors"
	"testing"
)

func setEnv(t *testing.T, kv map[string]string) {
	t.Helper()
	for _, k := range []string{"DATABASE_URL", "API_KEY", "ADMIN_KEY", "PORT", "ALLOWED_ORIGINS"} {
		t.Setenv(k, "")
	}
	for k, v := range kv {
		t.Setenv(k, v)
	}
}

func validEnv() map[string]string {
	return map[string]string{
		"DATABASE_URL": "postgres://localhost/pitstop",
		"API_KEY":      "app-key",
		"ADMIN_KEY":    "admin-key",
	}
}

func TestLoadDefaults(t *testing.T) {
	setEnv(t, validEnv())
	c, err := Load()
	if err != nil {
		t.Fatalf("Load() = %v", err)
	}
	if c.Port != 8080 {
		t.Errorf("Port = %d, want the 8080 default", c.Port)
	}
	if c.AllowedOrigins != "*" {
		t.Errorf("AllowedOrigins = %q, want the * default", c.AllowedOrigins)
	}
	if c.Addr() != ":8080" {
		t.Errorf("Addr() = %q", c.Addr())
	}
}

func TestLoadRequiresVars(t *testing.T) {
	for _, missing := range []string{"DATABASE_URL", "API_KEY", "ADMIN_KEY"} {
		env := validEnv()
		delete(env, missing)
		setEnv(t, env)

		_, err := Load()
		var me *MissingError
		if !errors.As(err, &me) {
			t.Fatalf("without %s, Load() = %v, want a MissingError", missing, err)
		}
		if me.Key != missing {
			t.Errorf("MissingError names %q, want %q", me.Key, missing)
		}
	}
}

func TestLoadRejectsSharedKeys(t *testing.T) {
	env := validEnv()
	env["ADMIN_KEY"] = env["API_KEY"]
	setEnv(t, env)

	if _, err := Load(); err == nil {
		t.Error("Load() accepted an identical API_KEY and ADMIN_KEY")
	}
}

func TestLoadRejectsBadPort(t *testing.T) {
	for _, bad := range []string{"not-a-number", "0", "-1", "70000"} {
		env := validEnv()
		env["PORT"] = bad
		setEnv(t, env)

		if _, err := Load(); err == nil {
			t.Errorf("Load() accepted PORT=%q", bad)
		}
	}
}

func TestLoadTrimsAndOverrides(t *testing.T) {
	env := validEnv()
	env["PORT"] = "9999"
	env["ALLOWED_ORIGINS"] = "  http://booth.local  "
	setEnv(t, env)

	c, err := Load()
	if err != nil {
		t.Fatalf("Load() = %v", err)
	}
	if c.Port != 9999 {
		t.Errorf("Port = %d, want 9999", c.Port)
	}
	if c.AllowedOrigins != "http://booth.local" {
		t.Errorf("AllowedOrigins = %q, want it trimmed", c.AllowedOrigins)
	}
}
