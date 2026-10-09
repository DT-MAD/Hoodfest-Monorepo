//
//  PitStopGameView.swift
//  PitStop-iOS
//

import SwiftUI

struct PitStopGameView: View {

    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dismiss) private var dismiss

    @State private var model = PitStopGameModel()
    @State private var isNaming = false

    var body: some View {
        ZStack {
            Theme.asphalt.ignoresSafeArea()

            VStack(spacing: 18) {
                scoreboard
                carArea
                footer
            }
            .padding(24)
            .frame(maxWidth: 700)

            if model.phase == .cancelled {
                PanelBackdrop()
                RoundProblemPanel(
                    title: "Round cancelled",
                    message: "Something interrupted the timer, so this run can't be scored. Give it another go.",
                    onRetry: { model.reset() },
                    onQuit: { dismiss() }
                )
            }

            if let score = model.score {
                PanelBackdrop()
                ResultPanel(
                    game: .pitstop,
                    score: score,
                    detail: model.misTaps > 0
                        ? "\(ScoreRules.format(model.elapsedMS, for: .pitstop)) on the clock, plus \(model.misTaps) second\(model.misTaps == 1 ? "" : "s") of penalties."
                        : "A clean stop — no missed taps.",
                    onSubmit: { isNaming = true },
                    onRetry: { model.reset() },
                    onQuit: { dismiss() }
                )
            }
        }
        .animation(Theme.settle, value: model.phase)
        .navigationTitle(GameID.pitstop.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Theme.slate, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .onDisappear { model.reset() }
        .onChange(of: scenePhase) { _, phase in
            model.handleScenePhaseChange(to: phase)
        }
        .sheet(isPresented: $isNaming) {
            if let score = model.score {
                SubmitNameView(game: .pitstop, score: score, detail: ["misTaps": model.misTaps])
            }
        }
        .onChange(of: isNaming) { wasShowing, isShowing in
            if wasShowing && !isShowing { dismiss() }
        }
    }

    // MARK: - Scoreboard

    private var scoreboard: some View {
        HStack(spacing: 14) {
            stat(
                title: "Clock",
                value: model.isRoundActive || model.score != nil ? model.runningScoreText : "0.000s",
                tint: Theme.racingYellow
            )
            stat(
                title: "Tires left",
                value: "\(model.remainingCount)",
                tint: Theme.ink
            )
            stat(
                title: "Missed",
                value: "\(model.misTaps)",
                tint: model.misTaps > 0 ? Theme.signalRed : Theme.ink
            )
        }
    }

    private func stat(title: String, value: String, tint: Color) -> some View {
        VStack(spacing: 2) {
            Text(title.uppercased())
                .font(Theme.caption())
                .tracking(1.3)
                .foregroundStyle(Theme.inkMuted)
            Text(value)
                .font(.system(size: 30, weight: .black, design: .rounded).monospacedDigit())
                .foregroundStyle(tint)
                .minimumScaleFactor(0.5)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .pitStopCard()
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(title): \(value)")
    }

    // MARK: - The car

    /// A top-down car with a tire at each corner. Tapping the body — anywhere
    /// that is not an un-changed tire — costs a second.
    private var carArea: some View {
        ZStack {
            RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous)
                .fill(Theme.slate)

            carBody

            VStack(spacing: 0) {
                HStack {
                    tire(.frontLeft)
                    Spacer()
                    tire(.frontRight)
                }
                Spacer()
                HStack {
                    tire(.rearLeft)
                    Spacer()
                    tire(.rearRight)
                }
            }
            .padding(26)
        }
        .frame(maxHeight: .infinity)
        // Registered after the tires, so a tap that lands on a tire is handled
        // there and only a genuine miss reaches this.
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onEnded { _ in model.tapMissed() }
        )
        .accessibilityElement(children: .contain)
    }

    private var carBody: some View {
        RoundedRectangle(cornerRadius: 46, style: .continuous)
            .fill(Theme.slateHigh)
            .overlay(
                VStack(spacing: 10) {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(Theme.asphalt.opacity(0.55))
                        .frame(height: 46)
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(Theme.asphalt.opacity(0.35))
                        .frame(height: 70)
                }
                .padding(.horizontal, 34)
                .padding(.vertical, 56)
            )
            .padding(.horizontal, 78)
            .padding(.vertical, 18)
            .accessibilityHidden(true)
    }

    private func tire(_ tire: Tire) -> some View {
        let isChanged = model.isChanged(tire)

        return TouchDownArea(onTouchDown: { model.tap(tire) }) {
            ZStack {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(isChanged ? Theme.racingYellow : Theme.asphalt)
                    .overlay(
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .strokeBorder(isChanged ? Theme.racingYellow : Theme.inkMuted, lineWidth: 3)
                    )

                // Changed tires get a check mark, so the state is legible
                // without relying on the color change.
                Image(systemName: isChanged ? "checkmark" : "circle.hexagonpath.fill")
                    .font(.system(size: isChanged ? 40 : 30, weight: .black))
                    .foregroundStyle(isChanged ? Theme.asphalt : Theme.inkMuted)
            }
            .frame(width: 96, height: 128)
            .scaleEffect(isChanged ? 0.94 : 1)
        }
        .animation(Theme.quick, value: isChanged)
        .accessibilityElement()
        .accessibilityLabel("\(tire.label) tire")
        .accessibilityValue(isChanged ? "Changed" : "Not changed")
        .accessibilityAddTraits(.isButton)
    }

    // MARK: - Footer

    @ViewBuilder
    private var footer: some View {
        if model.phase == .ready {
            VStack(spacing: 14) {
                Text(GameID.pitstop.instruction)
                    .font(Theme.body())
                    .foregroundStyle(Theme.inkMuted)
                    .multilineTextAlignment(.center)

                PitStopButton(title: "Start", systemImage: "flag.checkered") {
                    model.start()
                }
            }
            .frame(maxWidth: 420)
        }
    }
}
