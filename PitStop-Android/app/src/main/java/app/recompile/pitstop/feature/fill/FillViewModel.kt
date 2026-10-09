package app.recompile.pitstop.feature.fill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.recompile.pitstop.core.RoundClock
import app.recompile.pitstop.core.ScoreRules
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Fill It Up.
 *
 * The player holds the pump and lets go as close to a dollar target as they can.
 * The amount dispensed is derived from monotonic hold time, not accumulated per
 * frame, so the result does not depend on frame rate or on the display dropping
 * a frame at the wrong moment.
 */
class FillViewModel : ViewModel() {

    sealed interface Phase {
        data object Ready : Phase
        data object Pumping : Phase
        data class Finished(val score: Int) : Phase
        data object Cancelled : Phase
    }

    data class RoundState(
        val phase: Phase = Phase.Ready,
        /** The dollar target for this round, in cents. */
        val goalCents: Int = 0,
        /** The price of gas this round, in cents per gallon. */
        val pricePerGallonCents: Int = 0,
        /** What the pump reads right now, in cents. */
        val dispensedCents: Int = 0,
    ) {
        val goalText: String get() = money(goalCents)
        val dispensedText: String get() = money(dispensedCents)
        val priceText: String get() = "${money(pricePerGallonCents)} / gal"

        val gallonsText: String
            get() = if (pricePerGallonCents <= 0) "0.00 gal"
            else "%.2f gal".format(dispensedCents.toDouble() / pricePerGallonCents)

        /** How full the gauge looks, 0 to 1, clamped at the overfill limit. */
        val gaugeFraction: Float
            get() {
                if (goalCents <= 0) return 0f
                val limit = goalCents * ScoreRules.FILL_OVERFILL_LIMIT_MULTIPLE
                return min(1f, dispensedCents.toFloat() / limit)
            }

        /** Where the target sits on the gauge, so the player can aim at it. */
        val goalFraction: Float get() = 1f / ScoreRules.FILL_OVERFILL_LIMIT_MULTIPLE

        /**
         * True once the player has gone past the target — shown as a label, not
         * only as a color.
         */
        val isOverTarget: Boolean get() = dispensedCents > goalCents

        val score: Int? get() = (phase as? Phase.Finished)?.score

        companion object {
            fun money(cents: Int): String = "$%d.%02d".format(cents / 100, cents % 100)
        }
    }

    private val clock = RoundClock()
    private var holdStart: RoundClock.Instant? = null
    private var tickJob: Job? = null

    private val _state = MutableStateFlow(RoundState())
    val state: StateFlow<RoundState> = _state.asStateFlow()

    val isRoundActive: Boolean get() = _state.value.phase == Phase.Pumping

    /** Picks a fresh target and gas price. */
    fun prepareRound() {
        tickJob?.cancel()
        tickJob = null
        holdStart = null
        _state.value = RoundState(
            phase = Phase.Ready,
            goalCents = ScoreRules.randomFillGoalCents(),
            pricePerGallonCents = ScoreRules.randomFillPriceCents(),
            dispensedCents = 0,
        )
    }

    /** The player pressed the pump. Called on touch-DOWN. */
    fun beginPumping() {
        if (_state.value.phase != Phase.Ready) return

        holdStart = clock.now()
        _state.update { it.copy(phase = Phase.Pumping) }

        // The gauge is redrawn on a timer, but the VALUE is always recomputed
        // from elapsed monotonic time rather than accumulated per tick, so a
        // dropped frame cannot cost the player fuel.
        tickJob = viewModelScope.launch {
            while (isActive) {
                delay(16)
                updateDispensed()
            }
        }
    }

    /** The player let go. This is their answer. */
    fun endPumping() {
        val current = _state.value
        val startedAt = holdStart
        if (current.phase != Phase.Pumping || startedAt == null) return

        tickJob?.cancel()
        tickJob = null
        holdStart = null

        val dispensed = dispensedFor(
            elapsedMs = clock.millisecondsSince(startedAt),
            pricePerGallonCents = current.pricePerGallonCents,
            goalCents = current.goalCents,
        )
        val score = ScoreRules.fillScore(dispensed, current.goalCents)
            .coerceAtMost(ScoreRules.fillBounds.last)

        _state.value = current.copy(
            phase = Phase.Finished(score),
            dispensedCents = dispensed,
        )
    }

    private fun updateDispensed() {
        val current = _state.value
        val startedAt = holdStart
        if (current.phase != Phase.Pumping || startedAt == null) return

        val dispensed = dispensedFor(
            elapsedMs = clock.millisecondsSince(startedAt),
            pricePerGallonCents = current.pricePerGallonCents,
            goalCents = current.goalCents,
        )
        _state.value = current.copy(dispensedCents = dispensed)

        // The pump stops on its own at the overfill limit.
        if (dispensed >= current.goalCents * ScoreRules.FILL_OVERFILL_LIMIT_MULTIPLE) {
            endPumping()
        }
    }

    /**
     * The amount on the pump after holding for [elapsedMs], capped at the
     * overfill limit.
     */
    private fun dispensedFor(elapsedMs: Int, pricePerGallonCents: Int, goalCents: Int): Int {
        val gallons = ScoreRules.gallonsDispensed(elapsedMs)
        val cents = ScoreRules.dispensedCents(gallons, pricePerGallonCents)
        return min(cents, goalCents * ScoreRules.FILL_OVERFILL_LIMIT_MULTIPLE)
    }

    fun reset() {
        prepareRound()
    }

    fun onStopped() {
        if (!isRoundActive) return

        tickJob?.cancel()
        tickJob = null
        holdStart = null
        _state.update { it.copy(phase = Phase.Cancelled) }
    }

    override fun onCleared() {
        tickJob?.cancel()
    }
}
