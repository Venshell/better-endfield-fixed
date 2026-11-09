package dev.betterendfield.next

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

@Composable
fun AdministratorAppearancePage(onSaved: () -> Unit = {}) {
    val context = LocalContext.current
    val state = remember(context) { AdministratorAppearanceState(context) }
    val selection = state.selection
    fun save(next: AdministratorAppearanceSelection) {
        if (state.update(next)) onSaved()
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Be.Space.l)) {
        SectionCard(eyebrow = stringResource(R.string.admin_appearance_eyebrow),
            title = stringResource(R.string.appearance_card_title)) {
            SwitchRow(stringResource(R.string.appearance_enable), "", selection.enabled,
                { save(selection.copy(enabled = it)) })
            BodyText(stringResource(R.string.admin_appearance_next_launch))
            if (state.saveFailed) BodyText(stringResource(R.string.admin_appearance_save_failed))
        }
        SectionCard(eyebrow = "", title = stringResource(R.string.appearance_weapon_section_title)) {
            SwitchRow(stringResource(R.string.appearance_weapon_toggle), "", selection.weapon,
                { save(selection.copy(weapon = it)) }, enabled = selection.enabled)
            FieldLabel(stringResource(R.string.appearance_weapon_mode_label))
            SelectField(listOf(stringResource(R.string.appearance_weapon_mode_ink),
                stringResource(R.string.appearance_weapon_mode_default)),
                if (selection.weaponMode == ModuleSettings.APPEARANCE_MODE_DEFAULT) 1 else 0,
                { save(selection.copy(weaponMode = if (it == 0) ModuleSettings.APPEARANCE_MODE_INK
                    else ModuleSettings.APPEARANCE_MODE_DEFAULT)) },
                enabled = selection.enabled && selection.weapon)
        }
        SectionCard(eyebrow = "", title = stringResource(R.string.appearance_character_section_title)) {
            SwitchRow(stringResource(R.string.appearance_character_toggle), "", selection.character,
                { save(selection.copy(character = it)) }, enabled = selection.enabled)
            FieldLabel(stringResource(R.string.appearance_character_mode_label))
            val modes = listOf(ModuleSettings.APPEARANCE_MODE_INK,
                ModuleSettings.APPEARANCE_MODE_UNMASKED, ModuleSettings.APPEARANCE_MODE_DEFAULT)
            SelectField(listOf(stringResource(R.string.appearance_character_ink),
                stringResource(R.string.appearance_character_unmasked),
                stringResource(R.string.appearance_character_default)),
                modes.indexOf(selection.characterMode).coerceAtLeast(0),
                { save(selection.copy(characterMode = modes[it])) },
                enabled = selection.enabled && selection.character)
        }
        SectionCard(eyebrow = "", title = stringResource(R.string.appearance_effects_section_title)) {
            SwitchRow(stringResource(R.string.appearance_effects_toggle), "", selection.effects,
                { save(selection.copy(effects = it)) }, enabled = selection.enabled)
        }
    }
}
