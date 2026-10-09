package app.recompile.pitstop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.recompile.pitstop.core.GameId
import app.recompile.pitstop.data.AppConfig
import app.recompile.pitstop.data.LeaderboardApi
import app.recompile.pitstop.data.LeaderboardRepository
import app.recompile.pitstop.data.LocalLeaderboardStore
import app.recompile.pitstop.feature.fill.FillScreen
import app.recompile.pitstop.feature.home.HomeScreen
import app.recompile.pitstop.feature.pitstop.PitStopScreen
import app.recompile.pitstop.feature.reaction.ReactionScreen
import app.recompile.pitstop.feature.settings.SettingsScreen
import app.recompile.pitstop.feature.shared.LeaderboardScreen
import app.recompile.pitstop.feature.shared.SubmitNameScreen

/**
 * Routes. The submit route carries the score so a rotation on the name screen
 * cannot lose it.
 */
private object Routes {
    const val HOME = "home"
    const val LEADERBOARD = "leaderboard"
    const val SETTINGS = "settings"
    const val REACTION = "game/reaction"
    const val FILL = "game/fill"
    const val PITSTOP = "game/pitstop"

    const val SUBMIT = "submit/{game}/{score}/{detail}"

    fun submit(game: GameId, score: Int, detail: Map<String, Int>): String {
        // A compact encoding rather than JSON, to keep the route readable and
        // free of characters that would need escaping.
        val encoded = detail.entries.joinToString(",") { "${it.key}=${it.value}" }.ifEmpty { "-" }
        return "submit/${game.wire}/$score/$encoded"
    }

    fun decodeDetail(encoded: String?): Map<String, Int>? {
        if (encoded.isNullOrEmpty() || encoded == "-") return null
        return encoded.split(",").mapNotNull { pair ->
            val (key, value) = pair.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
            value.toIntOrNull()?.let { key to it }
        }.toMap().takeIf { it.isNotEmpty() }
    }
}

@Composable
fun PitStopApp() {
    val context = LocalContext.current

    // Manual wiring. Three objects and no lifecycle subtleties — a dependency
    // injection framework here would be more to read, not less.
    val config = remember { AppConfig(context) }
    val api = remember { LeaderboardApi(config) }
    val repository = remember {
        LeaderboardRepository(LocalLeaderboardStore(context), api)
    }

    val navController = rememberNavController()

    fun goHome() {
        navController.popBackStack(Routes.HOME, inclusive = false)
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                repository = repository,
                onPlay = { game ->
                    navController.navigate(
                        when (game) {
                            GameId.REACTION -> Routes.REACTION
                            GameId.FILL -> Routes.FILL
                            GameId.PITSTOP -> Routes.PITSTOP
                        }
                    )
                },
                onLeaderboard = { navController.navigate(Routes.LEADERBOARD) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.REACTION) {
            ReactionScreen(
                onSubmit = { score ->
                    navController.navigate(Routes.submit(GameId.REACTION, score, emptyMap()))
                },
                onQuit = ::goHome,
            )
        }

        composable(Routes.FILL) {
            FillScreen(
                onSubmit = { score, detail ->
                    navController.navigate(Routes.submit(GameId.FILL, score, detail))
                },
                onQuit = ::goHome,
            )
        }

        composable(Routes.PITSTOP) {
            PitStopScreen(
                onSubmit = { score, detail ->
                    navController.navigate(Routes.submit(GameId.PITSTOP, score, detail))
                },
                onQuit = ::goHome,
            )
        }

        composable(
            route = Routes.SUBMIT,
            arguments = listOf(
                navArgument("game") { type = NavType.StringType },
                navArgument("score") { type = NavType.IntType },
                navArgument("detail") { type = NavType.StringType },
            ),
        ) { entry ->
            val game = GameId.fromWire(entry.arguments?.getString("game").orEmpty())
            val score = entry.arguments?.getInt("score") ?: 0

            if (game == null) {
                // An unreachable route in practice, but better to go home than
                // to crash in front of a visitor.
                goHome()
            } else {
                SubmitNameScreen(
                    game = game,
                    score = score,
                    detail = Routes.decodeDetail(entry.arguments?.getString("detail")),
                    repository = repository,
                    onDone = ::goHome,
                )
            }
        }

        composable(Routes.LEADERBOARD) {
            LeaderboardScreen(repository = repository)
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                config = config,
                api = api,
                repository = repository,
                onDone = { navController.popBackStack() },
            )
        }
    }
}
