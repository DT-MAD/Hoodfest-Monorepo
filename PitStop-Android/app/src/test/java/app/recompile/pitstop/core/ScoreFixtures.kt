package app.recompile.pitstop.core

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Loads spec/score-fixtures.json — the same file the Go and Swift test suites
 * read — so the three implementations of the scoring rules cannot drift apart
 * silently.
 */
@Serializable
data class ScoreFixtures(
    val reaction: List<Reaction>,
    val fill: List<Fill>,
    val pitstop: List<PitStop>,
    val bounds: List<Bound>,
    val format: List<Format>,
) {
    @Serializable
    data class Reaction(val name: String, val greenToTapMs: Int, val expected: Int)

    @Serializable
    data class Fill(
        val name: String,
        val dispensedCents: Int,
        val goalCents: Int,
        val expected: Int,
    )

    @Serializable
    data class PitStop(val name: String, val elapsedMs: Int, val misTaps: Int, val expected: Int)

    @Serializable
    data class Bound(val game: String, val score: Int, val inBounds: Boolean)

    @Serializable
    data class Format(val game: String, val score: Int, val expected: String)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Walks up from the working directory to find the repository root. Unit
         * tests run with the module directory as their working directory, but
         * searching rather than hard-coding "../" survives the module being
         * moved or the tests being run from elsewhere.
         */
        fun file(): File {
            var directory: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
            while (directory != null) {
                val candidate = File(directory, "spec/score-fixtures.json")
                if (candidate.isFile) return candidate
                directory = directory.parentFile
            }
            error(
                "Could not find spec/score-fixtures.json above ${System.getProperty("user.dir")}. " +
                    "It is the shared scoring fixture table and must be present."
            )
        }

        fun load(): ScoreFixtures = json.decodeFromString(file().readText())
    }
}

/** Resolves a fixture's game string to the enum, failing loudly on a typo. */
fun gameOf(wire: String): GameId =
    GameId.fromWire(wire) ?: error("Fixture names an unknown game: $wire")
