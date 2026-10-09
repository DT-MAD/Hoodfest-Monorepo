//
//  LocalLeaderboardStore.swift
//  PitStop-iOS
//
//  The on-device leaderboard.
//
//  Every game must work with no network at all, so a score always lands here
//  first. If the server is unreachable the entry simply stays local — it is not
//  queued for later upload, by design: a score that shows up on the big screen
//  twenty minutes after the player walked away is confusing, not useful.
//

import Foundation

@Observable
final class LocalLeaderboardStore {

    /// How many entries to keep per game. Generous: the board only ever shows
    /// ten, but keeping more means a deleted or beaten score still has history
    /// behind it.
    static let retainedPerGame = 100

    private let fileURL: URL
    private(set) var entries: [Entry] = []

    /// Ids are negative so a local-only entry can never collide with a server
    /// id, which lets the two be merged into one list safely.
    private var nextLocalID: Int64 = -1

    init(filename: String = "leaderboard.json") {
        let directory = (try? FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )) ?? URL.temporaryDirectory

        fileURL = directory.appendingPathComponent(filename)
        load()
    }

    // MARK: - Reading

    /// Every entry for a game, best first and ranked, ties broken by the
    /// earlier entry — the same ordering the server uses.
    private func ranked(_ game: GameID) -> [Entry] {
        entries
            .filter { $0.game == game }
            .sorted {
                $0.score == $1.score ? $0.createdAt < $1.createdAt : $0.score < $1.score
            }
            .enumerated()
            .map { index, entry in
                var ranked = entry
                ranked.rank = index + 1
                return ranked
            }
    }

    /// One game's local board.
    func board(for game: GameID) -> Board {
        let mine = entries.filter { $0.game == game }
        let ranked = ranked(game)

        let recent = mine
            .sorted { $0.createdAt > $1.createdAt }
            .prefix(Board.recentCount)
            .map { entry -> Entry in
                var unranked = entry
                unranked.rank = 0
                return unranked
            }

        return Board(
            game: game,
            title: game.title,
            tagline: game.tagline,
            top: Array(ranked.prefix(Board.topCount)),
            recent: Array(recent),
            total: mine.count
        )
    }

    /// Every game's local board.
    func boards() -> Boards {
        Boards(boards: GameID.allCases.map(board(for:)), updatedAt: .now)
    }

    // MARK: - Writing

    /// Saves a score locally and returns it with the rank it earned.
    @discardableResult
    func add(game: GameID, name: String, score: Int) -> Entry {
        let entry = Entry(
            id: nextLocalID,
            game: game,
            name: name,
            score: score,
            platform: .ios,
            createdAt: .now
        )
        nextLocalID -= 1

        entries.append(entry)
        prune(game)
        save()

        // Report the real rank, which may well be outside the visible top ten.
        return ranked(game).first { $0.id == entry.id } ?? entry
    }

    /// Drops the weakest entries once a game has more than it needs.
    private func prune(_ game: GameID) {
        let mine = entries.filter { $0.game == game }
        guard mine.count > Self.retainedPerGame else { return }

        // Keep the best scores AND the most recent ones. Between them they are
        // everything a board can show, and keeping only the best would quietly
        // erase a player's run the moment a better one came along.
        var keep = Set(
            mine.sorted { $0.score < $1.score }
                .prefix(Self.retainedPerGame)
                .map(\.id)
        )
        keep.formUnion(
            mine.sorted { $0.createdAt > $1.createdAt }
                .prefix(Board.recentCount)
                .map(\.id)
        )
        entries.removeAll { $0.game == game && !keep.contains($0.id) }
    }

    /// Removes every local entry. Used by the settings screen between events.
    func clear() {
        entries.removeAll()
        nextLocalID = -1
        save()
    }

    // MARK: - Persistence

    private func load() {
        guard let data = try? Data(contentsOf: fileURL) else { return }

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        guard let stored = try? decoder.decode([Entry].self, from: data) else {
            // A corrupt file must not stop the app from opening at a booth.
            // Start clean rather than crashing on launch.
            return
        }

        entries = stored
        nextLocalID = (stored.map(\.id).min() ?? 0) - 1
    }

    private func save() {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601

        guard let data = try? encoder.encode(entries) else { return }

        // Atomic so a crash mid-write cannot leave a half-written file that
        // loses the whole day's scores.
        try? data.write(to: fileURL, options: .atomic)
    }
}
