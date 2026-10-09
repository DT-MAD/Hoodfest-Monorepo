-- Pit Stop leaderboard schema.
--
-- Applied in full on every boot; every statement is idempotent, so there is no
-- migration tool and no ordering to get wrong at the booth.

CREATE TABLE IF NOT EXISTS entries (
    id          BIGSERIAL   PRIMARY KEY,
    game        TEXT        NOT NULL CHECK (game IN ('reaction', 'fill', 'pitstop')),
    player_name TEXT        NOT NULL CHECK (char_length(player_name) BETWEEN 1 AND 16),
    -- Canonical score in the game's own unit. Lower is always better:
    --   reaction  milliseconds from green to touch
    --   fill      cents away from the dollar target
    --   pitstop   milliseconds elapsed, plus 1000 per mis-tap
    score       INTEGER     NOT NULL CHECK (score >= 0),
    platform    TEXT        NOT NULL CHECK (platform IN ('ios', 'android', 'web')),
    -- Per-game extras for display: the fill target and gas price, the pit stop
    -- mis-tap count. Kept out of columns so a new game needs no migration.
    detail      JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Serves the top-N board query, including the earliest-wins tie-break.
CREATE INDEX IF NOT EXISTS entries_board_idx
    ON entries (game, score ASC, created_at ASC);

-- Serves the "latest entries" strip under each board.
CREATE INDEX IF NOT EXISTS entries_recent_idx
    ON entries (game, created_at DESC);
