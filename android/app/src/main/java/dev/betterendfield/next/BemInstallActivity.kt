package dev.betterendfield.next

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * Import and startup-selection screen for installed BEM packages.
 *
 * The poll lives on the Activity rather than inside the composition: a texture
 * conversion keeps running after the screen is left, and re-reading the
 * installer's progress when the user comes back is the point. Driving it from
 * onResume/onPause means the tick stops exactly when this screen is not visible.
 */
class BemInstallActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var install: BemInstallState

    private val poll = object : Runnable {
        override fun run() {
            install.refresh()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        install = BemInstallState(applicationContext)
        install.refresh()
        savedInstanceState?.getString(PENDING_EXTERNAL_URI)?.let {
            install.restorePendingExternalUri(android.net.Uri.parse(it))
        }

        setContent {
            BetterEndfieldTheme {
                BemInstallScreen(install)
            }
        }

        // An explicit URI grant is still required; there is no arbitrary
        // filesystem path extra, and the picker result arrives through the
        // composition's launcher rather than onActivityResult.
        if (savedInstanceState == null) handleImportIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleImportIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        install.pendingExternalUri?.let { outState.putString(PENDING_EXTERNAL_URI, it.toString()) }
        super.onSaveInstanceState(outState)
    }

    private fun handleImportIntent(intent: Intent) {
        try {
            BemImportRequest.fromIntent(intent)?.let { install.startFromViewIntent(it) }
        } catch (error: IllegalArgumentException) {
            BemInstaller.status = error.message ?: getString(R.string.bem_import_request_malformed)
            install.refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(poll)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        super.onPause()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 500L
        const val PENDING_EXTERNAL_URI = "pending_external_bem_uri"
    }
}
