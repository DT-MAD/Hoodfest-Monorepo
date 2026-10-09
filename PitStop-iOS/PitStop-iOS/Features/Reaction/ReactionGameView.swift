//
//  ReactionGameView.swift
//  PitStop-iOS
//

import SwiftUI

struct ReactionGameView: View {

    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dismiss) private var dismiss

    @State private var model = ReactionGameModel()
    @State private var isNaming = false

    var body: some View {
        ZStack {
            lightSurface
                .ignoresSafeArea()

            VStack(spacing: 28) {
                Spacer()
                statusText
                Spacer()
                footer
            }
            .padding(28)

            if model.phase == .falseStart {
                PanelBackdrop()
                RoundProblemPanel(
                    title: "False start",
                    message: "You went before the light. Wait for green, then tap.",
                    systemImage: "flag.slash.fill",
                    onRetry: { model.reset(); model.start() },
                    onQuit: { dismiss() }
                )
            }

            if model.phase == .cancelled {
                PanelBackdrop()
                RoundProblemPanel(
                    title: "Round cancelled",
                    message: "Something interrupted the timer, so this run can't be scored. Give it another go.",
                    onRetry: { model.reset(); model.start() },
                    onQuit: { dismiss() }
                )
            }

            if let score = model.score {
                PanelBackdrop()
                ResultPanel(
                    game: .reaction,
                    score: score,
                    detail: nil,
                    onSubmit: { isNaming = true },
                    onRetry: { model.reset(); model.start() },
                    onQuit: { dismiss() }
                )
            }
        }
        .animation(Theme.settle, value: model.phase)
        .navigationTitle(GameID.reaction.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Theme.slate, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .onChange(of: scenePhase) { _, phase in
            model.handleScenePhaseChange(to: phase)
        }
        .onDisappear { model.reset() }
        .sheet(isPresented: $isNaming) {
            if let score = model.score {
                SubmitNameView(game: .reaction, score: score, detail: nil)
            }
        }
        .onChange(of: isNaming) { wasShowing, isShowing in
            // Returning from the name sheet means the run is done with.
            if wasShowing && !isShowing { dismiss() }
        }
    }

    // MARK: - The light

    /// The whole screen is the light, and the whole screen is the target. A
    /// large tap area is the point: nobody should miss.
    private var lightSurface: some View {
        TouchDownArea(onTouchDown: handleTouch) {
            surfaceColor
                .overlay(alignment: .center) { lightGlyph }
        }
        .accessibilityElement()
        .accessibilityLabel(accessibilityLabel)
        .accessibilityHint(model.phase == .ready ? "Double tap to start" : "Tap when the light turns green")
        .accessibilityAddTraits(.isButton)
    }

    private var surfaceColor: Color {
        switch model.phase {
        case .ready: Theme.asphalt
        case .waiting: Theme.signalRed
        case .go: Color(red: 0.18, green: 0.78, blue: 0.35)
        case .finished, .falseStart, .cancelled: Theme.asphalt
        }
    }

    /// State is carried by a shape and a word as well as by the color, so the
    /// game is playable by someone who cannot tell red from green.
    @ViewBuilder
    private var lightGlyph: some View {
        switch model.phase {
        case .waiting:
            Image(systemName: "hand.raised.fill")
                .font(.system(size: 120, weight: .bold))
                .foregroundStyle(.white.opacity(0.92))
                .accessibilityHidden(true)
        case .go:
            Image(systemName: "bolt.fill")
                .font(.system(size: 140, weight: .black))
                .foregroundStyle(.white)
                .accessibilityHidden(true)
        default:
            EmptyView()
        }
    }

    private var accessibilityLabel: String {
        switch model.phase {
        case .ready: "Reaction Lights. Not started."
        case .waiting: "Red light. Wait."
        case .go: "Green light. Tap now."
        case .finished(let score): "Finished. \(ScoreRules.format(score, for: .reaction))."
        case .falseStart: "False start."
        case .cancelled: "Round cancelled."
        }
    }

    // MARK: - Chrome

    @ViewBuilder
    private var statusText: some View {
        switch model.phase {
        case .ready:
            VStack(spacing: 12) {
                Image(systemName: GameID.reaction.symbolName)
                    .font(.system(size: 64, weight: .bold))
                    .foregroundStyle(Theme.racingYellow)
                    .accessibilityHidden(true)

                Text(GameID.reaction.instruction)
                    .font(Theme.cardTitle())
                    .foregroundStyle(Theme.ink)
                    .multilineTextAlignment(.center)
            }
            .accessibilityElement(children: .combine)

        case .waiting:
            Text("WAIT")
                .font(.system(size: 76, weight: .black, design: .rounded))
                .foregroundStyle(.white)

        case .go:
            Text("TAP!")
                .font(.system(size: 92, weight: .black, design: .rounded))
                .foregroundStyle(.white)

        case .finished, .falseStart, .cancelled:
            EmptyView()
        }
    }

    @ViewBuilder
    private var footer: some View {
        if model.phase == .ready {
            PitStopButton(title: "Start", systemImage: "flag.checkered") {
                model.start()
            }
            .frame(maxWidth: 420)
        }
    }

    private func handleTouch() {
        switch model.phase {
        case .ready: model.start()
        case .waiting, .go: model.tap()
        default: break
        }
    }
}
