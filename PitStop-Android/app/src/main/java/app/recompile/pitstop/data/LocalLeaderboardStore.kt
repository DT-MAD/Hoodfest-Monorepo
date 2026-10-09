package app.recompile.pitstop.data

import android.content.Context
import app.recompile.pitstop.core.GameId
import java.io.File
import kotlinx.serialization.json.Json

/**
 * The on-device leaderboard.
 *
 * Every game must work with no network at all, so a score always lands here
 * first. If the server is unreachable the entry simply stays local — it is not
 * queued for later upload, by design: a score that shows up on the big screen
 * twenty minutes after the player walked away is confusing, not useful.
 */
class LocalLeaderboardStore(context: Context, filename: String = "leaderboard.json") {

    companion object {
        /**
         * How many entries to keep per game. Generous: a board only ever shows
         * ten, but keeping more means a beaten score still has history behind it.
         */
        const val RETAINED_PER_GAME = 100
    }

    private val file = File(context.applicationContext.filesDir, filename)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val entries = mutableListOf<Entry>()

    /**
     * Ids are negative so a local-only entry can never collide with a server id,
     * which lets the two be merged into one list safely.
     */
    private var nextLocalId = -1L

    /** Strictly increasing, so two entries saved in the same millisecond still
     *  have a definite order to break a score tie on. */
    private var nextSequence = 0L

    init {
        load()
    }

    // ----------------------------------------------------------------- reading

    val count: Int get() = entries.size

    /**
     * Every entry for a game, best first and ranked, ties broken by the earlier
     * entry — the same ordering the server uses.
     */
    private fun ranked(game: GameId): List<Entry> =
        entries.filter { it.game == game }
            .sortedWith(compareBy({ it.score }, { it.localSequence }))
            .mapIndexed { index, entry -> entry.copy(rank = index + 1) }

    /** One game's local board. */
    fun board(game: GameId): Board {
        val mine = entries.filter { it.game == game }
        val ranked = ranked(game)

        val recent = mine
            .sortedByDescending { it.localSequence }
            .take(Board.RECENT_COUNT)
            .map { it.copy(rank = 0) }

        return Board(
            game = game,
            top = ranked.take(Board.TOP_COUNT),
            recent = recent,
            total = mine.size,
        )
    }

    /** Every game's local board. */
    fun boards(): Boards = Boards(GameId.displayOrder.map(::board))

    // ----------------------------------------------------------------- writing

    /** Saves a score locally and returns it with the rank it earned. */
    fun add(game: GameId, name: String, score: Int): Entry {
        val entry = Entry(
            id = nextLocalId,
            game = game,
            name = name,
            score = score,
            display = app.recompile.pitstop.core.ScoreRules.format(score, game),
            platform = Platform.ANDROID,
            localSequence = nextSequence,
        )
        nextLocalId -= 1
        nextSequence += 1

        entries.add(entry)
        prune(game)
        save()

        // Report the real rank, which may well be outside the visible top ten.
        return ranked(game).firstOrNull { it.id == entry.id } ?: entry
    }

    /** Drops the weakest entries once a game has more than it needs. */
    private fun prune(game: GameId) {
        val mine = entries.filter { it.game == game }
        if (mine.size <= RETAINED_PER_GAME) return

        // Keep the best scores AND the most recent ones. Between them they are
        // everything a board can show, and keeping only the best would quietly
        // erase a player's run the moment a better one came along.
        val keep = buildSet {
            addAll(mine.sortedBy { it.score }.take(RETAINED_PER_GAME).map { it.id })
            addAll(
                mine.sortedByDescending { it.localSequence }
                    .take(Board.RECENT_COUNT)
                    .map { it.id }
            )
        }
        entries.removeAll { it.game == game && it.id !in keep }
    }

    /** Removes every local entry. Used by the settings screen between events. */
    fun clear() {
        entries.clear()
        nextLocalId = -1L
        nextSequence = 0L
        save()
    }

    // ------------------------------------------------------------- persistence

    private fun load() {
        if (!file.exists()) return

        val stored = runCatching {
            json.decodeFromString<List<Entry>>(file.readText())
        }.getOrNull()
            // A corrupt file must not stop the app from opening at a booth.
            // Start clean rather than crashing on launch.
            ?: return

        entries.addAll(stored)
        nextLocalId = (stored.minOfOrNull { it.id } ?: 0L) - 1L
        nextSequence = (stored.maxOfOrNull { it.localSequence } ?: -1L) + 1L
    }

    private fun save() {
        runCatching {
            val text = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Entry.serializer()), entries)

            // Write to a temporary file and rename, so a crash mid-write cannot
            // leave a half-written file that loses the whole day's scores.
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(text)
            temporary.renameTo(file)
        }
    }
}
