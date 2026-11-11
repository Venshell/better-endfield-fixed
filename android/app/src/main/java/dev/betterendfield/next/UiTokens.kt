package dev.betterendfield.next

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The single source of truth for the app's look.
 *
 * The direction is industrial: near-black ground, one white text ramp for
 * hierarchy, and a single yellow that is spent only on the primary action, the
 * current selection and the few states worth interrupting for. There is no
 * second hue anywhere - a green or teal reference would break the palette's
 * discipline even when it is only used for a success message.
 *
 * Layers step by fill rather than by outline, so the elevation ramp is the
 * information architecture: page, then panel, then row, then field. The four
 * steps are deliberately close together, because a large luminance jump between
 * a card and the row inside it reads as a rendering error rather than a
 * hierarchy.
 *
 * Text contrast is chosen so body text on [Colors.panel] and ink text on a
 * filled [Colors.accent] button both clear the 7:1 target; that is what keeps a
 * borderless layout legible instead of merely sparse.
 *
 * The mix is held to roughly 76% black/grey, 18% white/grey text and 6% yellow.
 * Anything that would raise the yellow above a sixth of the surface belongs in
 * the neutral ramp instead.
 */
object Be {

    object Colors {
        /* --- the neutral ramp: page -> panel -> row -> field --- */

        val background = Color(0xFF0A0A0A)
        val panel = Color(0xFF121212)
        val panelHigh = Color(0xFF1D1D1D)

        /**
         * The fourth step. The palette sheet only names three greys, but a row
         * sits *on* a panel, so panel itself cannot serve as the row fill
         * without flattening the card. #191919 keeps the step visible from the
         * panel (#121212) and still readable against the field (#1D1D1D).
         */
        val row = Color(0xFF191919)
        val rowHigh = Color(0xFF242424)
        val field = Color(0xFF1D1D1D)

        /* --- the one accent --- */

        val accent = Color(0xFFF4E900)
        val accentPressed = Color(0xFFE8DC00)

        /** Pressed-but-not-primary and disabled accent, for borders and busy hints. */
        val accentDim = Color(0xFFB9AE00)
        val accentSoft = Color(0x1FF4E900)
        val accentInk = Color(0xFF0A0A0A)

        /* --- text --- */

        val textPrimary = Color(0xFFF2F2EE)
        val textSecondary = Color(0xFFA8A8A8)
        val textMuted = Color(0xFF777777)

        /* --- dividers, only ever drawn as a 1dp rule --- */

        val track = Color(0xFF303030)
        val outline = Color(0xFF303030)
        val outlineStrong = Color(0xFF3A3A3A)

        val switchTrackOff = Color(0xFF303030)
        val switchThumbOff = Color(0xFFA8A8A8)

        /** Selection strokes on the colour palette, and the remove affordance. */
        val selectionRing = Color(0xFFFFFFFF)

        /**
         * Errors keep their own ramp. Reusing the accent for a failure would
         * teach the eye that yellow means "something went wrong", which is the
         * opposite of what it means everywhere else.
         */
        val danger = Color(0xFFFF6B5A)
        val dangerSoft = Color(0x1FFF6B5A)

        /**
         * Panel tones for the in-game surface. It sits on top of a rendered
         * frame, so it stays more opaque than the settings app and leans harder
         * on fill contrast under a bright HUD.
         *
         * The experimental in-game Compose panel reads these tokens directly.
         * The previous Java/View panel mirrored them as integer constants;
         * game-process acceptance of the new composition remains pending.
         */
        val overlayPanel = Color(0xED1F2326)
        val overlayRow = Color(0xC0343635)
        val overlayField = Color(0x99343635)
        val overlayPressed = Color(0xFF2A2A2A)
        val overlayOutline = Color(0x40B5B8BB)
        val overlayHandle = Color(0xFFCECBC1)
        val overlayHandleOutline = Color(0xFFB5B8BB)

        /** Palette presets offered for the login logo theme colour. */
        val logoPresets = listOf(
            "#F4E900", "#35C8E8", "#F0645A", "#41C77A", "#D866B7", "#F2F2F2",
        )
    }

    object Space {
        val none = 0.dp
        val hairline = 2.dp
        val xs = 4.dp
        val s = 6.dp
        val m = 8.dp
        val l = 12.dp
        val xl = 14.dp
        val xxl = 16.dp
        val gutter = 16.dp
        val cardInner = 16.dp
        val section = 22.dp
        val page = 20.dp
        val footer = 32.dp
    }

    object Radius {
        val chip = 7.dp
        val inner = 8.dp
        val input = 12.dp
        val row = 13.dp
        val status = 13.dp
        val button = 14.dp
        val tabs = 18.dp
        val card = 20.dp
        val badge = 20.dp
        val header = 22.dp
        val pill = 999.dp
    }

    object Size {
        val touchTarget = 52.dp
        val rowMinHeight = 64.dp
        val fieldHeight = 54.dp
        val buttonHeight = 54.dp
        val tabHeight = 44.dp
        val tabHeightBottom = 56.dp
        val sliderHeight = 40.dp
        val swatch = 42.dp
        val avatar = 68.dp
        val railWidth = 240.dp
        val contentMaxWidth = 860.dp
        val overlayPanelWidth = 300.dp
        val overlayPanelMaxWidth = 320.dp
        val overlayHandle = 50.dp
    }

    object Type {
        val eyebrow = 11.sp
        val caption = 11.sp
        val body = 13.sp
        val bodySmall = 12.sp
        val label = 12.sp
        val value = 13.sp
        val title = 22.sp
        val titleLarge = 26.sp
        val cardTitle = 16.sp
        val rowTitle = 15.sp
        val tab = 14.sp
        val tabCompact = 13.sp
        val appName = 24.sp
        val mono = 11.sp
        val monoSmall = 9.sp
    }

    /** Letter spacing for the spaced all-caps eyebrows and group labels. */
    const val EyebrowTracking = 0.1f
    const val CaptionTracking = 0.06f
    const val RawEyebrowTracking = 0.14f
}
