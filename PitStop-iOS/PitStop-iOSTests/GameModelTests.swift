//
//  GameModelTests.swift
//  PitStop-iOSTests
//
//  The input-protection and lifecycle guarantees: a round can never be scored
//  twice, a tire can never register twice, and a round whose timing was
//  interrupted is never turned into a score.
//

import Foundation
import SwiftUI
import Testing

@testable import PitStop_iOS

@Suite("Reaction Lights round", .serialized)
@MainActor
struct ReactionGameModelTests {

    @Test("A fresh round is idle")
    func startsIdle() {
        let model = ReactionGameModel()
        #expect(model.phase == .ready)
        #expect(model.score == nil)
        #expect(!model.isRoundActive)
    }

    @Test("Starting shows the red light")
    func startArmsTheRound() {
        let model = ReactionGameModel()
        model.start()
        #expect(model.phase == .waiting)
        #expect(model.isRoundActive)
    }

    @Test("Tapping on red is a false start, and does not score")
    func tappingEarlyIsAFalseStart() {
        let model = ReactionGameModel()
        model.start()
        model.tap()

        #expect(model.phase == .falseStart)
        #expect(model.score == nil)
    }

    @Test("A second Start cannot restart an armed round")
    func startIsGuarded() {
        let model = ReactionGameModel()
        model.start()
        model.start()
        model.start()

        // Still a single armed round, not three racing timers.
        #expect(model.phase == .waiting)
    }

    @Test("Taps outside a round do nothing")
    func tapsOutsideARoundAreIgnored() {
        let model = ReactionGameModel()

        model.tap()
        #expect(model.phase == .ready)

        model.start()
        model.tap()                 // false start
        #expect(model.phase == .falseStart)

        model.tap()                 // and again
        #expect(model.phase == .falseStart, "a tap after the round ended changed the phase")
    }

    @Test("Backgrounding mid-round cancels it rather than scoring it")
    func backgroundingCancelsTheRound() {
        let model = ReactionGameModel()
        model.start()

        model.handleScenePhaseChange(to: .background)

        #expect(model.phase == .cancelled)
        #expect(model.score == nil)
    }

    @Test("Backgrounding outside a round changes nothing")
    func backgroundingOutsideARoundIsHarmless() {
        let model = ReactionGameModel()
        model.handleScenePhaseChange(to: .background)
        #expect(model.phase == .ready)
    }

    @Test("Reset returns the round to idle")
    func resetClearsTheRound() {
        let model = ReactionGameModel()
        model.start()
        model.tap()
        model.reset()

        #expect(model.phase == .ready)
        #expect(model.score == nil)
    }

    @Test("The light goes green on its own, and a tap then scores")
    func greenLightProducesAScore() async throws {
        let model = ReactionGameModel()
        model.start()

        // The wait is 2 to 5 seconds by specification; give it the full range
        // plus a margin rather than guessing.
        try await waitUntil(timeout: .seconds(7)) { model.phase == .go }

        // Wait past the plausibility floor before tapping. A tap within 50ms of
        // the green light is not a reaction — it is a finger that was already
        // on its way down — and the model refuses to score it.
        try await Task.sleep(for: .milliseconds(120))
        model.tap()

        let score = try #require(model.score, "tapping on green should produce a score")
        #expect(ScoreRules.inBounds(score, for: .reaction))
        #expect(score >= 120, "the score should reflect the time actually waited")
    }

    @Test("A tap too soon after green is refused as a false start")
    func impossiblyFastTapsAreRefused() async throws {
        let model = ReactionGameModel()
        model.start()

        try await waitUntil(timeout: .seconds(7)) { model.phase == .go }

        // Tap immediately: well under the 50ms floor.
        model.tap()

        #expect(model.phase == .falseStart)
        #expect(model.score == nil)
    }
}

@Suite("Fill It Up round", .serialized)
@MainActor
struct FillGameModelTests {

    @Test("Preparing a round picks a legal target and price")
    func prepareRoundIsLegal() {
        let model = FillGameModel()
        model.prepareRound()

        #expect(model.phase == .ready)
        #expect(model.goalCents % ScoreRules.fillGoalStepCents == 0)
        #expect(ScoreRules.fillGoalRange.contains(model.goalCents))
        #expect(ScoreRules.fillPriceRange.contains(model.pricePerGallonCents))
        #expect(model.dispensedCents == 0)
    }

    @Test("Pumping only starts from a prepared round")
    func pumpingIsGuarded() {
        let model = FillGameModel()
        model.prepareRound()

        model.beginPumping()
        #expect(model.phase == .pumping)

        // A second press while already pumping must not restart the clock.
        model.beginPumping()
        #expect(model.phase == .pumping)
    }

    @Test("Releasing without pumping does nothing")
    func releaseWithoutPumpIsIgnored() {
        let model = FillGameModel()
        model.prepareRound()

        model.endPumping()
        #expect(model.phase == .ready)
        #expect(model.score == nil)
    }

    @Test("Releasing ends the round with a score in range")
    func releaseScoresTheRound() {
        let model = FillGameModel()
        model.prepareRound()
        model.beginPumping()
        model.endPumping()

        let score = try? #require(model.score)
        #expect(score != nil)
        #expect(ScoreRules.inBounds(model.score ?? -1, for: .fill))

        // Releasing again must not score twice.
        let first = model.score
        model.endPumping()
        #expect(model.score == first)
    }

    @Test("Backgrounding mid-pump cancels the round")
    func backgroundingCancelsTheRound() {
        let model = FillGameModel()
        model.prepareRound()
        model.beginPumping()

        model.handleScenePhaseChange(to: .inactive)

        #expect(model.phase == .cancelled)
        #expect(model.score == nil)
    }

    @Test("The gauge never reads past full")
    func gaugeIsClamped() {
        let model = FillGameModel()
        model.prepareRound()

        #expect(model.gaugeFraction >= 0)
        #expect(model.gaugeFraction <= 1)
        #expect(model.goalFraction > 0 && model.goalFraction < 1,
                "the target must sit somewhere a player can aim at")
    }
}

@Suite("Perfect Pit Stop round", .serialized)
@MainActor
struct PitStopGameModelTests {

    @Test("A fresh round has four tires to change")
    func startsWithFourTires() {
        let model = PitStopGameModel()
        #expect(model.phase == .ready)
        #expect(model.remainingCount == ScoreRules.pitStopTireCount)
        #expect(model.misTaps == 0)
    }

    @Test("Taps before Start are ignored")
    func tapsBeforeStartAreIgnored() {
        let model = PitStopGameModel()

        model.tap(.frontLeft)
        model.tapMissed()

        #expect(model.remainingCount == ScoreRules.pitStopTireCount)
        #expect(model.misTaps == 0)
    }

    @Test("A tire registers exactly once, however fast it is tapped")
    func tiresRegisterOnce() {
        let model = PitStopGameModel()
        model.start()

        model.tap(.frontLeft)
        #expect(model.isChanged(.frontLeft))
        #expect(model.remainingCount == 3)

        // Drumming on the same tire costs penalties, it does not finish faster.
        model.tap(.frontLeft)
        model.tap(.frontLeft)

        #expect(model.remainingCount == 3)
        #expect(model.misTaps == 2, "re-tapping a changed tire should count against the player")
    }

    @Test("Missing the tires costs a second each")
    func misTapsArePenalized() {
        let model = PitStopGameModel()
        model.start()

        model.tapMissed()
        model.tapMissed()

        #expect(model.misTaps == 2)
    }

    @Test("The fourth tire ends the round")
    func fourTiresFinishesTheRound() {
        let model = PitStopGameModel()
        model.start()

        for tire in Tire.allCases {
            model.tap(tire)
        }

        #expect(model.remainingCount == 0)

        // The round is over: either scored, or refused as implausibly fast.
        // Four synthetic taps land in well under the 300ms floor, so in a test
        // this is the cancelled path. Either way it must not still be running.
        #expect(model.phase != .running)
    }

    @Test("Input is dead once the round ends")
    func inputStopsAfterTheRound() {
        let model = PitStopGameModel()
        model.start()
        for tire in Tire.allCases { model.tap(tire) }

        let misTapsAtEnd = model.misTaps
        model.tap(.frontLeft)
        model.tapMissed()

        #expect(model.misTaps == misTapsAtEnd, "taps after the round ended were still counted")
    }

    @Test("Backgrounding mid-round cancels it")
    func backgroundingCancelsTheRound() {
        let model = PitStopGameModel()
        model.start()
        model.tap(.frontLeft)

        model.handleScenePhaseChange(to: .background)

        #expect(model.phase == .cancelled)
        #expect(model.score == nil)
    }

    @Test("Reset clears the tires and penalties")
    func resetClearsTheRound() {
        let model = PitStopGameModel()
        model.start()
        model.tap(.frontLeft)
        model.tapMissed()
        model.reset()

        #expect(model.phase == .ready)
        #expect(model.remainingCount == ScoreRules.pitStopTireCount)
        #expect(model.misTaps == 0)
    }
}

// MARK: - Helpers

/// Polls until `condition` holds, or fails the test at `timeout`. Used instead
/// of a fixed sleep so a slow machine does not produce a flaky failure.
@MainActor
func waitUntil(
    timeout: Duration,
    pollEvery interval: Duration = .milliseconds(20),
    _ condition: () -> Bool
) async throws {
    let deadline = ContinuousClock().now + timeout

    while ContinuousClock().now < deadline {
        if condition() { return }
        try await Task.sleep(for: interval)
    }
    Issue.record("Timed out after \(timeout) waiting for a condition")
}
