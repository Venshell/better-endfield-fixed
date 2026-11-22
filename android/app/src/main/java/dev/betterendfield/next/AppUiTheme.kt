package dev.betterendfield.next

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.view.Window
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

internal enum class AppThemeMode(val key: String, @StringRes val label: Int) {
    System("system", R.string.app_theme_system),
    Light("light", R.string.app_theme_light),
    Dark("dark", R.string.app_theme_dark),
}

@Immutable
internal data class AppAppearanceSettings(
    val theme: AppThemeMode = AppThemeMode.System,
    val glass: Boolean = true,
) {
    fun resolve(systemDark: Boolean): AppAppearance {
        val dark = theme == AppThemeMode.Dark || (theme == AppThemeMode.System && systemDark)
        return if (dark) AppAppearance.MiuixDark else AppAppearance.MiuixLight
    }

    fun splashTheme(appearance: AppAppearance): Int =
        if (theme == AppThemeMode.System) R.style.Theme_Bem_Starting_MiuixSystem else appearance.splashTheme
}

internal enum class AppAppearance(
    val lightBars: Boolean,
    @StyleRes val splashTheme: Int,
    @ColorRes val background: Int,
    @ColorRes val controlAccent: Int,
) {
    MiuixLight(
        true, R.style.Theme_Bem_Starting_MiuixLight,
        R.color.miuix_light_background, R.color.miuix_light_control_active,
    ),
    MiuixDark(
        false, R.style.Theme_Bem_Starting_MiuixDark,
        R.color.miuix_dark_background, R.color.miuix_dark_control_active,
    ),
}

internal data class UiPalette(
    val background: Color = Be.Colors.background,
    val panel: Color = Be.Colors.panel,
    val panelHigh: Color = Be.Colors.panelHigh,
    val row: Color = Be.Colors.row,
    val rowHigh: Color = Be.Colors.rowHigh,
    val field: Color = Be.Colors.field,
    val insetControl: Color = field,
    val accent: Color = Be.Colors.accent,
    val accentPressed: Color = Be.Colors.accentPressed,
    val accentDim: Color = Be.Colors.accentDim,
    val accentSoft: Color = Be.Colors.accentSoft,
    val accentInk: Color = Be.Colors.accentInk,
    val accentText: Color = accent,
    val textPrimary: Color = Be.Colors.textPrimary,
    val textSecondary: Color = Be.Colors.textSecondary,
    val textMuted: Color = Be.Colors.textMuted,
    val track: Color = Be.Colors.track,
    val outline: Color = Be.Colors.outline,
    val outlineStrong: Color = Be.Colors.outlineStrong,
    val switchTrackOff: Color = Be.Colors.switchTrackOff,
    val switchThumbOff: Color = Be.Colors.switchThumbOff,
    val selectionRing: Color = Be.Colors.selectionRing,
    val danger: Color = Be.Colors.danger,
    val dangerSoft: Color = Be.Colors.dangerSoft,
)

internal val IndustrialPalette = UiPalette()

// Miuix documentation site's background, brand, text and border scales.
// Stronger blue and red variants keep small labels legible on both surfaces.
private val WebLight = UiPalette(
    panel = Color(0xFFF6F6F7),
    panelHigh = Color.White,
    row = Color(0xFFEEEDF5),
    rowHigh = Color(0xFFE0E0E0),
    field = Color(0xFFEEEDF5),
    insetControl = Color.White,
    accent = Color(0xFF3482FF),
    accentPressed = Color(0xFF5D9BFF),
    accentDim = Color(0xFF2769CA),
    accentSoft = Color(0x193482FF),
    accentInk = Color(0xFF161618),
    accentText = Color(0xFF2259AE),
    textPrimary = Color(0xFF3C3C43),
    textSecondary = Color(0xFF67676C),
    textMuted = Color(0xFF67676C),
    track = Color(0xFFC2C2C4),
    outline = Color(0xFFD9D9D9),
    outlineStrong = Color(0xFFC2C2C4),
    switchTrackOff = Color(0xFFC2C2C4),
    switchThumbOff = Color.White,
    selectionRing = Color(0xFF3C3C43),
    danger = Color(0xFF9D3928),
    dangerSoft = Color(0x199D3928),
)

private val WebDark = UiPalette(
    panel = Color(0xFF202127),
    panelHigh = Color(0xFF202127),
    row = Color(0xFF2E2E32),
    rowHigh = Color(0xFF393939),
    field = Color(0xFF2E2E32),
    insetControl = Color(0xFF161618),
    accent = Color(0xFF277AF7),
    accentPressed = Color(0xFF338FE4),
    accentDim = Color(0xFF338FE4),
    accentSoft = Color(0x20277AF7),
    accentInk = Color.Black,
    accentText = Color(0xFF5D9BFF),
    textPrimary = Color(0xFFDFDFD6),
    textSecondary = Color(0xFF98989F),
    textMuted = Color(0xFF98989F),
    track = Color(0xFF404040),
    outline = Color(0xFF393939),
    outlineStrong = Color(0xFF404040),
    switchTrackOff = Color(0xFF404040),
    switchThumbOff = Color(0xFF98989F),
)

private fun miuixColors(palette: UiPalette, light: Boolean): top.yukonga.miuix.kmp.theme.Colors {
    val colors = if (light) lightColorScheme() else darkColorScheme()
    return colors.copy(
        primary = palette.accent,
        onPrimary = palette.accentInk,
        primaryVariant = palette.accentPressed,
        onPrimaryVariant = palette.accentText,
        primaryContainer = palette.accentSoft,
        onPrimaryContainer = palette.accentText,
        tertiaryContainer = palette.accentSoft,
        onTertiaryContainer = palette.accentText,
        disabledPrimary = palette.track,
        disabledOnPrimary = palette.textMuted,
        disabledPrimaryButton = palette.outline,
        disabledOnPrimaryButton = palette.textSecondary,
        disabledPrimarySlider = palette.track,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.panel,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.row,
        onSurfaceSecondary = palette.textSecondary,
        surfaceContainer = palette.panel,
        onSurfaceContainer = palette.textPrimary,
        onSurfaceContainerVariant = palette.textSecondary,
        secondaryVariant = palette.row,
        onSecondaryVariant = palette.textPrimary,
        disabledSecondaryVariant = palette.outline,
        disabledOnSecondaryVariant = palette.textSecondary,
        outline = palette.outlineStrong,
        dividerLine = palette.outline,
        sliderKeyPoint = palette.outline,
        sliderKeyPointForeground = palette.accent,
        sliderBackground = palette.track,
        error = palette.danger,
        onError = if (light) Color.White else palette.accentInk,
        errorContainer = palette.dangerSoft,
        onErrorContainer = palette.danger,
    )
}

internal val LocalUiPalette = staticCompositionLocalOf { IndustrialPalette }
internal val LocalAppAppearance = staticCompositionLocalOf<AppAppearance?> { null }
internal val LocalAppearanceSettings = staticCompositionLocalOf { AppAppearanceSettings() }
internal val LocalSetAppearanceSettings = staticCompositionLocalOf<(AppAppearanceSettings) -> Unit> { {} }

internal val appColors: UiPalette
    @Composable @ReadOnlyComposable get() = LocalUiPalette.current

internal val usesMiuix: Boolean
    @Composable @ReadOnlyComposable get() = LocalAppAppearance.current != null

internal object MiuixLayout {
    val pageGutter = 12.dp
    val groupGap = 20.dp
    val inset = 16.dp
    val cardRadius = 18.dp
    val controlRadius = 12.dp
    val rowHeight = 56.dp
}

internal val appSectionGap
    @Composable @ReadOnlyComposable get() = if (usesMiuix) MiuixLayout.groupGap else Be.Space.l

internal val appPageGutter
    @Composable @ReadOnlyComposable get() = if (usesMiuix) MiuixLayout.pageGutter else Be.Space.gutter

@Immutable
internal data class MiuixControlColors(
    val activeTrack: Color,
    val activeThumb: Color,
    val inactiveTrack: Color,
    val inactiveThumb: Color,
    val onActiveTrack: Color = activeThumb,
)

private val MiuixActiveThumb = Color(0xFFF9F8FE)

internal val miuixControlColors: MiuixControlColors
    @Composable @ReadOnlyComposable get() =
        if (LocalAppAppearance.current == AppAppearance.MiuixLight) {
            MiuixControlColors(
                activeTrack = colorResource(R.color.miuix_light_control_active),
                activeThumb = MiuixActiveThumb,
                inactiveTrack = Color(0xFFB1B1BD),
                inactiveThumb = Color(0xFF7E818A),
            )
        } else {
            MiuixControlColors(
                activeTrack = colorResource(R.color.miuix_dark_control_active),
                activeThumb = MiuixActiveThumb,
                inactiveTrack = Color(0xFF404040),
                inactiveThumb = Color(0xFF98989F),
                onActiveTrack = Color.Black,
            )
        }

internal val appActionText: Color
    @Composable @ReadOnlyComposable get() = when (LocalAppAppearance.current) {
        null -> appColors.accentText
        AppAppearance.MiuixLight -> colorResource(R.color.miuix_light_control_active)
        AppAppearance.MiuixDark -> lerp(colorResource(R.color.miuix_dark_control_active), MiuixActiveThumb, 0.25f)
    }

private const val APPEARANCE_PREFS = "app_appearance"
private const val LEGACY_APPEARANCE_KEY = "theme"
private const val LEGACY_STYLE_KEY = "style"
private const val THEME_MODE_KEY = "theme_mode"
private const val GLASS_KEY = "liquid_glass"

private fun Context.appearancePreferences() = getSharedPreferences(APPEARANCE_PREFS, Context.MODE_PRIVATE)

private fun SharedPreferences.readAppearanceSettings(): AppAppearanceSettings {
    val legacyMode = when (getString(LEGACY_APPEARANCE_KEY, null)) {
        "miuix_light" -> AppThemeMode.Light
        "miuix_dark", "industrial" -> AppThemeMode.Dark
        else -> AppThemeMode.System
    }
    val theme = getString(THEME_MODE_KEY, null)
    return AppAppearanceSettings(
        theme = AppThemeMode.entries.firstOrNull { it.key == theme } ?: legacyMode,
        glass = getBoolean(GLASS_KEY, true),
    )
}

internal fun Context.readAppearanceSettings() = appearancePreferences().readAppearanceSettings()

internal fun Context.systemUsesDarkTheme(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

@Composable
internal fun AppUiTheme(
    window: Window,
    onAppearanceChanged: (Int) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) { context.appearancePreferences() }
    val saveAppearance: (AppAppearanceSettings) -> Unit = { selection ->
        preferences.edit()
            .putString(THEME_MODE_KEY, selection.theme.key)
            .putBoolean(GLASS_KEY, selection.glass)
            .remove(LEGACY_APPEARANCE_KEY)
            .remove(LEGACY_STYLE_KEY)
            .apply()
    }

    var settings by remember(preferences) { mutableStateOf(preferences.readAppearanceSettings()) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == THEME_MODE_KEY || key == GLASS_KEY || key == LEGACY_APPEARANCE_KEY) {
                settings = preferences.readAppearanceSettings()
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        settings = preferences.readAppearanceSettings()
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val appearance = settings.resolve(isSystemInDarkTheme())
    val light = appearance.lightBars
    val background = colorResource(appearance.background)
    val splashTheme = settings.splashTheme(appearance)
    DisposableEffect(window, splashTheme, background) {
        onAppearanceChanged(splashTheme)
        window.setBackgroundDrawable(ColorDrawable(background.toArgb()))
        onDispose {}
    }
    SideEffect {
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.isAppearanceLightStatusBars = light
        bars.isAppearanceLightNavigationBars = light
    }

    val currentContent by rememberUpdatedState(content)
    val page = remember { movableContentOf { currentContent() } }
    val palette = remember(appearance, background) {
        (if (light) WebLight else WebDark).copy(background = background)
    }
    val material = remember(palette, light) { materialColors(palette, light) }
    CompositionLocalProvider(
        LocalAppAppearance provides appearance,
        LocalAppearanceSettings provides settings,
        LocalSetAppearanceSettings provides saveAppearance,
        LocalUiPalette provides palette,
    ) {
        val miuix = remember(palette, light) { miuixColors(palette, light) }
        MiuixTheme(colors = miuix) {
            MaterialUiTheme(material, page)
        }
    }
}
