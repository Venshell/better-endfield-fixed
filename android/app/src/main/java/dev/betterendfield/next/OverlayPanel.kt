package dev.betterendfield.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun OverlayPanel(
    features: OverlayFeatures,
    preview: Boolean,
    journal: String,
    callbacks: OverlaySurface.Callbacks,
) {
    // One page at a time, in the same column. The model page is a screenful of
    // its own - pickers, switches and sliders per installed model - and inlining
    // it would bury the camera controls the panel exists for under it.
    var models by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .background(Be.Colors.overlayPanel, RoundedCornerShape(17.dp))
            .border(1.dp, Be.Colors.outline, RoundedCornerShape(17.dp))
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (models) {
            OverlayModelPage(preview = preview, onBack = { models = false })
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("BETTER ENDFIELD", color = Be.Colors.accent,
                    fontSize = 10.sp, fontWeight = FontWeight.Medium)
                Text(if (preview) "悬浮窗预览" else "游戏内控制",
                    color = Be.Colors.textPrimary, fontSize = 18.sp)
            }
            Box(
                Modifier.width(42.dp).height(36.dp)
                    .background(Be.Colors.overlayRow, RoundedCornerShape(10.dp))
                    .border(1.dp, Be.Colors.outline, RoundedCornerShape(10.dp))
                    .clickable(onClick = callbacks::collapse),
                contentAlignment = Alignment.Center,
            ) {
                Text("─", color = Be.Colors.textSecondary, fontSize = 20.sp)
            }
        }
        if (features.hideHud()) {
            OverlaySection("界面") {
                OverlayAction("隐藏 / 恢复 HUD") { callbacks.pulse(Hotkeys.HIDE_HUD, "隐藏 / 恢复 HUD") }
            }
        }
        if (features.freeCamera() || features.firstPerson()) {
            OverlaySection("相机") {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (features.freeCamera() && features.firstPerson()) {
                        OverlayActionRow("自由视角", { callbacks.pulse(Hotkeys.FREE_CAMERA, "自由视角") },
                            "第一人称", { callbacks.pulse(Hotkeys.FIRST_PERSON, "第一人称") })
                    } else if (features.freeCamera()) {
                        OverlayAction("自由视角") { callbacks.pulse(Hotkeys.FREE_CAMERA, "自由视角") }
                    } else {
                        OverlayAction("第一人称") { callbacks.pulse(Hotkeys.FIRST_PERSON, "第一人称") }
                    }
                    if (features.worldPause()) {
                        OverlayAction("时间冻结") { callbacks.pulse(Hotkeys.WORLD_PAUSE, "时间冻结") }
                    }
                }
            }
        }
        if (features.mmd()) {
            MmdControls(callbacks)
        }
        if (features.freeCamera()) {
            // Aiming comes first: framing a shot is look, then move, then set
            // the lens, and the pad that does the most work should not be the
            // one the thumb has to scroll to find.
            LookPad(callbacks)
            MovementPad(callbacks)
            MotionControls(features, callbacks)
        }
        if (!features.anyControl()) {
            Text("还没有需要即时操作的功能。请在「体验」页启用控制项。",
                color = Be.Colors.textSecondary, fontSize = 12.sp)
        }
        if (features.models()) {
            OverlaySection("模型") {
                // The settings app can also do this, but only after leaving the
                // game: swapping which model a character wears is something the
                // player decides while looking at the character.
                OverlayAction("模型管理 / 游戏视野") { models = true }
            }
        }
        OverlayAction(if (preview) "结束预览" else "打开体验设置") {
            if (preview) callbacks.collapse() else callbacks.openSettings()
        }
        if (!preview) RuntimeLogPanel(journal, callbacks)
        Text(if (preview) "预览模式：按钮不会发送指令。"
            else "按钮按下的是桌面端同一套热键。",
            color = Be.Colors.textSecondary, fontSize = 10.sp)
    }
}

@Composable
internal fun RuntimeLogPanel(journal: String, callbacks: OverlaySurface.Callbacks) {
    OverlaySection("运行日志") {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                journal,
                modifier = Modifier.fillMaxWidth()
                    .background(Be.Colors.overlayField, RoundedCornerShape(11.dp))
                    .border(1.dp, Be.Colors.outline, RoundedCornerShape(11.dp))
                    .combinedClickable(onClick = callbacks::refreshLog, onLongClick = callbacks::copyLog)
                    .padding(9.dp),
                color = Be.Colors.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 12.sp,
            )
            OverlayAction("保存日志到文件", onClick = callbacks::saveLog)
        }
    }
}
