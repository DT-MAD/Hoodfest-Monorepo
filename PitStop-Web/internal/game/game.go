// Package game holds the authoritative Pit Stop scoring specification.
//
// Every rule in this file is mirrored byte-for-byte in the iOS and Android
// clients (ScoreRules.swift / ScoreRules.kt) and asserted against the same
// fixture table in all three test suites. If you change a number here, change
// it in all three places or the parity tests will fail.
//
// The governing rule: for all three games, a LOWER score is better.
package game

import "fmt"

// ID identifies one of the three mini-games. These strings are the wire format
// and the database enum; do not rename them.
type ID string

const (
	Reaction ID = "reaction"
	Fill     ID = "fill"
	PitStop  ID = "pitstop"
)

// All returns the games in the order they are displayed, left to right.
func All() []ID { return []ID{Reaction, Fill, PitStop} }

// Valid reports whether id names a real game.
func (id ID) Valid() bool {
	switch id {
	case Reaction, Fill, PitStop:
		return true
	}
	return false
}

// Title is the display name shown on leaderboards.
func (id ID) Title() string {
	switch id {
	case Reaction:
		return "Reaction Lights"
	case Fill:
		return "Fill It Up"
	case PitStop:
		return "Perfect Pit Stop"
	}
	return string(id)
}

// Tagline is the one-line explanation shown above each board.
func (id ID) Tagline() string {
	switch id {
	case Reaction:
		return "Fastest reaction to the green light"
	case Fill:
		return "Closest to the dollar target"
	case PitStop:
		return "Fastest four-tire change"
	}
	return ""
}

// Score bounds. A submission outside these is implausible for a real round and
// is rejected by the API — it is either a bug or someone with curl.
const (
	ReactionMinMS = 50
	ReactionMaxMS = 10_000

	FillMinCentsOff = 0
	FillMaxCentsOff = 100_000

	PitStopMinMS = 300
	PitStopMaxMS = 120_000
)

// Bounds returns the inclusive valid range for a game's canonical score.
func (id ID) Bounds() (min, max int) {
	switch id {
	case Reaction:
		return ReactionMinMS, ReactionMaxMS
	case Fill:
		return FillMinCentsOff, FillMaxCentsOff
	case PitStop:
		return PitStopMinMS, PitStopMaxMS
	}
	return 0, 0
}

// InBounds reports whether score is plausible for this game.
func (id ID) InBounds(score int) bool {
	min, max := id.Bounds()
	return score >= min && score <= max
}

// Round parameters shared by the clients. The server never generates a round,
// but these live here so the specification has exactly one home.
const (
	// Reaction Lights: the red light holds for a uniformly random delay in
	// [ReactionWaitMinMS, ReactionWaitMaxMS] before turning green.
	ReactionWaitMinMS = 2_000
	ReactionWaitMaxMS = 5_000

	// Fill It Up: the dollar target is a multiple of 50 cents in
	// [FillGoalMinCents, FillGoalMaxCents]; gas costs a uniformly random
	// price in [FillPriceMinCents, FillPriceMaxCents] per gallon.
	FillGoalMinCents  = 500
	FillGoalMaxCents  = 2_000
	FillGoalStepCents = 50
	FillPriceMinCents = 200
	FillPriceMaxCents = 500

	// The pump dispenses at a fixed rate, accumulated from monotonic hold
	// time so the result does not depend on frame rate. Expressed in
	// thousandths of a gallon per second to keep the constant integral.
	FillMilliGallonsPerSecond = 750

	// The gauge hard-stops once the player has pumped twice the target.
	FillOverfillLimitMultiple = 2

	// Perfect Pit Stop: four tires, and every tap that misses an un-changed
	// tire adds a one second penalty.
	PitStopTireCount     = 4
	PitStopMisTapPenalty = 1_000
)

// ReactionScore is the canonical Reaction Lights score: whole milliseconds
// elapsed between the green light and the player's touch-down.
//
// Both timestamps must come from the same monotonic clock.
func ReactionScore(greenToTapMS int) int { return greenToTapMS }

// FillScore is the canonical Fill It Up score: how many cents the player
// ended up away from the target, in either direction.
func FillScore(dispensedCents, goalCents int) int {
	diff := dispensedCents - goalCents
	if diff < 0 {
		return -diff
	}
	return diff
}

// PitStopScore is the canonical Perfect Pit Stop score: elapsed milliseconds
// from the start tap to the fourth tire, plus a one second penalty per mis-tap.
func PitStopScore(elapsedMS, misTaps int) int {
	return elapsedMS + misTaps*PitStopMisTapPenalty
}

// FormatScore renders a canonical score the way it appears on a leaderboard.
// The clients render it identically.
func (id ID) FormatScore(score int) string {
	switch id {
	case Reaction:
		return fmt.Sprintf("%d ms", score)
	case Fill:
		return fmt.Sprintf("$%d.%02d off", score/100, score%100)
	case PitStop:
		return fmt.Sprintf("%d.%03ds", score/1000, score%1000)
	}
	return fmt.Sprintf("%d", score)
}
