package dev.betterendfield.next

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp

private data class HomeSearchEntry(
    val title: Int,
    val page: Int,
    val activity: Class<*>? = null,
)

private fun featuresOn(page: Int, vararg titles: Int) = titles.map { HomeSearchEntry(it, page) }

private val HomeSearchEntries = buildList {
    addAll(featuresOn(SettingsPage.HOME,
        R.string.game_management_title, R.string.home_title, R.string.home_shortcuts_title))
    addAll(featuresOn(SettingsPage.EXPERIENCE,
        R.string.page_experience, R.string.ui_card_title, R.string.ui_hide_uid,
        R.string.ui_hide_hud, R.string.ui_pc, R.string.camera_card_title, R.string.camera_dither,
        R.string.camera_free, R.string.camera_pause, R.string.camera_global_fov,
        R.string.dash_card_title, R.string.dash_enable, R.string.dash_aglina,
        R.string.dash_liino, R.string.dash_jsspsi))
    addAll(featuresOn(SettingsPage.FIRST_PERSON,
        R.string.page_first_person, R.string.fp_enable, R.string.fp_group_display,
        R.string.fp_group_control, R.string.camera_fp_fov_label, R.string.camera_fp_eye_height,
        R.string.camera_fp_near_clip))
    addAll(featuresOn(SettingsPage.CAMERA_MOTION,
        R.string.page_camera_motion, R.string.motion_group_look, R.string.motion_group_preset,
        R.string.motion_group_keyframe, R.string.motion_group_vmd, R.string.motion_vmd_import))
    addAll(featuresOn(SettingsPage.MMD, R.string.mmd_title, R.string.mmd_alignment_open_import))
    addAll(featuresOn(SettingsPage.CHARACTERS,
        R.string.page_characters, R.string.model_title, R.string.settings_title,
        R.string.home_login_display_title))
    addAll(featuresOn(SettingsPage.APPEARANCE,
        R.string.custom_model_title, R.string.home_appearance_title, R.string.custom_model_install))
    addAll(featuresOn(SettingsPage.ADMINISTRATOR_APPEARANCE,
        R.string.appearance_card_title, R.string.appearance_enable, R.string.appearance_weapon_section_title,
        R.string.appearance_character_section_title, R.string.appearance_effects_section_title))
    addAll(featuresOn(SettingsPage.TOOLS,
        R.string.page_tools, R.string.overlay_card_title, R.string.overlay_enable,
        R.string.overlay_auto_snap, R.string.overlay_log_visible, R.string.overlay_transparency,
        R.string.overlay_preview, R.string.diagnostics_title))
    add(HomeSearchEntry(R.string.third_party_manage, SettingsPage.TOOLS, ThirdPartyModulesActivity::class.java))
    add(HomeSearchEntry(R.string.media_screen_title, SettingsPage.TOOLS, MediaScreenActivity::class.java))
    addAll(featuresOn(SettingsPage.WORKSHOP,
        R.string.next_workshop, R.string.next_workshop_direct, R.string.next_workshop_domain))
    addAll(featuresOn(SettingsPage.LOG, R.string.page_log, R.string.log_refresh, R.string.log_export))
    addAll(featuresOn(SettingsPage.SETTINGS,
        R.string.page_app_settings, R.string.next_language, R.string.next_software_update,
        R.string.next_auto_check, R.string.next_check_update, R.string.next_resources,
        R.string.next_import_resource, R.string.next_update_core, R.string.next_update_preview,
        R.string.next_resume_resources))
    addAll(featuresOn(SettingsPage.THEME_SETTINGS,
        R.string.app_theme_title, R.string.app_theme_mode, R.string.app_theme_glass))
    addAll(featuresOn(SettingsPage.ABOUT,
        R.string.page_about, R.string.next_project, R.string.next_github,
        R.string.next_upstream_github, R.string.about_license, R.string.next_disclaimer, R.string.about_version))
}

@Composable
internal fun HomeSearchResults(
    query: String,
    topPadding: Dp,
    onOpenPage: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val needle = query.trim()
    val gap = appSectionGap
    val context = LocalContext.current
    val rows = HomeSearchEntries.map { entry ->
        Triple(entry, stringResource(entry.title), stringResource(SettingsPage.titleOf(entry.page)))
    }.filter { (_, title, page) ->
        title.contains(needle, ignoreCase = true) || page.contains(needle, ignoreCase = true)
    }
    LazyColumn(
        Modifier.widthIn(max = Be.Size.contentMaxWidth).fillMaxSize(),
        contentPadding = PaddingValues(appPageGutter, topPadding, appPageGutter, Be.Space.footer),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        if (rows.isEmpty()) {
            item { BodyText(stringResource(R.string.search_empty)) }
        }
        items(rows.size, key = { rows[it].first.title }) { index ->
            val (entry, title, page) = rows[index]
            SubPageRow(title = title, hint = page.takeUnless { it == title }, onClick = {
                onClose()
                if (entry.activity == null) {
                    if (entry.page != SettingsPage.HOME) onOpenPage(entry.page)
                } else {
                    context.startActivity(Intent(context, entry.activity))
                }
            })
        }
    }
}

internal val LocalPageFilter = staticCompositionLocalOf { "" }

@Composable
internal fun matchesPageFilter(vararg labels: String?): Boolean {
    val query = LocalPageFilter.current
    return query.isBlank() || labels.any { it?.contains(query, ignoreCase = true) == true }
}

@Composable
internal fun pageSearchLabels(vararg labels: Int): List<String> = labels.map { stringResource(it) }
