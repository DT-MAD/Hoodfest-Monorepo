package api

import (
	"crypto/subtle"
	"log/slog"
	"net/http"
	"time"
)

// requireKey wraps a handler so it only runs when the request carries the
// expected secret in header.
//
// The comparison is constant-time. The timing leak it closes is not realistic
// over a venue network, but a shared key check is exactly the code a student
// will copy into something that matters later, so it should be right here.
func requireKey(header, expected string, next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		got := r.Header.Get(header)
		if subtle.ConstantTimeCompare([]byte(got), []byte(expected)) != 1 {
			writeError(w, http.StatusUnauthorized, "unauthorized",
				"A valid "+header+" header is required.")
			return
		}
		next(w, r)
	}
}

// withCORS allows the browser leaderboard to call the API from another origin.
// Only the read-only endpoints are exposed this way.
func withCORS(origins string, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", origins)
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, X-API-Key, X-Admin-Key")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")

		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}

// statusRecorder captures the response status for the access log.
type statusRecorder struct {
	http.ResponseWriter
	status int
}

func (r *statusRecorder) WriteHeader(code int) {
	r.status = code
	r.ResponseWriter.WriteHeader(code)
}

// Unwrap lets http.Flusher reach the underlying writer, which the SSE handler
// depends on.
func (r *statusRecorder) Unwrap() http.ResponseWriter { return r.ResponseWriter }

func (r *statusRecorder) Flush() {
	if f, ok := r.ResponseWriter.(http.Flusher); ok {
		f.Flush()
	}
}

// withLogging records one line per request.
func withLogging(log *slog.Logger, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		rec := &statusRecorder{ResponseWriter: w, status: http.StatusOK}

		next.ServeHTTP(rec, r)

		// The SSE stream stays open for as long as the display is up; logging
		// its duration on close would be noise.
		if r.URL.Path == pathEvents {
			return
		}
		log.Info("request",
			"method", r.Method,
			"path", r.URL.Path,
			"status", rec.status,
			"duration", time.Since(start).Round(time.Millisecond),
		)
	})
}

// withRecover turns a panic in a handler into a 500 instead of taking the
// whole booth server down mid-event.
func withRecover(log *slog.Logger, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if v := recover(); v != nil {
				log.Error("recovered from a panic in a handler",
					"panic", v, "method", r.Method, "path", r.URL.Path)
				writeError(w, http.StatusInternalServerError, "internal",
					"Something went wrong on the server.")
			}
		}()
		next.ServeHTTP(w, r)
	})
}
