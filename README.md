# Pit Stop

A racing-themed mini-game collection for the Dixie Tech car-show booth, built as a
showcase for the Mobile App Development program.

Visitors walk up to a tablet, play a 30–60 second challenge, and put their name on a
shared leaderboard shown on a screen at the booth.

## The three games

| Game | Objective | Score (lower always wins) |
| ---- | --------- | ------------------------- |
| **Reaction Lights** | Tap the moment the light turns green | milliseconds from green to touch |
| **Fill It Up** | Hold to pump exactly to the dollar target | cents away from the target |
| **Perfect Pit Stop** | Tap all four tires as fast as you can | elapsed ms, +1000 ms per mis-tap |

The authoritative rules — timings, ranges, and random distributions — live in
[`PROJECT.md`](PROJECT.md). All three codebases implement that one specification and
assert the same fixture table in their unit tests, so a score means the same thing on
every platform.

## Projects

| Directory | What it is | Build |
| --------- | ---------- | ----- |
| [`PitStop-Web/`](PitStop-Web) | Go REST API + live leaderboard display | `make run` |
| [`PitStop-iOS/`](PitStop-iOS) | SwiftUI app, iPad-first | open in Xcode |
| [`PitStop-Android/`](PitStop-Android) | Jetpack Compose app, tablet-first | `./gradlew assembleDebug` |

Build order is backend first: the apps consume the API contract the server defines.

## Design language

| Token | Hex | Use |
| ----- | --- | --- |
| Racing Yellow | `#F7C843` | accents, the leading position |
| Signal Red | `#E7473F` | headers, stop states |
| Asphalt | `#15191F` | background |
| Slate | `#252B34` | cards and surfaces |
| White | `#FFFFFF` | body text |

Large tap targets, bold tabular numerals, high outdoor contrast, and state never
communicated by color alone.

## Shared behavior

Both apps work fully offline, time rounds with a monotonic clock, cancel any round
that gets interrupted, and require no accounts, permissions, or analytics.
