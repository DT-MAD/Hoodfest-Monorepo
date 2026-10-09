package app.recompile.pitstop.core

/**
 * Validates and canonicalizes the names that go on a leaderboard. Mirrored from
 * PitStop-Web/internal/name/name.go, so a name accepted here is never rejected
 * by the server.
 */
object NameValidator {

    const val MIN_LENGTH = 1

    /** Sixteen characters fits the leaderboard column on the booth display. */
    const val MAX_LENGTH = 16

    /** The non-alphanumeric characters a name may contain. */
    private val allowedPunctuation = setOf('-', '_', '\'', ' ')

    /** Why a name was refused. The [message] is shown under the text field. */
    enum class Failure(val message: String) {
        EMPTY("Enter a name"),
        TOO_LONG("Name must be $MAX_LENGTH characters or fewer"),
        BAD_CHARACTER("Use only letters, numbers, spaces, - _ or '"),
        UNAVAILABLE("Pick a different name"),
    }

    /**
     * The outcome of validating a name. [canonical] is populated either way, so
     * the UI can show the player what was made of their input alongside a
     * refusal.
     */
    data class Result(val canonical: String, val failure: Failure?) {
        val isValid: Boolean get() = failure == null
    }

    /**
     * Trims, collapses internal whitespace, and uppercases for display.
     * Does not validate.
     */
    fun canonical(raw: String): String =
        raw.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ").uppercase()

    /** Canonicalizes a name and reports whether it may be displayed. */
    fun validate(raw: String): Result {
        val name = canonical(raw)

        if (name.length < MIN_LENGTH) return Result(name, Failure.EMPTY)
        if (name.length > MAX_LENGTH) return Result(name, Failure.TOO_LONG)

        // Deliberately ASCII-only. The profanity normalizer strips other
        // scripts, so accepting them would let a full-width or Cyrillic
        // lookalike spelling walk straight past the blocklist.
        if (name.any { !it.isAsciiAlphanumeric() && it !in allowedPunctuation }) {
            return Result(name, Failure.BAD_CHARACTER)
        }

        // A name made entirely of punctuation passes the check above but is not
        // a name.
        if (name.none { it.isAsciiAlphanumeric() }) return Result(name, Failure.EMPTY)

        if (!Profanity.isClean(name)) return Result(name, Failure.UNAVAILABLE)

        return Result(name, null)
    }

    /**
     * Convenience for live feedback as the player types: the reason the name is
     * not yet acceptable, or null if it is.
     */
    fun failureFor(raw: String): Failure? = validate(raw).failure

    private fun Char.isAsciiAlphanumeric(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9'
}
