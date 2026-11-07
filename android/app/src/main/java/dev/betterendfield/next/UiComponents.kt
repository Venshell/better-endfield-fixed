package dev.betterendfield.next

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The shared widget vocabulary.
 *
 * Everything is a flat fill with no outline: a row is a lighter rectangle on a
 * panel, a field is a darker one, and the accent appears only on what is
 * actionable right now. Where a stock Material control is reused - the switch,
 * the slider, the dropdown - its colours are overridden so it cannot
 * reintroduce Material's own tonal surfaces into the palette.
 *
 * Press feedback is a fill step rather than a ripple, because a ripple reads as
 * a second, brighter accent on a surface that is allowed exactly one.
 */

/* ------------------------------------------------------------------ surfaces */

/** Press state plus the interaction modifier that produced it. */
class Pressed(val value: Boolean, val modifier: Modifier)

/** Whole-row press feedback with the ripple suppressed. */
@Composable
fun rememberPressed(enabled: Boolean, onClick: () -> Unit): Pressed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return Pressed(
        pressed,
        Modifier.clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        ),
    )
}

/** Whole-row toggle semantics, so a screen reader announces a real switch state. */
@Composable
fun rememberToggleable(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
): Pressed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return Pressed(
        pressed,
        Modifier.toggleable(
            value = checked,
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
    )
}

/** A titled card: accent eyebrow, title, one sentence of context, then content. */
@Composable
fun SectionCard(
    eyebrow: String,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    status: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.card))
            .background(Be.Colors.panel)
            .padding(Be.Space.cardInner),
    ) {
        Eyebrow(eyebrow)
        Text(
            text = title,
            color = Be.Colors.textPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = Be.Space.xs),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = Be.Colors.textMuted,
                fontSize = Be.Type.bodySmall,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = Be.Space.s),
            )
        }
        Column(Modifier.padding(top = Be.Space.xl)) { content() }
        if (!status.isNullOrEmpty()) {
            StatusBlock(status, Modifier.padding(top = Be.Space.l))
        }
    }
}

/** A plain panel for content that carries its own heading. */
@Composable
fun PanelCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.card))
            .background(Be.Colors.panel)
            .padding(Be.Space.cardInner),
        content = content,
    )
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Be.Colors.accent,
        fontSize = Be.Type.eyebrow,
        fontWeight = FontWeight.Medium,
        letterSpacing = Be.EyebrowTracking.sp,
        modifier = modifier,
    )
}

/** A small all-caps divider inside a card, for a group of related rows. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier, top: Dp = Be.Space.xxl) {
    Text(
        text = text,
        color = Be.Colors.textMuted,
        fontSize = Be.Type.caption,
        fontWeight = FontWeight.Medium,
        letterSpacing = Be.CaptionTracking.sp,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = top, start = Be.Space.hairline, bottom = Be.Space.hairline),
    )
}

@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Be.Colors.textPrimary,
        fontSize = Be.Type.cardTitle,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier, top: Dp = Be.Space.page) {
    Text(
        text = text,
        color = Be.Colors.textSecondary,
        fontSize = Be.Type.label,
        fontWeight = FontWeight.Medium,
        modifier = modifier.padding(top = top, bottom = Be.Space.m),
    )
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Be.Colors.textSecondary,
        fontSize = Be.Type.body,
        lineHeight = 18.sp,
        modifier = modifier,
    )
}

/** The accent-on-soft chip used for values, hotkey names and inline badges. */
@Composable
fun ValueChip(
    text: String,
    modifier: Modifier = Modifier,
    monospace: Boolean = true,
) {
    Text(
        text = text,
        color = Be.Colors.accent,
        fontSize = Be.Type.value,
        fontFamily = if (monospace) Monospace else FontFamily.Default,
        fontWeight = if (monospace) FontWeight.Normal else FontWeight.Medium,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(Be.Radius.chip))
            .background(Be.Colors.accentSoft)
            .padding(horizontal = Be.Space.m + 1.dp, vertical = 3.dp),
    )
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Be.Colors.accent,
        fontSize = Be.Type.caption,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(Be.Radius.badge))
            .background(Be.Colors.accentSoft)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** A recessed block for status pills, selection summaries and the journal. */
@Composable
fun StatusBlock(
    text: String,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
) {
    Text(
        text = text,
        color = Be.Colors.textSecondary,
        fontSize = if (monospace) Be.Type.mono else Be.Type.bodySmall,
        fontFamily = if (monospace) Monospace else FontFamily.Default,
        lineHeight = if (monospace) 16.sp else 17.sp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.status))
            .background(Be.Colors.field)
            .padding(horizontal = Be.Space.l, vertical = Be.Space.l),
    )
}

/** The page-footer reminder, with the accent info mark drawn inline. */
@Composable
fun Notice(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.button))
            .background(Be.Colors.field)
            .padding(Be.Space.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InfoMark()
        Spacer(Modifier.width(Be.Space.l))
        Text(
            text = text,
            color = Be.Colors.textSecondary,
            fontSize = Be.Type.body,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun InfoMark(modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val width = 1.8.dp.toPx()
        drawCircle(
            color = Be.Colors.accent,
            radius = size.minDimension / 2f - width,
            style = Stroke(width = width),
        )
        drawCircle(
            color = Be.Colors.accent,
            radius = 1.1.dp.toPx(),
            center = Offset(size.width / 2f, size.height * 0.33f),
        )
        drawLine(
            color = Be.Colors.accent,
            start = Offset(size.width / 2f, size.height * 0.47f),
            end = Offset(size.width / 2f, size.height * 0.74f),
            strokeWidth = width,
        )
    }
}

/* ---------------------------------------------------------------------- rows */

/**
 * One switch in a settings card: title, the sentence that explains it, an
 * optional badge naming the control that triggers it in game, and the switch.
 * The whole row is the touch target, so the switch itself carries no click.
 */
@Composable
fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    enabled: Boolean = true,
) {
    val press = rememberToggleable(checked, enabled, onCheckedChange)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.row))
            .background(if (press.value) Be.Colors.rowHigh else Be.Colors.row)
            .then(press.modifier)
            .alpha(if (enabled) 1f else 0.42f)
            .heightIn(min = Be.Size.rowMinHeight)
            .padding(
                start = Be.Space.xl,
                top = Be.Space.l,
                end = Be.Space.m + 2.dp,
                bottom = Be.Space.l,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = if (checked) Be.Colors.textPrimary else Be.Colors.textSecondary,
                    fontSize = Be.Type.rowTitle,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!badge.isNullOrEmpty()) {
                    Spacer(Modifier.width(Be.Space.m))
                    Badge(badge)
                }
            }
            // Several switches carry no explanation - "强制循环（覆盖非循环动画）"
            // says everything a second sentence would. An empty string renders
            // nothing rather than an empty line, so the rows still align.
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    color = Be.Colors.textSecondary,
                    fontSize = Be.Type.bodySmall,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        Spacer(Modifier.width(Be.Space.l))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Be.Colors.accent,
                checkedTrackColor = Be.Colors.accentSoft,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Be.Colors.switchThumbOff,
                uncheckedTrackColor = Be.Colors.switchTrackOff,
                uncheckedBorderColor = Color.Transparent,
                disabledCheckedThumbColor = Be.Colors.accent,
                disabledCheckedTrackColor = Be.Colors.accentSoft,
                disabledUncheckedThumbColor = Be.Colors.switchThumbOff,
                disabledUncheckedTrackColor = Be.Colors.switchTrackOff,
            ),
        )
    }
}

/**
 * A numeric setting: label, current value, and a slider.
 *
 * A negative [decimals] quantizes the readout to the slider's own step. Zero or
 * more keeps the exact stored number in the readout and only replaces it once
 * the user actually drags, which is what stops saving an unrelated setting from
 * silently rounding a first-person eye offset.
 */
@Composable
fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    unit: String = "",
    steps: Int = 200,
    decimals: Int = -1,
    enabled: Boolean = true,
) {
    val minimum = valueRange.start
    val span = (valueRange.endInclusive - minimum).takeIf { it > 0f } ?: 1f
    var precise by remember { mutableFloatStateOf(value.coerceIn(valueRange)) }

    fun slot(v: Float): Float = ((v - minimum) / span * steps).coerceIn(0f, steps.toFloat())
    fun reported(): Float =
        if (decimals >= 0) precise else minimum + span * slot(precise).roundToInt() / steps

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.row))
            .background(Be.Colors.row)
            .alpha(if (enabled) 1f else 0.42f)
            .padding(
                start = Be.Space.xl,
                top = Be.Space.m + 2.dp,
                end = Be.Space.xl,
                bottom = Be.Space.l,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = Be.Colors.textSecondary,
                fontSize = Be.Type.value,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            ValueChip(formatReading(reported(), unit, decimals))
        }
        Slider(
            value = slot(precise),
            onValueChange = { next ->
                precise = minimum + span * next / steps
                onValueChange(reported())
            },
            valueRange = 0f..steps.toFloat(),
            steps = (steps - 1).coerceAtLeast(0),
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Be.Colors.accent,
                activeTrackColor = Be.Colors.accent,
                activeTickColor = Color.Transparent,
                inactiveTrackColor = Be.Colors.track,
                inactiveTickColor = Color.Transparent,
                disabledThumbColor = Be.Colors.accent,
                disabledActiveTrackColor = Be.Colors.accent,
                disabledInactiveTrackColor = Be.Colors.track,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Be.Space.xs)
                .height(Be.Size.sliderHeight),
        )
    }
}

/** Turns the stored number into the string the settings screen shows. */
private fun formatReading(value: Float, unit: String, decimals: Int): String {
    val text = when {
        decimals >= 0 -> String.format(Locale.ROOT, "%.${decimals}f", value)
        value >= 10f -> String.format(Locale.ROOT, "%.0f", value)
        else -> String.format(Locale.ROOT, "%.1f", value)
    }
    return text + unit
}

/**
 * A picker that opens in place. Material's exposed dropdown would work, but this
 * keeps the closed state identical to a field's fill and puts the accent on the
 * caret rather than on a floating label.
 */
@Composable
fun SelectField(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val safeIndex = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
    val press = rememberPressed(enabled && options.isNotEmpty()) { expanded = true }
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Be.Radius.input))
                .background(if (press.value) Be.Colors.rowHigh else Be.Colors.field)
                .then(press.modifier)
                .heightIn(min = Be.Size.fieldHeight)
                .alpha(if (enabled) 1f else 0.42f)
                .padding(start = Be.Space.xl, end = Be.Space.m + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = options.getOrNull(safeIndex) ?: placeholder,
                color = Be.Colors.textPrimary,
                fontSize = Be.Type.rowTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Be.Space.s))
            Caret(Be.Colors.accent)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Be.Colors.panelHigh,
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = if (index == safeIndex) Be.Colors.accent else Be.Colors.textPrimary,
                            fontSize = Be.Type.rowTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        if (index != safeIndex) onSelect(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun Caret(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(12.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.15f, size.height * 0.36f)
            lineTo(size.width * 0.5f, size.height * 0.7f)
            lineTo(size.width * 0.85f, size.height * 0.36f)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    }
}

/**
 * A single-line field. [onCommit] replaces the old focus-loss save: the text is
 * held in the caller's state as it is typed, but the configuration is only
 * rewritten when the field is left.
 */
@Composable
fun TextFieldRow(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    keyboardType: KeyboardType = KeyboardType.Decimal,
    maxLength: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    onCommit: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = { next -> onValueChange(next.take(maxLength)) },
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(color = Be.Colors.textPrimary, fontSize = Be.Type.rowTitle),
        cursorBrush = SolidColor(Be.Colors.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.input))
            .background(Be.Colors.field)
            .alpha(if (enabled) 1f else 0.42f)
            .onFocusChanged { state ->
                if (focused && !state.isFocused) onCommit()
                focused = state.isFocused
            }
            .padding(horizontal = Be.Space.xl, vertical = Be.Space.xxl),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(text = hint, color = Be.Colors.textMuted, fontSize = Be.Type.rowTitle)
                }
                inner()
            }
        },
    )
}

/* ------------------------------------------------------------------- buttons */

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val press = rememberPressed(enabled) { onClick() }
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.button))
            .background(
                when {
                    !enabled -> Be.Colors.accent.copy(alpha = 0.35f)
                    press.value -> Be.Colors.accentPressed
                    else -> Be.Colors.accent
                },
            )
            .then(press.modifier)
            .heightIn(min = Be.Size.buttonHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Be.Colors.accentInk,
            fontSize = Be.Type.rowTitle,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val press = rememberPressed(enabled) { onClick() }
    Box(
        modifier
            .clip(RoundedCornerShape(Be.Radius.button))
            .background(if (press.value) Be.Colors.rowHigh else Be.Colors.row)
            .then(press.modifier)
            .alpha(if (enabled) 1f else 0.42f)
            .heightIn(min = Be.Size.buttonHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Be.Colors.textPrimary,
            fontSize = Be.Type.value,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = Be.Space.xl),
        )
    }
}

/* --------------------------------------------------------------- page chrome */

/** The tab strip. It turns into the wide layout's side rail on tablets. */
@Composable
fun PageTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
) {
    if (vertical) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(Be.Space.s)) {
            labels.forEachIndexed { index, label ->
                TabItem(label, index == selectedIndex, true, { onSelect(index) }, Modifier.fillMaxWidth())
            }
        }
    } else {
        Row(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Be.Radius.tabs))
                .background(Be.Colors.panel)
                .padding(Be.Space.xs),
            horizontalArrangement = Arrangement.spacedBy(Be.Space.xs),
        ) {
            labels.forEachIndexed { index, label ->
                TabItem(label, index == selectedIndex, false, { onSelect(index) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    selected: Boolean,
    vertical: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val press = rememberPressed(enabled = true) { onClick() }
    Box(
        modifier
            .clip(RoundedCornerShape(Be.Radius.button))
            .background(
                when {
                    selected -> Be.Colors.accent
                    press.value -> Be.Colors.panelHigh
                    else -> Color.Transparent
                },
            )
            .then(press.modifier)
            .heightIn(min = if (vertical) Be.Size.touchTarget else Be.Size.tabHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Be.Colors.accentInk else Be.Colors.textSecondary,
            fontSize = if (vertical) Be.Type.tab else Be.Type.tabCompact,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Be.Space.s),
        )
    }
}

/* -------------------------------------------------------------- colour wheel */

/**
 * HSV wheel state, kept outside composition so the hue marker stays put when the
 * colour moves to grey or white, where hue is mathematically undefined and would
 * otherwise snap back to red.
 */
class WheelColor {
    var hue by mutableFloatStateOf(0f)
        private set
    var saturation by mutableFloatStateOf(0f)
        private set
    var brightness by mutableFloatStateOf(1f)
        private set
    private var lastArgb = Int.MIN_VALUE

    fun syncFrom(argb: Int) {
        if (argb == lastArgb) return
        lastArgb = argb
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb or -0x1000000, hsv)
        hue = if (hsv[1] < 0.001f) hue else hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
    }

    fun setHue(value: Float): Int {
        hue = ((value % 360f) + 360f) % 360f
        return publish()
    }

    fun setSaturationBrightness(s: Float, v: Float): Int {
        saturation = s.coerceIn(0f, 1f)
        brightness = v.coerceIn(0f, 1f)
        return publish()
    }

    private fun publish(): Int {
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)) and 0xFFFFFF
        lastArgb = argb
        return argb
    }
}

/**
 * A hue ring around a saturation/value square, mirroring the desktop ring-shaped
 * picker. [onChange]'s committed flag is false while dragging and true once the
 * finger lifts, which is what lets the caller defer the disk write.
 */
@Composable
fun HueColorWheel(
    argb: Int,
    onChange: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 240.dp,
) {
    val state = remember { WheelColor() }
    LaunchedEffect(argb) { state.syncFrom(argb) }

    Canvas(
        modifier
            .size(diameter)
            .pointerInput(Unit) {
                val shortest = minOf(size.width, size.height)
                val outer = shortest / 2f - 2.dp.toPx()
                val ringWidth = shortest * RingFraction
                val inner = outer - ringWidth
                val half = (inner - 10.dp.toPx()) / sqrt(2f)
                val squareLeft = size.width / 2f - half
                val squareTop = size.height / 2f - half
                val squareSize = half * 2f
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val slack = 8.dp.toPx()

                fun hit(x: Float, y: Float): Int {
                    val distance = hypot(x - centerX, y - centerY)
                    val inSquare = x >= squareLeft && x <= squareLeft + squareSize &&
                        y >= squareTop && y <= squareTop + squareSize
                    return when {
                        inSquare -> SQUARE
                        distance <= outer + slack && distance >= inner - slack -> RING
                        else -> NONE
                    }
                }

                fun apply(x: Float, y: Float, mode: Int, committed: Boolean) {
                    val updated = if (mode == RING) {
                        val degrees = Math.toDegrees(
                            atan2((y - centerY).toDouble(), (x - centerX).toDouble()),
                        ).toFloat()
                        state.setHue(degrees)
                    } else {
                        state.setSaturationBrightness(
                            (x - squareLeft) / squareSize,
                            1f - (y - squareTop) / squareSize,
                        )
                    }
                    onChange(updated, committed)
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val mode = hit(down.position.x, down.position.y)
                    if (mode == NONE) return@awaitEachGesture
                    apply(down.position.x, down.position.y, mode, false)
                    var pointer = down
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointer.id } ?: break
                        if (!change.pressed) {
                            apply(change.position.x, change.position.y, mode, true)
                            break
                        }
                        change.consume()
                        apply(change.position.x, change.position.y, mode, false)
                        pointer = change
                    }
                }
            },
    ) {
        drawColorWheel(state.hue, state.saturation, state.brightness, RingFraction)
    }
}

private const val RING = 1
private const val SQUARE = 2
private const val NONE = 0
private const val RingFraction = 0.11f

private fun DrawScope.drawColorWheel(
    hue: Float,
    saturation: Float,
    brightness: Float,
    ringFraction: Float,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val outer = size.minDimension / 2f - 2.dp.toPx()
    val ringWidth = size.minDimension * ringFraction
    val inner = outer - ringWidth
    val ringCenter = (outer + inner) / 2f

    val hues = List(13) { index ->
        Color(android.graphics.Color.HSVToColor(floatArrayOf(index * 30f % 360f, 1f, 1f)))
    }
    drawCircle(
        brush = Brush.sweepGradient(hues, center),
        radius = ringCenter,
        center = center,
        style = Stroke(width = ringWidth),
    )

    val half = (inner - 10.dp.toPx()) / sqrt(2f)
    val squareLeft = size.width / 2f - half
    val squareTop = size.height / 2f - half
    val squareSize = half * 2f
    val squarePath = Path().apply {
        addRoundRect(
            RoundRect(
                left = squareLeft,
                top = squareTop,
                right = squareLeft + squareSize,
                bottom = squareTop + squareSize,
                cornerRadius = CornerRadius(4.dp.toPx()),
            ),
        )
    }
    val pure = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    clipPath(squarePath) {
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.White, pure)),
            topLeft = Offset(squareLeft, squareTop),
            size = Size(squareSize, squareSize),
        )
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
            topLeft = Offset(squareLeft, squareTop),
            size = Size(squareSize, squareSize),
        )
    }

    val angle = Math.toRadians(hue.toDouble())
    val marker = Offset(
        center.x + cos(angle).toFloat() * ringCenter,
        center.y + sin(angle).toFloat() * ringCenter,
    )
    val markerRadius = ringWidth * 0.42f
    drawCircle(pure, radius = markerRadius, center = marker)
    drawCircle(Color.White, radius = markerRadius, center = marker, style = Stroke(width = 2.dp.toPx()))

    val svMarker = Offset(
        squareLeft + saturation * squareSize,
        squareTop + (1f - brightness) * squareSize,
    )
    drawCircle(
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))),
        radius = 7.dp.toPx(),
        center = svMarker,
    )
    drawCircle(Color.White, radius = 7.dp.toPx(), center = svMarker, style = Stroke(width = 2.dp.toPx()))
}

/** A single palette swatch; the ring marks the colour currently in force. */
@Composable
fun ColorSwatch(
    hex: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val press = rememberPressed(enabled = true) { onClick() }
    Box(
        modifier
            .size(Be.Size.swatch)
            .clip(RoundedCornerShape(Be.Radius.chip))
            .background(parseHex(hex) ?: Be.Colors.row)
            .then(press.modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Color.White,
                    style = Stroke(width = 2.dp.toPx()),
                    cornerRadius = CornerRadius(Be.Radius.chip.toPx()),
                )
            }
        }
    }
}

/** A read-only colour preview rectangle next to the hex field. */
@Composable
fun ColorPreview(hex: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(52.dp)
            .clip(RoundedCornerShape(Be.Radius.inner))
            .background(parseHex(hex) ?: Be.Colors.row),
    )
}

/** Accepts `#RRGGBB` with or without the hash; null when the text is not a colour. */
fun parseHex(text: String): Color? {
    val trimmed = text.trim().removePrefix("#")
    if (trimmed.length != 6) return null
    val value = trimmed.toIntOrNull(16) ?: return null
    return Color(value or -0x1000000)
}
