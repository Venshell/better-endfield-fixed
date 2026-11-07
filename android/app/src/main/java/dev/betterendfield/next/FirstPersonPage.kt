package dev.betterendfield.next

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * The first-person sub-page.
 *
 * Split from the camera card because it is the only block long enough to need its
 * own screen, and because its switches gate each other in ways that read better
 * in one column: the neck plug only means anything once the head is hidden, and
 * the side-look threshold only once the body follows the look direction. Rows
 * that cannot act are dimmed rather than hidden - this screen doubles as the
 * inventory of what the camera module can do, and a vanished row is
 * indistinguishable from one that an update removed.
 */
@Composable
fun FirstPersonPage(state: SettingsState) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Be.Space.l)) {
        SectionCard(
            eyebrow = stringResource(R.string.camera_card_eyebrow),
            title = stringResource(R.string.page_first_person),
            status = state.cameraCardStatus,
        ) {
            GroupLabel(stringResource(R.string.fp_group_basic), top = Be.Space.l)
            Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
                SwitchRow(
                    title = stringResource(R.string.fp_enable),
                    description = "",
                    checked = state.firstPerson,
                    onCheckedChange = state::updateFirstPerson,
                    badge = stringResource(R.string.badge_overlay),
                )
                SliderRow(
                    label = stringResource(R.string.camera_fp_fov_label),
                    value = state.firstPersonFov,
                    onValueChange = state::updateFirstPersonFov,
                    valueRange = ModuleSettings.FOV_MINIMUM..ModuleSettings.FOV_MAXIMUM,
                    unit = stringResource(R.string.degree_suffix),
                    enabled = state.firstPersonFovAvailable,
                )
                SliderRow(
                    label = stringResource(R.string.camera_fp_eye_forward),
                    value = state.firstPersonEyeForward,
                    onValueChange = state::updateFirstPersonEyeForward,
                    valueRange = ModuleSettings.FP_EYE_FORWARD_MINIMUM..ModuleSettings.FP_EYE_FORWARD_MAXIMUM,
                    steps = 500,
                    decimals = 4,
                    enabled = state.firstPersonEyeForwardAvailable,
                )
                SliderRow(
                    label = stringResource(R.string.camera_fp_eye_height),
                    value = state.firstPersonEyeHeight,
                    onValueChange = state::updateFirstPersonEyeHeight,
                    valueRange = ModuleSettings.FP_EYE_HEIGHT_MINIMUM..ModuleSettings.FP_EYE_HEIGHT_MAXIMUM,
                    steps = 1000,
                    decimals = 4,
                    enabled = state.firstPersonEyeHeightAvailable,
                )
                SliderRow(
                    label = stringResource(R.string.camera_fp_near_clip),
                    value = state.firstPersonNearClip,
                    onValueChange = state::updateFirstPersonNearClip,
                    valueRange = ModuleSettings.FP_NEAR_CLIP_MINIMUM..ModuleSettings.FP_NEAR_CLIP_MAXIMUM,
                    steps = 999,
                    decimals = 4,
                    enabled = state.firstPersonNearClipAvailable,
                )
            }

            GroupLabel(stringResource(R.string.fp_group_display))
            Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
                SwitchRow(
                    title = stringResource(R.string.camera_hide_head),
                    description = stringResource(R.string.camera_hide_head_hint),
                    checked = state.firstPersonHideHead,
                    onCheckedChange = state::updateFirstPersonHideHead,
                    enabled = state.hideHeadAvailable,
                )
                SwitchRow(
                    title = stringResource(R.string.camera_fill_neck),
                    description = stringResource(R.string.camera_fill_neck_hint),
                    checked = state.firstPersonFillNeck,
                    onCheckedChange = state::updateFirstPersonFillNeck,
                    enabled = state.fillNeckAvailable,
                )
                SwitchRow(
                    title = stringResource(R.string.camera_fp_external_head_scale),
                    description = stringResource(R.string.camera_fp_external_head_scale_hint),
                    checked = state.firstPersonExternalHeadScale,
                    onCheckedChange = state::updateFirstPersonExternalHeadScale,
                    enabled = state.firstPersonExternalHeadScaleAvailable,
                )
            }

            GroupLabel(stringResource(R.string.fp_group_control))
            Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
                SwitchRow(
                    title = stringResource(R.string.camera_fp_extend_look_range),
                    description = stringResource(R.string.camera_fp_extend_look_range_hint),
                    checked = state.firstPersonExtendLookRange,
                    onCheckedChange = state::updateFirstPersonExtendLookRange,
                    enabled = state.firstPersonExtendLookRangeAvailable,
                )
                SliderRow(
                    label = stringResource(R.string.camera_fp_look_up_limit),
                    value = state.firstPersonLookUpLimit,
                    onValueChange = state::updateFirstPersonLookUpLimit,
                    valueRange = 0f..89f,
                    unit = stringResource(R.string.degree_suffix),
                    enabled = state.firstPersonLookLimitAvailable,
                )
                SliderRow(
                    label = stringResource(R.string.camera_fp_look_down_limit),
                    value = state.firstPersonLookDownLimit,
                    onValueChange = state::updateFirstPersonLookDownLimit,
                    valueRange = 0f..89f,
                    unit = stringResource(R.string.degree_suffix),
                    enabled = state.firstPersonLookLimitAvailable,
                )
                SwitchRow(
                    title = stringResource(R.string.camera_fp_movement),
                    description = stringResource(R.string.camera_fp_movement_hint),
                    checked = state.firstPersonMovement,
                    onCheckedChange = state::updateFirstPersonMovement,
                    enabled = state.firstPersonMovementAvailable,
                )
                SliderRow(
                    label = stringResource(R.string.camera_fp_side_look_limit),
                    value = state.firstPersonSideLookLimit,
                    onValueChange = state::updateFirstPersonSideLookLimit,
                    valueRange = 0f..90f,
                    unit = stringResource(R.string.degree_suffix),
                    steps = 900,
                    decimals = 4,
                    enabled = state.firstPersonSideLookLimitAvailable,
                )
            }

            GroupLabel(stringResource(R.string.fp_group_animation))
            Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
                SelectField(
                    options = listOf(
                        stringResource(R.string.camera_fp_animation_off),
                        stringResource(R.string.camera_fp_animation_body),
                        stringResource(R.string.camera_fp_animation_head),
                        stringResource(R.string.camera_fp_animation_realistic),
                    ),
                    selectedIndex = state.firstPersonAnimationMode,
                    onSelect = state::updateFirstPersonAnimationMode,
                    enabled = state.firstPersonAnimationModeAvailable,
                )
                SliderRow(
                    label = stringResource(R.string.camera_fp_animation_strength),
                    value = state.firstPersonAnimationStrength,
                    onValueChange = state::updateFirstPersonAnimationStrength,
                    valueRange = 0f..1f,
                    steps = 1000,
                    decimals = 4,
                    enabled = state.firstPersonAnimationStrengthAvailable,
                )
            }

            GroupLabel(stringResource(R.string.fp_group_scene))
            Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
                SwitchRow(
                    title = stringResource(R.string.camera_fp_yield_dialogue),
                    description = stringResource(R.string.camera_fp_yield_dialogue_hint),
                    checked = state.firstPersonYieldDialogue,
                    onCheckedChange = state::updateFirstPersonYieldDialogue,
                    enabled = state.firstPersonYieldDialogueAvailable,
                )
                SwitchRow(
                    title = stringResource(R.string.camera_fp_third_person_in_combat),
                    description = stringResource(R.string.camera_fp_third_person_in_combat_hint),
                    checked = state.firstPersonThirdPersonInCombat,
                    onCheckedChange = state::updateFirstPersonThirdPersonInCombat,
                    enabled = state.firstPersonThirdPersonInCombatAvailable,
                )
                SliderRow(
                    label = stringResource(R.string.camera_fp_transition_seconds),
                    value = state.firstPersonTransitionSeconds,
                    onValueChange = state::updateFirstPersonTransitionSeconds,
                    valueRange = 0f..1f,
                    unit = stringResource(R.string.camera_fp_seconds),
                    steps = 1000,
                    decimals = 4,
                    enabled = state.firstPersonTransitionSecondsAvailable,
                )
            }
            GroupLabel(stringResource(R.string.fp_group_gyroscope))
            Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
                SwitchRow(
                    title = stringResource(R.string.camera_fp_gyroscope),
                    description = stringResource(R.string.camera_fp_gyroscope_hint),
                    checked = state.gyroscopeEnabled,
                    onCheckedChange = state::updateGyroscopeEnabled,
                    enabled = state.gyroscopeAvailable,
                )
                SliderRow(
                    label = stringResource(R.string.camera_fp_gyroscope_horizontal),
                    value = state.gyroscopeHorizontalSensitivity,
                    onValueChange = state::updateGyroscopeHorizontalSensitivity,
                    valueRange = ModuleSettings.FirstPersonGyro.SENSITIVITY_MINIMUM.toFloat()..
                        ModuleSettings.FirstPersonGyro.SENSITIVITY_MAXIMUM.toFloat(),
                    steps = 480,
                    decimals = 2,
                    enabled = state.gyroscopeTuningAvailable,
                )
                SliderRow(
                    label = stringResource(R.string.camera_fp_gyroscope_vertical),
                    value = state.gyroscopeVerticalSensitivity,
                    onValueChange = state::updateGyroscopeVerticalSensitivity,
                    valueRange = ModuleSettings.FirstPersonGyro.SENSITIVITY_MINIMUM.toFloat()..
                        ModuleSettings.FirstPersonGyro.SENSITIVITY_MAXIMUM.toFloat(),
                    steps = 480,
                    decimals = 2,
                    enabled = state.gyroscopeTuningAvailable,
                )
                SwitchRow(
                    title = stringResource(R.string.camera_fp_gyroscope_invert_horizontal),
                    description = stringResource(R.string.camera_fp_gyroscope_invert_horizontal_hint),
                    checked = state.gyroscopeInvertHorizontal,
                    onCheckedChange = state::updateGyroscopeInvertHorizontal,
                    enabled = state.gyroscopeTuningAvailable,
                )
                SwitchRow(
                    title = stringResource(R.string.camera_fp_gyroscope_invert_vertical),
                    description = stringResource(R.string.camera_fp_gyroscope_invert_vertical_hint),
                    checked = state.gyroscopeInvertVertical,
                    onCheckedChange = state::updateGyroscopeInvertVertical,
                    enabled = state.gyroscopeTuningAvailable,
                )
                SliderRow(
                    label = stringResource(R.string.camera_fp_gyroscope_smoothing),
                    value = state.gyroscopeSmoothing,
                    onValueChange = state::updateGyroscopeSmoothing,
                    valueRange = ModuleSettings.FirstPersonGyro.SMOOTHING_MINIMUM.toFloat()..
                        ModuleSettings.FirstPersonGyro.SMOOTHING_MAXIMUM.toFloat(),
                    steps = 900,
                    decimals = 3,
                    enabled = state.gyroscopeTuningAvailable,
                )
                SliderRow(
                    label = stringResource(R.string.camera_fp_gyroscope_deadzone),
                    value = state.gyroscopeDeadzone,
                    onValueChange = state::updateGyroscopeDeadzone,
                    valueRange = ModuleSettings.FirstPersonGyro.DEADZONE_MINIMUM.toFloat()..
                        ModuleSettings.FirstPersonGyro.DEADZONE_MAXIMUM.toFloat(),
                    steps = 250,
                    decimals = 4,
                    enabled = state.gyroscopeTuningAvailable,
                )
            }
            GroupLabel(stringResource(R.string.fp_group_diagnostics))
            Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
                // A research switch, kept beside the feature it is researching so
                // it is not mistaken for one. It logs the game's look entry
                // points on the next launch and changes nothing else.
                SwitchRow(
                    title = stringResource(R.string.camera_fp_look_probe),
                    description = stringResource(R.string.camera_fp_look_probe_hint),
                    checked = state.firstPersonLookProbe,
                    onCheckedChange = state::updateFirstPersonLookProbe,
                )
            }
        }
    }
}
