package dev.betterendfield.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun OverlaySection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, color = Be.Colors.textSecondary, fontSize = 11.sp,
            fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 2.dp))
        content()
    }
}

@Composable
internal fun OverlayAction(
    title: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier.fillMaxWidth().height(42.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .background(Be.Colors.overlayRow, RoundedCornerShape(11.dp))
            .border(1.dp, Be.Colors.outline, RoundedCornerShape(11.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = title },
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = Be.Colors.textPrimary, fontSize = 13.sp,
            fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun OverlayActionRow(
    first: String, firstClick: () -> Unit,
    second: String, secondClick: () -> Unit,
    secondEnabled: Boolean = true,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        OverlayAction(first, Modifier.weight(1f), onClick = firstClick)
        OverlayAction(second, Modifier.weight(1f), enabled = secondEnabled, onClick = secondClick)
    }
}

/** Sends exactly one press and one release, including pointer cancellation. */
@Composable
internal fun HoldControl(
    title: String,
    key: Int,
    description: String,
    modifier: Modifier = Modifier,
    callbacks: OverlaySurface.Callbacks,
) {
    Box(
        modifier.height(43.dp)
            .background(Be.Colors.overlayRow, RoundedCornerShape(11.dp))
            .border(1.dp, Be.Colors.outline, RoundedCornerShape(11.dp))
            .pointerInput(key) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    callbacks.hold(key, true, description)
                    try { waitForUpOrCancellation() }
                    finally { callbacks.hold(key, false, description) }
                }
            }
            .semantics { contentDescription = "$description，按住生效" },
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = Be.Colors.textPrimary, fontSize = 16.sp)
    }
}

@Composable
internal fun MovementPad(callbacks: OverlaySurface.Callbacks) {
    OverlaySection("镜头移动（按住）") {
        Column(
            Modifier.fillMaxWidth().background(Be.Colors.overlayField, RoundedCornerShape(12.dp))
                .border(1.dp, Be.Colors.outline, RoundedCornerShape(12.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                HoldControl("◀", Hotkeys.MOVE_LEFT, "左移", Modifier.weight(1f), callbacks)
                HoldControl("▲", Hotkeys.MOVE_FORWARD, "前进", Modifier.weight(1f), callbacks)
                HoldControl("▼", Hotkeys.MOVE_BACK, "后退", Modifier.weight(1f), callbacks)
                HoldControl("▶", Hotkeys.MOVE_RIGHT, "右移", Modifier.weight(1f), callbacks)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                HoldControl("上升", Hotkeys.MOVE_UP, "上升", Modifier.weight(1f), callbacks)
                HoldControl("下降", Hotkeys.MOVE_DOWN, "下降", Modifier.weight(1f), callbacks)
            }
        }
    }
}

/**
 * Aiming, which on a desktop is what the mouse does. A device has no cursor for
 * the free camera's mouse hook to watch, so the drag is the input: whatever the
 * finger moves is what the camera turns, with the desktop's own sensitivity and
 * inversion settings applied by the native side.
 *
 * The deltas go out in screen pixels with y downwards, exactly the coordinates
 * the hook produces, so dragging right turns right and dragging up looks up -
 * the first of those is the desktop convention and the second is the touch one.
 * At the native default sensitivity (0.1 deg per pixel) one swipe across the pad
 * already turns the camera a good part of a turn on a typical phone, which is why
 * the settings page exposes the sensitivity alongside this.
 *
 * The pad consumes its drags, so a drag here never scrolls the panel. Everything
 * else in the panel is a tap or a hold and keeps working while the camera is
 * armed; look input is ignored during a playback, which is what makes a preset
 * or VMD shot reproducible.
 */
@Composable
internal fun LookPad(callbacks: OverlaySurface.Callbacks) {
    OverlaySection("镜头转向（拖动）") {
        Box(
            Modifier.fillMaxWidth().height(62.dp)
                .background(Be.Colors.overlayField, RoundedCornerShape(12.dp))
                .border(1.dp, Be.Colors.outline, RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        callbacks.look(dragAmount.x, dragAmount.y)
                    }
                }
                .semantics { contentDescription = "镜头转向：按住拖动，右为右转，上为上抬" },
            contentAlignment = Alignment.Center,
        ) {
            Text("拖动转向", color = Be.Colors.textSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
internal fun MotionControls(features: OverlayFeatures, callbacks: OverlaySurface.Callbacks) {
    OverlaySection("运镜 / 关键帧") {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            // These three start a shot, so they do not fire on the tap: the
            // controller collapses the panel first and sends the key a second
            // later, leaving the camera a clean screen to start on. Everything
            // else here is an adjustment that belongs under the finger, which
            // is why nothing else is deferred.
            OverlayActionRow("播放 / 停止", { callbacks.delayedPulse(Hotkeys.MOTION, "运镜 播放/停止") },
                "视角回正", { callbacks.pulse(Hotkeys.VIEW_RESET, "视角回正") })
            // The zoom keys are read while they are down on the desktop, so a
            // 180 ms pulse only steps the lens about 3.6 degrees per tap. Holding
            // is both what the key means and the only way to frame a wide shot.
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                HoldControl("广角 +", Hotkeys.FOV_WIDE, "广角 +", Modifier.weight(1f), callbacks)
                HoldControl("长焦 +", Hotkeys.FOV_NARROW, "长焦 +", Modifier.weight(1f), callbacks)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                HoldControl("滚转 ↺", Hotkeys.ROLL_LEFT, "逆时针滚转", Modifier.weight(1f), callbacks)
                HoldControl("滚转 ↻", Hotkeys.ROLL_RIGHT, "顺时针滚转", Modifier.weight(1f), callbacks)
            }
            OverlayAction("记录关键帧") { callbacks.pulse(Hotkeys.KEYFRAME_ADD, "记录关键帧") }
            OverlayActionRow("回放关键帧", { callbacks.delayedPulse(Hotkeys.KEYFRAME_PLAY, "回放关键帧") },
                "清除", { callbacks.pulse(Hotkeys.KEYFRAME_CLEAR, "清除关键帧") })
            // Offered only once a .vmd has been imported: without one the native
            // side logs "no VMD camera file is configured", so the button would
            // be a control whose only outcome is a complaint. The key also enters
            // the free camera by itself when it is not running yet.
            if (features.vmdCamera()) {
                OverlayAction("VMD 镜头 播放 / 停止") {
                    callbacks.delayedPulse(Hotkeys.VMD_PLAY, "VMD 镜头 播放/停止")
                }
            }
        }
    }
}

/**
 * MMD playback, driven by the runtime command channel rather than by keys.
 *
 * The desktop module reads its MMD controls from NUMPAD4/5/6 - the same codes
 * the touch panel's virtual-key latch already uses - so pressing them here
 * would have the panel fighting itself. Commands sidestep that: the panel is
 * inside the game process, so it hands the payload to the relay the native side
 * tails, exactly as the settings app does through the framework's remote file
 * space.
 *
 * <p>Play and stop are plain taps, unlike the camera keys that start a shot:
 * those are deferred so the panel is out of frame before the camera moves,
 * whereas a dance is the character's own motion and the panel being visible
 * while it plays is the point of having these controls in the overlay at all.
 */
@Composable
internal fun MmdControls(callbacks: OverlaySurface.Callbacks) {
    // The channel is asynchronous: the runtime answers only once it has drained
    // the command, so the row shows the last outcome rather than pretending the
    // new one has already arrived. Tapping again is the refresh, and the
    // journal below carries the same status line in full.
    var revision by remember { mutableStateOf(0) }
    val status = remember(revision) { callbacks.commandStatus() }
    val send = { verb: String ->
        callbacks.command("mmd", verb)
        revision += 1
    }

    OverlaySection("MMD 舞蹈") {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            OverlayActionRow("播放 / 暂停", { send("play_pause") }, "停止", { send("stop") })
            OverlayActionRow("循环", { send("loop") }, "镜头模式", { send("camera next") })
            Text(mmdStatusSummary(status), color = Be.Colors.textSecondary, fontSize = 10.sp)
        }
    }
}

/**
 * The runtime's status line shortened for one row. The header and generation
 * are transport detail; the outcome and the command it refers to are not.
 */
private fun mmdStatusSummary(raw: String): String {
    val lines = raw.split('\n').filter { it.isNotEmpty() }
    if (lines.size < 3 || lines[0] != "BE_STATUS_V1") return "尚未收到回执"
    val command = if (lines.size > 3) lines[3] else ""
    return when (lines[2]) {
        "idle" -> if (command.isEmpty()) "就绪" else "已下发：$command"
        "accepted" -> "已下发：$command"
        "applied" -> "已执行：$command"
        "rejected" -> "被拒绝：$command"
        "unsupported" -> "不支持：$command"
        else -> lines[2]
    }
}
