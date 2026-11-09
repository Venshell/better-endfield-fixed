package dev.betterendfield.next

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID

/** Settings remain in the same INI and file index consumed by the game process. */
internal data class MediaScreenConfig(
    val enabled: Boolean = false,
    val source: Int = 0,
    val sourcePath: String = "",
    val imagePaths: String = "",
    val wallScreen: Boolean = true,
    val deskScreen: Boolean = true,
    val deskFit: Int = 0,
    val audio: Boolean = true,
    val loop: Boolean = true,
    val interval: String = "10",
    val gain: String = "1",
    val target: String = "ReceptionRoomImagePoster",
    val resolution: Int = 1,
    val width: String = "1920",
    val height: String = "1080",
    val fps: Int = 60,
    val toggleHotkey: String = "-",
    val pauseHotkey: String = "",
)

/** Validation and import own the contract; Compose only maps controls to it. */
internal object MediaScreenStore {
    private val resolutions = listOf(1280 to 720, 1920 to 1080, 2560 to 1440, 3840 to 2160)
    private val hotkeyPattern = Regex("NONE|(?:(?:CTRL|ALT|SHIFT|WIN)\\+)*(?:[A-Z0-9]|-|=|PLUS|MINUS|OEM_MINUS|OEM_PLUS|ENTER|NUMPAD_ENTER|SPACE|TAB|ESC|ADD|SUBTRACT|MULTIPLY|DIVIDE|DECIMAL|UP|DOWN|LEFT|RIGHT|NUMPAD[0-9]|F(?:[1-9]|1[0-9]|2[0-4]))")

    fun read(context: Context): MediaScreenConfig {
        val prefs = FrameworkSettings.open(context)
        val ini = prefs.getString(ModuleSettings.MEDIA_SETTINGS,
            prefs.getString(ModuleSettings.MEDIA_CONFIGURATION, "")) ?: ""
        val values = ini.lineSequence().mapNotNull { line ->
            val equal = line.indexOf('=')
            if (equal > 0) line.substring(0, equal) to line.substring(equal + 1).trim() else null
        }.toMap()
        val width = values["width"] ?: "1920"
        val height = values["height"] ?: "1080"
        return MediaScreenConfig(
            enabled = values["enabled"] == "true",
            source = listOf("image", "slideshow", "video").indexOf(values["source"]).coerceAtLeast(0),
            sourcePath = values["source_path"] ?: "",
            imagePaths = values["image_paths"] ?: "",
            wallScreen = values["wall_screen_enabled"] !in listOf("false", "0"),
            deskScreen = values["desk_screen_enabled"] !in listOf("false", "0"),
            deskFit = listOf("fit", "crop", "stretch").indexOf(values["desk_fit_mode"]).coerceAtLeast(0),
            audio = values["audio_enabled"] != "false",
            loop = values["loop"] != "false",
            interval = values["slide_interval"] ?: "10",
            gain = values["gain"] ?: "1",
            target = values["target_object"] ?: "ReceptionRoomImagePoster",
            resolution = resolutions.indexOfFirst { it.first.toString() == width && it.second.toString() == height }
                .let { if (it < 0) resolutions.size else it },
            width = width,
            height = height,
            fps = values["fps"]?.toIntOrNull()?.coerceIn(1, 120) ?: 60,
            toggleHotkey = values["toggle_hotkey"] ?: "-",
            pauseHotkey = values["pause_hotkey"]?.takeUnless { it == "NONE" } ?: "",
        )
    }

    fun withResolution(config: MediaScreenConfig, index: Int): MediaScreenConfig {
        val size = resolutions.getOrNull(index) ?: return config.copy(resolution = resolutions.size)
        return config.copy(resolution = index, width = size.first.toString(), height = size.second.toString())
    }

    private fun hotkey(context: Context, raw: String): String {
        var text = raw.trim().uppercase(Locale.ROOT).replace(" ", "")
        if (text.isEmpty() || text == "OFF" || text == "DISABLED") return "NONE"
        if (text == "+") text = "PLUS"
        else if (text.endsWith("++")) text = text.dropLast(1) + "PLUS"
        require(hotkeyPattern.matches(text)) { context.getString(R.string.media_invalid_hotkey) }
        return text
    }

    fun save(context: Context, config: MediaScreenConfig) {
        require(config.source in 0..2 && config.deskFit in 0..2) { context.getString(R.string.media_invalid_settings) }
        val seconds = config.interval.toDoubleOrNull()
        val volume = config.gain.toDoubleOrNull()
        val width = config.width.toIntOrNull()
        val height = config.height.toIntOrNull()
        require(seconds != null && seconds.isFinite() && seconds in 1.0..3600.0) { context.getString(R.string.media_invalid_interval) }
        require(volume != null && volume.isFinite() && volume in 0.0..2.0) { context.getString(R.string.media_invalid_gain) }
        require(width != null && height != null && width in 64..3840 && height in 64..3840 &&
            width.toLong() * height <= 3840L * 2160L) { context.getString(R.string.media_invalid_canvas) }
        val target = config.target.trim()
        require(target.isNotEmpty() && target.length <= 512 && !target.contains('\n') &&
            !target.contains('\r') && !target.contains('\u0000')) { context.getString(R.string.media_invalid_target) }
        require(config.sourcePath.isEmpty() ||
            (config.sourcePath.startsWith("@") && MediaScreenFiles.valid(config.sourcePath.removePrefix("@")))) {
            context.getString(R.string.media_invalid_source)
        }
        require(config.imagePaths.isEmpty() || config.imagePaths.split('|').let { paths ->
            paths.size <= 256 && paths.all { it.startsWith("@") && MediaScreenFiles.valid(it.removePrefix("@")) }
        }) { context.getString(R.string.media_invalid_source) }
        val ini = buildString {
            append("enabled=${config.enabled}\nsource=${listOf("image", "slideshow", "video")[config.source]}\n")
            append("source_path=${config.sourcePath}\nimage_paths=${config.imagePaths}\n")
            append("wall_screen_enabled=${config.wallScreen}\ndesk_screen_enabled=${config.deskScreen}\n")
            append("desk_fit_mode=${listOf("fit", "crop", "stretch")[config.deskFit]}\n")
            append("target_object=$target\naudio_enabled=${config.audio}\nloop=${config.loop}\n")
            append("slide_interval=$seconds\ngain=$volume\nwidth=$width\nheight=$height\nfps=${config.fps}\n")
            append("toggle_hotkey=${hotkey(context, config.toggleHotkey)}\npause_hotkey=${hotkey(context, config.pauseHotkey)}\n")
        }
        if (!FrameworkSettings.open(context).edit()
                .putString(ModuleSettings.MEDIA_SETTINGS, ini)
                .putString(ModuleSettings.MEDIA_CONFIGURATION, if (config.enabled) ini else "")
                .commit()) throw IOException(context.getString(R.string.media_settings_write_failed))
    }

    fun importFiles(context: Context, uris: List<Uri>, source: Int): List<String> {
        require(uris.isNotEmpty() && uris.size <= 256 && source in 0..2 && (source == 1 || uris.size == 1)) {
            context.getString(R.string.media_file_limit)
        }
        FrameworkSettings.awaitConnection()
        val prefs = FrameworkSettings.open(context)
        val manifest = JSONArray(prefs.getString(MediaScreenFiles.INDEX, "[]"))
        require(manifest.length() + uris.size <= 256) { context.getString(R.string.media_file_limit) }
        val root = File(context.filesDir, "media-screen")
        if (!root.isDirectory && !root.mkdirs()) throw IOException(context.getString(R.string.media_directory_failed))
        val imported = mutableListOf<File>()
        val tokens = mutableListOf<String>()
        try {
            for (uri in uris) {
                val extension = when (context.contentResolver.getType(uri)) {
                    "image/png" -> ".png"
                    "image/jpeg" -> ".jpg"
                    "video/mp4" -> ".mp4"
                    else -> throw IOException(context.getString(R.string.media_invalid_file))
                }
                require((source == 2) == (extension == ".mp4")) { context.getString(R.string.media_invalid_file) }
                val name = "media-${UUID.randomUUID()}$extension"
                val file = File(root, name)
                imported += file
                val limit = if (source == 2) 512L * 1024 * 1024 else 32L * 1024 * 1024
                context.contentResolver.openInputStream(uri).use { input ->
                    if (input == null) throw IOException(context.getString(R.string.media_read_failed))
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(65536)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > limit) throw IOException(context.getString(R.string.media_file_large))
                            output.write(buffer, 0, count)
                        }
                        if (total == 0L) throw IOException(context.getString(R.string.media_file_empty))
                        output.fd.sync()
                    }
                }
                FrameworkSettings.publishMedia(file, name)
                manifest.put(JSONObject().put("remote", name).put("bytes", file.length()))
                tokens += "@$name"
            }
            if (!prefs.edit().putString(MediaScreenFiles.INDEX, manifest.toString()).commit())
                throw IOException(context.getString(R.string.media_index_failed))
            return tokens
        } catch (error: Exception) {
            imported.forEach { file ->
                FrameworkSettings.removeMedia(file.name)
                file.delete()
            }
            throw error
        }
    }
}

class MediaScreenActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { BetterEndfieldTheme { MediaScreenPage(onBack = ::finish) } }
    }
}

@Composable
private fun MediaScreenPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(MediaScreenStore.read(context)) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var pendingSource by remember { mutableStateOf(0) }

    fun save(next: MediaScreenConfig) {
        config = next
        runCatching { MediaScreenStore.save(context, next) }
            .onSuccess { status = context.getString(R.string.media_saved) }
            .onFailure { status = context.getString(R.string.media_save_failed, it.message ?: it.javaClass.simpleName) }
    }

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty() || busy) return
        val mode = pendingSource
        val beforeImport = config
        busy = true
        status = context.getString(R.string.media_importing)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val tokens = MediaScreenStore.importFiles(context, uris, mode)
                    val next = if (mode == 1) beforeImport.copy(imagePaths = tokens.joinToString("|"))
                        else beforeImport.copy(sourcePath = tokens.first())
                    MediaScreenStore.save(context, next)
                    next
                }
            }
            busy = false
            result.fold(
                onSuccess = { next ->
                    config = next
                    status = context.getString(R.string.media_imported)
                },
                onFailure = { status = context.getString(R.string.media_import_failed, it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    val oneFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFiles(listOf(uri))
    }
    val manyFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> importFiles(uris) }

    Surface(color = Be.Colors.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = Be.Size.contentMaxWidth).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = Be.Space.gutter, end = Be.Space.gutter,
                        top = Be.Space.m, bottom = Be.Space.footer),
                verticalArrangement = Arrangement.spacedBy(Be.Space.l),
            ) {
                BackButton(onBack)
                SectionCard(
                    eyebrow = stringResource(R.string.media_screen_eyebrow),
                    title = stringResource(R.string.media_screen_title),
                ) {
                SwitchRow(stringResource(R.string.media_enable), "", config.enabled,
                    { save(config.copy(enabled = it)) }, enabled = !busy)
                BodyText(stringResource(R.string.media_runtime_manual_hint))
                }
            PanelCard {
                CardTitle(stringResource(R.string.media_section_content))
                FieldLabel(stringResource(R.string.media_source))
                SelectField(
                    listOf(stringResource(R.string.media_image), stringResource(R.string.media_slideshow), stringResource(R.string.media_video)),
                    config.source, { save(config.copy(source = it)) }, enabled = !busy,
                )
                GhostButton(stringResource(R.string.media_import), {
                    pendingSource = config.source
                    if (config.source == 1) manyFiles.launch(arrayOf("image/png", "image/jpeg"))
                    else oneFile.launch(arrayOf(if (config.source == 2) "video/mp4" else "image/*"))
                }, Modifier.fillMaxWidth().padding(top = Be.Space.m), !busy)
                BodyText(
                    when {
                        config.source == 1 -> context.getString(R.string.media_images_selected,
                            config.imagePaths.split('|').count { it.isNotBlank() })
                        config.sourcePath.isBlank() -> stringResource(R.string.media_none_selected)
                        else -> context.getString(R.string.media_file_ready, config.sourcePath.removePrefix("@"))
                    }, Modifier.padding(top = Be.Space.m),
                )
                SwitchRow(stringResource(R.string.media_audio), "", config.audio, { save(config.copy(audio = it)) }, enabled = !busy)
                SwitchRow(stringResource(R.string.media_loop), "", config.loop, { save(config.copy(loop = it)) }, enabled = !busy)
                FieldLabel(stringResource(R.string.media_interval))
                TextFieldRow(config.interval, { config = config.copy(interval = it) }, maxLength = 32,
                    enabled = !busy, onCommit = { save(config) })
                FieldLabel(stringResource(R.string.media_gain))
                TextFieldRow(config.gain, { config = config.copy(gain = it) }, maxLength = 32,
                    enabled = !busy, onCommit = { save(config) })
            }
            PanelCard {
                CardTitle(stringResource(R.string.media_section_screens))
                SwitchRow(stringResource(R.string.media_wall), "", config.wallScreen, { save(config.copy(wallScreen = it)) }, enabled = !busy)
                SwitchRow(stringResource(R.string.media_desk), "", config.deskScreen, { save(config.copy(deskScreen = it)) }, enabled = !busy)
                FieldLabel(stringResource(R.string.media_desk_fit))
                SelectField(listOf(stringResource(R.string.media_fit), stringResource(R.string.media_crop), stringResource(R.string.media_stretch)),
                    config.deskFit, { save(config.copy(deskFit = it)) }, enabled = !busy && config.deskScreen)
                FieldLabel(stringResource(R.string.media_target))
                TextFieldRow(config.target, { config = config.copy(target = it) }, keyboardType = KeyboardType.Text, maxLength = 512,
                    enabled = !busy, onCommit = { save(config) })
            }
            PanelCard {
                CardTitle(stringResource(R.string.media_section_render))
                FieldLabel(stringResource(R.string.media_resolution))
                SelectField(listOf("720p · 1280 × 720", "1080p · 1920 × 1080", "1440p · 2560 × 1440", "4K · 3840 × 2160", stringResource(R.string.media_custom)),
                    config.resolution, { save(MediaScreenStore.withResolution(config, it)) }, enabled = !busy)
                if (config.resolution == 4) {
                    FieldLabel(stringResource(R.string.media_width))
                    TextFieldRow(config.width, { config = config.copy(width = it) }, maxLength = 5,
                        enabled = !busy, onCommit = { save(config) })
                    FieldLabel(stringResource(R.string.media_height))
                    TextFieldRow(config.height, { config = config.copy(height = it) }, maxLength = 5,
                        enabled = !busy, onCommit = { save(config) })
                }
                FieldLabel(stringResource(R.string.media_toggle_hotkey))
                TextFieldRow(config.toggleHotkey, { config = config.copy(toggleHotkey = it) }, keyboardType = KeyboardType.Text,
                    maxLength = 64,
                    enabled = !busy, onCommit = { save(config) })
                FieldLabel(stringResource(R.string.media_pause_hotkey))
                TextFieldRow(config.pauseHotkey, { config = config.copy(pauseHotkey = it) }, keyboardType = KeyboardType.Text,
                    maxLength = 64,
                    enabled = !busy, onCommit = { save(config) })
            }
            PrimaryButton(stringResource(R.string.media_save), { save(config) }, enabled = !busy)
                if (status.isNotBlank()) BodyText(status)
            }
        }
    }
}
