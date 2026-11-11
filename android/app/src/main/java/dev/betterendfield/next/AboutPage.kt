package dev.betterendfield.next

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FilterInputStream
import java.io.IOException

/** The upstream About actions, bound to the existing update and resource owners. */
@Composable
fun AboutPage() {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val preferences = remember(context) { FrameworkSettings.open(context) }
    var language by remember { mutableStateOf(preferences.getString(AppLanguage.KEY, "system") ?: "system") }
    var autoCheck by remember { mutableStateOf(preferences.getBoolean(ModuleSettings.AUTO_CHECK_UPDATES, true)) }
    var updateStatus by remember { mutableStateOf("") }
    var resourceStatus by remember { mutableStateOf("") }
    var releaseUrl by remember { mutableStateOf(AppUpdateChecker.RELEASES) }
    var checking by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }
    var showDisclaimer by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf(false) }

    fun open(url: String) {
        linkError = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        }.isFailure
    }

    val importResource = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (!DistributionUpdater.busy.compareAndSet(false, true)) {
            resourceStatus = context.getString(R.string.next_resource_busy)
            return@rememberLauncherForActivityResult
        }
        updating = true
        DistributionUpdater.cancelled.set(false)
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                try {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw IOException(context.getString(R.string.next_resource_open_failed))
                    val installed = input.use { source ->
                        GameResourceStore.install(context, object : FilterInputStream(source) {
                            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                                DistributionUpdater.checkCancelled()
                                return super.read(buffer, offset, length)
                            }
                        })
                    }
                    context.getString(R.string.next_resource_imported, installed)
                } catch (error: Exception) {
                    context.getString(R.string.next_resource_failed, error.message ?: error.javaClass.simpleName)
                } finally {
                    DistributionUpdater.busy.set(false)
                }
            }
            updating = false
            resourceStatus = outcome
        }
    }

    fun updateResources(preview: Boolean, resume: Boolean) {
        if (updating || DistributionUpdater.busy.get()) {
            resourceStatus = context.getString(R.string.next_resource_busy)
            return
        }
        updating = true
        resourceStatus = context.getString(R.string.next_resource_working)
        scope.launch {
            resourceStatus = withContext(Dispatchers.IO) {
                runCatching { DistributionUpdater.update(context.applicationContext, preview, resume) }
                    .fold(
                        onSuccess = { context.getString(R.string.next_resource_updated, it) },
                        onFailure = { context.getString(R.string.next_resource_failed, it.message ?: it.javaClass.simpleName) },
                    )
            }
            updating = false
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Be.Space.l)) {
        SectionCard(
            eyebrow = stringResource(R.string.about_eyebrow),
            title = stringResource(R.string.page_about),
            subtitle = stringResource(R.string.about_description),
        ) {}

        PanelCard {
            CardTitle(stringResource(R.string.about_version))
            BodyText("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", Modifier.padding(top = Be.Space.m))
            BodyText(stringResource(R.string.about_platform), Modifier.padding(top = Be.Space.s))
            BodyText(stringResource(R.string.about_license_value), Modifier.padding(top = Be.Space.s))
            BodyText(stringResource(R.string.about_modules_value), Modifier.padding(top = Be.Space.s))
        }

        PanelCard {
            CardTitle(stringResource(R.string.next_language))
            SelectField(
                options = listOf(stringResource(R.string.next_language_system), stringResource(R.string.next_language_zh), "English"),
                selectedIndex = listOf("system", "zh-CN", "en").indexOf(UiLanguagePolicy.normalize(language)).coerceAtLeast(0),
                onSelect = { index ->
                    val selected = listOf("system", "zh-CN", "en")[index]
                    AppLanguage.select(context, selected)
                    language = selected
                    activity.recreate()
                },
                modifier = Modifier.padding(top = Be.Space.m),
            )
        }

        PanelCard {
            CardTitle(stringResource(R.string.next_software_update))
            SwitchRow(
                title = stringResource(R.string.next_auto_check),
                description = stringResource(R.string.next_auto_check_hint),
                checked = autoCheck,
                onCheckedChange = {
                    autoCheck = it
                    preferences.edit().putBoolean(ModuleSettings.AUTO_CHECK_UPDATES, it).apply()
                },
            )
            GhostButton(
                text = stringResource(if (checking) R.string.next_checking else R.string.next_check_update),
                enabled = !checking,
                onClick = {
                    checking = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { AppUpdateChecker.check() } }
                        result.fold(
                            onSuccess = { updateStatus = it.message; releaseUrl = it.releaseUrl },
                            onFailure = { updateStatus = context.getString(R.string.next_update_failed, it.message ?: it.javaClass.simpleName) },
                        )
                        checking = false
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = Be.Space.m),
            )
            if (updateStatus.isNotBlank()) BodyText(updateStatus, Modifier.padding(top = Be.Space.m))
            GhostButton(stringResource(R.string.next_open_releases), { open(releaseUrl) }, Modifier.fillMaxWidth().padding(top = Be.Space.m))
        }

        PanelCard {
            CardTitle(stringResource(R.string.next_resources))
            GhostButton(stringResource(R.string.next_import_resource), { importResource.launch(arrayOf("application/zip")) }, Modifier.fillMaxWidth().padding(top = Be.Space.m), !updating)
            GhostButton(stringResource(R.string.next_update_core), { updateResources(false, false) }, Modifier.fillMaxWidth().padding(top = Be.Space.m), !updating)
            GhostButton(stringResource(R.string.next_update_preview), { updateResources(true, false) }, Modifier.fillMaxWidth().padding(top = Be.Space.m), !updating)
            GhostButton(stringResource(R.string.next_resume_resources), { updateResources(false, true) }, Modifier.fillMaxWidth().padding(top = Be.Space.m), !updating)
            if (updating) GhostButton(stringResource(R.string.next_cancel_download), { DistributionUpdater.cancelled.set(true) }, Modifier.fillMaxWidth().padding(top = Be.Space.m))
            if (resourceStatus.isNotBlank()) BodyText(resourceStatus, Modifier.padding(top = Be.Space.m))
        }

        PanelCard {
            CardTitle(stringResource(R.string.next_project))
            GhostButton(stringResource(R.string.next_github), { open(ProjectLinks.REPOSITORY) }, Modifier.fillMaxWidth().padding(top = Be.Space.m))
            GhostButton(stringResource(R.string.next_upstream_github), { open(ProjectLinks.UPSTREAM_REPOSITORY) }, Modifier.fillMaxWidth().padding(top = Be.Space.m))
            GhostButton(stringResource(R.string.next_open_releases), { open(AppUpdateChecker.RELEASES) }, Modifier.fillMaxWidth().padding(top = Be.Space.m))
            GhostButton(stringResource(R.string.about_license), { open(ProjectLinks.LICENSE) }, Modifier.fillMaxWidth().padding(top = Be.Space.m))
        }

        PanelCard {
            CardTitle(stringResource(R.string.next_disclaimer))
            BodyText(stringResource(R.string.next_disclaimer_short), Modifier.padding(top = Be.Space.m))
            GhostButton(stringResource(R.string.next_disclaimer_full), { showDisclaimer = true }, Modifier.fillMaxWidth().padding(top = Be.Space.m))
        }
        if (linkError) BodyText(stringResource(R.string.next_link_open_failed))
    }

    if (showDisclaimer) AlertDialog(
        onDismissRequest = { showDisclaimer = false },
        title = { Text(stringResource(R.string.next_disclaimer)) },
        text = { Text(stringResource(R.string.next_disclaimer_text)) },
        confirmButton = { GhostButton(stringResource(R.string.next_close), { showDisclaimer = false }) },
    )
}
