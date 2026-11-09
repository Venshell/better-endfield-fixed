package dev.betterendfield.next

import android.content.Context

/** Read-only projection of saved settings. It does not report runtime activation. */
internal object HomeFeatureSummary {
    enum class Destination { CHARACTERS, EXPERIENCE, ADMINISTRATOR, FIRST_PERSON, MMD, TOOLS, MEDIA }
    data class Item(val title: Int, val detail: String, val destination: Destination)

    fun read(context: Context, state: SettingsState): List<Item> = buildList {
        fun addFeature(title: Int, detail: String, destination: Destination) {
            add(Item(title, detail, destination))
        }
        if (state.modelEnabled) addFeature(R.string.model_title, state.loginDisplaySummary, Destination.CHARACTERS)
        if (state.logoEnabled) addFeature(R.string.home_feature_logo,
            context.getString(R.string.home_logo_summary, state.logoColor), Destination.CHARACTERS)
        val appearance = AdministratorAppearanceSelection.read(context)
        if (appearance.enabled) {
            val parts = buildList {
                if (appearance.weapon && appearance.weaponMode == ModuleSettings.APPEARANCE_MODE_INK)
                    add(context.getString(R.string.appearance_weapon_mode_ink))
                if (appearance.character) when (appearance.characterMode) {
                    ModuleSettings.APPEARANCE_MODE_INK -> add(context.getString(R.string.appearance_character_ink))
                    ModuleSettings.APPEARANCE_MODE_UNMASKED -> add(context.getString(R.string.appearance_character_unmasked))
                }
                if (appearance.effects) add(context.getString(R.string.appearance_effects_section_title))
            }
            addFeature(R.string.appearance_card_title, parts.joinToString(" · ").ifEmpty {
                context.getString(R.string.home_appearance_off)
            }, Destination.ADMINISTRATOR)
        }
        if (ModuleSettings.getVoiceRules(context).isNotBlank())
            addFeature(R.string.voice_page, context.getString(R.string.state_on), Destination.CHARACTERS)
        val ui = buildList {
            if (state.hideUid) add(context.getString(R.string.ui_hide_uid))
            if (state.hideHud) add(context.getString(R.string.ui_hide_hud))
            if (state.pcUi) add(context.getString(R.string.ui_pc))
        }
        if (ui.isNotEmpty()) addFeature(R.string.ui_card_title, ui.joinToString(" · "), Destination.EXPERIENCE)
        val camera = buildList {
            if (state.disableDither) add(context.getString(R.string.camera_dither))
            if (state.freeCamera) add(context.getString(R.string.camera_free))
            if (state.freeCamera && state.worldPause) add(context.getString(R.string.camera_pause))
            if (state.globalFovEnabled) add(context.getString(R.string.camera_global_fov))
        }
        if (camera.isNotEmpty()) addFeature(R.string.camera_card_title, camera.joinToString(" · "), Destination.EXPERIENCE)
        if (state.firstPerson) addFeature(R.string.camera_first_person,
            context.getString(R.string.state_on), Destination.FIRST_PERSON)
        if (state.sustainedDash) {
            val characters = buildList {
                if (state.dashAglina) add(context.getString(R.string.dash_aglina))
                if (state.dashLiino) add(context.getString(R.string.dash_liino))
                if (state.dashJsspsi) add(context.getString(R.string.dash_jsspsi))
            }
            addFeature(R.string.home_feature_dash, characters.joinToString(" · ").ifEmpty {
                context.getString(R.string.home_dash_no_characters)
            }, Destination.EXPERIENCE)
        }
        if (state.overlayEnabled) addFeature(R.string.overlay_card_title,
            context.getString(R.string.home_overlay_summary, state.overlayTransparency.toInt()), Destination.TOOLS)
        if (state.mmdEnabled) addFeature(R.string.mmd_title, state.mmdSourceSummary, Destination.MMD)
        if (FrameworkSettings.open(context).getString(ModuleSettings.MEDIA_CONFIGURATION, "").orEmpty().isNotBlank())
            addFeature(R.string.media_screen_title, context.getString(R.string.state_on), Destination.MEDIA)
    }
}
