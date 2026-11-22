package dev.betterendfield.next

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
    private lateinit var gameControl: GameControlController
    private var overlayPreview: GameOverlay? = null
    private var availableUpdate by mutableStateOf<AppUpdateChecker.Result?>(null)
    private val splashFinished = CompletableDeferred<Unit>()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val appearanceSettings = readAppearanceSettings()
        val appearance = appearanceSettings.resolve(systemUsesDarkTheme())
        val startingTheme = appearanceSettings.splashTheme(appearance)
        setTheme(startingTheme)
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        rememberPlatformSplashTheme(startingTheme)
        window.setBackgroundDrawable(ColorDrawable(getColor(appearance.background)))
        if (savedInstanceState == null) {
            splash.setOnExitAnimationListener { provider ->
                finishLaunchSplash(provider, appearance) { splashFinished.complete(Unit) }
            }
        } else {
            splashFinished.complete(Unit)
        }
        val bars = if (appearance.lightBars) {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        } else {
            SystemBarStyle.dark(Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)

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
        gameControl = GameControlController(this).also { it.start() }

        setContent {
            AppUiTheme(window, onAppearanceChanged = ::rememberPlatformSplashTheme) {
                SettingsShell(
                    state = settings,
                    gameControl = gameControl,
                    onPreviewOverlay = { showOverlayPreview() },
                    onInstallBem = {
                        startActivity(Intent(this@MainActivity, BemInstallActivity::class.java))
                    },
                )
                availableUpdate?.let { update ->
                    AlertDialog(
                        onDismissRequest = { availableUpdate = null },
                        title = { Text(getString(R.string.next_update_available)) },
                        text = { Text(getString(R.string.next_update_available_detail, update.currentVersion, update.latestVersion)) },
                        confirmButton = {
                            GhostButton(getString(R.string.next_open_releases), {
                                availableUpdate = null
                                runCatching {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl)))
                                }.onFailure {
                                    Toast.makeText(this@MainActivity, R.string.next_link_open_failed, Toast.LENGTH_SHORT).show()
                                }
                            })
                        },
                        dismissButton = { GhostButton(getString(R.string.next_update_later), { availableUpdate = null }) },
                    )
                }
            }
        }
        if (savedInstanceState == null) checkUpdateOnStartup()
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

    override fun onDestroy() {
        gameControl.close()
        super.onDestroy()
    }

    /**
     * Shows the same experimental Compose panel inside this app. Preview skips
     * sending key events; it cannot verify composition in the hooked game process.
     */
    private fun showOverlayPreview() {
        overlayPreview?.remove()
        overlayPreview = GameOverlay(this, true)
    }

    private fun checkUpdateOnStartup() {
        if (!FrameworkSettings.open(this).getBoolean(ModuleSettings.AUTO_CHECK_UPDATES, true)) return
        val missingCore = GameResourceStore.resolve(this, "core") == null
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { DistributionUpdater.update(applicationContext, false, false) }
            }
            if (missingCore && GameResourceStore.resolve(this@MainActivity, "core") != null) {
                awaitLaunchTransition()
                recreate()
            }
        }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { AppUpdateChecker.check() } }
            result.getOrNull()?.takeIf { it.isUpdateAvailable }?.let {
                awaitLaunchTransition()
                availableUpdate = it
            }
        }
    }

    private suspend fun awaitLaunchTransition() {
        // Some task launches have no starting surface and send no exit event.
        withTimeoutOrNull(2_000L) { splashFinished.await() }
    }

    companion object {
        const val EXTRA_PAGE = "settings_page"
        private const val KEY_PAGE = "page"
    }
}
