//
//  AppConfig.swift
//  PitStop-iOS
//
//  Where the app looks for the leaderboard server.
//
//  The booth server usually runs on a laptop on the venue's network, and its
//  address is not known until the morning of the event. So the compiled-in
//  default is only a starting point: the operator can change it on site from
//  the settings screen, reached by a long press on the home screen logo.
//

import Foundation

@Observable
final class AppConfig {

    /// Where the server lives if nobody has said otherwise. Change this to the
    /// booth laptop's address before the event, or set it on site in Settings.
    static let defaultBaseURL = "http://localhost:8080"

    /// The shared key the server expects in X-API-Key.
    ///
    /// This value ships inside the app binary, so treat it as "keeps honest
    /// people honest", not as a real secret. It must match the server's
    /// API_KEY, and must NOT be the server's ADMIN_KEY.
    static let apiKey = "change-me-app-key"

    private enum Keys {
        static let baseURL = "pitstop.baseURL"
    }

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.baseURLString = defaults.string(forKey: Keys.baseURL) ?? Self.defaultBaseURL
    }

    /// The address in use, as typed. Setting it persists across launches.
    var baseURLString: String {
        didSet { defaults.set(baseURLString, forKey: Keys.baseURL) }
    }

    /// The address as a URL, or nil if what was typed is not usable.
    var baseURL: URL? {
        Self.normalizedURL(from: baseURLString)
    }

    var isUsingDefaultAddress: Bool {
        baseURLString == Self.defaultBaseURL
    }

    func resetToDefault() {
        baseURLString = Self.defaultBaseURL
    }

    /// Accepts what an operator would actually type — "192.168.1.50:8080",
    /// with or without a scheme, with or without a trailing slash.
    static func normalizedURL(from raw: String) -> URL? {
        var text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }

        if !text.contains("://") {
            text = "http://" + text
        }
        while text.hasSuffix("/") {
            text.removeLast()
        }

        guard let url = URL(string: text), let host = url.host(), !host.isEmpty else {
            return nil
        }
        return url
    }
}
