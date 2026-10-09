//
//  PitStop_iOSApp.swift
//  PitStop-iOS
//
//  Created by Lukas Simonson on 10/9/26.
//

import SwiftUI

@main
struct PitStop_iOSApp: App {

    @State private var config: AppConfig
    @State private var localStore: LocalLeaderboardStore
    @State private var repository: LeaderboardRepository

    init() {
        let config = AppConfig()
        let localStore = LocalLeaderboardStore()

        _config = State(initialValue: config)
        _localStore = State(initialValue: localStore)
        _repository = State(initialValue: LeaderboardRepository(config: config, local: localStore))
    }

    var body: some Scene {
        WindowGroup {
            HomeView()
                .environment(config)
                .environment(localStore)
                .environment(repository)
                // The booth runs this in a dark, high-contrast palette by
                // design; it is not a light-mode app with a dark variant.
                .preferredColorScheme(.dark)
                .tint(Theme.racingYellow)
        }
    }
}
