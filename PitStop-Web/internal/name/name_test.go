package name

import (
	"errors"
	"strings"
	"testing"
)

func TestCanonical(t *testing.T) {
	cases := map[string]string{
		"  ace  ":        "ACE",
		"dale   jr":      "DALE JR",
		"Speedy":         "SPEEDY",
		"\tturbo\n":      "TURBO",
		"pit   crew   3": "PIT CREW 3",
		"":               "",
		"     ":          "",
	}
	for in, want := range cases {
		if got := Canonical(in); got != want {
			t.Errorf("Canonical(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestValidateAccepts(t *testing.T) {
	good := []string{
		"ACE", "ace", "Rio", "TURBO", "DALE JR", "O'NEIL", "JEAN-LUC",
		"T_REX", "007", "PIT CREW 3", "X", "SIXTEEN CHARSX",
	}
	for _, in := range good {
		got, err := Validate(in)
		if err != nil {
			t.Errorf("Validate(%q) = %v, want accepted", in, err)
		}
		if got != Canonical(in) {
			t.Errorf("Validate(%q) returned %q, want canonical %q", in, got, Canonical(in))
		}
	}
}

func TestValidateRejects(t *testing.T) {
	cases := []struct {
		in   string
		want error
	}{
		{"", ErrEmpty},
		{"   ", ErrEmpty},
		{"---", ErrEmpty},
		{"'", ErrEmpty},
		{"THIS NAME IS WAY TOO LONG", ErrTooLong},
		{"ACE<script>", ErrBadChar},
		{"ACE;DROP", ErrBadChar},
		{"CAFÉ", ErrBadChar},
		{"ＦＵＣＫ", ErrBadChar},
		{"ЖУК", ErrBadChar},
		{"ACE🏁", ErrBadChar},
		{"SHIT", ErrUnavailable},
		{"A55", ErrUnavailable},
		{"DUMBASS", ErrUnavailable},
	}
	for _, tc := range cases {
		_, err := Validate(tc.in)
		if !errors.Is(err, tc.want) {
			t.Errorf("Validate(%q) = %v, want %v", tc.in, err, tc.want)
		}
	}
}

func TestMaxLenBoundary(t *testing.T) {
	exactly := strings.Repeat("A", MaxLen)
	if _, err := Validate(exactly); err != nil {
		t.Errorf("Validate(%d chars) = %v, want accepted", MaxLen, err)
	}
	oneOver := strings.Repeat("A", MaxLen+1)
	if _, err := Validate(oneOver); !errors.Is(err, ErrTooLong) {
		t.Errorf("Validate(%d chars) = %v, want ErrTooLong", MaxLen+1, err)
	}
}

// TestRejectedNamesStillReturnCanonicalForm covers the UI affordance of showing
// the player what the server made of their input alongside the refusal.
func TestRejectedNamesStillReturnCanonicalForm(t *testing.T) {
	got, err := Validate("  shit  ")
	if err == nil {
		t.Fatal("expected the name to be refused")
	}
	if got != "SHIT" {
		t.Errorf("got canonical %q, want %q", got, "SHIT")
	}
}
