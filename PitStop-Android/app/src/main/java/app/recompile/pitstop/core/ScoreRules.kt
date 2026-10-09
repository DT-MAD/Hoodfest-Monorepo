package app.recompile.pitstop.core

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The authoritative Pit Stop scoring specification, mirrored from
 * PitStop-Web/internal/game/game.go and PitStop-iOS's ScoreRules.swift.
 *
 * Every constant here is asserted against spec/score-fixtures.json, the same
 * file the Go and Swift suites read. If you change a number, change it in all
 * three places or the parity tests will fail.
 *
 * The governing rule: for all three games, a LOWER score is better.
 */
object ScoreRules {

    // ---------------------------------------------------------------- bounds
    //
    // A score outside these is not a real round. The server rejects them, so
    // the app must not produce them either.

    val reactionBounds = 50..10_000          // milliseconds
    val fillBounds = 0..100_000              // cents off target
    val pitStopBounds = 300..120_000         // milliseconds

    fun bounds(game: GameId): IntRange = when (game) {
        GameId.REACTION -> reactionBounds
        GameId.FILL -> fillBounds
        GameId.PITSTOP -> pitStopBounds
    }

    fun inBounds(score: Int, game: GameId): Boolean = score in bounds(game)

    // -------------------------------------------------------- Reaction Lights

    /** The red light holds for a uniformly random delay in this range. */
    val reactionWaitRange = 2_000..5_000

    fun randomReactionWaitMs(random: Random = Random): Int =
        random.nextInt(reactionWaitRange.first, reactionWaitRange.last + 1)

    /**
     * Canonical score: whole milliseconds between the green light and the tap.
     * Both timestamps must come from the same monotonic clock.
     */
    fun reactionScore(greenToTapMs: Int): Int = greenToTapMs

    // ------------------------------------------------------------- Fill It Up

    val fillGoalRange = 500..2_000           // cents
    const val FILL_GOAL_STEP_CENTS = 50
    val fillPriceRange = 200..500            // cents per gallon

    /**
     * The pump dispenses at a fixed rate, accumulated from monotonic hold time
     * so the result does not depend on frame rate.
     */
    const val FILL_MILLI_GALLONS_PER_SECOND = 750

    /** The gauge hard-stops once the player has pumped this multiple of the target. */
    const val FILL_OVERFILL_LIMIT_MULTIPLE = 2

    /** A random dollar target, in cents, always a multiple of 50. */
    fun randomFillGoalCents(random: Random = Random): Int {
        val lowestStep = fillGoalRange.first / FILL_GOAL_STEP_CENTS
        val highestStep = fillGoalRange.last / FILL_GOAL_STEP_CENTS
        return random.nextInt(lowestStep, highestStep + 1) * FILL_GOAL_STEP_CENTS
    }

    /** A random gas price, in cents per gallon. */
    fun randomFillPriceCents(random: Random = Random): Int =
        random.nextInt(fillPriceRange.first, fillPriceRange.last + 1)

    /** Gallons dispensed after holding the pump for [elapsedMs]. */
    fun gallonsDispensed(elapsedMs: Int): Double =
        elapsedMs / 1000.0 * FILL_MILLI_GALLONS_PER_SECOND / 1000.0

    /** The cost of [gallons] at [pricePerGallonCents], rounded to the nearest cent. */
    fun dispensedCents(gallons: Double, pricePerGallonCents: Int): Int =
        (gallons * pricePerGallonCents).roundToInt()

    /**
     * Canonical score: how many cents the player ended up from the target,
     * in either direction.
     */
    fun fillScore(dispensedCents: Int, goalCents: Int): Int = abs(dispensedCents - goalCents)

    // ------------------------------------------------------ Perfect Pit Stop

    const val PIT_STOP_TIRE_COUNT = 4

    /** Every tap that misses an un-changed tire costs a full second. */
    const val PIT_STOP_MIS_TAP_PENALTY_MS = 1_000

    /**
     * Canonical score: elapsed milliseconds from the start tap to the fourth
     * tire, plus a one second penalty per mis-tap.
     */
    fun pitStopScore(elapsedMs: Int, misTaps: Int): Int =
        elapsedMs + misTaps * PIT_STOP_MIS_TAP_PENALTY_MS

    // --------------------------------------------------------------- display

    /**
     * Renders a canonical score the way it appears on a leaderboard. The Go
     * server and the iOS app format these identically.
     */
    fun format(score: Int, game: GameId): String = when (game) {
        GameId.REACTION -> "$score ms"
        GameId.FILL -> "$%d.%02d off".format(score / 100, score % 100)
        GameId.PITSTOP -> "%d.%03ds".format(score / 1000, score % 1000)
    }
}
