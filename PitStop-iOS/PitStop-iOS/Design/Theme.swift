//
//  Theme.swift
//  PitStop-iOS
//
//  The Pit Stop design language: a motorsports arcade game, readable outdoors,
//  operated by someone who has never seen the app before and is standing at a
//  booth with people waiting behind them.
//

import SwiftUI

enum Theme {

    // MARK: - Palette

    /// Accents, the leading position, and the "go" state.
    static let racingYellow = Color(red: 0xF7 / 255, green: 0xC8 / 255, blue: 0x43 / 255)

    /// Headers, the "wait" state, and destructive actions.
    static let signalRed = Color(red: 0xE7 / 255, green: 0x47 / 255, blue: 0x3F / 255)

    /// The page behind everything.
    static let asphalt = Color(red: 0x15 / 255, green: 0x19 / 255, blue: 0x1F / 255)

    /// Cards and raised surfaces.
    static let slate = Color(red: 0x25 / 255, green: 0x2B / 255, blue: 0x34 / 255)

    /// A surface one step above `slate`.
    static let slateHigh = Color(red: 0x2F / 255, green: 0x37 / 255, blue: 0x41 / 255)

    static let ink = Color.white
    static let inkMuted = Color.white.opacity(0.62)
    static let hairline = Color.white.opacity(0.12)

    // MARK: - Shape

    static let cornerRadius: CGFloat = 20
    static let cardRadius: CGFloat = 16

    /// Minimum height for anything a visitor taps. Comfortably above the 44pt
    /// guideline because this is a shared tablet used at arm's length, often
    /// by children, often in a hurry.
    static let tapTargetHeight: CGFloat = 88

    // MARK: - Type
    //
    // Everything below is relative to a Dynamic Type text style, so the whole
    // interface scales with the system setting rather than ignoring it.

    /// The big number on a result screen.
    static func scoreDisplay() -> Font {
        .system(.largeTitle, design: .rounded, weight: .black).monospacedDigit()
    }

    /// A score on a leaderboard row.
    static func scoreRow() -> Font {
        .system(.title3, design: .rounded, weight: .bold).monospacedDigit()
    }

    static func screenTitle() -> Font {
        .system(.largeTitle, design: .rounded, weight: .black)
    }

    static func cardTitle() -> Font {
        .system(.title2, design: .rounded, weight: .bold)
    }

    static func buttonLabel() -> Font {
        .system(.title3, design: .rounded, weight: .bold)
    }

    static func body() -> Font {
        .system(.body, design: .rounded)
    }

    static func caption() -> Font {
        .system(.subheadline, design: .rounded, weight: .semibold)
    }

    // MARK: - Motion

    /// Brief, so a round starts without the player waiting on an animation.
    static let quick = Animation.easeOut(duration: 0.18)
    static let settle = Animation.spring(response: 0.34, dampingFraction: 0.72)
}

extension View {
    /// The standard screen background.
    func pitStopBackground() -> some View {
        background(Theme.asphalt.ignoresSafeArea())
    }

    /// A raised card.
    func pitStopCard(_ fill: Color = Theme.slate) -> some View {
        background(fill, in: RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous))
    }
}
