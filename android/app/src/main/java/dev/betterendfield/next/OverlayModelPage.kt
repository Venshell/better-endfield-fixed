package dev.betterendfield.next

import android.content.Context
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * The in-game model page: which installed model each character wears, plus the
 * global lens override.
 *
 * <p>Every read and write goes through the settings channel
 * ([OverlaySettingsClient] to [OverlaySettingsProvider]) instead of through
 * [ModuleSettings]. The panel runs inside the game process, so the preferences
 * it would be opening belong to the module and are not its to read; more
 * importantly the channel carries a revision, and that is what stops a page left
 * open in the overlay from overwriting a change made in the settings app in the
 * meantime.
 *
 * <p>A switch that quietly does nothing is the failure this page has to avoid,
 * so an unwritable state disables its controls rather than accepting a tap: the
 * channel answered with a revision (this process is allowed to write), no
 * import is running, and no earlier write is still in flight.
 */
@Composable
internal fun OverlayModelPage(preview: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current

    val modelState = remember { OverlayModelCatalogState() }
    var modelVersion by remember { mutableIntStateOf(0) }
    var modelRequest by remember { mutableLongStateOf(0L) }
    val index = remember(modelVersion) { modelState.index() }
    val modelsRevision = remember(modelVersion) { modelState.revision() }
    var importing by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    var target by remember { mutableStateOf("") }

    var fovEnabled by remember { mutableStateOf(false) }
    var fov by remember { mutableStateOf(60f) }
    var fovRevision by remember { mutableStateOf("") }
    var sentFovEnabled by remember { mutableStateOf(false) }
    var sentFov by remember { mutableStateOf(60f) }

    var writing by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }

    fun acceptModels(request: Long, reply: Bundle) {
        if (!modelState.succeed(request, reply.getString("index"), reply.getString("revision"))) return
        modelVersion = modelState.version()
        if (!modelState.canWrite()) return
        importing = reply.getBoolean("busy")
        notice = ""
    }

    fun acceptFov(reply: Bundle) {
        fovRevision = reply.getString("revision", "")
        fovEnabled = reply.getBoolean("enabled")
        fov = readFov(reply.getString("value", "60"))
        sentFovEnabled = fovEnabled
        sentFov = fov
        notice = ""
    }

    fun refreshModels() {
        val request = ++modelRequest
        modelState.beginRead(request)
        OverlaySettingsClient.call(context, "read_models", null, null) { reply ->
            if (reply.getBoolean("ok")) acceptModels(request, reply)
            else {
                if (modelState.fail(request, reply.getString("error")))
                    modelVersion = modelState.version()
            }
        }
    }

    fun refreshFov() {
        OverlaySettingsClient.call(context, "read_fov", null, null) { reply ->
            if (reply.getBoolean("ok")) acceptFov(reply)
            else {
                fovRevision = ""
                notice = reply.getString("error") ?: "设置桥不可用"
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshModels()
        refreshFov()
    }

    /**
     * Writes one model change and re-reads on refusal.
     *
     * <p>The refusal that matters is the revision one: the settings app changed
     * the same list while this page was open. Re-reading is what gives the page
     * a revision it can write with again, instead of leaving every control
     * disabled until the panel is reopened.
     */
    fun writeModels(method: String, patch: String?) {
        if (preview || writing || importing || !modelState.canWrite()) return
        writing = true
        val request = ++modelRequest
        modelState.invalidate(request)
        OverlaySettingsClient.call(context, method, modelsRevision, patch) { reply ->
            writing = false
            if (reply.getBoolean("ok")) acceptModels(request, reply)
            else {
                notice = reply.getString("error") ?: "设置未保存"
                modelState.fail(request, notice)
                modelVersion = modelState.version()
                refreshModels()
            }
        }
    }

    fun writeFov(enabled: Boolean?, value: Float?) {
        if (preview || writing || fovRevision.isEmpty()) return
        val patch = JSONObject()
        if (enabled != null) patch.put("enabled", enabled)
        if (value != null) patch.put("value", value.toDouble())
        writing = true
        notice = ""
        OverlaySettingsClient.call(context, "edit_fov", fovRevision, patch.toString()) { reply ->
            writing = false
            if (reply.getBoolean("ok")) acceptFov(reply)
            else {
                // Roll the previewed value back to the last confirmed one: leaving
                // it on screen would show a number the module never accepted.
                fovEnabled = sentFovEnabled
                fov = sentFov
                notice = reply.getString("error") ?: "设置未保存"
                refreshFov()
            }
        }
    }

    val writable = !preview && !writing && !importing
    val runtimeLive = RuntimeBootstrap.loaded()

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("MODEL", color = Be.Colors.accent, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            Text(if (preview) "模型管理（预览）" else "模型管理",
                color = Be.Colors.textPrimary, fontSize = 18.sp)
        }
        Box(
            Modifier.width(72.dp).height(36.dp)
                .background(Be.Colors.overlayRow, RoundedCornerShape(10.dp))
                .border(1.dp, Be.Colors.outline, RoundedCornerShape(10.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("返回", color = Be.Colors.textPrimary, fontSize = 13.sp)
        }
    }

    OverlaySection("游戏视野") {
        Column(
            Modifier.fillMaxWidth().background(Be.Colors.overlayRow, RoundedCornerShape(12.dp))
                .border(1.dp, Be.Colors.outline, RoundedCornerShape(12.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            OverlaySwitch(
                title = "全局 FOV",
                // The runtime is the only thing that can apply this, and it is
                // loaded once per launch: saying "on the next launch" is the
                // honest wording until it is in.
                detail = when {
                    preview -> "预览模式：不会写入"
                    fovRevision.isEmpty() -> "设置桥不可用"
                    !runtimeLive -> "相机模块未加载，重启后生效"
                    else -> stringResource(R.string.overlay_fov_delivery_hint)
                },
                checked = fovEnabled,
                enabled = writable && fovRevision.isNotEmpty(),
                onChange = { writeFov(enabled = it, value = null) },
            )
            OverlaySlider(
                label = "视场角",
                reading = String.format(Locale.ROOT, "%.0f°", fov),
                value = fov,
                range = 5f..150f,
                steps = 144,
                enabled = writable && fovEnabled && fovRevision.isNotEmpty(),
                onChange = { fov = it },
                onCommit = { writeFov(enabled = null, value = it) },
            )
        }
    }

    ModelDriverControls(preview)

    OverlaySection("已安装模型") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val entries = remember(index) { parseModels(index) }
            if (entries.isEmpty()) {
                Text(
                    if (modelState.hasError() || !modelState.hasCatalog()) modelState.message()
                    else "无已安装模型。可在设置 app 的「模型安装」页导入。",
                    color = Be.Colors.textSecondary, fontSize = 12.sp,
                )
            } else {
                val targets = remember(entries) { entries.map { it.targetKey }.distinct().sorted() }
                // Resolved rather than written back: a target that left the
                // list (its model was removed in the settings app) falls back to
                // "all" for this frame without a state write during composition.
                val filter = if (targets.contains(target)) target else ""
                OverlayActionRow(
                    if (filter.isEmpty()) stringResource(R.string.bem_all_targets) + " ▾" else "${TargetNames.label(context, filter)} ▾",
                    { target = nextTarget(targets, filter) },
                    "关闭全部",
                    { writeModels("disable_models", null) },
                    secondEnabled = writable && modelState.canWrite(),
                )
                entries.filter { filter.isEmpty() || it.targetKey == filter }
                    .forEach { model ->
                        ModelCard(
                            context = context,
                            model = model,
                            open = expanded.contains(model.generation),
                            enabled = writable && modelsRevision.isNotEmpty(),
                            onToggleOpen = {
                                expanded = if (expanded.contains(model.generation)) {
                                    expanded - model.generation
                                } else {
                                    expanded + model.generation
                                }
                            },
                            onEnabled = { writeModels("edit_models", patchOf(model.generation, "enabled", it)) },
                            onAppearance = { writeModels("edit_models", patchOf(model.generation, "appearance", it)) },
                            onOptions = { writeModels("edit_models", patchOf(model.generation, "options", it)) },
                            onParameters = { writeModels("edit_models", patchOf(model.generation, "parameters", it)) },
                        )
                    }
            }
            if (entries.isNotEmpty() && modelState.hasError()) {
                Text(modelState.message(), color = Be.Colors.danger, fontSize = 11.sp)
            }
            if (importing) {
                Text("正在导入模型包，导入完成前不能修改。", color = Be.Colors.textSecondary, fontSize = 11.sp)
            }
            if (notice.isNotEmpty()) {
                Text(notice, color = Be.Colors.danger, fontSize = 11.sp)
            }
        }
    }

    Text(
        if (preview) "预览模式：控件不会写入任何设置。"
        else stringResource(R.string.overlay_model_restart_hint),
        color = Be.Colors.textSecondary, fontSize = 10.sp,
    )
}

/** One installed model, as the channel's index describes it. */
private class OverlayModel(
    val generation: String,
    val targetKey: String,
    val name: String,
    val minor: Int,
    val raw: JSONObject,
) {
    val enabled: Boolean get() = raw.optBoolean("enabled", true)
    val appearances: JSONArray? get() = raw.optJSONArray("appearances")
    val selectedAppearance: String
        get() = raw.optString("selected_appearance", raw.optString("default_appearance"))
}

private fun parseModels(index: String): List<OverlayModel> = try {
    val entries = JSONArray(index)
    (0 until entries.length()).map { position ->
        val entry = entries.getJSONObject(position)
        OverlayModel(
            generation = entry.getString("generation"),
            targetKey = BemOptions.targetKey(entry),
            name = entry.optString("name", entry.getString("generation")),
            minor = entry.optInt("bem_minor", 0),
            raw = entry,
        )
    }
} catch (_: Exception) {
    emptyList()
}

private fun patchOf(generation: String, field: String, value: Any): String =
    JSONObject().put("generation", generation).put(field, value).toString()

private fun readFov(encoded: String): Float =
    ModuleSettings.parse(encoded, 60.0).toFloat().coerceIn(5f, 150f)

/** Cycles the target filter, since a dropdown inside the panel would need a second window. */
private fun nextTarget(targets: List<String>, current: String): String {
    val all = listOf("") + targets
    val at = all.indexOf(current).coerceAtLeast(0)
    return all[(at + 1) % all.size]
}

@Composable
private fun ModelCard(
    context: Context,
    model: OverlayModel,
    open: Boolean,
    enabled: Boolean,
    onToggleOpen: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onAppearance: (String) -> Unit,
    onOptions: (String) -> Unit,
    onParameters: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(Be.Colors.overlayField, RoundedCornerShape(12.dp))
            .border(1.dp, Be.Colors.outline, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OverlaySwitch(
            title = model.name,
            detail = TargetNames.label(context, model.targetKey) +
                " · " + stringResource(R.string.overlay_model_restart_detail),
            checked = model.enabled,
            enabled = enabled,
            onChange = onEnabled,
        )
        OverlayAction(if (open) "收起详细选项 ▴" else "详细选项 ▾", onClick = onToggleOpen)
        if (!open) return@Column

        if (model.minor < 1) {
            val appearances = model.appearances
            val options = (0 until (appearances?.length() ?: 0)).map { appearances!!.getString(it) }
            OverlayPicker("外观", options,
                options.indexOf(model.selectedAppearance).coerceAtLeast(0), enabled) { picked ->
                onAppearance(options[picked])
            }
            return@Column
        }

        val selections: Map<String, String> = try {
            BemOptions.parse(model.raw, model.raw.optString("selected_options",
                model.raw.getString("default_options")))
        } catch (_: Exception) {
            emptyMap()
        }
        val reachable: Map<String, String> = try {
            BemOptions.effective(model.raw, selections)
        } catch (_: Exception) {
            emptyMap()
        }
        val groups = model.raw.optJSONArray("option_groups") ?: JSONArray()
        for (position in 0 until groups.length()) {
            val group = groups.getJSONObject(position)
            val id = group.getString("id")
            if (!reachable.containsKey(id)) continue
            val choices = group.getJSONArray("choices")
            val ids = (0 until choices.length()).map { choices.getJSONObject(it).getString("id") }
            val labels = (0 until choices.length()).map { choices.getJSONObject(it).optString("name") }
            OverlayPicker(group.optString("name", id), labels,
                ids.indexOf(selections[id] ?: "").coerceAtLeast(0), enabled) { picked -> onOptions(encodeOption(model, id, ids[picked])) }
        }

        val parameters = try {
            BemParameters.groups(model.raw)
        } catch (_: Exception) {
            JSONArray()
        }
        val values: Map<String, Int> = try {
            BemParameters.parse(model.raw, model.raw.optString("selected_parameters",
                model.raw.optString("default_parameters", "")))
        } catch (_: Exception) {
            emptyMap()
        }
        for (position in 0 until parameters.length()) {
            val parameter = parameters.getJSONObject(position)
            val available = try {
                BemParameters.available(model.raw, parameter, selections)
            } catch (_: Exception) {
                false
            }
            if (!available) continue
            val id = parameter.getString("id")
            val min = BemParameters.tick(parameter, "min")
            val max = BemParameters.tick(parameter, "max")
            val step = BemParameters.tick(parameter, "step").coerceAtLeast(1)
            val current = (values[id] ?: min).coerceIn(min, max)
            var draft by remember(model.generation, id, current) { mutableStateOf(current.toFloat()) }
            OverlaySlider(
                label = parameter.optString("name", id),
                // Raw ticks, the unit the settings screen shows for the same
                // parameter: the two surfaces edit one value, and showing it in
                // two scales would read as two different settings.
                reading = String.format(Locale.ROOT, "%.0f", draft),
                value = draft,
                range = min.toFloat()..max.toFloat(),
                steps = ((max - min) / step - 1).coerceAtLeast(0),
                enabled = enabled,
                onChange = { draft = it },
                onCommit = { committed ->
                    draft = committed
                    onParameters(encodeParameters(model, id, committed.toInt()))
                },
            )
        }
        if (parameters.length() > 0) {
            OverlayAction("作者默认值") {
                onParameters(model.raw.optString("default_parameters", ""))
            }
        }
    }
}

private fun encodeOption(model: OverlayModel, group: String, choice: String): String = try {
    val current = BemOptions.parse(model.raw, model.raw.optString("selected_options",
        model.raw.getString("default_options")))
    val next = LinkedHashMap(current)
    next[group] = choice
    // Round-tripped through the package's own validation: a combination the
    // package cannot reach is rejected here, in the page, rather than by the
    // channel as a save failure the user cannot act on.
    BemOptions.encode(BemOptions.parse(model.raw, BemOptions.encode(next)))
} catch (_: Exception) {
    model.raw.optString("selected_options", "")
}

private fun encodeParameters(model: OverlayModel, parameter: String, value: Int): String = try {
    val current = BemParameters.parse(model.raw, model.raw.optString("selected_parameters",
        model.raw.optString("default_parameters", "")))
    val next = LinkedHashMap(current)
    next[parameter] = value
    BemParameters.encode(next)
} catch (_: Exception) {
    model.raw.optString("selected_parameters", "")
}

@Composable
private fun OverlaySwitch(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().alphaIf(enabled),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Be.Colors.textPrimary, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = Be.Colors.textSecondary, fontSize = 10.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
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
}

/** A labelled picker that opens in place; the panel is too narrow for a second window. */
@Composable
private fun OverlayPicker(
    label: String,
    options: List<String>,
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    if (options.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val safe = selected.coerceIn(0, options.size - 1)
    Column(Modifier.fillMaxWidth().alphaIf(enabled)) {
        Text(label, color = Be.Colors.textSecondary, fontSize = 10.sp)
        Box {
            Row(
                Modifier.fillMaxWidth().height(40.dp)
                    .background(Be.Colors.overlayRow, RoundedCornerShape(11.dp))
                    .border(1.dp, Be.Colors.outline, RoundedCornerShape(11.dp))
                    .clickable(enabled = enabled) { open = true }
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(options[safe], color = Be.Colors.textPrimary, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("▾", color = Be.Colors.accent, fontSize = 12.sp)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false },
                containerColor = Be.Colors.overlayPanel) {
                options.forEachIndexed { position, option ->
                    DropdownMenuItem(
                        text = {
                            Text(option,
                                color = if (position == safe) Be.Colors.accent else Be.Colors.textPrimary,
                                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        onClick = {
                            open = false
                            if (position != safe) onSelect(position)
                        },
                    )
                }
            }
        }
    }
}

/**
 * A labelled slider that reports while dragging and commits once, on release.
 *
 * <p>One commit per gesture is deliberate: every step used to be its own write
 * and its own published snapshot, and the settings screen already pays that cost
 * for its own slider. Here the finger is over a game, so the value only has to
 * reach the module when the hand stops.
 */
@Composable
private fun OverlaySlider(
    label: String,
    reading: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    onCommit: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().alphaIf(enabled)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Be.Colors.textSecondary, fontSize = 10.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(reading, color = Be.Colors.accent, fontSize = 11.sp)
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            onValueChangeFinished = { onCommit(value.coerceIn(range)) },
            valueRange = range,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Be.Colors.accent,
                activeTrackColor = Be.Colors.accent,
                activeTickColor = Color.Transparent,
                inactiveTrackColor = Be.Colors.track,
                inactiveTickColor = Color.Transparent,
                disabledThumbColor = Be.Colors.accent,
                disabledActiveTrackColor = Be.Colors.accent,
                disabledInactiveTrackColor = Be.Colors.track,
            ),
            modifier = Modifier.fillMaxWidth().height(32.dp),
        )
    }
}

private fun Modifier.alphaIf(enabled: Boolean): Modifier =
    if (enabled) this else this.alpha(0.42f)

/**
 * Character ids are internal (`chr_0002_...`); the display names ship as an
 * asset of the module.
 *
 * <p>The panel runs in the game process, so it cannot read that asset from its
 * own context. It opens the module's package context instead - the same route
 * the runtime bootstrap takes for the module's other assets. A failure falls
 * back to the id, which is what this page would show without names at all.
 */
private object TargetNames {
    @Volatile private var table: JSONObject? = null

    fun label(context: Context, key: String): String {
        if (key.isEmpty()) return context.getString(R.string.bem_all_targets)
        val id = BemOptions.targetLabel(key)
        val names = table ?: load(context)?.also { table = it } ?: return id
        return names.optString(id, id)
    }

    private fun load(context: Context): JSONObject? = try {
        val module = context.createPackageContext(RuntimeBootstrap.MODULE_PACKAGE,
            Context.CONTEXT_IGNORE_SECURITY)
        JSONObject(module.assets.open("character-names.json").bufferedReader().use { it.readText() })
    } catch (_: Exception) {
        null
    }
}
