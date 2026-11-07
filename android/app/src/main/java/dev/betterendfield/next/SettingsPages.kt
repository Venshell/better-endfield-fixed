package dev.betterendfield.next

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The characters tab: the login display, per-character voice, and the way in to
 * the BEM packages.
 *
 * The appearance manager is its own screen because a package is imported from a
 * file picker, converted on a worker, and edited option by option; folding that
 * into this column would put a progress bar next to a language picker.
 */

@Composable
fun CharacterPage(state: SettingsState, onInstallBem: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Be.Space.l)) {
        PanelCard {
            SubPageRow(
                title = stringResource(R.string.custom_model_title),
                hint = state.appearanceSummary,
                state = state.appearancePackages.any { it.enabled },
                onClick = { state.openPage(SettingsPage.APPEARANCE) },
            )
        }

        LoginDisplayPage(state)
        VoicePage(state)
    }
}

/**
 * The appearance sub-page. It reports what is installed and hands the actual
 * editing to the manager, rather than keeping a second copy of the option
 * pickers that has to be kept in step with the real ones.
 */
@Composable
fun AppearancePage(state: SettingsState, onInstallBem: () -> Unit) {
    val packages = state.appearancePackages
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Be.Space.l)) {
        SectionCard(
            eyebrow = "BEM",
            title = stringResource(R.string.custom_model_title),
            status = if (packages.isEmpty()) {
                stringResource(R.string.home_appearance_none)
            } else {
                stringResource(R.string.home_appearance_idle, packages.size)
            },
        ) {
            PrimaryButton(
                text = stringResource(R.string.custom_model_install),
                onClick = onInstallBem,
                modifier = Modifier.padding(top = Be.Space.s),
            )
        }

        if (packages.isNotEmpty()) {
            ListCaption(
                text = stringResource(R.string.home_appearance_title),
                modifier = Modifier.padding(start = Be.Space.hairline, top = Be.Space.xs),
            )
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
                packages.forEach { pkg -> PackageRow(pkg, onInstallBem) }
            }
        }
    }
}

/**
 * One installed package. The whole row opens the manager, because that is where
 * its own "detail" lives - the alternative is a read-only copy of every picker
 * here, which is the drift this screen exists to avoid.
 */
@Composable
private fun PackageRow(pkg: BemPackage, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.row))
            .background(Be.Colors.row)
            .clickable(onClick = onOpen)
            .padding(horizontal = Be.Space.xl, vertical = Be.Space.l),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pkg.name,
                color = Be.Colors.textPrimary,
                fontSize = Be.Type.rowTitle,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(if (pkg.enabled) R.string.state_on else R.string.state_off),
                color = if (pkg.enabled) Be.Colors.accent else Be.Colors.textMuted,
                fontSize = Be.Type.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            text = if (pkg.convertedTextures) {
                "纹理：手机版（已转换）"
            } else {
                "纹理：原始"
            },
            color = Be.Colors.textSecondary,
            fontSize = Be.Type.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * The login display.
 *
 * Three groups in the order the desktop module applies them: which character and
 * how it is scaled, how its animation is driven, and whether the logo band is
 * recoloured. The numeric fields are validated as a set on save, because the
 * loop window and the crossfade length constrain each other.
 */
@Composable
fun LoginDisplayPage(state: SettingsState) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Be.Space.l)) {
        SectionCard(
            eyebrow = stringResource(R.string.model_eyebrow),
            title = stringResource(R.string.model_title),
            status = state.modelTableStatus,
        ) {}

        PanelCard {
            GroupLabel(stringResource(R.string.model_group_model), top = Be.Space.l)
            Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
                SwitchRow(
                    title = stringResource(R.string.model_enable),
                    description = "",
                    checked = state.modelEnabled,
                    onCheckedChange = state::updateModelEnabled,
                    enabled = state.modelAvailable,
                )

                FieldLabel(stringResource(R.string.model_character), top = Be.Space.m)
                SelectField(
                    options = state.characterNames,
                    selectedIndex = state.characterIndex,
                    onSelect = state::selectCharacter,
                    enabled = state.modelAvailable,
                )

                FieldLabel(stringResource(R.string.model_scale))
                TextFieldRow(
                    value = state.modelScale,
                    onValueChange = state::updateModelScale,
                    hint = "1.0",
                    onCommit = state::saveModelSettings,
                    enabled = state.modelAvailable,
                )
            }

            GroupLabel(stringResource(R.string.model_group_animation))
            Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
                FieldLabel(stringResource(R.string.model_action), top = Be.Space.none)
                SelectField(
                    options = state.actionIds,
                    selectedIndex = state.actionIndex,
                    onSelect = state::selectAction,
                    enabled = state.modelAvailable,
                )
                SwitchRow(
                    title = stringResource(R.string.model_final_loop),
                    description = "",
                    checked = state.modelFinalLoop,
                    onCheckedChange = state::updateModelFinalLoop,
                    enabled = state.modelAvailable,
                )
                SwitchRow(
                    title = stringResource(R.string.model_force_loop),
                    description = "",
                    checked = state.modelForceLoop,
                    onCheckedChange = state::updateModelForceLoop,
                    enabled = state.modelAvailable,
                )
                SwitchRow(
                    title = stringResource(R.string.model_crossfade),
                    description = "",
                    checked = state.modelCrossfade,
                    onCheckedChange = state::updateModelCrossfade,
                    enabled = state.modelAvailable,
                )

                FieldLabel(stringResource(R.string.model_loop_start))
                TextFieldRow(
                    value = state.modelLoopStart,
                    onValueChange = state::updateModelLoopStart,
                    hint = "0.968",
                    onCommit = state::saveModelSettings,
                )

                FieldLabel(stringResource(R.string.model_loop_end))
                TextFieldRow(
                    value = state.modelLoopEnd,
                    onValueChange = state::updateModelLoopEnd,
                    hint = "2.376",
                    onCommit = state::saveModelSettings,
                )

                FieldLabel(stringResource(R.string.model_crossfade_duration))
                TextFieldRow(
                    value = state.modelCrossfadeDuration,
                    onValueChange = state::updateModelCrossfadeDuration,
                    hint = "0.20",
                    onCommit = state::saveModelSettings,
                )
            }

            GroupLabel(stringResource(R.string.model_group_logo))
            Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
                SwitchRow(
                    title = stringResource(R.string.logo_enable),
                    description = "",
                    checked = state.logoEnabled,
                    onCheckedChange = state::updateLogoEnabled,
                    enabled = state.modelAvailable,
                )

                FieldLabel(stringResource(R.string.logo_palette_label), top = Be.Space.m)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val current = state.logoColor.trim()
                    Be.Colors.logoPresets.forEach { hex ->
                        ColorSwatch(
                            hex = hex,
                            selected = hex.equals(current, ignoreCase = true),
                            onClick = { state.choosePaletteColor(hex) },
                        )
                    }
                }

                FieldLabel(stringResource(R.string.logo_wheel_label))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    HueColorWheel(
                        argb = state.logoWheelArgb,
                        onChange = state::onWheelColorChanged,
                    )
                }

                FieldLabel(stringResource(R.string.logo_color_label))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextFieldRow(
                        value = state.logoColor,
                        onValueChange = state::updateLogoColor,
                        hint = "#FFC928",
                        keyboardType = KeyboardType.Ascii,
                        maxLength = 7,
                        onCommit = state::saveModelSettings,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    ColorPreview(state.logoColor)
                }
            }
        }

        PrimaryButton(
            text = stringResource(R.string.model_save),
            onClick = state::saveModelSettings,
            modifier = Modifier.padding(top = Be.Space.hairline),
        )

        StatusBlock(state.modelSelectionStatus, monospace = true)
    }
}

@Composable
fun VoicePage(state: SettingsState) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Be.Space.l)) {
        SectionCard(
            eyebrow = stringResource(R.string.voice_eyebrow),
            title = stringResource(R.string.settings_title),
            status = state.voiceTableStatus,
        ) {}

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
            state.voiceCharacterIds.forEachIndexed { index, characterId ->
                VoiceRuleRow(
                    name = state.voiceCharacterNames.getOrElse(index) { characterId },
                    selected = state.voiceLanguage(characterId),
                    onSelect = { position -> state.updateVoiceLanguage(characterId, position) },
                )
            }
        }
    }
}

/**
 * One character's language. The name and the picker sit side by side until the
 * row is too narrow for both - a 132dp picker plus a readable name needs about
 * 340dp - and then stack, which is also what a large font scale calls for.
 */
@Composable
private fun VoiceRuleRow(
    name: String,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.row))
            .background(Be.Colors.row)
            .padding(start = Be.Space.xl, top = Be.Space.l, end = Be.Space.m + 2.dp, bottom = Be.Space.l),
    ) {
        val narrow = maxWidth < 340.dp || fontScale >= 1.3f
        if (narrow) {
            Column(Modifier.fillMaxWidth()) {
                CharacterName(name)
                SelectField(
                    options = LANGUAGE_LABELS,
                    selectedIndex = selected,
                    onSelect = onSelect,
                    modifier = Modifier.fillMaxWidth().padding(top = Be.Space.m),
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CharacterName(name, Modifier.weight(1f))
                SelectField(
                    options = LANGUAGE_LABELS,
                    selectedIndex = selected,
                    onSelect = onSelect,
                    modifier = Modifier.width(132.dp),
                )
            }
        }
    }
}

@Composable
private fun CharacterName(name: String, modifier: Modifier = Modifier) {
    Text(
        text = name,
        color = Be.Colors.textPrimary,
        fontSize = 14.sp,
        maxLines = 2,
        modifier = modifier,
    )
}
