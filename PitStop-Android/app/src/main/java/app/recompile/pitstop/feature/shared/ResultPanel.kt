package app.recompile.pitstop.feature.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recompile.pitstop.core.GameId
import app.recompile.pitstop.core.ScoreRules
import app.recompile.pitstop.ui.components.ButtonKind
import app.recompile.pitstop.ui.components.PitStopButton
import app.recompile.pitstop.ui.components.PitStopCard
import app.recompile.pitstop.ui.components.ScoreDisplay
import app.recompile.pitstop.ui.theme.Ink
import app.recompile.pitstop.ui.theme.InkMuted
import app.recompile.pitstop.ui.theme.SignalRed

/** Dims whatever is behind a panel. */
@Composable
fun PanelBackdrop(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * What a player sees the moment a round ends: their score, and three large
 * choices. Shared by all three games so the ending is always the same shape.
 */
@Composable
fun ResultPanel(
    game: GameId,
    score: Int,
    detail: String? = null,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onQuit: () -> Unit,
) {
    PitStopCard(modifier = Modifier.widthIn(max = 520.dp).padding(24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Nice run", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)

            ScoreDisplay(
                value = ScoreRules.format(score, game),
                caption = game.scoreCaption,
            )

            if (detail != null) {
                Text(
                    text = detail,
                    color = InkMuted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }

            PitStopButton("Add to leaderboard", icon = Icons.Filled.Star, onClick = onSubmit)
            PitStopButton("Try again", icon = Icons.Filled.Refresh, kind = ButtonKind.SECONDARY, onClick = onRetry)
            PitStopButton("Done", icon = Icons.Filled.Home, kind = ButtonKind.QUIET, onClick = onQuit)
        }
    }
}

/**
 * Shown when a round could not be scored honestly — a false start, or an
 * interruption that broke the timing.
 */
@Composable
fun RoundProblemPanel(
    title: String,
    message: String,
    icon: ImageVector = Icons.Filled.Warning,
    onRetry: () -> Unit,
    onQuit: () -> Unit,
) {
    PitStopCard(modifier = Modifier.widthIn(max = 520.dp).padding(24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SignalRed,
                modifier = Modifier.size(56.dp),
            )

            Text(
                text = title,
                color = Ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Text(
                text = message,
                color = InkMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )

            PitStopButton("Try again", icon = Icons.Filled.Refresh, onClick = onRetry)
            PitStopButton("Done", icon = Icons.Filled.Home, kind = ButtonKind.QUIET, onClick = onQuit)
        }
    }
}
