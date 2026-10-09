//
//  ResultPanel.swift
//  PitStop-iOS
//
//  What a player sees the moment a round ends: their score, and three large
//  choices. Shared by all three games so the ending is always the same shape.
//

import SwiftUI

struct ResultPanel: View {

    let game: GameID
    let score: Int
    var detail: String?

    let onSubmit: () -> Void
    let onRetry: () -> Void
    let onQuit: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Text("Nice run")
                .font(Theme.cardTitle())
                .foregroundStyle(Theme.ink)

            ScoreDisplay(
                value: ScoreRules.format(score, for: game),
                caption: game.scoreCaption
            )

            if let detail {
                Text(detail)
                    .font(Theme.body())
                    .foregroundStyle(Theme.inkMuted)
                    .multilineTextAlignment(.center)
            }

            VStack(spacing: 12) {
                PitStopButton(title: "Add to leaderboard", systemImage: "trophy.fill", action: onSubmit)
                PitStopButton(title: "Try again", systemImage: "arrow.clockwise", kind: .secondary, action: onRetry)
                PitStopButton(title: "Done", systemImage: "house.fill", kind: .quiet, action: onQuit)
            }
        }
        .padding(28)
        .frame(maxWidth: 520)
        .pitStopCard()
        .padding(24)
    }
}

/// Shown when a round could not be scored honestly — a false start, or an
/// interruption that broke the timing.
struct RoundProblemPanel: View {

    let title: String
    let message: String
    var systemImage: String = "exclamationmark.triangle.fill"

    let onRetry: () -> Void
    let onQuit: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: systemImage)
                .font(.system(size: 52, weight: .bold))
                .foregroundStyle(Theme.signalRed)
                .accessibilityHidden(true)

            Text(title)
                .font(Theme.cardTitle())
                .foregroundStyle(Theme.ink)
                .multilineTextAlignment(.center)

            Text(message)
                .font(Theme.body())
                .foregroundStyle(Theme.inkMuted)
                .multilineTextAlignment(.center)

            VStack(spacing: 12) {
                PitStopButton(title: "Try again", systemImage: "arrow.clockwise", action: onRetry)
                PitStopButton(title: "Done", systemImage: "house.fill", kind: .quiet, action: onQuit)
            }
        }
        .padding(28)
        .frame(maxWidth: 520)
        .pitStopCard()
        .padding(24)
        .accessibilityElement(children: .contain)
    }
}

/// Dims whatever is behind a panel.
struct PanelBackdrop: View {
    var body: some View {
        Color.black.opacity(0.62)
            .ignoresSafeArea()
            .transition(.opacity)
    }
}
