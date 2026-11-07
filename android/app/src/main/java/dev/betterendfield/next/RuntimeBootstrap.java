package dev.betterendfield.next;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.system.Os;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Framework-independent catalog preparation and first-frame native loading. */
final class RuntimeBootstrap {
    static final String MODULE_PACKAGE = "dev.betterendfield.next";
    private static final AtomicBoolean PREPARED = new AtomicBoolean();
    private static final AtomicBoolean LOADING = new AtomicBoolean();
    private static final AtomicInteger ATTEMPTS = new AtomicInteger();
    private static volatile boolean loaded;
    private static volatile String failure = Text.of("等待游戏首帧");

    static String failure() { return failure; }

    interface FrameTrigger {
        // Keep a successful frame hook for the Unity-thread control pump;
        // remove it only when loading permanently fails.
        void install(ClassLoader loader, BooleanSupplier callback) throws Throwable;
    }

    static boolean isTarget(String packageName, String processName) {
        return packageName != null && packageName.equals(processName) && !MODULE_PACKAGE.equals(packageName)
                && !"android".equals(packageName);
    }

    /**
     * Whether the native runtime has finished loading in this process.
     *
     * <p>The overlay asks because it has to word a change honestly: before the
     * library is in, nothing can be applied live, so a setting the panel just
     * wrote is "on the next launch" rather than "in the game".
     */
    static boolean loaded() {
        return loaded;
    }

    static void prepare(Application application, Context context, ClassLoader loader,
            ModuleConfigurations configs, long vmdBytes, BemInstalledResources.Source vmdSource,
            FrameTrigger trigger, Consumer<String> log) {
        prepare(application, context, loader, configs, vmdBytes, vmdSource, trigger, log,
                FrameworkSettings.open(context));
    }

    /**
     * The framework snapshot is passed in rather than re-read here, because the
     * only readable copy of the module's settings is the one the host resolved
     * before calling in: {@code context} is the game's Application, and
     * {@link FrameworkSettings#open} on it would open the game's own
     * preferences file, which no settings screen ever writes.
     */
    static void prepare(Application application, Context context, ClassLoader loader,
            ModuleConfigurations configs, long vmdBytes, BemInstalledResources.Source vmdSource,
            FrameTrigger trigger, Consumer<String> log, SharedPreferences settings) {
        if (configs.none() && !debugResourceProbe() && !lookProbeRequested(settings)
                && customModelConfig().isEmpty()) {
            log.accept("no modules selected; native runtime skipped");
            return;
        }
        if (!PREPARED.compareAndSet(false, true)) return;
        // The panel presses keys through a file relay (NativeCommandBridge /
        // input_relay.cpp), not JNI: the runtime registers under the game's
        // classloader, the panel's classes under the module classloader, and
        // Android scopes both JNI lookup and .so openings per classloader.
        // Opening the channel here also truncates the stream, so a fresh
        // session never replays the previous one's events.
        File relayDir = new File(application.getFilesDir(), "betterendfield");
        if (!relayDir.isDirectory() && !relayDir.mkdirs()) {
            log.accept("input relay directory unavailable: " + relayDir);
        }
        NativeCommandBridge.configure(
                new File(relayDir, "input.latch"), new File(relayDir, "status.txt"),
                new File(relayDir, "native.log"));
        Thread worker = new Thread(() -> {
            String poseRoot = "";
            String headwearRoot = "";
            if (!configs.voice().isEmpty() || configs.needsActionPoses()) {
                try {
                    Context module = context.createPackageContext(MODULE_PACKAGE,
                            Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);
                    if (!configs.voice().isEmpty()) {
                        VoiceCatalogMaterializer.Result result =
                                VoiceCatalogMaterializer.prepare(context, module, configs.voice());
                        log.accept("catalog prepared: " + result.summary());
                        for (String failure : result.failures()) log.accept(failure);
                    }
                    if (configs.needsActionPoses()) {
                        poseRoot = ActionPoseAssets.materialize(context, module, log);
                    }
                } catch (Throwable error) {
                    log.accept("asset preparation failed: " + error);
                }
            }
            if (!configs.camera().isEmpty()) {
                try {
                    Context module = context.createPackageContext(MODULE_PACKAGE,
                            Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);
                    headwearRoot = HeadwearAssets.materialize(context, module, log);
                } catch (Exception error) {
                    log.accept("headwear asset preparation failed: " + error);
                }
            }
            String actionPoseRoot = poseRoot;
            String preparedHeadwearRoot = headwearRoot;
            // The camera module takes a path, so the imported .vmd has to be a
            // real file inside the game's own data directory before the native
            // loader is handed the configuration that names it.
            if (configs.needsVmdCameraFile()) {
                CameraVmdFile.materialize(context, vmdBytes, vmdSource, log);
            }
            try {
                trigger.install(loader,
                        () -> load(application, context, configs, actionPoseRoot,
                                preparedHeadwearRoot, settings, log));
                log.accept("waiting for first successful Unity frame");
            } catch (Throwable error) {
                failure = Text.of("Unity 首帧入口不可用");
                log.accept("Unity frame trigger unavailable: " + error);
            }
        }, "BetterEndfieldNext-Catalog");
        worker.setDaemon(true);
        worker.start();
    }

    private static boolean load(Application application, Context context,
            ModuleConfigurations configs, String actionPoseRoot, String headwearRoot,
            SharedPreferences settings, Consumer<String> log) {
        if (loaded || ATTEMPTS.get() >= 3) return true;
        if (!LOADING.compareAndSet(false, true)) return false;
        try {
            // Recheck after acquiring the guard: another frame may have completed.
            if (loaded || ATTEMPTS.get() >= 3) return true;
            ATTEMPTS.incrementAndGet();
            log.accept("native load attempt " + ATTEMPTS.get() + "/3 starting");
            Context module = context.createPackageContext(MODULE_PACKAGE,
                    Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);
            File library = new File(module.getApplicationInfo().nativeLibraryDir,
                    "libbetterendfieldnext_android.so");
            if (!library.isFile()) throw new IllegalStateException("missing library: " + library);
            Os.setenv("BETTER_ENDFIELD_NEXT_VOICE_RULES", configs.voice(), true);
            Os.setenv("BETTER_ENDFIELD_NEXT_MODEL_CONFIG", configs.model(), true);
            Os.setenv("BETTER_ENDFIELD_NEXT_UI_CONFIG", configs.ui(), true);
            Os.setenv("BETTER_ENDFIELD_NEXT_CAMERA_CONFIG", resolveFiles(configs.camera(), context), true);
            if (!headwearRoot.isEmpty()) {
                Os.setenv("BETTER_ENDFIELD_NEXT_HEADWEAR_DIRECTORY", headwearRoot, true);
                log.accept("bundled headwear catalog available");
            } else {
                Os.unsetenv("BETTER_ENDFIELD_NEXT_HEADWEAR_DIRECTORY");
            }
            Os.setenv("BETTER_ENDFIELD_NEXT_ACTIONS_CONFIG", configs.actions(), true);
            Os.setenv("BETTER_ENDFIELD_NEXT_MEDIA_SCREEN_CONFIG", MediaScreenFiles.configuration, true);
            Os.setenv("BETTER_ENDFIELD_NEXT_APPEARANCE_CONFIG", configs.appearance(), true);
            Os.setenv("BETTER_ENDFIELD_NEXT_RUNTIME_LOCK", new File(context.getCacheDir(), "betterendfieldnext-runtime.lock").getAbsolutePath(), true);
            Os.setenv("BETTER_ENDFIELD_NEXT_ACTIONS_ASSET_ROOT", actionPoseRoot, true);
            Os.setenv("BETTER_ENDFIELD_NEXT_CUSTOM_MODEL_PROBE", debugResourceProbe() ? "1" : "0", true);
            Os.setenv("BETTER_ENDFIELD_NEXT_FP_LOOK_PROBE", lookProbeRequested(settings) ? "1" : "0", true);
            Os.setenv("BETTER_ENDFIELD_NEXT_CUSTOM_MODEL_CONFIG", customModelConfig(), true);
            Os.setenv("BETTER_ENDFIELD_NEXT_THIRD_PARTY_INDEX",
                    ThirdPartyRuntimeMaterializer.indexPath, true);
            Os.setenv("BETTER_ENDFIELD_NEXT_VOICE_CATALOG_ROOT",
                    new File(context.getFilesDir(), "betterendfield/catalog").getAbsolutePath(), true);
            // Where the MMD director looks for library works, and where the
            // imported slots were copied by MmdSlotFiles. Set unconditionally:
            // an empty directory is a valid answer ("no works"), and leaving the
            // variable unset made the director scan an empty path, which reports
            // as a broken library rather than as an empty one.
            Os.setenv("BETTER_ENDFIELD_NEXT_MMD_ROOT",
                    new File(context.getFilesDir(), ModuleSettings.MMD_SLOT_DIRECTORY).getAbsolutePath(), true);
            // The diagnostics file is the only log sink a release build has:
            // logcat is often suppressed for an injected process, and the ring
            // that feeds the on-device journal is bounded, so a burst of module
            // init lines can evict a probe's output before the relay drains it.
            // A caller that set the path before the process started keeps its
            // choice — overwriting it here would silently send the probe's
            // output somewhere the caller is not reading.
            if (System.getenv("BETTER_ENDFIELD_NEXT_DIAGNOSTICS_PATH") == null) {
                Os.setenv("BETTER_ENDFIELD_NEXT_DIAGNOSTICS_PATH", BuildConfig.DEBUG
                        ? new File(context.getCacheDir(), "betterendfield-diagnostics.log").getAbsolutePath()
                        : lookProbeJournalPath(context), true);
            }
            Os.setenv("BETTER_ENDFIELD_NEXT_INPUT_FILE",
                    new File(context.getFilesDir(), "betterendfield/input.latch").getAbsolutePath(), true);
            Os.setenv("BETTER_ENDFIELD_NEXT_STATUS_FILE",
                    new File(context.getFilesDir(), "betterendfield/status.txt").getAbsolutePath(), true);
            Os.setenv("BETTER_ENDFIELD_NEXT_NATIVE_LOG",
                    new File(context.getFilesDir(), "betterendfield/native.log").getAbsolutePath(), true);
            // The library has to be opened under the game's classloader -- its
            // whole purpose is to register against the game's Unity player, and
            // Android scopes both .so openings and JNI lookup per classloader.
            // The PC mouse natives, though, live on a class from the module
            // classloader, so JNI_OnLoad registers them through the *context*
            // classloader of the thread that calls in. Point it at the module's
            // own for the duration of the load and put it back afterwards.
            Thread current = Thread.currentThread();
            ClassLoader previous = current.getContextClassLoader();
            try {
                current.setContextClassLoader(NativeCommandBridge.class.getClassLoader());
                loadIntoTargetNamespace(library.getAbsolutePath(), context.getClassLoader(),
                        application.getClass());
            } finally {
                current.setContextClassLoader(previous);
            }
            loaded = true;
            failure = "";
            log.accept("native runtime loaded; " + configs.summary());
            log.accept("panel input channel: file relay "
                    + new File(context.getFilesDir(), "betterendfield/input.latch"));
            startGyroscope(application, settings, log);
        } catch (Throwable error) {
            failure = Text.of("运行时载入失败：") + error.getClass().getSimpleName();
            log.accept("native runtime load attempt " + ATTEMPTS.get() + "/3 failed: " + error);
        } finally {
            LOADING.set(false);
        }
        return loaded || ATTEMPTS.get() >= 3;
    }

    /**
     * Arms the gyroscope look source in this process.
     *
     * The sensor is only registered once the native runtime has loaded: until
     * then there is no camera module to steer and no relay to carry the deltas,
     * so every sample would be written to a file nobody reads.
     *
     * The configuration comes from the framework snapshot the host resolved,
     * not from a file opened here. That is not a style choice: {@code
     * FrameworkSettings.open(context)} with the context this process has would
     * open the <em>game's</em> own "module_settings", which no settings screen
     * ever writes, so every key would read as its default and the switch would
     * appear dead. The snapshot is the copy that actually carries the module's
     * values, and re-reading it on each {@code camera_config} reload is what
     * lets a toggle take effect without a game restart.
     *
     * A missing gyroscope is reported rather than silently ignored, because
     * "the switch is on and nothing happens" is otherwise indistinguishable from
     * a broken mapping. A switch that is simply off says nothing at all, so the
     * journal does not grow a line per launch.
     */
    private static void startGyroscope(Context context, SharedPreferences settings,
            Consumer<String> log) {
        try {
            ModuleSettings.FirstPersonGyro gyro = ModuleSettings.readFirstPersonGyro(settings);
            GyroscopeController controller = GYROSCOPE.get();
            if (controller == null) {
                controller = new GyroscopeController(context);
                GYROSCOPE.set(controller);
            }
            if (!gyro.enabled()) {
                controller.stop();
                return;
            }
            if (!controller.available()) {
                log.accept("gyroscope look source requested, but this device has no "
                        + "TYPE_GYROSCOPE sensor");
                return;
            }
            final boolean running = controller.start(gyro);
            log.accept(running
                    ? "gyroscope look source active (horizontal=" + gyro.horizontalSensitivity()
                            + " vertical=" + gyro.verticalSensitivity()
                            + " deadzone=" + gyro.deadzone() + " smoothing=" + gyro.smoothing() + ")"
                    : "gyroscope look source could not be registered");
        } catch (Throwable error) {
            log.accept("gyroscope look source failed to start: " + error);
        }
    }

    /** The single controller for this process; null until the runtime loads. */
    private static final AtomicReference<GyroscopeController> GYROSCOPE = new AtomicReference<>();

    /**
     * Re-reads the stored gyroscope settings and re-arms or stops the sensor.
     * Called when the settings screen commits a change, so a toggle takes effect
     * without a game restart.
     */
    static void refreshGyroscope(Context context, SharedPreferences settings,
            Consumer<String> log) {
        if (GYROSCOPE.get() == null && !ModuleSettings.readFirstPersonGyro(settings).enabled()) {
            return;
        }
        startGyroscope(context, settings, log);
    }

    // Runtime.nativeLoad is a non-SDK interface, blocked when targeting API 35+.
    // There is no public replacement: System.load/nativeLoad can only open a
    // library into the *calling* classloader, while this module has to load
    // libbetterendfieldnext_android.so into the game's classloader namespace --
    // Android scopes both JNI lookup and .so openings per classloader, and the
    // runtime registers itself against the game's Unity player. The access is a
    // deliberate, static target: do not replace it with a public API. Failure is
    // already treated as normal (see load(): 3-arg -> 2-arg fallback, retries,
    // then a log line), so the check is suppressed here rather than module-wide
    // to keep any future accidental blocked-API use visible to lint.
    @SuppressLint("BlockedPrivateApi")
    private static void loadIntoTargetNamespace(String path, ClassLoader loader,
            Class<?> caller) throws ReflectiveOperationException {
        Method method;
        Object[] args;
        try {
            method = Runtime.class.getDeclaredMethod("nativeLoad", String.class, ClassLoader.class, Class.class);
            args = new Object[]{path, loader, caller};
        } catch (NoSuchMethodException unavailable) {
            method = Runtime.class.getDeclaredMethod("nativeLoad", String.class, ClassLoader.class);
            args = new Object[]{path, loader};
        }
        method.setAccessible(true);
        Object error = method.invoke(null, args);
        if (error != null) throw new UnsatisfiedLinkError(error.toString());
    }

    /**
     * Expands the path placeholders a configuration may carry into paths only
     * this process can know.
     *
     * {@code %files%} is the game's own files directory. The settings app cannot
     * write it out: the two processes are different UIDs, so it can neither read
     * nor create anything there, and it also cannot tell which user or cloned
     * profile the game will start under. This is the single point that holds both
     * the configuration string and the directory it has to name.
     */
    private static String resolveFiles(String configuration, Context context) {
        if (configuration.isEmpty() || configuration.indexOf('%') < 0) return configuration;
        return configuration.replace("%files%", context.getFilesDir().getAbsolutePath());
    }

    private static boolean debugResourceProbe() {
        if (!BuildConfig.DEBUG) return false;
        try {
            Method get = Class.forName("android.os.SystemProperties")
                    .getDeclaredMethod("get", String.class, String.class);
            return "1".equals(get.invoke(null, "debug.betterendfieldnext.resource_probe", "0"));
        } catch (ReflectiveOperationException unavailable) { return false; }
    }

    /**
     * Read-only metadata probe for the first-person look entry point.
     *
     * The gate is a {@link ModuleSettings} preference, not a system property or
     * an environment variable. Both of those were tried and both fail on the
     * only device this runs on: the phone is not rooted, so a release install
     * cannot be started with a variable in its environment, and a system
     * property is unusable because {@code setprop} cannot influence a process
     * that is already running while the module set is decided during library
     * load. The preferences file is the one channel that already crosses from
     * the settings app into the game, it needs no root, and it takes effect on
     * the next launch.
     */
    private static boolean debugLookProbe(SharedPreferences settings) {
        return settings != null && settings.getBoolean(ModuleSettings.DEBUG_FP_LOOK_PROBE, false);
    }

    static boolean lookProbeRequested(SharedPreferences settings) {
        return debugLookProbe(settings)
                || "1".equals(System.getenv("BETTER_ENDFIELD_NEXT_FP_LOOK_PROBE"));
    }

    /**
     * The probe writes its enumeration to logcat under the module tag, but an
     * injected process's logcat may be suppressed. A file beside the game's own
     * data is the reliable copy, and it is also what the settings app exports,
     * so the probe output can be read back the same way as any other journal.
     */
    static String lookProbeJournalPath(Context context) {
        return new File(context.getCacheDir(), "betterendfield-fp-look-probe.log").getAbsolutePath();
    }

    static String debugCustomModelConfig() {
        if (!BuildConfig.DEBUG) return "";
        try {
            Method get = Class.forName("android.os.SystemProperties")
                    .getDeclaredMethod("get", String.class, String.class);
            String value = (String) get.invoke(null, "debug.betterendfieldnext.custom_model_config", "");
            android.util.Log.i("BetterEndfieldNext.Debug", "custom model config present=" + (value != null && !value.isEmpty()));
            return value == null ? "" : value;
        } catch (ReflectiveOperationException unavailable) { return ""; }
    }

    static String customModelConfig() {
        String debug=debugCustomModelConfig();
        return debug.isEmpty()?BemInstalledResources.configuration:debug;
    }
}
