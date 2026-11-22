package dev.betterendfield.next

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

/**
 * The tools tab: the in-game panel, the diagnostics readout, the journal, and the
 * about page.
 *
 * Diagnostics and the journal are separate entries even though both read the same
 * snapshot - one answers "will the module load at all" and the other "what did it
 * do once it did" - and merging them produces a card nobody reads to the end.
 */
@Composable
fun ToolsPage(state: SettingsState, onPreviewOverlay: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(appSectionGap)) {
        OverlayCard(state, onPreviewOverlay)
        DiagnosticsCard(state)

        SettingsPreferenceCard(searchLabels = pageSearchLabels(
            R.string.third_party_manage, R.string.third_party_hint, R.string.media_screen_title,
            R.string.media_screen_hint, R.string.next_workshop,
        )) {
            SubPageRow(
                title = stringResource(R.string.third_party_manage),
                hint = stringResource(R.string.third_party_hint),
                onClick = { context.startActivity(android.content.Intent(context, ThirdPartyModulesActivity::class.java)) },
            )
            SubPageRow(
                title = stringResource(R.string.media_screen_title),
                hint = stringResource(R.string.media_screen_hint),
                onClick = { context.startActivity(android.content.Intent(context, MediaScreenActivity::class.java)) },
            )
            SubPageRow(
                title = stringResource(R.string.next_workshop),
                onClick = { state.openPage(SettingsPage.WORKSHOP) },
            )
        }

        SettingsPreferenceCard(searchLabels = pageSearchLabels(R.string.page_log, R.string.log_caption, R.string.page_app_settings)) {
            SubPageRow(
                title = stringResource(R.string.page_log),
                hint = stringResource(R.string.log_caption),
                state = state.journalText.isNotEmpty(),
                onClick = { state.openPage(SettingsPage.LOG) },
            )
            SubPageRow(
                title = stringResource(R.string.page_app_settings),
                onClick = { state.openPage(SettingsPage.SETTINGS) },
            )
        }
    }
}
@Composable
private fun OverlayCard(state: SettingsState, onPreviewOverlay: () -> Unit) {
    SectionCard(
        eyebrow = stringResource(R.string.overlay_card_eyebrow),
        title = stringResource(R.string.overlay_card_title),
        searchLabels = pageSearchLabels(
            R.string.overlay_enable, R.string.overlay_enable_hint,
            R.string.overlay_auto_snap, R.string.overlay_auto_snap_hint,
            R.string.overlay_log_visible, R.string.overlay_log_visible_hint,
            R.string.overlay_transparency, R.string.overlay_preview,
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
            SwitchRow(
                title = stringResource(R.string.overlay_enable),
                description = stringResource(R.string.overlay_enable_hint),
                checked = state.overlayEnabled,
                onCheckedChange = state::updateOverlayEnabled,
            )
            SwitchRow(
                title = stringResource(R.string.overlay_auto_snap),
                description = stringResource(R.string.overlay_auto_snap_hint),
                checked = state.overlayAutoSnap,
                onCheckedChange = state::updateOverlayAutoSnap,
            )
            SwitchRow(
                title = stringResource(R.string.overlay_log_visible),
                description = stringResource(R.string.overlay_log_visible_hint),
                checked = state.overlayLogVisible,
                onCheckedChange = state::updateOverlayLogVisible,
            )
            SliderRow(
                label = stringResource(R.string.overlay_transparency),
                value = state.overlayTransparency,
                onValueChange = state::updateOverlayTransparency,
                valueRange = 0f..ModuleSettings.OVERLAY_TRANSPARENCY_MAXIMUM,
                unit = "%",
                steps = 80,
                decimals = 0,
            )
            GhostButton(
                text = stringResource(R.string.overlay_preview),
                onClick = onPreviewOverlay,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Reports what is knowable from here: which modules the game will load, and the
 * journal the hooked process publishes back. Whether each Hook resolved is only
 * answerable inside the game, and is reported there.
 */
@Composable
private fun DiagnosticsCard(state: SettingsState) {
    SectionCard(
        eyebrow = stringResource(R.string.diagnostics_eyebrow),
        title = stringResource(R.string.diagnostics_title),
        searchLabels = pageSearchLabels(R.string.diagnostics_framework),
    ) {
        BodyText(
            text = stringResource(R.string.diagnostics_framework),
            modifier = Modifier.padding(top = Be.Space.s),
        )
        BodyText(
            text = state.diagnosticsOverlay,
            modifier = Modifier.padding(top = Be.Space.l),
        )
        BodyText(
            text = state.diagnosticsModules,
            modifier = Modifier.padding(top = Be.Space.l),
        )
        BodyText(
            text = stringResource(R.string.diagnostics_mesh),
            modifier = Modifier.padding(top = Be.Space.l),
        )
    }
}

/**
 * The journal.
 *
 * Two ways out on purpose: reading it here costs nothing and answers most
 * questions, while exporting is what produces something attachable to a bug
 * report. The export goes through the system file picker rather than a fixed
 * path, because the companion app has no storage permission and does not need
 * one.
 */
@Composable
fun LogPage(state: SettingsState) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    val tail = state.journalTailText()
    val logScroll = rememberScrollState()

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { target ->
        if (target == null) return@rememberLauncherForActivityResult
        val outcome = runCatching {
            context.contentResolver.openOutputStream(target).use { out ->
                if (out == null) throw java.io.IOException("provider returned no stream")
                out.write(state.journalBody().toByteArray(Charsets.UTF_8))
            }
        }
        message = if (outcome.isSuccess) {
            context.getString(R.string.log_exported)
        } else {
            context.getString(R.string.log_export_failed, outcome.exceptionOrNull()?.message.orEmpty())
        }
    }

    LaunchedEffect(state.page) {
        state.refreshJournal()
    }
    LaunchedEffect(tail) {
        // Wait for the new text to be measured before positioning at its end.
        withFrameNanos { }
        logScroll.scrollTo(logScroll.maxValue)
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(appSectionGap)) {
        SectionCard(
            eyebrow = stringResource(R.string.log_eyebrow),
            title = stringResource(R.string.page_log),
            subtitle = stringResource(R.string.log_description),
            searchLabels = pageSearchLabels(R.string.log_refresh, R.string.log_export),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = Be.Space.s)) {
                GhostButton(
                    text = stringResource(R.string.log_refresh),
                    onClick = { state.refreshJournal() },
                    modifier = Modifier.width(120.dp),
                )
                Spacer(Modifier.width(Be.Space.m))
                PrimaryButton(
                    text = stringResource(R.string.log_export),
                    onClick = { saver.launch(state.journalFileName()) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (message.isNotEmpty()) {
                BodyText(message, Modifier.padding(top = Be.Space.m))
            }
        }

        ListCaption(
            text = stringResource(R.string.log_caption),
            modifier = Modifier.padding(start = Be.Space.hairline, top = Be.Space.xs),
        )
        if (tail.isBlank()) {
            StatusBlock(stringResource(R.string.log_empty), monospace = true)
        } else {
            Box(
                Modifier.fillMaxWidth().height(380.dp)
                    .clip(RoundedCornerShape(Be.Radius.status))
                    .background(appColors.field)
                    .verticalScroll(logScroll),
            ) {
                Text(
                    text = tail,
                    color = appColors.textSecondary,
                    fontSize = Be.Type.mono,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(Be.Space.l),
                )
            }
        }
    }
}
