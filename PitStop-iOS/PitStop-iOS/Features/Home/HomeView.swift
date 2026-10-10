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
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    @State private var route: GameID?
    @State private var isShowingLeaderboard = false
    @State private var isShowingSettings = false

    /// Below this width three tiles stop being readable and the grid becomes a
    /// single column. Comfortably under an iPad in portrait, which is the booth
    /// configuration, so the grid is what visitors actually see.
    private static let gridBreakpoint: CGFloat = 700

    var body: some View {
        NavigationStack {
            GeometryReader { proxy in
                let isWide = proxy.size.width >= Self.gridBreakpoint

                ScrollView {
                    VStack(spacing: 20) {
                        masthead(isWide: isWide)
                        gameGrid(isWide: isWide, availableHeight: proxy.size.height)
                        leaderboardButton
                        programNote
                    }
                    .padding(22)
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

    private func masthead(isWide: Bool) -> some View {
        HStack(spacing: 18) {
            CheckeredFlag(size: isWide ? 64 : 52)

            VStack(alignment: .leading, spacing: 2) {
                Text("PIT STOP")
                    .font(.system(size: isWide ? 52 : 40, weight: .black, design: .rounded))
                    .foregroundStyle(Theme.ink)
                    .minimumScaleFactor(0.5)
                    .lineLimit(1)

                Text("DIXIE TECH")
                    .font(Theme.caption())
                    .tracking(2.8)
                    .foregroundStyle(Theme.racingYellow)
                    .lineLimit(1)
            }
            .layoutPriority(1)

            Spacer(minLength: 0)

            // Dropped on a narrow screen: it is a nicety, and competing with
            // it for width was truncating the app's own name to "PIT S...".
            if isWide {
                Text("Pick a game")
                    .font(Theme.body())
                    .foregroundStyle(Theme.inkMuted)
            }
        }
        .padding(20)
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
        .accessibilityLabel("Pit Stop, by Dixie Tech. Pick a game.")
        .accessibilityHint("Press and hold for booth settings")
        // Hidden from visitors, obvious to an operator who has been told.
        // Matches Android's long-press duration: an operator who has been told
        // "press and hold" should not have to be told how long for.
        .onLongPressGesture(minimumDuration: 0.6) {
            Haptics.tap()
            isShowingSettings = true
        }
    }

    // MARK: - Games

    /// Three tiles side by side on a tablet, stacked on anything narrow. The
    /// grid is the point: all three games are visible at once, each one big
    /// enough to read and hit from a step away.
    private func gameGrid(isWide: Bool, availableHeight: CGFloat) -> some View {
        // The games are the point of the screen, so let the tiles grow into
        // whatever room the device has rather than sitting at a fixed size
        // with a band of empty asphalt above and below them.
        let tileHeight = isWide
            ? min(430, max(290, availableHeight * 0.32))
            : 112

        return LazyVGrid(
            columns: Array(
                repeating: GridItem(.flexible(), spacing: 16),
                count: isWide ? GameID.allCases.count : 1
            ),
            spacing: 16
        ) {
            ForEach(GameID.allCases) { game in
                Button {
                    route = game
                } label: {
                    GameTile(
                        game: game,
                        best: repository.board(for: game).top.first,
                        isStacked: !isWide,
                        height: tileHeight
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - Leaderboard

    private var leaderboardButton: some View {
        PitStopButton(title: "See the leaderboard", systemImage: "list.number", kind: .secondary) {
            isShowingLeaderboard = true
        }
    }

    // MARK: - The pitch
    //
    // The booth exists to showcase the program, so this is the message. It
    // describes what students in the program do — it does not claim they wrote
    // this particular app.

    private var programNote: some View {
        HStack(alignment: .top, spacing: 16) {
            Image(systemName: "hammer.fill")
                .font(.system(size: 26, weight: .bold))
                .foregroundStyle(Theme.asphalt)
                .frame(width: 58, height: 58)
                .background(Theme.racingYellow, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 6) {
                Text("Mobile App Development at Dixie Tech")
                    .font(Theme.cardTitle())
                    .foregroundStyle(Theme.racingYellow)
                    .fixedSize(horizontal: false, vertical: true)

                Text("""
                     Students in this program build real iOS and Android apps — Swift, \
                     Kotlin, and the servers behind them. If making something like this \
                     sounds like your kind of thing, come talk to us.
                     """)
                    .font(Theme.body())
                    .foregroundStyle(Theme.inkMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer(minLength: 0)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .pitStopCard(Theme.slateHigh)
        .accessibilityElement(children: .combine)
    }
}

/// One game, as a tile in the home grid.
struct GameTile: View {

    let game: GameID
    var best: Entry?
    var isStacked: Bool
    var height: CGFloat

    var body: some View {
        Group {
            if isStacked {
                HStack(spacing: 18) {
                    icon
                    titleBlock
                    Spacer(minLength: 0)
                    bestBlock
                }
            } else {
                VStack(alignment: .leading, spacing: 14) {
                    icon
                    titleBlock
                    Spacer(minLength: 8)
                    Divider().overlay(Theme.hairline)
                    bestBlock
                }
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(minHeight: height, alignment: .topLeading)
        .pitStopCard()
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityHint(game.instruction)
        .accessibilityAddTraits(.isButton)
    }

    private var icon: some View {
        let side = isStacked ? 76 : min(120, max(88, height * 0.26))

        return Image(systemName: game.symbolName)
            .font(.system(size: side * 0.48, weight: .bold))
            .foregroundStyle(Theme.asphalt)
            .frame(width: side, height: side)
            .background(Theme.racingYellow, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .accessibilityHidden(true)
    }

    private var titleBlock: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(game.title)
                .font(Theme.cardTitle())
                .foregroundStyle(Theme.ink)
                .fixedSize(horizontal: false, vertical: true)

            Text(game.tagline)
                .font(Theme.body())
                .foregroundStyle(Theme.inkMuted)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// The current leader, or an invitation when nobody has played yet.
    @ViewBuilder
    private var bestBlock: some View {
        if let best {
            VStack(alignment: isStacked ? .trailing : .leading, spacing: 2) {
                Text("BEST")
                    .font(.system(size: 11, weight: .heavy, design: .rounded))
                    .tracking(1.6)
                    .foregroundStyle(Theme.inkMuted)

                Text(best.display)
                    .font(Theme.scoreRow())
                    .foregroundStyle(Theme.racingYellow)
                    .lineLimit(1)

                Text(best.name)
                    .font(Theme.caption())
                    .foregroundStyle(Theme.inkMuted)
                    .lineLimit(1)
            }
        } else {
            Text("No scores yet")
                .font(Theme.caption())
                .foregroundStyle(Theme.inkMuted)
        }
    }

    private var accessibilityLabel: String {
        guard let best else {
            return "\(game.title). \(game.tagline). No scores yet."
        }
        return "\(game.title). \(game.tagline). Best so far: \(best.name), \(best.display)."
    }
}
