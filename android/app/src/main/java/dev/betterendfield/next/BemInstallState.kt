package dev.betterendfield.next

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** One finite option group inside a BEM 1.1 package. */
class BemOptionGroup(
    val id: String,
    val name: String,
    val choices: List<Choice>,
) {
    class Choice(val id: String, val name: String)
}

class BemParameterGroup(val raw: JSONObject) {
    val id: String = raw.getString("id")
    val name: String = raw.getString("name")
    val min: Int = BemParameters.tick(raw, "min")
    val max: Int = BemParameters.tick(raw, "max")
    val step: Int = BemParameters.tick(raw, "step")
}

/**
 * One installed package, as the management screen edits it.
 *
 * The selection is kept in the same encoded `group:choice&group:choice` form the
 * installer and the native reader use, so validating a change is the same call
 * the save path makes rather than a second, parallel implementation of the
 * reachability rules.
 */
class BemPackage(
    val generation: String,
    val targetKey: String,
    val name: String,
    val textureMode: String,
    val minorVersion: Int,
    val appearances: List<String>,
    val optionGroups: List<BemOptionGroup>,
    val parameterGroups: List<BemParameterGroup>,
    val raw: JSONObject,
) {
    var enabled by mutableStateOf(true)
    var appearanceIndex by mutableStateOf(0)
    var options by mutableStateOf("")
    var parameters by mutableStateOf("")
    var problem by mutableStateOf<String?>(null)
    val visibleGroups = mutableStateMapOf<String, Boolean>()

    val convertedTextures: Boolean get() = textureMode != "original"

    val astcConverted: Boolean get() = textureMode == "astc" || raw.optBoolean("astc_converted", false)

    fun resetParameters() {
        try {
            parameters = BemParameters.encode(BemParameters.parse(raw, ""))
            problem = null
        } catch (error: Exception) {
            problem = error.message
        }
    }

    /** What this package currently claims it should be saved as. */
    fun draft(): JSONObject = JSONObject().apply {
        put("generation", generation)
        put("enabled", enabled)
        if (minorVersion >= 1) put("options", options) else put("appearance", appearances[appearanceIndex])
        if (minorVersion >= 3) put("parameters", parameters)
    }

    fun selections(): LinkedHashMap<String, String> = LinkedHashMap(BemOptions.parse(raw, options))

    /**
     * Applies a choice, refusing combinations the package cannot reach. A
     * refusal reverts the picker and explains itself instead of leaving a
     * selection the native reader would reinterpret.
     */
    fun chooseOption(groupId: String, choiceId: String) {
        val candidate = selections().also { it[groupId] = choiceId }
        try {
            if (!BemOptions.valid(raw, candidate)) {
                problem = "这个选项组合在包内不可达，请选择其他组合。"
                return
            }
            options = BemOptions.encode(candidate)
            problem = null
            refreshVisibility(candidate)
        } catch (error: Exception) {
            problem = "选项错误：" + error.message
        }
    }

    fun refreshVisibility(active: Map<String, String> = selections()) {
        val effective = try {
            BemOptions.effective(raw, active)
        } catch (error: Exception) {
            problem = "选项错误：" + error.message
            return
        }
        optionGroups.forEach { group ->
            visibleGroups[group.id] = effective.containsKey(group.id)
        }
    }

    fun selectedOption(groupId: String): String = selections()[groupId] ?: ""

    fun selectedAppearance(): String = appearances.getOrElse(appearanceIndex) { "" }

    fun selectedParameter(id: String): Int = BemParameters.parse(raw, parameters)[id] ?: 0

    fun chooseParameter(group: BemParameterGroup, value: Int) {
        try {
            val selected = BemParameters.parse(raw, parameters)
            selected[group.id] = BemParameters.snap(group.raw, value)
            parameters = BemParameters.encode(selected)
            problem = null
        } catch (error: Exception) {
            problem = "滑条设置错误：" + error.message
        }
    }

    fun parameterAvailable(group: BemParameterGroup): Boolean = try {
        BemParameters.available(raw, group.raw, selections())
    } catch (_: Exception) { false }
}

/**
 * The BEM page's state.
 *
 * The installer reports progress through static fields because it runs on its own
 * worker and can outlive this screen; polling them on the main thread is what
 * keeps a long texture conversion observable while the user navigates away and
 * back. The index is only re-parsed when its serialized form changes, so a live
 * option picker is never rebuilt underneath the finger that is using it.
 */
class BemInstallState(private val context: Context) {

    var status by mutableStateOf(BemInstaller.status)
        private set
    var busy by mutableStateOf(false)
        private set
    internal var pendingBundle by mutableStateOf<BemInstaller.PendingBundle?>(null)
        private set
    internal var pendingExternalUri by mutableStateOf<android.net.Uri?>(null)
        private set
    var removing by mutableStateOf(false)
        private set
    var percent by mutableStateOf(-1)
        private set
    var elapsedSeconds by mutableStateOf(0L)
        private set
    var packages by mutableStateOf<List<BemPackage>>(emptyList())
        private set
    var indexBroken by mutableStateOf<String?>(null)
        private set
    var selectedTarget by mutableStateOf("")
        private set
    var keepLocalCopies by mutableStateOf(FrameworkSettings.open(context).getBoolean(BemInstaller.KEEP_LOCAL_COPIES, true))
        private set
    private val characterNames: JSONObject = try {
        context.assets.open("character-names.json").bufferedReader().use { JSONObject(it.readText()) }
    } catch (_: Exception) { JSONObject() }

    val targetKeys: List<String> get() = packages.map { it.targetKey }.distinct().sorted()
    val visiblePackages: List<BemPackage> get() = packages.filter { selectedTarget.isEmpty() || it.targetKey == selectedTarget }
    fun targetLabel(key: String): String {
        val label = BemOptions.targetLabel(key)
        return characterNames.optString(label, label)
    }
    fun selectTarget(id: String) { selectedTarget = id }

    val experiments = listOf(
        BemInstaller.HOT_SWITCH to R.string.next_bem_hot_switch,
        BemInstaller.FAST_LOADING to R.string.next_bem_fast_loading,
        BemInstaller.SKIP_VALIDATION to R.string.next_bem_skip_validation,
        BemInstaller.CLONE_SUPPORT to R.string.next_bem_clone_support,
        BemInstaller.RELEASE_RETIRED_RESOURCES to R.string.next_bem_release_retired,
    )
    val experimentValues = mutableStateMapOf<String, Boolean>()
    private var savedSelections: Map<String, String> = emptyMap()
    val hasDraft: Boolean get() = selections() != savedSelections
    val canTransform: Boolean get() = !busy && !indexConflict && indexBroken == null && !hasDraft

    fun changeExperiment(key: String, enabled: Boolean) {
        if (busy || experiments.none { it.first == key }) return
        if (FrameworkSettings.open(context).edit().putBoolean(key, enabled).commit()) {
            experimentValues[key] = enabled
        } else {
            BemInstaller.status = context.getString(R.string.next_bem_setting_failed)
            status = BemInstaller.status
        }
    }

    private val draftSession = BemIndexRevision.Draft()
    var indexConflict by mutableStateOf(false)
        private set
    private fun selections(): Map<String, String> = packages.associate { it.generation to it.draft().toString() }

    fun refresh() {
        status = BemInstaller.status
        val prefs = FrameworkSettings.open(context)
        experiments.forEach { (key, _) -> experimentValues[key] = prefs.getBoolean(key, false) }
        busy = BemInstaller.busy
        pendingBundle = BemInstaller.pendingBundle
        removing = BemInstaller.removing
        percent = BemInstaller.progressPercent
        elapsedSeconds = if (BemInstaller.busy) {
            (SystemClock.elapsedRealtime() - BemInstaller.startedAt) / 1000L
        } else {
            0L
        }
        keepLocalCopies = FrameworkSettings.open(context).getBoolean(BemInstaller.KEEP_LOCAL_COPIES, true)
        try {
            val snapshot = BemInstaller.snapshot(context)
            when (draftSession.refresh(snapshot.revision, selections())) {
                BemIndexRevision.Draft.Refresh.LOAD_LATEST -> accept(snapshot)
                BemIndexRevision.Draft.Refresh.KEEP_DRAFT -> indexConflict = true
                BemIndexRevision.Draft.Refresh.UNCHANGED -> {
                    indexConflict = false
                    indexBroken = null
                }
            }
        } catch (error: Exception) {
            indexBroken = context.getString(R.string.bem_index_read_failed, error.message ?: "")
        }
    }

    private fun accept(snapshot: BemInstaller.IndexSnapshot) {
        val parsed = JSONArray(snapshot.index)
        val list = (0 until parsed.length()).map { position -> parse(parsed.getJSONObject(position)) }
        packages = list
        savedSelections = selections()
        draftSession.accept(snapshot.revision, savedSelections)
        indexConflict = false
        indexBroken = null
        if (selectedTarget.isNotEmpty() && list.none { it.targetKey == selectedTarget }) selectedTarget = ""
    }

    /** Called only after the user confirms discarding the retained draft. */
    fun discardDraftAndReload() {
        try { accept(BemInstaller.snapshot(context)) }
        catch (error: Exception) {
            indexBroken = context.getString(R.string.bem_index_read_failed, error.message ?: "")
        }
    }

    private fun parse(entry: JSONObject): BemPackage {
        val generation = entry.getString("generation")
        val minor = entry.optInt("bem_minor", 0)
        val appearances = buildList {
            val array = entry.optJSONArray("appearances") ?: JSONArray()
            for (index in 0 until array.length()) add(array.getString(index))
        }
        val groups = buildList {
            if (minor < 1) return@buildList
            val array = entry.getJSONArray("option_groups")
            for (index in 0 until array.length()) {
                val group = array.getJSONObject(index)
                val choices = group.getJSONArray("choices")
                add(
                    BemOptionGroup(
                        id = group.getString("id"),
                        name = group.getString("name"),
                        choices = (0 until choices.length()).map { choiceIndex ->
                            val choice = choices.getJSONObject(choiceIndex)
                            BemOptionGroup.Choice(choice.getString("id"), choice.getString("name"))
                        },
                    ),
                )
            }
        }
        val parameterGroups = buildList {
            val array = BemParameters.groups(entry)
            for (index in 0 until array.length()) add(BemParameterGroup(array.getJSONObject(index)))
        }

        val active = if (minor >= 1) {
            entry.optString("selected_options", entry.getString("default_options"))
        } else {
            entry.optString("selected_appearance", entry.optString("default_appearance"))
        }

        val pkg = BemPackage(
            generation = generation,
            targetKey = BemOptions.targetKey(entry),
            name = entry.getString("name"),
            textureMode = entry.optString("texture_mode", "converted"),
            minorVersion = minor,
            appearances = appearances,
            optionGroups = groups,
            parameterGroups = parameterGroups,
            raw = entry,
        )

        if (minor >= 1) {
            // A stored selection can go stale when the package is re-imported with
            // a group removed; falling back to the package default keeps the screen
            // usable instead of throwing on open.
            pkg.options = try {
                BemOptions.encode(BemOptions.parse(entry, active))
            } catch (stale: Exception) {
                entry.getString("default_options")
            }
            pkg.refreshVisibility()
        } else {
            val stored = entry.optString("selected_appearance", entry.optString("default_appearance"))
            pkg.appearanceIndex = appearances.indexOfFirst { it == stored }.takeIf { it >= 0 } ?: 0
        }

        if (minor >= 3) {
            val saved = entry.optString("selected_parameters", entry.optString("default_parameters", ""))
            pkg.parameters = try {
                BemParameters.encode(BemParameters.parse(entry, saved))
            } catch (_: Exception) {
                BemParameters.encode(BemParameters.parse(entry, entry.optString("default_parameters", "")))
            }
        }

        pkg.enabled = entry.optBoolean("enabled", true)
        return pkg
    }

    fun saveAll(): Boolean = try {
        if (indexConflict) throw BemInstaller.IndexConflict(context)
        val changes = JSONArray()
        packages.forEach { changes.put(it.draft()) }
        accept(BemInstaller.saveAll(context, draftSession.revision(), changes))
        BemInstaller.status = context.getString(R.string.bem_selection_saved)
        status = BemInstaller.status
        true
    } catch (error: Exception) {
        if (error is BemInstaller.IndexConflict) indexConflict = true
        BemInstaller.status = context.getString(R.string.bem_selection_failed, error.message ?: "")
        status = BemInstaller.status
        false
    }

    fun disableAll(): Boolean = try {
        if (indexConflict) throw BemInstaller.IndexConflict(context)
        val pending = packages.associateBy { it.generation }
        accept(BemInstaller.disableAll(context, draftSession.revision()))
        // Disable changes only the durable switches; retain unsaved option/slider edits.
        packages.forEach { pkg -> pending[pkg.generation]?.let { old ->
            pkg.appearanceIndex = old.appearanceIndex
            pkg.options = old.options
            pkg.parameters = old.parameters
            if (pkg.minorVersion >= 1) pkg.refreshVisibility()
        } }
        BemInstaller.status = context.getString(R.string.bem_disable_all_saved)
        status = BemInstaller.status
        true
    } catch (error: Exception) {
        if (error is BemInstaller.IndexConflict) indexConflict = true
        BemInstaller.status = context.getString(R.string.bem_disable_all_failed, error.message ?: "")
        status = BemInstaller.status
        false
    }

    fun cleanUnused() {
        if (!BemInstaller.cleanUnused(context)) status = context.getString(R.string.bem_operation_busy)
    }

    fun changeLocalCopies(keep: Boolean) {
        if (busy) return
        val settings = FrameworkSettings.open(context)
        if (!settings.edit().putBoolean(BemInstaller.KEEP_LOCAL_COPIES, keep).commit()) {
            status = context.getString(R.string.bem_storage_setting_failed)
            return
        }
        if (!BemInstaller.applyLocalCopies(context, keep)) {
            settings.edit().putBoolean(BemInstaller.KEEP_LOCAL_COPIES, !keep).commit()
            status = context.getString(R.string.bem_operation_busy)
            return
        }
        keepLocalCopies = keep
        refresh()
    }

    fun convert(generation: String) {
        if (!canTransform) return
        BemInstaller.convert(context, generation)
        refresh()
    }

    fun convertAstc(generation: String) {
        if (!canTransform) return
        if (!BemInstaller.convertToAstc(context, generation)) status = context.getString(R.string.bem_operation_busy)
        refresh()
    }

    fun repairNormals() {
        if (!canTransform) return
        if (!BemInstaller.repairAllInstalledNormals(context)) status = context.getString(R.string.bem_operation_busy)
        refresh()
    }

    fun remove(generation: String) {
        try {
            BemInstaller.remove(context, generation)
        } catch (error: Exception) {
            BemInstaller.status = error.message ?: "移除失败"
            status = BemInstaller.status
        }
    }

    fun cancel() {
        BemInstaller.cancel()
    }

    fun import(uri: android.net.Uri) {
        if (BemInstaller.start(context, uri)) pendingExternalUri = null
    }

    internal fun importSelected(bundle: BemInstaller.PendingBundle, selected: List<Boolean>) {
        BemInstaller.importSelected(bundle, BooleanArray(selected.size) { selected[it] })
        refresh()
    }

    internal fun cancelBundle(bundle: BemInstaller.PendingBundle) {
        BemInstaller.cancelBundle(bundle)
        refresh()
    }

    fun startFromViewIntent(uri: android.net.Uri) {
        if (BemInstaller.start(context, uri)) pendingExternalUri = null
        else pendingExternalUri = uri
    }

    internal fun restorePendingExternalUri(uri: android.net.Uri) {
        pendingExternalUri = uri
    }

    internal fun retryPendingExternalImport() {
        pendingExternalUri?.let { startFromViewIntent(it) }
    }
}
