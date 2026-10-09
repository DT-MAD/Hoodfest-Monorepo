//
//  RoundClock.swift
//  PitStop-iOS
//
//  Timing for a round.
//
//  Deliberately built on ContinuousClock, which is monotonic: it cannot jump
//  backwards when the device syncs its time, and it keeps counting while the
//  device is asleep. Date() would be wrong on both counts, and a reaction time
//  measured against a clock that moved is not a score.
//
//  Note for anyone reading this at the booth: a phone's touch hardware, display
//  refresh and OS scheduling all add a few milliseconds of variation. These
//  numbers are a fair competition between players on the same device, not a
//  scientific measure of reflexes.
//

import Foundation

struct RoundClock {

    /// A moment on the monotonic clock.
    struct Instant {
        fileprivate let value: ContinuousClock.Instant
    }

    private let clock = ContinuousClock()

    /// The current monotonic instant.
    func now() -> Instant {
        Instant(value: clock.now)
    }

    /// Whole milliseconds elapsed between two instants, never negative.
    ///
    /// Rounds to nearest rather than truncating: truncation would bias every
    /// score in the same direction, which across a day of play is a visible
    /// thumb on the scale.
    func milliseconds(from start: Instant, to end: Instant) -> Int {
        let duration = end.value - start.value
        let (seconds, attoseconds) = duration.components
        let millis = Double(seconds) * 1000.0 + Double(attoseconds) / 1e15
        return max(0, Int(millis.rounded()))
    }

    /// Whole milliseconds between an instant and now.
    func millisecondsSince(_ start: Instant) -> Int {
        milliseconds(from: start, to: now())
    }
}
