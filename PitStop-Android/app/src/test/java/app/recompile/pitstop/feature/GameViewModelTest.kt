package app.recompile.pitstop.feature

import app.recompile.pitstop.core.ScoreRules
import app.recompile.pitstop.feature.fill.FillViewModel
import app.recompile.pitstop.feature.pitstop.PitStopViewModel
import app.recompile.pitstop.feature.pitstop.Tire
import app.recompile.pitstop.feature.reaction.ReactionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The input-protection and lifecycle guarantees: a round can never be scored
 * twice, a tire can never register twice, and a round whose timing was
 * interrupted is never turned into a score.
 *
 * Mirrors PitStop-iOS's GameModelTests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        // viewModelScope runs on Dispatchers.Main, which does not exist in a
        // plain JVM unit test.
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ------------------------------------------------------ Reaction Lights

    @Test
    fun `a fresh reaction round is idle`() {
        val model = ReactionViewModel()
        assertEquals(ReactionViewModel.Phase.Ready, model.phase.value)
        assertNull(model.score)
        assertTrue(!model.isRoundActive)
    }

    @Test
    fun `starting shows the red light`() {
        val model = ReactionViewModel()
        model.start()
        assertEquals(ReactionViewModel.Phase.Waiting, model.phase.value)
        assertTrue(model.isRoundActive)
    }

    @Test
    fun `tapping on red is a false start and does not score`() {
        val model = ReactionViewModel()
        model.start()
        model.tap()

        assertEquals(ReactionViewModel.Phase.FalseStart, model.phase.value)
        assertNull(model.score)
    }

    @Test
    fun `a second start cannot restart an armed round`() {
        val model = ReactionViewModel()
        model.start()
        model.start()
        model.start()

        // Still a single armed round, not three racing timers.
        assertEquals(ReactionViewModel.Phase.Waiting, model.phase.value)
    }

    @Test
    fun `taps outside a round do nothing`() {
        val model = ReactionViewModel()

        model.tap()
        assertEquals(ReactionViewModel.Phase.Ready, model.phase.value)

        model.start()
        model.tap()
        assertEquals(ReactionViewModel.Phase.FalseStart, model.phase.value)

        model.tap()
        assertEquals(
            "a tap after the round ended changed the phase",
            ReactionViewModel.Phase.FalseStart,
            model.phase.value,
        )
    }

    @Test
    fun `backgrounding mid-round cancels it rather than scoring it`() {
        val model = ReactionViewModel()
        model.start()

        model.onStopped()

        assertEquals(ReactionViewModel.Phase.Cancelled, model.phase.value)
        assertNull(model.score)
    }

    @Test
    fun `backgrounding outside a round changes nothing`() {
        val model = ReactionViewModel()
        model.onStopped()
        assertEquals(ReactionViewModel.Phase.Ready, model.phase.value)
    }

    @Test
    fun `reset returns the round to idle`() {
        val model = ReactionViewModel()
        model.start()
        model.tap()
        model.reset()

        assertEquals(ReactionViewModel.Phase.Ready, model.phase.value)
        assertNull(model.score)
    }

    @Test
    fun `the light goes green on its own`() = runTest(dispatcher) {
        val model = ReactionViewModel()
        model.start()

        // The wait is 2 to 5 seconds by specification; skip past the longest.
        advanceTimeBy(ScoreRules.reactionWaitRange.last + 100L)

        assertEquals(ReactionViewModel.Phase.Go, model.phase.value)
    }

    @Test
    fun `a tap too soon after green is refused as a false start`() = runTest(dispatcher) {
        val model = ReactionViewModel()
        model.start()
        advanceTimeBy(ScoreRules.reactionWaitRange.last + 100L)

        // The virtual clock jumped, but RoundClock is real and monotonic: no
        // wall time has passed, so this tap is far under the 50ms floor.
        model.tap()

        assertEquals(ReactionViewModel.Phase.FalseStart, model.phase.value)
        assertNull(model.score)
    }

    // ------------------------------------------------------------ Fill It Up

    @Test
    fun `preparing a fill round picks a legal target and price`() {
        val model = FillViewModel()
        model.prepareRound()
        val state = model.state.value

        assertEquals(FillViewModel.Phase.Ready, state.phase)
        assertEquals(0, state.goalCents % ScoreRules.FILL_GOAL_STEP_CENTS)
        assertTrue(state.goalCents in ScoreRules.fillGoalRange)
        assertTrue(state.pricePerGallonCents in ScoreRules.fillPriceRange)
        assertEquals(0, state.dispensedCents)
    }

    @Test
    fun `pumping only starts from a prepared round`() {
        val model = FillViewModel()
        model.prepareRound()

        model.beginPumping()
        assertEquals(FillViewModel.Phase.Pumping, model.state.value.phase)

        // A second press while already pumping must not restart the clock.
        model.beginPumping()
        assertEquals(FillViewModel.Phase.Pumping, model.state.value.phase)
    }

    @Test
    fun `releasing without pumping does nothing`() {
        val model = FillViewModel()
        model.prepareRound()

        model.endPumping()
        assertEquals(FillViewModel.Phase.Ready, model.state.value.phase)
        assertNull(model.state.value.score)
    }

    @Test
    fun `releasing ends the round with a score in range and does not score twice`() {
        val model = FillViewModel()
        model.prepareRound()
        model.beginPumping()
        model.endPumping()

        val score = model.state.value.score
        assertNotNull(score)
        assertTrue(ScoreRules.inBounds(score!!, app.recompile.pitstop.core.GameId.FILL))

        model.endPumping()
        assertEquals(score, model.state.value.score)
    }

    @Test
    fun `backgrounding mid-pump cancels the round`() {
        val model = FillViewModel()
        model.prepareRound()
        model.beginPumping()

        model.onStopped()

        assertEquals(FillViewModel.Phase.Cancelled, model.state.value.phase)
        assertNull(model.state.value.score)
    }

    @Test
    fun `the gauge never reads past full`() {
        val model = FillViewModel()
        model.prepareRound()
        val state = model.state.value

        assertTrue(state.gaugeFraction in 0f..1f)
        assertTrue(
            "the target must sit somewhere a player can aim at",
            state.goalFraction > 0f && state.goalFraction < 1f,
        )
    }

    // ----------------------------------------------------- Perfect Pit Stop

    @Test
    fun `a fresh pit stop round has four tires to change`() {
        val model = PitStopViewModel()
        assertEquals(PitStopViewModel.Phase.Ready, model.state.value.phase)
        assertEquals(ScoreRules.PIT_STOP_TIRE_COUNT, model.state.value.remainingCount)
        assertEquals(0, model.state.value.misTaps)
    }

    @Test
    fun `taps before start are ignored`() {
        val model = PitStopViewModel()

        model.tap(Tire.FRONT_LEFT)
        model.tapMissed()

        assertEquals(ScoreRules.PIT_STOP_TIRE_COUNT, model.state.value.remainingCount)
        assertEquals(0, model.state.value.misTaps)
    }

    @Test
    fun `a tire registers exactly once however fast it is tapped`() {
        val model = PitStopViewModel()
        model.start()

        model.tap(Tire.FRONT_LEFT)
        assertTrue(model.state.value.isChanged(Tire.FRONT_LEFT))
        assertEquals(3, model.state.value.remainingCount)

        // Drumming on the same tire costs penalties, it does not finish faster.
        model.tap(Tire.FRONT_LEFT)
        model.tap(Tire.FRONT_LEFT)

        assertEquals(3, model.state.value.remainingCount)
        assertEquals(
            "re-tapping a changed tire should count against the player",
            2,
            model.state.value.misTaps,
        )
    }

    @Test
    fun `missing the tires costs a second each`() {
        val model = PitStopViewModel()
        model.start()

        model.tapMissed()
        model.tapMissed()

        assertEquals(2, model.state.value.misTaps)
    }

    @Test
    fun `the fourth tire ends the round`() {
        val model = PitStopViewModel()
        model.start()

        Tire.entries.forEach(model::tap)

        assertEquals(0, model.state.value.remainingCount)
        // Four synthetic taps land well under the 300ms floor, so in a test this
        // is the cancelled path. Either way the round must not still be running.
        assertTrue(model.state.value.phase != PitStopViewModel.Phase.Running)
    }

    @Test
    fun `input is dead once the round ends`() {
        val model = PitStopViewModel()
        model.start()
        Tire.entries.forEach(model::tap)

        val misTapsAtEnd = model.state.value.misTaps
        model.tap(Tire.FRONT_LEFT)
        model.tapMissed()

        assertEquals(
            "taps after the round ended were still counted",
            misTapsAtEnd,
            model.state.value.misTaps,
        )
    }

    @Test
    fun `backgrounding mid pit stop cancels it`() {
        val model = PitStopViewModel()
        model.start()
        model.tap(Tire.FRONT_LEFT)

        model.onStopped()

        assertEquals(PitStopViewModel.Phase.Cancelled, model.state.value.phase)
        assertNull(model.state.value.score)
    }

    @Test
    fun `reset clears the tires and penalties`() {
        val model = PitStopViewModel()
        model.start()
        model.tap(Tire.FRONT_LEFT)
        model.tapMissed()
        model.reset()

        assertEquals(PitStopViewModel.Phase.Ready, model.state.value.phase)
        assertEquals(ScoreRules.PIT_STOP_TIRE_COUNT, model.state.value.remainingCount)
        assertEquals(0, model.state.value.misTaps)
    }
}
