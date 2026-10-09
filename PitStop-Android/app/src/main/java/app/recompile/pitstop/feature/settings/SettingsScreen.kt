package app.recompile.pitstop.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recompile.pitstop.data.AppConfig
import app.recompile.pitstop.data.LeaderboardApi
import app.recompile.pitstop.data.LeaderboardRepository
import app.recompile.pitstop.ui.components.ButtonKind
import app.recompile.pitstop.ui.components.PitStopButton
import app.recompile.pitstop.ui.components.PitStopCard
import app.recompile.pitstop.ui.theme.Asphalt
import app.recompile.pitstop.ui.theme.Hairline
import app.recompile.pitstop.ui.theme.Ink
import app.recompile.pitstop.ui.theme.InkMuted
import app.recompile.pitstop.ui.theme.PitStopDimens
import app.recompile.pitstop.ui.theme.RacingYellow
import app.recompile.pitstop.ui.theme.SignalRed
import kotlinx.coroutines.launch

/**
 * For the booth operator, not the visitor. Reached by a long press on the home
 * screen logo so it stays out of the way during the event.
 */
@Composable
fun SettingsScreen(
    config: AppConfig,
    api: LeaderboardApi,
    repository: LeaderboardRepository,
    onDone: () -> Unit,
) {
    var address by remember { mutableStateOf(config.baseUrl()) }
    var checkState by remember { mutableStateOf(CheckState.IDLE) }
    var isConfirmingClear by remember { mutableStateOf(false) }
    var localCount by remember { mutableStateOf(repository.localCount) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(Asphalt), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
                .padding(PitStopDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Booth settings", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Black)

            PitStopCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(PitStopDimens.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Leaderboard server", color = InkMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it; checkState = CheckState.IDLE },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("192.168.1.50:8080", color = InkMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Ink,
                            unfocusedTextColor = Ink,
                            focusedBorderColor = RacingYellow,
                            unfocusedBorderColor = Hairline,
                            cursorColor = RacingYellow,
                        ),
                    )

                    checkState.message?.let { message ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(checkState.icon, null, tint = checkState.tint, modifier = Modifier.size(18.dp))
                            Text(message, color = checkState.tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Text(
                        "The scheme is optional — \"192.168.1.50:8080\" works. Scores are always " +
                            "saved on this tablet first, so the games keep working if the server " +
                            "is unreachable.",
                        color = InkMuted,
                        fontSize = 13.sp,
                    )

                    PitStopButton(
                        "Test connection",
                        kind = ButtonKind.SECONDARY,
                        enabled = checkState != CheckState.CHECKING,
                    ) {
                        val normalized = AppConfig.normalizedUrl(address)
                        if (normalized == null) {
                            checkState = CheckState.INVALID
                        } else {
                            checkState = CheckState.CHECKING
                            scope.launch {
                                // Check the typed address without committing to it.
                                checkState = if (api.checkHealth(normalized)) {
                                    CheckState.REACHABLE
                                } else {
                                    CheckState.UNREACHABLE
                                }
                            }
                        }
                    }

                    if (!config.isUsingDefaultAddress()) {
                        PitStopButton("Reset to default", kind = ButtonKind.QUIET) {
                            address = AppConfig.DEFAULT_BASE_URL
                            checkState = CheckState.IDLE
                        }
                    }
                }
            }

            PitStopCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(PitStopDimens.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("On this device", color = InkMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Saved on this tablet", color = InkMuted, fontSize = 16.sp)
                        Text("$localCount", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Clearing affects only this tablet. Scores already on the booth " +
                            "leaderboard stay there.",
                        color = InkMuted,
                        fontSize = 13.sp,
                    )
                    PitStopButton(
                        "Clear local scores",
                        kind = ButtonKind.SECONDARY,
                        enabled = localCount > 0,
                    ) { isConfirmingClear = true }
                }
            }

            PitStopButton("Done") {
                AppConfig.normalizedUrl(address)?.let(config::setBaseUrl)
                scope.launch { repository.refresh() }
                onDone()
            }
        }
    }

    if (isConfirmingClear) {
        AlertDialog(
            onDismissRequest = { isConfirmingClear = false },
            containerColor = app.recompile.pitstop.ui.theme.Slate,
            title = { Text("Clear every score saved on this tablet?", color = Ink) },
            text = {
                Text(
                    "Scores already on the booth leaderboard are not affected.",
                    color = InkMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    isConfirmingClear = false
                    scope.launch {
                        repository.clearLocal()
                        localCount = repository.localCount
                    }
                }) { Text("Clear scores", color = SignalRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { isConfirmingClear = false }) {
                    Text("Keep them", color = InkMuted)
                }
            },
        )
    }
}

private enum class CheckState {
    IDLE, CHECKING, REACHABLE, UNREACHABLE, INVALID;

    val message: String?
        get() = when (this) {
            IDLE -> null
            CHECKING -> "Checking…"
            REACHABLE -> "Server reachable"
            UNREACHABLE -> "No answer from that address"
            INVALID -> "That doesn't look like an address"
        }

    val icon
        get() = when (this) {
            IDLE, CHECKING -> Icons.Filled.HourglassEmpty
            REACHABLE -> Icons.Filled.CheckCircle
            UNREACHABLE, INVALID -> Icons.Filled.Warning
        }

    val tint: Color
        get() = when (this) {
            REACHABLE -> RacingYellow
            UNREACHABLE, INVALID -> SignalRed
            else -> InkMuted
        }
}
