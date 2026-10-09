package app.recompile.pitstop.feature.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.recompile.pitstop.core.GameId
import app.recompile.pitstop.data.Board
import app.recompile.pitstop.data.Entry
import app.recompile.pitstop.data.LeaderboardRepository
import app.recompile.pitstop.ui.components.PitStopCard
import app.recompile.pitstop.ui.components.StatusPill
import app.recompile.pitstop.ui.theme.Asphalt
import app.recompile.pitstop.ui.theme.Hairline
import app.recompile.pitstop.ui.theme.Ink
import app.recompile.pitstop.ui.theme.InkMuted
import app.recompile.pitstop.ui.theme.PitStopDimens
import app.recompile.pitstop.ui.theme.RacingYellow
import app.recompile.pitstop.ui.theme.SignalRed
import app.recompile.pitstop.ui.theme.SlateHigh

@Composable
fun LeaderboardScreen(repository: LeaderboardRepository) {
    val boards by repository.boards.collectAsStateWithLifecycle()
    val source by repository.source.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { repository.refresh() }

    Box(modifier = Modifier.fillMaxSize().background(Asphalt), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 700.dp).padding(PitStopDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                StatusPill(
                    text = source.label,
                    icon = if (source == LeaderboardRepository.Source.SERVER) Icons.Filled.Wifi else Icons.Filled.Tablet,
                    tint = if (source == LeaderboardRepository.Source.SERVER) RacingYellow else InkMuted,
                )
            }

            items(GameId.displayOrder, key = { it.wire }) { game ->
                BoardCard(boards.board(game))
            }
        }
    }
}

@Composable
fun BoardCard(board: Board) {
    PitStopCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            // Header
            Column(
                modifier = Modifier.fillMaxWidth().background(SignalRed).padding(16.dp)
            ) {
                Text(board.title, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(board.tagline, color = Ink.copy(alpha = 0.85f), fontSize = 13.sp)
            }

            if (board.top.isEmpty()) {
                Text(
                    "No scores yet — be the first.",
                    color = InkMuted,
                    fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            } else {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    board.top.forEachIndexed { index, entry ->
                        EntryRow(entry, isLeader = entry.rank == 1)
                        if (index != board.top.lastIndex) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
                        }
                    }
                }
            }

            if (board.recent.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().background(SlateHigh).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "JUST PLAYED",
                        color = InkMuted,
                        fontSize = 11.sp,
                        letterSpacing = 1.6.sp,
                        fontWeight = FontWeight.Black,
                    )
                    board.recent.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth().semantics {
                                contentDescription = "${entry.name}, ${entry.display}"
                            },
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(entry.name, color = Ink.copy(alpha = 0.82f), fontSize = 15.sp)
                            Text(
                                entry.display,
                                color = Ink.copy(alpha = 0.82f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: Entry, isLeader: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isLeader) RacingYellow.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
            .padding(vertical = 11.dp, horizontal = 6.dp)
            .semantics {
                contentDescription = "Position ${entry.rank}" +
                    (if (isLeader) ", leader" else "") +
                    ": ${entry.name}, ${entry.display}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The leader is marked with a trophy as well as a color, so the position
        // reads without relying on hue.
        Row(
            modifier = Modifier.widthIn(min = 52.dp).clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                "${entry.rank}",
                color = if (isLeader) RacingYellow else InkMuted,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            if (isLeader) {
                Icon(Icons.Filled.EmojiEvents, null, tint = RacingYellow, modifier = Modifier.size(16.dp))
            }
        }

        Text(
            entry.name,
            color = Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.weight(1f).clearAndSetSemantics {},
        )

        Text(
            entry.display,
            color = RacingYellow,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
