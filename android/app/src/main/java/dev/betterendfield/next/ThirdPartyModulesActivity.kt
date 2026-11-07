package dev.betterendfield.next

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import java.util.concurrent.Executors

/** The settings UI consumes the package store; it never opens a native module. */
class ThirdPartyModulesActivity : ComponentActivity() {
    private data class Entry(
        val id: String,
        val name: String,
        val version: String,
        val enabled: Boolean,
        val supported: Boolean,
        val hasUi: Boolean,
    )

    private val worker = Executors.newSingleThreadExecutor()
    private var entries by mutableStateOf<List<Entry>>(emptyList())
    private var status by mutableStateOf("")
    private var busy by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { BetterEndfieldTheme { Content() } }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun readEntries(): List<Entry> {
        val records = ThirdPartyModuleStore.index(this).getJSONArray("modules")
        return (0 until records.length()).map { position ->
            val record = records.getJSONObject(position)
            val manifest = ThirdPartyModuleStore.manifest(record)
            Entry(
                id = record.getString("id"),
                name = manifest.getString("name"),
                version = manifest.getString("version"),
                enabled = record.getBoolean("enabled"),
                supported = ThirdPartyModulePackage.supported(manifest),
                hasUi = manifest.optString("ui").isNotEmpty(),
            )
        }
    }

    private fun refresh() {
        worker.execute {
            try {
                val loaded = readEntries()
                runOnUiThread { entries = loaded }
            } catch (error: Exception) {
                runOnUiThread { status = getString(R.string.third_party_error, error.message ?: error.javaClass.simpleName) }
            }
        }
    }

    private fun change(action: () -> Unit) {
        if (busy) return
        busy = true
        worker.execute {
            try {
                action()
                val loaded = readEntries()
                runOnUiThread {
                    entries = loaded
                    status = getString(R.string.third_party_saved)
                    busy = false
                }
            } catch (error: Exception) {
                runOnUiThread {
                    status = getString(R.string.third_party_error, error.message ?: error.javaClass.simpleName)
                    busy = false
                }
            }
        }
    }

    private fun import(uri: Uri) = change { ThirdPartyModuleStore.importArchive(this, uri) }

    private fun openUi(id: String) {
        startActivity(Intent(this, ThirdPartyModuleActivity::class.java).putExtra("module_id", id))
    }

    @Composable
    private fun Content() {
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) import(uri)
        }
        Surface(color = Be.Colors.background, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
                    .padding(horizontal = Be.Space.gutter, vertical = Be.Space.page),
                verticalArrangement = Arrangement.spacedBy(Be.Space.l),
            ) {
                SectionCard(
                    eyebrow = stringResource(R.string.third_party_eyebrow),
                    title = stringResource(R.string.third_party_title),
                    subtitle = stringResource(R.string.third_party_hint),
                ) {
                    GhostButton(
                        text = stringResource(R.string.third_party_import),
                        onClick = { picker.launch("*/*") },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (entries.isEmpty()) {
                    PanelCard { BodyText(stringResource(R.string.third_party_empty)) }
                }
                entries.forEachIndexed { index, entry ->
                    PanelCard {
                        CardTitle("${entry.name} · ${entry.version}")
                        BodyText(entry.id, modifier = Modifier.padding(top = Be.Space.s))
                        SwitchRow(
                            title = stringResource(R.string.third_party_enable),
                            description = if (entry.supported) "" else stringResource(R.string.third_party_unsupported),
                            checked = entry.enabled,
                            enabled = entry.supported && !busy,
                            onCheckedChange = { enabled ->
                                change { ThirdPartyModuleStore.enabled(this@ThirdPartyModulesActivity, entry.id, enabled) }
                            },
                            modifier = Modifier.padding(top = Be.Space.m),
                        )
                        if (entry.hasUi) {
                            GhostButton(
                                text = stringResource(R.string.third_party_open_ui),
                                onClick = { openUi(entry.id) },
                                modifier = Modifier.fillMaxWidth().padding(top = Be.Space.m),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Be.Space.s)) {
                            GhostButton(
                                text = stringResource(R.string.third_party_up),
                                onClick = { change { ThirdPartyModuleStore.move(this@ThirdPartyModulesActivity, entry.id, -1) } },
                                enabled = !busy && index > 0,
                                modifier = Modifier.weight(1f),
                            )
                            GhostButton(
                                text = stringResource(R.string.third_party_down),
                                onClick = { change { ThirdPartyModuleStore.move(this@ThirdPartyModulesActivity, entry.id, 1) } },
                                enabled = !busy && index < entries.lastIndex,
                                modifier = Modifier.weight(1f),
                            )
                            GhostButton(
                                text = stringResource(R.string.third_party_remove),
                                onClick = { change { ThirdPartyModuleStore.remove(this@ThirdPartyModulesActivity, entry.id) } },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                if (status.isNotEmpty()) Notice(status)
                Spacer(Modifier.padding(bottom = Be.Space.xxl))
            }
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
