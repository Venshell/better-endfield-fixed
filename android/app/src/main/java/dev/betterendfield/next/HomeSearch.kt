package dev.betterendfield.next

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Features the home search can open. A row is a name the user would type and
 * the screen that name lives on.
 */
private sealed interface HomeSearchTarget {
    data class Page(val page: Int) : HomeSearchTarget
    data object Media : HomeSearchTarget
    data object ThirdParty : HomeSearchTarget
}

private data class HomeSearchEntry(
    val title: Int,
    val place: Int?,
    val target: HomeSearchTarget,
)

private val HomeSearchCatalog = listOf(
    HomeSearchEntry(R.string.page_experience, null, HomeSearchTarget.Page(SettingsPage.EXPERIENCE)),
    HomeSearchEntry(R.string.ui_card_title, R.string.page_experience, HomeSearchTarget.Page(SettingsPage.EXPERIENCE)),
    HomeSearchEntry(R.string.ui_hide_uid, R.string.page_experience, HomeSearchTarget.Page(SettingsPage.EXPERIENCE)),
    HomeSearchEntry(R.string.ui_hide_hud, R.string.page_experience, HomeSearchTarget.Page(SettingsPage.EXPERIENCE)),
    HomeSearchEntry(R.string.ui_pc, R.string.page_experience, HomeSearchTarget.Page(SettingsPage.EXPERIENCE)),
    HomeSearchEntry(R.string.camera_card_title, R.string.page_experience, HomeSearchTarget.Page(SettingsPage.EXPERIENCE)),
    HomeSearchEntry(R.string.camera_dither, R.string.page_experience, HomeSearchTarget.Page(SettingsPage.EXPERIENCE)),
    HomeSearchEntry(R.string.camera_free, R.string.page_experience, HomeSearchTarget.Page(SettingsPage.EXPERIENCE)),
    HomeSearchEntry(R.string.camera_pause, R.string.page_experience, HomeSearchTarget.Page(SettingsPage.EXPERIENCE)),
    HomeSearchEntry(R.string.camera_global_fov, R.string.page_experience, HomeSearchTarget.Page(SettingsPage.EXPERIENCE)),
    HomeSearchEntry(R.string.page_first_person, R.string.page_experience, HomeSearchTarget.Page(SettingsPage.FIRST_PERSON)),
    HomeSearchEntry(R.string.page_camera_motion, R.string.page_experience, HomeSearchTarget.Page(SettingsPage.CAMERA_MOTION)),
    HomeSearchEntry(R.string.mmd_title, R.string.page_experience, HomeSearchTarget.Page(SettingsPage.MMD)),
    HomeSearchEntry(R.string.dash_card_title, R.string.page_experience, HomeSearchTarget.Page(SettingsPage.EXPERIENCE)),

    HomeSearchEntry(R.string.page_characters, null, HomeSearchTarget.Page(SettingsPage.CHARACTERS)),
    HomeSearchEntry(R.string.custom_model_title, R.string.page_characters, HomeSearchTarget.Page(SettingsPage.APPEARANCE)),
    HomeSearchEntry(R.string.appearance_card_title, R.string.page_characters, HomeSearchTarget.Page(SettingsPage.ADMINISTRATOR_APPEARANCE)),
    HomeSearchEntry(R.string.model_title, R.string.page_characters, HomeSearchTarget.Page(SettingsPage.CHARACTERS)),
    HomeSearchEntry(R.string.settings_title, R.string.page_characters, HomeSearchTarget.Page(SettingsPage.CHARACTERS)),

    HomeSearchEntry(R.string.page_tools, null, HomeSearchTarget.Page(SettingsPage.TOOLS)),
    HomeSearchEntry(R.string.overlay_card_title, R.string.page_tools, HomeSearchTarget.Page(SettingsPage.TOOLS)),
    HomeSearchEntry(R.string.diagnostics_title, R.string.page_tools, HomeSearchTarget.Page(SettingsPage.TOOLS)),
    HomeSearchEntry(R.string.third_party_title, R.string.page_tools, HomeSearchTarget.ThirdParty),
    HomeSearchEntry(R.string.media_screen_title, R.string.page_tools, HomeSearchTarget.Media),
    HomeSearchEntry(R.string.next_workshop, R.string.page_tools, HomeSearchTarget.Page(SettingsPage.WORKSHOP)),
    HomeSearchEntry(R.string.page_log, R.string.page_tools, HomeSearchTarget.Page(SettingsPage.LOG)),
    HomeSearchEntry(R.string.page_about, R.string.page_tools, HomeSearchTarget.Page(SettingsPage.ABOUT)),

    HomeSearchEntry(R.string.game_management_title, R.string.page_home, HomeSearchTarget.Page(SettingsPage.HOME)),
)

@Composable
fun HomeSearchPanel(
    onOpenPage: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val needle = query.trim()
    val rows = HomeSearchCatalog.map { entry ->
        Triple(entry, stringResource(entry.title), entry.place?.let { stringResource(it) }.orEmpty())
    }.filter { (_, title, place) ->
        needle.isEmpty() || title.contains(needle, ignoreCase = true) || place.contains(needle, ignoreCase = true)
    }
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = Be.Space.gutter),
    ) {
        SearchField(
            value = query,
            onValueChange = { query = it },
            hint = stringResource(R.string.search_features),
            requestFocus = true,
        )
        if (rows.isEmpty()) {
            Text(
                text = stringResource(R.string.search_empty),
                color = Be.Colors.textMuted,
                fontSize = Be.Type.bodySmall,
                modifier = Modifier.padding(top = Be.Space.l, start = Be.Space.s),
            )
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = Be.Space.m, bottom = Be.Space.l),
                verticalArrangement = Arrangement.spacedBy(Be.Space.m),
            ) {
                rows.forEach { (entry, title, place) ->
                    HomeSearchRow(title, place) {
                        onClose()
                        when (val target = entry.target) {
                            is HomeSearchTarget.Page -> onOpenPage(target.page)
                            HomeSearchTarget.Media ->
                                context.startActivity(Intent(context, MediaScreenActivity::class.java))
                            HomeSearchTarget.ThirdParty ->
                                context.startActivity(Intent(context, ThirdPartyModulesActivity::class.java))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSearchRow(title: String, place: String, onClick: () -> Unit) {
    val press = rememberPressed(true, onClick)
    Row(
        Modifier
            .pressScale(press.value)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.row))
            .background(pressedFill(press.value, Be.Colors.row, Be.Colors.rowHigh))
            .then(press.modifier)
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
            if (place.isNotEmpty() && place != title) {
                Text(
                    text = place,
                    color = Be.Colors.textMuted,
                    fontSize = Be.Type.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Caret(
            color = Be.Colors.textSecondary,
            modifier = Modifier
                .padding(start = Be.Space.m)
                .rotate(-90f),
        )
    }
}
