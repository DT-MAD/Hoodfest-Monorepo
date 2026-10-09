// Package name validates and canonicalizes the player names that go on a
// leaderboard. It is mirrored in NameValidator.swift and NameValidator.kt so
// that a name accepted on a tablet is never rejected by the server.
package name

import (
	"errors"
	"strings"

	"edu.dixietech.pitstop/internal/profanity"
)

const (
	// MinLen and MaxLen bound the canonical name. Sixteen characters fits the
	// leaderboard column at the font size the booth display uses.
	MinLen = 1
	MaxLen = 16
)

// Validation failures. These are compared with errors.Is by the API layer,
// which turns them into a message the player sees.
var (
	ErrEmpty       = errors.New("enter a name")
	ErrTooLong     = errors.New("name must be 16 characters or fewer")
	ErrBadChar     = errors.New("use only letters, numbers, spaces, - _ or '")
	ErrUnavailable = errors.New("pick a different name")
)

// allowedPunct is the non-alphanumeric set a name may contain.
const allowedPunct = "-_' "

// Canonical trims a name, collapses internal whitespace, and uppercases it for
// display. It does not validate; call Validate for that.
func Canonical(raw string) string {
	return strings.ToUpper(strings.Join(strings.Fields(raw), " "))
}

// Validate canonicalizes a name and reports whether it may be displayed.
//
// It returns the canonical form on success. On failure the returned string is
// still the canonical form, so a UI can show the player what it made of their
// input alongside the reason it was refused.
func Validate(raw string) (string, error) {
	c := Canonical(raw)

	switch {
	case len([]rune(c)) < MinLen:
		return c, ErrEmpty
	case len([]rune(c)) > MaxLen:
		return c, ErrTooLong
	}

	// Deliberately ASCII-only. Unicode letters are stripped by the profanity
	// filter's normalizer, so accepting them here would let a full-width or
	// Cyrillic lookalike spelling walk straight past the blocklist.
	for _, r := range c {
		if isASCIIAlnum(r) || strings.ContainsRune(allowedPunct, r) {
			continue
		}
		return c, ErrBadChar
	}

	// A name made entirely of punctuation passes the loop above but is not a
	// name. Require at least one letter or digit.
	if !strings.ContainsFunc(c, isASCIIAlnum) {
		return c, ErrEmpty
	}

	if !profanity.IsClean(c) {
		return c, ErrUnavailable
	}

	return c, nil
}

// isASCIIAlnum reports whether r is an unaccented letter or a digit.
func isASCIIAlnum(r rune) bool {
	return (r >= 'A' && r <= 'Z') || (r >= 'a' && r <= 'z') || (r >= '0' && r <= '9')
}
