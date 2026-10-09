package app.recompile.pitstop.data

import app.recompile.pitstop.core.GameId
import app.recompile.pitstop.core.ScoreRules
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The wire types shared with the Go server. Field names match the JSON the
 * server emits; see PitStop-Web/README.md for the API contract.
 */

/** Which client submitted a score. */
@Serializable
enum class Platform {
    @SerialName("ios")
    IOS,

    @SerialName("android")
    ANDROID,

    @SerialName("web")
    WEB,
}

/** One score on one leaderboard. */
@Serializable
data class Entry(
    val id: Long,
    val game: GameId,
    val name: String,
    val score: Int,
    /**
     * The score formatted for display, as the server renders it. Entries that
     * never reached the server fall back to the local formatter.
     */
    val display: String = ScoreRules.format(score, game),
    val platform: Platform = Platform.ANDROID,
    /** RFC 3339, as the server emits it. Kept as text: nothing sorts on it. */
    val createdAt: String = "",
    /** 1-based position on the board, or 0 when this result did not rank it. */
    val rank: Int = 0,
    /** Monotonic ordering for local entries, which have no server timestamp. */
    val localSequence: Long = 0,
)

/**
 * One game's leaderboard: the best scores, plus the handful of most recent ones
 * so a player who did not place still sees their name.
 */
@Serializable
data class Board(
    val game: GameId,
    val title: String = game.title,
    val tagline: String = game.tagline,
    val top: List<Entry> = emptyList(),
    val recent: List<Entry> = emptyList(),
    val total: Int = 0,
) {
    companion object {
        /** Matches the server's limits, so local and web boards are one shape. */
        const val TOP_COUNT = 10
        const val RECENT_COUNT = 3

        fun empty(game: GameId) = Board(game = game)
    }
}

/** Every game's board, in display order. */
@Serializable
data class Boards(
    val boards: List<Board> = GameId.displayOrder.map(Board::empty),
) {
    fun board(game: GameId): Board = boards.firstOrNull { it.game == game } ?: Board.empty(game)

    companion object {
        val EMPTY = Boards()
    }
}

/** A score being submitted. */
@Serializable
data class ScoreSubmission(
    val game: GameId,
    val name: String,
    val score: Int,
    val platform: Platform = Platform.ANDROID,
    val detail: Map<String, Int>? = null,
)

/** The server's reply to a submission: the stored entry and the refreshed board. */
@Serializable
data class SubmissionResult(
    val entry: Entry,
    val board: Board,
)

/** The server's error shape. */
@Serializable
data class ApiErrorBody(val error: Payload) {
    @Serializable
    data class Payload(val code: String, val message: String, val field: String? = null)
}
