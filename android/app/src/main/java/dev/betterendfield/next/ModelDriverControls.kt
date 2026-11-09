package dev.betterendfield.next

import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

/** Polls only while the real overlay view is visible and its game Activity is resumed. */
@Composable
internal fun ModelDriverControls(preview: Boolean) {
    val view = LocalView.current
    var shown by remember(view) { mutableStateOf(view.isShown) }
    var resumed by remember(view) { mutableStateOf(false) }
    val transport = remember {
        object : ModelDriverControl.Transport {
            override fun read(): String = NativeCommandBridge.customModelDriverStatus()
            override fun set(commandId: String, state: Int, speed: Int): Boolean =
                NativeCommandBridge.setCustomModelDriver(commandId, state, speed)
        }
    }
    var snapshot by remember { mutableStateOf(ModelDriverControl.Snapshot(emptyList(), ModelDriverControl.Error.UNAVAILABLE)) }
    var commandError by remember { mutableStateOf(ModelDriverControl.Error.NONE) }
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
        if (!preview && shown && resumed) {
            while (isActive) {
                snapshot = ModelDriverControl.read(transport)
                delay(500)
            }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.model_driver_title), color = Be.Colors.textPrimary)
        when {
            preview -> Text(stringResource(R.string.model_driver_preview), color = Be.Colors.textSecondary)
            !snapshot.available() -> Text(stringResource(R.string.model_driver_unavailable), color = Be.Colors.textSecondary)
            snapshot.drivers().isEmpty() -> Text(stringResource(R.string.model_driver_empty), color = Be.Colors.textSecondary)
        }
        if (commandError != ModelDriverControl.Error.NONE) {
            Text(stringResource(R.string.model_driver_rejected), color = Be.Colors.danger)
        }
        snapshot.drivers().forEach { driver ->
            fun send(state: Int, speed: Int = driver.speed()) {
                val result = ModelDriverControl.apply(transport, driver, state, speed)
                snapshot = result.snapshot()
                commandError = result.error()
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val status = when (driver.state()) {
                    1 -> R.string.model_driver_playing
                    2 -> R.string.model_driver_paused
                    3 -> R.string.model_driver_finished
                    else -> R.string.model_driver_stopped
                }
                Text(stringResource(R.string.model_driver_status, driver.name(), stringResource(status)),
                    color = Be.Colors.textPrimary)
                Text(stringResource(if (driver.once()) R.string.model_driver_once else R.string.model_driver_loop),
                    color = Be.Colors.textSecondary)
                val enabled = !preview && shown && resumed && snapshot.available()
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { send(1) }, enabled = enabled) {
                        Text(stringResource(if (driver.state() == 3) R.string.model_driver_replay else R.string.model_driver_start))
                    }
                    TextButton(onClick = { send(if (driver.state() == 2) 1 else 2) },
                        enabled = enabled && driver.state() in 1..2) {
                        Text(stringResource(if (driver.state() == 2) R.string.model_driver_resume else R.string.model_driver_pause))
                    }
                    TextButton(onClick = { send(0) }, enabled = enabled) {
                        Text(stringResource(R.string.model_driver_stop))
                    }
                }
                var speed by remember(driver.commandId(), driver.speed()) { mutableStateOf(driver.speed().toFloat()) }
                Text(stringResource(R.string.model_driver_speed, String.format(Locale.ROOT, "%.2f", speed / 1000)),
                    color = Be.Colors.textSecondary)
                Slider(value = speed, onValueChange = { speed = it },
                    valueRange = 250f..4000f, steps = 74,
                    enabled = enabled && driver.state() != 3,
                    onValueChangeFinished = { send(driver.state(), (speed / 50).toInt() * 50) })
            }
        }
    }
}
