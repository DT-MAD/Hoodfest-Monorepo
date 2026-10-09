package app.recompile.pitstop.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The three mini-games.
 *
 * The [wire] values are the format shared with the Go server and the iOS app;
 * do not rename them.
 */
@Serializable
enum class GameId(val wire: String) {
    @SerialName("reaction")
    REACTION("reaction"),

    @SerialName("fill")
    FILL("fill"),

    @SerialName("pitstop")
    PITSTOP("pitstop");

    /** Display name, matching the leaderboard headings on the web board. */
    val title: String
        get() = when (this) {
            REACTION -> "Reaction Lights"
            FILL -> "Fill It Up"
            PITSTOP -> "Perfect Pit Stop"
        }

    /** One line explaining the goal, shown on the home screen and above a board. */
    val tagline: String
        get() = when (this) {
            REACTION -> "Fastest reaction to the green light"
            FILL -> "Closest to the dollar target"
            PITSTOP -> "Fastest four-tire change"
        }

    /**
     * The single instruction a visitor reads before playing. The whole booth
     * experience depends on this being understandable at a glance.
     */
    val instruction: String
        get() = when (this) {
            REACTION -> "Wait for the light to turn green, then tap as fast as you can."
            FILL -> "Hold the pump. Stop as close to the target as you can."
            PITSTOP -> "Tap all four tires as fast as you can. Missing costs you a second."
        }

    /** What the score means, shown under a result so "243 ms" needs no explaining. */
    val scoreCaption: String
        get() = when (this) {
            REACTION -> "Reaction time"
            FILL -> "Off the target"
            PITSTOP -> "Pit stop time"
        }

    companion object {
        /** Games in the order they are displayed, left to right. */
        val displayOrder: List<GameId> = listOf(REACTION, FILL, PITSTOP)

        fun fromWire(value: String): GameId? = entries.firstOrNull { it.wire == value }
    }
}
