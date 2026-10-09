//
//  LeaderboardModels.swift
//  PitStop-iOS
//
//  The wire types shared with the Go server. Field names match the JSON the
//  server emits; see PitStop-Web/README.md for the API contract.
//

import Foundation

/// Which client submitted a score.
enum Platform: String, Codable, Sendable {
    case ios
    case android
    case web
}

/// One score on one leaderboard.
struct Entry: Codable, Identifiable, Hashable, Sendable {
    let id: Int64
    let game: GameID
    let name: String
    let score: Int
    /// The score formatted for display, as the server renders it. Falls back to
    /// the local formatter for entries that never reached the server.
    let display: String
    let platform: Platform
    let createdAt: Date
    /// 1-based position on the board, or 0 when the entry is not ranked in
    /// this particular result.
    var rank: Int

    enum CodingKeys: String, CodingKey {
        case id, game, name, score, display, platform, createdAt, rank
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(Int64.self, forKey: .id)
        game = try container.decode(GameID.self, forKey: .game)
        name = try container.decode(String.self, forKey: .name)
        score = try container.decode(Int.self, forKey: .score)
        platform = try container.decode(Platform.self, forKey: .platform)
        createdAt = try container.decode(Date.self, forKey: .createdAt)
        // Both are omitted by the server when they are zero.
        rank = try container.decodeIfPresent(Int.self, forKey: .rank) ?? 0
        display = try container.decodeIfPresent(String.self, forKey: .display)
            ?? ScoreRules.format(score, for: game)
    }

    init(id: Int64, game: GameID, name: String, score: Int,
         platform: Platform = .ios, createdAt: Date = .now, rank: Int = 0) {
        self.id = id
        self.game = game
        self.name = name
        self.score = score
        self.display = ScoreRules.format(score, for: game)
        self.platform = platform
        self.createdAt = createdAt
        self.rank = rank
    }
}

/// One game's leaderboard: the best scores, plus the handful of most recent
/// ones so a player who did not place still sees their name.
struct Board: Codable, Identifiable, Sendable {
    let game: GameID
    let title: String
    let tagline: String
    var top: [Entry]
    var recent: [Entry]
    var total: Int

    var id: GameID { game }

    /// How many entries a board shows. These match the server's limits, so the
    /// local board and the web display are the same shape.
    static let topCount = 10
    static let recentCount = 3

    static func empty(_ game: GameID) -> Board {
        Board(game: game, title: game.title, tagline: game.tagline,
              top: [], recent: [], total: 0)
    }
}

/// Every game's board, in display order.
struct Boards: Codable, Sendable {
    var boards: [Board]
    var updatedAt: Date?

    static let empty = Boards(boards: GameID.allCases.map(Board.empty), updatedAt: nil)

    func board(for game: GameID) -> Board {
        boards.first { $0.game == game } ?? .empty(game)
    }
}

/// A score being submitted.
struct ScoreSubmission: Encodable, Sendable {
    let game: GameID
    let name: String
    let score: Int
    let platform: Platform
    let detail: [String: Int]?

    init(game: GameID, name: String, score: Int, detail: [String: Int]? = nil) {
        self.game = game
        self.name = name
        self.score = score
        self.platform = .ios
        self.detail = detail
    }
}

/// The server's reply to a submission: the stored entry and the refreshed board.
struct SubmissionResult: Decodable, Sendable {
    let entry: Entry
    let board: Board
}

/// The server's error shape.
struct APIError: Decodable, Error, Sendable {
    struct Payload: Decodable, Sendable {
        let code: String
        let message: String
        let field: String?
    }
    let error: Payload

    var message: String { error.message }
}
