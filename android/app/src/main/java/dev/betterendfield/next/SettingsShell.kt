package dev.betterendfield.next

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
 * keeps that parent's tab lit by going through [SettingsPage.tabOf], so the
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
    val dockBackdrop = rememberGlassBackdrop(!wide && LocalAppearanceSettings.current.glass)
    var searchPage by rememberSaveable { mutableStateOf<Int?>(null) }
    var query by rememberSaveable(state.page) { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val closeSearch: () -> Unit = {
        keyboard?.hide()
        searchPage = null
        query = ""
    }
    val openPage = { index: Int ->
        if (searchPage != null) closeSearch()
        state.openPage(index)
    }
    LaunchedEffect(state.page) {
        if (searchPage != state.page) searchPage = null
    }
    val showSearch = searchPage == state.page
    val toggleSearch: () -> Unit = {
        if (showSearch) closeSearch() else searchPage = state.page
    }

    val destination = SettingsDestination(state.page, showSearch)
    val previous = destination.previous()
    val motion = remember { SeekableTransitionState(destination) }
    val transition = rememberTransition(motion, label = "settingsDestination")
    var backProgress by remember(destination) { mutableStateOf<Float?>(null) }

    PredictiveBackHandler(enabled = previous != null) { events ->
        if (previous == null) return@PredictiveBackHandler
        try {
            events.collect { event -> backProgress = event.progress }
            if (destination.searching) closeSearch() else state.back()
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

    val searchExpansion = transition.animateFloat(
        transitionSpec = { tween(240) },
        label = "searchWidth",
    ) { if (it.searching) 1f else 0f }
    val previewPage by remember(destination) {
        derivedStateOf { (backProgress ?: 0f) >= 0.5f }
    }
    val dockVisible by remember(searchExpansion) {
        derivedStateOf { searchExpansion.value < 1f }
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
                        PageTabs(
                            labels = SettingsPage.TAB_LABELS.map { stringResource(it) },
                            selectedIndex = SettingsPage.TAB_ORDER.indexOf(SettingsPage.tabOf(state.page)),
                            onSelect = { tab -> openPage(SettingsPage.TAB_ORDER[tab]) },
                            vertical = true,
                            glyphs = DockGlyphs,
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        ShellBody(
                            transition, state, gameControl, onPreviewOverlay, onInstallBem,
                            showSearch, previewPage, searchExpansion, query, { query = it },
                            closeSearch, toggleSearch, Modifier.fillMaxSize(),
                        )
                    }
                }
            } else {
                ShellBody(
                    transition, state, gameControl, onPreviewOverlay, onInstallBem,
                    showSearch, previewPage, searchExpansion, query, { query = it },
                    closeSearch, toggleSearch,
                    Modifier.fillMaxSize().captureGlassBackdrop(dockBackdrop),
                    dockClearance = DockClearance,
                )
                if (dockVisible) {
                    BottomDock(
                        state.page,
                        openPage,
                        modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer {
                            alpha = 1f - searchExpansion.value
                        },
                        backdrop = dockBackdrop,
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomDock(
    page: Int,
    onOpenPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
) {
    PageTabs(
        labels = SettingsPage.TAB_LABELS.map { stringResource(it) },
        selectedIndex = SettingsPage.TAB_ORDER.indexOf(SettingsPage.tabOf(page)),
        onSelect = { tab -> onOpenPage(SettingsPage.TAB_ORDER[tab]) },
        glyphs = DockGlyphs,
        backdrop = backdrop,
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
    transition: Transition<SettingsDestination>,
    state: SettingsState,
    gameControl: GameControlController,
    onPreviewOverlay: () -> Unit,
    onInstallBem: () -> Unit,
    showSearch: Boolean,
    previewPage: Boolean,
    searchExpansion: State<Float>,
    query: String,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onToggleSearch: () -> Unit,
    modifier: Modifier = Modifier,
    dockClearance: Dp = Be.Space.none,
) {
    transition.AnimatedContent(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.TopCenter,
        contentKey = { it.page },
        transitionSpec = {
            when {
                SettingsPage.tabOf(initialState.page) != SettingsPage.tabOf(targetState.page) -> {
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
        val pageId = page.page
        val headerBackdrop = rememberGlassBackdrop(LocalAppearanceSettings.current.glass)
        val currentPage = state.page == pageId
        val filtering = showSearch && currentPage && !previewPage
        val back: (() -> Unit)? = if (SettingsPage.isSubPage(pageId)) {
            { if (currentPage) { if (showSearch) onCloseSearch() else state.back() } }
        } else null
        SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
            val header = subcompose("header") {
                PageHeader(
                    page = pageId,
                    searchOpen = showSearch && currentPage,
                    searchExpansion = searchExpansion.takeIf { currentPage },
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = { if (currentPage) onToggleSearch() },
                    onBack = back,
                    backdrop = headerBackdrop,
                )
            }.single().measure(constraints.copy(minHeight = 0))
            val headerClearance = header.height.toDp()
            val contents = subcompose("contents") {
                Box(
                    Modifier.fillMaxSize()
                        .captureGlassBackdrop(headerBackdrop)
                        .background(appColors.background),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    if (filtering && pageId == SettingsPage.HOME) {
                        HomeSearchResults(query, headerClearance + Be.Space.m, state::openPage, onCloseSearch)
                    } else {
                        CompositionLocalProvider(LocalPageFilter provides if (filtering) query.trim() else "") {
                            SettingsPageContent(
                                pageId, state, gameControl, onPreviewOverlay, onInstallBem, headerClearance,
                                if (filtering) Be.Space.none else dockClearance,
                            )
                        }
                    }
                }
            }.single().measure(constraints)
            layout(contents.width, contents.height) {
                contents.placeRelative(0, 0)
                header.placeRelative(0, 0)
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
    val topPadding = Be.Space.m + headerClearance
    val bottomPadding = Be.Space.footer + dockClearance
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        if (page == SettingsPage.HOME || page == SettingsPage.EXPERIENCE) {
            val status = if (LocalPageFilter.current.isBlank()) state.status else ""
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
            when (page) {
                SettingsPage.CHARACTERS -> CharacterPage(state, onInstallBem)
                SettingsPage.TOOLS -> ToolsPage(state, onPreviewOverlay)
                SettingsPage.FIRST_PERSON -> FirstPersonPage(state)
                SettingsPage.CAMERA_MOTION -> CameraMotionPage(state)
                SettingsPage.MMD -> MmdPage(state)
                SettingsPage.APPEARANCE -> AppearancePage(state, onInstallBem)
                SettingsPage.ADMINISTRATOR_APPEARANCE -> AdministratorAppearancePage(state::markAdministratorAppearanceSaved)
                SettingsPage.LOG -> LogPage(state)
                SettingsPage.SETTINGS -> AppSettingsPage(
                    onThemeSettings = { state.openPage(SettingsPage.THEME_SETTINGS) },
                    onAbout = { state.openPage(SettingsPage.ABOUT) },
                )
                SettingsPage.THEME_SETTINGS -> AppThemeSettingsPage()
                SettingsPage.ABOUT -> AboutPage()
                SettingsPage.WORKSHOP -> WorkshopPage()
            }
            Spacer(Modifier.height(Be.Space.xxl))
            if (LocalPageFilter.current.isBlank() && state.status.isNotBlank()) Notice(state.status)
        }
    }
}

/** Which way a page change travels: 1 enters from the right, -1 from the left. */
private fun pageTravel(from: Int, to: Int): Int {
    val fromTab = SettingsPage.tabOf(from)
    val toTab = SettingsPage.tabOf(to)
    return when {
        SettingsPage.isSubPage(to) && from == SettingsPage.parentOf(to) -> 1
        SettingsPage.isSubPage(from) && to == SettingsPage.parentOf(from) -> -1
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
private fun PageHeader(
    page: Int,
    searchOpen: Boolean,
    searchExpansion: State<Float>?,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: (() -> Unit)?,
    backdrop: GlassBackdrop? = null,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .widthIn(max = Be.Size.contentMaxWidth)
                .fillMaxWidth()
                .padding(
                    start = Be.Space.gutter,
                    end = Be.Space.gutter,
                    top = DockBottomGap,
                    bottom = DockTopGap,
                ),
        ) {
            AppHeader(page, searchOpen, searchExpansion, query, onQueryChange, onSearch, onBack, backdrop)
        }
    }
}

@Composable
private fun AppHeader(
    page: Int,
    searchOpen: Boolean,
    searchExpansion: State<Float>?,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: (() -> Unit)?,
    backdrop: GlassBackdrop?,
) {
    val radius = Be.Radius.card
    val panel = appColors.panel
    val material = if (backdrop == null) Modifier.background(panel) else {
        Modifier.glassSurface(
            backdrop, panel, LocalAppAppearance.current?.lightBars == true, cornerRadius = radius,
        )
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .then(material)
            .padding(MiuixLayout.inset),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).graphicsLayer {
                alpha = 1f - (searchExpansion?.value ?: 0f)
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (page == SettingsPage.HOME) {
                Box(
                    Modifier
                        .size(Be.Size.avatar)
                        .clip(RoundedCornerShape(Be.Radius.badge))
                ) {
                    Image(
                        painter = painterResource(R.drawable.be_wordmark),
                        contentDescription = stringResource(R.string.app_icon_description),
                        colorFilter = ColorFilter.tint(miuixControlColors.activeTrack),
                        modifier = Modifier.fillMaxSize(),
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
            } else {
                if (onBack != null) {
                    BackButton(onBack)
                    Spacer(Modifier.width(Be.Space.m))
                }
                Text(
                    text = stringResource(SettingsPage.titleOf(page)),
                    color = appColors.textPrimary,
                    fontSize = Be.Type.title,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(40.dp + Be.Space.m))
        }
        val expanded by remember(searchExpansion) {
            derivedStateOf { (searchExpansion?.value ?: 0f) > 0f }
        }
        val focusReady by remember(searchExpansion) {
            derivedStateOf { (searchExpansion?.value ?: 0f) >= 0.85f }
        }
        if (searchOpen || expanded) {
            val collapsedWidth = minOf(40.dp, maxWidth)
            val fullWidth = maxWidth
            val shape = RoundedCornerShape(Be.Radius.input)
            SearchField(
                value = query,
                onValueChange = onQueryChange,
                hint = stringResource(R.string.search_features),
                modifier = Modifier.clip(shape).layout { measurable, constraints ->
                    val fieldWidth = fullWidth.roundToPx()
                    val compactWidth = collapsedWidth.roundToPx()
                    val placeable = measurable.measure(constraints.copy(minWidth = fieldWidth, maxWidth = fieldWidth))
                    val expansion = searchExpansion?.value ?: 0f
                    val visibleWidth = compactWidth + ((fieldWidth - compactWidth) * expansion).roundToInt()
                    layout(visibleWidth, placeable.height) { placeable.placeRelative(0, 0) }
                },
                requestFocus = searchOpen && focusReady,
                onClose = onSearch,
                contentAlpha = { searchExpansion?.value ?: 0f },
            )
        } else {
            SearchIconButton(
                open = false,
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
