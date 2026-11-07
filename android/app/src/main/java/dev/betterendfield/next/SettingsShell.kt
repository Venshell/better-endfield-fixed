package dev.betterendfield.next

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The screen chrome: header, page tabs, the scrolling body, and the title row a
 * sub-page gets instead of a tab.
 *
 * The layout has two shapes rather than two screens. Past 720dp of width with a
 * saner font scale, the header and the tabs move into a fixed side rail and stay
 * reachable while a long settings column scrolls; below that they become a top
 * bar and a bottom bar around the same column. One composition, two arrangements,
 * so a setting can never exist on one layout and be missing from the other.
 *
 * A sub-page is not a fifth tab. It is reached from a card inside its parent and
 * keeps that parent's tab lit by going through [SettingsPage.parentOf], so the
 * strip always answers "which tab am I in" even two levels down.
 */
@Composable
fun SettingsShell(
    state: SettingsState,
    onPreviewOverlay: () -> Unit,
    onInstallBem: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp >= 720 && configuration.fontScale < 1.5f
    val openPage = { index: Int -> state.openPage(index) }

    Surface(
        color = Be.Colors.background,
        contentColor = Be.Colors.textPrimary,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .width(Be.Size.railWidth)
                            .fillMaxSize()
                            .padding(start = Be.Space.l, top = Be.Space.xxl, end = Be.Space.l, bottom = Be.Space.l),
                    ) {
                        AppHeader()
                        Spacer(Modifier.height(Be.Space.xxl))
                        PageTabs(
                            labels = SettingsPage.TAB_LABELS.map { stringResource(it) },
                            selectedIndex = SettingsPage.TAB_ORDER.indexOf(SettingsPage.parentOf(state.page)),
                            onSelect = { tab -> openPage(SettingsPage.TAB_ORDER[tab]) },
                            vertical = true,
                        )
                    }
                    SettingsBody(state, onPreviewOverlay, onInstallBem, Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    // A sub-page replaces the big header with its own back-and-title
                    // row, which lives in the body so the rail layout gets it too.
                    if (!SettingsPage.isSubPage(state.page)) {
                        Box(Modifier.padding(horizontal = Be.Space.gutter, vertical = Be.Space.page)) {
                            AppHeader()
                        }
                    }
                    SettingsBody(state, onPreviewOverlay, onInstallBem, Modifier.weight(1f))
                    PageTabs(
                        labels = SettingsPage.TAB_LABELS.map { stringResource(it) },
                        selectedIndex = SettingsPage.TAB_ORDER.indexOf(SettingsPage.parentOf(state.page)),
                        onSelect = { tab -> openPage(SettingsPage.TAB_ORDER[tab]) },
                        modifier = Modifier.padding(
                            start = Be.Space.gutter,
                            end = Be.Space.gutter,
                            top = Be.Space.m,
                            bottom = Be.Space.l,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * The scrolling body that every page shares.
 *
 * The scroll offset is keyed by [SettingsState.page]. That is what makes a page
 * open at its top: one container serves all eight pages, so a state that
 * outlived a page change would drop the reader into the middle of the next page
 * at whatever offset the previous one happened to be left at. Keying by page
 * still survives a rotation, because the page is restored before this composes.
 */
@Composable
private fun SettingsBody(
    state: SettingsState,
    onPreviewOverlay: () -> Unit,
    onInstallBem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = Be.Size.contentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(
                    // Keyed by the page: the shell owns a single scroll container
                    // for every page, so an unkeyed state would hand a freshly
                    // opened page whatever offset the previous one was left at.
                    rememberSaveable(state.page, saver = ScrollState.Saver) { ScrollState(0) },
                )
                .padding(
                    start = Be.Space.gutter,
                    end = Be.Space.gutter,
                    top = Be.Space.m,
                    bottom = Be.Space.footer,
                ),
        ) {
            if (SettingsPage.isSubPage(state.page)) {
                // The rail layout keeps the header instead of a title row, so the
                // back affordance has to exist there too or a sub-page would be a
                // one-way trip on a tablet.
                Box(Modifier.fillMaxWidth().padding(bottom = Be.Space.l)) {
                    BackButton(state::back)
                }
            }

            when (state.page) {
                SettingsPage.HOME -> HomePage(state)
                SettingsPage.EXPERIENCE -> ExperiencePage(state)
                SettingsPage.CHARACTERS -> CharacterPage(state, onInstallBem)
                SettingsPage.TOOLS -> ToolsPage(state, onPreviewOverlay)
                SettingsPage.FIRST_PERSON -> FirstPersonPage(state)
                SettingsPage.CAMERA_MOTION -> CameraMotionPage(state)
                SettingsPage.MMD -> MmdPage(state)
                SettingsPage.APPEARANCE -> AppearancePage(state, onInstallBem)
                SettingsPage.LOG -> LogPage(state)
                SettingsPage.ABOUT -> AboutPage()
            }
            Spacer(Modifier.height(Be.Space.xxl))
            if (state.status.isNotBlank()) Notice(state.status)
        }
    }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    Text(
        text = "← " + stringResource(R.string.action_back),
        color = Be.Colors.accent,
        fontSize = Be.Type.body,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(Be.Radius.chip))
            .clickable(onClick = onBack)
            .padding(horizontal = Be.Space.m, vertical = Be.Space.s),
    )
}

@Composable
private fun AppHeader(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.header))
            .background(Be.Colors.panel)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(Be.Size.avatar)
                .clip(RoundedCornerShape(Be.Radius.badge))
                .background(Be.Colors.accent),
        ) {
            Image(
                painter = painterResource(R.drawable.gilberta),
                contentDescription = stringResource(R.string.app_icon_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().padding(3.dp),
            )
        }
        Spacer(Modifier.width(Be.Space.xxl))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                color = Be.Colors.textPrimary,
                fontSize = Be.Type.appName,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.settings_subtitle),
                color = Be.Colors.textSecondary,
                fontSize = Be.Type.body,
                modifier = Modifier.padding(top = 3.dp),
            )
            Badge(
                text = stringResource(R.string.runtime_badge),
                modifier = Modifier.padding(top = Be.Space.m + 2.dp),
            )
        }
    }
}

/** A muted all-caps caption that introduces a list outside a card. */
@Composable
fun ListCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Be.Colors.textMuted,
        fontSize = Be.Type.caption,
        fontWeight = FontWeight.Medium,
        letterSpacing = Be.CaptionTracking.sp,
        modifier = modifier,
    )
}
