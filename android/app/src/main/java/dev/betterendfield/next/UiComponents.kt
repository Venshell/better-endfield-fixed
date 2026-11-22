package dev.betterendfield.next

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.SliderDefaults as MiuixSliderDefaults
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.SwitchDefaults as MiuixSwitchDefaults
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton

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

/**
 * Material expressive springs. The scheme type in Material 3 is internal, so the
 * same damping and stiffness live here and every motion in the shell uses them.
 */
internal object BeMotion {
    fun <T> tabTravel(): FiniteAnimationSpec<T> = androidx.compose.animation.core.tween(250)
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)
    fun <T> effects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)
}

/** Press state plus the interaction modifier that produced it. */
class Pressed(val value: Boolean, val modifier: Modifier)

/** Shrinks a control around its center while it is held. */
@Composable
internal fun Modifier.pressScale(pressed: Boolean, to: Float = 0.975f): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (pressed) to else 1f,
        animationSpec = BeMotion.fastSpatial(),
        label = "press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Fills that ease between the resting and pressed colours. */
@Composable
internal fun pressedFill(pressed: Boolean, idle: Color, down: Color): Color {
    val color by animateColorAsState(
        targetValue = if (pressed) down else idle,
        animationSpec = BeMotion.effects(),
        label = "pressFill",
    )
    return color
}

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
    searchLabels: List<String> = emptyList(),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!matchesPageFilter(title, eyebrow, subtitle, *searchLabels.toTypedArray())) return
    PanelCard(modifier) {
        val hasEyebrow = eyebrow.isNotBlank()
        if (hasEyebrow) Eyebrow(eyebrow)
        Text(
            text = title,
            color = appColors.textPrimary,
            fontSize = if (usesMiuix) Be.Type.title else 19.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = if (hasEyebrow) Be.Space.xs else 0.dp),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = appColors.textMuted,
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

/**
 * A card that stays folded until its header is tapped. The body stays on this
 * page; expanding does not open another screen.
 */
@Composable
fun Disclosure(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    hint: String? = null,
    searchLabels: List<String> = emptyList(),
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val headingMatches = matchesPageFilter(title, eyebrow, hint)
    if (!headingMatches && !matchesPageFilter(*searchLabels.toTypedArray())) return
    var searchExpansion by remember(LocalPageFilter.current) { mutableStateOf(!headingMatches) }
    val visible = if (headingMatches) expanded else searchExpansion
    val press = rememberPressed(true) {
        if (headingMatches) expanded = !expanded else searchExpansion = !searchExpansion
    }
    val caret by animateFloatAsState(
        targetValue = if (visible) 0f else -90f,
        animationSpec = BeMotion.fastSpatial(),
        label = "disclosure",
    )
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.card))
            .background(appColors.panel),
    ) {
        Row(
            Modifier
                .pressScale(press.value)
                .fillMaxWidth()
                .background(pressedFill(press.value, appColors.panel, appColors.rowHigh))
                .then(press.modifier)
                .padding(horizontal = Be.Space.cardInner, vertical = Be.Space.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                val hasEyebrow = !eyebrow.isNullOrBlank()
                if (hasEyebrow) Eyebrow(eyebrow)
                Text(
                    text = title,
                    color = appColors.textPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = if (hasEyebrow) Be.Space.xs else 0.dp),
                )
                if (!hint.isNullOrEmpty()) {
                    Text(
                        text = hint,
                        color = appColors.textMuted,
                        fontSize = Be.Type.bodySmall,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Be.Space.s),
                    )
                }
            }
            Caret(
                color = appColors.textSecondary,
                modifier = Modifier
                    .padding(start = Be.Space.m)
                    .rotate(caret),
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = expandVertically(BeMotion.spatial(), expandFrom = Alignment.Top) + fadeIn(BeMotion.effects()),
            exit = shrinkVertically(BeMotion.spatial(), shrinkTowards = Alignment.Top) + fadeOut(BeMotion.effects()),
        ) {
            Column(
                Modifier.padding(
                    start = Be.Space.cardInner,
                    end = Be.Space.cardInner,
                    bottom = Be.Space.cardInner,
                ),
            ) {
                CompositionLocalProvider(LocalMiuixCard provides usesMiuix) { content() }
            }
        }
    }
}

/** A plain panel for content that carries its own heading. */
@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    searchLabels: List<String>? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (searchLabels != null && !matchesPageFilter(*searchLabels.toTypedArray())) return
    if (usesMiuix) {
        MiuixPanelCard(modifier, PaddingValues(MiuixLayout.inset), content)
        return
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.card))
            .background(appColors.panel)
            .padding(Be.Space.cardInner),
        content = content,
    )
}

private val LocalMiuixCard = staticCompositionLocalOf { false }

private val fieldSurface: Color
    @Composable get() = if (LocalMiuixCard.current) appColors.insetControl else appColors.field

@Composable
private fun Modifier.controlOutline(shape: Shape, separated: Boolean = true): Modifier =
    if (usesMiuix && separated && !LocalMiuixCard.current) border(1.dp, appColors.outline, shape) else this

@Composable
private fun MiuixPanelCard(
    modifier: Modifier,
    insideMargin: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    MiuixCard(modifier.fillMaxWidth(), MiuixLayout.cardRadius, insideMargin) {
        CompositionLocalProvider(LocalMiuixCard provides true) { content() }
    }
}

@Composable
internal fun SettingsPreferenceCard(
    searchLabels: List<String>? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (searchLabels != null && !matchesPageFilter(*searchLabels.toTypedArray())) return
    if (usesMiuix) {
        MiuixPanelCard(Modifier, PaddingValues(horizontal = MiuixLayout.inset), content)
    } else {
        PanelCard(content = content)
    }
}

@Composable
internal fun SettingsGroup(
    title: String,
    searchLabels: List<String> = emptyList(),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!matchesPageFilter(title, *searchLabels.toTypedArray())) return
    val query = if (matchesPageFilter(title)) "" else LocalPageFilter.current
    if (usesMiuix) {
        Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
            Text(
                text = title,
                color = appColors.accentText,
                fontSize = Be.Type.body,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = MiuixLayout.inset),
            )
            SettingsPreferenceCard { CompositionLocalProvider(LocalPageFilter provides query) { content() } }
        }
    } else {
        PanelCard {
            CardTitle(title)
            content()
        }
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = appColors.accentText,
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
        color = appColors.textMuted,
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
        color = appColors.textPrimary,
        fontSize = Be.Type.cardTitle,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier, top: Dp = Be.Space.page) {
    Text(
        text = text,
        color = appColors.textSecondary,
        fontSize = Be.Type.label,
        fontWeight = FontWeight.Medium,
        modifier = modifier.padding(top = top, bottom = Be.Space.m),
    )
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = appColors.textSecondary,
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
        color = appColors.accentText,
        fontSize = Be.Type.value,
        fontFamily = if (monospace) Monospace else FontFamily.Default,
        fontWeight = if (monospace) FontWeight.Normal else FontWeight.Medium,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(Be.Radius.chip))
            .background(appColors.accentSoft)
            .padding(horizontal = Be.Space.m + 1.dp, vertical = 3.dp),
    )
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = appColors.accentText,
        fontSize = Be.Type.caption,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(Be.Radius.badge))
            .background(appColors.accentSoft)
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
        color = appColors.textSecondary,
        fontSize = if (monospace) Be.Type.mono else Be.Type.bodySmall,
        fontFamily = if (monospace) Monospace else FontFamily.Default,
        lineHeight = if (monospace) 16.sp else 17.sp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.status))
            .background(appColors.field)
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
            .background(appColors.field)
            .padding(Be.Space.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InfoMark()
        Spacer(Modifier.width(Be.Space.l))
        Text(
            text = text,
            color = appColors.textSecondary,
            fontSize = Be.Type.body,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun InfoMark(modifier: Modifier = Modifier) {
    val color = appColors.accentText
    Canvas(modifier.size(20.dp)) {
        val width = 1.8.dp.toPx()
        drawCircle(
            color = color,
            radius = size.minDimension / 2f - width,
            style = Stroke(width = width),
        )
        drawCircle(
            color = color,
            radius = 1.1.dp.toPx(),
            center = Offset(size.width / 2f, size.height * 0.33f),
        )
        drawLine(
            color = color,
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
    val grouped = LocalMiuixCard.current
    val inset = if (grouped) 0.dp else Be.Space.xl
    Row(
        modifier
            .pressScale(press.value)
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (usesMiuix) MiuixLayout.controlRadius else Be.Radius.row))
            .background(pressedFill(press.value, if (grouped) Color.Transparent else appColors.row, appColors.rowHigh))
            .then(press.modifier)
            .alpha(if (enabled) 1f else 0.42f)
            .heightIn(min = if (usesMiuix) MiuixLayout.rowHeight else Be.Size.rowMinHeight)
            .padding(
                start = inset,
                top = Be.Space.l,
                end = if (grouped) 0.dp else Be.Space.m + 2.dp,
                bottom = Be.Space.l,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = if (checked || usesMiuix) appColors.textPrimary else appColors.textSecondary,
                    fontSize = if (usesMiuix) 16.sp else Be.Type.rowTitle,
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
                    color = appColors.textSecondary,
                    fontSize = if (usesMiuix) 13.sp else Be.Type.bodySmall,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = if (usesMiuix) 1.dp else 3.dp),
                )
            }
        }
        Spacer(Modifier.width(Be.Space.l))
        AppSwitch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            materialColors = SwitchDefaults.colors(
                checkedThumbColor = appColors.accent,
                checkedTrackColor = appColors.accentSoft,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = appColors.switchThumbOff,
                uncheckedTrackColor = appColors.switchTrackOff,
                uncheckedBorderColor = Color.Transparent,
                disabledCheckedThumbColor = appColors.accent,
                disabledCheckedTrackColor = appColors.accentSoft,
                disabledUncheckedThumbColor = appColors.switchThumbOff,
                disabledUncheckedTrackColor = appColors.switchTrackOff,
            ),
        )
    }
}

@Composable
internal fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
    materialColors: SwitchColors = SwitchDefaults.colors(),
) {
    if (!usesMiuix) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled, colors = materialColors)
        return
    }

    MiuixSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = appMiuixSwitchColors(),
    )
}

@Composable
internal fun appMiuixSwitchColors(): top.yukonga.miuix.kmp.basic.SwitchColors {
    val colors = miuixControlColors
    return MiuixSwitchDefaults.switchColors(
        checkedTrackColor = colors.activeTrack,
        checkedThumbColor = colors.activeThumb,
        uncheckedTrackColor = colors.inactiveTrack,
        uncheckedThumbColor = colors.inactiveThumb,
        disabledCheckedTrackColor = colors.activeTrack.copy(alpha = 0.46f),
        disabledCheckedThumbColor = colors.activeThumb.copy(alpha = 0.66f),
        disabledUncheckedTrackColor = colors.inactiveTrack.copy(alpha = 0.46f),
        disabledUncheckedThumbColor = colors.inactiveThumb.copy(alpha = 0.66f),
    )
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
    val grouped = LocalMiuixCard.current
    val minimum = valueRange.start
    val span = (valueRange.endInclusive - minimum).takeIf { it > 0f } ?: 1f
    var precise by remember { mutableFloatStateOf(value.coerceIn(valueRange)) }

    fun slot(v: Float): Float = ((v - minimum) / span * steps).coerceIn(0f, steps.toFloat())
    fun reported(): Float =
        if (decimals >= 0) precise else minimum + span * slot(precise).roundToInt() / steps

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (usesMiuix) MiuixLayout.controlRadius else Be.Radius.row))
            .background(if (grouped) Color.Transparent else appColors.row)
            .alpha(if (enabled) 1f else 0.42f)
            .padding(
                start = if (grouped) 0.dp else Be.Space.xl,
                top = Be.Space.m + 2.dp,
                end = if (grouped) 0.dp else Be.Space.xl,
                bottom = Be.Space.l,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = appColors.textSecondary,
                fontSize = Be.Type.value,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            ValueChip(formatReading(reported(), unit, decimals))
        }
        val sliderValue = slot(precise)
        val updateSlider: (Float) -> Unit = { next ->
            precise = minimum + span * next / steps
            onValueChange(reported())
        }
        val sliderRange = 0f..steps.toFloat()
        val sliderSteps = (steps - 1).coerceAtLeast(0)
        val sliderModifier = Modifier
            .fillMaxWidth()
            .padding(top = Be.Space.xs)
            .height(Be.Size.sliderHeight)
        if (usesMiuix) {
            val colors = miuixControlColors
            MiuixSlider(
                value = sliderValue,
                onValueChange = updateSlider,
                valueRange = sliderRange,
                steps = sliderSteps,
                enabled = enabled,
                modifier = sliderModifier,
                colors = MiuixSliderDefaults.sliderColors(
                    foregroundColor = colors.activeTrack,
                    thumbColor = colors.activeThumb,
                    backgroundColor = colors.inactiveTrack,
                    disabledForegroundColor = colors.activeTrack.copy(alpha = 0.46f),
                    disabledThumbColor = colors.activeThumb.copy(alpha = 0.66f),
                    disabledBackgroundColor = colors.inactiveTrack.copy(alpha = 0.46f),
                ),
            )
        } else Slider(
            value = sliderValue,
            onValueChange = updateSlider,
            valueRange = sliderRange,
            steps = sliderSteps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = appColors.accent,
                activeTrackColor = appColors.accent,
                activeTickColor = Color.Transparent,
                inactiveTrackColor = appColors.track,
                inactiveTickColor = Color.Transparent,
                disabledThumbColor = appColors.accent,
                disabledActiveTrackColor = appColors.accent,
                disabledInactiveTrackColor = appColors.track,
            ),
            modifier = sliderModifier,
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
internal fun SubPageRow(
    title: String,
    hint: String? = null,
    state: Boolean? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    eyebrow: String? = null,
    onClick: () -> Unit,
) {
    if (!matchesPageFilter(title, hint, eyebrow)) return
    val grouped = LocalMiuixCard.current
    val surface = if (grouped) Modifier else Modifier
        .clip(RoundedCornerShape(Be.Radius.card))
        .background(appColors.panel)
    val inset = if (grouped) 0.dp else MiuixLayout.inset
    val hasEyebrow = !eyebrow.isNullOrBlank()
    Column(modifier.fillMaxWidth().then(surface)) {
        if (hasEyebrow) {
            Eyebrow(
                eyebrow,
                Modifier.padding(start = inset, end = inset, top = MiuixLayout.inset),
            )
        }
        ArrowPreference(
            title = title,
            summary = hint?.takeIf { it.isNotBlank() },
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(
                start = inset,
                end = inset,
                top = if (hasEyebrow) Be.Space.xs else MiuixLayout.inset,
                bottom = MiuixLayout.inset,
            ),
            enabled = enabled,
            onClick = onClick,
            endActions = {
                if (state != null) {
                    Text(
                        stringResource(if (state) R.string.state_on else R.string.state_off),
                        color = if (state) appColors.accentText else appColors.textMuted,
                        fontSize = Be.Type.bodySmall,
                    )
                }
            },
        )
    }
}

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
    val canSelect = enabled && options.isNotEmpty()
    val value = options.getOrNull(safeIndex) ?: placeholder
    val shape = RoundedCornerShape(if (usesMiuix) MiuixLayout.controlRadius else Be.Radius.input)
    Box(modifier) {
        val press = rememberPressed(canSelect) { expanded = true }
        Row(
            Modifier.pressScale(press.value)
                .fillMaxWidth()
                .clip(shape)
                .background(pressedFill(press.value, fieldSurface, appColors.rowHigh))
                .controlOutline(shape, separated = true)
                .then(press.modifier)
                .heightIn(min = if (usesMiuix) MiuixLayout.rowHeight else Be.Size.fieldHeight)
                .alpha(if (enabled) 1f else 0.42f)
                .padding(start = Be.Space.xl, end = Be.Space.m + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, color = appColors.textPrimary, fontSize = Be.Type.rowTitle,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(Be.Space.s))
            Caret(appColors.accent)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = appColors.panelHigh,
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = if (index == safeIndex) appColors.accentText else appColors.textPrimary,
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

/**
 * A field that opens a menu with a search box pinned to the top. The closed
 * field keeps the current choice; the menu filters the full list as it is typed.
 */
@Composable
fun SearchableSelect(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    searchHint: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var anchorWidth by remember { mutableStateOf(0) }
    val safeIndex = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
    val press = rememberPressed(enabled && options.isNotEmpty()) {
        query = ""
        expanded = true
    }
    val density = LocalDensity.current
    val caret by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = BeMotion.fastSpatial(),
        label = "menu",
    )
    Box(modifier.onSizeChanged { anchorWidth = it.width }) {
        Row(
            Modifier
                .pressScale(press.value)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Be.Radius.input))
                .background(pressedFill(press.value, fieldSurface, appColors.rowHigh))
                .then(press.modifier)
                .heightIn(min = Be.Size.fieldHeight)
                .alpha(if (enabled) 1f else 0.42f)
                .padding(start = Be.Space.xl, end = Be.Space.m + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = options.getOrNull(safeIndex) ?: placeholder,
                color = appColors.textPrimary,
                fontSize = Be.Type.rowTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Be.Space.s))
            Caret(
                color = appColors.accentText,
                modifier = Modifier.rotate(caret),
            )
        }
        if (expanded && anchorWidth > 0) {
            val needle = query.trim()
            val matches = if (needle.isEmpty()) {
                options.indices.toList()
            } else {
                options.indices.filter { options[it].contains(needle, ignoreCase = true) }
            }
            Popup(
                popupPositionProvider = DropdownAnchorPosition(),
                onDismissRequest = {
                    expanded = false
                    query = ""
                },
                properties = PopupProperties(focusable = true),
            ) {
                SearchSelectMenu(
                    width = with(density) { anchorWidth.toDp() },
                    options = options,
                    matches = matches,
                    selectedIndex = safeIndex,
                    query = query,
                    onQueryChange = { query = it },
                    searchHint = searchHint,
                    onPick = { index ->
                        expanded = false
                        query = ""
                        if (index != safeIndex) onSelect(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchSelectMenu(
    width: Dp,
    options: List<String>,
    matches: List<Int>,
    selectedIndex: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    searchHint: String,
    onPick: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val position = matches.indexOf(selectedIndex)
        if (position > 0) listState.scrollToItem(position)
    }
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.96f,
        animationSpec = BeMotion.fastSpatial(),
        label = "menuScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = BeMotion.effects(),
        label = "menuAlpha",
    )
    Column(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .width(width)
            .heightIn(max = 360.dp)
            .clip(RoundedCornerShape(Be.Radius.card))
            .background(appColors.panelHigh)
            .padding(Be.Space.m),
    ) {
        SearchField(
            value = query,
            onValueChange = onQueryChange,
            hint = searchHint,
            requestFocus = true,
        )
        if (matches.isEmpty()) {
            Text(
                text = stringResource(R.string.search_empty),
                color = appColors.textMuted,
                fontSize = Be.Type.bodySmall,
                modifier = Modifier.padding(horizontal = Be.Space.m, vertical = Be.Space.l),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .padding(top = Be.Space.s)
                    .heightIn(max = 280.dp),
            ) {
                items(matches, key = { it }) { index ->
                    val selected = index == selectedIndex
                    val rowPress = rememberPressed(true) { onPick(index) }
                    Text(
                        text = options[index],
                        color = if (selected) appColors.accentText else appColors.textPrimary,
                        fontSize = Be.Type.rowTitle,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .pressScale(rowPress.value)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Be.Radius.input))
                            .background(
                                when {
                                    rowPress.value -> appColors.rowHigh
                                    selected -> appColors.accentSoft
                                    else -> Color.Transparent
                                },
                            )
                            .then(rowPress.modifier)
                            .padding(horizontal = Be.Space.l, vertical = Be.Space.l),
                    )
                }
            }
        }
    }
}

/** A single-line search box: mark, hint, and a clear control once there is text. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = false,
    onClose: (() -> Unit)? = null,
    contentAlpha: Float = 1f,
) {
    val closeDescription = stringResource(R.string.next_close)
    val focus = remember { FocusRequester() }
    if (requestFocus) {
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(Be.Radius.input))
            .background(appColors.field)
            .padding(horizontal = Be.Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchMark(appColors.textMuted)
        Spacer(Modifier.width(Be.Space.m))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = appColors.textPrimary, fontSize = Be.Type.rowTitle),
            cursorBrush = SolidColor(appColors.accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search,
            ),
            modifier = Modifier
                .weight(1f)
                .alpha(contentAlpha)
                .then(if (requestFocus) Modifier.focusRequester(focus) else Modifier),
            decorationBox = { inner ->
                Box(Modifier.padding(vertical = Be.Space.l)) {
                    if (value.isEmpty()) {
                        Text(
                            text = hint,
                            color = appColors.textMuted,
                            fontSize = Be.Type.rowTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
        if (value.isNotEmpty() || onClose != null) {
            Text(
                text = "×",
                color = appColors.textSecondary,
                fontSize = 18.sp,
                modifier = Modifier
                    .alpha(contentAlpha)
                    .then(if (onClose != null) Modifier.semantics {
                        contentDescription = closeDescription
                    } else Modifier)
                    .padding(start = Be.Space.s)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose ?: { onValueChange("") },
                    ),
            )
        }
    }
}

/** Header control that opens or closes the home feature search. */
@Composable
fun SearchIconButton(
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val press = rememberPressed(true, onClick)
    Box(
        modifier
            .pressScale(press.value, 0.92f)
            .size(40.dp)
            .clip(RoundedCornerShape(Be.Radius.input))
            .background(pressedFill(press.value || open, appColors.field, appColors.rowHigh))
            .then(press.modifier)
            .then(
                if (contentDescription == null) {
                    Modifier
                } else {
                    Modifier.semantics { this.contentDescription = contentDescription }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = open,
            transitionSpec = { fadeIn(BeMotion.effects()) togetherWith fadeOut(BeMotion.effects()) },
            label = "searchIcon",
        ) { opened ->
            if (opened) {
                Text(text = "×", color = appColors.textPrimary, fontSize = 18.sp)
            } else {
                SearchMark(appColors.textPrimary)
            }
        }
    }
}

@Composable
private fun SearchMark(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val radius = size.minDimension * 0.28f
        val center = Offset(size.width * 0.40f, size.height * 0.40f)
        drawCircle(color, radius, center, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
        val handle = Offset(center.x + radius * 0.72f, center.y + radius * 0.72f)
        drawLine(
            color = color,
            start = handle,
            end = Offset(size.width * 0.88f, size.height * 0.88f),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** Puts the menu under the field, or above it when the field sits too low. */
private class DropdownAnchorPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val gap = 8
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - popupContentSize.height - gap
        val y = when {
            below + popupContentSize.height <= windowSize.height -> below
            above >= 0 -> above
            else -> (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        }
        val x = anchorBounds.left.coerceIn(
            0,
            (windowSize.width - popupContentSize.width).coerceAtLeast(0),
        )
        return IntOffset(x, y)
    }
}

@Composable
internal fun Caret(color: Color, modifier: Modifier = Modifier) {
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
        textStyle = TextStyle(color = appColors.textPrimary, fontSize = Be.Type.rowTitle),
        cursorBrush = SolidColor(appColors.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.input))
            .background(appColors.field)
            .alpha(if (enabled) 1f else 0.42f)
            .onFocusChanged { state ->
                if (focused && !state.isFocused) onCommit()
                focused = state.isFocused
            }
            .padding(horizontal = Be.Space.xl, vertical = Be.Space.xxl),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(text = hint, color = appColors.textMuted, fontSize = Be.Type.rowTitle)
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
    if (usesMiuix) {
        val colors = miuixControlColors
        MiuixTextButton(
            text = text,
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            cornerRadius = MiuixLayout.controlRadius,
            minHeight = Be.Size.buttonHeight,
            colors = MiuixButtonDefaults.textButtonColorsPrimary(
                color = colors.activeTrack,
                textColor = colors.onActiveTrack,
            ),
        )
        return
    }
    val press = rememberPressed(enabled) { onClick() }
    val fill = when {
        !enabled -> appColors.accent.copy(alpha = 0.35f)
        else -> pressedFill(press.value, appColors.accent, appColors.accentPressed)
    }
    Box(
        modifier
            .pressScale(press.value, 0.96f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.button))
            .background(fill)
            .then(press.modifier)
            .heightIn(min = Be.Size.buttonHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = appColors.accentInk,
            fontSize = Be.Type.rowTitle,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun AppTextButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = if (usesMiuix) ButtonDefaults.textButtonColors(contentColor = appActionText)
        else ButtonDefaults.textButtonColors(),
        content = content,
    )
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (usesMiuix) {
        val fill = if (LocalMiuixCard.current) appColors.insetControl else appColors.row
        MiuixTextButton(
            text = text,
            onClick = onClick,
            modifier = modifier.controlOutline(RoundedCornerShape(MiuixLayout.controlRadius)),
            enabled = enabled,
            cornerRadius = MiuixLayout.controlRadius,
            minHeight = Be.Size.buttonHeight,
            colors = MiuixButtonDefaults.textButtonColors(color = fill),
        )
        return
    }
    val press = rememberPressed(enabled) { onClick() }
    Box(
        modifier
            .pressScale(press.value, 0.96f)
            .clip(RoundedCornerShape(Be.Radius.button))
            .background(pressedFill(press.value, appColors.row, appColors.rowHigh))
            .then(press.modifier)
            .alpha(if (enabled) 1f else 0.42f)
            .heightIn(min = Be.Size.buttonHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = appColors.textPrimary,
            fontSize = Be.Type.value,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = Be.Space.xl),
        )
    }
}

/* --------------------------------------------------------------- page chrome */

/** Glyphs for the five dock entries, in [SettingsPage.TAB_ORDER]. */
enum class DockGlyph { Home, Experience, Characters, Tools, User }

/** The tab strip. On a phone it is the bottom dock; on a wide screen it is the side rail. */
@Composable
internal fun PageTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    glyphs: List<DockGlyph> = emptyList(),
    backdrop: GlassBackdrop? = null,
) {
    if (vertical) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(Be.Space.s)) {
            labels.forEachIndexed { index, label ->
                TabItem(
                    label, glyphs.getOrNull(index), index == selectedIndex, true,
                    { onSelect(index) }, Modifier.fillMaxWidth(),
                )
            }
        }
    } else {
        val panel = appColors.panel
        val miuix = usesMiuix
        val light = LocalAppAppearance.current?.lightBars == true
        val shape = if (miuix) CircleShape else RoundedCornerShape(Be.Radius.card)
        val material = if (backdrop == null) Modifier.background(panel) else {
            Modifier.glassSurface(backdrop, panel, light)
        }
        Box(
            modifier
                .fillMaxWidth()
                .height(Be.Size.dockHeight)
        ) {
            Box(Modifier.matchParentSize().clip(shape).then(material))
            Box(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 5.dp)) {
                DockTabTrack(labels, glyphs, selectedIndex, onSelect, miuix, light, backdrop)
            }
        }
    }
}

@Composable
private fun DockTabTrack(
    labels: List<String>,
    glyphs: List<DockGlyph>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    miuix: Boolean,
    light: Boolean,
    backdrop: GlassBackdrop?,
) {
    if (labels.isEmpty()) return
    val last = labels.lastIndex
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val activeIndex by rememberUpdatedState(selectedIndex)
    val select by rememberUpdatedState(onSelect)
    val travel = remember { Animatable(selectedIndex.coerceIn(0, last).toFloat()) }
    var fingerPosition by remember { mutableStateOf<Float?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var pressedIndex by remember { mutableStateOf<Int?>(null) }
    var trackOrigin by remember { mutableStateOf(Offset.Unspecified) }
    val previewIndex by remember {
        derivedStateOf { fingerPosition?.roundToInt() ?: pressedIndex ?: activeIndex }
    }
    // Miuix LiquidGlassNavigationBar uses independent spring axes and 78/56 press magnification.
    val engaged = miuix && (pressedIndex != null || travel.isRunning)
    val pressedScale = if (engaged) 78f / 56f else 1f
    val focusScaleX by animateFloatAsState(pressedScale, spring(0.6f, 250f, 0.001f), label = "dockFocusX")
    val focusScaleY by animateFloatAsState(pressedScale, spring(0.7f, 250f, 0.001f), label = "dockFocusY")
    val pressProgress by animateFloatAsState(
        if (engaged) 1f else 0f, spring(1f, 1000f, 0.001f), label = "dockFocusPress",
    )

    val targetIndex = pressedIndex ?: selectedIndex
    LaunchedEffect(targetIndex, dragging, last) {
        if (!dragging) {
            fingerPosition?.let { travel.snapTo(it) }
            fingerPosition = null
            val motion = if (miuix) spring(1f, 1000f, 0.001f) else BeMotion.tabTravel()
            travel.animateTo(targetIndex.coerceIn(0, last).toFloat(), motion)
        }
    }

    val gestures = if (miuix && last > 0) {
        Modifier.pointerInput(last, rtl) {
            fun positionAt(x: Float): Float {
                val physical = x / (size.width.toFloat() / (last + 1)) - 0.5f
                return (if (rtl) last - physical else physical).coerceIn(0f, last.toFloat())
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val slop = viewConfiguration.touchSlop
                pressedIndex = positionAt(down.position.x).roundToInt()
                try {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes
                            .firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        if (!change.pressed) {
                            change.consume()
                            val position = positionAt(change.position.x)
                            if (dragging) fingerPosition = position
                            val target = position.roundToInt()
                            if (!dragging || target != activeIndex) select(target)
                            break
                        }
                        if (!dragging) {
                            val delta = change.position - down.position
                            if (abs(delta.y) > slop && abs(delta.y) > abs(delta.x)) break
                            if (abs(delta.x) < slop) continue
                            dragging = true
                        }
                        change.consume()
                        fingerPosition = positionAt(change.position.x)
                    }
                } finally {
                    dragging = false
                    pressedIndex = null
                }
            }
        }
    } else Modifier

    Box(
        Modifier.fillMaxSize().then(gestures)
            .onGloballyPositioned { trackOrigin = it.localToRoot(Offset.Zero) },
    ) {
        if (selectedIndex in labels.indices) {
            val tint = if (miuix) miuixControlColors.activeTrack else appColors.accent
            val material = if (backdrop == null) Modifier.background(tint) else {
                Modifier.glassSurface(backdrop, tint, light, pressProgress = { pressProgress }) { cellWidth ->
                    val position = fingerPosition ?: travel.value
                    trackOrigin + Offset((if (rtl) last - position else position) * cellWidth, 0f)
                }
            }
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(1f / labels.size)
                    .graphicsLayer {
                        val position = fingerPosition ?: travel.value
                        translationX = (if (rtl) -position else position) * size.width
                        scaleX = focusScaleX
                        scaleY = focusScaleY
                    }
                    .clip(if (miuix) CircleShape else RoundedCornerShape(Be.Radius.compact))
                    .then(material),
            )
        }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { index, label ->
                TabItem(
                    label, glyphs.getOrNull(index), index == previewIndex, false,
                    { onSelect(index) }, Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    glyph: DockGlyph?,
    selected: Boolean,
    vertical: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val press = rememberPressed(enabled = true) { onClick() }
    val miuixDock = !vertical && usesMiuix
    val selectedInk = if (miuixDock) miuixControlColors.onActiveTrack else appColors.accentInk
    val ink by animateColorAsState(
        targetValue = if (selected) selectedInk else appColors.textSecondary,
        animationSpec = BeMotion.spatial(),
        label = "tabInk",
    )
    val fillTarget = when {
        vertical && selected && press.value -> appColors.accentPressed
        vertical && selected -> appColors.accent
        !vertical && selected && press.value -> if (miuixDock) Color.Transparent else appColors.accentPressed
        press.value -> appColors.rowHigh
        else -> Color.Transparent
    }
    val fill by animateColorAsState(fillTarget, BeMotion.effects(), label = "tabFill")
    Box(
        modifier
            .pressScale(press.value && !miuixDock, 0.94f)
            .clip(RoundedCornerShape(if (vertical) Be.Radius.button else Be.Radius.compact))
            .background(fill)
            .then(press.modifier)
            .semantics {
                this.selected = selected
                role = Role.Tab
            }
            .heightIn(min = if (vertical) Be.Size.touchTarget else Be.Size.dockItemHeight),
        contentAlignment = Alignment.Center,
    ) {
        if (vertical) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Be.Space.l),
                modifier = Modifier.padding(horizontal = Be.Space.l),
            ) {
                if (glyph != null) DockMark(glyph, ink, Modifier.size(22.dp))
                TabLabel(label, ink, Be.Type.tab)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (glyph != null) DockMark(glyph, ink, Modifier.size(22.dp))
                if (glyph != null) Spacer(Modifier.height(2.dp))
                TabLabel(
                    label,
                    ink,
                    Be.Type.dock,
                    if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun TabLabel(
    label: String,
    color: Color,
    size: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight = FontWeight.Medium,
) {
    Text(
        text = label,
        color = color,
        fontSize = size,
        fontWeight = weight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Dock glyphs from the shipped SVG set, tinted for the active and idle states. */
@Composable
private fun DockMark(glyph: DockGlyph, color: Color, modifier: Modifier = Modifier) {
    val drawable = when (glyph) {
        DockGlyph.Home -> R.drawable.ic_dock_home
        DockGlyph.Experience -> R.drawable.ic_dock_experience
        DockGlyph.Characters -> R.drawable.ic_dock_characters
        DockGlyph.Tools -> R.drawable.ic_dock_tools
        DockGlyph.User -> R.drawable.ic_dock_user
    }
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier,
    )
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
            .pressScale(press.value, 0.92f)
            .size(Be.Size.swatch)
            .clip(RoundedCornerShape(Be.Radius.chip))
            .background(parseHex(hex) ?: appColors.row)
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
            .background(parseHex(hex) ?: appColors.row),
    )
}

/** Accepts `#RRGGBB` with or without the hash; null when the text is not a colour. */
fun parseHex(text: String): Color? {
    val trimmed = text.trim().removePrefix("#")
    if (trimmed.length != 6) return null
    val value = trimmed.toIntOrNull(16) ?: return null
    return Color(value or -0x1000000)
}
