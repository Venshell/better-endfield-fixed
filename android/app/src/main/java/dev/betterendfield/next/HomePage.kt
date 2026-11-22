package dev.betterendfield.next

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

/**
 * The home page: what the game will load, what is waiting for a restart, what
 * the two asset modules are currently pointed at, and the three places worth
 * reaching in one tap.
 *
 * Every line here is read back from the same stores the game reads, so the page
 * cannot claim a state the rest of the app disagrees with. The one thing it
 * deliberately does not claim is that the *running* game has picked a change up -
 * that is only knowable from the journal, so the wording stays at "will apply on
 * the next launch" rather than "applied".
 */
internal fun LazyListScope.homePage(state: SettingsState, gameControl: GameControlController) {
    item("game_management") { GameManagementCard(gameControl) }
    item("enabled_features") { EnabledFeaturesCard(state) }
    item("pending_changes") { PendingCard(state) }

    item("appearance_summary") {
        PanelCard {
            CardTitle(stringResource(R.string.home_appearance_title))
            BodyText(
                text = state.appearanceSummary,
                modifier = Modifier.padding(top = Be.Space.m),
            )
            SubPageRow(
                title = stringResource(R.string.page_appearance),
                onClick = { state.openPage(SettingsPage.APPEARANCE) },
                modifier = Modifier.fillMaxWidth().padding(top = Be.Space.l),
            )
        }
    }

    item("login_summary") {
        PanelCard {
            CardTitle(stringResource(R.string.home_login_display_title))
            BodyText(
                text = state.loginDisplaySummary,
                modifier = Modifier.padding(top = Be.Space.m),
            )
            SubPageRow(
                title = stringResource(R.string.page_characters),
                onClick = { state.openPage(SettingsPage.CHARACTERS) },
                modifier = Modifier.fillMaxWidth().padding(top = Be.Space.l),
            )
        }
    }

    item("shortcuts") {
        PanelCard {
            CardTitle(stringResource(R.string.home_shortcuts_title))
            SubPageRow(
                title = stringResource(R.string.home_shortcut_overlay),
                onClick = { state.openPage(SettingsPage.TOOLS) },
            )
            SubPageRow(
                title = stringResource(R.string.home_shortcut_first_person),
                onClick = { state.openPage(SettingsPage.FIRST_PERSON) },
            )
            SubPageRow(
                title = stringResource(R.string.home_shortcut_appearance),
                onClick = { state.openPage(SettingsPage.APPEARANCE) },
            )
        }
    }
}

@Composable
private fun EnabledFeaturesCard(state: SettingsState) {
    val context = LocalContext.current
    // The store also changes in separate Activities, which refreshHome observes on resume.
    val features = state.homeRevision.let { HomeFeatureSummary.read(context, state) }
    SectionCard(
        eyebrow = stringResource(R.string.home_eyebrow),
        title = stringResource(R.string.home_title),
    ) {
        BodyText(stringResource(R.string.home_feature_snapshot_hint))
        if (features.isEmpty()) {
            BodyText(stringResource(R.string.home_feature_empty), Modifier.padding(top = Be.Space.m))
        } else {
            BodyText(stringResource(R.string.home_count, features.size), Modifier.padding(top = Be.Space.m))
            features.forEach { feature ->
                SubPageRow(
                    title = stringResource(feature.title),
                    hint = feature.detail,
                    modifier = Modifier.padding(top = Be.Space.m),
                    onClick = {
                        when (feature.destination) {
                            HomeFeatureSummary.Destination.CHARACTERS -> state.openPage(SettingsPage.CHARACTERS)
                            HomeFeatureSummary.Destination.EXPERIENCE -> state.openPage(SettingsPage.EXPERIENCE)
                            HomeFeatureSummary.Destination.ADMINISTRATOR -> state.openPage(SettingsPage.ADMINISTRATOR_APPEARANCE)
                            HomeFeatureSummary.Destination.FIRST_PERSON -> state.openPage(SettingsPage.FIRST_PERSON)
                            HomeFeatureSummary.Destination.MMD -> state.openPage(SettingsPage.MMD)
                            HomeFeatureSummary.Destination.TOOLS -> state.openPage(SettingsPage.TOOLS)
                            HomeFeatureSummary.Destination.MEDIA -> context.startActivity(
                                android.content.Intent(context, MediaScreenActivity::class.java))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun GameManagementCard(controller: GameControlController) {
    val game = controller.state

    SectionCard(
        eyebrow = stringResource(R.string.game_management_eyebrow),
        title = stringResource(R.string.game_management_title),
    ) {
        if (game.targets.isEmpty()) {
            BodyText(stringResource(R.string.game_none_installed))
        } else {
            game.targets.forEach { target ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Be.Space.s),
                    horizontalArrangement = Arrangement.spacedBy(Be.Space.s),
                ) {
                    RadioButton(
                        selected = game.selectedPackage == target.packageName,
                        onClick = { controller.select(target.packageName) },
                    )
                    Column(Modifier.weight(1f).padding(top = Be.Space.s)) {
                        CardTitle(target.label)
                        BodyText(target.packageName)
                        val inScope = game.currentScope?.contains(target.packageName)
                        BodyText(stringResource(when (inScope) {
                            true -> R.string.game_scope_included
                            false -> R.string.game_scope_missing
                            null -> R.string.game_scope_status_unavailable
                        }))
                    }
                }
            }
            if (game.targets.size > 1 && game.selectedPackage == null) {
                BodyText(stringResource(R.string.game_multiple_choose),
                    modifier = Modifier.padding(top = Be.Space.s))
            }
            Row(Modifier.fillMaxWidth().padding(top = Be.Space.m),
                horizontalArrangement = Arrangement.spacedBy(Be.Space.s)) {
                GhostButton(
                    text = stringResource(R.string.game_launch),
                    onClick = controller::launchSelected,
                    modifier = Modifier.weight(1f),
                )
                GhostButton(
                    text = stringResource(R.string.game_force_stop),
                    onClick = controller::forceStopSelected,
                    modifier = Modifier.weight(1f),
                )
            }
            if (game.selectedPackage == null ||
                    game.currentScope?.contains(game.selectedPackage) != true) {
                GhostButton(
                    text = stringResource(R.string.game_scope_request),
                    onClick = controller::requestScope,
                    modifier = Modifier.fillMaxWidth().padding(top = Be.Space.s),
                )
            }
        }
        BodyText(stringResource(when {
            game.shizukuGranted -> R.string.game_shizuku_state_granted
            game.shizukuAvailable -> R.string.game_shizuku_state_denied
            else -> R.string.game_shizuku_state_unavailable
        }), modifier = Modifier.padding(top = Be.Space.m))
        if (!game.shizukuGranted) {
            GhostButton(
                text = stringResource(R.string.game_shizuku_request),
                onClick = controller::requestShizukuPermission,
                modifier = Modifier.fillMaxWidth().padding(top = Be.Space.s),
            )
        }
        if (game.status.isNotEmpty()) {
            BodyText(game.status, modifier = Modifier.padding(top = Be.Space.m))
        }
    }
}

/**
 * The restart card.
 *
 * It counts the writes made in this session instead of trying to compare against
 * what the game process consumed: the published snapshot carries a generation
 * number but nothing on this side can see which one the running process read, so
 * a comparison would be a guess dressed up as a fact.
 */
@Composable
private fun PendingCard(state: SettingsState) {
    PanelCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CardTitle(
                text = stringResource(R.string.home_pending_title),
                modifier = Modifier.weight(1f),
            )
            if (state.restartPending) {
                Badge(text = stringResource(R.string.home_state_label))
            }
        }
        BodyText(
            text = if (state.restartPending) {
                stringResource(R.string.home_pending_some, state.pendingChanges)
            } else {
                stringResource(R.string.home_pending_none)
            },
            modifier = Modifier.padding(top = Be.Space.m),
        )
    }
}
