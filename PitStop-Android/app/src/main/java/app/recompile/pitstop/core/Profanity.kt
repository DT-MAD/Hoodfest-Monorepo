package app.recompile.pitstop.core

/**
 * Screens player names before they reach a leaderboard that is displayed on a
 * screen in public, at a school event, in front of families.
 *
 * Mirrored from PitStop-Web/internal/profanity/profanity.go. The approach is
 * deliberately simple and deliberately strict: normalize away the obvious
 * evasions, then look for blocked terms as substrings. It will produce false
 * positives on innocent names containing a blocked substring. At a booth that
 * trade-off is the right way round — a visitor asked to pick a different name
 * loses three seconds; the alternative goes on the big screen.
 */
object Profanity {

    /**
     * Terms rejected anywhere inside a normalized name. Lowercase and
     * alphanumeric only, matching what [normalize] produces. Kept short and
     * root-like so suffixes and compounds are caught without their own lines.
     */
    private val blocked = listOf(
        "anal", "anus", "arse", "ass", "bastard", "bitch", "blowjob", "bollock",
        "boner", "boob", "bugger", "bullshit", "butthole", "clit", "cock", "coon",
        "crap", "cum", "cunt", "dick", "dildo", "dyke", "fag", "fuck", "goddamn",
        "handjob", "hitler", "homo", "jerkoff", "jizz", "kike", "kkk", "nazi",
        "nigg", "nutsack", "penis", "piss", "poon", "porn", "prick", "pube",
        "pussy", "queer", "rape", "rapist", "retard", "rimjob", "scrotum", "semen",
        "sex", "shit", "slut", "spic", "testicle", "tits", "titties", "turd",
        "twat", "vagina", "wank", "whore", "wetback",
    )

    /**
     * Innocent words the substring check would otherwise catch. Compared
     * against the whole normalized name.
     */
    private val allowed = setOf(
        "assist", "bass", "brass", "cascade", "cassidy", "classic", "essex",
        "glass", "grass", "massive", "pass", "raccoon", "sussex",
    )

    /** Characters people substitute for letters to sneak something past a filter. */
    private val leet = mapOf(
        '4' to 'a', '@' to 'a', '^' to 'a',
        '8' to 'b',
        '(' to 'c', '<' to 'c', '{' to 'c',
        '3' to 'e',
        '6' to 'g', '9' to 'g',
        '1' to 'i', '!' to 'i', '|' to 'i',
        '0' to 'o',
        '5' to 's', '$' to 's',
        '7' to 't', '+' to 't',
        '2' to 'z',
    )

    /**
     * A term that collapses shorter than this is only matched in its plain
     * form. Collapsing shortens a term, and one that collapses to a character
     * or two ("kkk" becomes "k") would match almost any name.
     */
    private const val MIN_COLLAPSED_LENGTH = 4

    /**
     * Reduces a name to the form the blocklist is matched against: lowercased,
     * leetspeak folded back to letters, everything else removed.
     *
     * Repeated characters are deliberately NOT collapsed here; see
     * [collapseRepeats].
     */
    fun normalize(name: String): String = buildString(name.length) {
        for (character in name.lowercase()) {
            val folded = leet[character] ?: character
            if (folded.isAsciiLetterOrDigit()) append(folded)
        }
    }

    /**
     * Squashes runs of the same character down to one, so "ffffuuuucccckkkk"
     * reduces to "fuck". A second, more aggressive pass applied alongside the
     * plain normalized form rather than instead of it.
     */
    fun collapseRepeats(value: String): String = buildString(value.length) {
        var previous: Char? = null
        for (character in value) {
            if (character != previous) {
                append(character)
                previous = character
            }
        }
    }

    /** Whether a name is acceptable for public display. */
    fun isClean(name: String): Boolean = match(name) == null

    /**
     * The blocked term a name trips on, or null if it is clean. Useful in
     * tests; never show the matched term back to the player.
     */
    fun match(name: String): String? {
        val plain = normalize(name)
        if (plain.isEmpty() || plain in allowed) return null

        val collapsed = collapseRepeats(plain)

        for (term in blocked) {
            if (plain.contains(term)) return term

            // Catch padded evasions like "fuuuuck", but only for terms that
            // stay distinctive once collapsed.
            val collapsedTerm = collapseRepeats(term)
            if (collapsedTerm.length >= MIN_COLLAPSED_LENGTH && collapsed.contains(collapsedTerm)) {
                return term
            }
        }
        return null
    }

    /**
     * Deliberately ASCII-only: the name validator rejects everything else, so a
     * full-width or Cyrillic lookalike cannot reach the blocklist unnoticed.
     */
    private fun Char.isAsciiLetterOrDigit(): Boolean = this in 'a'..'z' || this in '0'..'9'
}
