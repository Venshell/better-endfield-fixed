package dev.betterendfield.next

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The third-party model package manager.
 *
 * Everything the installer does happens on its own worker and reports through
 * static fields, so this screen is a poller plus a list: it never blocks on a
 * texture conversion, and a conversion that is still running when the user comes
 * back is shown where it left off rather than restarted.
 */
@Composable
fun BemInstallScreen(state: BemInstallState) {
    val activity = LocalContext.current as android.app.Activity
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) state.import(uri)
    }
    var pendingRemoval by remember { mutableStateOf<BemPackage?>(null) }
    var discardDraft by remember { mutableStateOf(false) }
    var astcTarget by remember { mutableStateOf<BemPackage?>(null) }
    var repairNormals by remember { mutableStateOf(false) }

    Surface(
        color = Be.Colors.background,
        contentColor = Be.Colors.textPrimary,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = Be.Size.contentMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = Be.Space.gutter,
                        end = Be.Space.gutter,
                        top = Be.Space.page,
                        bottom = Be.Space.footer,
                    ),
                verticalArrangement = Arrangement.spacedBy(Be.Space.l),
            ) {
                SectionCard(
                    eyebrow = "BEM · PACKAGE MANAGER",
                    title = stringResource(R.string.custom_model_title),
                    subtitle = stringResource(R.string.bem_install_restart_hint),
                ) {}

                PrimaryButton(
                    text = "导入 BEM / ZIP",
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = !state.busy,
                )
                if (state.pendingExternalUri != null) {
                    GhostButton(
                        text = stringResource(R.string.bem_retry_external_import),
                        onClick = { state.retryPendingExternalImport() },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.bem_pending_external_import), color = Be.Colors.textSecondary)
                }

                GhostButton(
                    text = stringResource(R.string.bem_save_selection),
                    onClick = { state.saveAll() },
                    enabled = !state.busy && state.indexBroken == null && !state.indexConflict && state.packages.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
                GhostButton(
                    text = stringResource(R.string.bem_disable_all),
                    onClick = { state.disableAll() },
                    enabled = !state.busy && state.indexBroken == null && !state.indexConflict && state.packages.any { it.enabled },
                    modifier = Modifier.fillMaxWidth(),
                )

                GhostButton(
                    text = stringResource(R.string.next_bem_compose),
                    onClick = { BemComposeActivity.open(activity) },
                    enabled = state.canTransform,
                    modifier = Modifier.fillMaxWidth(),
                )
                GhostButton(
                    text = stringResource(R.string.next_bem_repair_normals),
                    onClick = { repairNormals = true },
                    enabled = state.canTransform && state.packages.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.hasDraft) BodyText(stringResource(R.string.next_bem_save_before_tools))

                PanelCard {
                    ListCaption(stringResource(R.string.next_bem_experiments))
                    state.experiments.forEach { (key, title) ->
                        SwitchRow(
                            title = stringResource(title),
                            description = "",
                            checked = state.experimentValues[key] == true,
                            onCheckedChange = { state.changeExperiment(key, it) },
                            enabled = !state.busy,
                        )
                    }
                }

                if (state.indexConflict) {
                    StatusBlock(stringResource(R.string.bem_draft_conflict))
                    GhostButton(
                        text = stringResource(R.string.bem_reload_latest),
                        onClick = { discardDraft = true },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                PanelCard {
                    ListCaption(stringResource(R.string.bem_storage_title))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.bem_keep_local_copies),
                            color = Be.Colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = state.keepLocalCopies,
                            onCheckedChange = state::changeLocalCopies,
                            enabled = !state.busy,
                        )
                    }
                    BodyText(stringResource(R.string.bem_keep_local_copies_hint))
                    GhostButton(
                        text = stringResource(R.string.bem_clean_unused),
                        onClick = state::cleanUnused,
                        enabled = !state.busy && state.indexBroken == null,
                        modifier = Modifier.fillMaxWidth().padding(top = Be.Space.l),
                    )
                }

                PanelCard {
                    ListCaption("处理状态")
                    BodyText(state.status, Modifier.padding(top = 9.dp))
                    if (state.busy) {
                        ProgressBar(
                            fraction = state.percent.takeIf { it >= 0 }?.let { it / 100f },
                            modifier = Modifier.padding(top = Be.Space.xl),
                        )
                        BodyText(
                            text = progressLine(state.percent, state.elapsedSeconds),
                            modifier = Modifier.padding(top = Be.Space.m),
                        )
                        GhostButton(
                            text = "取消当前操作",
                            onClick = { state.cancel() },
                            enabled = !state.removing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Be.Space.l),
                        )
                    }
                }


                ListCaption(
                    text = "已安装的模型包",
                    modifier = Modifier.padding(start = Be.Space.hairline, top = Be.Space.l),
                )

                val broken = state.indexBroken
                if (broken != null) {
                    StatusBlock(broken, modifier = Modifier.padding(top = Be.Space.m))
                } else if (state.packages.isEmpty()) {
                    PanelCard {
                        Text(
                            text = "还没有模型包\n点击上方「导入 BEM / ZIP」添加模型。",
                            color = Be.Colors.textSecondary,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Be.Space.page),
                        )
                    }
                } else {
                    val targetKeys = state.targetKeys
                    FieldLabel(stringResource(R.string.bem_filter_target))
                    SelectField(
                        options = listOf(stringResource(R.string.bem_all_targets)) + targetKeys.map(state::targetLabel),
                        selectedIndex = (targetKeys.indexOf(state.selectedTarget) + 1).coerceAtLeast(0),
                        onSelect = { state.selectTarget(if (it == 0) "" else targetKeys[it - 1]) },
                    )
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Be.Space.l),
                    ) {
                        state.visiblePackages.forEach { pkg ->
                            PackageCard(
                                pkg = pkg,
                                busy = state.busy,
                                toolsEnabled = state.canTransform,
                                onPreview = { BemPreviewActivity.open(activity, pkg.generation) },
                                onConvertAstc = { astcTarget = pkg },
                                onRemove = { pendingRemoval = pkg },
                            )
                        }
                    }
                }
            }
        }
    }

    astcTarget?.let { pkg ->
        AlertDialog(
            onDismissRequest = { astcTarget = null },
            title = { Text(stringResource(R.string.next_bem_astc_title, pkg.name)) },
            text = { Text(stringResource(R.string.next_bem_astc_confirm)) },
            confirmButton = { TextButton(onClick = {
                state.convertAstc(pkg.generation)
                astcTarget = null
            }) { Text(stringResource(R.string.next_bem_convert_astc)) } },
            dismissButton = { TextButton(onClick = { astcTarget = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    if (repairNormals) {
        AlertDialog(
            onDismissRequest = { repairNormals = false },
            title = { Text(stringResource(R.string.next_bem_repair_normals)) },
            text = { Text(stringResource(R.string.next_bem_repair_confirm)) },
            confirmButton = { TextButton(onClick = {
                state.repairNormals()
                repairNormals = false
            }) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = { repairNormals = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    val target = pendingRemoval
    val bundle = state.pendingBundle
    if (bundle != null) {
        val selected = remember(bundle) { mutableStateListOf<Boolean>().also { list ->
            bundle.packages.forEach { list.add(!bundle.deselected.contains(it.report.optString("generation"))) }
        } }
        AlertDialog(
            onDismissRequest = { state.cancelBundle(bundle) },
            containerColor = Be.Colors.panelHigh,
            titleContentColor = Be.Colors.textPrimary,
            textContentColor = Be.Colors.textSecondary,
            title = { Text("导入 ZIP 中的模型包") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    bundle.packages.forEachIndexed { position, prepared ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selected[position], onCheckedChange = {
                                selected[position] = it
                                val generation = prepared.report.optString("generation")
                                if (it) bundle.deselected.remove(generation) else bundle.deselected.add(generation)
                            })
                            Column(Modifier.weight(1f)) {
                                Text(prepared.report.optString("name", prepared.item.name), color = Be.Colors.textPrimary)
                                Text(prepared.report.optString("target_id", prepared.report.optString("character_id")),
                                    color = Be.Colors.textSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                    if (bundle.issues.isNotEmpty()) {
                        Text("未通过校验：\n" + bundle.issues.joinToString("\n"),
                            color = Be.Colors.danger, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { state.importSelected(bundle, selected.toList()) }) {
                    Text("导入所选", color = Be.Colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { state.cancelBundle(bundle) }) {
                    Text("取消", color = Be.Colors.textSecondary)
                }
            },
        )
    }
    if (discardDraft) {
        AlertDialog(
            onDismissRequest = { discardDraft = false },
            containerColor = Be.Colors.panelHigh,
            title = { Text(stringResource(R.string.bem_reload_latest)) },
            text = { Text(stringResource(R.string.bem_discard_draft_hint)) },
            confirmButton = { TextButton(onClick = {
                state.discardDraftAndReload()
                discardDraft = false
            }) { Text(stringResource(R.string.bem_discard_draft)) } },
            dismissButton = { TextButton(onClick = { discardDraft = false }) {
                Text(stringResource(R.string.bem_keep_draft))
            } },
        )
    }
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            containerColor = Be.Colors.panelHigh,
            titleContentColor = Be.Colors.textPrimary,
            textContentColor = Be.Colors.textSecondary,
            title = { Text("移除「${target.name}」？") },
            text = {
                Text("将删除此模型包的安装文件和配置。下载目录中的原始 BEM 文件会保留，可重新导入。游戏内已加载的模型需重启游戏后恢复。")
            },
            confirmButton = {
                TextButton(onClick = {
                    state.remove(target.generation)
                    pendingRemoval = null
                }) { Text("移除", color = Be.Colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text("取消", color = Be.Colors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun PackageCard(
    pkg: BemPackage,
    busy: Boolean,
    toolsEnabled: Boolean,
    onPreview: () -> Unit,
    onConvertAstc: () -> Unit,
    onRemove: () -> Unit,
) {
    PanelCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pkg.name,
                color = Be.Colors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = pkg.enabled,
                onCheckedChange = { pkg.enabled = it },
                enabled = !busy,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Be.Colors.accent,
                    checkedTrackColor = Be.Colors.accentSoft,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = Be.Colors.switchThumbOff,
                    uncheckedTrackColor = Be.Colors.switchTrackOff,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }

        Text(
            text = if (pkg.convertedTextures) "手机纹理已就绪" else "原始纹理 · 可按需转换",
            color = Be.Colors.textSecondary,
            fontSize = Be.Type.bodySmall,
            modifier = Modifier.padding(top = Be.Space.hairline),
        )

        if (pkg.minorVersion >= 1) {
            pkg.optionGroups.forEach { group ->
                if (pkg.visibleGroups[group.id] != false) {
                    FieldLabel(group.name, top = Be.Space.page)
                    SelectField(
                        options = group.choices.map { it.name },
                        selectedIndex = group.choices
                            .indexOfFirst { it.id == pkg.selectedOption(group.id) }
                            .coerceAtLeast(0),
                        onSelect = { choiceIndex ->
                            pkg.chooseOption(group.id, group.choices[choiceIndex].id)
                        },
                        enabled = !busy,
                    )
                }
            }
        } else {
            FieldLabel("启动外观")
            SelectField(
                options = pkg.appearances,
                selectedIndex = pkg.appearanceIndex,
                onSelect = { pkg.appearanceIndex = it },
                enabled = !busy,
            )
        }

        pkg.parameterGroups.forEach { group ->
            SliderRow(
                label = group.name,
                value = pkg.selectedParameter(group.id).toFloat(),
                onValueChange = { pkg.chooseParameter(group, it.toInt()) },
                valueRange = group.min.toFloat()..group.max.toFloat(),
                steps = (group.max - group.min) / group.step,
                decimals = 0,
                enabled = !busy && pkg.parameterAvailable(group),
                modifier = Modifier.padding(top = Be.Space.m),
            )
        }

        if (pkg.parameterGroups.isNotEmpty()) {
            GhostButton(
                text = stringResource(R.string.next_bem_author_defaults),
                onClick = pkg::resetParameters,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(top = Be.Space.m),
            )
        }
        GhostButton(
            text = stringResource(R.string.next_bem_preview),
            onClick = onPreview,
            enabled = toolsEnabled,
            modifier = Modifier.fillMaxWidth().padding(top = Be.Space.m),
        )
        pkg.problem?.let { message ->
            Text(
                text = message,
                color = Be.Colors.danger,
                fontSize = Be.Type.bodySmall,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = Be.Space.l),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Be.Space.xxl),
            horizontalArrangement = Arrangement.spacedBy(Be.Space.m),
        ) {
            if (!pkg.astcConverted) {
                GhostButton(
                    text = stringResource(R.string.next_bem_convert_astc),
                    onClick = onConvertAstc,
                    enabled = toolsEnabled,
                    modifier = Modifier.weight(1f),
                )
            }
            GhostButton(
                text = "移除模型包",
                onClick = onRemove,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The flat two-tone bar; null draws the indeterminate state. */
@Composable
private fun ProgressBar(fraction: Float?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Be.Colors.track),
    ) {
        val resolved = fraction?.coerceIn(0.004f, 1f) ?: 1f
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(resolved)
                .background(
                    if (fraction == null) Be.Colors.accent.copy(alpha = 0.45f) else Be.Colors.accent,
                ),
        )
    }
}

private fun progressLine(percent: Int, elapsedSeconds: Long): String {
    val phase = if (percent < 0) "正在处理" else "当前 mip 编码：$percent%"
    return "$phase · 已用 ${elapsedSeconds / 60}分${elapsedSeconds % 60}秒"
}
