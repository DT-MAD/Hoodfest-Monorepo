package app.recompile.pitstop.feature.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recompile.pitstop.core.GameId
import app.recompile.pitstop.core.NameValidator
import app.recompile.pitstop.core.ScoreRules
import app.recompile.pitstop.data.LeaderboardRepository
import app.recompile.pitstop.ui.components.PitStopButton
import app.recompile.pitstop.ui.components.ScoreDisplay
import app.recompile.pitstop.ui.components.StatusPill
import app.recompile.pitstop.ui.theme.Hairline
import app.recompile.pitstop.ui.theme.Ink
import app.recompile.pitstop.ui.theme.InkMuted
import app.recompile.pitstop.ui.theme.RacingYellow
import app.recompile.pitstop.ui.theme.SignalRed
import kotlinx.coroutines.launch

/**
 * Name entry, with the same rules the server enforces so a name accepted here
 * is never refused after the fact.
 */
@Composable
fun SubmitNameScreen(
    game: GameId,
    score: Int,
    detail: Map<String, Int>?,
    repository: LeaderboardRepository,
    onDone: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<LeaderboardRepository.Outcome?>(null) }
    val scope = rememberCoroutineScope()

    // Live validation, but only once they have typed something — nobody wants
    // to be told their empty field is invalid before they start.
    val failure = if (name.isEmpty()) null else NameValidator.failureFor(name)
    val canSubmit = !isSubmitting && name.isNotEmpty() && failure == null

    val submit = {
        val result = NameValidator.validate(name)
        if (canSubmit && result.isValid) {
            isSubmitting = true
            scope.launch {
                outcome = repository.submit(game, result.canonical, score, detail)
                isSubmitting = false
            }
        }
        Unit
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .widthIn(max = 560.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val settled = outcome

        if (settled == null) {
            Text(
                "Add your name",
                color = Ink,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
            )

            ScoreDisplay(
                value = ScoreRules.format(score, game),
                caption = game.scoreCaption,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                placeholder = { Text("Your name", color = InkMuted) },
                singleLine = true,
                isError = failure != null,
                textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Ink,
                    unfocusedTextColor = Ink,
                    focusedBorderColor = RacingYellow,
                    unfocusedBorderColor = Hairline,
                    errorBorderColor = SignalRed,
                    cursorColor = RacingYellow,
                ),
            )

            // The reason is text and an icon, never color alone.
            if (failure != null) {
                RowLabel(
                    text = failure.message,
                    icon = Icons.Filled.Error,
                    tint = SignalRed,
                )
            } else {
                Text(
                    "${NameValidator.MAX_LENGTH - name.length} characters left",
                    color = InkMuted,
                    fontSize = 13.sp,
                )
            }

            PitStopButton(
                title = if (isSubmitting) "Adding…" else "Add to leaderboard",
                icon = Icons.Filled.Star,
                enabled = canSubmit,
                onClick = submit,
            )
        } else {
            Icon(
                imageVector = if (settled.entry.rank == 1) Icons.Filled.Star else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = RacingYellow,
                modifier = Modifier.size(72.dp),
            )

            Text(
                settled.entry.name,
                color = Ink,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )

            Text(
                rankText(settled.entry.rank),
                color = RacingYellow,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )

            ScoreDisplay(
                value = ScoreRules.format(settled.entry.score, game),
                caption = game.scoreCaption,
                tint = Ink,
            )

            StatusPill(
                text = if (settled.reachedServer) "On the booth leaderboard" else "Saved on this tablet",
                icon = if (settled.reachedServer) Icons.Filled.CheckCircle else Icons.Filled.Home,
                tint = if (settled.reachedServer) RacingYellow else InkMuted,
            )

            PitStopButton("Done", icon = Icons.Filled.Home, onClick = onDone)
        }
    }
}

private fun rankText(rank: Int): String = when (rank) {
    1 -> "1st place"
    2 -> "2nd place"
    3 -> "3rd place"
    in 4..Int.MAX_VALUE -> "${rank}th place"
    else -> "Added to the board"
}

@Composable
private fun RowLabel(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text, color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
