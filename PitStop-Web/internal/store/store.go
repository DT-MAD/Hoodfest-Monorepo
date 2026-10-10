// Package store persists leaderboard entries.
package store

import (
	"context"
	"errors"
	"time"

	"edu.dixietech.pitstop/internal/game"
)

// ErrNotFound is returned when an entry id does not exist.
var ErrNotFound = errors.New("entry not found")

// Platform identifies which client submitted a score.
type Platform string

const (
	IOS     Platform = "ios"
	Android Platform = "android"
	Web     Platform = "web"
)

// Valid reports whether p is a platform the database will accept.
func (p Platform) Valid() bool {
	switch p {
	case IOS, Android, Web:
		return true
	}
	return false
}

// Entry is one score on one leaderboard.
type Entry struct {
	ID        int64          `json:"id"`
	Game      game.ID        `json:"game"`
	Name      string         `json:"name"`
	Score     int            `json:"score"`
	Display   string         `json:"display"` // Score formatted for the UI
	Platform  Platform       `json:"platform"`
	Detail    map[string]any `json:"detail,omitempty"`
	CreatedAt time.Time      `json:"createdAt"`

	// Rank is the entry's 1-based position on its board, set only where the
	// query computed it. Zero means "not ranked in this result".
	Rank int `json:"rank,omitempty"`
}

// NewEntry is a score being submitted.
type NewEntry struct {
	Game     game.ID
	Name     string
	Score    int
	Platform Platform
	Detail   map[string]any
}

// Board is a single game's leaderboard: the best scores, plus the handful of
// most recent ones so a player who did not place still sees their name.
type Board struct {
	Game    game.ID `json:"game"`
	Title   string  `json:"title"`
	Tagline string  `json:"tagline"`
	Top     []Entry `json:"top"`
	Recent  []Entry `json:"recent"`
	Total   int     `json:"total"`
}

// Boards is the full payload: every game's board, in display order.
type Boards struct {
	Boards    []Board   `json:"boards"`
	UpdatedAt time.Time `json:"updatedAt"`
}

// Display limits, as agreed for the booth screen.
const (
	TopN    = 10
	RecentN = 3
)

// Stats summarizes what is on the boards, for the moderation dashboard.
type Stats struct {
	Total   int             `json:"total"`
	Seeded  int             `json:"seeded"`
	PerGame map[game.ID]int `json:"perGame"`
}

// Store is the persistence interface. The API layer depends on this rather
// than on Postgres, which lets the handler tests run against an in-memory fake.
type Store interface {
	// Insert saves a score and returns the stored entry with its id, timestamp
	// and rank filled in.
	Insert(ctx context.Context, e NewEntry) (Entry, error)

	// Boards returns every game's leaderboard.
	Boards(ctx context.Context) (Boards, error)

	// Board returns one game's leaderboard.
	Board(ctx context.Context, id game.ID) (Board, error)

	// Delete removes an entry, returning ErrNotFound if it was not there.
	Delete(ctx context.Context, id int64) error

	// InsertMany saves several entries at once. Used to seed a board.
	InsertMany(ctx context.Context, entries []NewEntry) (int, error)

	// DeleteAll removes every entry and returns how many went, resetting the
	// boards for a fresh event.
	DeleteAll(ctx context.Context) (int, error)

	// DeleteWhereDetail removes every entry whose detail has key set, and
	// returns how many went. Used to replace seeded rows rather than stack
	// another set on top of them.
	DeleteWhereDetail(ctx context.Context, key string) (int, error)

	// Stats summarizes the boards for the moderation dashboard.
	Stats(ctx context.Context) (Stats, error)

	// Ping reports whether the backing store is reachable.
	Ping(ctx context.Context) error

	// Close releases resources.
	Close()
}
