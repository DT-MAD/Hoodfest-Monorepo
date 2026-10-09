//
//  HomeView.swift
//  PitStop-iOS
//
//  What a visitor sees when they walk up. It has to explain itself in about two
//  seconds, with no instructions from the operator.
//

import SwiftUI

struct HomeView: View {

    @Environment(LeaderboardRepository.self) private var repository

    @State private var route: GameID?
    @State private var isShowingLeaderboard = false
    @State private var isShowingSettings = false

    var body: some View {
        NavigationStack {
            // The booth iPad is a big screen showing a short list. Measuring
            // the viewport lets the content centre itself when it fits and
            // scroll when it does not, instead of hugging the top and leaving
            // a third of the display empty.
            GeometryReader { proxy in
                ScrollView {
                    VStack(spacing: 20) {
                        masthead
                        gameCards
                        leaderboardButton
                        programNote
                    }
                    .padding(22)
                    .frame(maxWidth: 760)
                    .frame(maxWidth: .infinity, minHeight: proxy.size.height)
                }
            }
            .pitStopBackground()
            .navigationDestination(item: $route) { game in
                switch game {
                case .reaction: ReactionGameView()
                case .fill: FillGameView()
                case .pitstop: PitStopGameView()
                }
            }
            .navigationDestination(isPresented: $isShowingLeaderboard) {
                LeaderboardView()
            }
        }
        .sheet(isPresented: $isShowingSettings) { SettingsView() }
        .task { await repository.refresh() }
    }

    // MARK: - Masthead

    private var masthead: some View {
        HStack(spacing: 16) {
            CheckeredFlag(size: 56)

            VStack(alignment: .leading, spacing: 2) {
                Text("PIT STOP")
                    .font(.system(size: 44, weight: .black, design: .rounded))
                    .foregroundStyle(Theme.ink)

                Text("DIXIE TECH")
                    .font(Theme.caption())
                    .tracking(2.6)
                    .foregroundStyle(Theme.racingYellow)
            }

            Spacer()
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .pitStopCard()
        .overlay(alignment: .top) {
            Rectangle()
                .fill(Theme.signalRed)
                .frame(height: 4)
                .clipShape(UnevenRoundedRectangle(topLeadingRadius: Theme.cardRadius,
                                                  topTrailingRadius: Theme.cardRadius))
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Pit Stop, by Dixie Tech")
        .accessibilityHint("Press and hold for booth settings")
        // Hidden from visitors, obvious to an operator who has been told.
        .onLongPressGesture(minimumDuration: 1.2) {
            Haptics.tap()
            isShowingSettings = true
        }
    }

    // MARK: - Games

    private var gameCards: some View {
        VStack(spacing: 14) {
            ForEach(GameID.allCases) { game in
                Button {
                    route = game
                } label: {
                    GameCard(game: game, best: bestScoreText(for: game))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func bestScoreText(for game: GameID) -> String? {
        guard let best = repository.board(for: game).top.first else { return nil }
        return "Best: \(best.name) · \(best.display)"
    }

    // MARK: - Leaderboard

    private var leaderboardButton: some View {
        PitStopButton(title: "See the leaderboard", systemImage: "list.number", kind: .secondary) {
            isShowingLeaderboard = true
        }
    }

    // MARK: - The pitch

    private var programNote: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("Built by students", systemImage: "hammer.fill")
                .font(Theme.cardTitle())
                .foregroundStyle(Theme.racingYellow)

            Text("""
                 Everything here — this iPad app, the Android one next to it, and the \
                 leaderboard on the screen — was built by students in the Dixie Tech \
                 Mobile App Development program. Swift, Kotlin and Go. Ask us about it.
                 """)
                .font(Theme.body())
                .foregroundStyle(Theme.inkMuted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .pitStopCard(Theme.slateHigh)
        .accessibilityElement(children: .combine)
    }
}

struct GameCard: View {

    let game: GameID
    var best: String?

    var body: some View {
        HStack(spacing: 18) {
            Image(systemName: game.symbolName)
                .font(.system(size: 38, weight: .bold))
                .foregroundStyle(Theme.asphalt)
                .frame(width: 84, height: 84)
                .background(Theme.racingYellow, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 4) {
                Text(game.title)
                    .font(Theme.cardTitle())
                    .foregroundStyle(Theme.ink)

                Text(game.tagline)
                    .font(Theme.body())
                    .foregroundStyle(Theme.inkMuted)
                    .fixedSize(horizontal: false, vertical: true)

                if let best {
                    Text(best)
                        .font(Theme.caption())
                        .foregroundStyle(Theme.racingYellow)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 8)

            Image(systemName: "chevron.right")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(Theme.inkMuted)
                .accessibilityHidden(true)
        }
        .padding(20)
        .frame(maxWidth: .infinity, minHeight: 116, alignment: .leading)
        .pitStopCard()
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityLabel(game.title)
        .accessibilityHint(game.instruction)
        .accessibilityAddTraits(.isButton)
    }
}
