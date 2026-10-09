//
//  GameID.swift
//  PitStop-iOS
//
//  The three mini-games. These raw values are the wire format shared with the
//  server and the Android app; do not rename them.
//

import Foundation

enum GameID: String, CaseIterable, Codable, Identifiable, Sendable {
    case reaction
    case fill
    case pitstop

    var id: String { rawValue }

    /// Display name, matching the leaderboard headings on the web board.
    var title: String {
        switch self {
        case .reaction: "Reaction Lights"
        case .fill: "Fill It Up"
        case .pitstop: "Perfect Pit Stop"
        }
    }

    /// One line explaining the goal, shown on the home screen and above a board.
    var tagline: String {
        switch self {
        case .reaction: "Fastest reaction to the green light"
        case .fill: "Closest to the dollar target"
        case .pitstop: "Fastest four-tire change"
        }
    }

    /// The single instruction a visitor reads before playing. The whole booth
    /// experience depends on this being understandable at a glance.
    var instruction: String {
        switch self {
        case .reaction: "Wait for the light to turn green, then tap as fast as you can."
        case .fill: "Hold the pump. Stop as close to the target as you can."
        case .pitstop: "Tap all four tires as fast as you can. Missing costs you a second."
        }
    }

    /// SF Symbol used alongside the title, so the game is identifiable by shape
    /// as well as by color.
    var symbolName: String {
        switch self {
        case .reaction: "bolt.fill"
        case .fill: "fuelpump.fill"
        case .pitstop: "car.fill"
        }
    }

    /// What the score means, shown under a result so "243 ms" needs no explaining.
    var scoreCaption: String {
        switch self {
        case .reaction: "Reaction time"
        case .fill: "Off the target"
        case .pitstop: "Pit stop time"
        }
    }
}
