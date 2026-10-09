package app.recompile.pitstop.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * The HTTP client for the Pit Stop server.
 *
 * Built on HttpURLConnection rather than OkHttp or Retrofit. There are two
 * endpoints; a dependency would be more code to read, not less.
 */
class LeaderboardApi(private val config: AppConfig) {

    /** Why a call did not succeed. */
    sealed class Failure(val message: String) {
        data object NoServerConfigured : Failure("No leaderboard server is configured.")
        data object Unreachable : Failure("The leaderboard server could not be reached.")
        /** The server refused the request and said why, in words fit for a player. */
        class Rejected(message: String) : Failure(message)
        class UnexpectedStatus(val code: Int) : Failure("The server replied with status $code.")
        data object MalformedResponse : Failure("The server's reply could not be read.")
    }

    /** Either a value or a reason there isn't one. */
    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        data class Err(val failure: Failure) : Result<Nothing>
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Short timeouts on purpose. At a booth, a server not answering within a few
     * seconds is a server the player should not be waiting on — fall back to the
     * local board instead.
     */
    private companion object {
        const val CONNECT_TIMEOUT_MS = 4_000
        const val READ_TIMEOUT_MS = 5_000
    }

    /** Fetches every board. */
    suspend fun boards(): Result<Boards> = request("/api/leaderboard", "GET") { body ->
        json.decodeFromString<Boards>(body)
    }

    /** Submits a score and returns the stored entry with the rank it earned. */
    suspend fun submit(submission: ScoreSubmission): Result<SubmissionResult> =
        request(
            path = "/api/scores",
            method = "POST",
            body = json.encodeToString(ScoreSubmission.serializer(), submission),
            headers = mapOf(
                "Content-Type" to "application/json",
                "X-API-Key" to AppConfig.API_KEY,
            ),
        ) { responseBody ->
            json.decodeFromString<SubmissionResult>(responseBody)
        }

    /**
     * Whether the server is reachable. Used by the settings screen to tell an
     * operator whether the address they typed actually works.
     */
    suspend fun checkHealth(baseUrl: String? = null): Boolean {
        val result = request("/healthz", "GET", baseUrlOverride = baseUrl) { it }
        return result is Result.Ok
    }

    // ---------------------------------------------------------------- plumbing

    private suspend fun <T> request(
        path: String,
        method: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        baseUrlOverride: String? = null,
        decode: (String) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        val base = (baseUrlOverride ?: config.baseUrl())?.let(AppConfig::normalizedUrl)
            ?: return@withContext Result.Err(Failure.NoServerConfigured)

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body.toByteArray()) }
                }
            }

            val status = connection.responseCode
            val text = if (status in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            if (status !in 200..299) {
                // Prefer the server's own message, so a refused name reads
                // "Pick a different name" rather than "status 422".
                val apiMessage = runCatching {
                    json.decodeFromString<ApiErrorBody>(text).error.message
                }.getOrNull()

                return@withContext Result.Err(
                    if (apiMessage != null) Failure.Rejected(apiMessage)
                    else Failure.UnexpectedStatus(status)
                )
            }

            runCatching { decode(text) }
                .fold(
                    onSuccess = { Result.Ok(it) },
                    onFailure = { Result.Err(Failure.MalformedResponse) },
                )
        } catch (_: IOException) {
            Result.Err(Failure.Unreachable)
        } catch (_: SecurityException) {
            Result.Err(Failure.Unreachable)
        } finally {
            connection?.disconnect()
        }
    }
}
