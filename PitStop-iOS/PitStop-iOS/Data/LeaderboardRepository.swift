//
//  LeaderboardRepository.swift
//  PitStop-iOS
//
//  The single place the rest of the app asks about leaderboards.
//
//  It prefers the server and falls back to the device. The fallback is quiet by
//  design: a visitor at a booth should never see a network error. They see the
//  local board and a small "On this iPad" badge, and the booth keeps moving.
//

import Foundation

@Observable
@MainActor
final class LeaderboardRepository {

    /// Where the displayed boards came from.
    enum Source {
        case server
        case device

        var label: String {
            switch self {
            case .server: "Live leaderboard"
            case .device: "On this iPad"
            }
        }

        var symbolName: String {
            switch self {
            case .server: "antenna.radiowaves.left.and.right"
            case .device: "ipad"
            }
        }
    }

    private let config: AppConfig
    private let local: LocalLeaderboardStore
    private let api: LeaderboardAPI

    private(set) var boards: Boards = .empty
    private(set) var source: Source = .device
    private(set) var isRefreshing = false

    init(config: AppConfig, local: LocalLeaderboardStore) {
        self.config = config
        self.local = local
        self.api = LeaderboardAPI(config: config)
        self.boards = local.boards()
    }

    // MARK: - Reading

    /// Refreshes the boards, preferring the server.
    func refresh() async {
        guard !isRefreshing else { return }
        isRefreshing = true
        defer { isRefreshing = false }

        do {
            boards = try await api.boards()
            source = .server
        } catch {
            boards = local.boards()
            source = .device
        }
    }

    func board(for game: GameID) -> Board {
        boards.board(for: game)
    }

    // MARK: - Writing

    /// What happened to a submitted score.
    struct Outcome {
        let entry: Entry
        let source: Source

        /// True when the score reached the leaderboard on the big screen.
        var reachedServer: Bool { source == .server }
    }

    /// Submits a score.
    ///
    /// It is always written to the device first, so a score is never lost to a
    /// network failure, and then sent onward if the server can be reached.
    func submit(game: GameID, name: String, score: Int, detail: [String: Int]? = nil) async -> Outcome {
        let localEntry = local.add(game: game, name: name, score: score)

        do {
            let result = try await api.submit(
                ScoreSubmission(game: game, name: name, score: score, detail: detail)
            )
            boards = (try? await api.boards()) ?? boards
            source = .server
            return Outcome(entry: result.entry, source: .server)
        } catch {
            boards = local.boards()
            source = .device
            return Outcome(entry: localEntry, source: .device)
        }
    }
}
