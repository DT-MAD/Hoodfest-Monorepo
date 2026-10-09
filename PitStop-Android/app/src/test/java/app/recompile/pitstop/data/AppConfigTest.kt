package app.recompile.pitstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The address an operator types at the booth has to work. This is the one piece
 * of configuration that gets changed under time pressure, by hand, on a tablet.
 */
class AppConfigTest {

    @Test
    fun `a full url is accepted unchanged`() {
        assertEquals("http://10.0.2.2:8080", AppConfig.normalizedUrl("http://10.0.2.2:8080"))
        assertEquals("http://192.168.1.50:8080", AppConfig.normalizedUrl("http://192.168.1.50:8080"))
        assertEquals("https://pitstop.example.com", AppConfig.normalizedUrl("https://pitstop.example.com"))
    }

    @Test
    fun `a bare host and port gets a scheme`() {
        assertEquals("http://192.168.1.50:8080", AppConfig.normalizedUrl("192.168.1.50:8080"))
        assertEquals("http://booth.local:8080", AppConfig.normalizedUrl("booth.local:8080"))
    }

    @Test
    fun `whitespace and trailing slashes are trimmed`() {
        assertEquals("http://10.0.2.2:8080", AppConfig.normalizedUrl("  http://10.0.2.2:8080/  "))
        assertEquals("http://10.0.2.2:8080", AppConfig.normalizedUrl("http://10.0.2.2:8080///"))
    }

    @Test
    fun `the compiled-in default is itself a usable address`() {
        assertEquals(AppConfig.DEFAULT_BASE_URL, AppConfig.normalizedUrl(AppConfig.DEFAULT_BASE_URL))
    }

    @Test
    fun `nonsense is rejected`() {
        assertNull(AppConfig.normalizedUrl(""))
        assertNull(AppConfig.normalizedUrl("   "))
    }
}
