//
//  FillGameModel.swift
//  PitStop-iOS
//
//  Fill It Up.
//
//  The player holds the pump and lets go as close to a dollar target as they
//  can. The amount dispensed is derived from monotonic hold time, not from a
//  per-frame accumulation, so the result does not depend on frame rate or on
//  the display dropping a frame at the wrong moment.
//

import Foundation
import SwiftUI

@Observable
@MainActor
final class FillGameModel {

    enum Phase: Equatable {
        case ready
        case pumping
        case finished(score: Int)
        case cancelled
    }

    private(set) var phase: Phase = .ready

    /// The dollar target for this round, in cents.
    private(set) var goalCents: Int = 0

    /// The price of gas this round, in cents per gallon.
    private(set) var pricePerGallonCents: Int = 0

    /// What the pump reads right now, in cents.
    private(set) var dispensedCents: Int = 0

    private let clock = RoundClock()
    private var holdStart: RoundClock.Instant?
    private var tickTask: Task<Void, Never>?

    var score: Int? {
        if case .finished(let score) = phase { return score }
        return nil
    }

    var isRoundActive: Bool { phase == .pumping }

    // MARK: - Display

    var goalText: String { Self.money(goalCents) }
    var dispensedText: String { Self.money(dispensedCents) }
    var priceText: String { Self.money(pricePerGallonCents) + " / gal" }

    var gallonsText: String {
        String(format: "%.2f gal", gallons)
    }

    private var gallons: Double {
        guard pricePerGallonCents > 0 else { return 0 }
        return Double(dispensedCents) / Double(pricePerGallonCents)
    }

    /// How full the gauge looks, 0 to 1, clamped at the overfill limit.
    var gaugeFraction: Double {
        guard goalCents > 0 else { return 0 }
        let limit = Double(goalCents * ScoreRules.fillOverfillLimitMultiple)
        return min(1, Double(dispensedCents) / limit)
    }

    /// Where the target sits on the gauge, so the player can aim at it.
    var goalFraction: Double {
        1 / Double(ScoreRules.fillOverfillLimitMultiple)
    }

    /// True once the player has gone past the target — shown as a label, not
    /// only as a color.
    var isOverTarget: Bool { dispensedCents > goalCents }

    private static func money(_ cents: Int) -> String {
        String(format: "$%d.%02d", cents / 100, cents % 100)
    }

    // MARK: - Playing

    /// Picks a fresh target and gas price.
    func prepareRound() {
        tickTask?.cancel()
        tickTask = nil
        holdStart = nil
        dispensedCents = 0
        goalCents = ScoreRules.randomFillGoalCents()
        pricePerGallonCents = ScoreRules.randomFillPriceCents()
        phase = .ready
    }

    /// The player pressed the pump. Called on touch-DOWN.
    func beginPumping() {
        guard phase == .ready else { return }

        holdStart = clock.now()
        phase = .pumping

        // The gauge is redrawn on a timer, but the VALUE is always recomputed
        // from elapsed monotonic time rather than accumulated per tick, so a
        // dropped frame cannot cost the player fuel.
        tickTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(16))
                guard !Task.isCancelled else { return }
                await MainActor.run { self?.updateDispensed() }
            }
        }
    }

    /// The player let go. This is their answer.
    func endPumping() {
        guard phase == .pumping, let holdStart else { return }

        tickTask?.cancel()
        tickTask = nil

        dispensedCents = Self.dispensed(
            elapsedMS: clock.millisecondsSince(holdStart),
            pricePerGallonCents: pricePerGallonCents,
            goalCents: goalCents
        )
        self.holdStart = nil

        let score = ScoreRules.fillScore(dispensedCents: dispensedCents, goalCents: goalCents)
        phase = .finished(score: min(score, ScoreRules.fillBounds.upperBound))
        Haptics.success()
    }

    private func updateDispensed() {
        guard phase == .pumping, let holdStart else { return }

        let previous = dispensedCents
        dispensedCents = Self.dispensed(
            elapsedMS: clock.millisecondsSince(holdStart),
            pricePerGallonCents: pricePerGallonCents,
            goalCents: goalCents
        )

        // A light tick as the player crosses the target, so they can feel the
        // moment to let go as well as see it.
        if previous <= goalCents && dispensedCents > goalCents {
            Haptics.tap()
        }

        // The pump stops on its own at the overfill limit.
        if dispensedCents >= goalCents * ScoreRules.fillOverfillLimitMultiple {
            endPumping()
        }
    }

    /// The amount on the pump after holding for `elapsedMS`, capped at the
    /// overfill limit.
    private static func dispensed(elapsedMS: Int, pricePerGallonCents: Int, goalCents: Int) -> Int {
        let gallons = ScoreRules.gallonsDispensed(elapsedMS: elapsedMS)
        let cents = ScoreRules.dispensedCents(
            gallons: gallons,
            pricePerGallonCents: pricePerGallonCents
        )
        return min(cents, goalCents * ScoreRules.fillOverfillLimitMultiple)
    }

    func reset() {
        tickTask?.cancel()
        tickTask = nil
        holdStart = nil
        prepareRound()
    }

    // MARK: - Lifecycle

    func handleScenePhaseChange(to scenePhase: ScenePhase) {
        guard scenePhase != .active, isRoundActive else { return }

        tickTask?.cancel()
        tickTask = nil
        holdStart = nil
        phase = .cancelled
    }
}
