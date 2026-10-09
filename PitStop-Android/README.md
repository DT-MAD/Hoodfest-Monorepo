# Pit Stop — Android

The tablet app for the booth. Jetpack Compose, minSdk 24, no networking or DI
libraries.

## Running it

```sh
./gradlew assembleDebug          # build
./gradlew testDebugUnitTest      # 56 unit tests
```

The app works completely offline. To see live leaderboards, start the server
(see [`../PitStop-Web`](../PitStop-Web)) and point the app at it.

## Pointing it at the booth server

`AppConfig.DEFAULT_BASE_URL` is the compiled-in address, set to `10.0.2.2:8080`
— the host machine as seen from an emulator. The booth laptop's address is not
known until event morning, so the operator can change it on site: **press and
hold the Pit Stop logo on the home screen** to open booth settings, type the
address, and tap *Test connection*.

`AppConfig.API_KEY` must match the server's `API_KEY`. It ships inside the APK,
so treat it as "keeps honest people honest", not as a real secret.

Cleartext HTTP to a LAN address is permitted by
[`network_security_config.xml`](app/src/main/res/xml/network_security_config.xml),
which lists local addresses only — the public internet still requires HTTPS.

### If the emulator cannot reach your server

On macOS the application firewall blocks inbound connections to an unsigned
binary, which is what `go run` produces. The app then falls back to the local
board and logs the reason:

```sh
adb logcat -s PitStop
```

The quickest way around it, which avoids the host network path entirely:

```sh
adb reverse tcp:8080 tcp:8080
```

then set the address to `localhost:8080` in booth settings.

## Layout

```
app/src/main/java/app/recompile/pitstop/
  core/       GameId, ScoreRules, NameValidator, Profanity, RoundClock
  data/       AppConfig, LeaderboardApi, LocalLeaderboardStore,
              LeaderboardRepository, wire models
  ui/         theme and shared components
  feature/    home, reaction, fill, pitstop, shared, settings
  util/       lifecycle and haptics helpers
```

`core/` is the mirror of the Go server's `internal/game`, `internal/profanity`
and `internal/name`, and of the iOS app's `Core/`. All three read
[`spec/score-fixtures.json`](../spec/score-fixtures.json), so they cannot drift
apart silently.

## Things that look odd but are deliberate

**Taps register on touch-down, not touch-up.** `clickable` fires on release,
which would add the player's lift time to every reaction score. `onTouchDown`
uses `detectTapGestures(onPress = …)` instead.

**Timing uses `System.nanoTime`, never `currentTimeMillis`.** It is monotonic: it
cannot jump backwards when the device syncs its clock.

**`android:configChanges` is deliberately not set.** Rotation recreates the
activity and the round survives because it lives in a ViewModel. Handling the
config change would hide the problem rather than solve it.

**No Material You dynamic color.** The template enabled it, which would let the
device wallpaper repaint the booth's branding.

**A round interrupted by backgrounding is cancelled, not scored.** Every screen
uses `OnStopped`, and the view model refuses to turn a round it cannot vouch for
into a number.

**Offline scores stay offline.** Saved to the device and never queued for later
upload — a score appearing on the big screen twenty minutes after the player
walked away is confusing, not useful.

**Names are ASCII-only.** The profanity normalizer strips other scripts, so
accepting them would let a full-width or Cyrillic lookalike bypass the filter.

**HttpURLConnection, not OkHttp or Retrofit.** Two endpoints do not justify the
dependency, and the smaller surface is easier to read.
