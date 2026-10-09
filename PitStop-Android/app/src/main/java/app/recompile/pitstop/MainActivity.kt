package app.recompile.pitstop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import app.recompile.pitstop.ui.theme.Asphalt
import app.recompile.pitstop.ui.theme.PitStopTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PitStopTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Asphalt)
                        .systemBarsPadding()
                ) {
                    PitStopApp()
                }
            }
        }
    }
}
