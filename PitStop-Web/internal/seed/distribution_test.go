package seed

import (
	"testing"

	"edu.dixietech.pitstop/internal/game"
)

// TestPrintDistribution is a readable summary of the starter set, so the shape
// of the ladder can be checked at a glance rather than inferred from numbers in
// the source. Run with: go test ./internal/seed/ -run Distribution -v
func TestPrintDistribution(t *testing.T) {
	ordinary := map[game.ID]int{
		game.Reaction: 380,
		game.Fill:     60,
		game.PitStop:  4_000,
	}

	for _, id := range game.All() {
		var beaten int
		var ladder []string
		for _, row := range Rows() {
			if row.Game != id {
				continue
			}
			ladder = append(ladder, row.Name+" "+id.FormatScore(row.Score))
			if ordinary[id] < row.Score {
				beaten++
			}
		}
		t.Logf("%s: an ordinary %s beats %d of %d",
			id.Title(), id.FormatScore(ordinary[id]), beaten, len(ladder))
		for i, line := range ladder {
			t.Logf("    %2d. %s", i+1, line)
		}
	}
}
