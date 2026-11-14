package dev.betterendfield.next

import android.app.Activity
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/** Compose state and view ownership for the existing Java game-process controller. */
class OverlaySurface(
    activity: Activity,
    private val preview: Boolean,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun toggle()
        fun collapse()
        fun drag(dx: Float, dy: Float)
        fun dragEnd()
        /**
         * A look-pad drag, in screen pixels: x to the right, y downwards - the
         * same axes the desktop mouse hook reports, so the shared native mouse
         * term needs no translation. Sent as often as the gesture reports; the
         * controller decides how often the relay is actually written.
         */
        fun look(dx: Float, dy: Float)
        fun pulse(key: Int, description: String)
        /**
         * Collapses the panel, waits, then sends [key]. For the keys that start
         * a shot: the panel has to be out of frame before the camera starts
         * moving, so these must not fire under the finger the way [pulse] does.
         */
        fun delayedPulse(key: Int, description: String)
        fun hold(key: Int, pressed: Boolean, description: String)
        /**
         * Submits a runtime command to the native pump, the same channel the
         * settings app drives. That app has to go out to the framework's remote
         * file space and back because it runs in another process; this panel is
         * already inside the game, so it appends to the relay stream the native
         * side tails and skips the round trip.
         *
         * This exists for the modules whose desktop controls are hotkeys the
         * touch panel cannot press - MMD's are NUMPAD4/5/6, the very codes the
         * virtual-key latch already uses, so routing them through [pulse] would
         * have the panel pressing keys against itself.
         */
        fun command(name: String, value: String)
        /**
         * The command channel's own status line, as the native relay publishes
         * it, empty until the runtime has loaded. Callers read it after issuing
         * a command to tell "sent" from "the runtime refused it".
         */
        fun commandStatus(): String
        fun openSettings()
        fun saveLog()
        fun refreshLog()
        fun copyLog()
    }

    private val owner = OverlayOwner()
    private var features by mutableStateOf(OverlayFeatures.off())
    private var journal by mutableStateOf("")
    private var showJournal by mutableStateOf(true)
    private var compositionInitialized = false

    /**
     * Popup windows read [android.view.View.getContext], not [LocalContext].
     * A text cursor is one of those popups, and it asks for Compose's
     * `default_popup_window_title`. The game's resource table does not contain
     * that id, so the view itself has to carry the module context.
     */
    private val resourceContext = OverlayResourceContext(activity)

    val handle: ComposeView = ComposeView(resourceContext).apply {
        setViewTreeOwners(this)
    }
    val panel: ComposeView = ComposeView(resourceContext).apply {
        setViewTreeOwners(this)
    }

    private fun setViewTreeOwners(view: View) {
        OverlayViewOwners.set(view, owner, owner, owner)
    }

    fun installOwnersOn(host: View) {
        setViewTreeOwners(host)
    }

    fun render(next: OverlayFeatures) { features = next }
    fun renderJournal(next: String) { journal = next }
    fun renderShowJournal(next: Boolean) { showJournal = next }
    fun composeNow() {
        if (compositionInitialized) return
        check(handle.isAttachedToWindow && panel.isAttachedToWindow)
        handle.setContent {
            CompositionLocalProvider(LocalContext provides resourceContext,
                LocalResources provides resourceContext.resources,
                LocalConfiguration provides resourceContext.resources.configuration) {
                OverlayTheme { FloatingHandle(callbacks::toggle, callbacks::drag, callbacks::dragEnd) }
            }
        }
        panel.setContent {
            CompositionLocalProvider(LocalContext provides resourceContext,
                LocalResources provides resourceContext.resources,
                LocalConfiguration provides resourceContext.resources.configuration) {
                OverlayTheme { OverlayPanel(features, preview, journal, showJournal, callbacks) }
            }
        }
        handle.createComposition()
        panel.createComposition()
        compositionInitialized = true
    }
    fun resumed() { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
    fun paused() { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE) }
    fun dispose() {
        handle.disposeComposition()
        panel.disposeComposition()
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        owner.viewModelStore.clear()
    }

    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        override val lifecycle: Lifecycle get() = registry
        val registry = LifecycleRegistry(this)
        override val viewModelStore = ViewModelStore()
        private val savedState = SavedStateRegistryController.create(this)
        override val savedStateRegistry get() = savedState.savedStateRegistry

        init {
            savedState.performAttach()
            savedState.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }
}
