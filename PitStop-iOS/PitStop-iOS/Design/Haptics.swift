//
//  Haptics.swift
//  PitStop-iOS
//
//  Haptic feedback is always additive. Every state it reinforces is also shown
//  on screen, because the app runs on a shared tablet in a loud environment and
//  must not depend on anyone feeling anything.
//

import UIKit

enum Haptics {

    /// The green light, or the fourth tire.
    static func go() {
        UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
    }

    /// A correct tap.
    static func tap() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    /// A false start, a mis-tap, or a refused name.
    static func wrong() {
        UINotificationFeedbackGenerator().notificationOccurred(.error)
    }

    /// A score accepted onto the leaderboard.
    static func success() {
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    /// Prepares the engine so the first impact of a round is not delayed. Worth
    /// doing before a reaction test, where a few milliseconds is the score.
    static func prepare() {
        UIImpactFeedbackGenerator(style: .heavy).prepare()
    }
}
