package dev.betterendfield.next

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

internal fun materialColors(palette: UiPalette, light: Boolean): ColorScheme {
    val scheme = if (light) lightColorScheme() else darkColorScheme()
    return scheme.copy(
        primary = palette.accent,
        onPrimary = palette.accentInk,
        primaryContainer = palette.accentSoft,
        onPrimaryContainer = palette.accentText,
        inversePrimary = palette.accentPressed,

        secondary = palette.accent,
        onSecondary = palette.accentInk,
        secondaryContainer = palette.accentSoft,
        onSecondaryContainer = palette.accentText,

        tertiary = palette.accent,
        onTertiary = palette.accentInk,
        tertiaryContainer = palette.accentSoft,
        onTertiaryContainer = palette.accentText,

        background = palette.background,
        onBackground = palette.textPrimary,

        surface = palette.panel,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.row,
        onSurfaceVariant = palette.textSecondary,
        surfaceTint = palette.accent,

        surfaceContainerLowest = palette.background,
        surfaceContainerLow = palette.panel,
        surfaceContainer = palette.panel,
        surfaceContainerHigh = palette.panelHigh,
        surfaceContainerHighest = palette.rowHigh,

        inverseSurface = palette.textPrimary,
        inverseOnSurface = palette.background,

        outline = palette.outline,
        outlineVariant = palette.outlineStrong,

        error = palette.danger,
        onError = if (light) androidx.compose.ui.graphics.Color.White else palette.accentInk,
        errorContainer = palette.dangerSoft,
        onErrorContainer = palette.danger,

        scrim = androidx.compose.ui.graphics.Color.Black,
    )
}

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

private val industrialMaterialColors = materialColors(IndustrialPalette, light = false)

@Composable
fun BetterEndfieldTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalUiPalette provides IndustrialPalette,
        LocalAppAppearance provides AppAppearance.Industrial,
        LocalSelectAppAppearance provides {},
    ) {
        MaterialUiTheme(industrialMaterialColors, content)
    }
}

@Composable
internal fun MaterialUiTheme(colors: ColorScheme, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, shapes = BeShapes, typography = BeTypography, content = content)
}
