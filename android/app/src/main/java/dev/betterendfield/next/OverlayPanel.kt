package dev.betterendfield.next

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val PANEL_FADE_START = 0.88f
private const val PANEL_FADE_MID = 0.93f

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OverlayPanel(
    features: OverlayFeatures,
    preview: Boolean,
    journal: String,
    showJournal: Boolean,
    callbacks: OverlaySurface.Callbacks,
) {
    // One page at a time, in the same column. The model page is a screenful of
    // its own - pickers, switches and sliders per installed model - and inlining
    // it would bury the camera controls the panel exists for under it.
    var models by remember { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val sideInset = if (landscape) 72.dp else 8.dp
    val resources = LocalContext.current.resources
    val contours = remember(resources) {
        BitmapFactory.decodeResource(resources, R.drawable.overlay_cloud_contours)
    }
    val contourPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val controlsState = rememberLazyListState(
        cacheWindow = LazyLayoutCacheWindow(ahead = 10000.dp, behind = 10000.dp))
    val panelBrush = if (landscape) Brush.horizontalGradient(
        0f to Be.Colors.overlayPanel,
        PANEL_FADE_START to Be.Colors.overlayPanel,
        PANEL_FADE_MID to Be.Colors.overlayPanel.copy(alpha = 0.75f),
        1f to Color.Transparent)
        else SolidColor(Be.Colors.overlayPanel)
    Box(Modifier.fillMaxWidth().background(panelBrush)) {
        Box(Modifier.fillMaxWidth()
            .height(160.dp).align(Alignment.TopStart)
            .graphicsLayer { clip = true }
            .drawWithCache {
                val width = size.width.toInt().coerceAtLeast(1)
                val height = size.height.toInt().coerceAtLeast(1)
                val stripes = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    strokeWidth = 1.dp.toPx()
                    shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                        intArrayOf(0x28B5B8BB, 0x28B5B8BB, 0x00B5B8BB),
                        floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
                }
                val bitmapCanvas = android.graphics.Canvas(stripes)
                var x = -height.toFloat()
                val step = 10.dp.toPx()
                while (x < width) {
                    bitmapCanvas.drawLine(x, 0f, x + height, height.toFloat(), paint)
                    x += step
                }
                if (landscape) {
                    val fade = Paint().apply {
                        shader = LinearGradient(0f, 0f, width.toFloat(), 0f,
                            intArrayOf(android.graphics.Color.WHITE,
                                android.graphics.Color.WHITE, 0xBFFFFFFF.toInt(),
                                android.graphics.Color.TRANSPARENT),
                            floatArrayOf(0f, PANEL_FADE_START, PANEL_FADE_MID, 1f),
                            Shader.TileMode.CLAMP)
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                    }
                    bitmapCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fade)
                }
                val displayPaint = Paint(Paint.FILTER_BITMAP_FLAG)
                onDrawBehind {
                    drawIntoCanvas { it.nativeCanvas.drawBitmap(stripes, 0f, 0f, displayPaint) }
                }
            }
        )
        Canvas(Modifier.width(77.dp).height(280.dp).align(Alignment.BottomStart)
            .graphicsLayer()) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val saved = native.save()
                native.translate(size.width, 0f)
                native.rotate(90f)
                native.drawBitmap(contours, null,
                    RectF(0f, 0f, size.height, size.width), contourPaint)
                native.restoreToCount(saved)
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = sideInset, end = 16.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(20.dp).height(28.dp)
                .padding(top = 2.dp).clickable(onClick = callbacks::collapse),
                contentAlignment = Alignment.Center) {
                Text("↩", color = Be.Colors.textPrimary, fontSize = 20.sp)
            }
            Column(Modifier.weight(1f)) {
                Text("BETTER ENDFIELD", color = Be.Colors.textSecondary,
                    fontSize = 7.sp, fontWeight = FontWeight.Medium)
                Text(if (preview) "悬浮窗预览" else "游戏内控制",
                    color = Be.Colors.textPrimary, fontSize = 12.sp)
            }
        }
        LazyColumn(
            Modifier.fillMaxWidth(if (landscape) 0.864f else 1f).fillMaxHeight()
                .padding(top = 44.dp),
            state = controlsState,
            contentPadding = PaddingValues(start = sideInset,
                end = if (landscape) 0.dp else 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (models) {
                item {
                    OverlayModelPage(preview = preview, onBack = { models = false })
                }
            } else {
                if (features.hideHud()) {
                    item {
                        OverlaySection("界面") {
                            OverlayAction("隐藏 / 恢复 HUD") {
                                callbacks.pulse(Hotkeys.HIDE_HUD, "隐藏 / 恢复 HUD")
                            }
                        }
                    }
                }
                if (features.freeCamera() || features.firstPerson()) {
                    item {
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
                            }
                        }
                    }
                }
                if (features.worldPause()) {
                    item {
                        OverlaySection("时间冻结") {
                            OverlayAction("切换时间冻结") {
                                callbacks.pulse(Hotkeys.WORLD_PAUSE, "时间冻结")
                            }
                        }
                    }
                }
                if (features.mmd()) item { MmdControls(callbacks) }
                if (features.mediaScreen()) item { MediaScreenControls(preview) }
                if (features.freeCamera()) {
                    // Keep camera controls in their existing order.
                    item { LookPad(callbacks) }
                    item { MovementPad(callbacks) }
                    item { MotionControls(features, callbacks) }
                }
                if (!features.anyControl()) {
                    item {
                        Text("还没有需要即时操作的功能。请在「体验」页启用控制项。",
                            color = Be.Colors.textSecondary, fontSize = 12.sp)
                    }
                }
                if (features.models()) {
                    item {
                        OverlaySection("模型") {
                            OverlayAction("模型管理 / 游戏视野") { models = true }
                        }
                    }
                }
                item {
                    OverlayAction(if (preview) "结束预览" else "打开体验设置") {
                        if (preview) callbacks.collapse() else callbacks.openSettings()
                    }
                }
                if (!preview && showJournal) item { RuntimeLogPanel(journal, callbacks) }
                if (preview) item {
                    Text("预览模式：按钮不会发送指令。",
                        color = Be.Colors.textSecondary, fontSize = 10.sp)
                }
            }
        }
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
