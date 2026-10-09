package app.recompile.pitstop.data

import android.content.Context
import androidx.core.content.edit

/**
 * Where the app looks for the leaderboard server.
 *
 * The booth server usually runs on a laptop on the venue's network, and its
 * address is not known until the morning of the event. So the compiled-in
 * default is only a starting point: the operator can change it on site from the
 * settings screen, reached by a long press on the home screen logo.
 */
class AppConfig(context: Context) {

    companion object {
        /**
         * Where the server lives if nobody has said otherwise. Change this to
         * the booth laptop's address before the event, or set it on site in
         * Settings.
         *
         * 10.0.2.2 is the host machine as seen from the Android emulator.
         */
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8080"

        /**
         * The shared key the server expects in X-API-Key.
         *
         * This value ships inside the app binary, so treat it as "keeps honest
         * people honest", not as a real secret. It must match the server's
         * API_KEY, and must NOT be the server's ADMIN_KEY.
         */
        const val API_KEY = "change-me-app-key"

        private const val PREFS = "pitstop.settings"
        private const val KEY_BASE_URL = "baseUrl"

        /**
         * Accepts what an operator would actually type — "192.168.1.50:8080",
         * with or without a scheme, with or without a trailing slash. Returns
         * null if what was typed cannot be a server address.
         */
        fun normalizedUrl(raw: String): String? {
            var text = raw.trim()
            if (text.isEmpty()) return null

            if (!text.contains("://")) text = "http://$text"
            text = text.trimEnd('/')

            val host = runCatching { java.net.URI(text).host }.getOrNull()
            return if (host.isNullOrEmpty()) null else text
        }
    }

    // SharedPreferences rather than DataStore: this is one short string read at
    // startup and written from a settings screen. DataStore would make every
    // read a coroutine for no benefit.
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The address in use, as typed. */
    fun baseUrl(): String = prefs.getString(KEY_BASE_URL, null) ?: DEFAULT_BASE_URL

    fun setBaseUrl(value: String) {
        prefs.edit { putString(KEY_BASE_URL, value) }
    }

    fun isUsingDefaultAddress(): Boolean = baseUrl() == DEFAULT_BASE_URL

    fun resetToDefault() {
        prefs.edit { remove(KEY_BASE_URL) }
    }
}
