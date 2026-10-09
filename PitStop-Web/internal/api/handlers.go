package api

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strconv"

	"edu.dixietech.pitstop/internal/game"
	"edu.dixietech.pitstop/internal/name"
	"edu.dixietech.pitstop/internal/store"
)

// maxBodyBytes caps a submission. A legitimate one is a few hundred bytes.
const maxBodyBytes = 8 << 10

// errorBody is the single error shape every endpoint returns, so the apps have
// one thing to decode.
type errorBody struct {
	Error struct {
		Code    string `json:"code"`
		Message string `json:"message"`
		Field   string `json:"field,omitempty"`
	} `json:"error"`
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(v); err != nil {
		// The status is already written; all that is left is to not pretend
		// this succeeded.
		return
	}
}

func writeError(w http.ResponseWriter, status int, code, message string, field ...string) {
	var body errorBody
	body.Error.Code = code
	body.Error.Message = message
	if len(field) > 0 {
		body.Error.Field = field[0]
	}
	writeJSON(w, status, body)
}

// submitRequest is the body of POST /api/scores.
type submitRequest struct {
	Game     game.ID        `json:"game"`
	Name     string         `json:"name"`
	Score    *int           `json:"score"`
	Platform store.Platform `json:"platform"`
	Detail   map[string]any `json:"detail"`
}

// submitResponse hands back the stored entry — including the rank it earned —
// along with the refreshed board, so the app can show the result without a
// second round trip.
type submitResponse struct {
	Entry store.Entry `json:"entry"`
	Board store.Board `json:"board"`
}

// handleSubmit stores one score.
func (s *Server) handleSubmit(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, maxBodyBytes)

	var req submitRequest
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(&req); err != nil {
		if errors.Is(err, io.EOF) {
			writeError(w, http.StatusBadRequest, "empty_body", "Send a JSON score submission.")
			return
		}
		writeError(w, http.StatusBadRequest, "malformed_json", "The request body is not valid JSON: "+err.Error())
		return
	}

	if !req.Game.Valid() {
		writeError(w, http.StatusUnprocessableEntity, "unknown_game",
			"Unknown game. Expected one of reaction, fill or pitstop.", "game")
		return
	}
	if !req.Platform.Valid() {
		writeError(w, http.StatusUnprocessableEntity, "unknown_platform",
			"Unknown platform. Expected one of ios, android or web.", "platform")
		return
	}
	if req.Score == nil {
		writeError(w, http.StatusUnprocessableEntity, "missing_score",
			"A score is required.", "score")
		return
	}
	if !req.Game.InBounds(*req.Score) {
		min, max := req.Game.Bounds()
		writeError(w, http.StatusUnprocessableEntity, "implausible_score",
			"A "+req.Game.Title()+" score must be between "+strconv.Itoa(min)+
				" and "+strconv.Itoa(max)+".", "score")
		return
	}

	canonical, err := name.Validate(req.Name)
	if err != nil {
		code := "invalid_name"
		if errors.Is(err, name.ErrUnavailable) {
			code = "unavailable_name"
		}
		writeError(w, http.StatusUnprocessableEntity, code, capitalize(err.Error())+".", "name")
		return
	}

	entry, err := s.store.Insert(r.Context(), store.NewEntry{
		Game:     req.Game,
		Name:     canonical,
		Score:    *req.Score,
		Platform: req.Platform,
		Detail:   req.Detail,
	})
	if err != nil {
		s.log.Error("storing a score", "error", err, "game", req.Game)
		writeError(w, http.StatusInternalServerError, "store_failed", "The score could not be saved.")
		return
	}

	board, err := s.store.Board(r.Context(), req.Game)
	if err != nil {
		s.log.Error("loading a board after a submission", "error", err, "game", req.Game)
		writeError(w, http.StatusInternalServerError, "store_failed", "The score was saved but the board could not be loaded.")
		return
	}

	s.log.Info("score accepted",
		"game", entry.Game, "name", entry.Name, "score", entry.Score,
		"rank", entry.Rank, "platform", entry.Platform)

	s.publishBoards(r.Context())
	writeJSON(w, http.StatusCreated, submitResponse{Entry: entry, Board: board})
}

// handleBoards returns every leaderboard.
func (s *Server) handleBoards(w http.ResponseWriter, r *http.Request) {
	boards, err := s.store.Boards(r.Context())
	if err != nil {
		s.log.Error("loading boards", "error", err)
		writeError(w, http.StatusInternalServerError, "store_failed", "The leaderboards could not be loaded.")
		return
	}
	writeJSON(w, http.StatusOK, boards)
}

// handleBoard returns one leaderboard.
func (s *Server) handleBoard(w http.ResponseWriter, r *http.Request) {
	id := game.ID(r.PathValue("game"))
	if !id.Valid() {
		writeError(w, http.StatusNotFound, "unknown_game",
			"Unknown game. Expected one of reaction, fill or pitstop.", "game")
		return
	}

	board, err := s.store.Board(r.Context(), id)
	if err != nil {
		s.log.Error("loading a board", "error", err, "game", id)
		writeError(w, http.StatusInternalServerError, "store_failed", "The leaderboard could not be loaded.")
		return
	}
	writeJSON(w, http.StatusOK, board)
}

// handleDelete removes an entry the word filter let through.
func (s *Server) handleDelete(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(r.PathValue("id"), 10, 64)
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_id", "The entry id must be a number.", "id")
		return
	}

	switch err := s.store.Delete(r.Context(), id); {
	case errors.Is(err, store.ErrNotFound):
		writeError(w, http.StatusNotFound, "not_found", "That entry no longer exists.")
		return
	case err != nil:
		s.log.Error("deleting an entry", "error", err, "id", id)
		writeError(w, http.StatusInternalServerError, "store_failed", "The entry could not be deleted.")
		return
	}

	s.log.Info("entry deleted by an operator", "id", id)
	s.publishBoards(r.Context())
	w.WriteHeader(http.StatusNoContent)
}

// handleHealth reports whether the server can reach its database. The booth
// operator hits this first when something looks wrong.
func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	if err := s.store.Ping(r.Context()); err != nil {
		s.log.Error("health check failed", "error", err)
		writeJSON(w, http.StatusServiceUnavailable, map[string]any{
			"status": "unhealthy",
			"detail": "the database is not reachable",
		})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"status":      "ok",
		"subscribers": s.hub.subscriberCount(),
	})
}

// boardsPayload renders the current boards as the compact JSON the SSE stream
// carries.
func (s *Server) boardsPayload(ctx context.Context) ([]byte, error) {
	boards, err := s.store.Boards(ctx)
	if err != nil {
		return nil, err
	}
	return json.Marshal(boards)
}

// publishBoards pushes the current boards to every connected display.
func (s *Server) publishBoards(ctx context.Context) {
	payload, err := s.boardsPayload(ctx)
	if err != nil {
		s.log.Error("building the SSE payload", "error", err)
		return
	}
	s.hub.broadcast(payload)
}

func capitalize(s string) string {
	if s == "" {
		return s
	}
	r := []rune(s)
	if r[0] >= 'a' && r[0] <= 'z' {
		r[0] -= 'a' - 'A'
	}
	return string(r)
}
