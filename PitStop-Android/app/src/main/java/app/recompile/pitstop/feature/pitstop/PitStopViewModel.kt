package app.recompile.pitstop.feature.pitstop

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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The four corners of the car. */
enum class Tire(val label: String) {
    FRONT_LEFT("Front left"),
    FRONT_RIGHT("Front right"),
    REAR_LEFT("Rear left"),
    REAR_RIGHT("Rear right"),
}

/**
 * Perfect Pit Stop.
 *
 * Four tires, tapped as fast as possible. Each tire counts once; anything else
 * tapped in the play area costs a second.
 */
class PitStopViewModel : ViewModel() {

    sealed interface Phase {
        data object Ready : Phase
        data object Running : Phase
        data class Finished(val score: Int) : Phase
        data object Cancelled : Phase
    }

    data class RoundState(
        val phase: Phase = Phase.Ready,
        /**
         * Tires already changed. A Set, so a tire cannot register twice however
         * fast the player drums on it.
         */
        val changed: Set<Tire> = emptySet(),
        val misTaps: Int = 0,
        /** Elapsed time, updated for the on-screen clock while running. */
        val elapsedMs: Int = 0,
    ) {
        val score: Int? get() = (phase as? Phase.Finished)?.score

        val remainingCount: Int get() = ScoreRules.PIT_STOP_TIRE_COUNT - changed.size

        /** The running total, including penalties accrued so far. */
        val runningScoreText: String
            get() = ScoreRules.format(
                ScoreRules.pitStopScore(elapsedMs, misTaps),
                GameId.PITSTOP,
            )

        fun isChanged(tire: Tire): Boolean = tire in changed
    }

    private val clock = RoundClock()
    private var startedAt: RoundClock.Instant? = null
    private var tickJob: Job? = null

    private val _state = MutableStateFlow(RoundState())
    val state: StateFlow<RoundState> = _state.asStateFlow()

    val isRoundActive: Boolean get() = _state.value.phase == Phase.Running

    fun start() {
        if (_state.value.phase == Phase.Running) return

        startedAt = clock.now()
        _state.value = RoundState(phase = Phase.Running)

        tickJob = viewModelScope.launch {
            while (isActive) {
                delay(33)
                updateElapsed()
            }
        }
    }

    /** A tap on a tire. Called on touch-DOWN. */
    fun tap(tire: Tire) {
        val current = _state.value
        if (current.phase != Phase.Running) return

        // Already changed: this is a wasted tap, and counts against them, the
        // same as tapping the bodywork.
        if (current.isChanged(tire)) {
            registerMisTap()
            return
        }

        val changed = current.changed + tire
        _state.value = current.copy(changed = changed)

        if (changed.size == ScoreRules.PIT_STOP_TIRE_COUNT) finish()
    }

    /** A tap in the play area that did not land on an un-changed tire. */
    fun tapMissed() {
        if (_state.value.phase != Phase.Running) return
        registerMisTap()
    }

    private fun registerMisTap() {
        _state.update { it.copy(misTaps = it.misTaps + 1) }
        updateElapsed()
    }

    private fun finish() {
        val current = _state.value
        val began = startedAt
        if (current.phase != Phase.Running || began == null) return

        tickJob?.cancel()
        tickJob = null

        val elapsed = clock.millisecondsSince(began)
        startedAt = null

        val raw = ScoreRules.pitStopScore(elapsed, current.misTaps)

        // A run faster than the plausible floor is not a pit stop, and the
        // server would refuse it anyway.
        _state.value = current.copy(
            phase = if (ScoreRules.inBounds(raw, GameId.PITSTOP)) Phase.Finished(raw) else Phase.Cancelled,
            elapsedMs = elapsed,
        )
    }

    private fun updateElapsed() {
        val began = startedAt ?: return
        if (_state.value.phase != Phase.Running) return
        _state.update { it.copy(elapsedMs = clock.millisecondsSince(began)) }
    }

    fun reset() {
        tickJob?.cancel()
        tickJob = null
        startedAt = null
        _state.value = RoundState()
    }

    fun onStopped() {
        if (!isRoundActive) return

        tickJob?.cancel()
        tickJob = null
        startedAt = null
        _state.update { it.copy(phase = Phase.Cancelled) }
    }

    override fun onCleared() {
        tickJob?.cancel()
    }
}
