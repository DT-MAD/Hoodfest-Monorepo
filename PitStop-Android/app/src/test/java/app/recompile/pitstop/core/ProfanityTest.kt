package app.recompile.pitstop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProfanityTest {

    @Test
    fun `innocent names are never rejected`() {
        val clean = listOf(
            "MIKE", "KIKI", "KARL", "JACK", "NIKKI", "MCKENNA", "BUCK",
            "ACE", "RIO", "TURBO", "SPEEDY", "LIGHTNING", "DALE JR",
            "CASSIDY", "GLASS", "BASS", "CLASSIC", "GRASS", "RACCOON",
            "ESSEX", "SUSSEX", "ASSIST", "MASSIVE", "PASS", "BRASS",
            "ANA", "SCOTT", "MATT", "HANNAH", "AARON", "LEE", "BOBBY",
            "O'NEIL", "JEAN-LUC", "T_REX", "X", "007", "PIT CREW 3",
        )
        for (name in clean) {
            assertNull("$name was rejected on ${Profanity.match(name)}", Profanity.match(name))
        }
    }

    @Test
    fun `blocked names are caught including obvious evasions`() {
        val dirty = listOf(
            "SHIT", "shit", "Sh1t", "SH!T", "s.h.i.t", "s h i t", "shhhiiit",
            "FUCK", "f u c k", "FUUUUCK", "FFFUUUCCCKKK", "fuck you",
            "ASS", "A55", "@55", "BUTTHOLE", "DICKHEAD", "B1TCH", "BITCH",
            "NAZI", "N4ZI", "HITLER", "PENIS", "P3N15", "BOOBS", "WHORE",
            "CRAPPY", "TURD", "DUMBASS", "JACKASS",
        )
        for (name in dirty) {
            assertNotNull("$name was let through", Profanity.match(name))
        }
    }

    @Test
    fun `normalization folds leetspeak and strips punctuation`() {
        assertEquals("mike", Profanity.normalize("MIKE"))
        assertEquals("mike", Profanity.normalize("M!K3"))
        assertEquals("shit", Profanity.normalize("s.h.i.t"))
        assertEquals("ass", Profanity.normalize("a 5 5"))
        assertEquals("oneil", Profanity.normalize("O'Neil"))
        assertEquals("", Profanity.normalize(""))
    }

    @Test
    fun `repeated characters collapse`() {
        assertEquals("fuck", Profanity.collapseRepeats("fuuuck"))
        assertEquals("fuck", Profanity.collapseRepeats("ffffuuuucccckkkk"))
        assertEquals("mike", Profanity.collapseRepeats("mike"))
        assertEquals("a", Profanity.collapseRepeats("aaaa"))
        assertEquals("", Profanity.collapseRepeats(""))
    }

    /**
     * Regression test. An earlier version of the Go filter collapsed repeated
     * characters before matching, which turned the blocked term "kkk" into "k"
     * and then rejected every name containing the letter K — "MIKE" included.
     */
    @Test
    fun `short blocked terms do not match everything`() {
        for (name in listOf("MIKE", "KATIE", "KYLE", "NIKKI", "KKARL")) {
            assertNull("$name tripped an over-broad blocked term", Profanity.match(name))
        }
    }

    @Test
    fun `empty and symbol-only input is clean`() {
        // The name validator rejects these for being empty; the filter's job is
        // only to spot blocked words.
        for (name in listOf("", "   ", "---")) {
            assertNull(Profanity.match(name))
        }
    }
}
