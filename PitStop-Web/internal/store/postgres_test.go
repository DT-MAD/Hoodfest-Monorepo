package store

import (
	"context"
	"errors"
	"os"
	"testing"
	"time"

	"edu.dixietech.pitstop/internal/game"
)

// newTestStore connects to the database named by TEST_DATABASE_URL and hands
// back a clean table. Without that variable the Postgres-backed tests skip, so
// `go test ./...` stays green on a machine with no database.
func newTestStore(t *testing.T) *Postgres {
	t.Helper()

	url := os.Getenv("TEST_DATABASE_URL")
	if url == "" {
		t.Skip("TEST_DATABASE_URL is not set; skipping Postgres-backed tests")
	}
	if testing.Short() {
		t.Skip("skipping Postgres-backed tests in short mode")
	}

	ctx := context.Background()
	s, err := OpenPostgres(ctx, url)
	if err != nil {
		t.Fatalf("OpenPostgres: %v", err)
	}
	t.Cleanup(s.Close)

	if _, err := s.pool.Exec(ctx, `TRUNCATE entries RESTART IDENTITY`); err != nil {
		t.Fatalf("truncating entries: %v", err)
	}
	return s
}

func insert(t *testing.T, s *Postgres, id game.ID, name string, score int) Entry {
	t.Helper()
	e, err := s.Insert(context.Background(), NewEntry{
		Game: id, Name: name, Score: score, Platform: Web,
		Detail: map[string]any{"note": "test"},
	})
	if err != nil {
		t.Fatalf("Insert(%s, %s, %d): %v", id, name, score, err)
	}
	return e
}

func TestSchemaIsIdempotent(t *testing.T) {
	s := newTestStore(t)
	// A second OpenPostgres on the same database re-applies the schema.
	s2, err := OpenPostgres(context.Background(), os.Getenv("TEST_DATABASE_URL"))
	if err != nil {
		t.Fatalf("re-applying the schema failed: %v", err)
	}
	s2.Close()
	_ = s
}

func TestInsertAndBoard(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()

	insert(t, s, game.Reaction, "SLOW", 500)
	insert(t, s, game.Reaction, "ACE", 180)
	insert(t, s, game.Reaction, "MID", 300)

	b, err := s.Board(ctx, game.Reaction)
	if err != nil {
		t.Fatalf("Board: %v", err)
	}
	if b.Total != 3 {
		t.Errorf("Total = %d, want 3", b.Total)
	}
	want := []string{"ACE", "MID", "SLOW"}
	if len(b.Top) != len(want) {
		t.Fatalf("Top has %d entries, want %d", len(b.Top), len(want))
	}
	for i, name := range want {
		if b.Top[i].Name != name {
			t.Errorf("Top[%d] = %q, want %q (lower score must rank first)", i, b.Top[i].Name, name)
		}
		if b.Top[i].Rank != i+1 {
			t.Errorf("Top[%d].Rank = %d, want %d", i, b.Top[i].Rank, i+1)
		}
	}
	if b.Top[0].Display != "180 ms" {
		t.Errorf("Display = %q, want %q", b.Top[0].Display, "180 ms")
	}
	if b.Top[0].Detail["note"] != "test" {
		t.Errorf("Detail did not round-trip: %#v", b.Top[0].Detail)
	}
}

func TestTiesBreakOnEarlierSubmission(t *testing.T) {
	s := newTestStore(t)

	first := insert(t, s, game.PitStop, "FIRST", 2000)
	time.Sleep(5 * time.Millisecond)
	second := insert(t, s, game.PitStop, "SECOND", 2000)

	if first.Rank != 1 {
		t.Errorf("the earlier of two tied entries got rank %d, want 1", first.Rank)
	}
	if second.Rank != 2 {
		t.Errorf("the later of two tied entries got rank %d, want 2", second.Rank)
	}

	b, err := s.Board(context.Background(), game.PitStop)
	if err != nil {
		t.Fatalf("Board: %v", err)
	}
	if b.Top[0].Name != "FIRST" {
		t.Errorf("Top[0] = %q, want FIRST", b.Top[0].Name)
	}
}

func TestBoardLimits(t *testing.T) {
	s := newTestStore(t)

	for i := 0; i < TopN+5; i++ {
		insert(t, s, game.Fill, "P"+string(rune('A'+i)), i*10)
	}

	b, err := s.Board(context.Background(), game.Fill)
	if err != nil {
		t.Fatalf("Board: %v", err)
	}
	if len(b.Top) != TopN {
		t.Errorf("Top has %d entries, want the %d cap", len(b.Top), TopN)
	}
	if len(b.Recent) != RecentN {
		t.Errorf("Recent has %d entries, want the %d cap", len(b.Recent), RecentN)
	}
	if b.Total != TopN+5 {
		t.Errorf("Total = %d, want every row counted (%d)", b.Total, TopN+5)
	}
	// Recent is newest first, and the newest row is the worst score here.
	if b.Recent[0].Name != "P"+string(rune('A'+TopN+4)) {
		t.Errorf("Recent[0] = %q, want the most recently inserted entry", b.Recent[0].Name)
	}
}

func TestBoardsCoversEveryGame(t *testing.T) {
	s := newTestStore(t)
	insert(t, s, game.Reaction, "ACE", 200)

	bs, err := s.Boards(context.Background())
	if err != nil {
		t.Fatalf("Boards: %v", err)
	}
	if len(bs.Boards) != len(game.All()) {
		t.Fatalf("got %d boards, want %d", len(bs.Boards), len(game.All()))
	}
	for i, id := range game.All() {
		if bs.Boards[i].Game != id {
			t.Errorf("board %d is %q, want %q in display order", i, bs.Boards[i].Game, id)
		}
		if bs.Boards[i].Title == "" {
			t.Errorf("board %q has no title", id)
		}
		// An empty board must serialize as [], never null.
		if bs.Boards[i].Top == nil || bs.Boards[i].Recent == nil {
			t.Errorf("board %q has nil slices; they must be empty slices for JSON", id)
		}
	}
}

func TestDelete(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()

	e := insert(t, s, game.Reaction, "OOPS", 250)
	if err := s.Delete(ctx, e.ID); err != nil {
		t.Fatalf("Delete: %v", err)
	}

	b, _ := s.Board(ctx, game.Reaction)
	if b.Total != 0 {
		t.Errorf("Total = %d after deleting the only entry, want 0", b.Total)
	}
	if err := s.Delete(ctx, e.ID); !errors.Is(err, ErrNotFound) {
		t.Errorf("deleting a missing entry = %v, want ErrNotFound", err)
	}
}

func TestDatabaseRejectsBadValues(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()

	// The CHECK constraints are the last line of defense behind the API's
	// validation; make sure they are actually in place.
	cases := []struct {
		name string
		e    NewEntry
	}{
		{"unknown game", NewEntry{Game: "tires", Name: "ACE", Score: 10, Platform: Web}},
		{"unknown platform", NewEntry{Game: game.Reaction, Name: "ACE", Score: 10, Platform: "desktop"}},
		{"negative score", NewEntry{Game: game.Reaction, Name: "ACE", Score: -1, Platform: Web}},
		{"empty name", NewEntry{Game: game.Reaction, Name: "", Score: 10, Platform: Web}},
		{"overlong name", NewEntry{Game: game.Reaction, Name: "THIS IS FAR TOO LONG", Score: 10, Platform: Web}},
	}
	for _, tc := range cases {
		if _, err := s.Insert(ctx, tc.e); err == nil {
			t.Errorf("%s: Insert succeeded, want a constraint violation", tc.name)
		}
	}
}

func TestPing(t *testing.T) {
	s := newTestStore(t)
	if err := s.Ping(context.Background()); err != nil {
		t.Errorf("Ping: %v", err)
	}
}
