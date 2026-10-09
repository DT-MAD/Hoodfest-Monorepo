//
//  SubmitNameView.swift
//  PitStop-iOS
//
//  Name entry, with the same rules the server enforces so a name accepted here
//  is never refused after the fact.
//

import SwiftUI

struct SubmitNameView: View {

    let game: GameID
    let score: Int
    let detail: [String: Int]?

    @Environment(LeaderboardRepository.self) private var repository
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var isSubmitting = false
    @State private var outcome: LeaderboardRepository.Outcome?
    @FocusState private var isFieldFocused: Bool

    /// Live validation, but only once they have typed something — nobody wants
    /// to be told their empty field is invalid before they start.
    private var failure: NameValidator.Failure? {
        name.isEmpty ? nil : NameValidator.failure(for: name)
    }

    private var canSubmit: Bool {
        !isSubmitting && !name.isEmpty && failure == nil
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Theme.asphalt.ignoresSafeArea()

                if let outcome {
                    confirmation(outcome)
                } else {
                    entry
                }
            }
            .navigationTitle(outcome == nil ? "Add your name" : "You're on the board")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    if outcome == nil {
                        Button("Cancel") { dismiss() }
                            .foregroundStyle(Theme.inkMuted)
                    }
                }
            }
            .toolbarBackground(Theme.slate, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
    }

    // MARK: - Entry

    private var entry: some View {
        VStack(spacing: 24) {
            ScoreDisplay(
                value: ScoreRules.format(score, for: game),
                caption: game.scoreCaption
            )
            .padding(.top, 12)

            VStack(alignment: .leading, spacing: 10) {
                TextField("", text: $name, prompt: Text("Your name").foregroundStyle(Theme.inkMuted))
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .submitLabel(.done)
                    .focused($isFieldFocused)
                    .font(Theme.cardTitle())
                    .foregroundStyle(Theme.ink)
                    .padding(.horizontal, 18)
                    .frame(minHeight: 72)
                    .background(Theme.slate, in: RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous)
                            .strokeBorder(failure == nil ? Theme.hairline : Theme.signalRed, lineWidth: 2)
                    )
                    .onSubmit { if canSubmit { submit() } }
                    .accessibilityLabel("Your name")
                    .accessibilityHint("Up to \(NameValidator.maxLength) characters, letters and numbers")

                // The reason is text and an icon, never color alone.
                if let failure {
                    Label(failure.message, systemImage: "exclamationmark.circle.fill")
                        .font(Theme.caption())
                        .foregroundStyle(Theme.signalRed)
                        .transition(.opacity)
                } else {
                    Text("\(NameValidator.maxLength - name.count) characters left")
                        .font(Theme.caption())
                        .foregroundStyle(Theme.inkMuted)
                }
            }
            .animation(Theme.quick, value: failure)

            PitStopButton(
                title: isSubmitting ? "Adding…" : "Add to leaderboard",
                systemImage: "trophy.fill",
                action: submit
            )
            .opacity(canSubmit ? 1 : 0.45)
            .disabled(!canSubmit)

            Spacer()
        }
        .padding(24)
        .onAppear { isFieldFocused = true }
    }

    // MARK: - Confirmation

    private func confirmation(_ outcome: LeaderboardRepository.Outcome) -> some View {
        VStack(spacing: 22) {
            Image(systemName: outcome.entry.rank == 1 ? "trophy.fill" : "checkmark.circle.fill")
                .font(.system(size: 64, weight: .bold))
                .foregroundStyle(Theme.racingYellow)
                .accessibilityHidden(true)

            VStack(spacing: 6) {
                Text(outcome.entry.name)
                    .font(Theme.screenTitle())
                    .foregroundStyle(Theme.ink)

                Text(rankText(for: outcome.entry))
                    .font(Theme.cardTitle())
                    .foregroundStyle(Theme.racingYellow)
            }
            .accessibilityElement(children: .combine)

            ScoreDisplay(
                value: ScoreRules.format(outcome.entry.score, for: game),
                caption: game.scoreCaption,
                tint: Theme.ink
            )

            StatusPill(
                text: outcome.reachedServer ? "On the booth leaderboard" : "Saved on this iPad",
                systemImage: outcome.source.symbolName,
                tint: outcome.reachedServer ? Theme.racingYellow : Theme.inkMuted
            )

            Spacer()

            PitStopButton(title: "Done", systemImage: "house.fill") { dismiss() }
        }
        .padding(24)
    }

    private func rankText(for entry: Entry) -> String {
        switch entry.rank {
        case 1: "1st place"
        case 2: "2nd place"
        case 3: "3rd place"
        case let rank where rank > 0: "\(rank)th place"
        default: "Added to the board"
        }
    }

    // MARK: - Actions

    private func submit() {
        guard canSubmit, case .success(let canonical) = NameValidator.validate(name) else {
            Haptics.wrong()
            return
        }

        isSubmitting = true

        Task {
            let result = await repository.submit(
                game: game, name: canonical, score: score, detail: detail
            )
            isSubmitting = false
            Haptics.success()
            withAnimation(Theme.settle) { outcome = result }
        }
    }
}
