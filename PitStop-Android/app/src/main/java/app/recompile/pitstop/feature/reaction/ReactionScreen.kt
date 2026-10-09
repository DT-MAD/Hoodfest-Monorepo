package app.recompile.pitstop.feature.reaction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import app.recompile.pitstop.ui.components.onTouchDown
import app.recompile.pitstop.ui.theme.Asphalt
import app.recompile.pitstop.ui.theme.GoGreen
import app.recompile.pitstop.ui.theme.Ink
import app.recompile.pitstop.ui.theme.RacingYellow
import app.recompile.pitstop.ui.theme.SignalRed
import app.recompile.pitstop.util.OnStopped
import app.recompile.pitstop.util.performHaptic
import android.view.HapticFeedbackConstants

@Composable
fun ReactionScreen(
    onSubmit: (score: Int) -> Unit,
    onQuit: () -> Unit,
    model: ReactionViewModel = viewModel(),
) {
    val phase by model.phase.collectAsStateWithLifecycle()
    val view = LocalView.current

    // Any interruption invalidates the round rather than scoring it.
    OnStopped { model.onStopped() }

    val surface = when (phase) {
        ReactionViewModel.Phase.Waiting -> SignalRed
        ReactionViewModel.Phase.Go -> GoGreen
        else -> Asphalt
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surface)
            // Touch-DOWN, not touch-up: `clickable` fires on release, which
            // would add the player's lift time to every score.
            .onTouchDown {
                when (phase) {
                    ReactionViewModel.Phase.Ready -> model.start()
                    ReactionViewModel.Phase.Waiting, ReactionViewModel.Phase.Go -> {
                        model.tap()
                        view.performHaptic(HapticFeedbackConstants.LONG_PRESS)
                    }
                    else -> Unit
                }
            }
            .semantics { contentDescription = describe(phase) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (phase) {
                ReactionViewModel.Phase.Ready -> {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = RacingYellow,
                        modifier = Modifier.size(72.dp),
                    )
                    Text(
                        GameId.REACTION.instruction,
                        color = Ink,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp, bottom = 32.dp),
                    )
                    PitStopButton(
                        "Start",
                        icon = Icons.Filled.Flag,
                        modifier = Modifier.widthIn(max = 420.dp),
                        onClick = model::start,
                    )
                }

                // State is a shape and a word as well as a color, so the game is
                // playable by someone who cannot tell red from green.
                ReactionViewModel.Phase.Waiting -> {
                    Icon(
                        Icons.Filled.PanTool,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(110.dp),
                    )
                    Text(
                        "WAIT",
                        color = Color.White,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }

                ReactionViewModel.Phase.Go -> {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(130.dp),
                    )
                    Text(
                        "TAP!",
                        color = Color.White,
                        fontSize = 78.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }

                else -> Unit
            }
        }

        when (val current = phase) {
            ReactionViewModel.Phase.FalseStart -> PanelBackdrop {
                RoundProblemPanel(
                    title = "False start",
                    message = "You went before the light. Wait for green, then tap.",
                    onRetry = { model.reset(); model.start() },
                    onQuit = onQuit,
                )
            }

            ReactionViewModel.Phase.Cancelled -> PanelBackdrop {
                RoundProblemPanel(
                    title = "Round cancelled",
                    message = "Something interrupted the timer, so this run can't be scored. Give it another go.",
                    onRetry = { model.reset(); model.start() },
                    onQuit = onQuit,
                )
            }

            is ReactionViewModel.Phase.Finished -> PanelBackdrop {
                ResultPanel(
                    game = GameId.REACTION,
                    score = current.score,
                    onSubmit = { onSubmit(current.score) },
                    onRetry = { model.reset(); model.start() },
                    onQuit = onQuit,
                )
            }

            else -> Unit
        }
    }
}

private fun describe(phase: ReactionViewModel.Phase): String = when (phase) {
    ReactionViewModel.Phase.Ready -> "Reaction Lights. Not started. Tap to start."
    ReactionViewModel.Phase.Waiting -> "Red light. Wait."
    ReactionViewModel.Phase.Go -> "Green light. Tap now."
    is ReactionViewModel.Phase.Finished ->
        "Finished. ${ScoreRules.format(phase.score, GameId.REACTION)}."
    ReactionViewModel.Phase.FalseStart -> "False start."
    ReactionViewModel.Phase.Cancelled -> "Round cancelled."
}
