package dev.betterendfield.next

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FilterInputStream
import java.io.IOException

@Composable
internal fun AppSettingsPage(onThemeSettings: () -> Unit, onAbout: () -> Unit) {
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
    var linkError by remember { mutableStateOf(false) }

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

    val languageTags = listOf("system", "zh-CN", "en")
    val languagePreference: @Composable () -> Unit = {
        AboutPreference(
            title = stringResource(R.string.next_language),
            options = listOf(stringResource(R.string.next_language_system), stringResource(R.string.next_language_zh), "English"),
            selectedIndex = languageTags.indexOf(UiLanguagePolicy.normalize(language)).coerceAtLeast(0),
            onSelect = { index ->
                val selected = languageTags[index]
                AppLanguage.select(context, selected)
                language = selected
                activity.recreate()
            },
        )
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(appSectionGap)) {
        SettingsPreferenceCard(searchLabels = pageSearchLabels(R.string.app_theme_title, R.string.next_language)) {
            SubPageRow(title = stringResource(R.string.app_theme_title), onClick = onThemeSettings)
            if (matchesPageFilter(stringResource(R.string.next_language))) languagePreference()
        }

        SettingsGroup(stringResource(R.string.next_software_update), pageSearchLabels(
            R.string.next_auto_check, R.string.next_check_update, R.string.next_open_releases,
        )) {
            SwitchRow(
                title = stringResource(R.string.next_auto_check),
                description = stringResource(R.string.next_auto_check_hint),
                checked = autoCheck,
                onCheckedChange = {
                    autoCheck = it
                    preferences.edit().putBoolean(ModuleSettings.AUTO_CHECK_UPDATES, it).apply()
                },
            )
            AboutAction(
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
            )
            if (updateStatus.isNotBlank()) BodyText(updateStatus, Modifier.padding(vertical = Be.Space.m))
            AboutAction(stringResource(R.string.next_open_releases), { linkError = !context.openAppLink(releaseUrl) })
            if (linkError) BodyText(stringResource(R.string.next_link_open_failed))
        }

        SettingsGroup(stringResource(R.string.next_resources), pageSearchLabels(
            R.string.next_import_resource, R.string.next_update_core, R.string.next_update_preview, R.string.next_resume_resources,
        )) {
            AboutAction(stringResource(R.string.next_import_resource), { importResource.launch(arrayOf("application/zip")) }, enabled = !updating)
            AboutAction(stringResource(R.string.next_update_core), { updateResources(false, false) }, enabled = !updating)
            AboutAction(stringResource(R.string.next_update_preview), { updateResources(true, false) }, enabled = !updating)
            AboutAction(stringResource(R.string.next_resume_resources), { updateResources(false, true) }, enabled = !updating)
            if (updating) AboutAction(stringResource(R.string.next_cancel_download), { DistributionUpdater.cancelled.set(true) })
            if (resourceStatus.isNotBlank()) BodyText(resourceStatus, Modifier.padding(vertical = Be.Space.m))
        }

        SubPageRow(title = stringResource(R.string.page_about), onClick = onAbout)
    }
}

@Composable
internal fun AppThemeSettingsPage() {
    val appearance = LocalAppearanceSettings.current
    val saveAppearance = LocalSetAppearanceSettings.current
    val glassAvailable = isAppGlassSupported()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(appSectionGap)) {
        SettingsPreferenceCard(searchLabels = pageSearchLabels(R.string.app_theme_mode, R.string.app_theme_glass)) {
            if (matchesPageFilter(stringResource(R.string.app_theme_mode))) AboutPreference(
                title = stringResource(R.string.app_theme_mode),
                options = AppThemeMode.entries.map { stringResource(it.label) },
                selectedIndex = appearance.theme.ordinal,
                onSelect = { saveAppearance(appearance.copy(theme = AppThemeMode.entries[it])) },
            )
            if (matchesPageFilter(stringResource(R.string.app_theme_glass))) SwitchPreference(
                title = stringResource(R.string.app_theme_glass),
                checked = appearance.glass && glassAvailable,
                enabled = glassAvailable,
                insideMargin = PaddingValues(vertical = MiuixLayout.inset),
                switchColors = appMiuixSwitchColors(),
                onCheckedChange = { saveAppearance(appearance.copy(glass = it)) },
            )
        }
    }
}

@Composable
internal fun AboutPage() {
    val context = LocalContext.current
    var linkError by remember { mutableStateOf(false) }
    var showDisclaimer by remember { mutableStateOf(false) }

    fun open(url: String) {
        linkError = !context.openAppLink(url)
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(appSectionGap)) {
        val description = stringResource(R.string.about_description)
        if (matchesPageFilter(description)) {
            BodyText(description, Modifier.padding(horizontal = MiuixLayout.inset))
        }
        SettingsGroup(stringResource(R.string.next_project), pageSearchLabels(
            R.string.next_github, R.string.next_upstream_github, R.string.next_open_releases, R.string.about_license,
        )) {
            AboutAction(stringResource(R.string.next_github), { open(ProjectLinks.REPOSITORY) })
            AboutAction(stringResource(R.string.next_upstream_github), { open(ProjectLinks.UPSTREAM_REPOSITORY) })
            AboutAction(stringResource(R.string.next_open_releases), { open(AppUpdateChecker.RELEASES) })
            AboutAction(stringResource(R.string.about_license), { open(ProjectLinks.LICENSE) })
        }

        SettingsGroup(stringResource(R.string.next_disclaimer), pageSearchLabels(R.string.next_disclaimer_full)) {
            BodyText(stringResource(R.string.next_disclaimer_short), Modifier.padding(top = Be.Space.m))
            AboutAction(stringResource(R.string.next_disclaimer_full), { showDisclaimer = true })
        }
        AboutVersion()
        if (linkError) BodyText(stringResource(R.string.next_link_open_failed))
    }

    if (showDisclaimer) AlertDialog(
        onDismissRequest = { showDisclaimer = false },
        title = { Text(stringResource(R.string.next_disclaimer)) },
        text = { Text(stringResource(R.string.next_disclaimer_text)) },
        confirmButton = { GhostButton(stringResource(R.string.next_close), { showDisclaimer = false }) },
    )
}

private fun Context.openAppLink(url: String): Boolean = runCatching {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
}.isSuccess

private val appVersion get() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

@Composable
private fun AboutVersion() {
    SettingsGroup(stringResource(R.string.about_version), listOf(appVersion) + pageSearchLabels(
        R.string.about_platform, R.string.about_license_value, R.string.about_modules_value,
    )) {
        Column(Modifier.padding(vertical = if (usesMiuix) MiuixLayout.inset else Be.Space.none)) {
            BodyText(appVersion, Modifier.padding(top = if (usesMiuix) Be.Space.none else Be.Space.m))
            BodyText(stringResource(R.string.about_platform), Modifier.padding(top = Be.Space.s))
            BodyText(stringResource(R.string.about_license_value), Modifier.padding(top = Be.Space.s))
            BodyText(stringResource(R.string.about_modules_value), Modifier.padding(top = Be.Space.s))
        }
    }
}

@Composable
private fun AboutAction(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    SubPageRow(title = text, enabled = enabled, onClick = onClick)
}

@Composable
private fun AboutPreference(title: String, options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    WindowDropdownPreference(
        title = title,
        items = options,
        selectedIndex = selectedIndex,
        insideMargin = PaddingValues(vertical = MiuixLayout.inset),
        onSelectedIndexChange = { if (it != selectedIndex) onSelect(it) },
    )
}
