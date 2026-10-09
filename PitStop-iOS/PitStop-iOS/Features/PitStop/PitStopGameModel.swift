//
//  PitStopGameModel.swift
//  PitStop-iOS
//
//  Perfect Pit Stop.
//
//  Four tires, tapped as fast as possible. Each tire counts once; anything else
//  tapped in the play area costs a second.
//

import Foundation
import SwiftUI

/// The four corners of the car.
enum Tire: String, CaseIterable, Identifiable, Sendable {
    case frontLeft, frontRight, rearLeft, rearRight

    var id: String { rawValue }

    var label: String {
        switch self {
        case .frontLeft: "Front left"
        case .frontRight: "Front right"
        case .rearLeft: "Rear left"
        case .rearRight: "Rear right"
        }
    }
}

@Observable
@MainActor
final class PitStopGameModel {

    enum Phase: Equatable {
        case ready
        case running
        case finished(score: Int)
        case cancelled
    }

    private(set) var phase: Phase = .ready

    /// Tires already changed. A Set, so a tire cannot register twice however
    /// fast the player drums on it.
    private(set) var changed: Set<Tire> = []

    private(set) var misTaps = 0

    /// Elapsed time, updated for the on-screen clock while running.
    private(set) var elapsedMS = 0

    private let clock = RoundClock()
    private var startedAt: RoundClock.Instant?
    private var tickTask: Task<Void, Never>?

    var score: Int? {
        if case .finished(let score) = phase { return score }
        return nil
    }

    var isRoundActive: Bool { phase == .running }

    var remainingCount: Int { ScoreRules.pitStopTireCount - changed.count }

    /// The running total, including penalties accrued so far.
    var runningScoreText: String {
        ScoreRules.format(
            ScoreRules.pitStopScore(elapsedMS: elapsedMS, misTaps: misTaps),
            for: .pitstop
        )
    }

    func isChanged(_ tire: Tire) -> Bool { changed.contains(tire) }

    // MARK: - Playing

    func start() {
        guard phase != .running else { return }

        changed = []
        misTaps = 0
        elapsedMS = 0
        startedAt = clock.now()
        phase = .running
        Haptics.go()

        tickTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(33))
                guard !Task.isCancelled else { return }
                await MainActor.run { self?.updateElapsed() }
            }
        }
    }

    /// A tap on a tire. Called on touch-DOWN.
    func tap(_ tire: Tire) {
        guard phase == .running else { return }

        // Already changed: this is a wasted tap, and counts against them, the
        // same as tapping the bodywork.
        guard !changed.contains(tire) else {
            registerMisTap()
            return
        }

        changed.insert(tire)
        Haptics.tap()

        if changed.count == ScoreRules.pitStopTireCount {
            finish()
        }
    }

    /// A tap in the play area that did not land on an un-changed tire.
    func tapMissed() {
        guard phase == .running else { return }
        registerMisTap()
    }

    private func registerMisTap() {
        misTaps += 1
        Haptics.wrong()
        updateElapsed()
    }

    private func finish() {
        guard phase == .running, let startedAt else { return }

        tickTask?.cancel()
        tickTask = nil

        elapsedMS = clock.millisecondsSince(startedAt)
        self.startedAt = nil

        let raw = ScoreRules.pitStopScore(elapsedMS: elapsedMS, misTaps: misTaps)

        // A run faster than the plausible floor is not a pit stop, and the
        // server would refuse it anyway.
        guard ScoreRules.inBounds(raw, for: .pitstop) else {
            phase = .cancelled
            return
        }

        phase = .finished(score: raw)
        Haptics.success()
    }

    private func updateElapsed() {
        guard phase == .running, let startedAt else { return }
        elapsedMS = clock.millisecondsSince(startedAt)
    }

    func reset() {
        tickTask?.cancel()
        tickTask = nil
        startedAt = nil
        changed = []
        misTaps = 0
        elapsedMS = 0
        phase = .ready
    }

    // MARK: - Lifecycle

    func handleScenePhaseChange(to scenePhase: ScenePhase) {
        guard scenePhase != .active, isRoundActive else { return }

        tickTask?.cancel()
        tickTask = nil
        startedAt = nil
        phase = .cancelled
    }
}
