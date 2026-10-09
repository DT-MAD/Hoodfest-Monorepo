package app.recompile.pitstop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Asserts the scoring specification against the shared fixture table. */
class ScoreRulesTest {

    private val fixtures = ScoreFixtures.load()

    @Test
    fun `reaction scores match the shared fixtures`() {
        for (fixture in fixtures.reaction) {
            assertEquals(
                fixture.name,
                fixture.expected,
                ScoreRules.reactionScore(fixture.greenToTapMs),
            )
        }
    }

    @Test
    fun `fill scores match the shared fixtures`() {
        for (fixture in fixtures.fill) {
            val score = ScoreRules.fillScore(fixture.dispensedCents, fixture.goalCents)
            assertEquals(fixture.name, fixture.expected, score)
            assertTrue(
                "${fixture.name}: the score must be an absolute difference",
                score >= 0,
            )
        }
    }

    @Test
    fun `pit stop scores match the shared fixtures`() {
        for (fixture in fixtures.pitstop) {
            assertEquals(
                fixture.name,
                fixture.expected,
                ScoreRules.pitStopScore(fixture.elapsedMs, fixture.misTaps),
            )
        }
    }

    @Test
    fun `plausibility bounds match the shared fixtures`() {
        for (fixture in fixtures.bounds) {
            assertEquals(
                "${fixture.game} ${fixture.score}",
                fixture.inBounds,
                ScoreRules.inBounds(fixture.score, gameOf(fixture.game)),
            )
        }
    }

    @Test
    fun `score formatting matches the shared fixtures`() {
        for (fixture in fixtures.format) {
            assertEquals(
                "${fixture.game} ${fixture.score}",
                fixture.expected,
                ScoreRules.format(fixture.score, gameOf(fixture.game)),
            )
        }
    }

    // ------------------------------------------------------ round generation

    @Test
    fun `fill targets are always a whole number of 50 cent steps within range`() {
        repeat(500) {
            val goal = ScoreRules.randomFillGoalCents()
            assertEquals("goal $goal is not a 50 cent step", 0, goal % ScoreRules.FILL_GOAL_STEP_CENTS)
            assertTrue("goal $goal is out of range", goal in ScoreRules.fillGoalRange)
        }
    }

    @Test
    fun `gas prices stay within range`() {
        repeat(500) {
            assertTrue(ScoreRules.randomFillPriceCents() in ScoreRules.fillPriceRange)
        }
    }

    @Test
    fun `reaction waits stay within the 2 to 5 second range`() {
        repeat(500) {
            assertTrue(ScoreRules.randomReactionWaitMs() in ScoreRules.reactionWaitRange)
        }
    }

    @Test
    fun `the pump dispenses at the specified rate`() {
        // One second of holding is 0.75 gallons, by specification.
        assertEquals(0.75, ScoreRules.gallonsDispensed(1000), 0.0001)
        assertEquals(0.0, ScoreRules.gallonsDispensed(0), 0.0001)
        assertEquals(1.5, ScoreRules.gallonsDispensed(2000), 0.0001)
    }

    @Test
    fun `cost rounds to the nearest cent`() {
        // 0.75 gallons at $2.25 is $1.6875, which rounds to $1.69.
        assertEquals(169, ScoreRules.dispensedCents(0.75, 225))
        assertEquals(0, ScoreRules.dispensedCents(0.0, 225))
    }

    @Test
    fun `each mis-tap costs exactly one second`() {
        val clean = ScoreRules.pitStopScore(2000, 0)
        val one = ScoreRules.pitStopScore(2000, 1)
        assertEquals(ScoreRules.PIT_STOP_MIS_TAP_PENALTY_MS, one - clean)
    }

    @Test
    fun `every game has a title tagline and ordered bounds`() {
        for (game in GameId.displayOrder) {
            assertTrue(game.title.isNotEmpty())
            assertTrue(game.tagline.isNotEmpty())
            assertTrue(game.instruction.isNotEmpty())
            val bounds = ScoreRules.bounds(game)
            assertTrue("${game.wire} has min >= max", bounds.first < bounds.last)
        }
    }

    @Test
    fun `unknown wire values are rejected`() {
        assertEquals(null, GameId.fromWire("tires"))
        assertEquals(null, GameId.fromWire("REACTION"))
        assertEquals(GameId.REACTION, GameId.fromWire("reaction"))
    }
}
