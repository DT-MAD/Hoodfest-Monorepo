package api

import (
	"fmt"
	"log/slog"
	"net/http"
	"sync"
	"time"
)

// heartbeatInterval keeps proxies and browsers from treating an idle stream as
// a dead one. It is sent as an SSE comment, which clients ignore.
const heartbeatInterval = 20 * time.Second

// clientBuffer is how many undelivered payloads a single subscriber may fall
// behind by before it is dropped. The payload is the whole board, so a client
// that is behind only needs the newest one — a small buffer is plenty.
const clientBuffer = 4

// hub fans leaderboard updates out to every connected browser.
//
// Each update carries the complete set of boards rather than a diff. The boards
// are at most thirteen rows per game, and sending the whole thing makes the
// browser a pure render function with no state that can drift out of sync.
type hub struct {
	mu      sync.RWMutex
	clients map[chan []byte]struct{}
	log     *slog.Logger
}

func newHub(log *slog.Logger) *hub {
	return &hub{clients: make(map[chan []byte]struct{}), log: log}
}

// subscribe registers a new listener and returns it with a function to remove it.
func (h *hub) subscribe() (<-chan []byte, func()) {
	ch := make(chan []byte, clientBuffer)

	h.mu.Lock()
	h.clients[ch] = struct{}{}
	n := len(h.clients)
	h.mu.Unlock()

	h.log.Debug("leaderboard subscriber connected", "subscribers", n)

	return ch, func() { h.unsubscribe(ch) }
}

func (h *hub) unsubscribe(ch chan []byte) {
	h.mu.Lock()
	if _, ok := h.clients[ch]; ok {
		delete(h.clients, ch)
		close(ch)
	}
	n := len(h.clients)
	h.mu.Unlock()

	h.log.Debug("leaderboard subscriber disconnected", "subscribers", n)
}

// broadcast delivers payload to every subscriber. A subscriber whose buffer is
// full is dropped rather than allowed to block the caller: a wedged browser
// must never stall a score submission.
func (h *hub) broadcast(payload []byte) {
	h.mu.RLock()
	stalled := make([]chan []byte, 0)
	for ch := range h.clients {
		select {
		case ch <- payload:
		default:
			stalled = append(stalled, ch)
		}
	}
	h.mu.RUnlock()

	for _, ch := range stalled {
		h.log.Warn("dropping a leaderboard subscriber that fell behind")
		h.unsubscribe(ch)
	}
}

// subscriberCount reports how many browsers are listening. Used in tests and
// the health endpoint.
func (h *hub) subscriberCount() int {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return len(h.clients)
}

// serveSSE streams leaderboard updates to one browser until it disconnects.
func (s *Server) serveSSE(w http.ResponseWriter, r *http.Request) {
	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "streaming is not supported by this server", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")
	// Defeats proxy buffering, which would otherwise hold events back.
	w.Header().Set("X-Accel-Buffering", "no")
	w.WriteHeader(http.StatusOK)

	events, cancel := s.hub.subscribe()
	defer cancel()

	// Send the current state immediately so a browser that connects between
	// updates is not staring at an empty board.
	if payload, err := s.boardsPayload(r.Context()); err == nil {
		writeSSE(w, "leaderboard", payload)
	} else {
		s.log.Error("building the initial SSE payload", "error", err)
	}
	flusher.Flush()

	ticker := time.NewTicker(heartbeatInterval)
	defer ticker.Stop()

	for {
		select {
		case <-r.Context().Done():
			return

		case payload, open := <-events:
			if !open {
				return
			}
			writeSSE(w, "leaderboard", payload)
			flusher.Flush()

		case <-ticker.C:
			fmt.Fprint(w, ": heartbeat\n\n")
			flusher.Flush()
		}
	}
}

// writeSSE emits one server-sent event. The payload is compact JSON, which
// never contains a raw newline, so it needs no multi-line data handling.
func writeSSE(w http.ResponseWriter, event string, payload []byte) {
	fmt.Fprintf(w, "event: %s\ndata: %s\n\n", event, payload)
}
