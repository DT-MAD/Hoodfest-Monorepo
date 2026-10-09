//
//  SettingsView.swift
//  PitStop-iOS
//
//  For the booth operator, not the visitor. Reached by a long press on the home
//  screen logo so it stays out of the way during the event.
//

import SwiftUI

struct SettingsView: View {

    @Environment(AppConfig.self) private var config
    @Environment(LocalLeaderboardStore.self) private var localStore
    @Environment(LeaderboardRepository.self) private var repository
    @Environment(\.dismiss) private var dismiss

    @State private var address = ""
    @State private var checkState = CheckState.idle
    @State private var isConfirmingClear = false

    private enum CheckState: Equatable {
        case idle, checking, reachable, unreachable, invalid

        var message: String? {
            switch self {
            case .idle: nil
            case .checking: "Checking…"
            case .reachable: "Server reachable"
            case .unreachable: "No answer from that address"
            case .invalid: "That doesn't look like an address"
            }
        }

        var symbolName: String {
            switch self {
            case .idle, .checking: "clock"
            case .reachable: "checkmark.circle.fill"
            case .unreachable, .invalid: "exclamationmark.triangle.fill"
            }
        }

        var tint: Color {
            switch self {
            case .reachable: Theme.racingYellow
            case .unreachable, .invalid: Theme.signalRed
            case .idle, .checking: Theme.inkMuted
            }
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                serverSection
                localSection
                aboutSection
            }
            .scrollContentBackground(.hidden)
            .background(Theme.asphalt.ignoresSafeArea())
            .navigationTitle("Booth settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { save() }
                        .fontWeight(.bold)
                        .foregroundStyle(Theme.racingYellow)
                }
            }
            .toolbarBackground(Theme.slate, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .onAppear { address = config.baseURLString }
    }

    // MARK: - Server

    private var serverSection: some View {
        Section {
            TextField("", text: $address,
                      prompt: Text("192.168.1.50:8080").foregroundStyle(Theme.inkMuted))
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.URL)
                .foregroundStyle(Theme.ink)
                .onChange(of: address) { _, _ in checkState = .idle }
                .accessibilityLabel("Leaderboard server address")

            if let message = checkState.message {
                Label(message, systemImage: checkState.symbolName)
                    .font(Theme.caption())
                    .foregroundStyle(checkState.tint)
            }

            Button("Test connection") { test() }
                .foregroundStyle(Theme.racingYellow)
                .disabled(checkState == .checking)

            if !config.isUsingDefaultAddress {
                Button("Reset to default") {
                    address = AppConfig.defaultBaseURL
                    checkState = .idle
                }
                .foregroundStyle(Theme.inkMuted)
            }
        } header: {
            Text("Leaderboard server").foregroundStyle(Theme.inkMuted)
        } footer: {
            Text("The scheme is optional — \"192.168.1.50:8080\" works. Scores are always saved on this iPad first, so the games keep working if the server is unreachable.")
                .foregroundStyle(Theme.inkMuted)
        }
        .listRowBackground(Theme.slate)
    }

    // MARK: - Local scores

    private var localSection: some View {
        Section {
            LabeledContent("Saved on this iPad") {
                Text("\(localStore.entries.count)")
                    .monospacedDigit()
                    .foregroundStyle(Theme.ink)
            }
            .foregroundStyle(Theme.inkMuted)

            Button("Clear local scores", role: .destructive) {
                isConfirmingClear = true
            }
            .disabled(localStore.entries.isEmpty)
        } header: {
            Text("On this device").foregroundStyle(Theme.inkMuted)
        } footer: {
            Text("Clearing affects only this iPad. Scores already on the booth leaderboard stay there.")
                .foregroundStyle(Theme.inkMuted)
        }
        .listRowBackground(Theme.slate)
        .confirmationDialog(
            "Clear every score saved on this iPad?",
            isPresented: $isConfirmingClear,
            titleVisibility: .visible
        ) {
            Button("Clear scores", role: .destructive) {
                localStore.clear()
                Task { await repository.refresh() }
            }
            Button("Keep them", role: .cancel) {}
        }
    }

    private var aboutSection: some View {
        Section {
            Text("Pit Stop — built by the Dixie Tech Mobile App Development program.")
                .font(Theme.caption())
                .foregroundStyle(Theme.inkMuted)
        }
        .listRowBackground(Theme.slate)
    }

    // MARK: - Actions

    private func test() {
        guard AppConfig.normalizedURL(from: address) != nil else {
            checkState = .invalid
            return
        }

        checkState = .checking

        Task {
            // Check against what was typed, without committing to it yet.
            let previous = config.baseURLString
            config.baseURLString = address
            let reachable = await LeaderboardAPI(config: config).checkHealth()
            config.baseURLString = previous

            checkState = reachable ? .reachable : .unreachable
        }
    }

    private func save() {
        if AppConfig.normalizedURL(from: address) != nil {
            config.baseURLString = address
        }
        Task { await repository.refresh() }
        dismiss()
    }
}
