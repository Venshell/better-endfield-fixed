package dev.betterendfield.next;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import android.os.ParcelFileDescriptor;

import io.github.libxposed.api.XposedModule;

/** The module's only framework entry point: libxposed API 102. */
public final class XposedEntry extends XposedModule {
    private String processName;
    private final AtomicBoolean attached = new AtomicBoolean();
    private final AtomicBoolean nativeReady = new AtomicBoolean();
    /**
     * The game's own context, kept for work the command poller has to do in this
     * process - materializing a reloaded .vmd into this app's files directory,
     * which is a place the settings app cannot write to.
     */
    private volatile Context gameContext;
    /**
     * The last command the poller refused to deliver, so a payload whose own
     * precondition cannot be met is not retried four times a second.
     */
    private volatile String heldBackCommand;
    private volatile boolean thirdPartyPrepared;
    private volatile boolean thirdPartyHostExpected;
    private volatile String thirdPartyObserved = "";
    private volatile String thirdPartyRestartPending = "";
    private volatile long thirdPartyRetryAfter;
    private long thirdPartyRetryDelay = 2000;
    private final Object thirdPartyQueueLock = new Object();
    private String thirdPartyPending;
    private String thirdPartyInFlight;
    private final AtomicBoolean thirdPartyUpdating = new AtomicBoolean();

    /** This module instance, so helper classes in this package can reach hook(). */
    private static volatile XposedEntry instance;
    /** Set once the base Activity.onActivityResult relay hook is in place. */
    private static final AtomicBoolean resultRelayReady = new AtomicBoolean();
    /** Consumer for relayed host activity results (the overlay's save dialog). */
    private static volatile HostResultListener resultListener;
    /** Concrete activity classes whose own onActivityResult is already hooked. */
    private static final java.util.Set<String> hookedResultClasses = new java.util.HashSet<>();
    /** Consumer for volume key presses in the hosted activity (the overlay). */
    private static volatile VolumeKeyListener volumeListener;
    /** Concrete activity classes whose own dispatchKeyEvent is already hooked. */
    private static final java.util.Set<String> hookedKeyClasses = new java.util.HashSet<>();

    /** Receives host activity results relayed by the module's hooks. */
    interface HostResultListener {
        void onHostResult(int requestCode, int resultCode, Intent data);
    }

    /**
     * Receives a volume key press that reached the hosted activity. The overlay
     * registers here because a hidden overlay has no touch target left: while it
     * stands down for a take, the volume key is the only control on the glass.
     */
    interface VolumeKeyListener {
        void onVolumeKey(int keyCode);
    }

    /** Registers the listener that receives the host activity's results. */
    static void setActivityResultListener(HostResultListener listener) {
        resultListener = listener;
    }

    /** Registers the overlay as the consumer of volume key presses. */
    static void setVolumeKeyListener(VolumeKeyListener listener) {
        volumeListener = listener;
    }

    /** Releases the registration, but only if it is still this surface's. */
    static void clearVolumeKeyListener(VolumeKeyListener listener) {
        if (volumeListener == listener) volumeListener = null;
    }

    /** Whether this is a key the overlay may act on. */
    static boolean isVolumeKey(int keyCode) {
        return keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_MUTE;
    }

    /** Whether startActivityForResult results can reach the module at all. */
    static boolean activityResultRelayReady() {
        return resultRelayReady.get();
    }

    /**
     * Hooks the concrete activity class's own onActivityResult override. The
     * base Activity relay only fires when the override calls super; games that
     * swallow the callback need this per-class hook to see the result.
     */
    static void hookConcreteActivityResult(Class<?> cls) {
        XposedEntry module = instance;
        if (module == null) return;
        synchronized (hookedResultClasses) {
            if (!hookedResultClasses.add(cls.getName())) return;
        }
        try {
            Method method = cls.getDeclaredMethod("onActivityResult",
                    int.class, int.class, Intent.class);
            module.hook(method).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    module.dispatchActivityResult(
                            (Integer) chain.getArg(0),
                            (Integer) chain.getArg(1),
                            (Intent) chain.getArg(2));
                } catch (Throwable ignored) { /* relay must never break the game */ }
                return result;
            });
            RuntimeLog.record("activity result relay: hooked " + cls.getName());
        } catch (NoSuchMethodException noOverride) {
            // The activity inherits Activity.onActivityResult; the base relay covers it.
        } catch (Throwable error) {
            RuntimeLog.record("activity result relay on " + cls.getName() + " failed: " + error);
        }
    }

    private void installActivityResultRelay() {
        try {
            hook(Activity.class.getDeclaredMethod("onActivityResult",
                    int.class, int.class, Intent.class)).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    dispatchActivityResult(
                            (Integer) chain.getArg(0),
                            (Integer) chain.getArg(1),
                            (Intent) chain.getArg(2));
                } catch (Throwable ignored) { /* relay must never break the game */ }
                return result;
            });
            resultRelayReady.set(true);
            RuntimeLog.record("activity result relay installed (Activity base)");
        } catch (Throwable error) {
            RuntimeLog.record("activity result relay failed: " + error);
        }
    }

    private void dispatchActivityResult(int requestCode, int resultCode, Intent data) {
        HostResultListener listener = resultListener;
        if (listener != null) listener.onHostResult(requestCode, resultCode, data);
    }

    /**
     * Hooks the activity class's own {@code dispatchKeyEvent}, wherever the
     * override actually lives. An activity that overrides it without calling
     * super never reaches {@link #installVolumeKeyRelay}, and Unity's own
     * activity is exactly that kind of class, so the concrete override is
     * hooked too. Walking up to the declaring class matters: the game's activity
     * usually extends a base that carries the override, and asking only the leaf
     * class would look like "no override at all".
     */
    static void hookConcreteActivityKeys(Class<?> cls) {
        XposedEntry module = instance;
        if (module == null) return;
        Method method = null;
        for (Class<?> type = cls; type != null && type != Activity.class; type = type.getSuperclass()) {
            try {
                method = type.getDeclaredMethod("dispatchKeyEvent",
                        android.view.KeyEvent.class);
                break;
            } catch (NoSuchMethodException next) {
                // Not declared here; an ancestor may carry it.
            }
        }
        if (method == null) {
            // Activity.dispatchKeyEvent itself; the base relay already covers it.
            return;
        }
        final String owner = method.getDeclaringClass().getName();
        synchronized (hookedKeyClasses) {
            if (!hookedKeyClasses.add(owner)) return;
        }
        try {
            module.hook(method).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    relayVolumeKey(chain.getArg(0));
                } catch (Throwable ignored) { /* the relay must never break input */ }
                return result;
            });
            RuntimeLog.record("volume key relay: hooked " + owner + ".dispatchKeyEvent");
        } catch (Throwable error) {
            RuntimeLog.record("volume key relay on " + owner + " failed: " + error);
        }
    }

    /**
     * ColorOS leaves {@code Looper.mLooperExt} null on the Unity thread.
     * {@code loopOnce} then calls {@code traceEnd()} and the process dies.
     * A no-op stand-in keeps that thread looping; the main looper already has
     * a real extension and is left alone.
     */
    private void installLooperExtGuard() {
        int hooked = 0;
        for (Method method : android.os.Looper.class.getDeclaredMethods()) {
            String name = method.getName();
            if (!name.equals("loop")) continue;
            try {
                hook(method).intercept(chain -> {
                    try {
                        Object self = chain.getThisObject();
                        if (self instanceof android.os.Looper) ensureLooperExt((android.os.Looper) self);
                        try {
                            Object first = chain.getArg(0);
                            if (first instanceof android.os.Looper) ensureLooperExt((android.os.Looper) first);
                        } catch (Throwable ignored) { /* loop() has no arguments */ }
                        android.os.Looper current = android.os.Looper.myLooper();
                        if (current != null) ensureLooperExt(current);
                    } catch (Throwable ignored) { /* the guard must never break the looper */ }
                    return chain.proceed();
                });
                hooked++;
            } catch (Throwable error) {
                RuntimeLog.record("looper guard skipped " + method + ": " + error);
            }
        }
        RuntimeLog.record("looper guard installed on " + hooked + " Looper method(s)");
    }

    private static volatile Object looperExtStub;
    private static volatile java.lang.reflect.Field looperExtField;
    private static final ThreadLocal<Boolean> looperChecked = new ThreadLocal<>();

    private static void ensureLooperExt(android.os.Looper looper) {
        if (Boolean.TRUE.equals(looperChecked.get())) return;
        try {
            java.lang.reflect.Field field = looperExtField;
            if (field == null) {
                field = android.os.Looper.class.getDeclaredField("mLooperExt");
                field.setAccessible(true);
                looperExtField = field;
            }
            if (field.get(looper) != null) {
                looperChecked.set(Boolean.TRUE);
                return;
            }
            Object stub = looperExtStub;
            if (stub == null) {
                Class<?> type = Class.forName("android.os.ILooperExt");
                stub = java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                        (proxy, method, args) -> {
                            Class<?> ret = method.getReturnType();
                            if (ret == boolean.class) return Boolean.FALSE;
                            if (ret == int.class) return 0;
                            if (ret == long.class) return 0L;
                            if (ret == float.class) return 0f;
                            if (ret == double.class) return 0d;
                            return null;
                        });
                looperExtStub = stub;
            }
            field.set(looper, stub);
            looperChecked.set(Boolean.TRUE);
            RuntimeLog.record("looper guard filled a missing ILooperExt on "
                    + Thread.currentThread().getName());
        } catch (Throwable error) {
            RuntimeLog.record("looper guard failed: " + error);
        }
    }

    /**
     * Hooks the base {@code Activity.dispatchKeyEvent}, which is the first
     * Activity-level entry point a key event passes through. It is the relay
     * for every host that does not override the method.
     */
    private void installVolumeKeyRelay() {
        try {
            hook(Activity.class.getDeclaredMethod("dispatchKeyEvent",
                    android.view.KeyEvent.class)).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    relayVolumeKey(chain.getArg(0));
                } catch (Throwable ignored) { /* the relay must never break input */ }
                return result;
            });
            RuntimeLog.record("volume key relay installed (Activity.dispatchKeyEvent)");
        } catch (Throwable error) {
            RuntimeLog.record("volume key relay failed: " + error);
        }
    }

    /**
     * Reports volume key presses to the overlay. The event is never consumed:
     * the phone's volume still changes, and the relay stays additive so it can
     * never take a key away from the game. A host that both overrides
     * dispatchKeyEvent and calls super sees this twice for one press, which is
     * why the consumer is written to be idempotent.
     */
    private static void relayVolumeKey(Object event) {
        if (!(event instanceof android.view.KeyEvent)) return;
        android.view.KeyEvent key = (android.view.KeyEvent) event;
        if (key.getAction() != android.view.KeyEvent.ACTION_DOWN) return;
        if (key.getRepeatCount() != 0) return;  // one interruption per press
        if (!isVolumeKey(key.getKeyCode())) return;
        VolumeKeyListener listener = volumeListener;
        if (listener == null) return;
        try {
            listener.onVolumeKey(key.getKeyCode());
        } catch (Throwable ignored) { /* the overlay must never break input */ }
    }

    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
        Log.i("BetterEndfield.Runtime", "onModuleLoaded process=" + processName);
    }

    @Override public void onPackageLoaded(PackageLoadedParam param) {
        onPackageAvailable("onPackageLoaded", param.getPackageName());
    }

    @Override public void onPackageReady(PackageReadyParam param) {
        onPackageAvailable("onPackageReady", param.getPackageName());
    }

    private void onPackageAvailable(String stage, String packageName) {
        instance = this;
        // Buffer early hook events until Application.attach provides a Context.
        try {
            RuntimeLog.record("journal build " + BuildConfig.VERSION_NAME
                    + " (" + BuildConfig.VERSION_CODE + ")");
            RuntimeLog.record(stage + " pkg=" + packageName
                    + " process=" + processName);
        } catch (Throwable journalFailure) {
            log(Log.WARN, "BetterEndfield.Runtime",
                    "journal bind failed: " + journalFailure);
        }
        if (!RuntimeBootstrap.isTarget(packageName, processName)) {
            RuntimeLog.record("skip: not a target process (package != process, "
                    + "or the module/system package itself)");
            return;
        }
        try {
            SharedPreferences settings = getRemotePreferences("module_settings");
            RuntimeLog.record("settings schemaVersion="
                    + settings.getInt("schemaVersion", 1));
            if (settings.getInt("schemaVersion", 1) != 1) {
                report("unsupported settings schema; native runtime disabled");
                return;
            }
            // The overlay's settings channel needs the token the module publishes
            // in this snapshot. Re-read through the supplier rather than capture
            // the snapshot itself: the service-backed proxy goes stale after the
            // settings app commits a new one.
            OverlaySettingsClient.initialize(() -> getRemotePreferences("module_settings"));
            ModuleConfigurations configs = ModuleConfigurations.read(settings);
            RuntimeLog.record("configs read: " + (configs.none()
                    ? "NONE selected" : configs.summary()));
            if (!attached.compareAndSet(false, true)) return;
            hook(Application.class.getDeclaredMethod("attach", Context.class)).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Application application = (Application) chain.getThisObject();
                    installPcMouseCapture(application);
                    Context context = (Context) chain.getArg(0);
                    gameContext = context;
                    RuntimeLog.bind(context);
                    ClassLoader loader = context.getClassLoader();
                    GameOverlay.install(application, loader,
                            () -> {
                                // The service-backed preferences proxy can be
                                // stale after the settings Activity commits a
                                // new snapshot. Re-acquire it when an Activity
                                // resumes or the visible deck polls, so appearance
                                // changes also reach the already running game.
                                try {
                                    return getRemotePreferences("module_settings");
                                } catch (RuntimeException unavailable) {
                                    return settings;
                                }
                            });
                    new Thread(() -> {
                        String initialIndex=settings.getString(BemInstaller.INDEX,"[]");
                        boolean skipValidation=settings.getBoolean(BemInstaller.SKIP_VALIDATION,false);
                        boolean hotSwitch=settings.getBoolean(BemInstaller.HOT_SWITCH,false);
                        boolean cloneSupport=settings.getBoolean(BemInstaller.CLONE_SUPPORT,false);
                        boolean releaseRetiredResources=settings.getBoolean(BemInstaller.RELEASE_RETIRED_RESOURCES,false);
                        boolean fastLoading=settings.getBoolean(BemInstaller.FAST_LOADING,false);
                        boolean installedPrepared=false;
                        BemInstalledResources.Source modelSource=
                            name -> new ParcelFileDescriptor.AutoCloseInputStream(openRemoteFile(name));
                        try {
                            BemInstalledResources.configuration=BemInstalledResources.prepare(context,
                                initialIndex,modelSource,this::report,skipValidation,hotSwitch,fastLoading,true,cloneSupport,releaseRetiredResources);
                            installedPrepared=true;
                        } catch(Exception error) {report("Installed BEM preparation failed: "+error);}
                        // The imported MMD files land in the game's own directory
                        // before the native library is loaded, because the camera
                        // configuration that names them is read during that load.
                        try {
                            MmdSlotFiles.prepare(context, settings.getString("mmd_slots",""),
                                name -> new ParcelFileDescriptor.AutoCloseInputStream(openRemoteFile(name)),
                                this::report);
                        } catch(Exception error) {report("MMD slot preparation failed: "+error);}
                        // Library works are folders rather than slots, so they are
                        // materialized by their own pass - but before the native
                        // library loads, for the same reason: the camera
                        // configuration that names the selected work is read
                        // during that load.
                        try {
                            MmdSlotFiles.prepareWorks(context,
                                settings.getString(ModuleSettings.MMD_WORKS,""),
                                name -> new ParcelFileDescriptor.AutoCloseInputStream(openRemoteFile(name)),
                                this::report);
                        } catch(Exception error) {report("MMD library preparation failed: "+error);}
                        MediaScreenFiles.prepare(context, settings.getString(ModuleSettings.MEDIA_CONFIGURATION, ""),
                                settings.getString(MediaScreenFiles.INDEX, "[]"), modelSource, this::report);
                        thirdPartyHostExpected = !configs.thirdParty().isEmpty();
                        if (thirdPartyHostExpected) {
                            try {
                                ThirdPartyRuntimeMaterializer.prepareChecked(context, configs.thirdParty(),
                                        name -> new ParcelFileDescriptor.AutoCloseInputStream(openRemoteFile(name)),
                                        this::report);
                            } catch (Exception error) {
                                thirdPartyHostExpected = false;
                                report("Third-party preparation failed: " + error);
                            }
                        }
                        thirdPartyPrepared = true;
                        // The imported .vmd rides the same remote file space the
                        // packages do; its declared length comes from the same
                        // settings snapshot the configurations were read from.
                        RuntimeBootstrap.prepare(application, context, loader, configs,
                                settings.getLong(ModuleSettings.VMD_BYTES, 0L),
                                name -> new ParcelFileDescriptor.AutoCloseInputStream(openRemoteFile(name)),
                                this::installFrames, this::report, settings);
                        GlobalFovUpdater.start(()->getRemotePreferences("module_settings"));
                        if(installedPrepared) BemHotSwitchUpdater.start(context,
                            ()->getRemotePreferences("module_settings"),modelSource,initialIndex,
                            skipValidation,hotSwitch,fastLoading,cloneSupport,releaseRetiredResources,this::report);
                    },"BetterEndfield-InstalledModels").start();
                } catch (Throwable error) { report("bootstrap failed: " + error); }
                return result;
            });
            report("attached to " + packageName + " via " + stage);
            RuntimeLog.record("attached; Application.attach hook installed, "
                    + "waiting for game startup");
            installActivityResultRelay();
            installVolumeKeyRelay();
            installLooperExtGuard();
            startRemoteCommandPoller();
        } catch (Throwable error) { report("entry failed: " + error); }
    }

    private void installFrames(ClassLoader loader, java.util.function.BooleanSupplier callback) throws Throwable {
        Class<?> unity = null;
        for (int attempt = 0; attempt < 120; ++attempt) {
            try { unity = Class.forName("com.unity3d.player.UnityPlayer", false, loader); break; }
            catch (ClassNotFoundException missing) { Thread.sleep(100); }
        }
        if (unity == null) throw new ClassNotFoundException("UnityPlayer did not become available");
        CopyOnWriteArrayList<HookHandle> hooks = new CopyOnWriteArrayList<>();
        AtomicBoolean complete = new AtomicBoolean();
        AtomicBoolean announced = new AtomicBoolean();
        java.util.concurrent.atomic.AtomicInteger frames = new java.util.concurrent.atomic.AtomicInteger();
        try {
            for (Method method : unity.getDeclaredMethods()) {
                if (!method.getName().equals("nativeRender") || method.getReturnType() != boolean.class) continue;
                HookHandle handle = hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    // Frame probes: the pipeline previously stalled here with
                    // zero evidence. Record the opening frames so a dead hook is
                    // visible in the journal; after that the per-frame dispatch
                    // below is itself the evidence that frames keep arriving.
                    int seen = frames.incrementAndGet();
                    if (seen <= 3 || seen == 30) {
                        RuntimeLog.record("nativeRender frame " + seen
                                + " result=" + result);
                    }
                    if (Boolean.TRUE.equals(result)) {
                        if (!complete.get() && callback.getAsBoolean()) {
                            if (announced.compareAndSet(false, true)) {
                                RuntimeLog.record("first successful Unity frame; loading native runtime");
                            }
                            complete.set(true);
                        }
                        // nativeRender is the engine's per-frame tick and the
                        // only one a frozen world still runs. The native frame
                        // clients are driven from it, so the hook stays
                        // installed while the runtime is live instead of being
                        // removed the moment the runtime has loaded.
                        if (RuntimeBootstrap.loaded()) {
                            try {
                                NativeCommandBridge.frame();
                            } catch (Throwable error) {
                                hooks.forEach(HookHandle::unhook);
                                report("PC layout frame dispatch unavailable: " + error);
                            }
                        } else if (complete.get()) {
                            hooks.forEach(HookHandle::unhook);
                        }
                    }
                    return result;
                });
                hooks.add(handle);
                if (complete.get() && !RuntimeBootstrap.loaded()) handle.unhook();
            }
            RuntimeLog.record("hooked " + hooks.size()
                    + " UnityPlayer.nativeRender method(s), waiting for frames");
            if (hooks.isEmpty()) throw new NoSuchMethodException("UnityPlayer.nativeRender");
        } catch (Throwable error) {
            hooks.forEach(HookHandle::unhook);
            throw error;
        }
    }

    /**
     * Gives the PC layout a relative-mouse path. Unity's own input entry is the
     * absolute-touch one, so once the game hides the cursor and asks for
     * relative motion there is nothing on this platform to deliver it: the
     * panel has to take platform pointer capture on the Unity view and hand the
     * deltas to the native bridge. Only the three dispatch methods are hooked;
     * a game-owned capture on any other view keeps its original listener.
     */
    private void installPcMouseCapture(Application application) {
        CopyOnWriteArrayList<HookHandle> hooks = new CopyOnWriteArrayList<>();
        try {
            // dispatchCapturedPointerEvent is overridden in ViewGroup, so a hook
            // on View alone would miss every container that overrides it.
            for (Class<?> type : new Class<?>[]{android.view.View.class, android.view.ViewGroup.class}) {
                hooks.add(hook(type.getDeclaredMethod("dispatchCapturedPointerEvent", android.view.MotionEvent.class))
                        .intercept(chain -> {
                            if (PcUiMouseBridge.capturedEvent((android.view.View) chain.getThisObject(),
                                    (android.view.MotionEvent) chain.getArg(0))) return true;
                            return chain.proceed();
                        }));
            }
            hooks.add(hook(android.view.View.class.getDeclaredMethod("dispatchPointerCaptureChanged", boolean.class))
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        PcUiMouseBridge.captureChanged((android.view.View) chain.getThisObject(), (boolean) chain.getArg(0));
                        return result;
                    }));
            hooks.add(hook(android.view.View.class.getDeclaredMethod("dispatchWindowFocusChanged", boolean.class))
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        PcUiMouseBridge.windowFocusChanged((android.view.View) chain.getThisObject(), (boolean) chain.getArg(0));
                        return result;
                    }));
            PcUiMouseBridge.install(application, this::report);
        } catch (Throwable error) {
            hooks.forEach(HookHandle::unhook);
            report("PC mouse capture bridge unavailable: " + error);
        }
    }

    private void report(String message) {
        log(Log.INFO, "BetterEndfield.Xposed", message);
        RuntimeLog.record(message);
    }

    /**
     * Splits a runtime command payload into its command name and body.
     *
     * The shape is fixed by the native pump: {@code BE_COMMAND_V1\n<generation>
     * \n<command>\n<value>}. Returns null when the header is not there, which is
     * the only case the caller treats as "not for me".
     */
    private static String[] splitCommand(String payload) {
        int first = payload.indexOf('\n', 14);
        int second = first < 0 ? -1 : payload.indexOf('\n', first + 1);
        if (first < 0 || second < 0) return null;
        return new String[]{payload.substring(first + 1, second), payload.substring(second + 1)};
    }

    /**
     * Makes sure a command's own preconditions hold before it is handed to the
     * native module, and returns whether it may be delivered at all.
     *
     * Only the camera configuration needs this. A configuration that names the
     * imported .vmd points at a file inside the game's own files directory, and
     * the native reader takes a path - but the two processes are different UIDs,
     * so the settings app cannot put the file there. It publishes the payload
     * into the framework's remote space instead, and this process copies it in.
     * That copy normally happens once, at start-up; a reload in the middle of a
     * session would otherwise point the module at a file that does not exist
     * yet and report "could not be opened" for a motion the user just imported.
     *
     * A configuration whose payload cannot be materialized is held back: keeping
     * the previous, working configuration is better than delivering one that is
     * known to be unreadable.
     */
    private boolean prepareCommand(String payload) {
        String[] parts = splitCommand(payload);
        if (parts == null || !"camera_config".equals(parts[0])) return true;
        // Every camera configuration write is also the moment the gyroscope
        // settings may have changed: they are edited on the same screen and
        // committed in the same transaction. Re-arming here keeps the sensor in
        // step with the configuration without a game restart, and without a
        // second cross-process channel.
        Context game = gameContext;
        if (game != null) {
            // The snapshot is re-read here for the same reason the .vmd length
            // below is: the service-backed proxy can be stale, and this is the
            // moment the new values were just committed. An unavailable
            // snapshot is not fatal to the reload - the sensor simply keeps its
            // previous settings.
            try {
                RuntimeBootstrap.refreshGyroscope(game,
                        getRemotePreferences("module_settings"), RuntimeLog::record);
            } catch (RuntimeException unavailable) {
                RuntimeLog.record("gyroscope refresh skipped: settings snapshot unavailable");
            }
        }
        if (!parts[1].contains(ModuleSettings.VMD_FILE_SLOT)) return true;
        if (game == null) return true;
        long declared;
        try {
            declared = getRemotePreferences("module_settings")
                    .getLong(ModuleSettings.VMD_BYTES, 0L);
        } catch (RuntimeException unavailable) {
            RuntimeLog.record("configuration reload held back: settings snapshot unavailable");
            return false;
        }
        String path = CameraVmdFile.materialize(game, declared,
                name -> new ParcelFileDescriptor.AutoCloseInputStream(openRemoteFile(name)),
                RuntimeLog::record);
        if (path.isEmpty()) {
            RuntimeLog.record("configuration reload held back: the imported .vmd is not "
                    + "materialized yet (declared " + declared + " bytes)");
            return false;
        }
        return true;
    }

    private void startRemoteCommandPoller() {
        Thread worker = new Thread(() -> {
            String last = "";
            String lastStatus = "";
            long nativeLogOffset = 0L;
            long lastNativeSerial = 0L;
            String pendingNativeLine = "";
            long nextNativeProbeAt = 0L;
            while (attached.get()) {
                try {
                    maybeUpdateThirdParty();
                    boolean present = false;
                    for (String file : listRemoteFiles()) if ("command.next".equals(file)) { present = true; break; }
                    long now = System.currentTimeMillis();
                    // Native module logs (hook results, "Actions enabled ...",
                    // dash session events) reach the panel journal here: the
                    // relay publishes them to native.log, we tail it.
                    StringBuilder fresh = new StringBuilder();
                    long[] nativeLogCursor = {nativeLogOffset};
                    NativeCommandBridge.tailNativeLog(fresh, nativeLogCursor);
                    // tailNativeLog reports the advanced cursor through the
                    // array. Without this write-back every poll re-read the
                    // log from byte 0 (capped at 64 KB), so once the file grew
                    // past the cap the newest lines never reached the panel;
                    // the serial filter masked the replay but not the loss.
                    nativeLogOffset = nativeLogCursor[0];
                    if (fresh.length() > 0) {
                        pendingNativeLine += fresh.toString();
                        int newline;
                        while ((newline = pendingNativeLine.indexOf('\n')) >= 0) {
                            String line = pendingNativeLine.substring(0, newline).trim();
                            pendingNativeLine = pendingNativeLine.substring(newline + 1);
                            if (line.isEmpty()) continue;
                            // Native lines carry a monotonic "#<serial> " prefix.
                            // A journal truncate resets the byte offset, and the
                            // next tail would replay already-recorded lines; a
                            // serial at or below the last seen one is a replay.
                            long serial = -1L;
                            if (line.startsWith("#")) {
                                int space = line.indexOf(' ');
                                if (space > 1) {
                                    try {
                                        serial = Long.parseLong(line.substring(1, space));
                                    } catch (NumberFormatException malformed) {
                                        serial = -1L;
                                    }
                                    if (serial > 0) line = line.substring(space + 1);
                                    else serial = -1L;
                                }
                            }
                            if (serial > 0) {
                                if (serial <= lastNativeSerial) continue;
                                lastNativeSerial = serial;
                            }
                            RuntimeLog.record("[native] " + line);
                        }
                        if (pendingNativeLine.length() > 4096) pendingNativeLine = "";
                    }
                    if (present && (nativeReady.get() || now >= nextNativeProbeAt)) {
                        // status() reads the file the native relay publishes;
                        // empty means the runtime has not loaded (yet).
                        String status = NativeCommandBridge.status();
                        if (!status.isEmpty()) {
                            if (nativeReady.compareAndSet(false, true)) {
                                RuntimeLog.record("native command bridge reachable");
                            }
                            if (!status.equals(lastStatus)) {
                                // FrameworkSettings.writeRemoteStatus is dead in
                                // the game process (its service binder only binds
                                // in the module app); the journal's remote
                                // preference is the proven channel.
                                if (RuntimeLog.setStatus(status)) lastStatus = status;
                            }
                        } else if (nativeReady.compareAndSet(true, false)) {
                            RuntimeLog.record("native command bridge status empty");
                            nextNativeProbeAt = now + 10000L;
                        }
                    }
                    if (!present) { Thread.sleep(250); continue; }
                    try (ParcelFileDescriptor descriptor = openRemoteFile("command.next");
                        BufferedReader reader = new BufferedReader(new InputStreamReader(
                                new ParcelFileDescriptor.AutoCloseInputStream(descriptor),
                                java.nio.charset.StandardCharsets.UTF_8))) {
                        StringBuilder content = new StringBuilder(); String line;
                        while ((line = reader.readLine()) != null) content.append(line).append('\n');
                        String value = content.toString();
                        if (!value.isEmpty() && !value.equals(last)
                                && !value.equals(heldBackCommand)) {
                            // Returns false only while the relay channel has not
                            // been configured; otherwise the command sits in the
                            // runtime's pump slot until the runtime consumes it.
                            if (prepareCommand(value) && NativeCommandBridge.submit(value)) {
                                last = value;
                            } else {
                                // Tried once per distinct payload: the settings
                                // app rewrites the whole command whenever the
                                // user changes anything, so a later attempt
                                // arrives as a different value.
                                heldBackCommand = value;
                            }
                        }
                    }
                } catch (java.io.FileNotFoundException missing) {
                    // The UI has not issued a command. Avoid repeatedly asking
                    // the framework to open a file that does not exist.
                } catch (Throwable ignored) { /* remote file may be unavailable during reload */ }
                try { Thread.sleep(250); } catch (InterruptedException stopped) { return; }
            }
        }, "BetterEndfieldNext-Commands");
        worker.setDaemon(true); worker.start();
    }

    /** The existing command poller detects changes; ZIP preparation runs only for a new snapshot. */
    private void maybeUpdateThirdParty() {
        Context context = gameContext;
        if (!thirdPartyPrepared || context == null) return;
        String current = getRemotePreferences("module_settings")
                .getString(ThirdPartyModuleStore.KEY, "");
        if (current == null || current.isEmpty()) return;
        synchronized (thirdPartyQueueLock) {
            if (current.equals(thirdPartyObserved) || current.equals(thirdPartyRestartPending)
                    || current.equals(thirdPartyInFlight) || current.equals(thirdPartyPending)
                    || android.os.SystemClock.elapsedRealtime() < thirdPartyRetryAfter) return;
            thirdPartyPending = current;
        }
        startThirdPartyWorker(context);
    }

    private void startThirdPartyWorker(Context context) {
        if (!thirdPartyUpdating.compareAndSet(false, true)) return;
        Thread worker = new Thread(() -> {
            try {
                while (true) {
                    String next;
                    synchronized (thirdPartyQueueLock) {
                        if (android.os.SystemClock.elapsedRealtime() < thirdPartyRetryAfter) break;
                        next = thirdPartyPending;
                        thirdPartyPending = null;
                        thirdPartyInFlight = next;
                    }
                    if (next == null) break;
                    try {
                        ThirdPartyRuntimeMaterializer.Publication publication =
                                ThirdPartyRuntimeMaterializer.publishChecked(context, next,
                                name -> new ParcelFileDescriptor.AutoCloseInputStream(openRemoteFile(name)),
                                this::report);
                        if (thirdPartyHostExpected) {
                            ThirdPartyRuntimeMaterializer.awaitReceipt(publication);
                            synchronized (thirdPartyQueueLock) { thirdPartyObserved = next; }
                        } else {
                            synchronized (thirdPartyQueueLock) { thirdPartyRestartPending = next; }
                            report("Third-party generation prepared; restart the game to start the module host");
                        }
                        synchronized (thirdPartyQueueLock) {
                            thirdPartyInFlight = null;
                            thirdPartyRetryDelay = 2000;
                        }
                    } catch (Exception error) {
                        report("Third-party update failed: " + error);
                        synchronized (thirdPartyQueueLock) {
                            thirdPartyInFlight = null;
                            thirdPartyRetryAfter = android.os.SystemClock.elapsedRealtime() + thirdPartyRetryDelay;
                            thirdPartyRetryDelay = Math.min(thirdPartyRetryDelay * 2, 60000);
                            if (next.equals(thirdPartyPending)) thirdPartyPending = null;
                        }
                        break;
                    }
                }
            } finally {
                thirdPartyUpdating.set(false);
                synchronized (thirdPartyQueueLock) {
                    if (thirdPartyPending != null
                            && android.os.SystemClock.elapsedRealtime() >= thirdPartyRetryAfter)
                        startThirdPartyWorker(context);
                }
            }
        }, "BetterEndfield-ThirdPartyUpdate");
        worker.setDaemon(true);
        worker.start();
    }
}
