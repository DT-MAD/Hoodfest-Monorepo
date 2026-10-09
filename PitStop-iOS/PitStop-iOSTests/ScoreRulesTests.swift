//
//  ScoreRulesTests.swift
//  PitStop-iOSTests
//
//  Asserts the scoring specification against the shared fixture table.
//

import Foundation
import Testing

@testable import PitStop_iOS

@Suite("Scoring rules")
struct ScoreRulesTests {

    let fixtures: ScoreFixtures

    init() throws {
        fixtures = try ScoreFixtures.load()
    }

    @Test("Reaction Lights scores match the shared fixtures")
    func reactionScores() {
        for fixture in fixtures.reaction {
            #expect(
                ScoreRules.reactionScore(greenToTapMS: fixture.greenToTapMs) == fixture.expected,
                "\(fixture.name)"
            )
        }
    }

    @Test("Fill It Up scores match the shared fixtures")
    func fillScores() {
        for fixture in fixtures.fill {
            let score = ScoreRules.fillScore(
                dispensedCents: fixture.dispensedCents,
                goalCents: fixture.goalCents
            )
            #expect(score == fixture.expected, "\(fixture.name)")
            #expect(score >= 0, "\(fixture.name): the score must be an absolute difference")
        }
    }

    @Test("Perfect Pit Stop scores match the shared fixtures")
    func pitStopScores() {
        for fixture in fixtures.pitstop {
            #expect(
                ScoreRules.pitStopScore(elapsedMS: fixture.elapsedMs, misTaps: fixture.misTaps)
                    == fixture.expected,
                "\(fixture.name)"
            )
        }
    }

    @Test("Plausibility bounds match the shared fixtures")
    func bounds() {
        for fixture in fixtures.bounds {
            #expect(
                ScoreRules.inBounds(fixture.score, for: fixture.game) == fixture.inBounds,
                "\(fixture.game.rawValue) \(fixture.score)"
            )
        }
    }

    @Test("Score formatting matches the shared fixtures")
    func formatting() {
        for fixture in fixtures.format {
            #expect(
                ScoreRules.format(fixture.score, for: fixture.game) == fixture.expected,
                "\(fixture.game.rawValue) \(fixture.score)"
            )
        }
    }

    // MARK: - Round generation

    @Test("Fill targets are always a whole number of 50 cent steps, within range")
    func fillGoalGeneration() {
        for _ in 0..<500 {
            let goal = ScoreRules.randomFillGoalCents()
            #expect(goal % ScoreRules.fillGoalStepCents == 0, "goal \(goal) is not a 50 cent step")
            #expect(ScoreRules.fillGoalRange.contains(goal), "goal \(goal) is out of range")
        }
    }

    @Test("Gas prices stay within range")
    func fillPriceGeneration() {
        for _ in 0..<500 {
            #expect(ScoreRules.fillPriceRange.contains(ScoreRules.randomFillPriceCents()))
        }
    }

    @Test("Reaction waits stay within the 2 to 5 second range")
    func reactionWaitGeneration() {
        for _ in 0..<500 {
            #expect(ScoreRules.reactionWaitRange.contains(ScoreRules.randomReactionWaitMS()))
        }
    }

    @Test("The pump dispenses at the specified rate")
    func pumpRate() {
        // One second of holding is 0.75 gallons, by specification.
        #expect(ScoreRules.gallonsDispensed(elapsedMS: 1000) == 0.75)
        #expect(ScoreRules.gallonsDispensed(elapsedMS: 0) == 0)
        #expect(ScoreRules.gallonsDispensed(elapsedMS: 2000) == 1.5)
    }

    @Test("Cost rounds to the nearest cent")
    func costRounding() {
        // 0.75 gallons at $2.25 is $1.6875, which rounds to $1.69.
        #expect(ScoreRules.dispensedCents(gallons: 0.75, pricePerGallonCents: 225) == 169)
        #expect(ScoreRules.dispensedCents(gallons: 0, pricePerGallonCents: 225) == 0)
    }

    @Test("A perfect pit stop scores its raw elapsed time")
    func pitStopWithoutPenalty() {
        #expect(ScoreRules.pitStopScore(elapsedMS: 2140, misTaps: 0) == 2140)
    }

    @Test("Each mis-tap costs exactly one second")
    func pitStopPenalty() {
        let clean = ScoreRules.pitStopScore(elapsedMS: 2000, misTaps: 0)
        let one = ScoreRules.pitStopScore(elapsedMS: 2000, misTaps: 1)
        #expect(one - clean == ScoreRules.pitStopMisTapPenaltyMS)
    }
}
