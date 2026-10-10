package seed

import (
	"testing"

	"edu.dixietech.pitstop/internal/game"
	"edu.dixietech.pitstop/internal/name"
)

// TestNamesSurviveValidation is the test that earns its keep: a starter name
// that the validator or the word filter rejects would be inserted straight into
// the database, bypassing the API, and then displayed on a screen in public.
//
// It has already caught one: "CHASSIS" normalizes to something containing
// "ass" and is refused.
func TestNamesSurviveValidation(t *testing.T) {
	for _, row := range Rows() {
		canonical, err := name.Validate(row.Name)
		if err != nil {
			t.Errorf("starter name %q would be refused: %v", row.Name, err)
		}
		if canonical != row.Name {
			t.Errorf("starter name %q is not in canonical form (want %q)", row.Name, canonical)
		}
	}
}

func TestScoresAreInBounds(t *testing.T) {
	for _, row := range Rows() {
		if !row.Game.InBounds(row.Score) {
			min, max := row.Game.Bounds()
			t.Errorf("%s score %d for %q is outside %d..%d",
				row.Game, row.Score, row.Name, min, max)
		}
	}
}

func TestEveryBoardIsFilled(t *testing.T) {
	counts := map[game.ID]int{}
	for _, row := range Rows() {
		counts[row.Game]++
	}

	for _, id := range game.All() {
		// Ten is what a board displays; anything less leaves a visible gap.
		if counts[id] != 10 {
			t.Errorf("%s has %d starter scores, want 10 to fill the board", id, counts[id])
		}
	}
}

func TestNamesAreUnique(t *testing.T) {
	seen := map[string]bool{}
	for _, row := range Rows() {
		if seen[row.Name] {
			t.Errorf("starter name %q is used twice", row.Name)
		}
		seen[row.Name] = true
	}
}

// TestScoresAreMostlyBeatable guards the point of the starter set: a visitor
// having an ordinary go should land on the board rather than off the bottom.
func TestScoresAreMostlyBeatable(t *testing.T) {
	// Roughly what an unremarkable attempt looks like on each game.
	ordinary := map[game.ID]int{
		game.Reaction: 380,   // ms; an average adult is nearer 250
		game.Fill:     60,    // 60 cents off the target
		game.PitStop:  4_000, // four seconds for four tires
	}

	for id, typical := range ordinary {
		var beaten int
		for _, row := range Rows() {
			if row.Game == id && typical < row.Score {
				beaten++
			}
		}
		// An ordinary attempt should get onto the board with room to spare.
		if beaten < 5 {
			t.Errorf("an ordinary %s score of %d only beats %d of the starter scores; "+
				"the set is too hard to be worth seeding", id, typical, beaten)
		}
	}
}

// TestTopScoreIsAspirational is the other half: a board everyone wins outright
// is no more interesting than one nobody gets onto.
func TestTopScoreIsAspirational(t *testing.T) {
	best := map[game.ID]int{}
	for _, row := range Rows() {
		if current, ok := best[row.Game]; !ok || row.Score < current {
			best[row.Game] = row.Score
		}
	}

	limits := map[game.ID]int{
		game.Reaction: 260,
		game.Fill:     10,
		game.PitStop:  2_400,
	}
	for id, limit := range limits {
		if best[id] > limit {
			t.Errorf("the best starter %s score is %d, which is too soft; "+
				"want %d or better so there is something to chase", id, best[id], limit)
		}
	}
}

func TestEntriesAreTaggedForReplacement(t *testing.T) {
	entries := Entries()
	if len(entries) != len(Rows()) {
		t.Fatalf("Entries() returned %d, want %d", len(entries), len(Rows()))
	}

	for _, e := range entries {
		if e.Detail[Marker] != true {
			t.Errorf("entry %q is not tagged %q, so seeding twice would duplicate it", e.Name, Marker)
		}
	}
}
