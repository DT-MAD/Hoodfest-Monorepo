//
//  LeaderboardAPI.swift
//  PitStop-iOS
//
//  The HTTP client for the Pit Stop server.
//

import Foundation

/// Why a call did not succeed. Every case carries something a player could be
/// shown, though in practice most of them just mean "fall back to local".
enum APIFailure: Error, LocalizedError {
    case noServerConfigured
    case unreachable
    case rejected(String)
    case unexpectedStatus(Int)
    case malformedResponse

    var errorDescription: String? {
        switch self {
        case .noServerConfigured: "No leaderboard server is configured."
        case .unreachable: "The leaderboard server could not be reached."
        case .rejected(let message): message
        case .unexpectedStatus(let code): "The server replied with status \(code)."
        case .malformedResponse: "The server's reply could not be read."
        }
    }
}

struct LeaderboardAPI {

    private let config: AppConfig
    private let session: URLSession

    init(config: AppConfig, session: URLSession? = nil) {
        self.config = config

        if let session {
            self.session = session
        } else {
            let configuration = URLSessionConfiguration.ephemeral
            // Short timeouts on purpose. At a booth, a server that is not
            // answering within a few seconds is a server the player should
            // not be waiting on — fall back to the local board instead.
            configuration.timeoutIntervalForRequest = 5
            configuration.timeoutIntervalForResource = 10
            configuration.waitsForConnectivity = false
            self.session = URLSession(configuration: configuration)
        }
    }

    private var decoder: JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601WithFractionalSeconds
        return decoder
    }

    /// Fetches every board.
    func boards() async throws -> Boards {
        let (data, response) = try await send(request(path: "/api/leaderboard"))
        try check(response, data: data)

        do {
            return try decoder.decode(Boards.self, from: data)
        } catch {
            throw APIFailure.malformedResponse
        }
    }

    /// Submits a score and returns the stored entry with the rank it earned.
    func submit(_ submission: ScoreSubmission) async throws -> SubmissionResult {
        var request = try request(path: "/api/scores")
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(AppConfig.apiKey, forHTTPHeaderField: "X-API-Key")
        request.httpBody = try JSONEncoder().encode(submission)

        let (data, response) = try await send(request)
        try check(response, data: data)

        do {
            return try decoder.decode(SubmissionResult.self, from: data)
        } catch {
            throw APIFailure.malformedResponse
        }
    }

    /// Whether the server is reachable. Used by the settings screen to tell an
    /// operator whether the address they typed actually works.
    func checkHealth() async -> Bool {
        guard let request = try? request(path: "/healthz"),
              let (_, response) = try? await send(request),
              let http = response as? HTTPURLResponse
        else { return false }

        return http.statusCode == 200
    }

    // MARK: - Plumbing

    private func request(path: String) throws -> URLRequest {
        guard let base = config.baseURL else { throw APIFailure.noServerConfigured }
        return URLRequest(url: base.appendingPathComponent(path))
    }

    private func send(_ request: URLRequest) async throws -> (Data, URLResponse) {
        do {
            return try await session.data(for: request)
        } catch {
            throw APIFailure.unreachable
        }
    }

    /// Turns a non-2xx response into a failure, preferring the server's own
    /// message so a refused name says "Pick a different name" rather than 422.
    private func check(_ response: URLResponse, data: Data) throws {
        guard let http = response as? HTTPURLResponse else {
            throw APIFailure.malformedResponse
        }
        guard (200..<300).contains(http.statusCode) else {
            if let apiError = try? decoder.decode(APIError.self, from: data) {
                throw APIFailure.rejected(apiError.message)
            }
            throw APIFailure.unexpectedStatus(http.statusCode)
        }
    }
}

private extension JSONDecoder.DateDecodingStrategy {
    /// The server emits RFC 3339 timestamps with fractional seconds, which the
    /// plain .iso8601 strategy refuses.
    static let iso8601WithFractionalSeconds = custom { decoder in
        let text = try decoder.singleValueContainer().decode(String.self)

        let withFraction = ISO8601DateFormatter()
        withFraction.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = withFraction.date(from: text) { return date }

        let plain = ISO8601DateFormatter()
        plain.formatOptions = [.withInternetDateTime]
        if let date = plain.date(from: text) { return date }

        throw DecodingError.dataCorruptedError(
            in: try decoder.singleValueContainer(),
            debugDescription: "Unrecognized timestamp: \(text)"
        )
    }
}
