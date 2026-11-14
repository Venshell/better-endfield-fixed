package dev.betterendfield.next

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The native loader compares a 30-byte VMD header; the import check mirrors it. */
private const val VMD_HEADER_BYTES = 30
private const val VMD_TIME_PATTERN = "yyyy-MM-dd HH:mm"

/**
 * One installed library work, as the page presents it.
 *
 * Deliberately not the stored record: the page needs a name to show and a
 * footprint to describe, while the index also carries the remote names and the
 * per-file lengths, which are the installer's and the game process's business.
 * A page that held the record could start making decisions from fields it has no
 * reason to understand.
 *
 * [generation] is the folder name under the game's library directory and the
 * identity a selection is stored as, so it is what the buttons act on.
 */
data class MmdWorkRow(
    val generation: String,
    val name: String,
    val files: Int,
    val bytes: Long,
)

/**
 * Page indices for the settings tree.
 *
 * Four tabs, and sub-pages that are reached from a card inside a tab. Both
 * kinds are addressed by the same integer because the shell switches on one
 * value; [parentOf] is what keeps the tab strip highlighted while a sub-page is
 * open, so "which tab am I in" stays answerable on every screen.
 *
 * The sub-page ids start well above the tab ids so [isSubPage] is a range check
 * rather than a second table to keep in sync.
 */
object SettingsPage {
    /* --- tabs, in the order they are shown --- */

    const val HOME = 0
    const val EXPERIENCE = 1
    const val CHARACTERS = 2
    const val TOOLS = 3

    val TAB_ORDER = intArrayOf(HOME, EXPERIENCE, CHARACTERS, TOOLS)

    val TAB_LABELS = intArrayOf(
        R.string.page_home,
        R.string.page_experience,
        R.string.page_characters,
        R.string.page_tools,
    )

    /* --- sub-pages, reached from a card --- */

    const val FIRST_PERSON = 10
    const val APPEARANCE = 11
    const val LOG = 12
    const val ABOUT = 13
    const val CAMERA_MOTION = 14
    const val MMD = 15
    const val ADMINISTRATOR_APPEARANCE = 16
    const val WORKSHOP = 17

    fun isSubPage(page: Int): Boolean = page >= FIRST_PERSON

    /** The tab a page belongs to, so a sub-page keeps its parent highlighted. */
    fun parentOf(page: Int): Int = when (page) {
        FIRST_PERSON, CAMERA_MOTION, MMD -> EXPERIENCE
        APPEARANCE, ADMINISTRATOR_APPEARANCE -> CHARACTERS
        LOG, ABOUT, WORKSHOP -> TOOLS
        else -> page
    }
}

/** The per-character language choices, in the order the desktop parser expects. */
val LANGUAGE_VALUES = arrayOf("FollowGlobal", "Chinese", "English", "Japanese", "Korean")
val LANGUAGE_LABELS = listOf("跟随游戏", "中文", "English", "日本語", "한국어")

/**
 * Every setting the companion app can change, held as Compose state.
 *
 * One holder rather than a view model per page: the pages cross-reference each
 * other (the diagnostics page reports what the enhancement page selected, and
 * the enhancement page's switches gate each other), and `ModuleSettings` is
 * itself a single flat preference store. Keeping the reads and writes in one
 * place is what makes "what will the game load" answerable in one pass.
 */
class SettingsState(private val context: Context) {

    var page by mutableStateOf(SettingsPage.HOME)
        private set

    var status by mutableStateOf("")
        private set

    /**
     * How many settings have been written since this screen was opened.
     *
     * Every write lands in the framework snapshot the game process reads on its
     * next start, so "changes are pending" is knowable here; which ones the
     * *running* process has already picked up is not, and the home page says so
     * in exactly those terms instead of guessing at a restart.
     */
    var pendingChanges by mutableStateOf(0)
        private set

    val restartPending: Boolean get() = pendingChanges > 0

    private fun markPending() {
        pendingChanges++
    }

    fun openPage(index: Int) {
        page = index
        // Both the tools tab and the log page report the snapshot the game will
        // load next, which any other page can have changed since it was built.
        if (index == SettingsPage.TOOLS || index == SettingsPage.LOG) refreshDiagnostics()
        if (index == SettingsPage.HOME) refreshHome()
    }

    fun markAdministratorAppearanceSaved() {
        markPending()
        refreshHome()
    }

    /** Returns from a sub-page to the tab that owns it. */
    fun back() {
        if (SettingsPage.isSubPage(page)) openPage(SettingsPage.parentOf(page))
    }

    fun restorePage(index: Int) {
        page = index
    }

    /* ----------------------------------------------------------------- 开屏模型 */

    private var modelIndex: ModelPresetIndex? = null
    private var modelLoadError: String? = null

    var modelTableStatus by mutableStateOf("")
        private set
    var modelSelectionStatus by mutableStateOf("")
        private set

    /**
     * The preset table itself stays private; the screen only ever needs the two
     * display strings, and `ModulePresetIndex.Character` is deliberately not part
     * of this class's surface.
     */
    private var characters: List<ModelPresetIndex.Character> = emptyList()
    private var actions: List<ModelPresetIndex.Action> = emptyList()

    var characterNames by mutableStateOf<List<String>>(emptyList())
        private set
    var actionIds by mutableStateOf<List<String>>(emptyList())
        private set
    var characterIndex by mutableStateOf(0)
        private set
    var actionIndex by mutableStateOf(0)
        private set

    var modelEnabled by mutableStateOf(false)
        private set
    var modelFinalLoop by mutableStateOf(true)
        private set
    var modelForceLoop by mutableStateOf(false)
        private set
    var modelCrossfade by mutableStateOf(false)
        private set
    var logoEnabled by mutableStateOf(false)
        private set

    var modelScale by mutableStateOf("1.0")
        private set
    var modelLoopStart by mutableStateOf("0.968")
        private set
    var modelLoopEnd by mutableStateOf("2.3760002")
        private set
    var modelCrossfadeDuration by mutableStateOf("0.20")
        private set
    var logoColor by mutableStateOf("#FFC928")
        private set

    /** The colour the wheel shows; kept separate so a half-typed hex does not reset it. */
    var logoWheelArgb by mutableStateOf(0x00FFC928)
        private set

    /** False when the desktop preset table is missing, which disables the page. */
    var modelAvailable by mutableStateOf(true)
        private set

    private fun loadModelPresets() {
        modelIndex = try {
            ModelPresetIndex.load(context)
        } catch (error: Exception) {
            modelLoadError = error.message
            null
        }
        val index = modelIndex
        if (index == null) {
            modelAvailable = false
            modelTableStatus = context.getString(R.string.model_table_failed, modelLoadError ?: "")
            return
        }
        characters = index.characters()
        characterNames = characters.map { it.displayName() }
        modelEnabled = ModuleSettings.isModelEnabled(context)
        modelFinalLoop = ModuleSettings.isModelFinalLoop(context)
        modelForceLoop = ModuleSettings.isModelForceLoop(context)
        modelCrossfade = ModuleSettings.isModelCrossfade(context)
        if (modelCrossfade) modelFinalLoop = true
        logoEnabled = ModuleSettings.isLogoEnabled(context)
        modelScale = ModuleSettings.getModelScale(context)
        modelLoopStart = ModuleSettings.getModelLoopStart(context)
        modelLoopEnd = ModuleSettings.getModelLoopEnd(context)
        modelCrossfadeDuration = ModuleSettings.getModelCrossfadeDuration(context)
        logoColor = ModuleSettings.getLogoColor(context)
        logoWheelArgb = logoColor.removePrefix("#").trim().toIntOrNull(16) ?: 0x00FFC928
        characterIndex = findCharacterPosition(ModuleSettings.getModelCharacter(context))
        refreshActionOptions(ModuleSettings.getModelAction(context))
        updateModelSelectionStatus()
        modelTableStatus = context.getString(
            R.string.model_table_ready,
            characters.size,
            index.actionCount(),
        )
    }

    private fun findCharacterPosition(id: String): Int =
        characters.indexOfFirst { it.id().equals(id, ignoreCase = true) }
            .takeIf { it >= 0 }
            ?: characters.indexOfFirst { "chr_0013_aglina".equals(it.id(), ignoreCase = true) }
                .takeIf { it >= 0 }
            ?: 0

    /** Reloads the action list for the selected character, keeping [preferred] if it exists. */
    private fun refreshActionOptions(preferred: String) {
        val character = characters.getOrNull(characterIndex) ?: return
        actions = character.actions()
        actionIds = actions.map { it.id() }
        val desired = preferred.ifEmpty { character.defaultActionId() }
        actionIndex = actions.indexOfFirst { it.id().equals(desired, ignoreCase = true) }
            .takeIf { it >= 0 } ?: 0
        updateModelSelectionStatus()
    }

    private fun updateModelSelectionStatus() {
        val character = characters.getOrNull(characterIndex)
        val action = actions.getOrNull(actionIndex)
        if (character != null && action != null) {
            modelSelectionStatus = context.getString(
                R.string.model_selection_ready,
                character.id(),
                action.id(),
            )
        }
    }

    fun selectCharacter(index: Int) {
        if (index == characterIndex) return
        characterIndex = index
        refreshActionOptions("")
        saveModelSettings()
    }

    fun selectAction(index: Int) {
        if (index == actionIndex) return
        actionIndex = index
        saveModelSettings()
    }

    fun updateModelEnabled(value: Boolean) {
        modelEnabled = value
        saveModelSettings()
    }

    fun updateModelFinalLoop(value: Boolean) {
        modelFinalLoop = value
        // Crossfading has nothing to blend into without a loop, so turning the
        // loop off has to turn the blend off with it rather than leave a switch
        // that silently does nothing.
        if (!value) modelCrossfade = false
        saveModelSettings()
    }

    fun updateModelForceLoop(value: Boolean) {
        modelForceLoop = value
        saveModelSettings()
    }

    fun updateModelCrossfade(value: Boolean) {
        modelCrossfade = value
        if (value) modelFinalLoop = true
        saveModelSettings()
    }

    fun updateLogoEnabled(value: Boolean) {
        logoEnabled = value
        saveModelSettings()
    }

    fun updateModelScale(value: String) {
        modelScale = value
    }

    fun updateModelLoopStart(value: String) {
        modelLoopStart = value
    }

    fun updateModelLoopEnd(value: String) {
        modelLoopEnd = value
    }

    fun updateModelCrossfadeDuration(value: String) {
        modelCrossfadeDuration = value
    }

    fun updateLogoColor(value: String) {
        logoColor = value
        logoColor.removePrefix("#").trim().toIntOrNull(16)?.let { logoWheelArgb = it and 0xFFFFFF }
    }

    /** Called by the wheel while dragging and once more when the finger lifts. */
    fun onWheelColorChanged(argb: Int, committed: Boolean) {
        val masked = argb and 0xFFFFFF
        logoWheelArgb = masked
        logoColor = String.format(Locale.ROOT, "#%06X", masked)
        if (committed) saveModelSettings()
    }

    fun choosePaletteColor(hex: String) {
        logoColor = hex
        hex.removePrefix("#").toIntOrNull(16)?.let { logoWheelArgb = it and 0xFFFFFF }
        saveModelSettings()
    }

    fun saveModelSettings() {
        if (modelIndex == null) return
        val character = characters.getOrNull(characterIndex) ?: return
        val action = actions.getOrNull(actionIndex) ?: return

        val scale = modelScale.trim().toDoubleOrNull()
        if (scale == null || !scale.isFinite() || scale < 0.05 || scale > 20.0) {
            modelSelectionStatus = context.getString(
                R.string.model_settings_invalid,
                "模型缩放应为 0.05–20",
            )
            return
        }

        val loopStart = modelLoopStart.trim().toDoubleOrNull()
        val loopEnd = modelLoopEnd.trim().toDoubleOrNull()
        val crossfadeDuration = modelCrossfadeDuration.trim().toDoubleOrNull()
        if (loopStart == null || loopEnd == null || crossfadeDuration == null ||
            !loopStart.isFinite() || loopStart < 0.0 || loopStart > 30.0 ||
            !loopEnd.isFinite() || loopEnd < 0.05 || loopEnd > 60.0 ||
            loopEnd < loopStart + 0.05 ||
            !crossfadeDuration.isFinite() || crossfadeDuration < 0.01 || crossfadeDuration > 10.0 ||
            crossfadeDuration > (loopEnd - loopStart) * 0.5
        ) {
            modelSelectionStatus = context.getString(
                R.string.model_settings_invalid,
                "循环区间或混合时长无效",
            )
            return
        }

        val color = logoColor.trim().uppercase(Locale.ROOT)
        if (!color.matches(HEX_COLOR)) {
            modelSelectionStatus = context.getString(
                R.string.model_settings_invalid,
                "主题色应为 #RRGGBB",
            )
            return
        }
        logoColor = color
        color.removePrefix("#").toIntOrNull(16)?.let { logoWheelArgb = it }

        val configuration = if (modelEnabled || logoEnabled) {
            buildModelConfiguration(character, action, scale, color, loopStart, loopEnd, crossfadeDuration)
        } else {
            ""
        }
        ModuleSettings.setModelSettings(
            context,
            modelEnabled,
            character.id(),
            action.id(),
            modelFinalLoop,
            modelForceLoop,
            modelCrossfade,
            number(loopStart, 8),
            number(loopEnd, 8),
            number(crossfadeDuration, 8),
            number(scale, 8),
            logoEnabled,
            color,
            configuration,
        )
        modelScale = number(scale, 8)
        modelLoopStart = number(loopStart, 8)
        modelLoopEnd = number(loopEnd, 8)
        modelCrossfadeDuration = number(crossfadeDuration, 8)
        updateModelSelectionStatus()
        markPending()
        status = context.getString(R.string.model_restart_required)
    }

    /**
     * The configuration string the native model module parses. Field order and
     * spelling are the desktop contract; changing either silently stops the game
     * from finding a key, so this mirrors the original line for line.
     */
    private fun buildModelConfiguration(
        character: ModelPresetIndex.Character,
        action: ModelPresetIndex.Action,
        scale: Double,
        color: String,
        loopStart: Double,
        loopEnd: Double,
        crossfadeDuration: Double,
    ): String = buildString {
        appendLine("schema_version=5")
        appendLine("enabled=true")
        appendLine("model_replacement_enabled=$modelEnabled")
        appendLine("logo_theme_enabled=$logoEnabled")
        appendLine("logo_theme_color=$color")
        appendLine("diagnostics=true")
        appendLine("character=${character.id()}")
        appendLine("final_action=${action.id()}")
        appendLine("model_path=${character.modelPath()}")
        appendLine("model_path_hash=${character.modelPathHash()}")
        appendLine("model_bundle_hash=${character.modelBundleHash()}")
        appendAsset("sit_loop", character.sitLoop())
        appendAsset("sit_special", character.sitSpecial())
        appendAsset("sit_to_walk", character.sitToWalk())
        appendLine("final_path=${action.path()}")
        appendLine("final_path_hash=${action.pathHash()}")
        appendLine("final_label=${action.id()}")
        appendLine("final_native_loop=${action.nativeLoop()}")
        appendLine("start_yaw=-120")
        appendLine("turn_duration=3.0333335")
        appendLine("scale=${number(scale, 8)}")
        appendLine("forward_lean_sample=1")
        appendLine("sit_loop_speed=1")
        appendLine("sit_special_speed=1")
        appendLine("sit_to_walk_speed=1")
        appendLine("final_speed=1")
        appendLine("final_loop=$modelFinalLoop")
        appendLine("force_loop=$modelForceLoop")
        appendLine("use_crossfade=$modelCrossfade")
        appendLine("loop_start=${number(loopStart, 8)}")
        appendLine("loop_end=${number(loopEnd, 8)}")
        appendLine("crossfade_duration=${number(crossfadeDuration, 8)}")
    }

    private fun StringBuilder.appendAsset(prefix: String, asset: ModelPresetIndex.Asset) {
        appendLine("${prefix}_path=${asset.path()}")
        appendLine("${prefix}_path_hash=${asset.pathHash()}")
        appendLine("${prefix}_label=${asset.label()}")
    }

    /* ----------------------------------------------------------------- 角色配音 */

    var voiceTableStatus by mutableStateOf("")
        private set
    var voiceReady by mutableStateOf(false)
        private set

    /** Character ids in catalog order; the serialized rule string follows it. */
    var voiceCharacterIds by mutableStateOf<List<String>>(emptyList())
        private set
    var voiceCharacterNames by mutableStateOf<List<String>>(emptyList())
        private set

    private val voiceSelection = mutableStateMapOf<String, Int>()
    private var lastSavedRules = ""

    fun prepareVoice(intent: Intent) {
        if (BuildConfig.DEBUG && intent.hasExtra("voice_rules")) {
            ModuleSettings.setVoiceRules(context, intent.getStringExtra("voice_rules"))
        }
        val index = try {
            VoiceCatalogIndex.load(context)
        } catch (error: Exception) {
            voiceTableStatus = context.getString(R.string.voice_table_failed, error.message)
            status = context.getString(R.string.voice_table_unavailable)
            return
        }
        lastSavedRules = ModuleSettings.getVoiceRules(context)
        val configured = parseRules(lastSavedRules)
        val choices = index.characters()
        choices.forEach { choice ->
            voiceSelection[choice.characterId()] = languagePosition(
                configured[choice.characterId()] ?: "FollowGlobal",
            )
        }
        voiceCharacterIds = choices.map { it.characterId() }
        voiceCharacterNames = choices.map { it.displayName() }
        voiceReady = true
        voiceTableStatus = context.getString(
            R.string.voice_table_ready,
            choices.size - 1,
            index.catalogCount(),
        )
    }

    fun voiceLanguage(characterId: String): Int = voiceSelection[characterId] ?: 0

    fun updateVoiceLanguage(characterId: String, position: Int) {
        voiceSelection[characterId] = position
        saveVoiceRules()
    }

    private fun saveVoiceRules() {
        if (voiceSelection.isEmpty()) return
        val serialized = voiceCharacterIds.mapNotNull { id ->
            val position = voiceSelection[id] ?: 0
            if (position <= 0 || position >= LANGUAGE_VALUES.size) {
                null
            } else {
                "$id:${LANGUAGE_VALUES[position]}"
            }
        }.joinToString(";")
        if (serialized == lastSavedRules) return
        ModuleSettings.setVoiceRules(context, serialized)
        lastSavedRules = serialized
        markPending()
        status = context.getString(R.string.restart_required)
    }

    private fun parseRules(value: String): Map<String, String> {
        val rules = LinkedHashMap<String, String>()
        if (value.isEmpty()) return rules
        for (item in value.split(";")) {
            val separator = item.indexOf(':')
            if (separator > 0 && separator + 1 < item.length) {
                rules[item.substring(0, separator)] = item.substring(separator + 1)
            }
        }
        return rules
    }

    private fun languagePosition(value: String): Int =
        LANGUAGE_VALUES.indexOfFirst { it.equals(value, ignoreCase = true) }.takeIf { it >= 0 } ?: 0

    /* ----------------------------------------------------------------- 画面增强 */

    // betterendfield.ui
    var hideUid by mutableStateOf(false)
        private set
    var hideHud by mutableStateOf(false)
        private set
    var pcUi by mutableStateOf(false)
        private set

    // betterendfield.camera
    var disableDither by mutableStateOf(false)
        private set
    var freeCamera by mutableStateOf(false)
        private set
    var freeCameraFollowCharacter by mutableStateOf(false)
        private set
    var worldPause by mutableStateOf(false)
        private set
    var firstPerson by mutableStateOf(false)
        private set
    var firstPersonHideHead by mutableStateOf(true)
        private set
    var firstPersonFillNeck by mutableStateOf(true)
        private set
    var cameraSpeed by mutableStateOf(5.0f)
        private set
    var cameraFov by mutableStateOf(60.0f)
        private set
    var globalFovEnabled by mutableStateOf(false)
        private set
    var globalFov by mutableStateOf(60.0f)
        private set
    var firstPersonFov by mutableStateOf(75.0f)
        private set
    var firstPersonEyeForward by mutableStateOf(0.03f)
        private set
    var firstPersonEyeHeight by mutableStateOf(0.05f)
        private set
    var firstPersonNearClip by mutableStateOf(0.03f)
        private set
    var firstPersonExtendLookRange by mutableStateOf(false)
        private set

    // The advanced first-person block, stored as one record by ModuleSettings.
    var firstPersonMovement by mutableStateOf(false)
        private set
    var firstPersonSideLookLimit by mutableStateOf(60f)
        private set
    var firstPersonLookUpLimit by mutableStateOf(89f)
        private set
    var firstPersonLookDownLimit by mutableStateOf(89f)
        private set
    var firstPersonAnimationMode by mutableStateOf(0)
        private set
    var firstPersonAnimationStrength by mutableStateOf(0.35f)
        private set
    var firstPersonYieldDialogue by mutableStateOf(false)
        private set
    var firstPersonThirdPersonInCombat by mutableStateOf(false)
        private set
    var firstPersonTransitionSeconds by mutableStateOf(0f)
        private set
    var firstPersonExternalHeadScale by mutableStateOf(false)
        private set

    /*
     * The gyroscope look source, stored as one record by ModuleSettings. None of
     * it reaches the native configuration's behaviour - the sensor steers the
     * camera through the panel's own input relay - so these fields exist to
     * drive the sensor loop in the game process and to show the user what it is
     * doing.
     */
    var gyroscopeEnabled by mutableStateOf(false)
        private set
    var gyroscopeHorizontalSensitivity by mutableStateOf(1f)
        private set
    var gyroscopeVerticalSensitivity by mutableStateOf(1f)
        private set
    // Initial values mirror the stored defaults (both axes inverted); apply()
    // overwrites them with whatever the saved configuration holds.
    var gyroscopeInvertHorizontal by mutableStateOf(true)
        private set
    var gyroscopeInvertVertical by mutableStateOf(true)
        private set
    var gyroscopeDeadzone by mutableStateOf(0.002f)
        private set
    var gyroscopeSmoothing by mutableStateOf(0.08f)
        private set

    /**
     * Research switch: asks the game to enumerate its first-person look entry
     * points on the next launch. It is here, and not on a system property or an
     * environment variable, because the phone is not rooted and this settings
     * screen is the only thing that can reach the game process without root.
     * Off by default, read once at library load, and it only ever logs.
     */
    var firstPersonLookProbe by mutableStateOf(false)
        private set

    /*
     * The free camera's motion/keyframe/VMD block, stored as one record by
     * ModuleSettings. mouseInvertY and mouseSensitivity travel with it because
     * the configuration has always carried them, and since the panel grew a look
     * pad they are what steers it: they are the desktop mouse settings, applied
     * to the drag deltas the pad sends.
     */
    var mouseInvertY by mutableStateOf(false)
        private set
    var mouseSensitivity by mutableStateOf(0.1f)
        private set
    var freeCameraSmoothing by mutableStateOf(0.3f)
        private set
    var motionPreset by mutableStateOf(0)
        private set
    var motionSpeed by mutableStateOf(1f)
        private set
    var orbitSpeed by mutableStateOf(20f)
        private set
    var motionDuration by mutableStateOf(0f)
        private set
    var motionTargetHeight by mutableStateOf(1.2f)
        private set
    var keyframeSegmentSeconds by mutableStateOf(3f)
        private set
    var keyframeLoop by mutableStateOf(false)
        private set
    var vmdScale by mutableStateOf(0.07f)
        private set
    var vmdFovBias by mutableStateOf(5f)
        private set
    var vmdLoop by mutableStateOf(false)
        private set

    /*
     * The imported VMD slot. The payload itself never lives here - it goes into
     * the framework's remote file space, which is the only place the game process
     * can read it from. These fields are what the page shows about it.
     */
    var vmdImported by mutableStateOf(false)
        private set
    var vmdName by mutableStateOf("")
        private set
    var vmdBytes by mutableStateOf(0L)
        private set
    var vmdImportedAt by mutableStateOf("")
        private set
    var vmdImportStatus by mutableStateOf("")
        private set
    var vmdImporting by mutableStateOf(false)
        private set

    /*
     * MMD. Two things decide whether the page can do anything at all: the module
     * has to be on (nothing else in this block is read with it off), and at least
     * one file has to be loaded, because the transport drives a playback that
     * otherwise has nothing to play.
     *
     * The transport is not a settings write. It goes out through the runtime
     * command pump, which the native director drains on the game's frame thread,
     * so a tap reaches a running game the way the panel's own buttons do.
     */
    var mmdEnabled by mutableStateOf(false)
        private set
    var mmdLoop by mutableStateOf(false)
        private set
    var mmdMusic by mutableStateOf(true)
        private set
    var mmdSeekSeconds by mutableStateOf(5f)
        private set
    var mmdGain by mutableStateOf(1f)
        private set
    var mmdAudioOffset by mutableStateOf(0f)
        private set
    var mmdBody by mutableStateOf(false)
        private set
    var mmdFace by mutableStateOf(false)
        private set
    var mmdTerrain by mutableStateOf(false)
        private set
    var mmdMotionScale by mutableStateOf(1f)
        private set
    var mmdClothMode by mutableStateOf(1)
        private set
    var mmdMotionLoop by mutableStateOf(false)
        private set

    /** The slot record, as the JSON the game process reads back. */
    var mmdSlots by mutableStateOf("")
        private set
    var mmdImporting by mutableStateOf("")
        private set
    var mmdImportStatus by mutableStateOf("")
        private set

    /** The last thing the transport sent, and whether it reached the game. */
    var mmdCommandStatus by mutableStateOf("")
        private set

    /**
     * The installed works, as the library card lists them.
     *
     * A view model rather than the storage record: the page shows a name, a
     * file count and a size, while the index's other fields - remote names,
     * per-file lengths - are the installer's business and the game process's.
     * Keeping those out of the state holder is what lets this read as a
     * description of what the user has rather than of how it is stored.
     */
    var mmdWorks by mutableStateOf(emptyList<MmdWorkRow>())
        private set

    /** The selected work's folder, empty while the loose slots below are in use. */
    var mmdWork by mutableStateOf("")
        private set

    var mmdLibraryStatus by mutableStateOf("")
        private set

    /** True while a selection is being copied and recognised off the main thread. */
    var mmdLibraryImporting by mutableStateOf(false)
        private set

    /** True while the installer is publishing or removing; every button goes inert. */
    var mmdLibraryBusy by mutableStateOf(false)
        private set

    // betterendfield.actions
    var sustainedDash by mutableStateOf(false)
        private set
    var liinoCleanDash by mutableStateOf(false)
        private set
    var dashAglina by mutableStateOf(true)
        private set
    var dashLiino by mutableStateOf(true)
        private set
    var dashJsspsi by mutableStateOf(false)
        private set
    var jsspsiQuality by mutableStateOf(false)
        private set

    // The in-game panel.
    var overlayEnabled by mutableStateOf(false)
        private set
    var overlayTransparency by mutableFloatStateOf(0f)
        private set
    var overlayAutoSnap by mutableStateOf(false)
        private set
    var overlayLogVisible by mutableStateOf(true)
        private set
    var eflinkEnabled by mutableStateOf(false)
        private set
    var eflinkName by mutableStateOf("")
        private set
    var eflinkRoom by mutableStateOf("")
        private set
    var eflinkPort by mutableStateOf(47615)
        private set
    var eflinkMaxPlayers by mutableStateOf(8)
        private set
    var eflinkLobby by mutableStateOf("")
        private set

    private fun loadEnhancement() {
        hideUid = ModuleSettings.isHideUidEnabled(context)
        hideHud = ModuleSettings.isHideHudEnabled(context)
        pcUi = ModuleSettings.isPcUiEnabled(context)
        disableDither = ModuleSettings.isDisableDitherEnabled(context)
        freeCamera = ModuleSettings.isFreeCameraEnabled(context)
        freeCameraFollowCharacter = ModuleSettings.isFreeCameraFollowCharacter(context)
        worldPause = ModuleSettings.isWorldPauseEnabled(context)
        firstPerson = ModuleSettings.isFirstPersonEnabled(context)
        firstPersonHideHead = ModuleSettings.isFirstPersonHideHead(context)
        firstPersonFillNeck = ModuleSettings.isFirstPersonFillNeck(context)
        cameraSpeed = ModuleSettings.parse(ModuleSettings.getCameraSpeed(context), 5.0).toFloat()
        cameraFov = ModuleSettings.parse(ModuleSettings.getCameraFieldOfView(context), 60.0).toFloat()
        globalFovEnabled = ModuleSettings.isGlobalFovEnabled(context)
        globalFov = ModuleSettings.getGlobalFov(context)
        firstPersonFov = ModuleSettings.parse(ModuleSettings.getFirstPersonFieldOfView(context), 75.0).toFloat()
        firstPersonEyeForward = ModuleSettings.parse(ModuleSettings.getFirstPersonEyeForward(context), 0.03).toFloat()
        firstPersonEyeHeight = ModuleSettings.parse(ModuleSettings.getFirstPersonEyeHeight(context), 0.05).toFloat()
        firstPersonNearClip = ModuleSettings.parse(ModuleSettings.getFirstPersonNearClip(context), 0.03).toFloat()
        firstPersonExtendLookRange = ModuleSettings.isFirstPersonExtendLookRange(context)
        val advanced = ModuleSettings.getFirstPersonAdvanced(context)
        firstPersonMovement = advanced.movement()
        firstPersonSideLookLimit = advanced.sideLookLimit().toFloat()
        firstPersonLookUpLimit = advanced.lookUpLimit().toFloat()
        firstPersonLookDownLimit = advanced.lookDownLimit().toFloat()
        firstPersonAnimationMode = advanced.animationMode()
        firstPersonAnimationStrength = advanced.animationStrength().toFloat()
        firstPersonYieldDialogue = advanced.yieldDialogue()
        firstPersonThirdPersonInCombat = advanced.thirdPersonInCombat()
        firstPersonTransitionSeconds = advanced.transitionSeconds().toFloat()
        firstPersonExternalHeadScale = advanced.externalHeadScale()
        val gyro = ModuleSettings.getFirstPersonGyro(context)
        gyroscopeEnabled = gyro.enabled()
        gyroscopeHorizontalSensitivity = gyro.horizontalSensitivity().toFloat()
        gyroscopeVerticalSensitivity = gyro.verticalSensitivity().toFloat()
        gyroscopeInvertHorizontal = gyro.invertHorizontal()
        gyroscopeInvertVertical = gyro.invertVertical()
        gyroscopeDeadzone = gyro.deadzone().toFloat()
        gyroscopeSmoothing = gyro.smoothing().toFloat()
        firstPersonLookProbe = ModuleSettings.readFirstPersonLookProbe(context)
        freeCameraSmoothing = ModuleSettings.getFreeCameraSmoothing(context).toFloat()
        val motion = ModuleSettings.getCameraMotion(context)
        mouseInvertY = motion.invertY()
        mouseSensitivity = motion.sensitivity().toFloat()
        motionPreset = ModuleSettings.MOTION_PRESETS
            .indexOfFirst { name -> name.equals(motion.preset(), ignoreCase = true) }
            .coerceAtLeast(0)
        motionSpeed = motion.speed().toFloat()
        orbitSpeed = motion.orbitSpeed().toFloat()
        motionDuration = motion.duration().toFloat()
        motionTargetHeight = motion.targetHeight().toFloat()
        keyframeSegmentSeconds = motion.segmentSeconds().toFloat()
        keyframeLoop = motion.keyframeLoop()
        vmdScale = motion.vmdScale().toFloat()
        vmdFovBias = motion.vmdFovBias().toFloat()
        vmdLoop = motion.vmdLoop()
        vmdImported = ModuleSettings.isVmdImported(context)
        vmdName = ModuleSettings.getVmdName(context)
        vmdBytes = ModuleSettings.getVmdBytes(context)
        vmdImportedAt = formatVmdTime(ModuleSettings.getVmdTime(context))
        val mmd = ModuleSettings.getMmdSettings(context)
        mmdEnabled = mmd.enabled()
        mmdLoop = mmd.loop()
        mmdMusic = mmd.music()
        mmdSeekSeconds = mmd.seekSeconds().toFloat()
        mmdGain = mmd.gain().toFloat()
        mmdAudioOffset = mmd.audioOffset().toFloat()
        mmdBody = mmd.body()
        mmdFace = mmd.face()
        mmdTerrain = mmd.terrain()
        mmdMotionScale = mmd.motionScale().toFloat()
        mmdClothMode = mmd.clothMode()
        mmdMotionLoop = mmd.motionLoop()
        // Read from the preference rather than from the record: the record only
        // describes what reaches the native configuration, while the page also
        // needs the slots that were just imported and not yet saved.
        mmdSlots = ModuleSettings.getMmdSlots(context)
        mmdWorks = mmdWorkRows()
        mmdWork = ModuleSettings.getMmdWork(context)
        sustainedDash = ModuleSettings.isSustainedDashEnabled(context)
        liinoCleanDash = ModuleSettings.isLiinoCleanDashEnabled(context)
        dashAglina = ModuleSettings.isDashCharacterEnabled(context, "aglina")
        dashLiino = ModuleSettings.isDashCharacterEnabled(context, "liino")
        dashJsspsi = ModuleSettings.isDashCharacterEnabled(context, "jsspsi")
        jsspsiQuality = ModuleSettings.isJsspsiQualityEnabled(context)
        overlayEnabled = ModuleSettings.isOverlayEnabled(context)
        overlayTransparency = ModuleSettings.getOverlayTransparency(context)
        overlayAutoSnap = ModuleSettings.isOverlayAutoSnap(context)
        overlayLogVisible = ModuleSettings.isOverlayLogVisible(context)
        eflinkEnabled = ModuleSettings.isEflinkEnabled(context)
        eflinkName = ModuleSettings.getEflinkPlayerName(context)
        eflinkRoom = ModuleSettings.getEflinkRoomName(context)
        eflinkPort = ModuleSettings.getEflinkPort(context)
        eflinkMaxPlayers = ModuleSettings.getEflinkMaxPlayers(context)
        eflinkLobby = ModuleSettings.getEflinkLobby(context)
    }

    /** Re-reads the panel switch, which the settings Activity can leave and re-enter. */
    fun refreshOverlayFromStore() {
        overlayEnabled = ModuleSettings.isOverlayEnabled(context)
        overlayTransparency = ModuleSettings.getOverlayTransparency(context)
        overlayAutoSnap = ModuleSettings.isOverlayAutoSnap(context)
        overlayLogVisible = ModuleSettings.isOverlayLogVisible(context)
    }

    fun updateEflinkEnabled(value: Boolean) {
        eflinkEnabled = value
        ModuleSettings.setEflinkEnabled(context, value)
    }

    fun updateEflinkName(value: String) {
        eflinkName = value.trim().take(64)
        ModuleSettings.setEflinkPlayerName(context, eflinkName)
    }

    fun updateEflinkRoom(value: String) {
        eflinkRoom = value.trim().take(64)
        ModuleSettings.setEflinkRoomName(context, eflinkRoom)
    }

    fun updateEflinkPort(value: Int) {
        if (value < 1024 || value > 65535 || value == 47616) return
        eflinkPort = value
        ModuleSettings.setEflinkPort(context, value)
    }

    fun updateEflinkMaxPlayers(value: Int) {
        if (value < 2 || value > 16) return
        eflinkMaxPlayers = value
        ModuleSettings.setEflinkMaxPlayers(context, value)
    }

    fun updateEflinkLobby(value: String) {
        eflinkLobby = value.trim().take(128)
        ModuleSettings.setEflinkLobby(context, eflinkLobby)
    }

    fun updateHideUid(value: Boolean) {
        hideUid = value
        saveInterfaceSettings()
    }

    fun updateHideHud(value: Boolean) {
        hideHud = value
        saveInterfaceSettings()
    }

    fun updatePcUi(value: Boolean) {
        pcUi = value
        // Writes the preference and re-emits the interface configuration in one
        // step: the configuration writer reads the flag back from the store.
        ModuleSettings.setPcUiEnabled(context, value)
        afterEnhancementChange()
    }

    fun updateDisableDither(value: Boolean) {
        disableDither = value
        saveCameraSettings()
    }

    fun updateFreeCamera(value: Boolean) {
        freeCamera = value
        saveCameraSettings()
    }

    fun updateFreeCameraFollowCharacter(value: Boolean) {
        if (!ModuleSettings.setFreeCameraFollowCharacter(context, value)) {
            status = context.getString(R.string.camera_follow_save_failed)
            return
        }
        freeCameraFollowCharacter = value
        saveCameraSettings()
    }

    fun updateWorldPause(value: Boolean) {
        worldPause = value
        saveCameraSettings()
    }

    fun updateFirstPerson(value: Boolean) {
        firstPerson = value
        saveCameraSettings()
    }

    fun updateFirstPersonHideHead(value: Boolean) {
        firstPersonHideHead = value
        saveCameraSettings()
    }

    fun updateFirstPersonFillNeck(value: Boolean) {
        firstPersonFillNeck = value
        saveCameraSettings()
    }

    fun updateCameraSpeed(value: Float) {
        cameraSpeed = value
        saveCameraSettings()
    }

    fun updateCameraFov(value: Float) {
        cameraFov = value
        saveCameraSettings()
    }

    fun updateGlobalFovEnabled(value: Boolean) {
        if (!ModuleSettings.setGlobalFov(context, value, globalFov.toDouble())) {
            status = context.getString(R.string.camera_global_fov_save_failed)
            return
        }
        globalFovEnabled = value
        saveCameraSettings()
    }

    fun updateGlobalFov(value: Float) {
        if (!ModuleSettings.setGlobalFov(context, globalFovEnabled, value.toDouble())) {
            status = context.getString(R.string.camera_global_fov_save_failed)
            return
        }
        globalFov = value
        saveCameraSettings()
    }

    fun updateFirstPersonFov(value: Float) {
        firstPersonFov = value
        saveCameraSettings()
    }

    fun updateFirstPersonEyeForward(value: Float) {
        firstPersonEyeForward = value
        saveCameraSettings()
    }

    fun updateFirstPersonEyeHeight(value: Float) {
        firstPersonEyeHeight = value
        saveCameraSettings()
    }

    fun updateFirstPersonNearClip(value: Float) {
        firstPersonNearClip = value
        saveCameraSettings()
    }

    fun updateFirstPersonExtendLookRange(value: Boolean) {
        firstPersonExtendLookRange = value
        saveCameraSettings()
    }

    fun updateFirstPersonMovement(value: Boolean) {
        firstPersonMovement = value
        saveCameraSettings()
    }

    fun updateFirstPersonSideLookLimit(value: Float) {
        firstPersonSideLookLimit = value
        saveCameraSettings()
    }

    fun updateFirstPersonLookUpLimit(value: Float) {
        firstPersonLookUpLimit = value
        saveCameraSettings()
    }

    fun updateFirstPersonLookDownLimit(value: Float) {
        firstPersonLookDownLimit = value
        saveCameraSettings()
    }

    fun updateFirstPersonAnimationMode(value: Int) {
        firstPersonAnimationMode = value
        saveCameraSettings()
    }

    fun updateFirstPersonAnimationStrength(value: Float) {
        firstPersonAnimationStrength = value
        saveCameraSettings()
    }

    fun updateFirstPersonYieldDialogue(value: Boolean) {
        firstPersonYieldDialogue = value
        saveCameraSettings()
    }

    fun updateFirstPersonThirdPersonInCombat(value: Boolean) {
        firstPersonThirdPersonInCombat = value
        saveCameraSettings()
    }

    fun updateFirstPersonTransitionSeconds(value: Float) {
        firstPersonTransitionSeconds = value
        saveCameraSettings()
    }

    fun updateFirstPersonExternalHeadScale(value: Boolean) {
        firstPersonExternalHeadScale = value
        saveCameraSettings()
    }

    fun updateGyroscopeEnabled(value: Boolean) {
        gyroscopeEnabled = value
        saveCameraSettings()
    }

    fun updateFirstPersonLookProbe(value: Boolean) {
        firstPersonLookProbe = value
        ModuleSettings.writeFirstPersonLookProbe(context, value)
    }

    fun updateGyroscopeHorizontalSensitivity(value: Float) {
        gyroscopeHorizontalSensitivity = value
        saveCameraSettings()
    }

    fun updateGyroscopeVerticalSensitivity(value: Float) {
        gyroscopeVerticalSensitivity = value
        saveCameraSettings()
    }

    fun updateGyroscopeInvertHorizontal(value: Boolean) {
        gyroscopeInvertHorizontal = value
        saveCameraSettings()
    }

    fun updateGyroscopeInvertVertical(value: Boolean) {
        gyroscopeInvertVertical = value
        saveCameraSettings()
    }

    fun updateGyroscopeDeadzone(value: Float) {
        gyroscopeDeadzone = value
        saveCameraSettings()
    }

    fun updateGyroscopeSmoothing(value: Float) {
        gyroscopeSmoothing = value
        saveCameraSettings()
    }

    /** A look-pad drag turns the camera up instead of down when this is on. */
    fun updateMouseInvertY(value: Boolean) {
        mouseInvertY = value
        saveCameraSettings()
    }

    /**
     * Degrees of turn per screen pixel dragged, which is the desktop mouse
     * setting applied to the pad. The slider's range is narrower than the native
     * clamp (0.01-2.0): at 0.5 one swipe across the pad turns the camera more
     * than a full circle, and below 0.02 the pad would read as broken rather
     * than slow.
     */
    fun updateMouseSensitivity(value: Float) {
        mouseSensitivity = value
        saveCameraSettings()
    }

    fun updateMotionPreset(index: Int) {
        motionPreset = index
        saveCameraSettings()
    }

    fun updateFreeCameraSmoothing(value: Float) {
        if (!ModuleSettings.setFreeCameraSmoothing(context, value.toDouble())) {
            status = context.getString(R.string.mmd_alignment_save_failed)
            return
        }
        freeCameraSmoothing = ModuleSettings.getFreeCameraSmoothing(context).toFloat()
        saveCameraSettings()
    }

    fun updateMotionSpeed(value: Float) {
        motionSpeed = value
        saveCameraSettings()
    }

    fun updateOrbitSpeed(value: Float) {
        orbitSpeed = value
        saveCameraSettings()
    }

    fun updateMotionDuration(value: Float) {
        motionDuration = value
        saveCameraSettings()
    }

    fun updateMotionTargetHeight(value: Float) {
        motionTargetHeight = value
        saveCameraSettings()
    }

    fun updateKeyframeSegmentSeconds(value: Float) {
        keyframeSegmentSeconds = value
        saveCameraSettings()
    }

    fun updateKeyframeLoop(value: Boolean) {
        keyframeLoop = value
        saveCameraSettings()
    }

    fun updateVmdScale(value: Float) {
        vmdScale = value
        saveCameraSettings()
    }

    fun updateVmdFovBias(value: Float) {
        vmdFovBias = value
        saveCameraSettings()
    }

    fun updateVmdLoop(value: Boolean) {
        vmdLoop = value
        saveCameraSettings()
    }

    /**
     * Imports a .vmd into the slot the game process reads.
     *
     * The file is validated here, before anything is published, so a wrong file is
     * refused with a reason instead of reaching the game and coming back as an
     * opaque native log line. Both rules are the native loader's own: its 64 MiB
     * ceiling and the two header generations [ModuleSettings.isVmdMotion] accepts.
     *
     * The payload travels through the framework's remote file space because that
     * is the only channel between this process and the game's - different UIDs,
     * so neither can read the other's data directory - and the native side takes a
     * path, which is why the game process copies it once on startup.
     */
    fun importVmd(uri: android.net.Uri) {
        if (vmdImporting) return
        vmdImporting = true
        vmdImportStatus = context.getString(R.string.motion_vmd_import_working)
        val name = vmdDisplayName(uri)
        Thread({
            val outcome = runCatching {
                val temporary = File(context.cacheDir, "vmd-import.tmp")
                try {
                    var bytes = 0L
                    var header = ByteArray(0)
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException(context.getString(R.string.motion_vmd_unreadable))
                    stream.use { input ->
                        FileOutputStream(temporary, false).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                if (header.isEmpty()) {
                                    header = buffer.copyOfRange(0, minOf(read, VMD_HEADER_BYTES))
                                }
                                bytes += read
                                if (bytes > ModuleSettings.vmdMaximumBytes()) {
                                    throw IllegalStateException(
                                        context.getString(R.string.motion_vmd_too_large),
                                    )
                                }
                                output.write(buffer, 0, read)
                            }
                            output.fd.sync()
                        }
                    }
                    if (!ModuleSettings.isVmdMotion(header)) {
                        throw IllegalStateException(context.getString(R.string.motion_vmd_not_a_motion))
                    }
                    if (!FrameworkSettings.publishVmd(temporary)) {
                        throw IllegalStateException(
                            context.getString(R.string.motion_vmd_publish_failed),
                        )
                    }
                    bytes
                } finally {
                    temporary.delete()
                }
            }
            Handler(Looper.getMainLooper()).post {
                vmdImporting = false
                outcome.fold(
                    onSuccess = { bytes ->
                        ModuleSettings.setVmdImport(context, name, bytes, System.currentTimeMillis())
                        vmdImported = true
                        vmdName = name
                        vmdBytes = bytes
                        vmdImportedAt = formatVmdTime(System.currentTimeMillis())
                        vmdImportStatus = context.getString(
                            R.string.motion_vmd_imported, name, formatVmdBytes(bytes),
                        )
                        // Until an import existed the configuration wrote the slot
                        // empty, so it has to be rewritten for the path to travel.
                        saveCameraSettings()
                    },
                    onFailure = { error ->
                        vmdImportStatus = context.getString(
                            R.string.motion_vmd_import_failed,
                            error.message ?: error.toString(),
                        )
                    },
                )
            }
        }, "BetterEndfield-VmdImport").start()
    }

    /**
     * Drops the slot. The configuration stops naming the file even when the
     * framework refuses to delete it: an unreferenced file is inert, while a
     * dangling reference would keep the module reading a motion the user removed.
     */
    fun clearVmd() {
        val dropped = FrameworkSettings.removeVmd()
        ModuleSettings.clearVmdImport(context)
        vmdImported = false
        vmdName = ""
        vmdBytes = 0L
        vmdImportedAt = ""
        vmdImportStatus = context.getString(
            if (dropped) R.string.motion_vmd_cleared else R.string.motion_vmd_cleared_unlinked,
        )
        saveCameraSettings()
    }

    private fun vmdDisplayName(uri: android.net.Uri): String {
        try {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getString(0)
                }
        } catch (ignored: RuntimeException) {
            // Fall through to the last path segment below.
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
            ?: context.getString(R.string.motion_vmd_unnamed)
    }

    /* ------------------------------------------------------------------- MMD */

    /*
     * The settings side of MMD. Both halves are writes, but they are different
     * kinds of write and are kept apart on purpose:
     *
     *  - the switches below rewrite the camera configuration, which the game
     *    process replays live through the native reload, so a parameter change
     *    needs no restart;
     *  - the transport buttons publish a command, which the native director
     *    drains on the game's frame thread. A transport command is not stored
     *    anywhere - "pause" is a moment, not a setting - so nothing here is read
     *    back at start-up.
     */

    fun updateMmdEnabled(value: Boolean) {
        mmdEnabled = value
        saveCameraSettings()
    }

    fun updateMmdLoop(value: Boolean) {
        mmdLoop = value
        saveCameraSettings()
    }

    fun updateMmdMusic(value: Boolean) {
        mmdMusic = value
        saveCameraSettings()
    }

    fun updateMmdSeekSeconds(value: Float) {
        mmdSeekSeconds = value
        saveCameraSettings()
    }

    fun updateMmdGain(value: Float) {
        mmdGain = value
        saveCameraSettings()
    }

    fun updateMmdAudioOffset(value: Float) {
        mmdAudioOffset = value
        saveCameraSettings()
    }

    fun updateMmdBody(value: Boolean) {
        mmdBody = value
        saveCameraSettings()
    }

    fun updateMmdFace(value: Boolean) {
        mmdFace = value
        saveCameraSettings()
    }

    fun updateMmdTerrain(value: Boolean) {
        mmdTerrain = value
        saveCameraSettings()
    }

    fun updateMmdMotionScale(value: Float) {
        mmdMotionScale = value
        saveCameraSettings()
    }

    fun updateMmdClothMode(value: Int) {
        mmdClothMode = value
        saveCameraSettings()
    }

    fun updateMmdMotionLoop(value: Boolean) {
        mmdMotionLoop = value
        saveCameraSettings()
    }

    /**
     * One transport tap, sent to the running game.
     *
     * The vocabulary is the native parser's own and is deliberately textual:
     * {@code play_pause}, {@code stop}, {@code loop}, {@code seek <seconds>},
     * {@code camera [0-2]}. Writing the verb from this side rather than an enum
     * means the two ends never have to agree on a numbering.
     *
     * A refusal is reported rather than swallowed. The only realistic reason for
     * one is that the framework service is not connected - i.e. the module is not
     * enabled for the game - and a button that appears to work while nothing
     * happens is exactly the failure this page cannot afford.
     */
    fun sendMmdCommand(verb: String) {
        val delivered = ModuleCommandRouter.issue(context, "mmd", verb)
        mmdCommandStatus = if (delivered) {
            "已发送：$verb"
        } else {
            "命令未送达：框架服务未连接，请先在模块管理里为该游戏启用本模块。"
        }
    }

    /**
     * Imports one file into its slot.
     *
     * The same three steps the VMD camera import uses: validate before anything
     * is published, let the framework's remote file space carry the payload, then
     * record what landed so the game process knows the length to expect. The
     * difference is that MMD has three slots, so the record is merged rather than
     * replaced.
     *
     * The header check is the native loader's own rule ({@code isVmdMotion}, the
     * two VMD generations) and it is applied to the motion and face slots only.
     * The music slot is handed to MediaPlayer, which sniffs the container, so a
     * VMD header rule there would refuse valid audio.
     */
    fun importMmdSlot(id: String, uri: android.net.Uri) {
        if (mmdImporting.isNotEmpty()) return
        mmdImporting = id
        mmdImportStatus = "正在读取所选文件…"
        val name = ModuleSettings.sanitizeMmdSlotName(vmdDisplayName(uri), id)
        val motionSourced = id != ModuleSettings.MMD_SLOT_MUSIC
        Thread({
            val outcome = runCatching {
                val temporary = File(context.cacheDir, "mmd-$id-import.tmp")
                try {
                    var bytes = 0L
                    var header = ByteArray(0)
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("无法读取所选文件")
                    stream.use { input ->
                        FileOutputStream(temporary, false).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                if (header.isEmpty()) {
                                    header = buffer.copyOfRange(0, minOf(read, VMD_HEADER_BYTES))
                                }
                                bytes += read
                                if (bytes > ModuleSettings.mmdSlotMaximumBytes()) {
                                    throw IllegalStateException("文件超过 64 MiB")
                                }
                                output.write(buffer, 0, read)
                            }
                            output.fd.sync()
                        }
                    }
                    if (bytes <= 0L) throw IllegalStateException("文件为空")
                    if (motionSourced && !ModuleSettings.isVmdMotion(header)) {
                        throw IllegalStateException("不是 VMD 动作文件")
                    }
                    if (!FrameworkSettings.publishMmdSlot(
                            temporary, ModuleSettings.mmdSlotRemote(id),
                        )
                    ) {
                        throw IllegalStateException("框架服务未连接或发布失败")
                    }
                    bytes
                } finally {
                    temporary.delete()
                }
            }
            Handler(Looper.getMainLooper()).post {
                mmdImporting = ""
                outcome.fold(
                    onSuccess = { bytes ->
                        val updated = ModuleSettings.mmdSlotsWith(mmdSlots, id, name, bytes)
                        ModuleSettings.setMmdSlots(context, updated)
                        mmdSlots = updated
                        mmdImportStatus =
                            "已导入 $name（${formatVmdBytes(bytes)}）。重启游戏后生效。"
                        // The path travels inside the camera configuration, so a
                        // slot that has just been filled has to be rewritten for
                        // the game process to find it.
                        saveCameraSettings()
                    },
                    onFailure = { error ->
                        mmdImportStatus = "导入失败：${error.message ?: error}"
                    },
                )
            }
        }, "BetterEndfield-MmdImport").start()
    }

    /**
     * Drops one slot. The configuration stops naming the file even when the
     * framework refuses to delete it: an unreferenced file is inert, while a
     * dangling reference would keep the director reading a file the user removed.
     */
    fun clearMmdSlot(id: String) {
        val dropped = FrameworkSettings.removeMmdSlot(ModuleSettings.mmdSlotRemote(id))
        val updated = ModuleSettings.mmdSlotsWithout(mmdSlots, id)
        ModuleSettings.setMmdSlots(context, updated)
        mmdSlots = updated
        mmdImportStatus = if (dropped) {
            "已清除该文件。"
        } else {
            "已从配置中移除该文件；框架里的副本未能删除。"
        }
        saveCameraSettings()
    }

    /** The one-line description of a slot for the page's rows. */
    fun mmdSlotLabel(id: String): String {
        val name = ModuleSettings.mmdSlotName(mmdSlots, id)
        if (name.isEmpty()) return "未导入"
        return "$name（${formatVmdBytes(ModuleSettings.mmdSlotBytes(mmdSlots, id))}）"
    }

    /** A work's footprint, for the library rows. */
    fun mmdWorkLabel(work: MmdWorkRow): String =
        "${work.files} 个文件 · ${formatVmdBytes(work.bytes)}"

    /**
     * Switches the library work, or back to the loose slots when given "".
     *
     * Two separate things are switched, and only one of them is instant. The
     * stored configuration is rewritten for the next launch. The running game is
     * told to switch now through the same command channel the transport uses -
     * but that can only succeed for a work whose files are already in the game's
     * own directory, and those are copied before the native library loads. So a
     * work invoked before the game was restarted is remembered, not playing, and
     * the message says so rather than pretending.
     */
    fun selectMmdWork(generation: String) {
        ModuleSettings.setMmdWork(context, generation)
        mmdWork = generation
        val verb = if (generation.isEmpty()) "work" else "work $generation"
        val delivered = ModuleCommandRouter.issue(context, "mmd", verb)
        mmdLibraryStatus = when {
            generation.isEmpty() -> "已切回散文件播放（配置里的作品选择已清空）。"
            delivered -> "已选择作品，游戏正在运行的话会立刻切换。"
            else -> "已选择作品；命令未送达，重启游戏后生效。"
        }
    }

    /**
     * Reads the index back as rows.
     *
     * Read from the store rather than kept in memory as it is built: the
     * installer runs on its own thread and appends there, so re-reading is how
     * a work it just added becomes visible without the page having to know an
     * install happened.
     */
    private fun mmdWorkRows(): List<MmdWorkRow> =
        ModuleSettings.getMmdWorks(context).map { work ->
            MmdWorkRow(
                generation = work.generation(),
                name = work.name(),
                files = work.files().size,
                bytes = work.files().sumOf { file -> file.bytes() },
            )
        }

    /** Re-reads the index, which the installer changes on its own thread. */
    private fun refreshMmdWorks() {
        mmdWorks = mmdWorkRows()
        mmdWork = ModuleSettings.getMmdWork(context)
    }

    /**
     * Runs one installer operation, mirroring its progress into the page.
     *
     * The installer reports through a static status string and a static busy
     * flag rather than a callback, because the operation outlives whichever
     * screen started it. Polling is therefore the only way to show progress, and
     * the flag dropping is the only signal that it finished - including when it
     * was rejected outright, which is why the rejected path still refreshes.
     */
    private fun runLibraryOperation(start: () -> Boolean) {
        if (mmdLibraryBusy) return
        mmdLibraryBusy = true
        Thread({
            val accepted = start()
            if (accepted) {
                while (MmdLibraryInstaller.busy) {
                    val text = MmdLibraryInstaller.status
                    if (text.isNotEmpty()) Handler(Looper.getMainLooper()).post { mmdLibraryStatus = text }
                    Thread.sleep(150)
                }
            }
            val summary = MmdLibraryInstaller.status
            Handler(Looper.getMainLooper()).post {
                mmdLibraryBusy = false
                refreshMmdWorks()
                mmdLibraryStatus = summary
            }
        }, "BetterEndfield-MmdLibrary").start()
    }

    fun removeMmdWork(generation: String) {
        runLibraryOperation { MmdLibraryInstaller.remove(context, generation) }
    }

    fun republishMmdWork(generation: String) {
        runLibraryOperation { MmdLibraryInstaller.republish(context, generation) }
    }

    fun refreshMmdLibraryAfterImport() {
        refreshMmdWorks()
        mmdLibraryStatus = MmdLibraryInstaller.status
    }

    private fun formatVmdTime(millis: Long): String =
        if (millis <= 0L) "" else SimpleDateFormat(VMD_TIME_PATTERN, Locale.ROOT).format(Date(millis))

    private fun formatVmdBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0)
        bytes >= 1024L -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
        else -> "$bytes 字节"
    }

    fun updateSustainedDash(value: Boolean) {
        sustainedDash = value
        saveDashSettings()
    }

    fun updateLiinoCleanDash(value: Boolean) {
        liinoCleanDash = value
        saveDashSettings()
    }

    fun updateDashAglina(value: Boolean) {
        dashAglina = value
        saveDashSettings()
    }

    fun updateDashJsspsi(value: Boolean) { dashJsspsi = value; saveDashSettings() }

    fun updateJsspsiQuality(value: Boolean) { jsspsiQuality = value; saveDashSettings() }

    fun updateDashLiino(value: Boolean) {
        dashLiino = value
        saveDashSettings()
    }

    /**
     * The panel switch lives on its own page rather than going through
     * [afterEnhancementChange]: the overlay is not a module, so it neither
     * restarts the game nor changes what loads.
     */
    fun updateOverlayEnabled(value: Boolean) {
        overlayEnabled = value
        ModuleSettings.setOverlayEnabled(context, value)
        status = context.getString(
            if (value) R.string.overlay_turned_on else R.string.overlay_turned_off,
        )
    }

    fun updateOverlayTransparency(value: Float) {
        ModuleSettings.setOverlayTransparency(context, value)
        overlayTransparency = ModuleSettings.getOverlayTransparency(context)
    }

    fun updateOverlayAutoSnap(value: Boolean) {
        ModuleSettings.setOverlayAutoSnap(context, value)
        overlayAutoSnap = value
    }

    fun updateOverlayLogVisible(value: Boolean) {
        ModuleSettings.setOverlayLogVisible(context, value)
        overlayLogVisible = value
    }

    private fun saveInterfaceSettings() {
        ModuleSettings.setInterfaceSettings(context, hideUid, hideHud)
        afterEnhancementChange()
    }

    private fun saveCameraSettings() {
        val write = ModuleSettings.setCameraSettings(
            context,
            disableDither,
            freeCamera,
            worldPause,
            firstPerson,
            firstPersonHideHead,
            firstPersonFillNeck,
            cameraSpeed.toDouble(),
            cameraFov.toDouble(),
            firstPersonFov.toDouble(),
            firstPersonEyeForward.toDouble(),
            firstPersonEyeHeight.toDouble(),
            firstPersonNearClip.toDouble(),
            firstPersonExtendLookRange,
            ModuleSettings.FirstPersonAdvanced(
                firstPersonMovement,
                firstPersonSideLookLimit.toDouble(),
                firstPersonLookUpLimit.toDouble(),
                firstPersonLookDownLimit.toDouble(),
                firstPersonAnimationMode,
                firstPersonAnimationStrength.toDouble(),
                firstPersonYieldDialogue,
                firstPersonThirdPersonInCombat,
                firstPersonTransitionSeconds.toDouble(),
                firstPersonExternalHeadScale,
            ),
            ModuleSettings.CameraMotion(
                mouseInvertY,
                mouseSensitivity.toDouble(),
                // The preset travels as its desktop name; the record maps
                // anything unknown back to orbit, so the index only has to stay
                // inside the array the picker was built from.
                ModuleSettings.MOTION_PRESETS[
                    motionPreset.coerceIn(0, ModuleSettings.MOTION_PRESETS.size - 1),
                ],
                motionSpeed.toDouble(),
                orbitSpeed.toDouble(),
                motionDuration.toDouble(),
                motionTargetHeight.toDouble(),
                keyframeSegmentSeconds.toDouble(),
                keyframeLoop,
                vmdImported,
                vmdScale.toDouble(),
                vmdFovBias.toDouble(),
                vmdLoop,
            ),
            ModuleSettings.FirstPersonGyro(
                gyroscopeEnabled,
                gyroscopeHorizontalSensitivity.toDouble(),
                gyroscopeVerticalSensitivity.toDouble(),
                gyroscopeInvertHorizontal,
                gyroscopeInvertVertical,
                gyroscopeDeadzone.toDouble(),
                gyroscopeSmoothing.toDouble(),
            ),
            ModuleSettings.MmdSettings(
                mmdEnabled,
                mmdLoop,
                mmdMusic,
                mmdSeekSeconds.toDouble(),
                mmdGain.toDouble(),
                mmdAudioOffset.toDouble(),
                mmdBody,
                mmdFace,
                mmdTerrain,
                mmdMotionScale.toDouble(),
                mmdClothMode,
                mmdMotionLoop,
                // The slot record is the one field read back from the store
                // rather than held as page state: it is written by the import
                // itself, and re-reading it here keeps the configuration and the
                // record from drifting apart if an import landed while the page
                // was open.
                ModuleSettings.getMmdSlots(context),
                // Read back for the same reason as the slots, and read from the
                // same store rather than from page state: selecting a work is
                // what writes it, and re-reading keeps the configuration from
                // naming a work the index no longer lists.
                ModuleSettings.getMmdWork(context),
            ),
        )
        afterCameraChange(write)
    }

    /**
     * The camera block is the one the running game picks up on its own: the
     * configuration string is handed to the native module's runtime command pump
     * and replayed there through the same entry point the boot path uses, so a
     * parameter change no longer costs a restart. Two cases keep the old
     * wording - the first time the camera is switched on, because whether the
     * module is loaded into the process at all was decided when it started, and
     * a delivery that never reached the game process.
     */
    private fun afterCameraChange(write: ModuleSettings.CameraWrite) {
        refreshDiagnostics()
        status = context.getString(
            when {
                !write.changed() -> R.string.camera_settings_unchanged
                write.delivered() -> R.string.camera_settings_live
                else -> R.string.camera_settings_not_delivered
            },
        )
        if (write.firstEnable()) {
            markPending()
            status = context.getString(R.string.restart_required)
        }
    }

    private fun saveDashSettings() {
        ModuleSettings.setSustainedDashSettings(
            context,
            sustainedDash,
            liinoCleanDash,
            dashAglina,
            dashLiino,
            dashJsspsi,
            jsspsiQuality,
        )
        afterEnhancementChange()
    }

    private fun afterEnhancementChange() {
        refreshDiagnostics()
        markPending()
        status = context.getString(R.string.enhancement_restart_required)
    }

    /*
     * The dependent options. World pause is a free-camera sub-mode, the first-person
     * mesh options only mean anything in first person, and the clean-exhaust option
     * belongs to Liino; a switch that cannot do anything reads as broken, so each of
     * these is dimmed rather than hidden.
     */
    val worldPauseAvailable get() = freeCamera
    val cameraSpeedAvailable get() = freeCamera
    val cameraFovAvailable get() = freeCamera
    val hideHeadAvailable get() = firstPerson
    val fillNeckAvailable get() = firstPerson && firstPersonHideHead
    val firstPersonFovAvailable get() = firstPerson
    val firstPersonEyeForwardAvailable get() = firstPerson
    val firstPersonEyeHeightAvailable get() = firstPerson
    val firstPersonNearClipAvailable get() = firstPerson
    val firstPersonExtendLookRangeAvailable get() = firstPerson
    val firstPersonMovementAvailable get() = firstPerson
    val firstPersonSideLookLimitAvailable get() = firstPerson && firstPersonMovement
    val firstPersonLookLimitAvailable get() = firstPerson
    val firstPersonAnimationModeAvailable get() = firstPerson
    val firstPersonAnimationStrengthAvailable get() = firstPerson && firstPersonAnimationMode != 0
    val firstPersonYieldDialogueAvailable get() = firstPerson
    val firstPersonThirdPersonInCombatAvailable get() = firstPerson
    val firstPersonTransitionSecondsAvailable get() = firstPerson
    val firstPersonExternalHeadScaleAvailable get() = firstPerson && firstPersonHideHead
    /*
     * The whole motion block hangs off the free camera. The desktop module only
     * polls the preset, keyframe and VMD hotkeys while the free camera is armed,
     * so a row that could be edited with the camera off would never be read -
     * the same reason "camera speed" is dimmed whenever the camera is off.
     */
    val cameraMotionAvailable get() = freeCamera

    /**
     * Gyroscope deltas reach the camera through the free camera's look term, so
     * the source is offered whenever the module could run at all: enabling the
     * switch turns the free camera on in the same write, which is what gives the
     * deltas something to steer. Gating this row on the free camera being
     * already on would hide the switch behind the setting it is supposed to
     * enable. The tunables below are dimmed until the switch is on, so the page
     * shows what the source will do rather than hiding it.
     */
    val gyroscopeAvailable get() = freeCamera || firstPerson || gyroscopeEnabled
    val gyroscopeTuningAvailable get() = gyroscopeAvailable && gyroscopeEnabled

    /**
     * What the import row says about the slot right now. The page needs the name
     * and the size visible before the game is started: the file is copied into
     * the game process only on the next launch, so this line is the only evidence
     * the user gets that the right .vmd went in.
     */
    val vmdSummary get() = if (!vmdImported) {
        context.getString(R.string.motion_vmd_not_imported)
    } else {
        context.getString(
            R.string.motion_vmd_slot_summary, vmdName, formatVmdBytes(vmdBytes), vmdImportedAt,
        )
    }
    val dashCharactersAvailable get() = sustainedDash
    val liinoCleanDashAvailable get() = sustainedDash && dashLiino

    /* --------------------------------------------------------------------- MMD */

    /**
     * The selected library work, or null while the loose slots are in use.
     *
     * Resolved against the index rather than trusted from the stored folder
     * name: a selection that outlived the work it names - because the work was
     * removed from another screen or the index was rewritten - must read as "no
     * work" here, not as a work this page cannot show.
     */
    val mmdSelectedWork get() = mmdWorks.firstOrNull { it.generation == mmdWork }

    /**
     * Whether the module has a file to play. The camera slot counts: an MMD with
     * only a camera motion is a valid playback, and the native side treats it as
     * one (a VMD motion often carries the camera keys with it). A selected work
     * counts for the same reason and by construction - the importer refuses to
     * install a work without a motion or a camera.
     */
    val mmdMaterialAvailable get() =
        mmdSelectedWork != null || ModuleSettings.mmdHasMaterial(mmdSlots, vmdImported)

    /**
     * The transport is offered only when the module is on and something is
     * loaded. Both halves are load-bearing: the native director stops and stays
     * stopped while {@code mmd_enabled} is false, so a play button with the
     * module off would queue a command that is discarded on the next frame.
     */
    val mmdTransportAvailable get() = mmdEnabled && mmdMaterialAvailable

    /** The page's one-line answer to "what will it play". */
    val mmdSourceSummary: String
        get() {
            // A selected work is named by its display name, not its folder: the
            // folder is a UUID and means nothing to the user who imported it.
            val work = mmdSelectedWork
            if (work != null) return "作品库：${work.name}"
            return when {
                !mmdEnabled -> "未启用"
                !mmdMaterialAvailable -> "没有可播放的文件；先导入动作或镜头"
                vmdImported -> "镜头：已导入的 VMD 镜头文件"
                else -> "散文件：${ModuleSettings.mmdSlotName(mmdSlots, ModuleSettings.MMD_SLOT_MOTION)}"
            }
        }

    /* ----------------------------------------------------------------------- 诊断 */

    var diagnosticsOverlay by mutableStateOf("")
        private set
    var diagnosticsModules by mutableStateOf("")
        private set
    var diagnosticsRuntimeLog by mutableStateOf("")
        private set

    fun refreshDiagnostics() {
        diagnosticsOverlay = context.getString(
            R.string.diagnostics_overlay,
            state(overlayEnabled),
            "无需系统悬浮权限",
        )
        diagnosticsModules = context.getString(R.string.diagnostics_modules, loadedModules())
        val runtimeLog = FrameworkSettings.readRemoteLog().trim()
        diagnosticsRuntimeLog = if (runtimeLog.isEmpty()) {
            context.getString(R.string.diagnostics_runtime_empty)
        } else {
            context.getString(R.string.diagnostics_runtime) + "\n\n" + runtimeLog
        }
    }

    /**
     * The module ids the game process will actually start, read back from the same
     * configuration strings it reads. An empty configuration is how a module is kept
     * out of the process, so "absent from this list" is the real state rather than a
     * guess.
     */
    fun loadedModuleIds(): List<String> = buildList {
        if (ModuleSettings.getVoiceRules(context).isNotEmpty()) add("voice.character")
        if (modelEnabled || logoEnabled) add("model")
        if (hideUid || hideHud || pcUi) add("ui")
        if (disableDither || freeCamera || firstPerson) add("camera")
        if (sustainedDash) add("actions")
    }

    private fun loadedModules(): String {
        val loaded = loadedModuleIds()
        return if (loaded.isEmpty()) context.getString(R.string.state_none) else loaded.joinToString(" · ")
    }

    private fun state(on: Boolean): String =
        context.getString(if (on) R.string.state_on else R.string.state_off)

    /* -------------------------------------------------------------------- 卡片状态 */

    val interfaceCardStatus: String
        get() = moduleStatus("betterendfieldnext.ui", hideUid || hideHud || pcUi)

    val cameraCardStatus: String
        get() = moduleStatus(
            "betterendfieldnext.camera",
            // The same four terms the configuration writer uses to decide whether
            // it emits a configuration at all. They have to match, or this line
            // would claim the module will not load while the write that follows
            // it makes it load.
            disableDither || freeCamera || firstPerson || gyroscopeEnabled || mmdEnabled,
        )

    val dashCardStatus: String
        get() = moduleStatus("betterendfieldnext.actions", sustainedDash && (dashAglina || dashLiino || dashJsspsi))

    /**
     * Says whether the module will be loaded at all, which is the one thing this
     * screen can state for certain. Whether each Hook resolved is only knowable
     * inside the game, and is reported there.
     */
    private fun moduleStatus(moduleId: String, loaded: Boolean): String =
        "$moduleId  ·  " + context.getString(
            if (loaded) R.string.module_will_load else R.string.module_will_not_load,
        )

    /* ----------------------------------------------------------------------- 首页 */

    /**
     * The installed packages, read through the same state object the management
     * screen uses. Reading the index a second time here would be a second parser
     * to keep in step with [BemInstallState], and the two would eventually
     * disagree about what "enabled" means.
     */
    private val bem = BemInstallState(context)

    val appearancePackages: List<BemPackage> get() = bem.packages

    var homeRevision by mutableStateOf(0)
        private set

    fun refreshHome() {
        homeRevision++
        bem.refresh()
    }

    /** What the model module will put on the login screen, or that it is off. */
    val loginDisplaySummary: String
        get() {
            val parts = buildList {
                if (modelEnabled) {
                    add(characterNames.getOrNull(characterIndex) ?: context.getString(R.string.state_none))
                    actionIds.getOrNull(actionIndex)?.let(::add)
                }
                if (logoEnabled) add("Logo $logoColor")
            }
            return if (parts.isEmpty()) {
                context.getString(R.string.home_login_display_off)
            } else {
                parts.joinToString(" · ")
            }
        }

    /**
     * Which package is currently in charge, which is the only thing the stored
     * index can answer. Whether the *running* game has loaded it is a different
     * question and is answered by the journal, not here.
     */
    val appearanceSummary: String
        get() {
            val packages = bem.packages
            if (packages.isEmpty()) return context.getString(R.string.home_appearance_none)
            val active = packages.firstOrNull { it.enabled }
                ?: return context.getString(R.string.home_appearance_idle, packages.size)
            val detail = runCatching {
                if (active.minorVersion >= 1) {
                    val count = active.selections().size
                    if (count == 0) "" else " · $count 项选项"
                } else {
                    val appearance = active.selectedAppearance()
                    if (appearance.isEmpty()) "" else " · $appearance"
                }
            }.getOrDefault("")
            return active.name + detail
        }

    /* -------------------------------------------------------------------- 运行日志 */

    var journalText by mutableStateOf("")
        private set

    fun refreshJournal() {
        journalText = FrameworkSettings.readRemoteLog().trim()
    }

    /** The bounded viewport projection; export still uses the whole snapshot. */
    fun journalTailText(): String = journalText.lineSequence().toList()
        .takeLast(40).joinToString("\n")

    /**
     * The export body. The remote snapshot is everything this process can see:
     * the game's own ring and the native journal are assembled inside the game
     * and published as one string, so exporting is that string plus the header
     * that makes it identifiable once it leaves the device.
     */
    fun journalBody(): String = buildString {
        append("Better-Endfield journal\n")
        append("build ").append(BuildConfig.VERSION_NAME)
            .append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
        append("exported at ").append(stamp()).append("\n\n")
        append(journalText.ifBlank { context.getString(R.string.log_empty) }).append('\n')
    }

    fun journalFileName(): String = "betterendfield-log-${stamp()}.txt"

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())

    /*
     * This block must stay at the *end* of the class body, after every
     * `by mutableStateOf` property it writes.
     *
     * Kotlin runs property initialisers and init blocks in source order, and a
     * delegated property's backing field is only created where it is declared.
     * A loader placed above the properties it fills writes through a null
     * delegate and takes the whole Activity down before the first frame --
     * exactly the launch crash 3.3.22 shipped with (loadEnhancement() writing
     * hideUid: MutableState.setValue on a null object reference).
     * loadModelPresets() happens to touch only earlier declarations, which is
     * why the failure only showed up in the enhancement block.
     */
    init {
        ModuleSettings.republishConfigurations(context)
        loadModelPresets()
        loadEnhancement()
    }

    private companion object {
        val HEX_COLOR = Regex("#[0-9A-F]{6}")
    }
}

/** The stored form of a number: fixed decimals, no trailing zeros. */
fun number(value: Double, decimals: Int): String {
    val text = String.format(Locale.ROOT, "%.${decimals}f", value)
    val trimmed = text.trimEnd('0').trimEnd('.')
    return trimmed.ifEmpty { text }
}
