package app.recompile.pitstop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recompile.pitstop.ui.theme.Asphalt
import app.recompile.pitstop.ui.theme.Ink
import app.recompile.pitstop.ui.theme.InkMuted
import app.recompile.pitstop.ui.theme.PitStopDimens
import app.recompile.pitstop.ui.theme.RacingYellow
import app.recompile.pitstop.ui.theme.Slate
import app.recompile.pitstop.ui.theme.SlateHigh

/**
 * Reports a touch the instant the finger lands, rather than when it lifts.
 *
 * This matters more than it looks. `clickable` fires on release, which would add
 * the player's lift time to every reaction score. For a game measured in
 * milliseconds that is the difference between a real number and a fictional one.
 */
fun Modifier.onTouchDown(enabled: Boolean = true, onDown: () -> Unit): Modifier =
    if (!enabled) this else this.pointerInput(onDown) {
        detectTapGestures(onPress = { onDown() })
    }

/**
 * Reports both the press and the release, for the hold-to-pump control where
 * letting go is the player's answer.
 */
fun Modifier.onHold(onPress: () -> Unit, onRelease: () -> Unit): Modifier =
    this.pointerInput(onPress, onRelease) {
        detectTapGestures(
            onPress = {
                onPress()
                // Returns whether the press ended in a release or a cancel;
                // either way the hold is over and the round must be scored.
                tryAwaitRelease()
                onRelease()
            }
        )
    }

enum class ButtonKind { PRIMARY, SECONDARY, QUIET }

/**
 * The primary action on a screen. Deliberately enormous: this is a shared
 * tablet, used at arm's length, often by a child, often in a hurry.
 */
@Composable
fun PitStopButton(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    kind: ButtonKind = ButtonKind.PRIMARY,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val background = when (kind) {
        ButtonKind.PRIMARY -> RacingYellow
        ButtonKind.SECONDARY -> SlateHigh
        ButtonKind.QUIET -> Color.Transparent
    }
    val foreground = when (kind) {
        ButtonKind.PRIMARY -> Asphalt
        else -> Ink
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PitStopDimens.tapTargetHeight)
            .clip(RoundedCornerShape(PitStopDimens.cornerRadius))
            .background(if (enabled) background else background.copy(alpha = 0.4f))
            .then(
                if (kind == ButtonKind.QUIET) {
                    Modifier.border(1.dp, Ink.copy(alpha = 0.12f), RoundedCornerShape(PitStopDimens.cornerRadius))
                } else Modifier
            )
            .then(
                if (enabled) Modifier.pointerInput(onClick) {
                    detectTapGestures(onTap = { onClick() })
                } else Modifier
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) foreground else foreground.copy(alpha = 0.6f),
                modifier = Modifier.size(26.dp).padding(end = 0.dp),
            )
        }
        Text(
            text = title,
            color = if (enabled) foreground else foreground.copy(alpha = 0.6f),
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = if (icon != null) 12.dp else 0.dp),
        )
    }
}

/** The large number on a result screen. */
@Composable
fun ScoreDisplay(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    tint: Color = RacingYellow,
) {
    Column(
        modifier = modifier.clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            color = tint,
            fontSize = 52.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
        Text(
            text = caption.uppercase(),
            color = InkMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.6.sp,
        )
    }
}

/**
 * A labelled state pill. State is never carried by color alone — every pill has
 * an icon and a word as well.
 */
@Composable
fun StatusPill(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = InkMuted,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(SlateHigh)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The checkered flag mark used in the app's chrome. */
@Composable
fun CheckeredFlag(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 56.dp, squares: Int = 4) {
    val side = size / squares

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(Ink)
            .clearAndSetSemantics {},
    ) {
        for (row in 0 until squares) {
            for (column in 0 until squares) {
                if ((row + column) % 2 == 0) {
                    Box(
                        Modifier
                            .padding(start = side * column, top = side * row)
                            .size(side)
                            .background(Asphalt)
                    )
                }
            }
        }
    }
}

/** A raised card. */
@Composable
fun PitStopCard(
    modifier: Modifier = Modifier,
    fill: Color = Slate,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(PitStopDimens.cardRadius))
            .background(fill)
    ) {
        content()
    }
}
