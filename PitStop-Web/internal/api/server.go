// Package api serves the Pit Stop REST API and the live leaderboard display.
package api

import (
	"embed"
	"html/template"
	"log/slog"
	"net/http"

	"edu.dixietech.pitstop/internal/config"
	"edu.dixietech.pitstop/internal/game"
	"edu.dixietech.pitstop/internal/store"
)

// Route paths, named so the middleware can refer to them without a magic string.
const (
	pathEvents = "/api/events"
)

//go:embed all:templates all:static
var assets embed.FS

// Server wires the store, the SSE hub and the HTTP routes together.
type Server struct {
	cfg   *config.Config
	store store.Store
	hub   *hub
	log   *slog.Logger
	tmpl  *template.Template
}

// New builds a Server. The templates are parsed once, at startup, so a broken
// template fails the boot rather than the first page view at the event.
func New(cfg *config.Config, st store.Store, log *slog.Logger) (*Server, error) {
	tmpl, err := template.ParseFS(assets, "templates/*.html")
	if err != nil {
		return nil, err
	}
	return &Server{
		cfg:   cfg,
		store: st,
		hub:   newHub(log),
		log:   log,
		tmpl:  tmpl,
	}, nil
}

// Handler returns the fully wrapped HTTP handler.
func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()

	// Read-only leaderboard data.
	mux.HandleFunc("GET /api/leaderboard", s.handleBoards)
	mux.HandleFunc("GET /api/leaderboard/{game}", s.handleBoard)
	mux.HandleFunc("GET "+pathEvents, s.serveSSE)

	// Submission, from the apps.
	mux.HandleFunc("POST /api/scores", requireKey("X-API-Key", s.cfg.APIKey, s.handleSubmit))

	// Moderation, from the booth operator.
	admin := func(h http.HandlerFunc) http.HandlerFunc {
		return requireKey("X-Admin-Key", s.cfg.AdminKey, h)
	}
	mux.HandleFunc("GET /api/admin/stats", admin(s.handleAdminStats))
	mux.HandleFunc("POST /api/admin/seed", admin(s.handleSeed))
	mux.HandleFunc("DELETE /api/admin/entries", admin(s.handleReset))
	mux.HandleFunc("DELETE /api/admin/entries/{id}", admin(s.handleDelete))

	mux.HandleFunc("GET /healthz", s.handleHealth)

	// The display and the moderation view.
	mux.HandleFunc("GET /{$}", s.handleLeaderboardPage)
	mux.HandleFunc("GET /admin", s.handleAdminPage)
	mux.Handle("GET /static/", http.FileServerFS(assets))

	return withRecover(s.log, withLogging(s.log, withCORS(s.cfg.AllowedOrigins, mux)))
}

// pageData is what the HTML templates render against. The boards themselves
// arrive over SSE once the page is up; this is only the shell.
type pageData struct {
	Games []game.ID
	TopN  int
}

func (s *Server) handleLeaderboardPage(w http.ResponseWriter, r *http.Request) {
	s.renderPage(w, "leaderboard.html")
}

func (s *Server) handleAdminPage(w http.ResponseWriter, r *http.Request) {
	s.renderPage(w, "admin.html")
}

func (s *Server) renderPage(w http.ResponseWriter, page string) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	data := pageData{Games: game.All(), TopN: store.TopN}
	if err := s.tmpl.ExecuteTemplate(w, page, data); err != nil {
		s.log.Error("rendering a page", "error", err, "page", page)
	}
}
