package app.recompile.pitstop.data

import app.recompile.pitstop.core.GameId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single place the rest of the app asks about leaderboards.
 *
 * It prefers the server and falls back to the device. The fallback is quiet by
 * design: a visitor at a booth should never see a network error. They see the
 * local board and a small "On this tablet" badge, and the booth keeps moving.
 */
class LeaderboardRepository(
    private val local: LocalLeaderboardStore,
    private val api: LeaderboardApi,
) {

    /** Where the displayed boards came from. */
    enum class Source {
        SERVER,
        DEVICE;

        val label: String
            get() = when (this) {
                SERVER -> "Live leaderboard"
                DEVICE -> "On this tablet"
            }
    }

    /** What happened to a submitted score. */
    data class Outcome(val entry: Entry, val source: Source) {
        /** True when the score reached the leaderboard on the big screen. */
        val reachedServer: Boolean get() = source == Source.SERVER
    }

    private val _boards = MutableStateFlow(local.boards())
    val boards: StateFlow<Boards> = _boards.asStateFlow()

    private val _source = MutableStateFlow(Source.DEVICE)
    val source: StateFlow<Source> = _source.asStateFlow()

    /** Guards against two refreshes racing each other into the state flow. */
    private val mutex = Mutex()

    val localCount: Int get() = local.count

    /** Refreshes the boards, preferring the server. */
    suspend fun refresh() = mutex.withLock {
        when (val result = api.boards()) {
            is LeaderboardApi.Result.Ok -> {
                _boards.value = result.value
                _source.value = Source.SERVER
            }
            is LeaderboardApi.Result.Err -> {
                _boards.value = local.boards()
                _source.value = Source.DEVICE
            }
        }
    }

    fun board(game: GameId): Board = _boards.value.board(game)

    /**
     * Submits a score.
     *
     * It is always written to the device first, so a score is never lost to a
     * network failure, and then sent onward if the server can be reached.
     */
    suspend fun submit(
        game: GameId,
        name: String,
        score: Int,
        detail: Map<String, Int>? = null,
    ): Outcome {
        val localEntry = local.add(game, name, score)

        return when (val result = api.submit(ScoreSubmission(game, name, score, detail = detail))) {
            is LeaderboardApi.Result.Ok -> {
                refresh()
                Outcome(result.value.entry, Source.SERVER)
            }
            is LeaderboardApi.Result.Err -> {
                mutex.withLock {
                    _boards.value = local.boards()
                    _source.value = Source.DEVICE
                }
                Outcome(localEntry, Source.DEVICE)
            }
        }
    }

    /** Clears the device's scores. Server entries are untouched. */
    suspend fun clearLocal() {
        local.clear()
        refresh()
    }
}
