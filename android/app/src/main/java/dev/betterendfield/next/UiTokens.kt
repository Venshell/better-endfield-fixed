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

        val background = Color(0xFF0B0B0B)
        val panel = Color(0xFF171717)
        val panelHigh = Color(0xFF222222)

        /**
         * The fourth step. A row sits *on* a panel, so panel itself cannot serve
         * as the row fill without flattening the card. #222222 keeps the step
         * visible from the panel (#171717) and still readable against the field
         * (#292929).
         */
        val row = Color(0xFF222222)
        val rowHigh = Color(0xFF2E2E2E)
        val field = Color(0xFF292929)

        /* --- the one accent --- */

        val accent = Color(0xFFEDED00)
        val accentPressed = Color(0xFFDEDE00)

        /** Pressed-but-not-primary and disabled accent, for borders and busy hints. */
        val accentDim = Color(0xFFB5B000)
        val accentSoft = Color(0x1FEDED00)
        val accentInk = Color(0xFF111111)

        /* --- text --- */

        val textPrimary = Color(0xFFF5F5F5)
        val textSecondary = Color(0xFF999999)
        val textMuted = Color(0xFF777777)

        /* --- dividers, only ever drawn as a 1dp rule --- */

        val track = Color(0xFF333333)
        val outline = Color(0xFF333333)
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
            "#EDED00", "#35C8E8", "#F0645A", "#41C77A", "#D866B7", "#F2F2F2",
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
        val gutter = 22.dp
        val cardInner = 21.dp
        /** Gap between the profile-frame cards. */
        val stack = 15.dp
        val section = 22.dp
        val page = 20.dp
        val footer = 32.dp
    }

    /**
     * One radius scale, read off the 390-wide profile frame. Every language
     * uses these same values; they are mirrored in `res/values/dimens.xml` and
     * are not overridden per locale.
     *
     * 23 cards and the dock, 19 a full row, 17 a compact row and the active
     * dock item, 12 buttons and fields, 11 and 8 the two badges, 3 the meter.
     */
    object Radius {
        val progress = 3.dp
        val chip = 8.dp
        val inner = 8.dp
        val badge = 11.dp
        val input = 12.dp
        val button = 12.dp
        val compact = 17.dp
        val row = 19.dp
        val status = 19.dp
        val tabs = 23.dp
        val card = 23.dp
        val header = 23.dp
        val pill = 999.dp
    }

    object Size {
        val touchTarget = 52.dp
        val rowMinHeight = 64.dp
        val fieldHeight = 54.dp
        val buttonHeight = 54.dp
        val tabHeight = 44.dp
        val tabHeightBottom = 56.dp
        val dockHeight = 72.dp
        val dockItemHeight = 62.dp
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
        val dock = 10.sp
        val appName = 24.sp
        val mono = 11.sp
        val monoSmall = 9.sp
    }

    /** Letter spacing for the spaced all-caps eyebrows and group labels. */
    const val EyebrowTracking = 0.1f
    const val CaptionTracking = 0.06f
    const val RawEyebrowTracking = 0.14f
}
