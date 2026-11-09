package dev.betterendfield.next

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.view.ViewTreeObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner

/** Native status is sampled only while this panel is composed. */
@Composable
internal fun MediaScreenControls(preview: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    var shown by remember(view) { mutableStateOf(view.isShown) }
    var resumed by remember(view) { mutableStateOf(false) }
    val controller = remember { MediaScreenControl.nativeControl() }
    var snapshot by remember { mutableStateOf(MediaScreenControl.Snapshot(MediaScreenControl.Phase.UNAVAILABLE, "")) }
    var submission by remember { mutableStateOf("") }
    DisposableEffect(view) {
        val lifecycle = view.findViewTreeLifecycleOwner()?.lifecycle
        resumed = lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
        }
        val layout = ViewTreeObserver.OnGlobalLayoutListener { shown = view.isShown }
        lifecycle?.addObserver(observer)
        view.viewTreeObserver.addOnGlobalLayoutListener(layout)
        onDispose {
            lifecycle?.removeObserver(observer)
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnGlobalLayoutListener(layout)
        }
    }
    LaunchedEffect(preview, shown, resumed) {
        if (!preview && shown && resumed) while (isActive) {
            val next = controller.read()
            if (next != snapshot) submission = ""
            snapshot = next
            delay(500)
        }
    }
    fun send(action: Int, value: Double = 0.0) {
        if (preview || !shown || !resumed) return
        val result = controller.submit(action, value)
        snapshot = result.snapshot()
        submission = context.getString(if (result.accepted()) R.string.media_runtime_queued else R.string.media_runtime_rejected)
    }
    val enabled = !preview && shown && resumed && snapshot.phase() != MediaScreenControl.Phase.UNAVAILABLE
    val playing = snapshot.phase() == MediaScreenControl.Phase.PLAYING || snapshot.phase() == MediaScreenControl.Phase.PAUSED
    OverlaySection(stringResource(R.string.media_runtime_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(mediaScreenStatusText(snapshot), color = Be.Colors.textSecondary, fontSize = 12.sp)
            OverlayAction(stringResource(R.string.media_runtime_start), enabled = enabled) { send(0) }
            OverlayAction(stringResource(R.string.media_runtime_stop), enabled = enabled) { send(1) }
            OverlayAction(stringResource(R.string.media_runtime_pause), enabled = enabled && playing) { send(2) }
            OverlayAction(stringResource(R.string.media_runtime_seek_back), enabled = enabled && playing && snapshot.detail() == "video") { send(3, -10.0) }
            OverlayAction(stringResource(R.string.media_runtime_seek_forward), enabled = enabled && playing && snapshot.detail() == "video") { send(3, 10.0) }
            if (submission.isNotEmpty()) Text(submission, color = Be.Colors.textSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun mediaScreenStatusText(snapshot: MediaScreenControl.Snapshot): String {
    val detail = snapshot.detail()
    if (snapshot.phase() == MediaScreenControl.Phase.ERROR) return stringResource(R.string.media_runtime_error, detail)
    val reason = when (detail) {
        "no_screens_selected" -> R.string.media_runtime_no_screens
        "screen_released" -> R.string.media_runtime_screen_released
        "desk_screens_unavailable" -> R.string.media_runtime_desk_unavailable
        "settings_changed" -> R.string.media_runtime_settings_changed
        else -> null
    }
    if (reason != null) return stringResource(reason)
    return stringResource(when (snapshot.phase()) {
        MediaScreenControl.Phase.IDLE -> R.string.media_runtime_idle
        MediaScreenControl.Phase.LOADING -> R.string.media_runtime_loading
        MediaScreenControl.Phase.PLAYING -> R.string.media_runtime_playing
        MediaScreenControl.Phase.PAUSED -> R.string.media_runtime_paused
        MediaScreenControl.Phase.ENDED -> R.string.media_runtime_ended
        else -> R.string.media_runtime_unavailable
    })
}
