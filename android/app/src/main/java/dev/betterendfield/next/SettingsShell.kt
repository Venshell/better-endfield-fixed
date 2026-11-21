package dev.betterendfield.next

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val DockTopGap = Be.Space.m
private val DockBottomGap = Be.Space.l
private val DockClearance = Be.Size.dockHeight + DockTopGap + DockBottomGap

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
internal fun SettingsShell(
    state: SettingsState,
    gameControl: GameControlController,
    onPreviewOverlay: () -> Unit,
    onInstallBem: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp >= 720 && configuration.fontScale < 1.5f
    val miuix = usesMiuix
    val home = state.page == SettingsPage.HOME
    val openPage = { index: Int -> state.openPage(index) }
    var homeSearch by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.page) {
        if (state.page != SettingsPage.HOME) homeSearch = false
    }
    val showSearch = home && homeSearch
    val toggleSearch: () -> Unit = {
        if (state.page == SettingsPage.HOME) homeSearch = !homeSearch
    }

    Surface(
        color = appColors.background,
        contentColor = appColors.textPrimary,
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
                        if (!miuix) {
                            AppHeader(searchOpen = showSearch, onSearch = toggleSearch.takeIf { home })
                            Spacer(Modifier.height(Be.Space.xxl))
                        }
                        PageTabs(
                            labels = SettingsPage.TAB_LABELS.map { stringResource(it) },
                            selectedIndex = SettingsPage.TAB_ORDER.indexOf(SettingsPage.parentOf(state.page)),
                            onSelect = { tab -> openPage(SettingsPage.TAB_ORDER[tab]) },
                            vertical = true,
                            glyphs = DockGlyphs,
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        ShellBody(
                            state, gameControl, onPreviewOverlay, onInstallBem, showSearch, openPage,
                            { homeSearch = false }, toggleSearch, Modifier.fillMaxSize(),
                        )
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    if (!miuix && !SettingsPage.isSubPage(state.page)) {
                        PageHeader(searchOpen = showSearch, onSearch = toggleSearch.takeIf { home })
                    }
                    ShellBody(
                        state, gameControl, onPreviewOverlay, onInstallBem, showSearch, openPage,
                        { homeSearch = false }, toggleSearch, Modifier.weight(1f),
                        dockClearance = if (miuix) DockClearance else Be.Space.none,
                    )
                    if (!miuix) BottomDock(state.page, openPage)
                }
                if (miuix) BottomDock(state.page, openPage, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun BottomDock(page: Int, onOpenPage: (Int) -> Unit, modifier: Modifier = Modifier) {
    PageTabs(
        labels = SettingsPage.TAB_LABELS.map { stringResource(it) },
        selectedIndex = SettingsPage.TAB_ORDER.indexOf(SettingsPage.parentOf(page)),
        onSelect = { tab -> onOpenPage(SettingsPage.TAB_ORDER[tab]) },
        glyphs = DockGlyphs,
        modifier = modifier.padding(
            start = Be.Space.gutter,
            end = Be.Space.gutter,
            top = DockTopGap,
            bottom = DockBottomGap,
        ),
    )
}

private data class SettingsDestination(val page: Int, val searching: Boolean = false) {
    fun previous(): SettingsDestination? = when {
        searching -> copy(searching = false)
        SettingsPage.isSubPage(page) -> SettingsDestination(SettingsPage.parentOf(page))
        else -> null
    }
}

@Composable
private fun ShellBody(
    state: SettingsState,
    gameControl: GameControlController,
    onPreviewOverlay: () -> Unit,
    onInstallBem: () -> Unit,
    showSearch: Boolean,
    onOpenPage: (Int) -> Unit,
    onCloseSearch: () -> Unit,
    onToggleSearch: () -> Unit,
    modifier: Modifier = Modifier,
    dockClearance: Dp = Be.Space.none,
) {
    val destination = SettingsDestination(state.page, showSearch)
    val previous = destination.previous()
    val motion = remember { SeekableTransitionState(destination) }
    val transition = rememberTransition(motion, label = "settingsDestination")
    var backProgress by remember(destination) { mutableStateOf<Float?>(null) }
    var headerHeightPx by remember { mutableIntStateOf(0) }

    PredictiveBackHandler(enabled = previous != null) { events ->
        if (previous == null) return@PredictiveBackHandler
        try {
            events.collect { event -> backProgress = event.progress }
            if (destination.searching) onCloseSearch() else state.back()
        } finally {
            backProgress = null
        }
    }
    LaunchedEffect(destination, backProgress) {
        val progress = backProgress
        when {
            progress != null && previous != null -> {
                if (motion.currentState != destination) motion.snapTo(destination)
                motion.seekTo(progress, previous)
            }
            motion.currentState != destination -> motion.animateTo(destination)
            motion.targetState != destination -> {
                coroutineScope {
                    animate(motion.fraction, 0f, animationSpec = tween(160)) { fraction, _ ->
                        launch { motion.seekTo(fraction) }
                    }
                }
                motion.snapTo(destination)
            }
        }
    }

    transition.AnimatedContent(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.TopCenter,
        contentKey = { it.page },
        transitionSpec = {
            when {
                SettingsPage.parentOf(initialState.page) != SettingsPage.parentOf(targetState.page) -> {
                    val travel = pageTravel(initialState.page, targetState.page)
                    val slide = BeMotion.tabTravel<IntOffset>()
                    slideInHorizontally(slide) { travel * it }
                        .togetherWith(slideOutHorizontally(slide) { -travel * it })
                }
                initialState.previous() == targetState -> {
                    val slide = tween<IntOffset>(240)
                    slideInHorizontally(slide) { -it / 5 }
                        .togetherWith(slideOutHorizontally(slide) { it })
                        .apply { targetContentZIndex = -1f }
                }
                else -> {
                    val fade = BeMotion.effects<Float>()
                    val travel = pageTravel(initialState.page, targetState.page)
                    val shift = BeMotion.fastSpatial<IntOffset>()
                    (slideInHorizontally(shift) { travel * it / 8 } + fadeIn(fade))
                        .togetherWith(slideOutHorizontally(shift) { -travel * it / 8 } + fadeOut(fade))
                }
            }.using(null)
        },
    ) { page ->
        Box(Modifier.fillMaxSize().background(appColors.background)) {
            if (page.page == SettingsPage.HOME) {
                val floatingHeader = usesMiuix
                val headerClearance = with(LocalDensity.current) { headerHeightPx.toDp() }
                transition.AnimatedVisibility(
                    visible = { !it.searching },
                    enter = fadeIn(BeMotion.effects()),
                    exit = fadeOut(BeMotion.effects()),
                ) {
                    SettingsPageContent(
                        page.page, state, gameControl, onPreviewOverlay, onInstallBem, headerClearance, dockClearance,
                    )
                }
                transition.AnimatedVisibility(
                    visible = { it.searching },
                    enter = fadeIn(BeMotion.effects()) + expandVertically(BeMotion.spatial(), expandFrom = Alignment.Top),
                    exit = slideOutHorizontally(tween(240)) { it },
                ) {
                    HomeSearchPanel(
                        onOpenPage = onOpenPage,
                        onClose = onCloseSearch,
                        modifier = Modifier.fillMaxSize().background(appColors.background),
                        topPadding = headerClearance,
                        bottomPadding = Be.Space.l + dockClearance,
                    )
                }
                if (floatingHeader) {
                    PageHeader(
                        searchOpen = showSearch,
                        onSearch = onToggleSearch,
                        modifier = Modifier.onSizeChanged { headerHeightPx = it.height },
                    )
                }
            } else {
                SettingsPageContent(
                    page.page, state, gameControl, onPreviewOverlay, onInstallBem, Be.Space.none, dockClearance,
                )
            }
        }
    }
}

@Composable
private fun SettingsPageContent(
    page: Int,
    state: SettingsState,
    gameControl: GameControlController,
    onPreviewOverlay: () -> Unit,
    onInstallBem: () -> Unit,
    headerClearance: Dp,
    dockClearance: Dp,
) {
    val topPadding = Be.Space.m + if (page == SettingsPage.HOME) headerClearance else Be.Space.none
    val bottomPadding = Be.Space.footer + dockClearance
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        if (page == SettingsPage.HOME || page == SettingsPage.EXPERIENCE) {
            val status = state.status
            val gap = appSectionGap
            LazyColumn(
                modifier = Modifier.widthIn(max = Be.Size.contentMaxWidth).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = appPageGutter,
                    end = appPageGutter,
                    top = topPadding,
                    bottom = bottomPadding + if (status.isBlank()) Be.Space.xxl else Be.Space.none,
                ),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                if (page == SettingsPage.HOME) homePage(state, gameControl) else experiencePage(state)
                if (status.isNotBlank()) item("page_status") { Notice(status) }
            }
            return@Box
        }
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
                    start = appPageGutter,
                    end = appPageGutter,
                    top = topPadding,
                    bottom = bottomPadding,
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
        color = appActionText,
        fontSize = Be.Type.body,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(Be.Radius.chip))
            .clickable(onClick = onBack)
            .padding(horizontal = Be.Space.m, vertical = Be.Space.s),
    )
}

@Composable
private fun PageHeader(searchOpen: Boolean, onSearch: (() -> Unit)?, modifier: Modifier = Modifier) {
    val miuix = usesMiuix
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .widthIn(max = Be.Size.contentMaxWidth)
                .fillMaxWidth()
                .padding(
                    start = Be.Space.gutter,
                    end = Be.Space.gutter,
                    top = if (miuix) DockBottomGap else Be.Space.page,
                    bottom = if (miuix) DockTopGap else Be.Space.page,
                ),
        ) {
            AppHeader(searchOpen, onSearch)
        }
    }
}

@Composable
private fun AppHeader(
    searchOpen: Boolean = false,
    onSearch: (() -> Unit)? = null,
) {
    val miuix = usesMiuix
    val avatarFrame = if (miuix) Modifier else Modifier.background(appColors.accent)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (miuix) Be.Radius.card else Be.Radius.header))
            .background(appColors.panel)
            .padding(if (miuix) MiuixLayout.inset else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(Be.Size.avatar)
                .clip(RoundedCornerShape(Be.Radius.badge))
                .then(avatarFrame),
        ) {
            Image(
                painter = painterResource(R.drawable.gilberta),
                contentDescription = stringResource(R.string.app_icon_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().padding(if (miuix) 0.dp else 3.dp),
            )
        }
        Spacer(Modifier.width(Be.Space.xxl))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                color = appColors.textPrimary,
                fontSize = Be.Type.appName,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.settings_subtitle),
                color = appColors.textSecondary,
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
        color = appColors.textMuted,
        fontSize = Be.Type.caption,
        fontWeight = FontWeight.Medium,
        letterSpacing = Be.CaptionTracking.sp,
        modifier = modifier,
    )
}
