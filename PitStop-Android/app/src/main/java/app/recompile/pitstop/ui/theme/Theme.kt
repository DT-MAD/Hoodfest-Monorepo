package app.recompile.pitstop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * The Pit Stop theme.
 *
 * Note what is NOT here: Material You dynamic color. The template enabled it,
 * which would let the device's wallpaper repaint the booth's branding. This is a
 * fixed, dark, high-contrast palette by design — it is not a light-mode app with
 * a dark variant, and it does not follow the system theme.
 */
private val PitStopColorScheme = darkColorScheme(
    primary = RacingYellow,
    onPrimary = Asphalt,
    secondary = SlateHigh,
    onSecondary = Ink,
    tertiary = SignalRed,
    onTertiary = Ink,
    background = Asphalt,
    onBackground = Ink,
    surface = Slate,
    onSurface = Ink,
    surfaceVariant = SlateHigh,
    onSurfaceVariant = InkMuted,
    error = SignalRed,
    onError = Ink,
    outline = Hairline,
)

@Composable
fun PitStopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PitStopColorScheme,
        typography = Typography,
        content = content,
    )
}

/** Shape and spacing constants shared across the app. */
object PitStopDimens {
    val cornerRadius = 20.dp
    val cardRadius = 16.dp

    /**
     * Minimum height for anything a visitor taps. Comfortably above the 48dp
     * guideline because this is a shared tablet used at arm's length, often by
     * a child, often in a hurry.
     */
    val tapTargetHeight = 88.dp

    val screenPadding = 22.dp
    val cardPadding = 20.dp
    val gap = 16.dp
}
