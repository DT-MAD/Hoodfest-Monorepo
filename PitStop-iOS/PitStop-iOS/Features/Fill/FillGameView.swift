//
//  FillGameView.swift
//  PitStop-iOS
//

import SwiftUI

struct FillGameView: View {

    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dismiss) private var dismiss

    @State private var model = FillGameModel()
    @State private var isNaming = false

    var body: some View {
        ZStack {
            Theme.asphalt.ignoresSafeArea()

            VStack(spacing: 22) {
                targetCard
                gauge
                readout
                pumpButton
            }
            .padding(24)
            .frame(maxWidth: .infinity)

            if model.phase == .cancelled {
                PanelBackdrop()
                RoundProblemPanel(
                    title: "Round cancelled",
                    message: "Something interrupted the pump, so this run can't be scored. Give it another go.",
                    onRetry: { model.reset() },
                    onQuit: { dismiss() }
                )
            }

            if let score = model.score {
                PanelBackdrop()
                ResultPanel(
                    game: .fill,
                    score: score,
                    detail: "You pumped \(model.dispensedText) of a \(model.goalText) target.",
                    onSubmit: { isNaming = true },
                    onRetry: { model.reset() },
                    onQuit: { dismiss() }
                )
            }
        }
        .animation(Theme.settle, value: model.phase)
        .navigationTitle(GameID.fill.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Theme.slate, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .onAppear { model.prepareRound() }
        .onDisappear { model.reset() }
        .onChange(of: scenePhase) { _, phase in
            model.handleScenePhaseChange(to: phase)
        }
        .sheet(isPresented: $isNaming) {
            if let score = model.score {
                SubmitNameView(
                    game: .fill,
                    score: score,
                    detail: ["goalCents": model.goalCents,
                             "pricePerGallonCents": model.pricePerGallonCents]
                )
            }
        }
        .onChange(of: isNaming) { wasShowing, isShowing in
            if wasShowing && !isShowing { dismiss() }
        }
    }

    // MARK: - Target

    private var targetCard: some View {
        VStack(spacing: 6) {
            Text("FILL UP TO")
                .font(Theme.caption())
                .tracking(1.6)
                .foregroundStyle(Theme.inkMuted)

            Text(model.goalText)
                .font(.system(size: 64, weight: .black, design: .rounded).monospacedDigit())
                .foregroundStyle(Theme.racingYellow)
                .minimumScaleFactor(0.5)
                .lineLimit(1)

            Text(model.priceText)
                .font(Theme.body())
                .foregroundStyle(Theme.inkMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 18)
        .pitStopCard()
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Target \(model.goalText), at \(model.priceText)")
    }

    // MARK: - Gauge

    private var gauge: some View {
        GeometryReader { proxy in
            let height = proxy.size.height
            let fill = height * model.gaugeFraction
            let goalY = height * (1 - model.goalFraction)

            ZStack(alignment: .bottom) {
                RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous)
                    .fill(Theme.slate)

                RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous)
                    .fill(model.isOverTarget ? Theme.signalRed : Theme.racingYellow)
                    .frame(height: fill)

                // The target line, labelled — the player aims at this.
                VStack(spacing: 0) {
                    Rectangle()
                        .fill(Theme.ink)
                        .frame(height: 3)
                    Text("TARGET")
                        .font(.system(size: 11, weight: .heavy, design: .rounded))
                        .tracking(1.4)
                        .foregroundStyle(Theme.ink)
                        .padding(.top, 2)
                }
                .frame(maxHeight: .infinity, alignment: .top)
                .offset(y: goalY)
            }
            .clipShape(RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous))
        }
        .frame(maxHeight: .infinity)
        .accessibilityElement()
        .accessibilityLabel("Fuel gauge")
        .accessibilityValue("\(model.dispensedText) of \(model.goalText)")
    }

    // MARK: - Readout

    private var readout: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("TOTAL")
                    .font(Theme.caption())
                    .tracking(1.4)
                    .foregroundStyle(Theme.inkMuted)
                Text(model.dispensedText)
                    .font(.system(size: 44, weight: .black, design: .rounded).monospacedDigit())
                    .foregroundStyle(model.isOverTarget ? Theme.signalRed : Theme.ink)
                    .contentTransition(.numericText())
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 6) {
                Text(model.gallonsText)
                    .font(Theme.scoreRow())
                    .foregroundStyle(Theme.inkMuted)

                // "Over" is a word and an icon, not just a red bar.
                if model.isOverTarget {
                    Label("Over target", systemImage: "exclamationmark.triangle.fill")
                        .font(Theme.caption())
                        .foregroundStyle(Theme.signalRed)
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity)
        .pitStopCard(Theme.slateHigh)
        .accessibilityElement(children: .combine)
    }

    // MARK: - Pump

    private var pumpButton: some View {
        TouchDownArea(onTouchDown: model.beginPumping) {
            VStack(spacing: 8) {
                Image(systemName: "fuelpump.fill")
                    .font(.system(size: 36, weight: .bold))
                Text(model.phase == .pumping ? "Let go to stop" : "Hold to pump")
                    .font(Theme.buttonLabel())
            }
            .frame(maxWidth: .infinity, minHeight: 130)
            .foregroundStyle(Theme.asphalt)
            .background(
                model.phase == .pumping ? Theme.signalRed : Theme.racingYellow,
                in: RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous)
            )
        }
        // The release is the player's answer, so it is handled here rather than
        // inside TouchDownArea, which only reports the press.
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onEnded { _ in model.endPumping() }
        )
        .accessibilityElement()
        .accessibilityLabel("Pump")
        .accessibilityHint("Touch and hold to dispense fuel. Let go to stop.")
        .accessibilityAddTraits(.isButton)
    }
}
