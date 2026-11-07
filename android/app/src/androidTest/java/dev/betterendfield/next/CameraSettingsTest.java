package dev.betterendfield.next;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

/** Configuration tests called by the registered BemInstallerTest instrumentation. */
final class CameraSettingsTest {
    static void run(Instrumentation instrumentation) {
        Context isolated = new ContextWrapper(instrumentation.getTargetContext()) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("camera-settings-test-" + name, mode);
            }
        };
        SharedPreferences preferences = FrameworkSettings.open(isolated);
        try {
            preferences.edit().clear().commit();
            ModuleSettings.republishConfigurations(isolated);
            check("0.03".equals(ModuleSettings.getFirstPersonEyeForward(isolated)), "forward default");
            check("0.05".equals(ModuleSettings.getFirstPersonEyeHeight(isolated)), "height default");
            check("0.03".equals(ModuleSettings.getFirstPersonNearClip(isolated)), "clip default");
            check(!ModuleSettings.isFirstPersonExtendLookRange(isolated), "look range default");
            ModuleSettings.setCameraSettings(isolated, false, false, false, true, true, true,
                    5, 60, 75, 0.1234, -0.25, 0.001, true, ModuleSettings.getFirstPersonAdvanced(isolated),
                    ModuleSettings.getCameraMotion(isolated));
            ModuleSettings.republishConfigurations(isolated);
            String ini = ModuleConfigurations.read(preferences).camera();
            check(ini.contains("first_person_eye_forward=0.1234\n"), "forward round trip");
            check(ini.contains("first_person_eye_height=-0.25\n"), "height round trip");
            check(ini.contains("first_person_near_clip=0.001\n"), "clip round trip");
            check(ini.contains("first_person_extend_look_range=true\n"), "look range round trip");
            // A save of an unrelated camera option and disabling the feature must
            // retain the first-person preferences for the next game launch.
            saveStored(isolated, false);
            check(ModuleConfigurations.read(preferences).camera().isEmpty(), "disabled module");
            ModuleSettings.republishConfigurations(isolated);
            saveStored(isolated, true);
            check(ModuleConfigurations.read(preferences).camera().contains(
                    "first_person_eye_forward=0.1234\n"), "disabled/re-enabled retains forward");
            check(ModuleConfigurations.read(preferences).camera().contains(
                    "first_person_extend_look_range=true\n"), "disabled/re-enabled retains switch");
            ModuleSettings.setCameraSettings(isolated, false, false, false, true, true, true,
                    5, 60, 75, -1, 2, 0, false, ModuleSettings.getFirstPersonAdvanced(isolated),
                    ModuleSettings.getCameraMotion(isolated));
            ini = ModuleConfigurations.read(preferences).camera();
            check(ini.contains("first_person_eye_forward=0\n"), "forward lower bound");
            check(ini.contains("first_person_eye_height=0.5\n"), "height upper bound");
            check(ini.contains("first_person_near_clip=0.001\n"), "clip lower bound");
            ModuleSettings.setCameraSettings(isolated, false, false, false, true, true, true,
                    5, 60, 75, 2, -2, 2, false, ModuleSettings.getFirstPersonAdvanced(isolated),
                    ModuleSettings.getCameraMotion(isolated));
            ini = ModuleConfigurations.read(preferences).camera();
            check(ini.contains("first_person_eye_forward=0.5\n"), "forward upper bound");
            check(ini.contains("first_person_eye_height=-0.5\n"), "height lower bound");
            check(ini.contains("first_person_near_clip=1\n"), "clip upper bound");
            ModuleSettings.setCameraSettings(isolated, false, false, false, true, true, true,
                    5, 60, 75, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false,
                    ModuleSettings.getFirstPersonAdvanced(isolated),
                    ModuleSettings.getCameraMotion(isolated));
            ini = ModuleConfigurations.read(preferences).camera();
            check(ini.contains("first_person_eye_forward=0.03\n"), "nonfinite forward fallback");
            check(ini.contains("first_person_eye_height=0.05\n"), "nonfinite height fallback");
            check(ini.contains("first_person_near_clip=0.03\n"), "nonfinite clip fallback");
            // The three slider assertions that used to sit here drove
            // ValueSlider, the hand-rolled View the 3.3.22 Compose rewrite
            // deleted. The rule they covered - keep the exact stored number in
            // the readout until the user actually drags - now lives in
            // SliderRow's own state, which only a Compose test host can reach.
            // Leaving the dead calls behind is what stopped this whole source
            // set from compiling.
            testAdvanced(isolated);
            testMotion(isolated);
            testGlobalFovAndFollow(isolated);
        } finally {
            preferences.edit().clear().commit();
        }
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    private static void saveStored(Context context, boolean enabled) {
        ModuleSettings.setCameraSettings(context, false, false, false, enabled, true, true,
                7, 60, 75,
                ModuleSettings.parse(ModuleSettings.getFirstPersonEyeForward(context), 0.03),
                ModuleSettings.parse(ModuleSettings.getFirstPersonEyeHeight(context), 0.05),
                ModuleSettings.parse(ModuleSettings.getFirstPersonNearClip(context), 0.03),
                ModuleSettings.isFirstPersonExtendLookRange(context), ModuleSettings.getFirstPersonAdvanced(context),
                ModuleSettings.getCameraMotion(context));
    }

    private static void testGlobalFovAndFollow(Context context) {
        SharedPreferences preferences = FrameworkSettings.open(context);
        check(ModuleSettings.setGlobalFov(context, true, 81), "global FOV save");
        check(ModuleSettings.setFreeCameraFollowCharacter(context, true), "follow save");
        ModuleSettings.setCameraSettings(context, false, false, false, false, true, true,
                5, 60, 75, 0.03, 0.05, 0.03, false,
                ModuleSettings.getFirstPersonAdvanced(context), ModuleSettings.getCameraMotion(context));
        String ini = ModuleConfigurations.read(preferences).camera();
        check(ini.contains("global_fov_enabled=true\n") && ini.contains("global_fov=81\n"),
                "global FOV alone starts camera module");
        check(ini.contains("free_camera_follow_character=true\n"), "follow serialized");
        check(ModuleSettings.setGlobalFov(context, false, Double.NaN), "global FOV disable");
        ModuleSettings.setCameraSettings(context, false, false, false, false, true, true,
                5, 60, 75, 0.03, 0.05, 0.03, false,
                ModuleSettings.getFirstPersonAdvanced(context), ModuleSettings.getCameraMotion(context));
        check(ModuleConfigurations.read(preferences).camera().isEmpty(), "global FOV disable stops module");
        check(ModuleSettings.getGlobalFov(context) == 60f, "nonfinite global FOV defaults");
        ModuleSettings.setCameraSettings(context, false, true, false, false, true, true,
                5, 60, 75, 0.03, 0.05, 0.03, false,
                ModuleSettings.getFirstPersonAdvanced(context), ModuleSettings.getCameraMotion(context));
        check(ModuleConfigurations.read(preferences).camera().contains("free_camera_follow_character=true\n"),
                "follow selection retained for free camera");
    }

    private static void testAdvanced(Context context) {
        ModuleSettings.FirstPersonAdvanced defaults = ModuleSettings.getFirstPersonAdvanced(context);
        check(!defaults.movement() && defaults.animationMode() == 0 && !defaults.yieldDialogue()
                && !defaults.thirdPersonInCombat()
                && defaults.transitionSeconds() == 0 && !defaults.externalHeadScale(), "advanced defaults off");
        check(defaults.sideLookLimit() == 60 && defaults.lookUpLimit() == 89
                && defaults.lookDownLimit() == 89 && defaults.animationStrength() == 0.35,
                "advanced tuning defaults");
        ModuleSettings.setCameraSettings(context, false, false, false, true, true, true,
                5, 60, 75, 0.03, 0.05, 0.03, false, defaults,
                ModuleSettings.getCameraMotion(context));
        check(ModuleConfigurations.read(FrameworkSettings.open(context)).camera().contains(
                "first_person_third_person_in_combat=false\n"), "combat INI default off");
        ModuleSettings.FirstPersonAdvanced configured = new ModuleSettings.FirstPersonAdvanced(
                true, 42.5, 35, 55, 3, 0.1234, true, true, 0.2468, true);
        ModuleSettings.setCameraSettings(context, false, false, false, true, true, true,
                5, 60, 75, 0.03, 0.05, 0.03, false, configured,
                ModuleSettings.getCameraMotion(context));
        ModuleSettings.republishConfigurations(context);
        check(configured.equals(ModuleSettings.getFirstPersonAdvanced(context)), "advanced round trip");
        String ini = ModuleConfigurations.read(FrameworkSettings.open(context)).camera();
        for (String line : new String[]{"first_person_movement=true", "first_person_side_look_limit=42.5",
                "first_person_look_up_limit=35", "first_person_look_down_limit=55",
                "first_person_animation_mode=3", "first_person_animation_strength=0.1234",
                "first_person_yield_dialogue=true", "first_person_third_person_in_combat=true",
                "first_person_transition_seconds=0.2468",
                "first_person_external_head_scale=true"}) {
            check(ini.contains(line + "\n"), line);
        }
        ModuleSettings.setCameraSettings(context, false, false, false, false, true, true,
                5, 60, 75, 0.03, 0.05, 0.03, false, configured,
                ModuleSettings.getCameraMotion(context));
        ModuleSettings.republishConfigurations(context);
        check(configured.equals(ModuleSettings.getFirstPersonAdvanced(context)), "disabled advanced retained");
        saveStored(context, true);
        check(configured.equals(ModuleSettings.getFirstPersonAdvanced(context)), "re-enabled advanced retained");
        check(ModuleConfigurations.read(FrameworkSettings.open(context)).camera().contains(
                "first_person_third_person_in_combat=true\n"), "re-enabled combat INI retained");
        ModuleSettings.FirstPersonAdvanced bounded = new ModuleSettings.FirstPersonAdvanced(
                true, 100, 100, -1, 99, -1, true, true, 2, true);
        check(bounded.sideLookLimit() == 90 && bounded.lookUpLimit() == 89
                && bounded.lookDownLimit() == 0 && bounded.animationMode() == 0
                && bounded.animationStrength() == 0 && bounded.transitionSeconds() == 1, "advanced bounds");
        bounded = new ModuleSettings.FirstPersonAdvanced(false, -1, 0, 89, 2, 2, false, false, -1, false);
        check(bounded.sideLookLimit() == 0 && bounded.lookUpLimit() == 0
                && bounded.lookDownLimit() == 89 && bounded.animationMode() == 2
                && bounded.animationStrength() == 1 && bounded.transitionSeconds() == 0, "opposite advanced bounds");
        bounded = new ModuleSettings.FirstPersonAdvanced(false, Double.NaN,
                Double.NaN, Double.POSITIVE_INFINITY, -1,
                Double.POSITIVE_INFINITY, false, false, Double.NaN, false);
        check(bounded.sideLookLimit() == 60 && bounded.lookUpLimit() == 89
                && bounded.lookDownLimit() == 89 && bounded.animationMode() == 0
                && bounded.animationStrength() == 0.35 && bounded.transitionSeconds() == 0, "advanced finite fallback");
    }

    /**
     * The motion/keyframe/VMD block. The desktop module clamps these values
     * again on its own side, so the only thing worth asserting here is that the
     * settings screen writes the same keys with the same ranges: a mismatch
     * would show up as a number that changes on its own between the page and
     * the game.
     */
    private static void testMotion(Context context) {
        ModuleSettings.CameraMotion defaults = ModuleSettings.getCameraMotion(context);
        check(!defaults.invertY() && defaults.sensitivity() == 0.1, "motion look defaults");
        check("orbit".equals(defaults.preset()) && defaults.speed() == 1.0
                && defaults.orbitSpeed() == 20.0, "motion preset defaults");
        check(defaults.duration() == 0 && defaults.targetHeight() == 1.2
                && defaults.segmentSeconds() == 3.0, "motion timing defaults");
        check(!defaults.keyframeLoop() && defaults.vmdScale() == 0.07
                && defaults.vmdFovBias() == 5.0 && !defaults.vmdLoop(), "motion VMD defaults");

        ModuleSettings.CameraMotion configured = new ModuleSettings.CameraMotion(
                true, 0.42, "dolly_zoom", -3.5, 90, 12.5, -2.25, 7.5, true, true, 0.5, -12.5, true);
        ModuleSettings.setCameraSettings(context, false, true, false, false, true, true,
                5, 60, 75, 0.03, 0.05, 0.03, false,
                ModuleSettings.getFirstPersonAdvanced(context), configured);
        ModuleSettings.republishConfigurations(context);
        check(configured.equals(ModuleSettings.getCameraMotion(context)), "motion round trip");
        String ini = ModuleConfigurations.read(FrameworkSettings.open(context)).camera();
        for (String line : new String[]{"mouse_invert_y=true", "mouse_sensitivity=0.42",
                "motion_preset=dolly_zoom", "motion_speed=-3.5", "orbit_speed=90",
                "motion_duration=12.5", "motion_target_height=-2.25",
                "keyframe_segment_seconds=7.5", "keyframe_loop=true",
                "vmd_camera_scale=0.5", "vmd_camera_fov_bias=-12.5", "vmd_camera_loop=true"}) {
            check(ini.contains(line + "\n"), line);
        }
        // The file travels as a placeholder, not as a path: the settings app
        // cannot write out the game's files directory (another UID) and does not
        // know which user or cloned profile the game starts under, so
        // RuntimeBootstrap substitutes it. Getting this key wrong is invisible
        // until the game refuses to open the file.
        check(ini.contains("vmd_camera_file=" + ModuleSettings.VMD_FILE_SLOT + "\n"),
                "VMD slot placeholder");
        check(configured.vmdImported(), "VMD import flag round trip");

        // Clearing the import has to reach the key as well; an empty value is
        // what makes the native reader report "no VMD camera file is configured"
        // instead of falling back to a stale path.
        ModuleSettings.clearVmdImport(context);
        saveStored(context, true);
        check(ModuleConfigurations.read(FrameworkSettings.open(context)).camera()
                .contains("vmd_camera_file=\n"), "cleared import writes an empty file key");
        // ... and an import has to bring it back, without any page having to be
        // reopened: this is the same path the import row drives.
        ModuleSettings.setVmdImport(context, "camera.vmd", 1234L, 1700000000000L);
        ModuleSettings.republishConfigurations(context);
        check(ModuleSettings.isVmdImported(context), "import flag stored");
        check("camera.vmd".equals(ModuleSettings.getVmdName(context)), "import name stored");
        check(ModuleSettings.getVmdBytes(context) == 1234L, "import size stored");
        check(ModuleSettings.getVmdTime(context) == 1700000000000L, "import time stored");
        check(ModuleConfigurations.read(FrameworkSettings.open(context)).camera()
                .contains("vmd_camera_file=" + ModuleSettings.VMD_FILE_SLOT + "\n"),
                "re-published import restores the slot");
        ModuleSettings.clearVmdImport(context);

        ModuleSettings.CameraMotion bounded = new ModuleSettings.CameraMotion(
                false, 9, "nope", 100, -900, 9999, 99, 0.01, false, false, 99, -900, false);
        check(bounded.sensitivity() == 0.5 && "orbit".equals(bounded.preset())
                && bounded.speed() == 20 && bounded.orbitSpeed() == -180
                && bounded.duration() == 600 && bounded.targetHeight() == 5
                && bounded.segmentSeconds() == 0.2 && bounded.vmdScale() == 10
                && bounded.vmdFovBias() == -60, "motion bounds");
        bounded = new ModuleSettings.CameraMotion(false, Double.NaN, null, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, false, false, Double.NaN, Double.NaN, false);
        check(bounded.sensitivity() == 0.1 && bounded.speed() == 1.0 && bounded.orbitSpeed() == 20.0
                && bounded.duration() == 0 && bounded.targetHeight() == 1.2
                && bounded.segmentSeconds() == 3.0 && bounded.vmdScale() == 0.07
                && bounded.vmdFovBias() == 5.0 && "orbit".equals(bounded.preset()),
                "motion finite fallback");
        // An unparsable preset must land on orbit rather than on "index -1" when
        // the page maps the name back to a picker position.
        check(ModuleSettings.presetName(" DolLy_Zoom ").equals("dolly_zoom"), "preset name trimmed");
        check(ModuleSettings.presetName("truck").equals("truck"), "preset name kept");
        // ParseMotionPreset accepts these two short forms as well, so the store
        // has to agree or the round trip would quietly rewrite the preset.
        check(ModuleSettings.presetName("dolly").equals("dolly_zoom"), "dolly alias");
        check(ModuleSettings.presetName("pan").equals("truck"), "pan alias");
        check(ModuleSettings.presetName("bogus").equals("orbit"), "unknown preset falls back");

        // The import check mirrors LoadVmdCamera byte for byte: a 30-byte view,
        // two header generations, nothing else. A rule that is too generous
        // would publish a file the game then refuses, and the user would only
        // see "the file is not a VMD motion" with no hint as to which step let
        // it through.
        check(ModuleSettings.isVmdMotion(vmdHeader("Vocaloid Motion Data 0002")), "VMD 0002 header");
        check(ModuleSettings.isVmdMotion(vmdHeader("Vocaloid Motion Data file")), "legacy VMD header");
        check(!ModuleSettings.isVmdMotion(vmdHeader("Vocaloid Motion Data 0001")), "older VMD refused");
        check(!ModuleSettings.isVmdMotion(new byte[29]), "short header refused");
        check(!ModuleSettings.isVmdMotion(null), "missing header refused");
        check(ModuleSettings.vmdMaximumBytes() == 64L * 1024 * 1024, "ceiling matches the loader");
        check(ModuleSettings.VMD_FILE_SLOT.startsWith("%files%/"), "slot is a placeholder");
        check(ModuleSettings.VMD_REMOTE_NAME.equals("vmd.current"), "published slot name");
    }

    /** A 30-byte VMD header, exactly what the settings screen feeds the check. */
    private static byte[] vmdHeader(String text) {
        byte[] header = new byte[30];
        byte[] prefix = text.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(prefix, 0, header, 0, prefix.length);
        return header;
    }
}
