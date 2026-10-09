package profanity

import "testing"

func TestCleanNamesPass(t *testing.T) {
	// Real names, nicknames and racing handles that must never be rejected.
	clean := []string{
		"MIKE", "KIKI", "KARL", "JACK", "NIKKI", "MCKENNA", "BUCK",
		"ACE", "RIO", "TURBO", "SPEEDY", "LIGHTNING", "DALE JR",
		"CASSIDY", "GLASS", "BASS", "CLASSIC", "GRASS", "RACCOON",
		"ESSEX", "SUSSEX", "ASSIST", "MASSIVE", "PASS", "BRASS",
		"ANA", "SCOTT", "MATT", "HANNAH", "AARON", "LEE", "BOBBY",
		"O'NEIL", "JEAN-LUC", "T_REX", "X", "007", "PIT CREW 3",
	}
	for _, name := range clean {
		if term := Match(name); term != "" {
			t.Errorf("Match(%q) rejected an innocent name on blocked term %q", name, term)
		}
	}
}

func TestProfaneNamesBlocked(t *testing.T) {
	dirty := []string{
		"SHIT", "shit", "Sh1t", "SH!T", "s.h.i.t", "s h i t", "shhhiiit",
		"FUCK", "f u c k", "FUUUUCK", "FFFUUUCCCKKK", "fuck you",
		"ASS", "A55", "@55", "BUTTHOLE", "DICKHEAD", "B1TCH", "BITCH",
		"NAZI", "N4ZI", "HITLER", "PENIS", "P3N15", "BOOBS", "WHORE",
		"CRAPPY", "TURD", "DUMBASS", "JACKASS",
	}
	for _, name := range dirty {
		if Match(name) == "" {
			t.Errorf("Match(%q) let a blocked name through", name)
		}
	}
}

func TestNormalize(t *testing.T) {
	cases := map[string]string{
		"MIKE":       "mike",
		"M!K3":       "mike",
		"s.h.i.t":    "shit",
		"a 5 5":      "ass",
		"O'Neil":     "oneil",
		"Pit Crew 3": "pitcrewe", // the trailing 3 folds to 'e' via leetspeak
		"":           "",
		"!!!":        "iii", // '!' folds to 'i' so that "SH!T" is caught
	}
	for in, want := range cases {
		if got := Normalize(in); got != want {
			t.Errorf("Normalize(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestCollapseRepeats(t *testing.T) {
	cases := map[string]string{
		"fuuuck":           "fuck",
		"ffffuuuucccckkkk": "fuck",
		"mike":             "mike",
		"aaaa":             "a",
		"":                 "",
	}
	for in, want := range cases {
		if got := collapseRepeats(in); got != want {
			t.Errorf("collapseRepeats(%q) = %q, want %q", in, got, want)
		}
	}
}

// TestShortTermsDoNotMatchEverything is the regression test for a filter that
// collapsed "kkk" down to "k" and then rejected every name containing a K.
func TestShortTermsDoNotMatchEverything(t *testing.T) {
	for _, name := range []string{"MIKE", "KATIE", "KYLE", "NIKKI", "KKARL"} {
		if term := Match(name); term != "" {
			t.Errorf("Match(%q) = %q; a short blocked term is matching far too broadly", name, term)
		}
	}
}

func TestEmptyAndSymbolOnlyNamesAreClean(t *testing.T) {
	// These are rejected by the name validator for being empty, not by the
	// profanity filter. The filter's job is only to spot blocked words.
	for _, name := range []string{"", "   ", "!!!", "---"} {
		if term := Match(name); term != "" {
			t.Errorf("Match(%q) = %q, want clean", name, term)
		}
	}
}
