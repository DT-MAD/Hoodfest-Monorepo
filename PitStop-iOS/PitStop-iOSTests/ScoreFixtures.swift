//
//  ScoreFixtures.swift
//  PitStop-iOSTests
//
//  Loads spec/score-fixtures.json — the same file the Go and Kotlin test
//  suites read — so the three implementations of the scoring rules cannot
//  drift apart silently.
//

import Foundation
import Testing

@testable import PitStop_iOS

struct ScoreFixtures: Decodable {

    struct Reaction: Decodable {
        let name: String
        let greenToTapMs: Int
        let expected: Int
    }

    struct Fill: Decodable {
        let name: String
        let dispensedCents: Int
        let goalCents: Int
        let expected: Int
    }

    struct PitStop: Decodable {
        let name: String
        let elapsedMs: Int
        let misTaps: Int
        let expected: Int
    }

    struct Bound: Decodable {
        let game: GameID
        let score: Int
        let inBounds: Bool
    }

    struct Format: Decodable {
        let game: GameID
        let score: Int
        let expected: String
    }

    let reaction: [Reaction]
    let fill: [Fill]
    let pitstop: [PitStop]
    let bounds: [Bound]
    let format: [Format]

    /// The fixture file lives at the repository root, three directories above
    /// this source file. Locating it by #filePath keeps it out of the test
    /// bundle's resources, so there is one copy on disk and no chance of a
    /// stale duplicate.
    static var fileURL: URL {
        URL(fileURLWithPath: #filePath)        // .../PitStop-iOSTests/ScoreFixtures.swift
            .deletingLastPathComponent()        // .../PitStop-iOSTests
            .deletingLastPathComponent()        // .../PitStop-iOS
            .deletingLastPathComponent()        // repository root
            .appendingPathComponent("spec/score-fixtures.json")
    }

    static func load() throws -> ScoreFixtures {
        let url = fileURL
        try #require(
            FileManager.default.fileExists(atPath: url.path),
            "The shared fixture table is missing at \(url.path)"
        )
        return try JSONDecoder().decode(ScoreFixtures.self, from: Data(contentsOf: url))
    }
}
