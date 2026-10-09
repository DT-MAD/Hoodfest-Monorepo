package store

import (
	"context"
	"sort"
	"sync"
	"time"

	"edu.dixietech.pitstop/internal/game"
)

// Memory is an in-memory Store used by the API tests, and usable as a
// throwaway backend when demonstrating the server without a database.
//
// It reproduces the ordering rules of the Postgres implementation — ascending
// score, ties broken by the earlier submission — but not its CHECK constraints,
// which exist to catch what the API layer failed to reject.
type Memory struct {
	mu       sync.RWMutex
	entries  []Entry
	nextID   int64
	lastTime time.Time
}

// NewMemory returns an empty in-memory store.
func NewMemory() *Memory {
	return &Memory{nextID: 1}
}

// stamp returns a timestamp strictly later than the previous one, so that two
// entries inserted in the same nanosecond still have a definite order to break
// a score tie on. The caller holds the lock.
func (m *Memory) stamp() time.Time {
	t := time.Now().UTC()
	if !t.After(m.lastTime) {
		t = m.lastTime.Add(time.Nanosecond)
	}
	m.lastTime = t
	return t
}

func (m *Memory) Close() {}

func (m *Memory) Ping(context.Context) error { return nil }

func (m *Memory) Insert(_ context.Context, e NewEntry) (Entry, error) {
	m.mu.Lock()
	defer m.mu.Unlock()

	entry := Entry{
		ID:        m.nextID,
		Game:      e.Game,
		Name:      e.Name,
		Score:     e.Score,
		Display:   e.Game.FormatScore(e.Score),
		Platform:  e.Platform,
		Detail:    e.Detail,
		CreatedAt: m.stamp(),
	}
	m.nextID++
	m.entries = append(m.entries, entry)

	entry.Rank = 1
	for _, other := range m.entries {
		if other.ID == entry.ID || other.Game != entry.Game {
			continue
		}
		if other.Score < entry.Score ||
			(other.Score == entry.Score && other.CreatedAt.Before(entry.CreatedAt)) {
			entry.Rank++
		}
	}
	return entry, nil
}

func (m *Memory) Board(_ context.Context, id game.ID) (Board, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.board(id), nil
}

func (m *Memory) Boards(_ context.Context) (Boards, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()

	out := Boards{UpdatedAt: time.Now().UTC()}
	for _, id := range game.All() {
		out.Boards = append(out.Boards, m.board(id))
	}
	return out, nil
}

// board builds one leaderboard. The caller holds the lock.
func (m *Memory) board(id game.ID) Board {
	var all []Entry
	for _, e := range m.entries {
		if e.Game == id {
			all = append(all, e)
		}
	}

	byScore := append([]Entry(nil), all...)
	sort.SliceStable(byScore, func(i, j int) bool {
		if byScore[i].Score != byScore[j].Score {
			return byScore[i].Score < byScore[j].Score
		}
		return byScore[i].CreatedAt.Before(byScore[j].CreatedAt)
	})
	top := make([]Entry, 0, TopN)
	for i, e := range byScore {
		if i == TopN {
			break
		}
		e.Rank = i + 1
		top = append(top, e)
	}

	byRecency := append([]Entry(nil), all...)
	sort.SliceStable(byRecency, func(i, j int) bool {
		return byRecency[i].CreatedAt.After(byRecency[j].CreatedAt)
	})
	recent := make([]Entry, 0, RecentN)
	for i, e := range byRecency {
		if i == RecentN {
			break
		}
		e.Rank = 0
		recent = append(recent, e)
	}

	return Board{
		Game:    id,
		Title:   id.Title(),
		Tagline: id.Tagline(),
		Top:     top,
		Recent:  recent,
		Total:   len(all),
	}
}

func (m *Memory) Delete(_ context.Context, id int64) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	for i, e := range m.entries {
		if e.ID == id {
			m.entries = append(m.entries[:i], m.entries[i+1:]...)
			return nil
		}
	}
	return ErrNotFound
}

var _ Store = (*Memory)(nil)
