package dev.betterendfield.next

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Binds the industrial tokens to Material so that stock controls - switches,
 * sliders, text fields, menus, progress bars - inherit the palette instead of
 * arriving with Material's own tonal colours attached to them.
 *
 * The app has exactly one theme. There is no light variant on purpose: the panel
 * is read over a dark game frame and next to a dark launcher, so a light scheme
 * would only ever be wrong.
 */

private val BeColorScheme = darkColorScheme(
    primary = Be.Colors.accent,
    onPrimary = Be.Colors.accentInk,
    primaryContainer = Be.Colors.accentSoft,
    onPrimaryContainer = Be.Colors.accent,
    inversePrimary = Be.Colors.accentPressed,

    secondary = Be.Colors.accent,
    onSecondary = Be.Colors.accentInk,
    secondaryContainer = Be.Colors.accentSoft,
    onSecondaryContainer = Be.Colors.accent,

    tertiary = Be.Colors.accent,
    onTertiary = Be.Colors.accentInk,
    tertiaryContainer = Be.Colors.accentSoft,
    onTertiaryContainer = Be.Colors.accent,

    background = Be.Colors.background,
    onBackground = Be.Colors.textPrimary,

    surface = Be.Colors.panel,
    onSurface = Be.Colors.textPrimary,
    surfaceVariant = Be.Colors.row,
    onSurfaceVariant = Be.Colors.textSecondary,
    surfaceTint = Be.Colors.accent,

    surfaceContainerLowest = Be.Colors.background,
    surfaceContainerLow = Be.Colors.panel,
    surfaceContainer = Be.Colors.panel,
    surfaceContainerHigh = Be.Colors.panelHigh,
    surfaceContainerHighest = Be.Colors.rowHigh,

    inverseSurface = Be.Colors.textPrimary,
    inverseOnSurface = Be.Colors.background,

    outline = Be.Colors.outline,
    outlineVariant = Be.Colors.outlineStrong,

    error = Be.Colors.danger,
    onError = Be.Colors.accentInk,
    errorContainer = Be.Colors.dangerSoft,
    onErrorContainer = Be.Colors.danger,

    scrim = androidx.compose.ui.graphics.Color.Black,
)

private val BeShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(Be.Radius.chip),
    small = androidx.compose.foundation.shape.RoundedCornerShape(Be.Radius.input),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(Be.Radius.row),
    large = androidx.compose.foundation.shape.RoundedCornerShape(Be.Radius.button),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(Be.Radius.card),
)

/** Sizes are set per component; these keep any unstyled text from defaulting elsewhere. */
private val BeTypography = Typography(
    displayLarge = TextStyle(fontSize = Be.Type.appName, fontWeight = FontWeight.Medium),
    headlineSmall = TextStyle(fontSize = Be.Type.title, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = Be.Type.title, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = Be.Type.cardTitle, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = Be.Type.rowTitle, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = Be.Type.rowTitle),
    bodyMedium = TextStyle(fontSize = Be.Type.body),
    bodySmall = TextStyle(fontSize = Be.Type.bodySmall),
    labelLarge = TextStyle(fontSize = Be.Type.rowTitle, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = Be.Type.label, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = Be.Type.caption, fontWeight = FontWeight.Medium),
)

/** Numeric readouts and the journal are the only monospaced surfaces. */
val Monospace: FontFamily = FontFamily.Monospace

@Composable
fun BetterEndfieldTheme(content: @Composable () -> Unit) {
    // isSystemInDarkTheme() is read to keep the compiler honest about the fact
    // that this app ignores it; deleting the import would silently change nothing.
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = BeColorScheme,
        shapes = BeShapes,
        typography = BeTypography,
        content = content,
    )
}
