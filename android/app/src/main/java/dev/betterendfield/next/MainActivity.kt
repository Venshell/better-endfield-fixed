package dev.betterendfield.next

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * The settings app.
 *
 * Four tabs and four sub-pages over one store: what the game will load, the
 * interface/camera/action switches, the character assets, and the tools. Every
 * write goes to [ModuleSettings], which publishes a snapshot the hooked game
 * process reads on its next start - so nothing on this screen takes effect until
 * the game is restarted, and the home page counts how much is waiting.
 */
class MainActivity : ComponentActivity() {

    private lateinit var settings: SettingsState
    private var overlayPreview: GameOverlay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The palette has exactly one theme, so the system bars are pinned to
        // light-on-dark rather than following the phone's day/night setting. On
        // API 35 the window is edge-to-edge whether or not this is called, and
        // the shell pads itself out of the insets instead of drawing under them.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        val initialPage = savedInstanceState?.getInt(KEY_PAGE, SettingsPage.HOME)
            ?: when (intent.getStringExtra(EXTRA_PAGE)) {
                "custom_model" -> SettingsPage.APPEARANCE
                "first_person" -> SettingsPage.FIRST_PERSON
                "tools" -> SettingsPage.TOOLS
                "enhancement" -> SettingsPage.EXPERIENCE
                "diagnostics" -> SettingsPage.TOOLS
                else -> SettingsPage.HOME
            }

        settings = SettingsState(applicationContext)
        settings.restorePage(initialPage)
        settings.prepareVoice(intent)
        settings.refreshDiagnostics()
        settings.refreshHome()

        setContent {
            BetterEndfieldTheme {
                SettingsShell(
                    state = settings,
                    onPreviewOverlay = { showOverlayPreview() },
                    onInstallBem = {
                        startActivity(Intent(this@MainActivity, BemInstallActivity::class.java))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // A setting changed while the game was in the background has to be
        // re-read here: the panel switch lives in the same preference file, and
        // the appearance summary is written by the other Activity.
        settings.refreshOverlayFromStore()
        settings.refreshDiagnostics()
        settings.refreshHome()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_PAGE, settings.page)
        super.onSaveInstanceState(outState)
    }

    /**
     * Shows the same experimental Compose panel inside this app. Preview skips
     * sending key events; it cannot verify composition in the hooked game process.
     */
    private fun showOverlayPreview() {
        overlayPreview?.remove()
        overlayPreview = GameOverlay(this, true)
    }

    companion object {
        const val EXTRA_PAGE = "settings_page"
        private const val KEY_PAGE = "page"
    }
}
