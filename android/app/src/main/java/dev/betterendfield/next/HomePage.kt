package dev.betterendfield.next

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
@Composable
fun HomePage(state: SettingsState) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Be.Space.l)) {
        GameManagementCard()
        ModuleStatusCard(state)
        PendingCard(state)

        PanelCard {
            CardTitle(stringResource(R.string.home_appearance_title))
            BodyText(
                text = state.appearanceSummary,
                modifier = Modifier.padding(top = Be.Space.m),
            )
            GhostButton(
                text = stringResource(R.string.page_appearance),
                onClick = { state.openPage(SettingsPage.APPEARANCE) },
                modifier = Modifier.fillMaxWidth().padding(top = Be.Space.l),
            )
        }

        PanelCard {
            CardTitle(stringResource(R.string.home_login_display_title))
            BodyText(
                text = state.loginDisplaySummary,
                modifier = Modifier.padding(top = Be.Space.m),
            )
            GhostButton(
                text = stringResource(R.string.page_characters),
                onClick = { state.openPage(SettingsPage.CHARACTERS) },
                modifier = Modifier.fillMaxWidth().padding(top = Be.Space.l),
            )
        }

        PanelCard {
            CardTitle(stringResource(R.string.home_shortcuts_title))
            ShortcutRow(
                title = stringResource(R.string.home_shortcut_overlay),
                onClick = { state.openPage(SettingsPage.TOOLS) },
            )
            ShortcutRow(
                title = stringResource(R.string.home_shortcut_first_person),
                onClick = { state.openPage(SettingsPage.FIRST_PERSON) },
            )
            ShortcutRow(
                title = stringResource(R.string.home_shortcut_appearance),
                onClick = { state.openPage(SettingsPage.APPEARANCE) },
            )
        }
    }
}

/**
 * Which modules the next launch will start.
 *
 * Listed one per line with an accent dot rather than as a sentence, because the
 * list is the thing being read; a module that is absent is the normal state for
 * a feature nobody turned on, so the empty case explains the rule instead of
 * showing an error.
 */
@Composable
private fun ModuleStatusCard(state: SettingsState) {
    val modules = state.loadedModuleIds()
    SectionCard(
        eyebrow = stringResource(R.string.home_eyebrow),
        title = stringResource(R.string.home_modules_title),
        subtitle = if (modules.isEmpty()) stringResource(R.string.home_modules_none) else null,
    ) {
        if (modules.isNotEmpty()) {
            Column(
                Modifier.padding(top = Be.Space.l),
                verticalArrangement = Arrangement.spacedBy(Be.Space.m),
            ) {
                modules.forEach { module -> ModuleLine(module) }
            }
        }
    }
}

@Composable
private fun ModuleLine(id: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Be.Colors.accent))
        Spacer(Modifier.width(Be.Space.m))
        Text(
            text = id,
            color = Be.Colors.textPrimary,
            fontSize = Be.Type.value,
        )
    }
}

@Composable
private fun GameManagementCard() {
    val activity = LocalContext.current as? android.app.Activity ?: return
    val controller = remember(activity) { GameControlController(activity) }
    val game = controller.state
    DisposableEffect(controller) {
        controller.start()
        onDispose { controller.close() }
    }

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

/**
 * One shortcut. The whole row is the target rather than a trailing button, which
 * is what makes the block scannable; the chevron carries the affordance that a
 * borderless row would otherwise lack.
 */
@Composable
private fun ShortcutRow(
    title: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Be.Space.s)
            .clip(RoundedCornerShape(Be.Radius.row))
            .background(Be.Colors.row)
            .clickable(onClick = onClick)
            .padding(horizontal = Be.Space.xl, vertical = Be.Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Be.Colors.textPrimary,
            fontSize = Be.Type.rowTitle,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "›",
            color = Be.Colors.accent,
            fontSize = 20.sp,
        )
    }
}
