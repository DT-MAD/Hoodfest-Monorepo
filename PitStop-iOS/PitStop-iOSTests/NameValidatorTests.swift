//
//  NameValidatorTests.swift
//  PitStop-iOSTests
//

import Foundation
import Testing

@testable import PitStop_iOS

@Suite("Name validation")
struct NameValidatorTests {

    @Test("Canonicalization trims, collapses whitespace and uppercases",
          arguments: [
            ("  ace  ", "ACE"),
            ("dale   jr", "DALE JR"),
            ("Speedy", "SPEEDY"),
            ("\tturbo\n", "TURBO"),
            ("pit   crew   3", "PIT CREW 3"),
            ("", ""),
            ("     ", ""),
          ])
    func canonical(raw: String, expected: String) {
        #expect(NameValidator.canonical(raw) == expected)
    }

    @Test("Racing names are accepted",
          arguments: ["ACE", "ace", "Rio", "TURBO", "DALE JR", "O'NEIL",
                      "JEAN-LUC", "T_REX", "007", "PIT CREW 3", "X"])
    func accepts(name: String) {
        #expect(NameValidator.validate(name) == .success(NameValidator.canonical(name)))
    }

    @Test("Empty and punctuation-only names are refused",
          arguments: ["", "   ", "---", "'"])
    func refusesEmpty(name: String) {
        #expect(NameValidator.failure(for: name) == .empty)
    }

    @Test("Overlong names are refused")
    func refusesTooLong() {
        let exactly = String(repeating: "A", count: NameValidator.maxLength)
        #expect(NameValidator.failure(for: exactly) == nil, "the maximum length itself must be allowed")

        let oneOver = String(repeating: "A", count: NameValidator.maxLength + 1)
        #expect(NameValidator.failure(for: oneOver) == .tooLong)
    }

    @Test("Names outside the allowed character set are refused",
          arguments: ["ACE<script>", "ACE;DROP", "CAFÉ", "ＦＵＣＫ", "ЖУК", "ACE🏁"])
    func refusesBadCharacters(name: String) {
        #expect(NameValidator.failure(for: name) == .badCharacter,
                "\(name) must be refused: the profanity filter cannot read non-ASCII")
    }

    @Test("Profane names are refused", arguments: ["SHIT", "A55", "DUMBASS", "B1TCH"])
    func refusesProfanity(name: String) {
        #expect(NameValidator.failure(for: name) == .unavailable)
    }

    @Test("A refusal still reports the canonical form for the UI to show")
    func refusalCarriesCanonicalForm() {
        guard case .failure(let failure) = NameValidator.validate("  shit  ") else {
            Issue.record("the name should have been refused")
            return
        }
        #expect(failure == .unavailable)
        #expect(NameValidator.canonical("  shit  ") == "SHIT")
    }

    @Test("Every failure has a message fit to show a player")
    func failuresHaveMessages() {
        for failure in [NameValidator.Failure.empty, .tooLong, .badCharacter, .unavailable] {
            #expect(!failure.message.isEmpty)
        }
    }
}
