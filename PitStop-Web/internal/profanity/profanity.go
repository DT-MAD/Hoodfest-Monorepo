// Package profanity screens player names before they reach a leaderboard that
// is displayed on a screen in public, at a school event, in front of families.
//
// The approach is deliberately simple and deliberately strict: normalize away
// the obvious evasions (leetspeak, padding, repeated letters), then look for
// blocked terms as substrings. It will produce false positives on innocent
// names that happen to contain a blocked substring. At a car-show booth that
// trade-off is the right way round — a visitor who is asked to pick a
// different name loses three seconds; the alternative goes on the big screen.
//
// This logic is mirrored in Profanity.swift and Profanity.kt.
package profanity

import "strings"

// blocked holds the terms rejected anywhere inside a normalized name. Keep this
// list lowercase, alphanumeric only, and sorted — it is compared against the
// output of Normalize, which strips everything else.
//
// Entries are kept short and root-like so that suffixes and compounds are
// caught by the substring check without needing their own lines.
var blocked = []string{
	"anal", "anus", "arse", "ass", "bastard", "bitch", "blowjob", "bollock",
	"boner", "boob", "bugger", "bullshit", "butthole", "clit", "cock", "coon",
	"crap", "cum", "cunt", "dick", "dildo", "dyke", "fag", "fuck", "goddamn",
	"handjob", "hitler", "homo", "jerkoff", "jizz", "kike", "kkk", "nazi",
	"nigg", "nutsack", "penis", "piss", "poon", "porn", "prick", "pube",
	"pussy", "queer", "rape", "rapist", "retard", "rimjob", "scrotum", "semen",
	"sex", "shit", "slut", "spic", "testicle", "tits", "titties", "turd",
	"twat", "vagina", "wank", "whore", "wetback",
}

// allowed lists innocent words that the substring check would otherwise catch.
// Checked before the blocklist, against the normalized whole name.
var allowed = map[string]bool{
	"assist":  true,
	"bass":    true,
	"brass":   true,
	"cascade": true,
	"raccoon": true,
	"essex":   true,
	"cassidy": true,
	"classic": true,
	"glass":   true,
	"grass":   true,
	"massive": true,
	"pass":    true,
	"sussex":  true,
}

// leet maps the characters people substitute for letters when they are trying
// to sneak something past a filter.
var leet = map[rune]rune{
	'4': 'a', '@': 'a', '^': 'a',
	'8': 'b',
	'(': 'c', '<': 'c', '{': 'c',
	'3': 'e',
	'6': 'g', '9': 'g',
	'1': 'i', '!': 'i', '|': 'i',
	'0': 'o',
	'5': 's', '$': 's',
	'7': 't', '+': 't',
	'2': 'z',
}

// Normalize reduces a name to the form the blocklist is matched against:
// lowercased, with leetspeak folded back to letters and every non-alphanumeric
// character removed. "F.U.C.K", "f u c k", "sh1t" and "a55" all normalize to
// something the blocklist catches.
//
// Repeated characters are deliberately NOT collapsed here; see collapseRepeats.
func Normalize(name string) string {
	var b strings.Builder
	b.Grow(len(name))

	for _, r := range strings.ToLower(name) {
		if sub, ok := leet[r]; ok {
			r = sub
		}
		if (r >= 'a' && r <= 'z') || (r >= '0' && r <= '9') {
			b.WriteRune(r)
		}
	}
	return b.String()
}

// collapseRepeats squashes runs of the same character down to one, so that
// "ffffuuuuucccckkkk" reduces to "fuck". It is a second, more aggressive pass
// applied alongside the plain normalized form rather than instead of it.
func collapseRepeats(s string) string {
	var b strings.Builder
	b.Grow(len(s))

	var last rune = -1
	for _, r := range s {
		if r != last {
			b.WriteRune(r)
			last = r
		}
	}
	return b.String()
}

// minCollapsedLen guards the collapsed-form comparison. Collapsing shortens a
// term, and a term that collapses to one or two characters ("kkk" becomes "k")
// would match almost any name. Such terms are only matched in their plain form.
const minCollapsedLen = 4

// IsClean reports whether a name is acceptable for public display.
func IsClean(name string) bool {
	return Match(name) == ""
}

// Match returns the blocked term a name trips on, or "" if the name is clean.
// Useful in tests and logs; never show the matched term back to the player.
func Match(name string) string {
	plain := Normalize(name)
	if plain == "" || allowed[plain] {
		return ""
	}
	collapsed := collapseRepeats(plain)

	for _, term := range blocked {
		if strings.Contains(plain, term) {
			return term
		}
		// Catch padded evasions like "fuuuuck", but only for terms that stay
		// distinctive once collapsed.
		if ct := collapseRepeats(term); len(ct) >= minCollapsedLen && strings.Contains(collapsed, ct) {
			return term
		}
	}
	return ""
}
