package app.recompile.pitstop.core

/**
 * Timing for a round.
 *
 * Deliberately built on [System.nanoTime], which is monotonic: it cannot jump
 * backwards when the device syncs its clock. `System.currentTimeMillis` would be
 * wrong on exactly that count, and a reaction time measured against a clock that
 * moved is not a score.
 *
 * Note for anyone reading this at the booth: a tablet's touch hardware, display
 * refresh and OS scheduling all add a few milliseconds of variation. These
 * numbers are a fair competition between players on the same device, not a
 * scientific measure of reflexes.
 */
class RoundClock {

    /** A moment on the monotonic clock. */
    @JvmInline
    value class Instant internal constructor(internal val nanos: Long)

    /** The current monotonic instant. */
    fun now(): Instant = Instant(System.nanoTime())

    /**
     * Whole milliseconds elapsed between two instants, never negative.
     *
     * Rounds to nearest rather than truncating: truncation would bias every
     * score in the same direction, which across a day of play is a visible
     * thumb on the scale.
     */
    fun milliseconds(from: Instant, to: Instant): Int {
        val elapsedNanos = to.nanos - from.nanos
        if (elapsedNanos <= 0) return 0
        return ((elapsedNanos + 500_000L) / 1_000_000L).toInt()
    }

    /** Whole milliseconds between an instant and now. */
    fun millisecondsSince(start: Instant): Int = milliseconds(start, now())
}
