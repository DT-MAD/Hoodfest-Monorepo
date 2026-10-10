//
//  LeaderboardView.swift
//  PitStop-iOS
//

import SwiftUI

struct LeaderboardView: View {

    @Environment(LeaderboardRepository.self) private var repository

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                StatusPill(
                    text: repository.source.label,
                    systemImage: repository.source.symbolName,
                    tint: repository.source == .server ? Theme.racingYellow : Theme.inkMuted
                )
                .frame(maxWidth: .infinity, alignment: .center)

                ForEach(GameID.allCases) { game in
                    BoardCard(board: repository.board(for: game))
                }
            }
            .padding(20)
            .frame(maxWidth: .infinity)
        }
        .pitStopBackground()
        .navigationTitle("Leaderboard")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Theme.slate, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .refreshable { await repository.refresh() }
        .task { await repository.refresh() }
    }
}

struct BoardCard: View {

    let board: Board

    var body: some View {
        VStack(spacing: 0) {
            header

            if board.top.isEmpty {
                Text("No scores yet — be the first.")
                    .font(Theme.body())
                    .foregroundStyle(Theme.inkMuted)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 34)
            } else {
                VStack(spacing: 0) {
                    ForEach(board.top) { entry in
                        EntryRow(entry: entry, isLeader: entry.rank == 1)
                        if entry.id != board.top.last?.id {
                            Divider().overlay(Theme.hairline)
                        }
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 6)
            }

            if !board.recent.isEmpty {
                recent
            }
        }
        .pitStopCard()
    }

    private var header: some View {
        HStack(spacing: 12) {
            Image(systemName: board.game.symbolName)
                .font(.system(size: 24, weight: .bold))

            VStack(alignment: .leading, spacing: 2) {
                Text(board.title)
                    .font(Theme.cardTitle())
                Text(board.tagline)
                    .font(Theme.caption())
                    .foregroundStyle(.white.opacity(0.85))
            }

            Spacer()
        }
        .foregroundStyle(Theme.ink)
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.signalRed)
        .accessibilityElement(children: .combine)
    }

    private var recent: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("JUST PLAYED")
                .font(.system(size: 11, weight: .heavy, design: .rounded))
                .tracking(1.6)
                .foregroundStyle(Theme.inkMuted)

            ForEach(board.recent) { entry in
                HStack {
                    Text(entry.name)
                    Spacer()
                    Text(entry.display).monospacedDigit().fontWeight(.bold)
                }
                .font(Theme.body())
                .foregroundStyle(Theme.ink.opacity(0.82))
                .accessibilityElement(children: .combine)
                .accessibilityLabel("\(entry.name), \(entry.display)")
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.slateHigh)
    }
}

struct EntryRow: View {

    let entry: Entry
    let isLeader: Bool

    var body: some View {
        HStack(spacing: 14) {
            // The leader is marked with a trophy as well as a color, so the
            // position reads without relying on hue.
            HStack(spacing: 4) {
                Text("\(entry.rank)")
                    .font(Theme.scoreRow())
                    .foregroundStyle(isLeader ? Theme.racingYellow : Theme.inkMuted)
                if isLeader {
                    Image(systemName: "trophy.fill")
                        .font(.caption)
                        .foregroundStyle(Theme.racingYellow)
                }
            }
            .frame(minWidth: 48, alignment: .leading)

            Text(entry.name)
                .font(Theme.scoreRow())
                .foregroundStyle(Theme.ink)
                .lineLimit(1)
                .truncationMode(.tail)

            Spacer()

            Text(entry.display)
                .font(Theme.scoreRow())
                .foregroundStyle(Theme.racingYellow)
        }
        .padding(.vertical, 11)
        .padding(.horizontal, 6)
        .background(isLeader ? Theme.racingYellow.opacity(0.12) : .clear)
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityLabel(
            "Position \(entry.rank)\(isLeader ? ", leader" : ""): \(entry.name), \(entry.display)"
        )
    }
}
