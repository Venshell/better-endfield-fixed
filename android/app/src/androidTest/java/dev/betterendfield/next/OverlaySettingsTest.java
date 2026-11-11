package dev.betterendfield.next;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.app.Instrumentation;

/** Checks the persisted appearance contract shared by settings and the game overlay. */
final class OverlaySettingsTest {
    static void run(Instrumentation instrumentation) {
        Context isolated = new ContextWrapper(instrumentation.getTargetContext()) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("overlay-settings-test-" + name, mode);
            }
        };
        SharedPreferences preferences = FrameworkSettings.open(isolated);
        try {
            preferences.edit().clear().commit();
            ModuleSettings.OverlayAppearance initial = ModuleSettings.getOverlayAppearance(preferences);
            check(Math.abs(1f - initial.alpha()) < 0.0001f, "opaque default");
            check(initial.autoSnap(), "snap enabled by default");
            check(ModuleSettings.isOverlayLogVisible(preferences), "overlay log shown by default");

            ModuleSettings.setOverlayTransparency(isolated, 45f);
            ModuleSettings.setOverlayAutoSnap(isolated, true);
            ModuleSettings.OverlayAppearance saved = ModuleSettings.getOverlayAppearance(preferences);
            check(Math.abs(0.55f - saved.alpha()) < 0.0001f, "transparency persists");
            check(saved.autoSnap(), "snap persists");
            ModuleSettings.setOverlayAutoSnap(isolated, false);
            check(!ModuleSettings.getOverlayAppearance(preferences).autoSnap(),
                    "explicitly disabled snap persists");
            ModuleSettings.setOverlayLogVisible(isolated, false);
            check(!ModuleSettings.isOverlayLogVisible(preferences), "overlay log can be hidden");
            ModuleSettings.setOverlayLogVisible(isolated, true);
            check(ModuleSettings.isOverlayLogVisible(preferences), "overlay log can be restored");

            ModuleSettings.setOverlayTransparency(isolated, Float.POSITIVE_INFINITY);
            check(Math.abs(1f - ModuleSettings.getOverlayAppearance(preferences).alpha()) < 0.0001f,
                    "non-finite input becomes opaque");
            ModuleSettings.setOverlayTransparency(isolated, 100f);
            check(Math.abs(0.2f - ModuleSettings.getOverlayAppearance(preferences).alpha()) < 0.0001f,
                    "transparency is capped at 80 percent");
        } finally {
            preferences.edit().clear().commit();
        }
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
