// Package seed provides the starter scores a booth operator can load onto an
// empty leaderboard.
//
// An empty board is a bad first impression: the first visitor of the day sees
// three columns of "No scores yet" and has nothing to beat. Seeding fills all
// three boards so there is always a ladder to climb.
//
// The scores are deliberately soft. Each board has one genuinely good time at
// the top to aim at, and then falls away quickly, so a visitor having an
// ordinary go will land somewhere on the board rather than off the bottom of
// it. The whole point is that people see their own name.
package seed

import (
	"edu.dixietech.pitstop/internal/game"
	"edu.dixietech.pitstop/internal/store"
)

// Marker is set in an entry's detail so seeded rows can be told apart from real
// ones — which is what lets seeding twice replace rather than duplicate.
const Marker = "seeded"

// Row is one starter score.
type Row struct {
	Game  game.ID
	Name  string
	Score int
}

// Rows returns the full starter set: ten per board, in the order they will rank.
//
// Reaction times are in milliseconds, Fill It Up in cents off the target, and
// Perfect Pit Stop in milliseconds including mis-tap penalties. A typical adult
// reacts in about 250ms, so everything from third place down is beatable by an
// ordinary attempt.
func Rows() []Row {
	return []Row{
		// Reaction Lights — lower is faster.
		{game.Reaction, "ACE", 231},
		{game.Reaction, "TURBO", 288},
		{game.Reaction, "NITRO", 342},
		{game.Reaction, "DRIFTER", 397},
		{game.Reaction, "PISTON", 436},
		{game.Reaction, "RALLY", 468},
		{game.Reaction, "APEX", 503},
		{game.Reaction, "CHICANE", 547},
		{game.Reaction, "REDLINE", 589},
		{game.Reaction, "SLIPSTREAM", 634},

		// Fill It Up — cents away from the dollar target.
		{game.Fill, "GEARBOX", 3},
		{game.Fill, "CLUTCH", 11},
		{game.Fill, "OCTANE", 24},
		{game.Fill, "CAMBER", 44},
		{game.Fill, "DOWNFORCE", 63},
		{game.Fill, "TORQUE", 78},
		{game.Fill, "SPARKPLUG", 96},
		{game.Fill, "AXLE", 124},
		{game.Fill, "TAILPIPE", 162},
		{game.Fill, "GRIDLOCK", 210},

		// Perfect Pit Stop — elapsed milliseconds plus penalties.
		{game.PitStop, "BURNOUT", 2187},
		{game.PitStop, "HAIRPIN", 2562},
		{game.PitStop, "DIFFUSER", 2884},
		{game.PitStop, "FLATOUT", 3350},
		{game.PitStop, "INTAKE", 4050},
		{game.PitStop, "KERB", 4420},
		{game.PitStop, "TAILGATE", 4830},
		{game.PitStop, "ROLLCAGE", 5260},
		{game.PitStop, "PITWALL", 5740},
		{game.PitStop, "BACKMARKER", 6310},
	}
}

// Entries converts the starter set into storable entries, each tagged so it can
// be replaced on a later seed.
func Entries() []store.NewEntry {
	rows := Rows()
	entries := make([]store.NewEntry, 0, len(rows))

	for _, row := range rows {
		entries = append(entries, store.NewEntry{
			Game:     row.Game,
			Name:     row.Name,
			Score:    row.Score,
			Platform: store.Web,
			Detail:   map[string]any{Marker: true},
		})
	}
	return entries
}
