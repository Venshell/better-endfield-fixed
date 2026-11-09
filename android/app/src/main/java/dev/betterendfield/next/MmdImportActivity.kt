package dev.betterendfield.next

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType

/** Reviews a bounded import proposal before the existing library owner publishes it. */
class MmdImportActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var model: MmdImportState
    private val poll = object : Runnable {
        override fun run() {
            model.refresh()
            if (model.installed) {
                setResult(RESULT_OK)
                finish()
            } else handler.postDelayed(this, 250)
        }
    }

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        model = MmdImportState(applicationContext, savedInstanceState?.getString("session"))
        model.pendingSlot = savedInstanceState?.getString("slot")
        setContent { BetterEndfieldTheme { MmdImportPage(model, ::finish) } }
    }

    override fun onResume() {
        super.onResume()
        handler.post(poll)
    }

    override fun onPause() {
        model.saveEdits()
        handler.removeCallbacks(poll)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("session", model.sessionId)
        outState.putString("slot", model.pendingSlot)
        model.saveEdits()
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (isFinishing) model.close()
        super.onDestroy()
    }
}

internal class MmdImportState(private val context: Context, saved: String?) {
    private val session = MmdImportSession.open(context, saved)
    val sessionId: String get() = session.id
    var busy by mutableStateOf(false)
        private set
    var installed by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set
    var names by mutableStateOf<List<String>>(emptyList())
        private set
    var selected by mutableStateOf(0)
        private set
    var name by mutableStateOf("")
        private set
    var warnings by mutableStateOf<List<String>>(emptyList())
        private set
    var slots by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var pendingSlot: String? by mutableStateOf(null)
    private val work get() = session.plan?.works?.getOrNull(session.selected)

    init { refresh() }

    fun refresh() {
        busy = session.busy || MmdLibraryInstaller.busy
        installed = session.installed
        status = if (MmdLibraryInstaller.busy) MmdLibraryInstaller.status else session.status
        if (session.busy) return
        val plan = session.plan
        names = plan?.works?.map { it.name } ?: emptyList()
        selected = session.selected
        name = work?.name ?: ""
        slots = work?.slots?.toMap() ?: emptyMap()
        warnings = plan?.warnings?.toList() ?: emptyList()
    }

    fun prepare(uri: android.net.Uri, tree: Boolean, slot: String? = null) {
        if (busy) return
        session.prepare(context, uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION, tree, slot)
        refresh()
    }

    fun select(index: Int) {
        if (busy || index !in names.indices) return
        session.selected = index
        refresh()
        saveEdits()
    }

    fun rename(value: String) {
        if (busy) return
        work?.name = value.take(120)
        refresh()
    }

    fun candidates(slot: String): List<String> = session.plan?.assets
        ?.filter { it.supports(slot) }?.map { it.path } ?: emptyList()

    fun setSlot(slot: String, path: String?) {
        if (busy) return
        val current = work ?: return
        if (path == null) current.slots.remove(slot)
        else {
            val asset = session.plan?.asset(path) ?: return
            if (!asset.supports(slot)) return
            current.slots[slot] = path
        }
        refresh()
        saveEdits()
    }

    fun install() {
        if (busy) return
        val current = work ?: return
        try {
            session.plan?.validate(current.name, current.slots)
            if (!MmdLibraryInstaller.start(context, session, current.name, current.slots, current.settings)) {
                session.status = context.getString(R.string.mmd_alignment_busy)
                refresh()
            } else refresh()
        } catch (error: Exception) {
            session.status = context.getString(R.string.mmd_alignment_import_failed, error.message ?: error.javaClass.simpleName)
            refresh()
        }
    }

    fun saveEdits() = session.saveEdits()
    fun close() {
        if (session.installed) session.dispose() else session.cancel()
    }
}

@Composable
private fun MmdImportPage(model: MmdImportState, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val archive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.prepare(uri, false)
    }
    val directory = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) model.prepare(uri, true)
    }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val slot = model.pendingSlot
        model.pendingSlot = null
        if (uri != null && slot != null) model.prepare(uri, false, slot)
    }
    Surface(color = Be.Colors.background, contentColor = Be.Colors.textPrimary) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = Be.Space.gutter, vertical = Be.Space.page),
            verticalArrangement = Arrangement.spacedBy(Be.Space.l),
        ) {
            GhostButton(stringResource(R.string.mmd_alignment_back), onBack, Modifier.fillMaxWidth())
            SectionCard(eyebrow = "MMD", title = stringResource(R.string.mmd_alignment_import_title)) {
                GhostButton(stringResource(R.string.mmd_alignment_choose_archive),
                    { archive.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), !model.busy)
                GhostButton(stringResource(R.string.mmd_alignment_choose_directory),
                    { directory.launch(null) }, Modifier.fillMaxWidth(), !model.busy)
            }
            if (model.status.isNotBlank()) StatusBlock(model.status)
            if (model.names.isNotEmpty()) {
                PanelCard {
                    FieldLabel(stringResource(R.string.mmd_alignment_work))
                    SelectField(model.names, model.selected, model::select, enabled = !model.busy)
                    FieldLabel(stringResource(R.string.mmd_alignment_name))
                    TextFieldRow(model.name, model::rename, keyboardType = KeyboardType.Text,
                        maxLength = 120, enabled = !model.busy)
                    for (slot in MmdImportPlan.SLOTS) {
                        val candidates = model.candidates(slot)
                        FieldLabel(slotLabel(slot))
                        SelectField(
                            options = listOf(stringResource(R.string.mmd_alignment_unassigned)) + candidates,
                            selectedIndex = candidates.indexOf(model.slots[slot]) + 1,
                            onSelect = { index -> model.setSlot(slot, candidates.getOrNull(index - 1)) },
                            enabled = !model.busy,
                        )
                        GhostButton(stringResource(R.string.mmd_alignment_choose_external), {
                            model.pendingSlot = slot
                            file.launch(arrayOf("*/*"))
                        }, Modifier.fillMaxWidth(), !model.busy)
                    }
                }
                if (model.warnings.isNotEmpty()) StatusBlock(model.warnings.joinToString("\n"))
                PrimaryButton(stringResource(R.string.mmd_alignment_confirm), model::install,
                    enabled = !model.busy, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun slotLabel(slot: String): String = when (slot) {
    "camera" -> stringResource(R.string.mmd_alignment_camera)
    "music" -> stringResource(R.string.mmd_alignment_music)
    else -> {
        val dancer = slot.last().digitToIntOrNull() ?: 1
        stringResource(if (slot.startsWith("motion")) R.string.mmd_alignment_motion
            else R.string.mmd_alignment_face, dancer)
    }
}
