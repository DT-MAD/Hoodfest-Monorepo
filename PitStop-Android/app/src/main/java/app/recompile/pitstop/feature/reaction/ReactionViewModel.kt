package app.recompile.pitstop.feature.reaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.recompile.pitstop.core.GameId
import app.recompile.pitstop.core.RoundClock
import app.recompile.pitstop.core.ScoreRules
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Reaction Lights.
 *
 * The player waits on a red light and taps the instant it turns green. The score
 * is the milliseconds between those two events, measured on a monotonic clock
 * and registered on touch-down.
 *
 * Lives in a ViewModel so a rotation mid-round does not restart the timer.
 */
class ReactionViewModel : ViewModel() {

    /**
     * The round's state. Every transition below is guarded, so a double tap
     * cannot start two timers and a late tap cannot score a finished round.
     */
    sealed interface Phase {
        /** Before the first tap. */
        data object Ready : Phase
        /** Red light showing; tapping now is a false start. */
        data object Waiting : Phase
        /** Green light showing; the clock is running. */
        data object Go : Phase
        /** A valid reaction time. */
        data class Finished(val score: Int) : Phase
        /** Tapped too early. */
        data object FalseStart : Phase
        /** The round was interrupted and cannot be scored honestly. */
        data object Cancelled : Phase
    }

    private val clock = RoundClock()
    private var greenAt: RoundClock.Instant? = null
    private var waitJob: Job? = null

    private val _phase = MutableStateFlow<Phase>(Phase.Ready)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    val score: Int? get() = (_phase.value as? Phase.Finished)?.score

    val isRoundActive: Boolean
        get() = _phase.value == Phase.Waiting || _phase.value == Phase.Go

    /**
     * Arms the round: shows the red light, then turns it green after a random
     * delay of two to five seconds.
     */
    fun start() {
        // Guard against a second tap on Start before the UI has caught up.
        if (isRoundActive) return

        waitJob?.cancel()
        greenAt = null
        _phase.value = Phase.Waiting

        val waitMs = ScoreRules.randomReactionWaitMs()

        waitJob = viewModelScope.launch {
            delay(waitMs.toLong())
            if (_phase.value != Phase.Waiting) return@launch

            greenAt = clock.now()
            _phase.value = Phase.Go
        }
    }

    /** Registers a touch on the play area. Called on touch-DOWN. */
    fun tap() {
        when (_phase.value) {
            Phase.Waiting -> {
                // Jumped the light.
                waitJob?.cancel()
                waitJob = null
                _phase.value = Phase.FalseStart
            }

            Phase.Go -> {
                val startedAt = greenAt
                if (startedAt == null) {
                    // The clock was never started; refuse to invent a score.
                    _phase.value = Phase.Cancelled
                    return
                }

                val elapsed = clock.millisecondsSince(startedAt)
                greenAt = null

                // A reaction faster than the plausible floor is not a reaction,
                // it is a tap that was already on its way down.
                _phase.value = if (ScoreRules.inBounds(elapsed, GameId.REACTION)) {
                    Phase.Finished(ScoreRules.reactionScore(elapsed))
                } else {
                    Phase.FalseStart
                }
            }

            // Taps outside a round do nothing.
            else -> Unit
        }
    }

    /** Clears everything for another go. */
    fun reset() {
        waitJob?.cancel()
        waitJob = null
        greenAt = null
        _phase.value = Phase.Ready
    }

    /**
     * Invalidates an in-flight round when the app is backgrounded, a call
     * arrives, or the activity otherwise stops.
     *
     * A round whose timing cannot be vouched for must not produce a score.
     */
    fun onStopped() {
        if (!isRoundActive) return

        waitJob?.cancel()
        waitJob = null
        greenAt = null
        _phase.value = Phase.Cancelled
    }

    override fun onCleared() {
        waitJob?.cancel()
    }
}
