package app.recompile.pitstop.feature.pitstop

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.recompile.pitstop.core.GameId
import app.recompile.pitstop.core.ScoreRules
import app.recompile.pitstop.feature.shared.PanelBackdrop
import app.recompile.pitstop.feature.shared.ResultPanel
import app.recompile.pitstop.feature.shared.RoundProblemPanel
import app.recompile.pitstop.ui.components.PitStopButton
import app.recompile.pitstop.ui.components.PitStopCard
import app.recompile.pitstop.ui.components.onTouchDown
import app.recompile.pitstop.ui.theme.Asphalt
import app.recompile.pitstop.ui.theme.Ink
import app.recompile.pitstop.ui.theme.InkMuted
import app.recompile.pitstop.ui.theme.PitStopDimens
import app.recompile.pitstop.ui.theme.RacingYellow
import app.recompile.pitstop.ui.theme.SignalRed
import app.recompile.pitstop.ui.theme.Slate
import app.recompile.pitstop.ui.theme.SlateHigh
import app.recompile.pitstop.util.OnStopped
import app.recompile.pitstop.util.performHaptic

@Composable
fun PitStopScreen(
    onSubmit: (score: Int, detail: Map<String, Int>) -> Unit,
    onQuit: () -> Unit,
    model: PitStopViewModel = viewModel(),
) {
    val state by model.state.collectAsStateWithLifecycle()
    val view = LocalView.current

    OnStopped { model.onStopped() }

    Box(modifier = Modifier.fillMaxSize().background(Asphalt), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 700.dp)
                .padding(PitStopDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Scoreboard
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(
                    title = "Clock",
                    value = if (state.phase == PitStopViewModel.Phase.Running || state.score != null)
                        state.runningScoreText else "0.000s",
                    tint = RacingYellow,
                    modifier = Modifier.weight(1f),
                )
                Stat("Tires left", "${state.remainingCount}", Ink, Modifier.weight(1f))
                Stat(
                    title = "Missed",
                    value = "${state.misTaps}",
                    tint = if (state.misTaps > 0) SignalRed else Ink,
                    modifier = Modifier.weight(1f),
                )
            }

            // The car. Tapping the body — anywhere that is not an un-changed
            // tire — costs a second.
            //
            // The play area keeps a car-like proportion rather than stretching
            // to whatever height is going: filling the screen turns the
            // silhouette into a featureless slab and flings the tires into the
            // far corners, which is neither readable nor fair to reach.
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(CAR_ASPECT_RATIO)
                        .clip(RoundedCornerShape(PitStopDimens.cardRadius))
                        .background(Slate)
                        .onTouchDown(enabled = state.phase == PitStopViewModel.Phase.Running) {
                            model.tapMissed()
                            view.performHaptic(HapticFeedbackConstants.REJECT)
                        },
                ) {
                    CarBody(Modifier.align(Alignment.Center))

                    Column(
                        modifier = Modifier.fillMaxSize().padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TireTarget(Tire.FRONT_LEFT, state, model, view)
                            TireTarget(Tire.FRONT_RIGHT, state, model, view)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TireTarget(Tire.REAR_LEFT, state, model, view)
                            TireTarget(Tire.REAR_RIGHT, state, model, view)
                        }
                    }
                }
            }

            if (state.phase == PitStopViewModel.Phase.Ready) {
                Text(
                    GameId.PITSTOP.instruction,
                    color = InkMuted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                PitStopButton("Start", icon = Icons.Filled.Flag) {
                    model.start()
                    view.performHaptic(HapticFeedbackConstants.LONG_PRESS)
                }
            }
        }

        when (val phase = state.phase) {
            PitStopViewModel.Phase.Cancelled -> PanelBackdrop {
                RoundProblemPanel(
                    title = "Round cancelled",
                    message = "Something interrupted the timer, so this run can't be scored. Give it another go.",
                    onRetry = model::reset,
                    onQuit = onQuit,
                )
            }

            is PitStopViewModel.Phase.Finished -> PanelBackdrop {
                ResultPanel(
                    game = GameId.PITSTOP,
                    score = phase.score,
                    detail = if (state.misTaps > 0) {
                        "${ScoreRules.format(state.elapsedMs, GameId.PITSTOP)} on the clock, " +
                            "plus ${state.misTaps} second${if (state.misTaps == 1) "" else "s"} of penalties."
                    } else {
                        "A clean stop — no missed taps."
                    },
                    onSubmit = { onSubmit(phase.score, mapOf("misTaps" to state.misTaps)) },
                    onRetry = model::reset,
                    onQuit = onQuit,
                )
            }

            else -> Unit
        }
    }
}

@Composable
private fun Stat(title: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    PitStopCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title.uppercase(),
                color = InkMuted,
                fontSize = 12.sp,
                letterSpacing = 1.3.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(value, color = tint, fontSize = 28.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

/**
 * A top-down car silhouette. Purely decorative; the tires sit on top of it.
 *
 * Every dimension is a fraction of the play area, so the car keeps its shape on
 * a phone and on a large tablet alike rather than stretching into a slab.
 */
@Composable
private fun CarBody(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight(0.92f)
            .fillMaxWidth(0.66f)
            .clip(RoundedCornerShape(percent = 18))
            .background(SlateHigh),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .fillMaxHeight()
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            // Windshield, roof, rear window — enough to read as a car from a
            // step away without pretending to be an illustration.
            Box(
                Modifier.fillMaxWidth().fillMaxHeight(0.10f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Asphalt.copy(alpha = 0.55f))
            )
            Box(
                Modifier.fillMaxWidth().fillMaxHeight(0.26f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Asphalt.copy(alpha = 0.3f))
            )
            Box(
                Modifier.fillMaxWidth().fillMaxHeight(0.10f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Asphalt.copy(alpha = 0.45f))
            )
        }
    }
}

private const val CAR_ASPECT_RATIO = 0.66f

@Composable
private fun TireTarget(
    tire: Tire,
    state: PitStopViewModel.RoundState,
    model: PitStopViewModel,
    view: android.view.View,
) {
    val isChanged = state.isChanged(tire)
    val scale by animateFloatAsState(if (isChanged) 0.94f else 1f, label = "tire")

    Box(
        modifier = Modifier
            .size(width = 92.dp, height = 122.dp)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(if (isChanged) RacingYellow else Asphalt)
            .border(
                3.dp,
                if (isChanged) RacingYellow else InkMuted,
                RoundedCornerShape(18.dp),
            )
            .onTouchDown(enabled = state.phase == PitStopViewModel.Phase.Running) {
                model.tap(tire)
                view.performHaptic(
                    if (isChanged) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.CONFIRM
                )
            }
            .semantics {
                contentDescription = "${tire.label} tire"
                stateDescription = if (isChanged) "Changed" else "Not changed"
            },
        contentAlignment = Alignment.Center,
    ) {
        // Changed tires get a check mark, so the state is legible without
        // relying on the color change.
        Icon(
            imageVector = if (isChanged) Icons.Filled.Check else Icons.Filled.Circle,
            contentDescription = null,
            tint = if (isChanged) Asphalt else InkMuted,
            modifier = Modifier.size(if (isChanged) 44.dp else 30.dp),
        )
    }
}
