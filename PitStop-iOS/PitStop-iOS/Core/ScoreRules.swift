//
//  ScoreRules.swift
//  PitStop-iOS
//
//  The authoritative Pit Stop scoring specification, mirrored from
//  PitStop-Web/internal/game/game.go and PitStop-Android's ScoreRules.kt.
//
//  Every constant here is asserted against spec/score-fixtures.json, the same
//  file the Go and Kotlin suites read. If you change a number, change it in all
//  three places or the parity tests will fail.
//
//  The governing rule: for all three games, a LOWER score is better.
//

import Foundation

enum ScoreRules {

    // MARK: - Plausibility bounds
    //
    // A score outside these is not a real round. The server rejects them, so
    // the app must not produce them either.

    static let reactionBounds = 50...10_000          // milliseconds
    static let fillBounds = 0...100_000              // cents off target
    static let pitStopBounds = 300...120_000         // milliseconds

    static func bounds(for game: GameID) -> ClosedRange<Int> {
        switch game {
        case .reaction: reactionBounds
        case .fill: fillBounds
        case .pitstop: pitStopBounds
        }
    }

    static func inBounds(_ score: Int, for game: GameID) -> Bool {
        bounds(for: game).contains(score)
    }

    // MARK: - Reaction Lights

    /// The red light holds for a uniformly random delay in this range.
    static let reactionWaitRange: ClosedRange<Int> = 2_000...5_000

    /// A random wait, in milliseconds.
    static func randomReactionWaitMS() -> Int {
        Int.random(in: reactionWaitRange)
    }

    /// Canonical score: whole milliseconds between the green light and the tap.
    /// Both timestamps must come from the same monotonic clock.
    static func reactionScore(greenToTapMS: Int) -> Int {
        greenToTapMS
    }

    // MARK: - Fill It Up

    static let fillGoalRange: ClosedRange<Int> = 500...2_000   // cents
    static let fillGoalStepCents = 50
    static let fillPriceRange: ClosedRange<Int> = 200...500    // cents per gallon

    /// The pump dispenses at a fixed rate, accumulated from monotonic hold time
    /// so the result does not depend on frame rate.
    static let fillMilliGallonsPerSecond = 750

    /// The gauge hard-stops once the player has pumped this multiple of the target.
    static let fillOverfillLimitMultiple = 2

    /// A random dollar target, in cents, always a multiple of 50.
    static func randomFillGoalCents() -> Int {
        let steps = fillGoalRange.lowerBound / fillGoalStepCents
            ... fillGoalRange.upperBound / fillGoalStepCents
        return Int.random(in: steps) * fillGoalStepCents
    }

    /// A random gas price, in cents per gallon.
    static func randomFillPriceCents() -> Int {
        Int.random(in: fillPriceRange)
    }

    /// Gallons dispensed after holding the pump for `elapsedMS`.
    static func gallonsDispensed(elapsedMS: Int) -> Double {
        Double(elapsedMS) / 1000.0 * Double(fillMilliGallonsPerSecond) / 1000.0
    }

    /// The cost of `gallons` at `pricePerGallonCents`, rounded to the nearest cent.
    static func dispensedCents(gallons: Double, pricePerGallonCents: Int) -> Int {
        Int((gallons * Double(pricePerGallonCents)).rounded())
    }

    /// Canonical score: how many cents the player ended up from the target,
    /// in either direction.
    static func fillScore(dispensedCents: Int, goalCents: Int) -> Int {
        abs(dispensedCents - goalCents)
    }

    // MARK: - Perfect Pit Stop

    static let pitStopTireCount = 4

    /// Every tap that misses an un-changed tire costs a full second.
    static let pitStopMisTapPenaltyMS = 1_000

    /// Canonical score: elapsed milliseconds from the start tap to the fourth
    /// tire, plus a one second penalty per mis-tap.
    static func pitStopScore(elapsedMS: Int, misTaps: Int) -> Int {
        elapsedMS + misTaps * pitStopMisTapPenaltyMS
    }

    // MARK: - Display

    /// Renders a canonical score the way it appears on a leaderboard. The Go
    /// server and the Android app format these identically.
    static func format(_ score: Int, for game: GameID) -> String {
        switch game {
        case .reaction:
            return "\(score) ms"
        case .fill:
            let dollars = score / 100
            let cents = score % 100
            return String(format: "$%d.%02d off", dollars, cents)
        case .pitstop:
            let seconds = score / 1000
            let millis = score % 1000
            return String(format: "%d.%03ds", seconds, millis)
        }
    }
}
