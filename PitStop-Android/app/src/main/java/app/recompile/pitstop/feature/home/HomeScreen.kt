package app.recompile.pitstop.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.recompile.pitstop.core.GameId
import app.recompile.pitstop.data.Entry
import app.recompile.pitstop.data.LeaderboardRepository
import app.recompile.pitstop.ui.components.ButtonKind
import app.recompile.pitstop.ui.components.CheckeredFlag
import app.recompile.pitstop.ui.components.PitStopButton
import app.recompile.pitstop.ui.components.PitStopCard
import app.recompile.pitstop.ui.theme.Asphalt
import app.recompile.pitstop.ui.theme.Hairline
import app.recompile.pitstop.ui.theme.Ink
import app.recompile.pitstop.ui.theme.InkMuted
import app.recompile.pitstop.ui.theme.PitStopDimens
import app.recompile.pitstop.ui.theme.RacingYellow
import app.recompile.pitstop.ui.theme.SignalRed
import app.recompile.pitstop.ui.theme.SlateHigh
import app.recompile.pitstop.util.performHaptic

/**
 * What a visitor sees when they walk up. It has to explain itself in about two
 * seconds, with no instructions from the operator.
 */
@Composable
fun HomeScreen(
    repository: LeaderboardRepository,
    onPlay: (GameId) -> Unit,
    onLeaderboard: () -> Unit,
    onSettings: () -> Unit,
) {
    val boards by repository.boards.collectAsStateWithLifecycle()
    val view = LocalView.current

    LaunchedEffect(Unit) { repository.refresh() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Asphalt)) {
        // Below this width three tiles stop being readable and the grid becomes
        // a single column. Comfortably under a tablet, which is the booth
        // configuration, so the grid is what visitors actually see.
        val isWide = maxWidth >= 700.dp
        val tileHeight: Dp = if (isWide) (maxHeight * 0.32f).coerceIn(290.dp, 430.dp) else 112.dp
        val availableHeight = maxHeight

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = availableHeight)
                .widthIn(max = 1000.dp)
                .padding(PitStopDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        ) {
            Masthead(isWide = isWide) {
                view.performHaptic()
                onSettings()
            }

            if (isWide) {
                Row(horizontalArrangement = Arrangement.spacedBy(PitStopDimens.gap)) {
                    GameId.displayOrder.forEach { game ->
                        GameTile(
                            game = game,
                            best = boards.board(game).top.firstOrNull(),
                            isStacked = false,
                            height = tileHeight,
                            modifier = Modifier.weight(1f),
                            onClick = { onPlay(game) },
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(PitStopDimens.gap)) {
                    GameId.displayOrder.forEach { game ->
                        GameTile(
                            game = game,
                            best = boards.board(game).top.firstOrNull(),
                            isStacked = true,
                            height = tileHeight,
                            onClick = { onPlay(game) },
                        )
                    }
                }
            }

            PitStopButton(
                "See the leaderboard",
                icon = Icons.AutoMirrored.Filled.List,
                kind = ButtonKind.SECONDARY,
                onClick = onLeaderboard,
            )

            ProgramNote()
        }
    }
}

@Composable
private fun Masthead(isWide: Boolean, onLongPress: () -> Unit) {
    PitStopCard(
        modifier = Modifier
            .fillMaxWidth()
            // Hidden from visitors, obvious to an operator who has been told.
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
            .semantics { contentDescription = "Pit Stop, by Dixie Tech. Pick a game. Long press for booth settings." },
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(4.dp).background(SignalRed))
            Row(
                modifier = Modifier.fillMaxWidth().padding(PitStopDimens.cardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                CheckeredFlag(size = if (isWide) 64.dp else 52.dp)

                Column(Modifier.weight(1f)) {
                    Text(
                        "PIT STOP",
                        color = Ink,
                        fontSize = if (isWide) 46.sp else 36.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                    Text(
                        "DIXIE TECH",
                        color = RacingYellow,
                        fontSize = 13.sp,
                        letterSpacing = 2.8.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }

                // Dropped on a narrow screen, where it would compete for width
                // with the app's own name.
                if (isWide) {
                    Text("Pick a game", color = InkMuted, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun GameTile(
    game: GameId,
    best: Entry?,
    isStacked: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val label = if (best == null) {
        "${game.title}. ${game.tagline}. No scores yet."
    } else {
        "${game.title}. ${game.tagline}. Best so far: ${best.name}, ${best.display}."
    }

    PitStopCard(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) }
            .semantics { contentDescription = label },
    ) {
        if (isStacked) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(PitStopDimens.cardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TileIcon(game, 72.dp)
                Column(Modifier.weight(1f)) { TileTitle(game) }
                BestBlock(best, alignEnd = true)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(PitStopDimens.cardPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TileIcon(game, (height * 0.26f).coerceIn(84.dp, 118.dp))
                TileTitle(game)
                Box(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
                BestBlock(best, alignEnd = false)
            }
        }
    }
}

@Composable
private fun TileIcon(game: GameId, side: Dp) {
    Box(
        modifier = Modifier
            .size(side)
            .clip(RoundedCornerShape(20.dp))
            .background(RacingYellow),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = game.icon,
            contentDescription = null,
            tint = Asphalt,
            modifier = Modifier.size(side * 0.48f),
        )
    }
}

@Composable
private fun TileTitle(game: GameId) {
    Column {
        Text(game.title, color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(game.tagline, color = InkMuted, fontSize = 15.sp)
    }
}

/** The current leader, or an invitation when nobody has played yet. */
@Composable
private fun BestBlock(best: Entry?, alignEnd: Boolean) {
    if (best == null) {
        Text("No scores yet", color = InkMuted, fontSize = 13.sp)
        return
    }

    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            "BEST",
            color = InkMuted,
            fontSize = 11.sp,
            letterSpacing = 1.6.sp,
            fontWeight = FontWeight.Black,
        )
        Text(best.display, color = RacingYellow, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(best.name, color = InkMuted, fontSize = 13.sp, maxLines = 1)
    }
}

/**
 * The booth exists to showcase the program, so this is the message. It describes
 * what students in the program do — it does not claim they wrote this app.
 */
@Composable
private fun ProgramNote() {
    PitStopCard(modifier = Modifier.fillMaxWidth(), fill = SlateHigh) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(PitStopDimens.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(RacingYellow),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Build, null, tint = Asphalt, modifier = Modifier.size(28.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Mobile App Development at Dixie Tech",
                    color = RacingYellow,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Students in this program build real iOS and Android apps — Kotlin, Swift, " +
                        "and the servers behind them. If making something like this sounds like " +
                        "your kind of thing, come talk to us.",
                    color = InkMuted,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

/** The icon shown alongside each game, so it is identifiable by shape too. */
private val GameId.icon: ImageVector
    get() = when (this) {
        GameId.REACTION -> Icons.Filled.Bolt
        GameId.FILL -> Icons.Filled.LocalGasStation
        GameId.PITSTOP -> Icons.Filled.DirectionsCar
    }
