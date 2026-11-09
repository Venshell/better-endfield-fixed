package dev.betterendfield.next

import android.app.Activity
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal data class GameControlTarget(val packageName: String, val label: String)

internal data class GameControlState(
    val targets: List<GameControlTarget> = emptyList(),
    val selectedPackage: String? = null,
    val currentScope: Set<String>? = null,
    val shizukuAvailable: Boolean = false,
    val shizukuGranted: Boolean = false,
    val status: String = "",
)

/** Activity-lifecycle owner for launch, LSPosed scope, and separate Shizuku permission/action flows. */
internal class GameControlController(private val activity: Activity) : AutoCloseable {
    var state by mutableStateOf(GameControlState())
        private set

    private val packages = listOf("com.hypergryph.endfield", "com.gryphline.endfield.gp")
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "game-control").apply { isDaemon = true }
    }
    private var closed = false
    private var connection: ServiceConnection? = null
    private var serviceArgs: Shizuku.UserServiceArgs? = null
    private var serviceBound = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var bindTimeout: Runnable? = null

    private val binderReceived = Shizuku.OnBinderReceivedListener { onMainIfOpen(::refreshShizuku) }
    private val binderDead = Shizuku.OnBinderDeadListener { onMainIfOpen(::refreshShizuku) }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == PERMISSION_REQUEST_CODE) {
            onMainIfOpen {
                refreshShizuku()
                setStatus(if (grantResult == PackageManager.PERMISSION_GRANTED)
                    R.string.game_shizuku_granted else R.string.game_shizuku_denied)
            }
        }
    }
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) refresh()
    }

    fun start() {
        val targets = findTargets()
        state = state.copy(targets = targets, selectedPackage = targets.singleOrNull()?.packageName)
        Shizuku.addBinderReceivedListener(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        (activity as? LifecycleOwner)?.lifecycle?.addObserver(lifecycleObserver)
        refresh()
    }

    fun select(packageName: String) {
        if (packageName in packages && state.targets.any { it.packageName == packageName }) {
            state = state.copy(selectedPackage = packageName)
        }
    }

    fun launchSelected() {
        val target = selectedTarget() ?: return setStatus(R.string.game_choose_target)
        val launchIntent = activity.packageManager.getLaunchIntentForPackage(target.packageName)
            ?: return setStatus(R.string.game_launch_unavailable)
        try {
            activity.startActivity(launchIntent)
            setStatus(R.string.game_launch_requested)
        } catch (_: Exception) {
            setStatus(R.string.game_launch_failed)
        }
    }

    fun requestScope() {
        val target = selectedTarget() ?: return setStatus(R.string.game_choose_target)
        val currentScope = FrameworkSettings.gameScope()
            ?: return setStatus(R.string.game_scope_unavailable)
        if (target.packageName in currentScope) {
            refreshFrameworkScope()
            return setStatus(R.string.game_scope_approved)
        }
        val accepted = FrameworkSettings.requestGameScope(listOf(target.packageName), object : FrameworkSettings.ScopeResultCallback {
            override fun onApproved(scope: Set<String>) {
                onMainIfOpen {
                    refreshFrameworkScope()
                    setStatus(if (state.currentScope?.contains(target.packageName) == true)
                        R.string.game_scope_approved else R.string.game_scope_unverified)
                }
            }
            override fun onFailed(message: String) {
                onMainIfOpen {
                    refreshFrameworkScope()
                    setStatus(R.string.game_scope_failed)
                }
            }
        })
        if (!accepted) {
            refreshFrameworkScope()
            setStatus(if (FrameworkSettings.gameScope() == null)
                R.string.game_scope_unavailable else R.string.game_scope_failed)
        } else {
            setStatus(R.string.game_scope_waiting)
        }
    }

    fun requestShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) return setStatus(R.string.game_shizuku_unavailable)
            if (Shizuku.isPreV11()) return setStatus(R.string.game_shizuku_old_version)
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                refreshShizuku()
                return setStatus(R.string.game_shizuku_granted)
            }
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            setStatus(R.string.game_shizuku_waiting)
        } catch (_: RuntimeException) {
            refreshShizuku()
            setStatus(R.string.game_shizuku_unavailable)
        }
    }

    fun forceStopSelected() {
        val target = selectedTarget() ?: return setStatus(R.string.game_choose_target)
        val currentlyGranted = try {
            Shizuku.pingBinder() && !Shizuku.isPreV11()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: RuntimeException) { false }
        if (!currentlyGranted) {
            refreshShizuku()
            return setStatus(R.string.game_shizuku_need_permission)
        }
        if (serviceBound) return setStatus(R.string.game_force_stop_busy)
        val args = Shizuku.UserServiceArgs(ComponentName(activity, GameControlUserService::class.java))
            .tag("better-endfield-game-control")
            .version(1)
            .processNameSuffix("gamecontrol")
            .daemon(false)
        val finished = AtomicBoolean(false)
        val timeout = Runnable {
            if (finished.compareAndSet(false, true)) {
                connection?.let { unbindUserService(args, it) }
                bindTimeout = null
                setStatus(R.string.game_force_stop_timeout)
            }
        }
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                mainHandler.removeCallbacks(timeout)
                bindTimeout = null
                if (closed || finished.get()) {
                    unbindUserService(args, this)
                    return
                }
                val remote = IGameControlService.Stub.asInterface(binder)
                executor.execute {
                    val result = try { remote.forceStop(target.packageName) } catch (_: Exception) { -6 }
                    if (finished.compareAndSet(false, true)) {
                        activity.runOnUiThread {
                            mainHandler.removeCallbacks(timeout)
                            bindTimeout = null
                            unbindUserService(args, this)
                            when (result) {
                                0 -> setStatus(R.string.game_force_stop_sent)
                                -2 -> setStatus(R.string.game_force_stop_rejected)
                                -3 -> setStatus(R.string.game_force_stop_timeout)
                                else -> setStatus(R.string.game_force_stop_failed)
                            }
                        }
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                if (finished.compareAndSet(false, true)) {
                    activity.runOnUiThread {
                        mainHandler.removeCallbacks(timeout)
                        bindTimeout = null
                        unbindUserService(args, this)
                        setStatus(R.string.game_force_stop_disconnected)
                    }
                }
            }
        }
        connection = serviceConnection
        serviceArgs = args
        serviceBound = true
        setStatus(R.string.game_force_stop_starting)
        try {
            Shizuku.bindUserService(args, serviceConnection)
            bindTimeout = timeout
            mainHandler.postDelayed(timeout, 12_000)
        } catch (_: RuntimeException) {
            mainHandler.removeCallbacks(timeout)
            bindTimeout = null
            serviceBound = false
            connection = null
            serviceArgs = null
            setStatus(R.string.game_force_stop_failed)
        }
    }

    private fun unbindUserService(args: Shizuku.UserServiceArgs, serviceConnection: ServiceConnection) {
        try { Shizuku.unbindUserService(args, serviceConnection, true) } catch (_: RuntimeException) { }
        serviceBound = false
        connection = null
        serviceArgs = null
    }

    private fun refresh() {
        if (closed) return
        val targets = findTargets()
        val selected = state.selectedPackage?.takeIf { current -> targets.any { it.packageName == current } }
            ?: targets.singleOrNull()?.packageName
        state = state.copy(targets = targets, selectedPackage = selected)
        refreshFrameworkScope()
        refreshShizuku()
        if (targets.isEmpty()) setStatus(R.string.game_none_installed)
    }

    private fun findTargets(): List<GameControlTarget> = packages.mapNotNull { packageName ->
        try {
            val info = activity.packageManager.getApplicationInfo(packageName, 0)
            GameControlTarget(packageName, activity.packageManager.getApplicationLabel(info).toString())
        } catch (_: PackageManager.NameNotFoundException) { null }
    }

    private fun selectedTarget() = state.targets.firstOrNull { it.packageName == state.selectedPackage }

    private fun refreshFrameworkScope() {
        state = state.copy(currentScope = FrameworkSettings.gameScope())
    }

    private fun onMainIfOpen(action: () -> Unit) {
        if (closed) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (!closed) action()
        } else {
            mainHandler.post { if (!closed) action() }
        }
    }

    private fun refreshShizuku() {
        val available = try { Shizuku.pingBinder() && !Shizuku.isPreV11() } catch (_: RuntimeException) { false }
        val granted = if (available) try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: RuntimeException) { false } else false
        state = state.copy(shizukuAvailable = available, shizukuGranted = granted)
    }

    private fun setStatus(resource: Int) {
        if (!closed) state = state.copy(status = activity.getString(resource))
    }

    override fun close() {
        closed = true
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        (activity as? LifecycleOwner)?.lifecycle?.removeObserver(lifecycleObserver)
        val args = serviceArgs
        val serviceConnection = connection
        bindTimeout?.let(mainHandler::removeCallbacks)
        bindTimeout = null
        if (args != null && serviceConnection != null) unbindUserService(args, serviceConnection)
        executor.shutdownNow()
    }

    companion object { private const val PERMISSION_REQUEST_CODE = 4107 }
}
