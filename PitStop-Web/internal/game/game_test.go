package game

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strconv"
	"testing"
)

// fixturePath locates the cross-platform fixture table shared with the iOS and
// Android test suites. All three read this same file.
func fixturePath(t *testing.T) string {
	t.Helper()
	p := filepath.Join("..", "..", "..", "spec", "score-fixtures.json")
	if _, err := os.Stat(p); err != nil {
		t.Fatalf("shared fixture table missing at %s: %v", p, err)
	}
	return p
}

type fixtures struct {
	Reaction []struct {
		Name         string `json:"name"`
		GreenToTapMS int    `json:"greenToTapMs"`
		Expected     int    `json:"expected"`
	} `json:"reaction"`
	Fill []struct {
		Name           string `json:"name"`
		DispensedCents int    `json:"dispensedCents"`
		GoalCents      int    `json:"goalCents"`
		Expected       int    `json:"expected"`
	} `json:"fill"`
	PitStop []struct {
		Name      string `json:"name"`
		ElapsedMS int    `json:"elapsedMs"`
		MisTaps   int    `json:"misTaps"`
		Expected  int    `json:"expected"`
	} `json:"pitstop"`
	Bounds []struct {
		Game     ID   `json:"game"`
		Score    int  `json:"score"`
		InBounds bool `json:"inBounds"`
	} `json:"bounds"`
	Format []struct {
		Game     ID     `json:"game"`
		Score    int    `json:"score"`
		Expected string `json:"expected"`
	} `json:"format"`
}

func loadFixtures(t *testing.T) fixtures {
	t.Helper()
	raw, err := os.ReadFile(fixturePath(t))
	if err != nil {
		t.Fatalf("reading fixtures: %v", err)
	}
	var f fixtures
	if err := json.Unmarshal(raw, &f); err != nil {
		t.Fatalf("parsing fixtures: %v", err)
	}
	return f
}

func TestReactionScore(t *testing.T) {
	for _, tc := range loadFixtures(t).Reaction {
		t.Run(tc.Name, func(t *testing.T) {
			if got := ReactionScore(tc.GreenToTapMS); got != tc.Expected {
				t.Errorf("ReactionScore(%d) = %d, want %d", tc.GreenToTapMS, got, tc.Expected)
			}
		})
	}
}

func TestFillScore(t *testing.T) {
	for _, tc := range loadFixtures(t).Fill {
		t.Run(tc.Name, func(t *testing.T) {
			got := FillScore(tc.DispensedCents, tc.GoalCents)
			if got != tc.Expected {
				t.Errorf("FillScore(%d, %d) = %d, want %d",
					tc.DispensedCents, tc.GoalCents, got, tc.Expected)
			}
			if got < 0 {
				t.Errorf("FillScore returned a negative score %d; it must be an absolute difference", got)
			}
		})
	}
}

func TestPitStopScore(t *testing.T) {
	for _, tc := range loadFixtures(t).PitStop {
		t.Run(tc.Name, func(t *testing.T) {
			got := PitStopScore(tc.ElapsedMS, tc.MisTaps)
			if got != tc.Expected {
				t.Errorf("PitStopScore(%d, %d) = %d, want %d",
					tc.ElapsedMS, tc.MisTaps, got, tc.Expected)
			}
		})
	}
}

func TestInBounds(t *testing.T) {
	for _, tc := range loadFixtures(t).Bounds {
		t.Run(string(tc.Game)+"/"+strconv.Itoa(tc.Score), func(t *testing.T) {
			if got := tc.Game.InBounds(tc.Score); got != tc.InBounds {
				t.Errorf("%s.InBounds(%d) = %v, want %v", tc.Game, tc.Score, got, tc.InBounds)
			}
		})
	}
}

func TestFormatScore(t *testing.T) {
	for _, tc := range loadFixtures(t).Format {
		t.Run(string(tc.Game)+"/"+strconv.Itoa(tc.Score), func(t *testing.T) {
			if got := tc.Game.FormatScore(tc.Score); got != tc.Expected {
				t.Errorf("%s.FormatScore(%d) = %q, want %q", tc.Game, tc.Score, got, tc.Expected)
			}
		})
	}
}

func TestIDValid(t *testing.T) {
	for _, id := range All() {
		if !id.Valid() {
			t.Errorf("All() returned %q but Valid() rejects it", id)
		}
		if id.Title() == "" || id.Tagline() == "" {
			t.Errorf("%q is missing a title or tagline", id)
		}
	}
	for _, bad := range []ID{"", "REACTION", "reaction ", "tires", "sql injection"} {
		if ID(bad).Valid() {
			t.Errorf("Valid() accepted bogus game id %q", bad)
		}
	}
}

func TestBoundsAreOrdered(t *testing.T) {
	for _, id := range All() {
		min, max := id.Bounds()
		if min >= max {
			t.Errorf("%q has min %d >= max %d", id, min, max)
		}
	}
}
