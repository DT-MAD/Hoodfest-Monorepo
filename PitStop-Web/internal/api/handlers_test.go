package api

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"edu.dixietech.pitstop/internal/config"
	"edu.dixietech.pitstop/internal/store"
)

const (
	testAPIKey   = "test-app-key"
	testAdminKey = "test-admin-key"
)

func newTestServer(t *testing.T) (*Server, http.Handler, *store.Memory) {
	t.Helper()

	mem := store.NewMemory()
	cfg := &config.Config{
		DatabaseURL:    "memory",
		Port:           0,
		APIKey:         testAPIKey,
		AdminKey:       testAdminKey,
		AllowedOrigins: "*",
	}
	log := slog.New(slog.NewTextHandler(io.Discard, nil))

	s, err := New(cfg, mem, log)
	if err != nil {
		t.Fatalf("New: %v", err)
	}
	return s, s.Handler(), mem
}

func submit(t *testing.T, h http.Handler, key string, body any) *httptest.ResponseRecorder {
	t.Helper()

	var buf bytes.Buffer
	if s, ok := body.(string); ok {
		buf.WriteString(s)
	} else if err := json.NewEncoder(&buf).Encode(body); err != nil {
		t.Fatalf("encoding body: %v", err)
	}

	req := httptest.NewRequest(http.MethodPost, "/api/scores", &buf)
	req.Header.Set("Content-Type", "application/json")
	if key != "" {
		req.Header.Set("X-API-Key", key)
	}
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	return rec
}

func validScore() map[string]any {
	return map[string]any{
		"game": "reaction", "name": "ace", "score": 243, "platform": "ios",
	}
}

func decodeError(t *testing.T, rec *httptest.ResponseRecorder) errorBody {
	t.Helper()
	var e errorBody
	if err := json.Unmarshal(rec.Body.Bytes(), &e); err != nil {
		t.Fatalf("decoding error body %q: %v", rec.Body.String(), err)
	}
	return e
}

func TestSubmitRequiresAPIKey(t *testing.T) {
	_, h, mem := newTestServer(t)

	for _, key := range []string{"", "wrong-key", testAdminKey} {
		rec := submit(t, h, key, validScore())
		if rec.Code != http.StatusUnauthorized {
			t.Errorf("with key %q: status %d, want 401", key, rec.Code)
		}
	}

	boards, _ := mem.Boards(context.Background())
	if boards.Boards[0].Total != 0 {
		t.Error("an unauthorized submission reached the store")
	}
}

func TestSubmitStoresAndRanks(t *testing.T) {
	_, h, _ := newTestServer(t)

	rec := submit(t, h, testAPIKey, validScore())
	if rec.Code != http.StatusCreated {
		t.Fatalf("status %d, want 201. body: %s", rec.Code, rec.Body)
	}

	var resp submitResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decoding response: %v", err)
	}
	if resp.Entry.Name != "ACE" {
		t.Errorf("Name = %q, want the canonical uppercase ACE", resp.Entry.Name)
	}
	if resp.Entry.Rank != 1 {
		t.Errorf("Rank = %d, want 1 for the only entry", resp.Entry.Rank)
	}
	if resp.Entry.Display != "243 ms" {
		t.Errorf("Display = %q, want %q", resp.Entry.Display, "243 ms")
	}
	if resp.Board.Total != 1 {
		t.Errorf("the returned board has Total %d, want 1", resp.Board.Total)
	}
}

func TestSubmitValidation(t *testing.T) {
	cases := []struct {
		name      string
		mutate    func(m map[string]any)
		wantCode  string
		wantField string
	}{
		{"unknown game", func(m map[string]any) { m["game"] = "tires" }, "unknown_game", "game"},
		{"missing game", func(m map[string]any) { delete(m, "game") }, "unknown_game", "game"},
		{"unknown platform", func(m map[string]any) { m["platform"] = "desktop" }, "unknown_platform", "platform"},
		{"missing score", func(m map[string]any) { delete(m, "score") }, "missing_score", "score"},
		{"score below bounds", func(m map[string]any) { m["score"] = 10 }, "implausible_score", "score"},
		{"score above bounds", func(m map[string]any) { m["score"] = 999999 }, "implausible_score", "score"},
		{"negative score", func(m map[string]any) { m["score"] = -5 }, "implausible_score", "score"},
		{"empty name", func(m map[string]any) { m["name"] = "   " }, "invalid_name", "name"},
		{"overlong name", func(m map[string]any) { m["name"] = "THIS NAME IS MUCH TOO LONG" }, "invalid_name", "name"},
		{"bad characters", func(m map[string]any) { m["name"] = "ACE<script>" }, "invalid_name", "name"},
		{"profane name", func(m map[string]any) { m["name"] = "shit" }, "unavailable_name", "name"},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			_, h, mem := newTestServer(t)

			body := validScore()
			tc.mutate(body)

			rec := submit(t, h, testAPIKey, body)
			if rec.Code != http.StatusUnprocessableEntity {
				t.Fatalf("status %d, want 422. body: %s", rec.Code, rec.Body)
			}

			e := decodeError(t, rec)
			if e.Error.Code != tc.wantCode {
				t.Errorf("code = %q, want %q", e.Error.Code, tc.wantCode)
			}
			if e.Error.Field != tc.wantField {
				t.Errorf("field = %q, want %q", e.Error.Field, tc.wantField)
			}
			if e.Error.Message == "" {
				t.Error("the error has no message for the player")
			}

			boards, _ := mem.Boards(context.Background())
			for _, b := range boards.Boards {
				if b.Total != 0 {
					t.Errorf("a rejected submission reached the %s board", b.Game)
				}
			}
		})
	}
}

func TestSubmitMalformedBodies(t *testing.T) {
	cases := []struct {
		name string
		body string
		want string
	}{
		{"empty", "", "empty_body"},
		{"not json", "this is not json", "malformed_json"},
		{"unknown field", `{"game":"reaction","name":"ACE","score":243,"platform":"ios","admin":true}`, "malformed_json"},
		{"wrong score type", `{"game":"reaction","name":"ACE","score":"fast","platform":"ios"}`, "malformed_json"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			_, h, _ := newTestServer(t)
			rec := submit(t, h, testAPIKey, tc.body)
			if rec.Code != http.StatusBadRequest {
				t.Fatalf("status %d, want 400. body: %s", rec.Code, rec.Body)
			}
			if got := decodeError(t, rec).Error.Code; got != tc.want {
				t.Errorf("code = %q, want %q", got, tc.want)
			}
		})
	}
}

func TestSubmitRejectsOversizedBody(t *testing.T) {
	_, h, _ := newTestServer(t)

	body := validScore()
	body["detail"] = map[string]any{"padding": strings.Repeat("x", maxBodyBytes*2)}

	rec := submit(t, h, testAPIKey, body)
	if rec.Code == http.StatusCreated {
		t.Error("an oversized submission was accepted")
	}
}

func TestGetBoards(t *testing.T) {
	_, h, _ := newTestServer(t)
	submit(t, h, testAPIKey, validScore())

	req := httptest.NewRequest(http.MethodGet, "/api/leaderboard", nil)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status %d, want 200", rec.Code)
	}

	var boards store.Boards
	if err := json.Unmarshal(rec.Body.Bytes(), &boards); err != nil {
		t.Fatalf("decoding: %v", err)
	}
	if len(boards.Boards) != 3 {
		t.Fatalf("got %d boards, want 3", len(boards.Boards))
	}
	if boards.Boards[0].Top[0].Name != "ACE" {
		t.Errorf("the submitted score is missing from the board")
	}
	// Empty boards must be [] so the browser can iterate without a nil check.
	if !bytes.Contains(rec.Body.Bytes(), []byte(`"top":[]`)) {
		t.Error("an empty board serialized as null rather than []")
	}
}

func TestGetSingleBoard(t *testing.T) {
	_, h, _ := newTestServer(t)

	req := httptest.NewRequest(http.MethodGet, "/api/leaderboard/pitstop", nil)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("status %d, want 200", rec.Code)
	}

	req = httptest.NewRequest(http.MethodGet, "/api/leaderboard/tires", nil)
	rec = httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusNotFound {
		t.Errorf("unknown game: status %d, want 404", rec.Code)
	}
}

func TestAdminDelete(t *testing.T) {
	_, h, _ := newTestServer(t)
	submit(t, h, testAPIKey, validScore())

	del := func(key, path string) int {
		req := httptest.NewRequest(http.MethodDelete, path, nil)
		if key != "" {
			req.Header.Set("X-Admin-Key", key)
		}
		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, req)
		return rec.Code
	}

	if code := del("", "/api/admin/entries/1"); code != http.StatusUnauthorized {
		t.Errorf("no key: status %d, want 401", code)
	}
	// The app's API key must not grant moderation powers.
	if code := del(testAPIKey, "/api/admin/entries/1"); code != http.StatusUnauthorized {
		t.Errorf("app key: status %d, want 401", code)
	}
	if code := del(testAdminKey, "/api/admin/entries/abc"); code != http.StatusBadRequest {
		t.Errorf("non-numeric id: status %d, want 400", code)
	}
	if code := del(testAdminKey, "/api/admin/entries/999"); code != http.StatusNotFound {
		t.Errorf("missing entry: status %d, want 404", code)
	}
	if code := del(testAdminKey, "/api/admin/entries/1"); code != http.StatusNoContent {
		t.Errorf("valid delete: status %d, want 204", code)
	}
	if code := del(testAdminKey, "/api/admin/entries/1"); code != http.StatusNotFound {
		t.Errorf("repeat delete: status %d, want 404", code)
	}
}

func TestHealth(t *testing.T) {
	_, h, _ := newTestServer(t)

	req := httptest.NewRequest(http.MethodGet, "/healthz", nil)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status %d, want 200", rec.Code)
	}
	var body map[string]any
	json.Unmarshal(rec.Body.Bytes(), &body)
	if body["status"] != "ok" {
		t.Errorf("status = %v, want ok", body["status"])
	}
}

func TestPagesRender(t *testing.T) {
	_, h, _ := newTestServer(t)

	for _, path := range []string{"/", "/admin"} {
		req := httptest.NewRequest(http.MethodGet, path, nil)
		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("GET %s: status %d, want 200", path, rec.Code)
		}
		if ct := rec.Header().Get("Content-Type"); !strings.HasPrefix(ct, "text/html") {
			t.Errorf("GET %s: Content-Type %q, want text/html", path, ct)
		}
	}
}

func TestStaticAssetsServed(t *testing.T) {
	_, h, _ := newTestServer(t)

	for _, path := range []string{"/static/app.css", "/static/app.js"} {
		req := httptest.NewRequest(http.MethodGet, path, nil)
		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, req)
		if rec.Code != http.StatusOK {
			t.Errorf("GET %s: status %d, want 200", path, rec.Code)
		}
	}
}

func TestCORSPreflight(t *testing.T) {
	_, h, _ := newTestServer(t)

	req := httptest.NewRequest(http.MethodOptions, "/api/leaderboard", nil)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusNoContent {
		t.Errorf("status %d, want 204", rec.Code)
	}
	if rec.Header().Get("Access-Control-Allow-Origin") != "*" {
		t.Error("the preflight response is missing the allow-origin header")
	}
}

// TestSSEDeliversUpdates covers the path the booth display depends on: connect,
// receive the current board, then receive a push when a score arrives.
func TestSSEDeliversUpdates(t *testing.T) {
	_, h, _ := newTestServer(t)

	srv := httptest.NewServer(h)
	defer srv.Close()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	req, _ := http.NewRequestWithContext(ctx, http.MethodGet, srv.URL+pathEvents, nil)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("connecting to the event stream: %v", err)
	}
	defer resp.Body.Close()

	if ct := resp.Header.Get("Content-Type"); ct != "text/event-stream" {
		t.Errorf("Content-Type = %q, want text/event-stream", ct)
	}

	reader := bufio.NewReader(resp.Body)

	// The snapshot sent on connect.
	if data := readSSEData(t, reader); !strings.Contains(data, `"boards"`) {
		t.Fatalf("initial event did not carry the boards: %q", data)
	}

	// Wait for the subscriber to be registered before publishing, so the test
	// is not racing the server's bookkeeping.
	waitFor(t, func() bool { return subscriberCountOf(h) >= 1 })

	submit(t, h, testAPIKey, validScore())

	data := readSSEData(t, reader)
	if !strings.Contains(data, `"ACE"`) {
		t.Errorf("the pushed update did not contain the new score: %q", data)
	}
}

// readSSEData reads one event and returns its data line.
func readSSEData(t *testing.T, r *bufio.Reader) string {
	t.Helper()
	for {
		line, err := r.ReadString('\n')
		if err != nil {
			t.Fatalf("reading the event stream: %v", err)
		}
		if strings.HasPrefix(line, "data: ") {
			return strings.TrimSpace(strings.TrimPrefix(line, "data: "))
		}
	}
}

// subscriberCountOf reads the subscriber count out of /healthz.
func subscriberCountOf(h http.Handler) int {
	req := httptest.NewRequest(http.MethodGet, "/healthz", nil)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)

	var body struct {
		Subscribers int `json:"subscribers"`
	}
	json.Unmarshal(rec.Body.Bytes(), &body)
	return body.Subscribers
}

func waitFor(t *testing.T, cond func() bool) {
	t.Helper()
	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		if cond() {
			return
		}
		time.Sleep(5 * time.Millisecond)
	}
	t.Fatal("timed out waiting for a condition")
}
