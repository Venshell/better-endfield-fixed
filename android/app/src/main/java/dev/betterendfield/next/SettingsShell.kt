package dev.betterendfield.next

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
    var homeSearch by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.page) {
        if (state.page != SettingsPage.HOME) homeSearch = false
    }
    val showSearch = state.page == SettingsPage.HOME && homeSearch

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
                        AppHeader(
                            searchOpen = homeSearch,
                            onSearch = if (state.page == SettingsPage.HOME) {
                                { homeSearch = !homeSearch }
                            } else {
                                null
                            },
                        )
                        Spacer(Modifier.height(Be.Space.xxl))
                        PageTabs(
                            labels = SettingsPage.TAB_LABELS.map { stringResource(it) },
                            selectedIndex = SettingsPage.TAB_ORDER.indexOf(SettingsPage.parentOf(state.page)),
                            onSelect = { tab -> openPage(SettingsPage.TAB_ORDER[tab]) },
                            vertical = true,
                            glyphs = DockGlyphs,
                        )
                    }
                    ShellBody(state, onPreviewOverlay, onInstallBem, showSearch, openPage, { homeSearch = false }, Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    // A sub-page replaces the big header with its own back-and-title
                    // row, which lives in the body so the rail layout gets it too.
                    if (!SettingsPage.isSubPage(state.page)) {
                        Box(Modifier.padding(horizontal = Be.Space.gutter, vertical = Be.Space.page)) {
                            AppHeader(
                                searchOpen = homeSearch,
                                onSearch = if (state.page == SettingsPage.HOME) {
                                    { homeSearch = !homeSearch }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    ShellBody(state, onPreviewOverlay, onInstallBem, showSearch, openPage, { homeSearch = false }, Modifier.weight(1f))
                    PageTabs(
                        labels = SettingsPage.TAB_LABELS.map { stringResource(it) },
                        selectedIndex = SettingsPage.TAB_ORDER.indexOf(SettingsPage.parentOf(state.page)),
                        onSelect = { tab -> openPage(SettingsPage.TAB_ORDER[tab]) },
                        glyphs = DockGlyphs,
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

@Composable
private fun ShellBody(
    state: SettingsState,
    onPreviewOverlay: () -> Unit,
    onInstallBem: () -> Unit,
    showSearch: Boolean,
    onOpenPage: (Int) -> Unit,
    onCloseSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = showSearch,
        modifier = modifier.clipToBounds(),
        transitionSpec = {
            val fade = BeMotion.effects<Float>()
            val grow = BeMotion.spatial<IntSize>()
            if (targetState) {
                (fadeIn(fade) + expandVertically(grow, expandFrom = Alignment.Top))
                    .togetherWith(fadeOut(fade))
            } else {
                fadeIn(fade).togetherWith(
                    fadeOut(fade) + shrinkVertically(grow, shrinkTowards = Alignment.Top),
                )
            }.using(null)
        },
        label = "homeSearch",
    ) { searching ->
        if (searching) {
            HomeSearchPanel(
                onOpenPage = onOpenPage,
                onClose = onCloseSearch,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            SettingsBody(state, onPreviewOverlay, onInstallBem, Modifier.fillMaxSize())
        }
    }
}

/**
 * The scrolling body that every page shares.
 *
 * The scroll offset is keyed by [SettingsState.page]. That is what makes a page
 * open at its top: one container serves all pages, so a state that
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
    AnimatedContent(
        targetState = state.page,
        modifier = modifier.fillMaxWidth().clipToBounds(),
        contentAlignment = Alignment.TopCenter,
        transitionSpec = {
            val travel = pageTravel(initialState, targetState)
            val shift = BeMotion.fastSpatial<IntOffset>()
            val fade = BeMotion.effects<Float>()
            (slideInHorizontally(shift) { width -> travel * width / 8 } + fadeIn(fade))
                .togetherWith(slideOutHorizontally(shift) { width -> -travel * width / 8 } + fadeOut(fade))
                .using(null)
        },
        label = "page",
    ) { page ->
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = Be.Size.contentMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(
                        // Keyed by the page: the shell owns a single scroll container
                        // for every page, so an unkeyed state would hand a freshly
                        // opened page whatever offset the previous one was left at.
                        rememberSaveable(page, saver = ScrollState.Saver) { ScrollState(0) },
                    )
                    .padding(
                        start = Be.Space.gutter,
                        end = Be.Space.gutter,
                        top = Be.Space.m,
                        bottom = Be.Space.footer,
                    ),
            ) {
                if (SettingsPage.isSubPage(page)) {
                    // The rail layout keeps the header instead of a title row, so the
                    // back affordance has to exist there too or a sub-page would be a
                    // one-way trip on a tablet.
                    Box(Modifier.fillMaxWidth().padding(bottom = Be.Space.l)) {
                        BackButton(state::back)
                    }
                }

                when (page) {
                    SettingsPage.HOME -> HomePage(state)
                    SettingsPage.EXPERIENCE -> ExperiencePage(state)
                    SettingsPage.CHARACTERS -> CharacterPage(state, onInstallBem)
                    SettingsPage.TOOLS -> ToolsPage(state, onPreviewOverlay)
                    SettingsPage.FIRST_PERSON -> FirstPersonPage(state)
                    SettingsPage.CAMERA_MOTION -> CameraMotionPage(state)
                    SettingsPage.MMD -> MmdPage(state)
                    SettingsPage.APPEARANCE -> AppearancePage(state, onInstallBem)
                    SettingsPage.ADMINISTRATOR_APPEARANCE -> AdministratorAppearancePage(state::markAdministratorAppearanceSaved)
                    SettingsPage.LOG -> LogPage(state)
                    SettingsPage.ABOUT -> AboutPage()
                    SettingsPage.WORKSHOP -> WorkshopPage()
                }
                Spacer(Modifier.height(Be.Space.xxl))
                if (state.status.isNotBlank()) Notice(state.status)
            }
        }
    }
}

/** Which way a page change travels: 1 enters from the right, -1 from the left. */
private fun pageTravel(from: Int, to: Int): Int {
    val fromTab = SettingsPage.parentOf(from)
    val toTab = SettingsPage.parentOf(to)
    return when {
        SettingsPage.isSubPage(to) && from == SettingsPage.parentOf(to) -> 1
        SettingsPage.isSubPage(from) && to == fromTab -> -1
        toTab != fromTab -> if (toTab > fromTab) 1 else -1
        else -> 1
    }
}

private val DockGlyphs = listOf(
    DockGlyph.Home,
    DockGlyph.Experience,
    DockGlyph.Characters,
    DockGlyph.Tools,
)

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
private fun AppHeader(
    modifier: Modifier = Modifier,
    searchOpen: Boolean = false,
    onSearch: (() -> Unit)? = null,
) {
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
        if (onSearch != null) {
            Spacer(Modifier.width(Be.Space.m))
            SearchIconButton(
                open = searchOpen,
                onClick = onSearch,
                contentDescription = stringResource(R.string.search_features),
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
