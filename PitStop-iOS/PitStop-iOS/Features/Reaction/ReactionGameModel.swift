//
//  ReactionGameModel.swift
//  PitStop-iOS
//
//  Reaction Lights.
//
//  The player waits on a red light, and taps the instant it turns green. The
//  score is the milliseconds between those two events, measured on a monotonic
//  clock, registered on touch-down.
//

import Foundation
import SwiftUI

@Observable
@MainActor
final class ReactionGameModel {

    /// The round's state. Every transition below is guarded, so a double tap
    /// cannot start two timers and a late tap cannot score a finished round.
    enum Phase: Equatable {
        /// Before the first tap.
        case ready
        /// Red light showing; tapping now is a false start.
        case waiting
        /// Green light showing; the clock is running.
        case go
        /// A valid reaction time.
        case finished(score: Int)
        /// Tapped too early.
        case falseStart
        /// The round was interrupted and cannot be scored honestly.
        case cancelled
    }

    private(set) var phase: Phase = .ready

    private let clock = RoundClock()
    private var greenAt: RoundClock.Instant?
    private var waitTask: Task<Void, Never>?

    /// The score, once there is one.
    var score: Int? {
        if case .finished(let score) = phase { return score }
        return nil
    }

    var isRoundActive: Bool {
        phase == .waiting || phase == .go
    }

    // MARK: - Playing

    /// Arms the round: shows the red light, then turns it green after a random
    /// delay of two to five seconds.
    func start() {
        // Guard against a second tap on Start before the UI has caught up.
        guard !isRoundActive else { return }

        waitTask?.cancel()
        greenAt = nil
        phase = .waiting

        // Warm the haptic engine now, so the green light's feedback is not
        // delayed by a few milliseconds at the exact moment being measured.
        Haptics.prepare()

        let waitMS = ScoreRules.randomReactionWaitMS()

        waitTask = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(waitMS))
            guard !Task.isCancelled else { return }

            await MainActor.run {
                guard let self, self.phase == .waiting else { return }
                self.greenAt = self.clock.now()
                self.phase = .go
                Haptics.go()
            }
        }
    }

    /// Registers a touch on the play area. Called on touch-DOWN.
    func tap() {
        switch phase {
        case .waiting:
            // Jumped the light.
            waitTask?.cancel()
            waitTask = nil
            phase = .falseStart
            Haptics.wrong()

        case .go:
            guard let greenAt else {
                // The clock was never started; refuse to invent a score.
                phase = .cancelled
                return
            }
            let elapsed = clock.millisecondsSince(greenAt)
            self.greenAt = nil

            // A reaction faster than the plausible floor is not a reaction, it
            // is a tap that was already on its way down.
            guard ScoreRules.inBounds(elapsed, for: .reaction) else {
                phase = .falseStart
                Haptics.wrong()
                return
            }

            phase = .finished(score: ScoreRules.reactionScore(greenToTapMS: elapsed))
            Haptics.success()

        case .ready, .finished, .falseStart, .cancelled:
            // Taps outside a round do nothing.
            break
        }
    }

    /// Clears everything for another go. The view also calls this when it
    /// disappears, which is what retires a pending wait task — the task itself
    /// holds only a weak reference and checks the phase before acting, so an
    /// orphaned one can never score a round that is no longer on screen.
    func reset() {
        waitTask?.cancel()
        waitTask = nil
        greenAt = nil
        phase = .ready
    }

    // MARK: - Lifecycle

    /// Invalidates an in-flight round when the app is backgrounded, a call
    /// arrives, or the scene otherwise stops being active.
    ///
    /// A round whose timing cannot be vouched for must not produce a score.
    func handleScenePhaseChange(to scenePhase: ScenePhase) {
        guard scenePhase != .active, isRoundActive else { return }

        waitTask?.cancel()
        waitTask = nil
        greenAt = nil
        phase = .cancelled
    }
}
