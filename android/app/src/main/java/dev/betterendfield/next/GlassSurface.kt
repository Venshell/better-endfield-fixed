package dev.betterendfield.next

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal class GlassBackdrop(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Unspecified)
}

@Composable
internal fun isAppGlassSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocalView.current.isHardwareAccelerated

@Composable
internal fun rememberGlassBackdrop(enabled: Boolean): GlassBackdrop? {
    if (!enabled || !isAppGlassSupported()) {
        return null
    }
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

internal fun Modifier.captureGlassBackdrop(backdrop: GlassBackdrop?): Modifier {
    if (backdrop == null) return this
    return onGloballyPositioned { backdrop.origin = it.localToRoot(Offset.Zero) }
        .drawWithContent {
            backdrop.layer.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop.layer)
        }
}

@Composable
internal fun Modifier.glassSurface(
    backdrop: GlassBackdrop,
    tint: Color,
    light: Boolean,
    pressProgress: () -> Float = { 0f },
    cornerRadius: Dp? = null,
    focusOrigin: ((Float) -> Offset)? = null,
): Modifier {
    val sampledLayer = rememberGraphicsLayer()
    var surfaceOrigin by remember { mutableStateOf(Offset.Unspecified) }
    val focused = focusOrigin != null
    val rim = if (focused) {
        Brush.linearGradient(
            0.00f to Color.White.copy(alpha = 0.82f),
            0.42f to Color.White.copy(alpha = 0.29f),
            0.78f to Color.White.copy(alpha = 0.12f),
            1.00f to Color.White.copy(alpha = 0.48f),
        )
    } else if (light) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.94f), Color.Black.copy(alpha = 0.14f)))
    } else {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.06f)))
    }
    val positioned = if (focused) this else onGloballyPositioned { surfaceOrigin = it.localToRoot(Offset.Zero) }
    val shape = cornerRadius?.let { RoundedCornerShape(it) } ?: CircleShape
    val material = if (focused) positioned else positioned.border(1.dp, rim, shape)
    return material
        .drawWithCache {
            val padding = (if (focused) 24.dp else 40.dp).roundToPx()
            val maxCorner = minOf(size.width, size.height) / 2f
            val corner = cornerRadius?.toPx()?.coerceAtMost(maxCorner) ?: maxCorner
            val stroke = 1.dp.toPx()
            val sampleSize = IntSize(
                size.width.roundToInt() + 2 * padding,
                size.height.roundToInt() + 2 * padding,
            )
            sampledLayer.renderEffect = glassEffect(
                radius = (if (focused) 1.5.dp else 4.dp).toPx(),
                corner = corner,
                padding = padding.toFloat(),
                width = size.width,
                height = size.height,
                edgeWidth = (if (focused) 10.dp else 24.dp).toPx(),
                refraction = (if (focused) 14.dp else 24.dp).toPx(),
                lensDepth = if (focused) 0.08f else 0f,
                dispersion = if (focused) 0.5.dp.toPx() else 0f,
            ).asComposeRenderEffect()
            val focusTint = if (focused) Brush.verticalGradient(
                0.0f to tint.copy(alpha = 0.74f),
                0.27f to tint.copy(alpha = 0.88f),
                0.78f to tint.copy(alpha = 0.90f),
                1.0f to tint.copy(alpha = 0.76f),
            ) else null

            onDrawWithContent {
                val origin = backdrop.origin
                val destination = focusOrigin?.invoke(size.width) ?: surfaceOrigin
                if (origin.isSpecified && destination.isSpecified && backdrop.layer.size != IntSize.Zero) {
                    val offset = origin - destination + Offset(padding.toFloat(), padding.toFloat())
                    // Rasterize only this surface and its sampling margin.
                    sampledLayer.record(size = sampleSize) {
                        translate(offset.x, offset.y) { drawLayer(backdrop.layer) }
                    }
                    translate(-padding.toFloat(), -padding.toFloat()) { drawLayer(sampledLayer) }
                    if (focusTint != null) {
                        val progress = pressProgress().coerceIn(0f, 1f)
                        drawRect(focusTint, alpha = 1f - 0.12f * progress)
                        drawRoundRect(
                            brush = rim,
                            topLeft = Offset(stroke / 2f, stroke / 2f),
                            size = Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f)),
                            cornerRadius = CornerRadius((corner - stroke / 2f).coerceAtLeast(0f)),
                            alpha = 0.26f + 0.74f * progress,
                            style = Stroke(stroke),
                        )
                    } else {
                        drawRect(tint.copy(alpha = if (light) 0.40f else 0.55f))
                    }
                } else {
                    drawRect(tint)
                }
                drawContent()
            }
        }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun glassEffect(
    radius: Float,
    corner: Float,
    padding: Float,
    width: Float,
    height: Float,
    edgeWidth: Float,
    refraction: Float,
    lensDepth: Float,
    dispersion: Float,
): RenderEffect {
    val blur = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return blur
    val shader = RuntimeShader(GlassLens).apply {
        setFloatUniform("halfSize", width / 2f, height / 2f)
        setFloatUniform("padding", padding)
        setFloatUniform("corner", corner)
        setFloatUniform("edgeWidth", edgeWidth)
        setFloatUniform("refraction", refraction)
        setFloatUniform("lensDepth", lensDepth)
        setFloatUniform("dispersion", dispersion)
    }
    return RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader, "backdrop"), blur)
}

// Rounded-rectangle distance field. Bend samples inward near the rim and
// magnify the center and add a narrow amount of dispersion at the focus rim.
private const val GlassLens = """
    uniform shader backdrop;
    uniform float2 halfSize;
    uniform float padding;
    uniform float corner;
    uniform float edgeWidth;
    uniform float refraction;
    uniform float lensDepth;
    uniform float dispersion;

    half4 main(float2 p) {
        float2 local = p - (halfSize + float2(padding));
        float radius = min(corner, min(halfSize.x, halfSize.y));
        float2 q = abs(local) - (halfSize - float2(radius));
        float2 outside = max(q, float2(0.0));
        float distance = length(outside) + min(max(q.x, q.y), 0.0) - radius;
        float2 normal = length(outside) > 0.001
            ? normalize(outside)
            : (q.x > q.y ? float2(1.0, 0.0) : float2(0.0, 1.0));
        float bend = 1.0 - smoothstep(0.0, edgeWidth, abs(distance));
        float2 direction = normal * sign(local);
        float2 warped = p - local * lensDepth - direction * bend * bend * refraction;
        half4 color = backdrop.eval(warped);
        if (dispersion > 0.0 && bend > 0.01) {
            float2 spread = direction * bend * bend * dispersion;
            color.r = backdrop.eval(warped + spread).r;
            color.b = backdrop.eval(warped - spread).b;
        }
        return color;
    }
"""
