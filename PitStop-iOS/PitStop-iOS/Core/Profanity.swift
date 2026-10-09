//
//  Profanity.swift
//  PitStop-iOS
//
//  Screens player names before they reach a leaderboard displayed on a screen
//  in public, at a school event, in front of families.
//
//  Mirrored from PitStop-Web/internal/profanity/profanity.go. The approach is
//  deliberately simple and deliberately strict: normalize away the obvious
//  evasions, then look for blocked terms as substrings. It will produce false
//  positives on innocent names containing a blocked substring. At a booth that
//  trade-off is the right way round — a visitor asked to pick a different name
//  loses three seconds; the alternative goes on the big screen.
//

import Foundation

enum Profanity {

    /// Terms rejected anywhere inside a normalized name. Lowercase and
    /// alphanumeric only, matching what `normalize` produces. Kept short and
    /// root-like so suffixes and compounds are caught without their own lines.
    private static let blocked: [String] = [
        "anal", "anus", "arse", "ass", "bastard", "bitch", "blowjob", "bollock",
        "boner", "boob", "bugger", "bullshit", "butthole", "clit", "cock", "coon",
        "crap", "cum", "cunt", "dick", "dildo", "dyke", "fag", "fuck", "goddamn",
        "handjob", "hitler", "homo", "jerkoff", "jizz", "kike", "kkk", "nazi",
        "nigg", "nutsack", "penis", "piss", "poon", "porn", "prick", "pube",
        "pussy", "queer", "rape", "rapist", "retard", "rimjob", "scrotum", "semen",
        "sex", "shit", "slut", "spic", "testicle", "tits", "titties", "turd",
        "twat", "vagina", "wank", "whore", "wetback",
    ]

    /// Innocent words the substring check would otherwise catch. Compared
    /// against the whole normalized name.
    private static let allowed: Set<String> = [
        "assist", "bass", "brass", "cascade", "cassidy", "classic", "essex",
        "glass", "grass", "massive", "pass", "raccoon", "sussex",
    ]

    /// Characters people substitute for letters to sneak something past a filter.
    private static let leet: [Character: Character] = [
        "4": "a", "@": "a", "^": "a",
        "8": "b",
        "(": "c", "<": "c", "{": "c",
        "3": "e",
        "6": "g", "9": "g",
        "1": "i", "!": "i", "|": "i",
        "0": "o",
        "5": "s", "$": "s",
        "7": "t", "+": "t",
        "2": "z",
    ]

    /// A term that collapses shorter than this is only matched in its plain
    /// form. Collapsing shortens a term, and one that collapses to a character
    /// or two ("kkk" becomes "k") would match almost any name.
    private static let minCollapsedLength = 4

    /// Reduces a name to the form the blocklist is matched against: lowercased,
    /// leetspeak folded back to letters, everything else removed.
    ///
    /// Repeated characters are deliberately NOT collapsed here; see `collapseRepeats`.
    static func normalize(_ name: String) -> String {
        var out = ""
        out.reserveCapacity(name.count)

        for character in name.lowercased() {
            let folded = leet[character] ?? character
            if folded.isASCIILetterOrDigit {
                out.append(folded)
            }
        }
        return out
    }

    /// Squashes runs of the same character down to one, so "ffffuuuucccckkkk"
    /// reduces to "fuck". A second, more aggressive pass applied alongside the
    /// plain normalized form rather than instead of it.
    static func collapseRepeats(_ value: String) -> String {
        var out = ""
        out.reserveCapacity(value.count)

        var previous: Character?
        for character in value where character != previous {
            out.append(character)
            previous = character
        }
        return out
    }

    /// Whether a name is acceptable for public display.
    static func isClean(_ name: String) -> Bool {
        match(name) == nil
    }

    /// The blocked term a name trips on, or nil if it is clean. Useful in tests;
    /// never show the matched term back to the player.
    static func match(_ name: String) -> String? {
        let plain = normalize(name)
        guard !plain.isEmpty, !allowed.contains(plain) else { return nil }

        let collapsed = collapseRepeats(plain)

        for term in blocked {
            if plain.contains(term) { return term }

            // Catch padded evasions like "fuuuuck", but only for terms that
            // stay distinctive once collapsed.
            let collapsedTerm = collapseRepeats(term)
            if collapsedTerm.count >= minCollapsedLength, collapsed.contains(collapsedTerm) {
                return term
            }
        }
        return nil
    }
}

private extension Character {
    /// Deliberately ASCII-only: the name validator rejects everything else, so
    /// a full-width or Cyrillic lookalike cannot reach the blocklist unnoticed.
    var isASCIILetterOrDigit: Bool {
        ("a"..."z").contains(self) || ("0"..."9").contains(self)
    }
}
