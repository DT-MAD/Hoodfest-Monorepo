package app.recompile.pitstop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NameValidatorTest {

    @Test
    fun `canonicalization trims collapses whitespace and uppercases`() {
        assertEquals("ACE", NameValidator.canonical("  ace  "))
        assertEquals("DALE JR", NameValidator.canonical("dale   jr"))
        assertEquals("SPEEDY", NameValidator.canonical("Speedy"))
        assertEquals("TURBO", NameValidator.canonical("\tturbo\n"))
        assertEquals("PIT CREW 3", NameValidator.canonical("pit   crew   3"))
        assertEquals("", NameValidator.canonical(""))
        assertEquals("", NameValidator.canonical("     "))
    }

    @Test
    fun `racing names are accepted`() {
        val good = listOf(
            "ACE", "ace", "Rio", "TURBO", "DALE JR", "O'NEIL",
            "JEAN-LUC", "T_REX", "007", "PIT CREW 3", "X",
        )
        for (name in good) {
            val result = NameValidator.validate(name)
            assertNull("$name was refused: ${result.failure}", result.failure)
            assertEquals(NameValidator.canonical(name), result.canonical)
        }
    }

    @Test
    fun `empty and punctuation-only names are refused`() {
        for (name in listOf("", "   ", "---", "'")) {
            assertEquals(name, NameValidator.Failure.EMPTY, NameValidator.failureFor(name))
        }
    }

    @Test
    fun `the maximum length is allowed and one more is not`() {
        val exactly = "A".repeat(NameValidator.MAX_LENGTH)
        assertNull(NameValidator.failureFor(exactly))

        val oneOver = "A".repeat(NameValidator.MAX_LENGTH + 1)
        assertEquals(NameValidator.Failure.TOO_LONG, NameValidator.failureFor(oneOver))
    }

    @Test
    fun `names outside the allowed character set are refused`() {
        // Non-ASCII must be refused: the profanity normalizer strips other
        // scripts, so a full-width or Cyrillic lookalike would bypass it.
        val bad = listOf("ACE<script>", "ACE;DROP", "CAFÉ", "ＦＵＣＫ", "ЖУК", "ACE🏁")
        for (name in bad) {
            assertEquals(name, NameValidator.Failure.BAD_CHARACTER, NameValidator.failureFor(name))
        }
    }

    @Test
    fun `profane names are refused`() {
        for (name in listOf("SHIT", "A55", "DUMBASS", "B1TCH")) {
            assertEquals(name, NameValidator.Failure.UNAVAILABLE, NameValidator.failureFor(name))
        }
    }

    @Test
    fun `a refusal still reports the canonical form for the UI to show`() {
        val result = NameValidator.validate("  shit  ")
        assertEquals(NameValidator.Failure.UNAVAILABLE, result.failure)
        assertEquals("SHIT", result.canonical)
    }

    @Test
    fun `every failure has a message fit to show a player`() {
        for (failure in NameValidator.Failure.entries) {
            assertTrue(failure.message.isNotEmpty())
        }
    }
}
