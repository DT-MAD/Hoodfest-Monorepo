# Pit Stop — iOS

The iPad app for the booth. SwiftUI, iOS 27, no third-party dependencies.

## Running it

Open `PitStop-iOS.xcodeproj` and run on an iPad simulator, or:

```sh
xcodebuild test -scheme PitStop-iOS \
  -destination 'platform=iOS Simulator,name=iPad Air 11-inch (M4)'
```

The app works completely offline. To see live leaderboards, start the server
(see [`../PitStop-Web`](../PitStop-Web)) and point the app at it.

## Pointing it at the booth server

`AppConfig.defaultBaseURL` is the compiled-in address. The booth laptop's
address usually is not known until event morning, so the operator can change it
on site: **press and hold the Pit Stop logo on the home screen** for a second to
open booth settings, type the address, and tap *Test connection*.

`AppConfig.apiKey` must match the server's `API_KEY`. It ships inside the app
binary, so treat it as "keeps honest people honest", not as a real secret.

Cleartext HTTP to a LAN address is permitted by `NSAllowsLocalNetworking` in
[`Info.plist`](Info.plist). That covers `.local` names and private IPv4 ranges
only — the public internet still requires HTTPS.

## Layout

```
PitStop-iOS/
  Core/       GameID, ScoreRules, NameValidator, Profanity, RoundClock
  Design/     Theme, Components, Haptics
  Data/       AppConfig, LeaderboardAPI, LocalLeaderboardStore,
              LeaderboardRepository, wire models
  Features/   Home, Reaction, Fill, PitStop, Shared, Settings
PitStop-iOSTests/
```

`Core/` is the mirror of the Go server's `internal/game`, `internal/profanity`
and `internal/name`. Both read [`spec/score-fixtures.json`](../spec/score-fixtures.json),
so the two cannot drift apart silently.

## Things that look odd but are deliberate

**Taps register on touch-down, not touch-up.** `onTapGesture` fires when the
finger *lifts*, which would add the player's lift time to every reaction score.
`TouchDownArea` uses a zero-distance drag gesture instead, which fires the
instant the finger lands.

**Timing uses `ContinuousClock`, never `Date`.** It is monotonic: it cannot jump
backwards when the device syncs its clock. A reaction time measured against a
clock that moved is not a score.

**A round interrupted by backgrounding is cancelled, not scored.** Every game
model watches `scenePhase` and refuses to turn a round it cannot vouch for into
a number.

**Scores below the plausibility floor are refused.** A tap within 50ms of the
green light is not a reaction, it is a finger that was already on its way down.

**Offline scores stay offline.** They are saved to the device and never queued
for later upload — a score appearing on the big screen twenty minutes after the
player walked away is confusing, not useful.

**Names are ASCII-only.** The profanity normalizer strips other scripts, so
accepting them would let a full-width or Cyrillic lookalike bypass the filter.
