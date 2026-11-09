package dev.betterendfield.next;

import android.content.SharedPreferences;

/**
 * Which panel controls are worth showing.
 *
 * A control is only offered when its module was actually configured to load.
 * Pressing "free camera" when {@code betterendfieldnext.camera} is not in the process
 * would press a key that nothing reads, and a button that silently does nothing
 * is worse than a button that is not there.
 */
public record OverlayFeatures(
        boolean panel,
        boolean hideHud,
        boolean freeCamera,
        boolean worldPause,
        boolean firstPerson,
        boolean vmdCamera,
        boolean mmd,
        boolean models,
        boolean mediaScreen) {

    static OverlayFeatures read(SharedPreferences settings) {
        boolean freeCamera = settings.getBoolean(ModuleSettings.CAMERA_FREE, false);
        // The model page edits the installed index. With nothing in it the page
        // would be a list of one sentence, so the entry is only worth offering
        // once there is something to manage.
        String index = settings.getString(BemInstaller.INDEX, "[]");
        return new OverlayFeatures(
                settings.getBoolean(ModuleSettings.OVERLAY_ENABLED, false),
                settings.getBoolean(ModuleSettings.UI_HIDE_HUD, false),
                freeCamera,
                // World pause is a free-camera sub-mode: the desktop module only
                // reads its hotkey while the free camera is armed.
                freeCamera && settings.getBoolean(ModuleSettings.CAMERA_PAUSE, false),
                settings.getBoolean(ModuleSettings.CAMERA_FIRST_PERSON, false),
                // Two real gates on this one. The module only latches the VMD
                // hotkey while the free camera is enabled, and there is nothing to
                // play until a .vmd has actually been published - which is why
                // this button arrives only now, with the import path.
                freeCamera && settings.getBoolean(ModuleSettings.CAMERA_VMD_IMPORTED, false),
                // MMD needs no camera gate: its overlay controls are commands to
                // the director, not keys to the free camera, so they work with
                // the camera off. What they do need is the module switched on -
                // the director stops and stays stopped while it is off, and a
                // play button that queues a discarded command is worse than none.
                settings.getBoolean("mmd_enabled", false),
                index != null && !index.isEmpty() && !"[]".equals(index),
                !settings.getString(ModuleSettings.MEDIA_CONFIGURATION, "").isEmpty());
    }

    static OverlayFeatures off() {
        return new OverlayFeatures(false, false, false, false, false, false, false, false, false);
    }

    boolean anyControl() {
        return hideHud || freeCamera || firstPerson || mmd || mediaScreen || models;
    }
}
