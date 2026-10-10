# Pit Stop — Server & Leaderboard

The Go backend behind the Pit Stop booth: a small REST API the two mobile apps
submit scores to, and a live leaderboard page for the screen at the booth.

## Quick start

```sh
cp .env.example .env     # then edit the keys
make dev                 # starts Postgres in Docker and runs the server
```

Then open:

| URL | What it is |
| --- | ---------- |
| <http://localhost:8080/> | the leaderboard display |
| <http://localhost:8080/admin> | the operator dashboard, unlocked with `ADMIN_KEY` |
| <http://localhost:8080/healthz> | database reachability |

`make help` lists every target.

## Configuration

All configuration is environment variables; see [`.env.example`](.env.example).
`DATABASE_URL`, `API_KEY` and `ADMIN_KEY` are required and the server refuses to
start without them, naming the one that is missing.

`API_KEY` and `ADMIN_KEY` must differ. The API key is compiled into both app
binaries, so anything it unlocks is effectively public; the admin key is not.

## API

| Method | Path | Auth | Purpose |
| ------ | ---- | ---- | ------- |
| `GET` | `/api/leaderboard` | — | all three boards |
| `GET` | `/api/leaderboard/{game}` | — | one board |
| `GET` | `/api/events` | — | SSE stream, pushed on every change |
| `POST` | `/api/scores` | `X-API-Key` | submit a score |
| `GET` | `/api/admin/stats` | `X-Admin-Key` | entry counts and the boards |
| `POST` | `/api/admin/seed` | `X-Admin-Key` | load the starter scores |
| `DELETE` | `/api/admin/entries` | `X-Admin-Key` | wipe every score (needs `?confirm=RESET`) |
| `DELETE` | `/api/admin/entries/{id}` | `X-Admin-Key` | remove one entry |
| `GET` | `/healthz` | — | database ping |

Submitting a score:

```sh
curl -X POST localhost:8080/api/scores \
  -H "X-API-Key: $API_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"game":"reaction","name":"ace","score":243,"platform":"ios"}'
```

The response carries the stored entry — canonicalized name, formatted score and
the rank it earned — plus the refreshed board, so an app needs no second call:

```json
{
  "entry": { "id": 1, "name": "ACE", "score": 243, "display": "243 ms", "rank": 1 },
  "board": { "game": "reaction", "top": [ ... ], "recent": [ ... ], "total": 1 }
}
```

Errors are always the same shape, with a message fit to show a player:

```json
{ "error": { "code": "unavailable_name", "message": "Pick a different name.", "field": "name" } }
```

## The operator dashboard

`/admin` is locked behind `ADMIN_KEY`, which is typed into the page and kept in
`sessionStorage` — closing the tab forgets it. It shows entry counts, and offers:

**Load starter scores.** Fills all three boards with ten times apiece so the
first visitor of the day has something to beat rather than three columns of
"No scores yet". The scores are deliberately soft: one good time at the top to
chase, then a quick fall away, so an ordinary attempt lands on the board.
A test asserts that shape, so the set cannot quietly drift out of reach.

Pressing it again **replaces** the previous starter set rather than stacking
another one on top, and **leaves real runs alone** — an operator topping the
boards back up mid-event will not delete what people actually played. Starter
rows are tagged in `detail` and labelled on the dashboard.

**Reset everything.** Wipes every score, starter and real alike. Irreversible,
so it needs `?confirm=RESET` on the request and the word typed into a dialog in
the page. The dashboard is often left open on a laptop at a booth and a
mis-click must not cost the day's scores.

**Remove an entry.** Per-row delete for anything the word filter missed.

Seeding and resetting both push to the display over SSE, so the booth screen
updates without anyone touching it.

## Scoring

Lower wins on every board; ties break on the earlier submission.

| Game | `game` | Score unit | Valid range |
| ---- | ------ | ---------- | ----------- |
| Reaction Lights | `reaction` | milliseconds | 50 – 10,000 |
| Fill It Up | `fill` | cents off target | 0 – 100,000 |
| Perfect Pit Stop | `pitstop` | ms + 1000 per mis-tap | 300 – 120,000 |

Out-of-range scores are rejected as implausible. The rules live in
[`internal/game`](internal/game/game.go) and are mirrored by the iOS and Android
clients; all three assert the same
[`spec/score-fixtures.json`](../spec/score-fixtures.json).

## Layout

```
cmd/server         entrypoint, graceful shutdown
internal/config    environment loading, fails fast
internal/game      the scoring specification
internal/profanity name screening for a public display
internal/seed      the starter scores the dashboard can load
internal/name      name canonicalization and validation
internal/store     Store interface, Postgres and in-memory implementations
internal/api       routes, middleware, SSE hub, templates and static assets
```

The API depends on the `Store` interface rather than Postgres, so the handler
tests run against an in-memory implementation. Both implementations pass the
same conformance suite, so the fake cannot drift from the real thing.

## Tests

```sh
make test        # Postgres-backed tests skip without a database
make test-all    # starts Postgres in Docker and runs everything
make test-race   # the full suite under the race detector
```

## Notes for the booth

- The display falls back to polling if the event stream drops, and says so in
  the header — it will never sit silently stale.
- A browser that stops reading its stream is dropped rather than allowed to
  block a score submission.
- The schema is applied on every boot and is idempotent; there is no migration
  step to forget.
- `LOG_LEVEL=debug` turns on per-subscriber connection logging.
