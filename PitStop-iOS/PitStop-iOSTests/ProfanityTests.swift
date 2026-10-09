//
//  ProfanityTests.swift
//  PitStop-iOSTests
//

import Foundation
import Testing

@testable import PitStop_iOS

@Suite("Profanity filter")
struct ProfanityTests {

    @Test("Innocent names are never rejected",
          arguments: ["MIKE", "KIKI", "KARL", "JACK", "NIKKI", "MCKENNA", "BUCK",
                      "ACE", "RIO", "TURBO", "SPEEDY", "LIGHTNING", "DALE JR",
                      "CASSIDY", "GLASS", "BASS", "CLASSIC", "GRASS", "RACCOON",
                      "ESSEX", "SUSSEX", "ASSIST", "MASSIVE", "PASS", "BRASS",
                      "ANA", "SCOTT", "MATT", "HANNAH", "AARON", "LEE", "BOBBY",
                      "O'NEIL", "JEAN-LUC", "T_REX", "X", "007", "PIT CREW 3"])
    func allowsCleanNames(name: String) {
        #expect(Profanity.match(name) == nil,
                "\(name) was rejected on \(Profanity.match(name) ?? "")")
    }

    @Test("Blocked names are caught, including obvious evasions",
          arguments: ["SHIT", "shit", "Sh1t", "SH!T", "s.h.i.t", "s h i t", "shhhiiit",
                      "FUCK", "f u c k", "FUUUUCK", "FFFUUUCCCKKK", "fuck you",
                      "ASS", "A55", "@55", "BUTTHOLE", "DICKHEAD", "B1TCH", "BITCH",
                      "NAZI", "N4ZI", "HITLER", "PENIS", "P3N15", "BOOBS", "WHORE",
                      "CRAPPY", "TURD", "DUMBASS", "JACKASS"])
    func blocksProfanity(name: String) {
        #expect(Profanity.match(name) != nil, "\(name) was let through")
    }

    @Test("Normalization folds leetspeak and strips punctuation",
          arguments: [
            ("MIKE", "mike"),
            ("M!K3", "mike"),
            ("s.h.i.t", "shit"),
            ("a 5 5", "ass"),
            ("O'Neil", "oneil"),
            ("", ""),
          ])
    func normalizes(raw: String, expected: String) {
        #expect(Profanity.normalize(raw) == expected)
    }

    @Test("Repeated characters collapse",
          arguments: [
            ("fuuuck", "fuck"),
            ("ffffuuuucccckkkk", "fuck"),
            ("mike", "mike"),
            ("aaaa", "a"),
            ("", ""),
          ])
    func collapses(raw: String, expected: String) {
        #expect(Profanity.collapseRepeats(raw) == expected)
    }

    /// Regression test. An earlier version collapsed repeated characters before
    /// matching, which turned the blocked term "kkk" into "k" and then rejected
    /// every name containing the letter K — "MIKE" included.
    @Test("Short blocked terms do not match everything",
          arguments: ["MIKE", "KATIE", "KYLE", "NIKKI", "KKARL"])
    func shortTermsDoNotOverMatch(name: String) {
        #expect(Profanity.match(name) == nil)
    }

    @Test("Empty and symbol-only input is clean; the validator rejects it for being empty",
          arguments: ["", "   ", "---"])
    func emptyIsClean(name: String) {
        #expect(Profanity.match(name) == nil)
    }
}
