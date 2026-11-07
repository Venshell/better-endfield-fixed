package dev.betterendfield.next

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * The motion sub-page: camera presets, keyframes and VMD parameters.
 *
 * Split from the camera card because these are eight numeric targets plus two
 * switches, which buries the three on/off decisions the card is really about.
 * Everything here is a parameter of a feature whose buttons live in the in-game
 * panel, so the page is also the inventory of what the panel can drive once the
 * free camera is armed.
 *
 * The VMD group also owns the import: the .vmd is a user file, and the settings
 * app is where a file picker belongs - the in-game panel would have to reach the
 * framework's activity-result relay, which has a degraded path, and the import
 * could then only be tested with the game running. Here it is testable on a phone
 * with no game at all.
 *
 * The parameter rows are dimmed rather than hidden when the free camera is off:
 * the desktop module only polls the preset, keyframe and VMD hotkeys while the
 * free camera is armed, so with the camera off these values would be written but
 * never read - and a row that silently does nothing reads as broken.
 */
@Composable
fun CameraMotionPage(state: SettingsState) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) state.importVmd(uri)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Be.Space.l)) {
        SectionCard(
            eyebrow = stringResource(R.string.camera_card_eyebrow),
            title = stringResource(R.string.page_camera_motion),
            subtitle = stringResource(R.string.camera_motion_subtitle),
            status = state.cameraCardStatus,
        ) {
            // First, because it is the one value here a user will want to change
            // while holding the phone: the pad it scales is the control they use
            // on every shot, while everything below it is a parameter of a
            // one-off take.
            GroupLabel(stringResource(R.string.motion_group_look), top = Be.Space.l)
            Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
                SliderRow(
                    label = stringResource(R.string.motion_look_sensitivity_label),
                    value = state.mouseSensitivity,
                    onValueChange = state::updateMouseSensitivity,
                    valueRange = 0.02f..0.5f,
                    unit = stringResource(R.string.motion_look_sensitivity_unit),
                    // 48 stops across 0.02-0.5: one stop is one hundredth of a
                    // degree per pixel, which is the increment the record's own
                    // bounds and the stored text are written in.
                    steps = 48,
                    decimals = 2,
                    enabled = state.cameraMotionAvailable,
                )
                BodyText(stringResource(R.string.motion_look_hint))
                SwitchRow(
                    title = stringResource(R.string.motion_look_invert),
                    description = stringResource(R.string.motion_look_invert_hint),
                    checked = state.mouseInvertY,
                    onCheckedChange = state::updateMouseInvertY,
                    enabled = state.cameraMotionAvailable,
                )
            }

            GroupLabel(stringResource(R.string.motion_group_preset))
            Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
                FieldLabel(stringResource(R.string.motion_preset_label), top = Be.Space.none)
                SelectField(
                    options = listOf(
                        stringResource(R.string.motion_preset_orbit),
                        stringResource(R.string.motion_preset_dolly_zoom),
                        stringResource(R.string.motion_preset_crane),
                        stringResource(R.string.motion_preset_truck),
                    ),
                    selectedIndex = state.motionPreset,
                    onSelect = state::updateMotionPreset,
                    enabled = state.cameraMotionAvailable,
                )
                BodyText(stringResource(R.string.motion_preset_hint))
                SliderRow(
                    label = stringResource(R.string.motion_speed_label),
                    value = state.motionSpeed,
                    onValueChange = state::updateMotionSpeed,
                    valueRange = -20f..20f,
                    unit = stringResource(R.string.motion_speed_unit),
                    steps = 400,
                    decimals = 1,
                    enabled = state.cameraMotionAvailable,
                )
                SliderRow(
                    label = stringResource(R.string.motion_orbit_speed_label),
                    value = state.orbitSpeed,
                    onValueChange = state::updateOrbitSpeed,
                    valueRange = -180f..180f,
                    unit = stringResource(R.string.motion_orbit_speed_unit),
                    // One degree per stop, so the integer readout is the stored
                    // value rather than a rounded version of it.
                    steps = 360,
                    decimals = 0,
                    enabled = state.cameraMotionAvailable,
                )
                SliderRow(
                    label = stringResource(R.string.motion_duration_label),
                    value = state.motionDuration,
                    onValueChange = state::updateMotionDuration,
                    valueRange = 0f..600f,
                    unit = stringResource(R.string.camera_fp_seconds),
                    steps = 600,
                    decimals = 0,
                    enabled = state.cameraMotionAvailable,
                )
                SliderRow(
                    label = stringResource(R.string.motion_target_height_label),
                    value = state.motionTargetHeight,
                    onValueChange = state::updateMotionTargetHeight,
                    valueRange = -5f..5f,
                    steps = 100,
                    decimals = 1,
                    enabled = state.cameraMotionAvailable,
                )
            }

            GroupLabel(stringResource(R.string.motion_group_keyframe))
            Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
                SliderRow(
                    label = stringResource(R.string.motion_keyframe_segment_label),
                    value = state.keyframeSegmentSeconds,
                    onValueChange = state::updateKeyframeSegmentSeconds,
                    valueRange = 0.2f..60f,
                    unit = stringResource(R.string.camera_fp_seconds),
                    steps = 598,
                    decimals = 1,
                    enabled = state.cameraMotionAvailable,
                )
                SwitchRow(
                    title = stringResource(R.string.motion_keyframe_loop),
                    description = stringResource(R.string.motion_keyframe_loop_hint),
                    checked = state.keyframeLoop,
                    onCheckedChange = state::updateKeyframeLoop,
                    enabled = state.cameraMotionAvailable,
                )
                BodyText(stringResource(R.string.motion_keyframe_note))
            }

            GroupLabel(stringResource(R.string.motion_group_vmd))
            Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
                BodyText(state.vmdSummary)
                // The import row stays live with the free camera off, unlike every
                // slider around it: this is a file operation, and choosing the
                // motion before the game starts is the normal order. The sliders
                // control values the native module only reads while armed, so they
                // are dimmed for a different reason.
                PrimaryButton(
                    text = stringResource(R.string.motion_vmd_import),
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = !state.vmdImporting,
                )
                if (state.vmdImported) {
                    GhostButton(
                        text = stringResource(R.string.motion_vmd_clear),
                        onClick = state::clearVmd,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.vmdImporting,
                    )
                }
                if (state.vmdImportStatus.isNotEmpty()) {
                    BodyText(state.vmdImportStatus)
                }
                SliderRow(
                    label = stringResource(R.string.motion_vmd_scale_label),
                    value = state.vmdScale,
                    onValueChange = state::updateVmdScale,
                    valueRange = 0.001f..10f,
                    steps = 1000,
                    decimals = 3,
                    enabled = state.cameraMotionAvailable,
                )
                SliderRow(
                    label = stringResource(R.string.motion_vmd_fov_bias_label),
                    value = state.vmdFovBias,
                    onValueChange = state::updateVmdFovBias,
                    valueRange = -60f..60f,
                    unit = stringResource(R.string.degree_suffix),
                    steps = 600,
                    decimals = 1,
                    enabled = state.cameraMotionAvailable,
                )
                SwitchRow(
                    title = stringResource(R.string.motion_vmd_loop),
                    description = stringResource(R.string.motion_vmd_loop_hint),
                    checked = state.vmdLoop,
                    onCheckedChange = state::updateVmdLoop,
                    enabled = state.cameraMotionAvailable,
                )
                BodyText(stringResource(R.string.motion_vmd_note))
            }
        }
    }
}
