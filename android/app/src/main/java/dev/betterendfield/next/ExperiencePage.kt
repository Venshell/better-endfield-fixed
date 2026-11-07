package dev.betterendfield.next

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The experience tab: interface, camera and actions.
 *
 * The camera card is the one place the tree does not map one-to-one onto the
 * preference store. "Default FOV" and the free camera's FOV are the same stored
 * key - the desktop module reads it as the free camera's baseline and as the
 * target its "reset view" hotkey returns to - so it is offered once, under the
 * general camera group, and the free camera group points at it. Two sliders
 * bound to one value would drift apart as soon as one was dragged.
 */
@Composable
fun ExperiencePage(state: SettingsState) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Be.Space.l)) {
        InterfaceCard(state)
        CameraCard(state)
        DashCard(state)
    }
}

@Composable
private fun InterfaceCard(state: SettingsState) {
    SectionCard(
        eyebrow = stringResource(R.string.ui_card_eyebrow),
        title = stringResource(R.string.ui_card_title),
        status = state.interfaceCardStatus,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
            SwitchRow(
                title = stringResource(R.string.ui_hide_uid),
                description = stringResource(R.string.ui_hide_uid_hint),
                checked = state.hideUid,
                onCheckedChange = state::updateHideUid,
            )
            SwitchRow(
                title = stringResource(R.string.ui_hide_hud),
                description = stringResource(R.string.ui_hide_hud_hint),
                checked = state.hideHud,
                onCheckedChange = state::updateHideHud,
                badge = stringResource(R.string.badge_overlay),
            )
            SwitchRow(
                title = stringResource(R.string.ui_pc),
                description = stringResource(R.string.ui_pc_hint),
                checked = state.pcUi,
                onCheckedChange = state::updatePcUi,
            )
        }
    }
}

@Composable
private fun CameraCard(state: SettingsState) {
    SectionCard(
        eyebrow = stringResource(R.string.camera_card_eyebrow),
        title = stringResource(R.string.camera_card_title),
        status = state.cameraCardStatus,
    ) {
        GroupLabel(stringResource(R.string.camera_group_general), top = Be.Space.l)
        Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
            SwitchRow(
                title = stringResource(R.string.camera_dither),
                description = stringResource(R.string.camera_dither_hint),
                checked = state.disableDither,
                onCheckedChange = state::updateDisableDither,
            )
            SliderRow(
                label = stringResource(R.string.camera_default_fov),
                value = state.cameraFov,
                onValueChange = state::updateCameraFov,
                valueRange = ModuleSettings.FOV_MINIMUM..ModuleSettings.FOV_MAXIMUM,
                unit = stringResource(R.string.degree_suffix),
            )
            BodyText(stringResource(R.string.camera_default_fov_hint))
            SwitchRow(
                title = stringResource(R.string.camera_global_fov),
                description = stringResource(R.string.camera_global_fov_hint),
                checked = state.globalFovEnabled,
                onCheckedChange = state::updateGlobalFovEnabled,
            )
            SliderRow(
                label = stringResource(R.string.camera_global_fov_value),
                value = state.globalFov,
                onValueChange = state::updateGlobalFov,
                valueRange = 5f..150f,
                unit = stringResource(R.string.degree_suffix),
                enabled = state.globalFovEnabled,
            )
        }

        GroupLabel(stringResource(R.string.camera_group_free))
        Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
            SwitchRow(
                title = stringResource(R.string.camera_free),
                description = stringResource(R.string.camera_free_hint),
                checked = state.freeCamera,
                onCheckedChange = state::updateFreeCamera,
                badge = stringResource(R.string.badge_overlay),
            )
            SwitchRow(
                title = stringResource(R.string.camera_follow_character),
                description = stringResource(R.string.camera_follow_character_hint),
                checked = state.freeCameraFollowCharacter,
                onCheckedChange = state::updateFreeCameraFollowCharacter,
                enabled = state.freeCamera,
            )
            SliderRow(
                label = stringResource(R.string.camera_speed_label),
                value = state.cameraSpeed,
                onValueChange = state::updateCameraSpeed,
                valueRange = ModuleSettings.SPEED_MINIMUM..ModuleSettings.SPEED_MAXIMUM,
                enabled = state.cameraSpeedAvailable,
            )
            SwitchRow(
                title = stringResource(R.string.camera_pause),
                description = stringResource(R.string.camera_pause_hint),
                checked = state.worldPause,
                onCheckedChange = state::updateWorldPause,
                badge = stringResource(R.string.badge_overlay),
                enabled = state.worldPauseAvailable,
            )
        }

        // No group label of its own: the row's own title already names the
        // sub-page, and a heading repeating it would just double the text.
        SubPageRow(
            title = stringResource(R.string.page_camera_motion),
            hint = stringResource(R.string.camera_motion_hint_short),
            // There is no switch of its own: the desktop module only reads the
            // preset, keyframe and VMD hotkeys while the free camera is armed,
            // so the free camera is what decides whether this block is live.
            state = state.freeCamera,
            onClick = { state.openPage(SettingsPage.CAMERA_MOTION) },
        )

        GroupLabel(stringResource(R.string.camera_group_first_person))
        SubPageRow(
            title = stringResource(R.string.camera_first_person),
            hint = stringResource(R.string.camera_first_person_hint_short),
            state = state.firstPerson,
            onClick = { state.openPage(SettingsPage.FIRST_PERSON) },
        )

        // MMD is not a camera mode - it drives the character's own motion - but
        // it lives in the same module and is pumped from the same engine tick, so
        // it is reached from this card rather than from a tab of its own.
        GroupLabel("MMD")
        SubPageRow(
            title = "MMD 舞蹈与运镜",
            hint = state.mmdSourceSummary,
            state = state.mmdEnabled,
            onClick = { state.openPage(SettingsPage.MMD) },
        )
    }
}

@Composable
private fun DashCard(state: SettingsState) {
    SectionCard(
        eyebrow = stringResource(R.string.dash_card_eyebrow),
        title = stringResource(R.string.dash_card_title),
        status = state.dashCardStatus,
    ) {
        SwitchRow(
            title = stringResource(R.string.dash_enable),
            description = stringResource(R.string.dash_enable_hint),
            checked = state.sustainedDash,
            onCheckedChange = state::updateSustainedDash,
        )

        GroupLabel(stringResource(R.string.dash_group_characters))
        Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
            SwitchRow(
                title = stringResource(R.string.dash_aglina),
                description = stringResource(R.string.dash_aglina_hint),
                checked = state.dashAglina,
                onCheckedChange = state::updateDashAglina,
                enabled = state.dashCharactersAvailable,
            )
            SwitchRow(
                title = stringResource(R.string.dash_liino),
                description = stringResource(R.string.dash_liino_hint),
                checked = state.dashLiino,
                onCheckedChange = state::updateDashLiino,
                enabled = state.dashCharactersAvailable,
            )
            SwitchRow(
                title = stringResource(R.string.dash_jsspsi),
                description = "",
                checked = state.dashJsspsi,
                onCheckedChange = state::updateDashJsspsi,
                enabled = state.dashCharactersAvailable,
            )
            SwitchRow(
                title = stringResource(R.string.dash_jsspsi_quality),
                description = stringResource(R.string.dash_jsspsi_quality_hint),
                checked = state.jsspsiQuality,
                onCheckedChange = state::updateJsspsiQuality,
                enabled = state.sustainedDash && state.dashJsspsi,
            )
        }

        GroupLabel(stringResource(R.string.dash_group_options))
        SwitchRow(
            title = stringResource(R.string.dash_liino_clean),
            description = stringResource(R.string.dash_liino_clean_hint),
            checked = state.liinoCleanDash,
            onCheckedChange = state::updateLiinoCleanDash,
            enabled = state.liinoCleanDashAvailable,
        )
    }
}

/**
 * A card that leads to a sub-page. It carries the on/off state of the feature it
 * opens, because a row that only says "advanced" makes the user tap in to find
 * out whether the thing is even on.
 */
@Composable
fun SubPageRow(
    title: String,
    hint: String,
    state: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.row))
            .background(Be.Colors.row)
            .clickable(onClick = onClick)
            .padding(horizontal = Be.Space.xl, vertical = Be.Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Be.Colors.textPrimary,
                fontSize = Be.Type.rowTitle,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = hint,
                color = Be.Colors.textSecondary,
                fontSize = Be.Type.bodySmall,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Text(
            text = stringResource(if (state) R.string.state_on else R.string.state_off),
            color = if (state) Be.Colors.accent else Be.Colors.textMuted,
            fontSize = Be.Type.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "›",
            color = Be.Colors.accent,
            fontSize = 20.sp,
            modifier = Modifier.padding(start = Be.Space.m),
        )
    }
}
