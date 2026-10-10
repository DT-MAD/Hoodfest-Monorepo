package store

import (
	"context"
	_ "embed"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"edu.dixietech.pitstop/internal/game"
)

//go:embed schema.sql
var schema string

// Postgres is the production Store.
type Postgres struct {
	pool *pgxpool.Pool
}

// OpenPostgres connects to the database, verifies the connection, and applies
// the schema. The schema is idempotent, so this is safe on every boot.
func OpenPostgres(ctx context.Context, databaseURL string) (*Postgres, error) {
	cfg, err := pgxpool.ParseConfig(databaseURL)
	if err != nil {
		return nil, fmt.Errorf("parsing DATABASE_URL: %w", err)
	}
	// A booth server serves one display and a few tablets; a small pool is
	// plenty and keeps the footprint predictable on a laptop.
	cfg.MaxConns = 10
	cfg.MinConns = 1
	cfg.MaxConnIdleTime = 5 * time.Minute

	pool, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		return nil, fmt.Errorf("connecting to Postgres: %w", err)
	}

	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("Postgres is not reachable: %w", err)
	}

	if _, err := pool.Exec(ctx, schema); err != nil {
		pool.Close()
		return nil, fmt.Errorf("applying schema: %w", err)
	}

	return &Postgres{pool: pool}, nil
}

func (p *Postgres) Close() { p.pool.Close() }

func (p *Postgres) Ping(ctx context.Context) error { return p.pool.Ping(ctx) }

// Insert saves a score and reports where it landed on the board.
func (p *Postgres) Insert(ctx context.Context, e NewEntry) (Entry, error) {
	detail, err := json.Marshal(orEmpty(e.Detail))
	if err != nil {
		return Entry{}, fmt.Errorf("encoding detail: %w", err)
	}

	const q = `
		INSERT INTO entries (game, player_name, score, platform, detail)
		VALUES ($1, $2, $3, $4, $5)
		RETURNING id, created_at`

	var out Entry
	row := p.pool.QueryRow(ctx, q, string(e.Game), e.Name, e.Score, string(e.Platform), detail)
	if err := row.Scan(&out.ID, &out.CreatedAt); err != nil {
		return Entry{}, fmt.Errorf("inserting entry: %w", err)
	}

	out.Game = e.Game
	out.Name = e.Name
	out.Score = e.Score
	out.Display = e.Game.FormatScore(e.Score)
	out.Platform = e.Platform
	out.Detail = e.Detail

	rank, err := p.rankOf(ctx, out)
	if err != nil {
		return Entry{}, err
	}
	out.Rank = rank

	return out, nil
}

// rankOf counts the entries that beat this one. Ties break on the earlier
// submission, matching the ordering used to build a board.
func (p *Postgres) rankOf(ctx context.Context, e Entry) (int, error) {
	const q = `
		SELECT count(*) + 1
		FROM entries
		WHERE game = $1
		  AND (score < $2 OR (score = $2 AND created_at < $3))`

	var rank int
	if err := p.pool.QueryRow(ctx, q, string(e.Game), e.Score, e.CreatedAt).Scan(&rank); err != nil {
		return 0, fmt.Errorf("computing rank: %w", err)
	}
	return rank, nil
}

// Boards returns all three leaderboards in display order.
func (p *Postgres) Boards(ctx context.Context) (Boards, error) {
	out := Boards{UpdatedAt: time.Now().UTC()}
	for _, id := range game.All() {
		b, err := p.Board(ctx, id)
		if err != nil {
			return Boards{}, err
		}
		out.Boards = append(out.Boards, b)
	}
	return out, nil
}

// Board returns one game's top scores, most recent entries, and total count.
func (p *Postgres) Board(ctx context.Context, id game.ID) (Board, error) {
	b := Board{Game: id, Title: id.Title(), Tagline: id.Tagline()}

	top, err := p.query(ctx, `
		SELECT id, game, player_name, score, platform, detail, created_at
		FROM entries
		WHERE game = $1
		ORDER BY score ASC, created_at ASC
		LIMIT $2`, string(id), TopN)
	if err != nil {
		return Board{}, fmt.Errorf("loading top scores for %s: %w", id, err)
	}
	for i := range top {
		top[i].Rank = i + 1
	}
	b.Top = top

	recent, err := p.query(ctx, `
		SELECT id, game, player_name, score, platform, detail, created_at
		FROM entries
		WHERE game = $1
		ORDER BY created_at DESC
		LIMIT $2`, string(id), RecentN)
	if err != nil {
		return Board{}, fmt.Errorf("loading recent scores for %s: %w", id, err)
	}
	b.Recent = recent

	if err := p.pool.QueryRow(ctx,
		`SELECT count(*) FROM entries WHERE game = $1`, string(id),
	).Scan(&b.Total); err != nil {
		return Board{}, fmt.Errorf("counting entries for %s: %w", id, err)
	}

	return b, nil
}

// Delete removes one entry.
func (p *Postgres) Delete(ctx context.Context, id int64) error {
	tag, err := p.pool.Exec(ctx, `DELETE FROM entries WHERE id = $1`, id)
	if err != nil {
		return fmt.Errorf("deleting entry %d: %w", id, err)
	}
	if tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

// query runs a board query and decodes the rows into entries.
func (p *Postgres) query(ctx context.Context, sql string, args ...any) ([]Entry, error) {
	rows, err := p.pool.Query(ctx, sql, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	entries := []Entry{}
	for rows.Next() {
		var e Entry
		var raw []byte
		if err := rows.Scan(&e.ID, &e.Game, &e.Name, &e.Score, &e.Platform, &raw, &e.CreatedAt); err != nil {
			return nil, err
		}
		if len(raw) > 0 {
			if err := json.Unmarshal(raw, &e.Detail); err != nil {
				return nil, fmt.Errorf("decoding detail for entry %d: %w", e.ID, err)
			}
		}
		e.Display = e.Game.FormatScore(e.Score)
		entries = append(entries, e)
	}
	if err := rows.Err(); err != nil && !errors.Is(err, pgx.ErrNoRows) {
		return nil, err
	}
	return entries, nil
}

func orEmpty(m map[string]any) map[string]any {
	if m == nil {
		return map[string]any{}
	}
	return m
}

// Postgres satisfies Store.
var _ Store = (*Postgres)(nil)

// InsertMany saves several entries in one transaction, so a half-written seed
// cannot leave the boards in a strange state.
func (p *Postgres) InsertMany(ctx context.Context, entries []NewEntry) (int, error) {
	if len(entries) == 0 {
		return 0, nil
	}

	tx, err := p.pool.Begin(ctx)
	if err != nil {
		return 0, fmt.Errorf("starting a transaction: %w", err)
	}
	defer tx.Rollback(ctx)

	const q = `
		INSERT INTO entries (game, player_name, score, platform, detail)
		VALUES ($1, $2, $3, $4, $5)`

	for _, e := range entries {
		detail, err := json.Marshal(orEmpty(e.Detail))
		if err != nil {
			return 0, fmt.Errorf("encoding detail: %w", err)
		}
		if _, err := tx.Exec(ctx, q, string(e.Game), e.Name, e.Score, string(e.Platform), detail); err != nil {
			return 0, fmt.Errorf("inserting entry for %s: %w", e.Name, err)
		}
	}

	if err := tx.Commit(ctx); err != nil {
		return 0, fmt.Errorf("committing: %w", err)
	}
	return len(entries), nil
}

// DeleteAll empties the boards.
func (p *Postgres) DeleteAll(ctx context.Context) (int, error) {
	tag, err := p.pool.Exec(ctx, `DELETE FROM entries`)
	if err != nil {
		return 0, fmt.Errorf("deleting every entry: %w", err)
	}
	return int(tag.RowsAffected()), nil
}

// DeleteWhereDetail removes every entry carrying the given detail key.
func (p *Postgres) DeleteWhereDetail(ctx context.Context, key string) (int, error) {
	tag, err := p.pool.Exec(ctx, `DELETE FROM entries WHERE detail ? $1`, key)
	if err != nil {
		return 0, fmt.Errorf("deleting entries tagged %q: %w", key, err)
	}
	return int(tag.RowsAffected()), nil
}

// Stats summarizes the boards.
func (p *Postgres) Stats(ctx context.Context) (Stats, error) {
	out := Stats{PerGame: map[game.ID]int{}}
	for _, id := range game.All() {
		out.PerGame[id] = 0
	}

	rows, err := p.pool.Query(ctx, `SELECT game, count(*) FROM entries GROUP BY game`)
	if err != nil {
		return Stats{}, fmt.Errorf("counting entries: %w", err)
	}
	defer rows.Close()

	for rows.Next() {
		var id game.ID
		var n int
		if err := rows.Scan(&id, &n); err != nil {
			return Stats{}, err
		}
		out.PerGame[id] = n
		out.Total += n
	}
	if err := rows.Err(); err != nil {
		return Stats{}, err
	}

	if err := p.pool.QueryRow(ctx,
		`SELECT count(*) FROM entries WHERE detail ? $1`, seedMarker,
	).Scan(&out.Seeded); err != nil {
		return Stats{}, fmt.Errorf("counting seeded entries: %w", err)
	}

	return out, nil
}

// seedMarker is the detail key the seeder tags its rows with. Declared here
// rather than imported so that store does not depend on the seed package.
const seedMarker = "seeded"
