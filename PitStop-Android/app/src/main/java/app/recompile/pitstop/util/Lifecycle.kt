package app.recompile.pitstop.util

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Runs [action] when the screen stops being visible — backgrounded, a call
 * arriving, the screen locking.
 *
 * Every game uses this to invalidate an in-flight round. A round whose timing
 * cannot be vouched for must not produce a score.
 */
@Composable
fun OnStopped(action: () -> Unit) {
    val currentAction by rememberUpdatedState(action)
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) currentAction()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * Haptic feedback, always additive. Every state it reinforces is also shown on
 * screen, because the booth is loud and the tablet is shared.
 */
fun View.performHaptic(constant: Int = HapticFeedbackConstants.CONTEXT_CLICK) {
    performHapticFeedback(constant)
}
