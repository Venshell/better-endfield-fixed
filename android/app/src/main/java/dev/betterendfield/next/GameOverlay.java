package dev.betterendfield.next;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.util.function.Supplier;

/** Activity-scoped controller. Compose owns only the two visible touch surfaces. */
final class GameOverlay {
    private final Activity activity;
    private final FrameLayout host;
    private final View handle;
    private final View panel;
    private final OverlaySurface ui;
    private final boolean preview;
    /**
     * What the native modules last reported. Deliberately separate from the
     * overlay settings: it says what the runtime reached, not what the panel
     * asked for. Refreshed whenever the journal is redrawn.
     */
    private RuntimeSnapshot snapshot = RuntimeSnapshot.offline();
    private final Supplier<SharedPreferences> settings;
    private boolean closed;
    private float xFraction = 0.02f;
    private float yFraction = 0.28f;
    private boolean autoSnap;
    private OverlayFeatures shown = OverlayFeatures.off();
    private boolean bridgeMissing;
    private boolean removedByUs;
    private boolean compositionCreated;
    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable reattachCheck = this::ensureOnTop;
    private final Runnable composeCheck = this::composeWhenAttached;
    private final Runnable deferredPlayback = this::runDeferredPlayback;
    private final Runnable takeoffCheck = this::onTakeoffCheck;
    private final Runnable lookFlush = this::flushLook;
    /**
     * How long a playback key waits after the panel is collapsed. The panel
     * covers a third of the screen and needs about 100 ms to fade out, and the
     * finger is still leaving the glass when the tap lands, so a key sent on
     * the tap itself starts the shot with the controls in frame. The wait also
     * covers the handle, which fades out with the panel: it is drawn over the
     * game, so a take started while it is still on screen would be recorded
     * with it.
     */
    private static final long DEFERRED_PLAYBACK_DELAY_MS = 1000L;
    /**
     * Gap between releasing the key latch and the pulse that stops a take. The
     * runtime latches a pulse for 180 ms and arms a playback hotkey on a rising
     * edge, so a second press inside that window reads as one long press and
     * toggles nothing at all.
     */
    private static final long STOP_EDGE_GAP_MS = 120L;
    /**
     * How long the runtime gets to report a take before the overlay assumes it
     * has none. A playback key can land on a runtime with nothing to play - a
     * preset asked for while the free camera is off, a keyframe list holding
     * fewer than two entries, a VMD that failed to load - and every one of
     * those paths returns without logging a start or a stop, so this grace is
     * the only thing between the user and an overlay that never comes back.
     * The runtime publishes its journal to a file this process tails every
     * 250 ms, so the grace is twelve times the transport latency.
     */
    private static final long TAKEOFF_GRACE_MS = 3000L;
    /**
     * How long look-pad deltas wait before they are written to the relay. A drag
     * reports at display rate - of the order of 120 events a second - while the
     * native side reads the relay every 10 ms and sums whatever it finds, so one
     * line per flush carries exactly the same total as one line per event at a
     * fraction of the writes. The wait is one frame's worth, which is below the
     * threshold where a drag starts to feel detached from the camera.
     */
    private static final long LOOK_FLUSH_INTERVAL_MS = 16L;
    /** Deltas accumulated since the last write; see LOOK_FLUSH_INTERVAL_MS. */
    private float lookPendingX;
    private float lookPendingY;
    private boolean lookFlushScheduled;
    /** Set once the relay has refused a delta, so a drag cannot spam the log. */
    private boolean lookRejected;
    private int deferredKey;
    private String deferredDescription;
    /** What the last fired playback key was, for naming the take it started. */
    private String firedDescription;
    /** The playback key the volume key is armed to stop; 0 when none runs. */
    private int runningKey;
    /** What started that take, for the journal. */
    private String runningDescription;
    /** True while the panel and the handle are both hidden for a take. */
    private boolean stoodDown;
    /** Registered with the entry so a volume key can reach this surface. */
    private final XposedEntry.VolumeKeyListener volumeKeys = code -> onVolumeKey(code);
    /** Registered with the journal so the runtime's playback events arrive here. */
    private final RuntimeLog.Observer journalLines = this::observeJournalLine;

    static void install(Application app, ClassLoader loader, Supplier<SharedPreferences> settings) {
        RuntimeLog.record("overlay install: checking UnityPlayer");
        try {
            Class.forName("com.unity3d.player.UnityPlayer", false, loader);
        } catch (ClassNotFoundException notUnity) {
            RuntimeLog.record("overlay skipped: UnityPlayer class not found");
            return;
        }
        RuntimeLog.record("overlay install: UnityPlayer found, registering lifecycle");
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            private final java.util.Map<Activity, GameOverlay> surfaces = new java.util.HashMap<>();

            @Override public void onActivityResumed(Activity activity) {
                // Make sure the save dialog's result can reach us even if this
                // activity overrides onActivityResult without calling super.
                XposedEntry.hookConcreteActivityResult(activity.getClass());
                // Likewise for the volume key: an activity that overrides
                // dispatchKeyEvent does not necessarily call super.
                XposedEntry.hookConcreteActivityKeys(activity.getClass());
                OverlayFeatures current;
                try {
                    current = OverlayFeatures.read(settings.get());
                } catch (RuntimeException unavailable) {
                    current = OverlayFeatures.off();
                }
                GameOverlay surface = surfaces.get(activity);
                if (surface != null && surface.host.getParent() == null) {
                    // Game SDKs re-call setContentView on resume, which strips
                    // every child of the content view — including our host.
                    // A detached host can never become visible again; rebuild
                    // the surface on the current content view instead.
                    surfaces.remove(activity);
                    RuntimeLog.record("overlay host detached by setContentView; rebuilding");
                    // Disarm the discarded surface's re-attach watchdog so it
                    // cannot resurrect an orphaned host next to the new one.
                    surface.remove();
                    surface = null;
                }
                if (current.panel() && surface == null) {
                    try {
                        surface = new GameOverlay(activity, false, settings);
                        surfaces.put(activity, surface);
                        RuntimeLog.record("overlay panel attached to "
                                + activity.getClass().getName());
                    } catch (Throwable error) {
                        RuntimeLog.record("overlay panel attach failed: " + error);
                        android.util.Log.e("BetterEndfieldNext.Overlay", "Unable to attach panel", error);
                    }
                }
                if (surface == null) return;
                // A setting changed while the game was in the background has to
                // reach the controls, not just the panel's visibility.
                surface.ui.resumed();
                surface.refresh();
                // Returning to the foreground is a new session for this surface:
                // a stand-down armed in the last one is over, and a handle still
                // hidden would leave the overlay with no way back in.
                surface.endStandDown("the game came back to the foreground");
                surface.updateJournal();
                surface.host.setVisibility(
                        current.panel() && !surface.closed ? View.VISIBLE : View.GONE);
                RuntimeLog.record("activity resumed: host attached="
                        + (surface.host.getParent() != null));
                // The SDK may rebuild or bury the game view after this callback
                // returns; re-check the overlay's placement once the dust
                // settles rather than trusting this instant.
                surface.mainHandler.removeCallbacks(surface.reattachCheck);
                surface.mainHandler.postDelayed(surface.reattachCheck, 400);
                surface.mainHandler.postDelayed(surface.reattachCheck, 1500);
            }

            @Override public void onActivityPaused(Activity activity) {
                GameOverlay surface = surfaces.get(activity);
                if (surface == null) return;
                // Leaving a held movement key latched would keep the camera
                // drifting for as long as the game stays in the background.
                surface.releaseHeldKeys();
                // An armed take belongs to the foreground session it was armed
                // in; firing it into a backgrounded game would be a surprise.
                surface.cancelDeferredPlayback();
                // A drag belongs to the panel the finger was on, not to a game
                // the user has just left.
                surface.dropPendingLook();
                surface.ui.paused();
                surface.host.setVisibility(View.GONE);
            }

            @Override public void onActivityDestroyed(Activity activity) {
                GameOverlay surface = surfaces.remove(activity);
                if (surface != null) {
                    surface.releaseHeldKeys();
                    surface.remove();
                }
            }

            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override public void onActivityStarted(Activity a) { }
            @Override public void onActivityStopped(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
        });
    }

    GameOverlay(Activity activity, boolean preview) {
        this(activity, preview, () -> FrameworkSettings.open(activity));
    }

    private GameOverlay(Activity activity, boolean preview, Supplier<SharedPreferences> settings) {
        this.activity = activity;
        this.preview = preview;
        this.settings = settings;
        host = new FrameLayout(activity);
        host.setClipChildren(false);
        host.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        host.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                mainHandler.post(composeCheck);
            }
            @Override public void onViewDetachedFromWindow(View v) {
                if (removedByUs) return;
                RuntimeLog.record("overlay host detached; re-attach scheduled");
                mainHandler.removeCallbacks(reattachCheck);
                mainHandler.postDelayed(reattachCheck, 400);
            }
        });
        host.setOnApplyWindowInsetsListener((view, insets) -> {
            host.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            host.post(this::layout);
            return insets;
        });
        ui = new OverlaySurface(activity, preview, new OverlaySurface.Callbacks() {
            @Override public void toggle() { togglePanel(); }
            @Override public void collapse() {
                if (preview) remove(); else collapsePanel();
            }
            @Override public void drag(float dx, float dy) { dragBy(dx, dy); }
            @Override public void dragEnd() { snapToEdge(); }
            @Override public void look(float dx, float dy) { accumulateLook(dx, dy); }
            @Override public void pulse(int key, String description) {
                GameOverlay.this.pulse(key, description);
            }
            @Override public void delayedPulse(int key, String description) {
                GameOverlay.this.deferPlayback(key, description);
            }
            @Override public void hold(int key, boolean pressed, String description) {
                sendKey(key, pressed ? NativeCommandBridge.KEY_PRESS
                        : NativeCommandBridge.KEY_RELEASE, description);
            }
            @Override public void command(String name, String value) {
                GameOverlay.this.sendCommand(name, value);
            }
            @Override public String commandStatus() { return NativeCommandBridge.status(); }
            @Override public void openSettings() { GameOverlay.this.openSettings(); }
            @Override public void saveLog() { saveJournalToFile(); }
            @Override public void refreshLog() { updateJournal(); }
            @Override public void copyLog() { copyJournal(); }
        });
        // ComposeView looks up owners through its parent when it attaches.
        // Unity's Activity provides none, so the host must own this tree.
        ui.installOwnersOn(host);
        handle = ui.getHandle();
        panel = ui.getPanel();
        host.addView(panel, new FrameLayout.LayoutParams(dp(320), -2));
        host.addView(handle, new FrameLayout.LayoutParams(dp(50), dp(50)));
        // A cold Activity may not have a window yet during onActivityResumed.
        // The host's attach callback composes after both children are attached.
        try {
            activity.addContentView(host, new ViewGroup.LayoutParams(-1, -1));
        } catch (Throwable failure) {
            removedByUs = true;
            if (host.getParent() instanceof ViewGroup) {
                ((ViewGroup) host.getParent()).removeView(host);
            }
            ui.dispose();
            if (failure instanceof Error) throw (Error) failure;
            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
            throw new IllegalStateException("overlay composition failed", failure);
        }
        panel.setVisibility(preview ? View.VISIBLE : View.GONE);
        refresh();
        updateJournal();
        // A hidden overlay has no touch target left, so the volume key relay and
        // the runtime's own playback journal are the only channels back in.
        if (!preview) {
            XposedEntry.setVolumeKeyListener(volumeKeys);
            RuntimeLog.observe(journalLines);
        }
        host.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or_, ob) -> layout());
        host.requestApplyInsets();
        host.post(this::layout);
    }

    private void composeWhenAttached() {
        if (closed || compositionCreated || !host.isAttachedToWindow()) return;
        try {
            ui.composeNow();
            compositionCreated = true;
            RuntimeLog.record("overlay Compose first composition created");
        } catch (Throwable failure) {
            RuntimeLog.record("overlay first composition failed: " + failure);
            android.util.Log.e("BetterEndfieldNext.Overlay", "Unable to compose panel", failure);
            remove();
        }
    }

    private void refresh() {
        OverlayFeatures current;
        try {
            SharedPreferences preferences = settings.get();
            AppLanguage.read(preferences);
            current = OverlayFeatures.read(preferences);
            applyAppearance(ModuleSettings.getOverlayAppearance(preferences));
        } catch (RuntimeException unavailable) { current = OverlayFeatures.off(); }
        if (preview) {
            current = new OverlayFeatures(true, current.hideHud(), current.freeCamera(),
                    current.worldPause(), current.firstPerson(), current.vmdCamera(),
                    current.mmd(), current.models());
        }
        if (current.equals(shown)) return;
        shown = current;
        ui.render(current);
        host.post(this::layout);
    }

    private void updateJournal() {
        String stamp = "构建 " + BuildConfig.VERSION_NAME
                + " (" + BuildConfig.VERSION_CODE + ") · 点按刷新 · 长按复制 · 下方按钮另存";
        String lines = RuntimeLog.tail(14);
        String runtime = snapshotSummary();
        ui.renderJournal(lines.isEmpty()
                ? stamp + "\n" + runtime + "\n（暂无记录）"
                : stamp + "\n" + runtime + "\n" + lines.trim());
    }

    /**
     * Reads the native runtime status over JNI and renders the one-line summary
     * the journal shows under its header. A failed binding degrades to the
     * offline summary instead of dropping the line: the journal is the one
     * surface that has to keep working when the bridge does not.
     */
    private String snapshotSummary() {
        try {
            snapshot = RuntimeSnapshot.parse(NativeCommandBridge.runtimeStatus());
        } catch (RuntimeException | LinkageError unavailable) {
            snapshot = RuntimeSnapshot.offline();
        }
        return "运行时：" + snapshot.summary();
    }

    private void copyJournal() {
        String all = RuntimeLog.tail(150);
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) activity.getSystemService(
                        android.content.Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                    "BetterEndfield 运行日志", all.isEmpty() ? "(empty)" : all));
            toast("运行日志已复制（" + all.split("\n").length + " 行）");
        }
    }

    /** Request code for the system save dialog; must fit in the lower 16 bits. */
    private static final int REQUEST_SAVE_LOG = 0x1E10;
    /** The overlay surface waiting for the system save dialog to return. */
    private static GameOverlay saveRequester;
    /** Log text captured when the save dialog was opened. */
    private static String pendingSaveBody;

    /**
     * Opens the system save dialog (SAF ACTION_CREATE_DOCUMENT) so the user
     * picks where the log lands - Downloads, a cloud drive, anywhere. Needs no
     * storage permission. When the dialog cannot start or its result cannot
     * reach the hooked process, falls back to the share sheet instead.
     */
    private void saveJournalToFile() {
        pendingSaveBody = buildJournalBody();
        if (!preview && XposedEntry.activityResultRelayReady()) {
            try {
                String stamp = new java.text.SimpleDateFormat(
                        "yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(new java.util.Date());
                Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                save.addCategory(Intent.CATEGORY_OPENABLE);
                save.setType("text/plain");
                save.putExtra(Intent.EXTRA_TITLE, "betterendfield-log-" + stamp + ".txt");
                saveRequester = this;
                XposedEntry.setActivityResultListener(GameOverlay::dispatchSaveResult);
                activity.startActivityForResult(save, REQUEST_SAVE_LOG);
                RuntimeLog.record("journal save: location picker opened");
                return;
            } catch (Throwable pickerFailed) {
                saveRequester = null;
                RuntimeLog.record("journal save picker unavailable: " + pickerFailed);
            }
        } else {
            RuntimeLog.record("journal save: activity result relay absent, sharing instead");
        }
        shareJournal(pendingSaveBody);
    }

    /** Receives the system save dialog's outcome and writes the log there. */
    private static void dispatchSaveResult(int requestCode, int resultCode, Intent data) {
        GameOverlay surface = saveRequester;
        if (surface == null || requestCode != REQUEST_SAVE_LOG) return;
        saveRequester = null;
        android.net.Uri target = resultCode == Activity.RESULT_OK && data != null
                ? data.getData() : null;
        if (target == null) {
            surface.toast("已取消保存");
            return;
        }
        String body = pendingSaveBody == null ? "" : pendingSaveBody;
        pendingSaveBody = null;
        new Thread(() -> {
            String outcome;
            try (java.io.OutputStream out = surface.activity.getContentResolver()
                    .openOutputStream(target)) {
                if (out == null) throw new java.io.IOException("provider returned no stream");
                out.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                outcome = "日志已保存到所选位置";
                RuntimeLog.record("journal saved: " + target);
            } catch (Exception error) {
                RuntimeLog.record("journal save failed: " + error);
                outcome = "日志保存失败: " + error.getMessage();
            }
            String message = outcome;
            surface.mainHandler.post(() -> surface.toast(message));
        }, "BetterEndfield-SaveJournal").start();
    }

    /**
     * Share-sheet fallback for devices where the save dialog cannot start or
     * its result cannot reach us: the plain-text log travels via ACTION_SEND,
     * from where any files app can store it wherever the user chooses. Needs
     * no storage permission and no FileProvider declaration in the host.
     */
    private void shareJournal(String body) {
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, "Better-Endfield 运行日志");
            send.putExtra(Intent.EXTRA_TEXT, body);
            activity.startActivity(Intent.createChooser(send, "分享运行日志"));
            RuntimeLog.record("journal share opened");
        } catch (Throwable shareFailed) {
            RuntimeLog.record("journal share failed: " + shareFailed);
            toast("日志保存失败: " + shareFailed.getMessage());
        }
    }

    /** Assembles the journal text: java ring plus the native log tail. */
    private String buildJournalBody() {
        String stamp = new java.text.SimpleDateFormat(
                "yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(new java.util.Date());
        StringBuilder nativeTail = new StringBuilder();
        try {
            NativeCommandBridge.tailNativeLog(nativeTail, new long[]{0L});
        } catch (RuntimeException unavailable) {
            nativeTail.append("(native journal unavailable: ").append(unavailable).append(')');
        }
        String nativeText = nativeTail.toString();
        if (nativeText.length() > 40000) nativeText = nativeText.substring(nativeText.length() - 40000);
        return new StringBuilder()
                .append("Better-Endfield journal build ")
                .append(BuildConfig.VERSION_NAME).append(" (")
                .append(BuildConfig.VERSION_CODE).append(")\n")
                .append("saved at ").append(stamp).append("\n\n")
                .append("---- java journal (newest last) ----\n")
                .append(RuntimeLog.tail(150).trim()).append("\n\n")
                .append("---- native journal (newest last) ----\n")
                .append(nativeText.trim()).append('\n')
                .toString();
    }

    // ------------------------------------------------------------------ actions

    private void pulse(int virtualKey, String title) {
        sendKey(virtualKey, NativeCommandBridge.KEY_PULSE, title);
    }

    /**
     * Starts a playback key (motion preset, keyframe replay, VMD replay) only
     * once the panel is down and {@link #DEFERRED_PLAYBACK_DELAY_MS} has
     * passed. These three are the keys whose whole point is what the camera
     * does next, so the take has to start on a clean screen - which is also why
     * only these three are deferred and every other control still fires under
     * the finger, where an adjustment belongs.
     *
     * <p>The wait runs on the controller's own handler, never on a Compose
     * scope inside the panel: the moment the panel is collapsed is exactly when
     * a composable's lifetime stops being something to rely on, and a scope
     * cancelled with the panel would swallow the key silently.
     *
     * <p>A second tap inside the window replaces the pending key rather than
     * queueing another; expanding the panel again, leaving the activity, tearing
     * the surface down or pressing a volume key cancels it, which is also how a
     * take is abandoned before it starts.
     */
    private void deferPlayback(int virtualKey, String description) {
        if (preview) {
            pulse(virtualKey, description);
            return;
        }
        collapsePanel();
        // The handle is drawn over the game too, so it goes with the panel: the
        // point of the wait is a clean screen for the camera to start on.
        standDown();
        deferredKey = virtualKey;
        deferredDescription = description;
        mainHandler.removeCallbacks(deferredPlayback);
        mainHandler.postDelayed(deferredPlayback, DEFERRED_PLAYBACK_DELAY_MS);
        RuntimeLog.record("playback armed: " + description + " in "
                + DEFERRED_PLAYBACK_DELAY_MS + " ms (volume key cancels)");
    }

    private void runDeferredPlayback() {
        String description = deferredDescription;
        if (description == null) return;
        int virtualKey = deferredKey;
        deferredKey = 0;
        deferredDescription = null;
        firedDescription = description;
        pulse(virtualKey, description);
        RuntimeLog.record("playback fired: " + description
                + " (volume key stops it while it runs)");
        // The runtime is now the only party that knows whether anything is
        // playing, so the handle waits for it to say so; see TAKEOFF_GRACE_MS
        // for what happens when it stays silent.
        mainHandler.removeCallbacks(takeoffCheck);
        mainHandler.postDelayed(takeoffCheck, TAKEOFF_GRACE_MS);
    }

    // ------------------------------------------------------------- look pad

    /**
     * Takes a look-pad drag. Aiming is a stream rather than an event: the native
     * side adds whatever deltas arrive, so they are summed here and written at a
     * fixed rate instead of one file write per touch sample.
     */
    private void accumulateLook(float dx, float dy) {
        if (preview || closed) return;
        lookPendingX += dx;
        lookPendingY += dy;
        if (lookFlushScheduled) return;
        lookFlushScheduled = true;
        mainHandler.postDelayed(lookFlush, LOOK_FLUSH_INTERVAL_MS);
    }

    private void flushLook() {
        lookFlushScheduled = false;
        // Whole pixels: the native counter is an int. The fraction stays behind
        // rather than being cleared, because a drag slower than half a pixel per
        // window would otherwise be rounded away on every flush and never amount
        // to anything, however long it is held.
        int dx = Math.round(lookPendingX);
        int dy = Math.round(lookPendingY);
        lookPendingX -= dx;
        lookPendingY -= dy;
        if (dx == 0 && dy == 0) return;
        try {
            if (NativeCommandBridge.look(dx, dy)) {
                lookRejected = false;
                return;
            }
        } catch (Throwable failure) {
            if (!lookRejected) {
                lookRejected = true;
                RuntimeLog.record("look delta failed: " + failure);
            }
            return;
        }
        if (!lookRejected) {
            lookRejected = true;
            RuntimeLog.record("look deltas rejected (relay not configured)");
        }
    }

    /**
     * Drops an in-flight drag without sending it. Called when the panel goes
     * away or collapses mid-gesture: a delta that arrives after the control it
     * came from is gone would turn the camera with nothing on screen to explain
     * why.
     */
    private void dropPendingLook() {
        mainHandler.removeCallbacks(lookFlush);
        lookFlushScheduled = false;
        lookPendingX = 0f;
        lookPendingY = 0f;
    }

    private void cancelDeferredPlayback() {
        if (deferredDescription == null) return;
        deferredKey = 0;
        deferredDescription = null;
        mainHandler.removeCallbacks(deferredPlayback);
        RuntimeLog.record("playback cancelled before it fired");
    }

    // ------------------------------------------------------- stand-down / escape

    /**
     * Hides the handle, leaving nothing of the overlay on screen. What brings it
     * back is the runtime reporting that the take has ended, a volume key press,
     * or the game going to the background and returning. A take that runs
     * forever never reports an end - the motion preset without a duration, the
     * keyframe loop and the VMD loop can all run until stopped - so for those
     * the volume key remains the way out.
     */
    private void standDown() {
        stoodDown = true;
        // runningKey describes the take of the stand-down that is starting now,
        // not one from an earlier session that a journal replay might mention.
        runningKey = 0;
        runningDescription = null;
        if (handle.getVisibility() != View.VISIBLE) return;
        handle.animate().cancel();
        handle.animate().alpha(0f).setDuration(100)
                .withEndAction(() -> handle.setVisibility(View.GONE)).start();
    }

    /** Brings the handle back and forgets the stand-down, whatever ended it. */
    private void endStandDown() {
        endStandDown(null);
    }

    /** As above, naming the cause in the journal. */
    private void endStandDown(String reason) {
        if (!stoodDown) return;
        stoodDown = false;
        runningKey = 0;
        runningDescription = null;
        mainHandler.removeCallbacks(takeoffCheck);
        if (handle.getVisibility() == View.VISIBLE && handle.getAlpha() == 1f) return;
        handle.animate().cancel();
        handle.setAlpha(0f);
        handle.setVisibility(View.VISIBLE);
        handle.animate().alpha(1f).setDuration(140).start();
        RuntimeLog.record("overlay handle restored"
                + (reason == null ? "" : ": " + reason));
    }

    /**
     * Fires when the runtime never reported a take for the key that was just
     * sent. Nothing is playing, so the overlay has no reason to stay hidden -
     * and unlike the volume key, which can always be pressed, this one arrives
     * without the user having to know that a hidden overlay still listens.
     */
    private void onTakeoffCheck() {
        if (closed || !stoodDown || runningKey != 0) return;
        RuntimeLog.record("no take reported within " + TAKEOFF_GRACE_MS
                + " ms; the runtime had nothing to play");
        endStandDown("no take started");
    }

    /**
     * A volume key press while the overlay is standing down. With the panel and
     * the handle both gone this is the only control left on the glass, so it
     * does the obvious thing: a take that has not fired yet is dropped, a take
     * the runtime reports as running is stopped, and a stand-down whose take
     * already ended just restores the handle.
     *
     * <p>It stays idempotent because a host activity that overrides
     * dispatchKeyEvent and calls super delivers one press twice; the first call
     * consumes the state and the second one finds nothing to do.
     */
    private void onVolumeKey(int keyCode) {
        if (!stoodDown) return;
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post(() -> onVolumeKey(keyCode));
            return;
        }
        // Logged before anything else so a press that turns out to have nothing
        // to do still proves the relay reached this process.
        RuntimeLog.record("volume key " + keyCode + " during stand-down");
        if (runningKey != 0) {
            String description = runningDescription == null ? "播放" : runningDescription;
            int virtualKey = runningKey;
            // Leave the runtime a rising edge to see: see STOP_EDGE_GAP_MS.
            runningKey = 0;
            runningDescription = null;
            releaseHeldKeys();
            mainHandler.postDelayed(() -> pulse(virtualKey, "中断 " + description),
                    STOP_EDGE_GAP_MS);
            RuntimeLog.record("take stopped by volume key: " + description
                    + " (vk " + Integer.toHexString(virtualKey) + ")");
        } else if (deferredDescription != null) {
            String dropped = deferredDescription;
            cancelDeferredPlayback();
            RuntimeLog.record("armed playback dropped by volume key: " + dropped);
        } else {
            RuntimeLog.record("volume key: no take is running, restoring the handle");
        }
        endStandDown();
    }

    /**
     * Watches the runtime's own journal for playback events. The native module
     * is the only party that knows whether a take is running: it logs a started
     * line when one begins and a stop line when it ends, for every reason
     * (finished, switched, or stopped by its own hotkey), and a stop line is
     * also what brings the handle back. A take that fails to start logs neither,
     * which is why nothing is assumed from the key we sent - without a started
     * line the volume key cannot try to stop what never began, and the handle
     * comes back on {@link #TAKEOFF_GRACE_MS} instead.
     */
    private void observeJournalLine(String line) {
        if (line.indexOf("[native] ") < 0) return;
        boolean stopped = line.contains("Free camera playback stopped")
                || line.contains("Free camera disabled");
        int startedKey = 0;
        if (line.contains("Free camera motion started")) {
            startedKey = Hotkeys.MOTION;
        } else if (line.contains("Free camera keyframe playback started")) {
            startedKey = Hotkeys.KEYFRAME_PLAY;
        } else if (line.contains("VMD camera playback started")) {
            startedKey = Hotkeys.VMD_PLAY;
        }
        if (!stopped && startedKey == 0) return;
        final int began = startedKey;
        final boolean ended = stopped;
        // Journal lines arrive on the recording thread - the panel, the native
        // log poller, or a hook - and the state they touch is main-thread only.
        mainHandler.post(() -> applyPlaybackState(began, ended));
    }

    private void applyPlaybackState(int startedKey, boolean stopped) {
        if (stopped) {
            if (runningKey == 0) return;
            String description = runningDescription == null ? "播放" : runningDescription;
            runningKey = 0;
            runningDescription = null;
            // A take that has ended leaves the overlay nothing to stay hidden
            // for. Nothing to do when the panel is up, which is why this only
            // records and restores while a stand-down is in flight.
            if (!stoodDown) return;
            RuntimeLog.record("take finished: " + description);
            endStandDown("the take ended");
            return;
        }
        if (runningKey != 0 || startedKey == 0) return;
        // The runtime owns the take now; its own stop line ends the watch.
        mainHandler.removeCallbacks(takeoffCheck);
        runningKey = startedKey;
        runningDescription = firedDescription;
        RuntimeLog.record("take running: " + (firedDescription == null
                ? "vk " + Integer.toHexString(startedKey) : firedDescription));
    }

    /**
     * The native pump keeps one high-water mark for the whole process, so the
     * panel numbers its commands from the clock as well; see
     * ModuleSettings.nextCommandGeneration for why two private counters cannot
     * coexist.
     */
    private static final java.util.concurrent.atomic.AtomicLong COMMAND_GENERATION =
            new java.util.concurrent.atomic.AtomicLong();

    /**
     * Issues a runtime command from the panel.
     *
     * <p>The settings app has to write into the framework's remote file space
     * and wait for this process to pick the payload up. The panel is already in
     * that process, so it appends to the relay stream directly - both routes
     * end at the same "c &lt;payload&gt;" line the native side tails, which is
     * what makes the preview mode's promise of sending nothing meaningful.
     */
    private void sendCommand(String name, String value) {
        if (preview) {
            toast("预览模式：不会发送指令");
            return;
        }
        long generation = COMMAND_GENERATION.updateAndGet(
                previous -> Math.max(System.currentTimeMillis(), previous + 1L));
        String payload = "BE_COMMAND_V1\n" + generation + "\n" + name + "\n" + value + "\n";
        if (!NativeCommandBridge.submit(payload)) {
            RuntimeLog.record("command rejected (relay not configured): " + name + " " + value);
            if (!bridgeMissing) {
                bridgeMissing = true;
                toast("增强运行时尚未载入，请稍后重试");
            }
            return;
        }
        RuntimeLog.record("command sent: " + name + " " + value);
    }

    private void sendKey(int virtualKey, int action, String description) {
        if (preview) {
            if (action != NativeCommandBridge.KEY_RELEASE) {
                toast("预览模式：不会发送「" + description + "」");
            }
            return;
        }
        try {
            // False before RuntimeBootstrap.configure() opened the file relay,
            // i.e. while the native runtime cannot have loaded yet. Once the
            // channel exists every event is delivered; the native relay latches
            // it (presses held, pulses expiring) whenever it starts.
            if (!NativeCommandBridge.key(virtualKey, action)) {
                RuntimeLog.record("key rejected (relay not configured): " + description
                        + " (vk " + Integer.toHexString(virtualKey) + ")");
                if (!bridgeMissing) {
                    bridgeMissing = true;
                    toast("增强运行时尚未载入，请稍后重试");
                }
            } else if (!keyPathProven) {
                keyPathProven = true;
                bridgeMissing = false;
                RuntimeLog.record("key send ok; file relay active ("
                        + description + ", vk " + Integer.toHexString(virtualKey) + ")");
            }
        } catch (Throwable failure) {
            RuntimeLog.record("key " + description + " failed: " + failure);
            if (!bridgeMissing) {
                bridgeMissing = true;
                toast("按键发送失败，请稍后重试");
            }
        }
    }

    private static boolean keyPathProven;

    private void releaseHeldKeys() {
        if (preview) return;
        try {
            NativeCommandBridge.releaseKeys();
        } catch (UnsatisfiedLinkError | NoSuchMethodError ignored) {
            // Nothing is latched if the runtime was never loaded.
        }
    }

    private void openSettings() {
        Intent intent = new Intent()
                .setClassName(RuntimeBootstrap.MODULE_PACKAGE,
                        RuntimeBootstrap.MODULE_PACKAGE + ".MainActivity")
                .putExtra(MainActivity.EXTRA_PAGE, "enhancement")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(intent);
        } catch (RuntimeException unavailable) {
            toast("无法打开体验设置");
        }
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    // Position both real touch surfaces within the Activity's inset content.
    // The rest of the full-screen FrameLayout has no listener and passes events through.
    private void layout() {
        if (closed || host.getWidth() == 0) return;
        int left = host.getPaddingLeft() + dp(8);
        int top = host.getPaddingTop() + dp(8);
        int width = Math.max(1, host.getWidth() - left - host.getPaddingRight() - dp(8));
        int height = Math.max(1, host.getHeight() - top - host.getPaddingBottom() - dp(8));
        handle.setX(left + xFraction * Math.max(0, width - dp(50)));
        handle.setY(top + yFraction * Math.max(0, height - dp(50)));
        int panelWidth = Math.min(dp(320), width);
        panel.measure(View.MeasureSpec.makeMeasureSpec(panelWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST));
        int panelHeight = Math.min(height, panel.getMeasuredHeight());
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) panel.getLayoutParams();
        if (params.width != panelWidth || params.height != panelHeight) {
            params.width = panelWidth;
            params.height = panelHeight;
            panel.setLayoutParams(params);
        }
        float beside = handle.getX() + dp(58);
        if (beside + panelWidth > left + width) beside = handle.getX() - panelWidth - dp(8);
        panel.setX(Math.max(left, Math.min(beside, left + width - panelWidth)));
        panel.setY(Math.max(top, Math.min(handle.getY(), top + height - panelHeight)));
        handle.bringToFront();
    }

    private void dragBy(float dx, float dy) {
        int left = host.getPaddingLeft() + dp(8);
        int top = host.getPaddingTop() + dp(8);
        int w = Math.max(1, host.getWidth() - left - host.getPaddingRight() - dp(58));
        int h = Math.max(1, host.getHeight() - top - host.getPaddingBottom() - dp(58));
        xFraction = clamp((handle.getX() + dx - left) / w);
        yFraction = clamp((handle.getY() + dy - top) / h);
        layout();
    }

    private void applyAppearance(ModuleSettings.OverlayAppearance appearance) {
        host.setAlpha(appearance.alpha());
        boolean newlyEnabled = appearance.autoSnap() && !autoSnap;
        autoSnap = appearance.autoSnap();
        if (newlyEnabled) snapToEdge();
    }

    private void snapToEdge() {
        if (!autoSnap || closed) return;
        xFraction = xFraction < 0.5f ? 0f : 1f;
        if (panel.getVisibility() == View.VISIBLE) collapsePanel();
        layout();
    }

    private void togglePanel() {
        if (panel.getVisibility() == View.VISIBLE) { collapsePanel(); return; }
        // Re-opening the panel is the user changing their mind about an armed
        // take; it must not start behind the panel they just brought back.
        cancelDeferredPlayback();
        refresh();
        updateJournal();
        panel.setAlpha(0f);
        panel.setVisibility(View.VISIBLE);
        layout();
        panel.animate().alpha(1f).setDuration(140).start();
    }

    private void collapsePanel() {
        releaseHeldKeys();
        dropPendingLook();
        panel.animate().alpha(0f).setDuration(100)
                .withEndAction(() -> panel.setVisibility(View.GONE)).start();
    }

    void remove() {
        if (closed) return;
        removedByUs = true;
        mainHandler.removeCallbacks(reattachCheck);
        mainHandler.removeCallbacks(composeCheck);
        mainHandler.removeCallbacks(takeoffCheck);
        closed = true;
        releaseHeldKeys();
        cancelDeferredPlayback();
        dropPendingLook();
        // Both registrations are static and outlive this surface; a volume key
        // that still reached a torn-down overlay would have nowhere to land.
        if (!preview) {
            XposedEntry.clearVolumeKeyListener(volumeKeys);
            RuntimeLog.stopObserving(journalLines);
        }
        ui.dispose();
        if (host.getParent() instanceof ViewGroup) {
            ((ViewGroup) host.getParent()).removeView(host);
        }
    }

    private void ensureOnTop() {
        if (removedByUs || closed || activity.isDestroyed() || activity.isFinishing()) return;
        android.view.ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        if (host.getParent() == content) {
            // setContentView-style rebuilds can leave our host attached but
            // underneath the freshly added game view; the detach watchdog
            // never fires because the host was never detached. Only the
            // z-order tells the truth.
            if (content.getChildAt(content.getChildCount() - 1) != host) {
                content.bringChildToFront(host);
                RuntimeLog.record("overlay host re-raised above the game view");
            }
            return;
        }
        if (host.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) host.getParent()).removeView(host);
        }
        try {
            content.addView(host, new ViewGroup.LayoutParams(-1, -1));
            RuntimeLog.record("overlay host re-added to the content view");
        } catch (RuntimeException error) {
            RuntimeLog.record("overlay re-attach failed: " + error);
        }
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
