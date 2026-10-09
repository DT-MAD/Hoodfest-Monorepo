package store

import (
	"context"
	"errors"
	"testing"

	"edu.dixietech.pitstop/internal/game"
)

// runConformance exercises the behavior every Store must share. Both the
// in-memory fake and the Postgres implementation run it, so the fake used by
// the API tests cannot quietly drift from the real thing.
func runConformance(t *testing.T, newStore func(t *testing.T) Store) {
	ctx := context.Background()

	t.Run("ranks ascending with earliest-wins ties", func(t *testing.T) {
		s := newStore(t)
		mustInsert(t, s, game.Reaction, "SLOW", 500)
		first := mustInsert(t, s, game.Reaction, "TIEA", 200)
		second := mustInsert(t, s, game.Reaction, "TIEB", 200)

		if first.Rank != 1 || second.Rank != 2 {
			t.Errorf("tied ranks = %d and %d, want 1 and 2 (earlier submission first)",
				first.Rank, second.Rank)
		}

		b, err := s.Board(ctx, game.Reaction)
		if err != nil {
			t.Fatal(err)
		}
		if got := names(b.Top); !equal(got, []string{"TIEA", "TIEB", "SLOW"}) {
			t.Errorf("board order = %v, want [TIEA TIEB SLOW]", got)
		}
		for i, e := range b.Top {
			if e.Rank != i+1 {
				t.Errorf("Top[%d].Rank = %d, want %d", i, e.Rank, i+1)
			}
		}
	})

	t.Run("boards are isolated per game", func(t *testing.T) {
		s := newStore(t)
		mustInsert(t, s, game.Reaction, "ACE", 200)
		mustInsert(t, s, game.Fill, "RIO", 5)

		b, err := s.Board(ctx, game.Reaction)
		if err != nil {
			t.Fatal(err)
		}
		if b.Total != 1 || len(b.Top) != 1 || b.Top[0].Name != "ACE" {
			t.Errorf("reaction board leaked another game's entries: %+v", names(b.Top))
		}
	})

	t.Run("recent is newest first and capped", func(t *testing.T) {
		s := newStore(t)
		for _, n := range []string{"ONE", "TWO", "THREE", "FOUR"} {
			mustInsert(t, s, game.PitStop, n, 2000)
		}
		b, err := s.Board(ctx, game.PitStop)
		if err != nil {
			t.Fatal(err)
		}
		if len(b.Recent) != RecentN {
			t.Fatalf("Recent has %d entries, want %d", len(b.Recent), RecentN)
		}
		if b.Recent[0].Name != "FOUR" {
			t.Errorf("Recent[0] = %q, want the newest entry FOUR", b.Recent[0].Name)
		}
	})

	t.Run("top is capped", func(t *testing.T) {
		s := newStore(t)
		for i := 0; i < TopN+3; i++ {
			mustInsert(t, s, game.Fill, "P"+string(rune('A'+i)), i)
		}
		b, err := s.Board(ctx, game.Fill)
		if err != nil {
			t.Fatal(err)
		}
		if len(b.Top) != TopN {
			t.Errorf("Top has %d entries, want %d", len(b.Top), TopN)
		}
		if b.Total != TopN+3 {
			t.Errorf("Total = %d, want %d: every row is kept", b.Total, TopN+3)
		}
	})

	t.Run("empty boards serialize as empty slices", func(t *testing.T) {
		s := newStore(t)
		bs, err := s.Boards(ctx)
		if err != nil {
			t.Fatal(err)
		}
		if len(bs.Boards) != len(game.All()) {
			t.Fatalf("got %d boards, want %d", len(bs.Boards), len(game.All()))
		}
		for _, b := range bs.Boards {
			if b.Top == nil || b.Recent == nil {
				t.Errorf("board %q has a nil slice; JSON must show [] not null", b.Game)
			}
			if b.Total != 0 {
				t.Errorf("board %q has Total %d on an empty store", b.Game, b.Total)
			}
		}
	})

	t.Run("display string matches the game format", func(t *testing.T) {
		s := newStore(t)
		e := mustInsert(t, s, game.PitStop, "ACE", 2140)
		if e.Display != "2.140s" {
			t.Errorf("Display = %q, want %q", e.Display, "2.140s")
		}
	})

	t.Run("delete removes the entry and reports a missing one", func(t *testing.T) {
		s := newStore(t)
		e := mustInsert(t, s, game.Reaction, "OOPS", 250)

		if err := s.Delete(ctx, e.ID); err != nil {
			t.Fatalf("Delete: %v", err)
		}
		if err := s.Delete(ctx, e.ID); !errors.Is(err, ErrNotFound) {
			t.Errorf("second Delete = %v, want ErrNotFound", err)
		}
		b, _ := s.Board(ctx, game.Reaction)
		if b.Total != 0 {
			t.Errorf("Total = %d after deleting the only entry", b.Total)
		}
	})
}

func TestMemoryConformance(t *testing.T) {
	runConformance(t, func(t *testing.T) Store { return NewMemory() })
}

func TestPostgresConformance(t *testing.T) {
	runConformance(t, func(t *testing.T) Store { return newTestStore(t) })
}

func mustInsert(t *testing.T, s Store, id game.ID, player string, score int) Entry {
	t.Helper()
	e, err := s.Insert(context.Background(), NewEntry{
		Game: id, Name: player, Score: score, Platform: Web,
	})
	if err != nil {
		t.Fatalf("Insert(%s, %s, %d): %v", id, player, score, err)
	}
	return e
}

func names(entries []Entry) []string {
	out := make([]string, len(entries))
	for i, e := range entries {
		out[i] = e.Name
	}
	return out
}

func equal(a, b []string) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}
