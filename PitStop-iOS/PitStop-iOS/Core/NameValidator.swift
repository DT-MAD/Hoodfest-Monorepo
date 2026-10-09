//
//  NameValidator.swift
//  PitStop-iOS
//
//  Validates and canonicalizes the names that go on a leaderboard. Mirrored
//  from PitStop-Web/internal/name/name.go, so a name accepted here is never
//  rejected by the server.
//

import Foundation

enum NameValidator {

    static let minLength = 1

    /// Sixteen characters fits the leaderboard column on the booth display.
    static let maxLength = 16

    /// The non-alphanumeric characters a name may contain.
    static let allowedPunctuation: Set<Character> = ["-", "_", "'", " "]

    enum Failure: Error, Equatable {
        case empty
        case tooLong
        case badCharacter
        case unavailable

        /// Shown to the player, directly under the text field.
        var message: String {
            switch self {
            case .empty: "Enter a name"
            case .tooLong: "Name must be \(NameValidator.maxLength) characters or fewer"
            case .badCharacter: "Use only letters, numbers, spaces, - _ or '"
            case .unavailable: "Pick a different name"
            }
        }
    }

    /// Trims, collapses internal whitespace, and uppercases for display.
    /// Does not validate.
    static func canonical(_ raw: String) -> String {
        raw.split(whereSeparator: \.isWhitespace)
            .joined(separator: " ")
            .uppercased()
    }

    /// Canonicalizes a name and reports whether it may be displayed.
    ///
    /// On failure the canonical form is still returned alongside the reason, so
    /// the UI can show the player what was made of their input.
    static func validate(_ raw: String) -> Result<String, Failure> {
        let name = canonical(raw)

        guard name.count >= minLength else { return .failure(.empty) }
        guard name.count <= maxLength else { return .failure(.tooLong) }

        // Deliberately ASCII-only. The profanity normalizer strips other
        // scripts, so accepting them would let a full-width or Cyrillic
        // lookalike spelling walk straight past the blocklist.
        for character in name {
            guard character.isASCIIAlphanumeric || allowedPunctuation.contains(character) else {
                return .failure(.badCharacter)
            }
        }

        // A name made entirely of punctuation passes the loop above but is not
        // a name.
        guard name.contains(where: \.isASCIIAlphanumeric) else { return .failure(.empty) }

        guard Profanity.isClean(name) else { return .failure(.unavailable) }

        return .success(name)
    }

    /// Convenience for live feedback as the player types: the reason the name
    /// is not yet acceptable, or nil if it is.
    static func failure(for raw: String) -> Failure? {
        switch validate(raw) {
        case .success: nil
        case .failure(let failure): failure
        }
    }
}

private extension Character {
    var isASCIIAlphanumeric: Bool {
        ("A"..."Z").contains(self) || ("a"..."z").contains(self) || ("0"..."9").contains(self)
    }
}
