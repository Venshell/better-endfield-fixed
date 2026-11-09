package dev.betterendfield.next

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** UI snapshot of the existing appearance settings; ModuleSettings owns serialization. */
internal data class AdministratorAppearanceSelection(
    val enabled: Boolean,
    val weapon: Boolean,
    val weaponMode: String,
    val character: Boolean,
    val characterMode: String,
    val effects: Boolean,
) {
    companion object {
        fun read(context: Context) = AdministratorAppearanceSelection(
            ModuleSettings.isAppearanceEnabled(context),
            ModuleSettings.isAppearanceReplaceWeapon(context),
            ModuleSettings.getAppearanceWeaponMode(context),
            ModuleSettings.isAppearanceReplaceCharacter(context),
            ModuleSettings.getAppearanceMode(context),
            ModuleSettings.isAppearanceReplaceEffects(context),
        )
    }
}

internal class AdministratorAppearanceState(private val context: Context) {
    var selection by mutableStateOf(AdministratorAppearanceSelection.read(context))
        private set
    var saveFailed by mutableStateOf(false)
        private set

    fun update(next: AdministratorAppearanceSelection): Boolean {
        try {
            ModuleSettings.setAppearanceSettings(context, next.enabled, next.weapon, next.weaponMode,
                next.character, next.characterMode, next.effects)
            selection = AdministratorAppearanceSelection.read(context)
            saveFailed = selection != next
        } catch (_: RuntimeException) {
            saveFailed = true
        }
        return !saveFailed
    }
}
