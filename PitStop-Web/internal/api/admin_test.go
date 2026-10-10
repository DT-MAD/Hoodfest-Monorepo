package api

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"edu.dixietech.pitstop/internal/game"
	"edu.dixietech.pitstop/internal/seed"
	"edu.dixietech.pitstop/internal/store"
)

func adminRequest(t *testing.T, h http.Handler, method, path, key string) *httptest.ResponseRecorder {
	t.Helper()

	req := httptest.NewRequest(method, path, nil)
	if key != "" {
		req.Header.Set("X-Admin-Key", key)
	}
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	return rec
}

func TestAdminEndpointsRequireTheAdminKey(t *testing.T) {
	cases := []struct {
		method, path string
	}{
		{http.MethodGet, "/api/admin/stats"},
		{http.MethodPost, "/api/admin/seed"},
		{http.MethodDelete, "/api/admin/entries?confirm=RESET"},
		{http.MethodDelete, "/api/admin/entries/1"},
	}

	for _, tc := range cases {
		t.Run(tc.method+" "+tc.path, func(t *testing.T) {
			_, h, mem := newTestServer(t)
			mem.Insert(context.Background(), store.NewEntry{
				Game: game.Reaction, Name: "ACE", Score: 200, Platform: store.Web,
			})

			// No key, a wrong key, and the apps' own key must all be refused.
			for _, key := range []string{"", "wrong", testAPIKey} {
				if code := adminRequest(t, h, tc.method, tc.path, key).Code; code != http.StatusUnauthorized {
					t.Errorf("with key %q: status %d, want 401", key, code)
				}
			}

			stats, _ := mem.Stats(context.Background())
			if stats.Total != 1 {
				t.Errorf("an unauthorized call changed the store: total is %d, want 1", stats.Total)
			}
		})
	}
}

func TestAdminStats(t *testing.T) {
	_, h, _ := newTestServer(t)

	// Seed first, then add a real run on top of it.
	adminRequest(t, h, http.MethodPost, "/api/admin/seed", testAdminKey)
	submit(t, h, testAPIKey, validScore())

	rec := adminRequest(t, h, http.MethodGet, "/api/admin/stats", testAdminKey)
	if rec.Code != http.StatusOK {
		t.Fatalf("status %d, want 200. body: %s", rec.Code, rec.Body)
	}

	var body adminStatsResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("decoding: %v", err)
	}

	wantSeeded := len(seed.Rows())
	if body.Stats.Seeded != wantSeeded {
		t.Errorf("Seeded = %d, want %d", body.Stats.Seeded, wantSeeded)
	}
	if body.Stats.Total != wantSeeded+1 {
		t.Errorf("Total = %d, want %d", body.Stats.Total, wantSeeded+1)
	}
	if len(body.Boards.Boards) != len(game.All()) {
		t.Errorf("got %d boards, want %d", len(body.Boards.Boards), len(game.All()))
	}
	// The dashboard distinguishes starter rows from real ones by this flag.
	if body.Stats.Total-body.Stats.Seeded != 1 {
		t.Errorf("real runs = %d, want 1", body.Stats.Total-body.Stats.Seeded)
	}
}

func TestSeedFillsEveryBoard(t *testing.T) {
	_, h, mem := newTestServer(t)

	rec := adminRequest(t, h, http.MethodPost, "/api/admin/seed", testAdminKey)
	if rec.Code != http.StatusOK {
		t.Fatalf("status %d, want 200. body: %s", rec.Code, rec.Body)
	}

	var result map[string]int
	json.Unmarshal(rec.Body.Bytes(), &result)
	if result["inserted"] != len(seed.Rows()) {
		t.Errorf("inserted = %d, want %d", result["inserted"], len(seed.Rows()))
	}
	if result["replaced"] != 0 {
		t.Errorf("replaced = %d on a first seed, want 0", result["replaced"])
	}

	boards, _ := mem.Boards(context.Background())
	for _, b := range boards.Boards {
		if len(b.Top) != store.TopN {
			t.Errorf("%s shows %d entries after seeding, want a full board of %d",
				b.Game, len(b.Top), store.TopN)
		}
	}
}

// TestSeedingTwiceReplaces is the property that lets an operator press the
// button as often as they like without stacking sets on top of each other.
func TestSeedingTwiceReplaces(t *testing.T) {
	_, h, mem := newTestServer(t)

	adminRequest(t, h, http.MethodPost, "/api/admin/seed", testAdminKey)
	rec := adminRequest(t, h, http.MethodPost, "/api/admin/seed", testAdminKey)

	var result map[string]int
	json.Unmarshal(rec.Body.Bytes(), &result)
	if result["replaced"] != len(seed.Rows()) {
		t.Errorf("replaced = %d on a second seed, want %d", result["replaced"], len(seed.Rows()))
	}

	stats, _ := mem.Stats(context.Background())
	if stats.Total != len(seed.Rows()) {
		t.Errorf("total after seeding twice = %d, want %d", stats.Total, len(seed.Rows()))
	}
}

// TestSeedingLeavesRealScoresAlone matters at a booth: an operator topping the
// boards back up mid-event must not delete the runs people actually played.
func TestSeedingLeavesRealScoresAlone(t *testing.T) {
	_, h, mem := newTestServer(t)

	adminRequest(t, h, http.MethodPost, "/api/admin/seed", testAdminKey)
	submit(t, h, testAPIKey, validScore())
	adminRequest(t, h, http.MethodPost, "/api/admin/seed", testAdminKey)

	stats, _ := mem.Stats(context.Background())
	if real := stats.Total - stats.Seeded; real != 1 {
		t.Errorf("after re-seeding there are %d real runs, want the 1 that was played", real)
	}
}

func TestResetRequiresConfirmation(t *testing.T) {
	_, h, mem := newTestServer(t)
	adminRequest(t, h, http.MethodPost, "/api/admin/seed", testAdminKey)

	for _, path := range []string{
		"/api/admin/entries",
		"/api/admin/entries?confirm=",
		"/api/admin/entries?confirm=yes",
		"/api/admin/entries?confirm=reset",
	} {
		rec := adminRequest(t, h, http.MethodDelete, path, testAdminKey)
		if rec.Code != http.StatusBadRequest {
			t.Errorf("%s: status %d, want 400", path, rec.Code)
		}
		if got := decodeError(t, rec).Error.Code; got != "confirmation_required" {
			t.Errorf("%s: code %q, want confirmation_required", path, got)
		}
	}

	stats, _ := mem.Stats(context.Background())
	if stats.Total == 0 {
		t.Error("an unconfirmed reset emptied the boards")
	}
}

func TestResetEmptiesEveryBoard(t *testing.T) {
	_, h, mem := newTestServer(t)
	adminRequest(t, h, http.MethodPost, "/api/admin/seed", testAdminKey)
	submit(t, h, testAPIKey, validScore())

	rec := adminRequest(t, h, http.MethodDelete, "/api/admin/entries?confirm=RESET", testAdminKey)
	if rec.Code != http.StatusOK {
		t.Fatalf("status %d, want 200. body: %s", rec.Code, rec.Body)
	}

	var result map[string]int
	json.Unmarshal(rec.Body.Bytes(), &result)
	if result["deleted"] != len(seed.Rows())+1 {
		t.Errorf("deleted = %d, want %d", result["deleted"], len(seed.Rows())+1)
	}

	stats, _ := mem.Stats(context.Background())
	if stats.Total != 0 {
		t.Errorf("total after reset = %d, want 0", stats.Total)
	}
}

// TestSeedAndResetPushToTheDisplay covers the booth screen updating without
// anyone touching it.
func TestSeedAndResetPushToTheDisplay(t *testing.T) {
	s, h, _ := newTestServer(t)

	events, cancel := s.hub.subscribe()
	defer cancel()

	adminRequest(t, h, http.MethodPost, "/api/admin/seed", testAdminKey)
	select {
	case <-events:
	default:
		t.Error("seeding did not push an update to the display")
	}

	adminRequest(t, h, http.MethodDelete, "/api/admin/entries?confirm=RESET", testAdminKey)
	select {
	case <-events:
	default:
		t.Error("resetting did not push an update to the display")
	}
}

func TestDashboardPageRenders(t *testing.T) {
	_, h, _ := newTestServer(t)

	req := httptest.NewRequest(http.MethodGet, "/admin", nil)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status %d, want 200", rec.Code)
	}
	for _, needle := range []string{"unlock", "seed", "reset", "confirm-reset"} {
		if !strings.Contains(rec.Body.String(), needle) {
			t.Errorf("the dashboard page is missing %q", needle)
		}
	}
}
