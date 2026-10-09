package app.recompile.pitstop.feature.fill

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.recompile.pitstop.core.GameId
import app.recompile.pitstop.feature.shared.PanelBackdrop
import app.recompile.pitstop.feature.shared.ResultPanel
import app.recompile.pitstop.feature.shared.RoundProblemPanel
import app.recompile.pitstop.ui.components.PitStopCard
import app.recompile.pitstop.ui.components.onHold
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
fun FillScreen(
    onSubmit: (score: Int, detail: Map<String, Int>) -> Unit,
    onQuit: () -> Unit,
    model: FillViewModel = viewModel(),
) {
    val state by model.state.collectAsStateWithLifecycle()
    val view = LocalView.current

    LaunchedEffect(Unit) { model.prepareRound() }
    OnStopped { model.onStopped() }

    Box(modifier = Modifier.fillMaxSize().background(Asphalt), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
                .padding(PitStopDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Target
            PitStopCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("FILL UP TO", color = InkMuted, fontSize = 13.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        state.goalText,
                        color = RacingYellow,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                    Text(state.priceText, color = InkMuted, fontSize = 15.sp)
                }
            }

            // Gauge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(PitStopDimens.cardRadius))
                    .background(Slate)
                    .semantics {
                        contentDescription = "Fuel gauge, ${state.dispensedText} of ${state.goalText}"
                    },
            ) {
                val heightPx = remember { mutableIntStateOf(0) }
                val density = LocalDensity.current

                Box(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { heightPx.intValue = it.height }
                ) {
                    val totalHeight = with(density) { heightPx.intValue.toDp() }

                    // Fill, from the bottom.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(totalHeight * state.gaugeFraction)
                            .align(Alignment.BottomCenter)
                            .background(if (state.isOverTarget) SignalRed else RacingYellow)
                    )

                    // The target line, labelled — the player aims at this.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = totalHeight * (1f - state.goalFraction))
                    ) {
                        Box(Modifier.fillMaxWidth().height(3.dp).background(Ink))
                        Text(
                            "TARGET",
                            color = Ink,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.4.sp,
                            modifier = Modifier.padding(start = 10.dp, top = 2.dp),
                        )
                    }
                }
            }

            // Readout
            PitStopCard(modifier = Modifier.fillMaxWidth(), fill = SlateHigh) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("TOTAL", color = InkMuted, fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            state.dispensedText,
                            color = if (state.isOverTarget) SignalRed else Ink,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(state.gallonsText, color = InkMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        // "Over" is a word and an icon, not just a red bar.
                        if (state.isOverTarget) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Filled.Warning, null, tint = SignalRed, modifier = Modifier.size(16.dp))
                                Text("Over target", color = SignalRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Pump
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 130.dp)
                    .clip(RoundedCornerShape(PitStopDimens.cornerRadius))
                    .background(if (state.phase == FillViewModel.Phase.Pumping) SignalRed else RacingYellow)
                    .onHold(
                        onPress = {
                            model.beginPumping()
                            view.performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                        },
                        onRelease = { model.endPumping() },
                    )
                    .padding(16.dp)
                    .semantics {
                        contentDescription = "Pump. Touch and hold to dispense fuel. Let go to stop."
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.LocalGasStation, null, tint = Asphalt, modifier = Modifier.size(40.dp))
                Text(
                    if (state.phase == FillViewModel.Phase.Pumping) "Let go to stop" else "Hold to pump",
                    color = Asphalt,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        when (val phase = state.phase) {
            FillViewModel.Phase.Cancelled -> PanelBackdrop {
                RoundProblemPanel(
                    title = "Round cancelled",
                    message = "Something interrupted the pump, so this run can't be scored. Give it another go.",
                    onRetry = model::reset,
                    onQuit = onQuit,
                )
            }

            is FillViewModel.Phase.Finished -> PanelBackdrop {
                ResultPanel(
                    game = GameId.FILL,
                    score = phase.score,
                    detail = "You pumped ${state.dispensedText} of a ${state.goalText} target.",
                    onSubmit = {
                        onSubmit(
                            phase.score,
                            mapOf(
                                "goalCents" to state.goalCents,
                                "pricePerGallonCents" to state.pricePerGallonCents,
                            ),
                        )
                    },
                    onRetry = model::reset,
                    onQuit = onQuit,
                )
            }

            else -> Unit
        }
    }
}
