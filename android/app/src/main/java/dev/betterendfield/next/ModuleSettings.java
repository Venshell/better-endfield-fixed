package dev.betterendfield.next;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

final class ModuleSettings {
    static final String MMD_ENABLED = "mmd_enabled";
    private static final String VOICE_CATALOGS = "voice_catalogs";
    static final String VOICE_RULES = "voice_language_rules";
    private static final String MODEL_ENABLED = "model_replacement_enabled";
    private static final String MODEL_RUNTIME_ENABLED = "model_runtime_enabled";
    private static final String MODEL_CHARACTER = "model_character";
    private static final String MODEL_ACTION = "model_action";
    private static final String MODEL_FINAL_LOOP = "model_final_loop";
    private static final String MODEL_FORCE_LOOP = "model_force_loop";
    private static final String MODEL_CROSSFADE = "model_crossfade";
    private static final String MODEL_LOOP_START = "model_loop_start";
    private static final String MODEL_LOOP_END = "model_loop_end";
    private static final String MODEL_CROSSFADE_DURATION = "model_crossfade_duration";
    private static final String MODEL_SCALE = "model_scale";
    private static final String LOGO_ENABLED = "logo_theme_enabled";
    private static final String LOGO_COLOR = "logo_theme_color";
    static final String AUTO_CHECK_UPDATES = "auto_check_updates";
    static final String MODEL_CONFIGURATION = "model_configuration";

    // betterendfieldnext.ui — the desktop interface module.
    private static final String UI_HIDE_UID = "ui_hide_uid";
    static final String UI_HIDE_HUD = "ui_hide_hud";
    static final String PC_UI_ENABLED = "pc_ui_enabled";
    static final String UI_CONFIGURATION = "ui_configuration";

    // betterendfieldnext.camera — the desktop camera module.
    private static final String CAMERA_DITHER = "camera_disable_dither";
    static final String CAMERA_FREE = "camera_free_enabled";
    static final String CAMERA_PAUSE = "camera_pause_enabled";
    static final String CAMERA_FIRST_PERSON = "camera_first_person_enabled";
    private static final String CAMERA_FP_HIDE_HEAD = "camera_first_person_hide_head";
    private static final String CAMERA_FP_FILL_NECK = "camera_first_person_fill_neck";
    private static final String CAMERA_SPEED = "camera_movement_speed";
    private static final String CAMERA_FOV = "camera_field_of_view";
    static final String CAMERA_GLOBAL_FOV_ENABLED = "camera_global_fov_enabled";
    static final String CAMERA_GLOBAL_FOV = "camera_global_fov";
    private static final String CAMERA_FOLLOW_CHARACTER = "camera_free_follow_character";
    private static final String CAMERA_FP_FOV = "camera_first_person_fov";
    private static final String CAMERA_FP_EYE_FORWARD = "camera_first_person_eye_forward";
    private static final String CAMERA_FP_EYE_HEIGHT = "camera_first_person_eye_height";
    private static final String CAMERA_FP_NEAR_CLIP = "camera_first_person_near_clip";
    private static final String CAMERA_FP_EXTEND_LOOK_RANGE = "camera_first_person_extend_look_range";
    // The gyroscope look source. These live alongside the first-person keys
    // because the sensor only ever feeds the look channel while the camera
    // module is in the process; they are not part of the native configuration
    // string's contract, only the settings screen's record of the user's choice.
    private static final String CAMERA_FP_GYRO_ENABLED = "camera_first_person_gyro_enabled";
    private static final String CAMERA_FP_GYRO_HORIZONTAL = "camera_first_person_gyro_horizontal";
    private static final String CAMERA_FP_GYRO_VERTICAL = "camera_first_person_gyro_vertical";
    private static final String CAMERA_FP_GYRO_INVERT_HORIZONTAL =
            "camera_first_person_gyro_invert_horizontal";
    private static final String CAMERA_FP_GYRO_INVERT_VERTICAL =
            "camera_first_person_gyro_invert_vertical";
    private static final String CAMERA_FP_GYRO_DEADZONE = "camera_first_person_gyro_deadzone";
    private static final String CAMERA_FP_GYRO_SMOOTHING = "camera_first_person_gyro_smoothing";
    static final float FP_EYE_FORWARD_MINIMUM = 0f, FP_EYE_FORWARD_MAXIMUM = 0.5f;
    static final float FP_EYE_HEIGHT_MINIMUM = -0.5f, FP_EYE_HEIGHT_MAXIMUM = 0.5f;
    static final float FP_NEAR_CLIP_MINIMUM = 0.001f, FP_NEAR_CLIP_MAXIMUM = 1f;
    static final String CAMERA_CONFIGURATION = "camera_configuration";
    static final String[] CLOTH_MODES = {"game", "stable", "freeze"};

    record CameraExtras(boolean body, boolean face, boolean terrain, double motionScale,
            int clothMode, String preset, double smoothing, double motionSpeed,
            double orbitSpeed, double duration, double targetHeight,
            double segmentSeconds, boolean keyframeLoop) {
        CameraExtras {
            motionScale = bounded(motionScale, 1, 0.05, 5);
            clothMode = clothMode >= 0 && clothMode < CLOTH_MODES.length ? clothMode : 1;
            preset = MOTION_PRESETS[choice(MOTION_PRESETS, preset, 0)];
            smoothing = bounded(smoothing, 0.3, 0, 0.95);
            motionSpeed = bounded(motionSpeed, 1, -20, 20);
            orbitSpeed = bounded(orbitSpeed, 20, -180, 180);
            duration = bounded(duration, 0, 0, 600);
            targetHeight = bounded(targetHeight, 1.2, -5, 5);
            segmentSeconds = bounded(segmentSeconds, 3, 0.2, 60);
        }
    }

    /**
     * Turns on the read-only first-person look probe for the next game launch.
     *
     * This is a research switch, not a feature: it exists only so the developer
     * can ask the game what methods and fields its look path actually has. It is
     * deliberately a preference rather than a system property or an environment
     * variable, because the phone is not rooted and the settings app is the only
     * thing that can reach the game process without root. It is read once at
     * library load, so toggling it takes effect on the next launch, and it does
     * nothing at all unless it is on.
     */
    static final String DEBUG_FP_LOOK_PROBE = "debug_first_person_look_probe";

    /**
     * Where the imported VMD camera motion lives once the game process has
     * materialized it. The settings app cannot build this path: it does not know
     * which user or cloned profile the game runs under, and it cannot write into
     * another app's data directory at all. So the configuration carries the
     * placeholder and {@link RuntimeBootstrap} substitutes the game's own files
     * directory immediately before handing the string to the native loader.
     */
    static final String VMD_FILE_SLOT = "%files%/betterendfield/camera/current.vmd";
    /** The name the settings app publishes the imported .vmd under. */
    static final String VMD_REMOTE_NAME = "vmd.current";
    /** Whether a .vmd has been imported; both the panel and the config read it. */
    static final String CAMERA_VMD_IMPORTED = "camera_vmd_imported";
    /** The published payload's length, so the game side can skip an identical copy. */
    static final String VMD_BYTES = "camera_vmd_bytes";
    private static final String CAMERA_VMD_NAME = "camera_vmd_name";
    private static final String CAMERA_VMD_TIME = "camera_vmd_time";

    /** Mirrors the native loader's header contract in {@code LoadVmdCamera}. */
    private static final String VMD_HEADER_0002 = "Vocaloid Motion Data 0002";
    private static final String VMD_HEADER_LEGACY = "Vocaloid Motion Data file";
    private static final int VMD_HEADER_BYTES = 30;

    /**
     * The motion preset names {@code ParseMotionPreset} accepts, in the order the
     * settings picker shows them. Index 0 has to stay {@code orbit}: it is the
     * desktop default and the fallback for anything unparsable.
     */
    static final String[] MOTION_PRESETS = {"orbit", "dolly_zoom", "crane", "truck"};

    // betterendfieldnext.actions — the desktop sustained special dash.
    private static final String DASH_ENABLED = "dash_enabled";
    private static final String DASH_LIINO_CLEAN = "dash_liino_clean";
    private static final String DASH_AGLINA = "dash_character_aglina";
    private static final String DASH_LIINO = "dash_character_liino";
    private static final String DASH_JSSPSI = "dash_character_jsspsi";
    private static final String DASH_JSSPSI_QUALITY = "dash_jsspsi_quality";
    static final String ACTIONS_CONFIGURATION = "actions_configuration";
    static final String MEDIA_CONFIGURATION = "media_screen_configuration";
    static final String MEDIA_SETTINGS = "media_screen_settings";

    // betterendfieldnext.weapon_appearance — the desktop administrator ink skins.
    // Mirrors WeaponAppearanceConfiguration.cs: one master switch plus one
    // toggle+style pair for the weapon, the character and the combat effects.
    private static final String APPEARANCE_ENABLED = "appearance_enabled";
    private static final String APPEARANCE_REPLACE_WEAPON = "appearance_replace_weapon";
    private static final String APPEARANCE_WEAPON_MODE = "appearance_weapon_mode";
    private static final String APPEARANCE_REPLACE_CHARACTER = "appearance_replace_character";
    private static final String APPEARANCE_CHARACTER_MODE = "appearance_mode";
    private static final String APPEARANCE_REPLACE_EFFECTS = "appearance_replace_effects";
    static final String APPEARANCE_CONFIGURATION = "appearance_configuration";
    static final String APPEARANCE_MODE_INK = "ink";
    static final String APPEARANCE_MODE_UNMASKED = "unmasked";
    static final String APPEARANCE_MODE_DEFAULT = "default";

    // Superseded by UI_HIDE_UID and CAMERA_DITHER. Version 3.2.2 and earlier
    // implemented those two switches in a separate Android-only enhancement
    // module; they now run through the shared desktop sources. The old keys are
    // still read once so an upgrade keeps the user's choice.
    private static final String LEGACY_HIDE_UID = "enhancement_hide_uid";
    private static final String LEGACY_DISABLE_DITHER = "enhancement_disable_dither";

    static final String OVERLAY_ENABLED = "overlay_enabled";
    static final String EFLINK_ENABLED = "eflink_enabled";
    private static final String EFLINK_LAN = "eflink_lan";
    private static final String EFLINK_INTERNET = "eflink_internet";
    private static final String ACCOUNT_TOKEN = "account_token";
    private static final String ACCOUNT_USERNAME = "account_username";
    private static final String ACCOUNT_EMAIL = "account_email";
    private static final String EFLINK_NAME = "eflink_player_name";
    private static final String EFLINK_ROOM = "eflink_room_name";
    private static final String EFLINK_PORT = "eflink_port";
    private static final String EFLINK_MAX = "eflink_max_players";
    private static final String EFLINK_SERVER = "eflink_server";
    static final String EFLINK_DEFAULT_SERVER = "game.venshell.top";
    private static final String OVERLAY_TRANSPARENCY = "overlay_transparency";
    private static final String OVERLAY_AUTO_SNAP = "overlay_auto_snap";
    private static final String OVERLAY_LOG_VISIBLE = "overlay_log_visible";
    private static final String COMMAND_GENERATION = "command_generation";

    static final float OVERLAY_TRANSPARENCY_MAXIMUM = 80f;
    static final float SPEED_MINIMUM = 0.2f;
    static final float SPEED_MAXIMUM = 60.0f;
    static final float FOV_MINIMUM = 20.0f;
    static final float FOV_MAXIMUM = 120.0f;

    private ModuleSettings() {}

    static boolean isOverlayEnabled(Context context) {
        return preferences(context).getBoolean(OVERLAY_ENABLED, false);
    }

    static void setOverlayEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(OVERLAY_ENABLED, enabled).commit();
    }

    static boolean isEflinkEnabled(Context context) {
        return preferences(context).getBoolean(EFLINK_ENABLED, false);
    }

    static void setEflinkEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(EFLINK_ENABLED, enabled).commit();
    }

    /** Older installs had one switch. That choice stays on the LAN side. */
    static boolean isEflinkLanEnabled(Context context) {
        SharedPreferences settings = preferences(context);
        if (!settings.contains(EFLINK_LAN)) return settings.getBoolean(EFLINK_ENABLED, false);
        return settings.getBoolean(EFLINK_LAN, false);
    }

    static void setEflinkLanEnabled(Context context, boolean enabled) {
        SharedPreferences settings = preferences(context);
        boolean internet = settings.getBoolean(EFLINK_INTERNET, false);
        settings.edit()
                .putBoolean(EFLINK_LAN, enabled)
                .putBoolean(EFLINK_ENABLED, enabled || internet)
                .commit();
    }

    static boolean isEflinkInternetEnabled(Context context) {
        return preferences(context).getBoolean(EFLINK_INTERNET, false);
    }

    static void setEflinkInternetEnabled(Context context, boolean enabled) {
        SharedPreferences settings = preferences(context);
        boolean lan = isEflinkLanEnabled(context);
        settings.edit()
                .putBoolean(EFLINK_LAN, lan)
                .putBoolean(EFLINK_INTERNET, enabled)
                .putBoolean(EFLINK_ENABLED, lan || enabled)
                .commit();
    }

    static String getAccountUsername(Context context) {
        return text(preferences(context), ACCOUNT_USERNAME, 32);
    }

    static String getAccountEmail(Context context) {
        return text(preferences(context), ACCOUNT_EMAIL, 254);
    }

    static void setAccount(Context context, String token, String username, String email) {
        preferences(context).edit()
                .putString(ACCOUNT_TOKEN, clip(token, 128))
                .putString(ACCOUNT_USERNAME, clip(username, 32))
                .putString(ACCOUNT_EMAIL, clip(email, 254))
                .commit();
    }

    static void clearAccount(Context context) {
        preferences(context).edit()
                .remove(ACCOUNT_TOKEN)
                .remove(ACCOUNT_USERNAME)
                .remove(ACCOUNT_EMAIL)
                .commit();
    }

    static String getEflinkPlayerName(Context context) {
        return text(preferences(context), EFLINK_NAME, 64);
    }

    static void setEflinkPlayerName(Context context, String name) {
        preferences(context).edit().putString(EFLINK_NAME, clip(name, 64)).commit();
    }

    static String getEflinkRoomName(Context context) {
        return text(preferences(context), EFLINK_ROOM, 64);
    }

    static void setEflinkRoomName(Context context, String name) {
        preferences(context).edit().putString(EFLINK_ROOM, clip(name, 64)).commit();
    }

    static int getEflinkPort(Context context) {
        return port(preferences(context).getInt(EFLINK_PORT, 47615));
    }

    static void setEflinkPort(Context context, int port) {
        int value = port(port);
        if (value == port) preferences(context).edit().putInt(EFLINK_PORT, value).commit();
    }

    static String getEflinkServer(Context context) {
        String stored = text(preferences(context), EFLINK_SERVER, 128);
        return stored.isEmpty() ? EFLINK_DEFAULT_SERVER : stored;
    }

    static void setEflinkServer(Context context, String server) {
        String value = clip(server, 128);
        if (value.isEmpty()) value = EFLINK_DEFAULT_SERVER;
        preferences(context).edit().putString(EFLINK_SERVER, value).commit();
    }

    static int getEflinkMaxPlayers(Context context) {
        return players(preferences(context).getInt(EFLINK_MAX, 8));
    }

    static void setEflinkMaxPlayers(Context context, int count) {
        int value = players(count);
        if (value == count) preferences(context).edit().putInt(EFLINK_MAX, value).commit();
    }

    /** Empty when the feature is off, so the native loader does not start it. */
    static String eflinkConfiguration(SharedPreferences settings) {
        boolean lan = settings.contains(EFLINK_LAN)
                ? settings.getBoolean(EFLINK_LAN, false)
                : settings.getBoolean(EFLINK_ENABLED, false);
        boolean internet = settings.getBoolean(EFLINK_INTERNET, false);
        if (!lan && !internet) return "";
        try {
            return new JSONObject()
                    .put("lan", lan)
                    .put("internet", internet)
                    .put("player_name", text(settings, EFLINK_NAME, 64))
                    .put("room_name", text(settings, EFLINK_ROOM, 64))
                    .put("port", port(settings.getInt(EFLINK_PORT, 47615)))
                    .put("max_players", players(settings.getInt(EFLINK_MAX, 8)))
                    .put("lobby", EFLINK_DEFAULT_SERVER + ":47617")
                    .put("account", text(settings, ACCOUNT_TOKEN, 128))
                    .put("username", text(settings, ACCOUNT_USERNAME, 32))
                    .put("email", text(settings, ACCOUNT_EMAIL, 254))
                    .put("game", "endfield")
                    .toString();
        } catch (JSONException error) {
            return "";
        }
    }

    private static String text(SharedPreferences settings, String key, int limit) {
        return clip(settings.getString(key, ""), limit);
    }

    private static String clip(String value, int limit) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= limit ? trimmed : trimmed.substring(0, limit);
    }

    /** host, host:port, or a URL. Missing port means the lobby port 47617. */
    private static String lobbyEndpoint(String raw) {
        String text = clip(raw, 128);
        if (text.regionMatches(true, 0, "https://", 0, 8)) text = text.substring(8);
        else if (text.regionMatches(true, 0, "http://", 0, 7)) text = text.substring(7);
        int slash = text.indexOf('/');
        if (slash >= 0) text = text.substring(0, slash);
        if (text.isEmpty()) text = EFLINK_DEFAULT_SERVER;
        int colon = text.lastIndexOf(':');
        if (colon > 0 && text.indexOf(':') == colon) {
            try {
                int value = Integer.parseInt(text.substring(colon + 1));
                if (value >= 1 && value <= 65535) return text.substring(0, colon) + ":" + value;
            } catch (NumberFormatException ignored) {
                return EFLINK_DEFAULT_SERVER + ":47617";
            }
            return EFLINK_DEFAULT_SERVER + ":47617";
        }
        return text + ":47617";
    }

    private static int port(int value) {
        return value >= 1024 && value <= 65535 && value != 47616 ? value : 47615;
    }

    private static int players(int value) {
        return value >= 2 && value <= 16 ? value : 8;
    }

    record OverlayAppearance(float transparency, boolean autoSnap) {
        OverlayAppearance {
            transparency = Float.isFinite(transparency)
                    ? Math.max(0f, Math.min(OVERLAY_TRANSPARENCY_MAXIMUM, transparency)) : 0f;
        }

        float alpha() { return 1f - transparency / 100f; }
    }

    static OverlayAppearance getOverlayAppearance(SharedPreferences settings) {
        return new OverlayAppearance(settings.getFloat(OVERLAY_TRANSPARENCY, 0f),
                settings.getBoolean(OVERLAY_AUTO_SNAP, true));
    }

    static float getOverlayTransparency(Context context) {
        return getOverlayAppearance(preferences(context)).transparency();
    }

    static boolean isOverlayAutoSnap(Context context) {
        return getOverlayAppearance(preferences(context)).autoSnap();
    }

    static void setOverlayTransparency(Context context, float transparency) {
        float bounded = new OverlayAppearance(transparency, false).transparency();
        preferences(context).edit().putFloat(OVERLAY_TRANSPARENCY, bounded).commit();
    }

    static void setOverlayAutoSnap(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(OVERLAY_AUTO_SNAP, enabled).commit();
    }

    static boolean isOverlayLogVisible(SharedPreferences settings) {
        return settings.getBoolean(OVERLAY_LOG_VISIBLE, true);
    }

    static boolean isOverlayLogVisible(Context context) {
        return isOverlayLogVisible(preferences(context));
    }

    static void setOverlayLogVisible(Context context, boolean visible) {
        preferences(context).edit().putBoolean(OVERLAY_LOG_VISIBLE, visible).commit();
    }

    /**
     * The next command generation: the value the native pump compares its
     * high-water mark against to drop replayed payloads.
     *
     * Wall-clock milliseconds rather than a counter, because two processes
     * issue commands. The settings app writes through the framework's remote
     * file space, and the in-game overlay submits straight to the relay from
     * inside the game process. The pump keeps one mark for the whole process,
     * so two private counters would simply count past each other and whichever
     * side lagged would have every command silently discarded. A clock is
     * shared by both sides and needs no coordination.
     *
     * The persisted value is only a floor, for the case the clock steps
     * backwards: a counter here would instead reset to zero when the settings
     * app is reinstalled with empty preferences, and then never be accepted
     * again.
     */
    static long nextCommandGeneration(Context context) {
        SharedPreferences settings = preferences(context);
        long last = settings.getLong(COMMAND_GENERATION, 0L);
        long next = Math.max(System.currentTimeMillis(), last + 1L);
        settings.edit().putLong(COMMAND_GENERATION, next).commit();
        return next;
    }

    // ---------------------------------------------------------------- interface

    static boolean isHideUidEnabled(Context context) {
        SharedPreferences settings = preferences(context);
        return settings.contains(UI_HIDE_UID)
                ? settings.getBoolean(UI_HIDE_UID, false)
                : settings.getBoolean(LEGACY_HIDE_UID, false);
    }

    static boolean isHideHudEnabled(Context context) {
        return preferences(context).getBoolean(UI_HIDE_HUD, false);
    }

    static boolean isPcUiEnabled(Context context) {
        return preferences(context).getBoolean(PC_UI_ENABLED, false);
    }

    static void setPcUiEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(PC_UI_ENABLED, enabled).commit();
        setInterfaceSettings(context, isHideUidEnabled(context), isHideHudEnabled(context));
    }

    /**
     * The interface module configuration exactly as betterendfieldnext.ui parses it.
     *
     * Deliberately free of Android calls: this text is the contract handed to
     * the native module, and the key names have to match its string comparisons
     * one for one. A key typed slightly wrong is not an error anywhere - the
     * parser ignores it silently and the switch simply does nothing - so the
     * function is kept pure to be exercised off-device.
     */
    static String interfaceConfiguration(boolean hideUid, boolean hideHud, boolean pcUi) {
        // An empty configuration is what keeps the module out of the game process,
        // so it has to be empty exactly when nothing is selected.
        return hideUid || hideHud || pcUi
                ? "schema_version=1\n"
                        + "enabled=true\n"
                        + "diagnostics=true\n"
                        + "hide_uid_enabled=" + hideUid + "\n"
                        + "hide_hud_enabled=" + hideHud + "\n"
                        + "pc_ui_enabled=" + pcUi + "\n"
                        + "hide_hud_hotkey=" + Hotkeys.HIDE_HUD_NAME + "\n"
                // PC UI changes only input type; it never spoofs the platform.
                        + "mobile_ui_enabled=false\n"
                        + "platform_spoof_enabled=false\n"
                : "";
    }

    static void setInterfaceSettings(Context context, boolean hideUid, boolean hideHud) {
        String configuration = interfaceConfiguration(hideUid, hideHud, isPcUiEnabled(context));
        preferences(context)
                .edit()
                .putBoolean(UI_HIDE_UID, hideUid)
                .putBoolean(UI_HIDE_HUD, hideHud)
                .putString(UI_CONFIGURATION, configuration)
                .commit();
    }

    // ------------------------------------------------------------------- camera

    static boolean isDisableDitherEnabled(Context context) {
        SharedPreferences settings = preferences(context);
        return settings.contains(CAMERA_DITHER)
                ? settings.getBoolean(CAMERA_DITHER, false)
                : settings.getBoolean(LEGACY_DISABLE_DITHER, false);
    }

    static boolean isFreeCameraEnabled(Context context) {
        return preferences(context).getBoolean(CAMERA_FREE, false);
    }

    static boolean isWorldPauseEnabled(Context context) {
        return preferences(context).getBoolean(CAMERA_PAUSE, false);
    }







    static String getCameraSpeed(Context context) {
        return preferences(context).getString(CAMERA_SPEED, "5");
    }

    static String getCameraFieldOfView(Context context) {
        return preferences(context).getString(CAMERA_FOV, "60");
    }

    static boolean isGlobalFovEnabled(Context context) {
        return preferences(context).getBoolean(CAMERA_GLOBAL_FOV_ENABLED, false);
    }

    static float getGlobalFov(Context context) {
        return (float) bounded(parse(preferences(context).getString(CAMERA_GLOBAL_FOV, "60"), 60.0),
                60.0, 5.0, 150.0);
    }

    static boolean setGlobalFov(Context context, boolean enabled, double value) {
        return preferences(context).edit()
                .putBoolean(CAMERA_GLOBAL_FOV_ENABLED, enabled)
                .putString(CAMERA_GLOBAL_FOV, number(bounded(value, 60.0, 5.0, 150.0)))
                .commit();
    }

    /**
     * Applies a field-of-view change for a caller that has no settings screen.
     *
     * <p>The in-game overlay arrives through the cross-process settings channel.
     * It holds neither the settings screen's state nor a way to deliver a
     * command - runtime commands are issued from the module process - so this
     * does both halves: the preference, which is what the next launch boots
     * with, and the running runtime, by rewriting the two relevant lines of the
     * stored camera configuration and handing the result to
     * {@link ModuleCommandRouter}. The rewrite is line-level for the reason
     * {@link #withIniValue} gives: regenerating the text from a caller's view of
     * the settings would reset every other camera option on the way past, and
     * the overlay never saw those options.
     *
     * <p>Which command carries the result is decided by the same comparison the
     * settings screen uses. A value-only change travels on the lightweight
     * {@code global_fov} command; switching the override on or off reloads,
     * because that decides whether the override exists at all. An empty stored
     * configuration means no camera module was loaded, and then the preference
     * alone is the whole change.
     *
     * @return whether a command for this change was handed to the running game
     */
    static boolean applyGlobalFov(Context context, boolean enabled, double value) {
        if (!setGlobalFov(context, enabled, value)) return false;
        SharedPreferences prefs = preferences(context);
        String configuration = prefs.getString(CAMERA_CONFIGURATION, "");
        if (configuration == null || configuration.isEmpty()) return true;
        String updated = withIniValue(withIniValue(configuration, "global_fov_enabled",
                enabled ? "true" : "false"), "global_fov", number(bounded(value, 60.0, 5.0, 150.0)));
        if (updated.equals(configuration)) return true;
        prefs.edit().putString(CAMERA_CONFIGURATION, updated).commit();
        return onlyGlobalFovChanged(configuration, updated)
                ? ModuleCommandRouter.issue(context, "global_fov", globalFovCommand(updated))
                : ModuleCommandRouter.issue(context, "camera_config", updated);
    }

    static boolean isFreeCameraFollowCharacter(Context context) {
        return preferences(context).getBoolean(CAMERA_FOLLOW_CHARACTER, false);
    }

    static boolean setFreeCameraFollowCharacter(Context context, boolean enabled) {
        return preferences(context).edit().putBoolean(CAMERA_FOLLOW_CHARACTER, enabled).commit();
    }

    static String getFirstPersonFieldOfView(Context context) {
        return preferences(context).getString(CAMERA_FP_FOV, "75");
    }

    static String getFirstPersonEyeForward(Context context) {
        return preferences(context).getString(CAMERA_FP_EYE_FORWARD, "0.03");
    }

    static String getFirstPersonEyeHeight(Context context) {
        return preferences(context).getString(CAMERA_FP_EYE_HEIGHT, "0.05");
    }

    static String getFirstPersonNearClip(Context context) {
        return preferences(context).getString(CAMERA_FP_NEAR_CLIP, "0.03");
    }

    static boolean isFirstPersonExtendLookRange(Context context) {
        return preferences(context).getBoolean(CAMERA_FP_EXTEND_LOOK_RANGE, false);
    }

    /** Advanced camera preferences share one normalized save/read contract. */
    record FirstPersonAdvanced(boolean movement, double sideLookLimit,
            double lookUpLimit, double lookDownLimit, int animationMode,
            double animationStrength, boolean yieldDialogue, boolean thirdPersonInCombat, double transitionSeconds,
            boolean externalHeadScale) {
        FirstPersonAdvanced {
            sideLookLimit = bounded(sideLookLimit, 60, 0, 90);
            lookUpLimit = bounded(lookUpLimit, 89, 0, 89);
            lookDownLimit = bounded(lookDownLimit, 89, 0, 89);
            animationMode = animationMode >= 0 && animationMode <= 3 ? animationMode : 0;
            animationStrength = bounded(animationStrength, 0.35, 0, 1);
            transitionSeconds = bounded(transitionSeconds, 0, 0, 1);
        }

        String toIniLines() {
            return "first_person_movement=" + movement + "\n"
                    + "first_person_side_look_limit=" + number(sideLookLimit) + "\n"
                    + "first_person_look_up_limit=" + number(lookUpLimit) + "\n"
                    + "first_person_look_down_limit=" + number(lookDownLimit) + "\n"
                    + "first_person_animation_mode=" + animationMode + "\n"
                    + "first_person_animation_strength=" + number(animationStrength) + "\n"
                    + "first_person_yield_dialogue=" + yieldDialogue + "\n"
                    + "first_person_third_person_in_combat=" + thirdPersonInCombat + "\n"
                    + "first_person_transition_seconds=" + number(transitionSeconds) + "\n"
                    + "first_person_external_head_scale=" + externalHeadScale + "\n";
        }

        void store(SharedPreferences.Editor edit) {
            edit.putBoolean("camera_first_person_movement", movement)
                    .putString("camera_first_person_side_look_limit", number(sideLookLimit))
                    .putString("camera_first_person_look_up_limit", number(lookUpLimit))
                    .putString("camera_first_person_look_down_limit", number(lookDownLimit))
                    .putInt("camera_first_person_animation_mode", animationMode)
                    .putString("camera_first_person_animation_strength", number(animationStrength))
                    .putBoolean("camera_first_person_yield_dialogue", yieldDialogue)
                    .putBoolean("camera_first_person_third_person_in_combat", thirdPersonInCombat)
                    .putString("camera_first_person_transition_seconds", number(transitionSeconds))
                    .putBoolean("camera_first_person_external_head_scale", externalHeadScale);
        }
    }

    static FirstPersonAdvanced getFirstPersonAdvanced(Context context) {
        SharedPreferences prefs = preferences(context);
        return new FirstPersonAdvanced(
                prefs.getBoolean("camera_first_person_movement", false),
                parse(prefs.getString("camera_first_person_side_look_limit", "60"), 60),
                parse(prefs.getString("camera_first_person_look_up_limit", "89"), 89),
                parse(prefs.getString("camera_first_person_look_down_limit", "89"), 89),
                prefs.getInt("camera_first_person_animation_mode", 0),
                parse(prefs.getString("camera_first_person_animation_strength", "0.35"), 0.35),
                prefs.getBoolean("camera_first_person_yield_dialogue", false),
                prefs.getBoolean("camera_first_person_third_person_in_combat", false),
                parse(prefs.getString("camera_first_person_transition_seconds", "0"), 0),
                prefs.getBoolean("camera_first_person_external_head_scale", false));
    }

    /**
     * The gyroscope look source.
     *
     * Every number is clamped here to the range {@link GyroscopeController}
     * accepts, so a value this screen allows can never be silently corrected on
     * its way to the sensor loop. The record is also the one place the settings
     * screen, the game-process bootstrap and the controller agree on defaults:
     * {@link #disabled()} is what a fresh install and every "not configured"
     * read produce, which is why the sensor is never armed by accident.
     *
     * <p>The two invert switches exist because the correct sign depends on how a
     * device is held and how its sensor axes are laid out; the controller picks
     * a sensible mapping from the screen rotation and these are the escape hatch
     * for a device that disagrees. They are not a substitute for getting the
     * default right.
     */
    record FirstPersonGyro(boolean enabled, double horizontalSensitivity,
            double verticalSensitivity, boolean invertHorizontal, boolean invertVertical,
            double deadzone, double smoothing) {
        /** Matches the controller's own clamps: 0.2–5.0× either axis. */
        static final double SENSITIVITY_MINIMUM = 0.2, SENSITIVITY_MAXIMUM = 5.0;
        /** Below the sensor's noise floor is meaningless; above 0.25 swallows real input. */
        static final double DEADZONE_MINIMUM = 0.0, DEADZONE_MAXIMUM = 0.25;
        /** 0 is no filtering at all; the controller rejects anything heavier than this. */
        static final double SMOOTHING_MINIMUM = 0.0, SMOOTHING_MAXIMUM = 0.9;

        FirstPersonGyro {
            horizontalSensitivity = bounded(horizontalSensitivity, 1.0,
                    SENSITIVITY_MINIMUM, SENSITIVITY_MAXIMUM);
            verticalSensitivity = bounded(verticalSensitivity, 1.0,
                    SENSITIVITY_MINIMUM, SENSITIVITY_MAXIMUM);
            deadzone = bounded(deadzone, GyroscopeController.defaultDeadzone(),
                    DEADZONE_MINIMUM, DEADZONE_MAXIMUM);
            smoothing = bounded(smoothing, GyroscopeController.defaultSmoothing(),
                    SMOOTHING_MINIMUM, SMOOTHING_MAXIMUM);
        }

        /** The state every read starts from, and what "off" means everywhere. */
        static FirstPersonGyro disabled() {
            // Inversion matches the read defaults: off and default are the same
            // pointing behaviour, so re-enabling never silently re-flips an axis.
            return new FirstPersonGyro(false, 1.0, 1.0, true, true,
                    GyroscopeController.defaultDeadzone(), GyroscopeController.defaultSmoothing());
        }

        /**
         * The keys the native camera module parses.
         *
         * The sensor itself is armed on this side and the deltas reach the game
         * through the input relay, so nothing here is what "turns the gyroscope
         * on". What the native side does need is where those deltas should go:
         * {@code first_person_gyro_look} is what selects the first-person
         * consumer over the free camera's, and the two scales are what the game's
         * RotateCamera* receive per relay pixel. Writing only the enabled flag
         * (as an earlier revision did) left the native side unable to tell a
         * gyroscope from a finger, so the switch turned on and nothing moved.
         *
         * <p>The scales are the gyroscope's own, not the free camera's
         * {@code mouse_sensitivity}: that value is tuned for a drag across a
         * screen, and it is also clobbered by the camera-motion screen.
         */
        String toIniLines() {
            return "first_person_gyro_enabled=" + enabled + "\n"
                    + "first_person_gyro_look=" + enabled + "\n"
                    + "first_person_gyro_horizontal=" + number(horizontalSensitivity) + "\n"
                    + "first_person_gyro_vertical=" + number(verticalSensitivity) + "\n"
                    + "first_person_gyro_invert_horizontal=" + invertHorizontal + "\n"
                    + "first_person_gyro_invert_vertical=" + invertVertical + "\n"
                    + "first_person_gyro_deadzone=" + number(deadzone) + "\n"
                    + "first_person_gyro_smoothing=" + number(smoothing) + "\n";
        }

        void store(SharedPreferences.Editor edit) {
            edit.putBoolean(CAMERA_FP_GYRO_ENABLED, enabled)
                    .putString(CAMERA_FP_GYRO_HORIZONTAL, number(horizontalSensitivity))
                    .putString(CAMERA_FP_GYRO_VERTICAL, number(verticalSensitivity))
                    .putBoolean(CAMERA_FP_GYRO_INVERT_HORIZONTAL, invertHorizontal)
                    .putBoolean(CAMERA_FP_GYRO_INVERT_VERTICAL, invertVertical)
                    .putString(CAMERA_FP_GYRO_DEADZONE, number(deadzone))
                    .putString(CAMERA_FP_GYRO_SMOOTHING, number(smoothing));
        }
    }

    static FirstPersonGyro getFirstPersonGyro(Context context) {
        return readFirstPersonGyro(preferences(context));
    }

    /** The settings-process read of the look probe switch. */
    static boolean readFirstPersonLookProbe(Context context) {
        return preferences(context).getBoolean(DEBUG_FP_LOOK_PROBE, false);
    }

    /**
     * Written from the settings process only. The value reaches the game through
     * the framework's remote preferences, which is what makes this switch usable
     * on a non-rooted device where neither `setprop` nor an exported environment
     * variable can reach the game's process.
     */
    static void writeFirstPersonLookProbe(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(DEBUG_FP_LOOK_PROBE, enabled).apply();
    }

    /**
     * The same read, from a preferences snapshot the caller already holds.
     *
     * The game process must use this overload: {@link #preferences(Context)}
     * opens the file that belongs to <em>the context's own package</em>, and the
     * context there is the game's Application, so the file it would open is the
     * game's own "module_settings" — which no settings screen ever writes. The
     * module's values reach the game through the framework's remote
     * preferences, and {@code getRemotePreferences("module_settings")} is what
     * carries them.
     */
    static FirstPersonGyro readFirstPersonGyro(SharedPreferences prefs) {
        return new FirstPersonGyro(
                prefs.getBoolean(CAMERA_FP_GYRO_ENABLED, false),
                parse(prefs.getString(CAMERA_FP_GYRO_HORIZONTAL, "1"), 1),
                parse(prefs.getString(CAMERA_FP_GYRO_VERTICAL, "1"), 1),
                // Both axes default to inverted. The raw sensor axes reach the
                // camera looking right and looking down in the opposite sense to
                // what the module expects, which the first device run confirmed
                // as "both axes are backwards". Keeping that correction in the
                // defaults rather than in the sensor mapping leaves the two
                // switches meaningful: they still mean "flip this axis", and a
                // user whose device differs can turn either one back off.
                prefs.getBoolean(CAMERA_FP_GYRO_INVERT_HORIZONTAL, true),
                prefs.getBoolean(CAMERA_FP_GYRO_INVERT_VERTICAL, true),
                parse(prefs.getString(CAMERA_FP_GYRO_DEADZONE,
                        String.valueOf(GyroscopeController.defaultDeadzone())),
                        GyroscopeController.defaultDeadzone()),
                parse(prefs.getString(CAMERA_FP_GYRO_SMOOTHING,
                        String.valueOf(GyroscopeController.defaultSmoothing())),
                        GyroscopeController.defaultSmoothing()));
    }

    /**
     * The free camera's motion, keyframe and VMD parameters.
     *
     * One normalized save/read contract, like {@link FirstPersonAdvanced}: every
     * number is clamped to the exact range the native module clamps it to, so a
     * value this screen accepts can never be silently changed again on the way
     * into the game. {@code mouseInvertY} and {@code sensitivity} are part of the
     * block because the configuration always carried them (they used to be the
     * literals {@code false} and {@code 0.1}); the on-screen look pad is what will
     * finally give them something to steer, at which point only the UI for them
     * is still missing.
     *
     * {@code vmdImported} decides whether {@code vmd_camera_file} carries the
     * {@link #VMD_FILE_SLOT} placeholder. It is a switch rather than a path
     * because the Android side has exactly one deliverable slot: the file the
     * settings app published and the game process materialized. The publication
     * metadata (name, size, time) is deliberately kept out of this record - none
     * of it reaches the native loader, it only describes the slot on screen.
     */
    record CameraMotion(boolean invertY, double sensitivity, String preset, double speed,
            double orbitSpeed, double duration, double targetHeight, double segmentSeconds,
            boolean keyframeLoop, boolean vmdImported, double vmdScale, double vmdFovBias,
            boolean vmdLoop) {
        CameraMotion {
            sensitivity = bounded(sensitivity, 0.1, 0.02, 0.5);
            speed = bounded(speed, 1.0, -20, 20);
            orbitSpeed = bounded(orbitSpeed, 20.0, -180, 180);
            duration = bounded(duration, 0, 0, 600);
            targetHeight = bounded(targetHeight, 1.2, -5, 5);
            segmentSeconds = bounded(segmentSeconds, 3.0, 0.2, 60);
            vmdScale = bounded(vmdScale, 0.07, 0.001, 10);
            vmdFovBias = bounded(vmdFovBias, 5.0, -60, 60);
            preset = presetName(preset);
        }

        String toIniLines() {
            return "mouse_invert_y=" + invertY + "\n"
                    + "mouse_sensitivity=" + number(sensitivity) + "\n"
                    + "motion_preset=" + preset + "\n"
                    + "motion_speed=" + number(speed) + "\n"
                    + "orbit_speed=" + number(orbitSpeed) + "\n"
                    + "motion_duration=" + number(duration) + "\n"
                    + "motion_target_height=" + number(targetHeight) + "\n"
                    + "keyframe_segment_seconds=" + number(segmentSeconds) + "\n"
                    + "keyframe_loop=" + keyframeLoop + "\n"
                    // The slot is a placeholder rather than a path because only
                    // the game process knows its own files directory. An empty
                    // value still travels as a key: the native reader then sees
                    // the same key set every launch and reports "no VMD camera
                    // file is configured" instead of having to guess.
                    + "vmd_camera_file=" + (vmdImported ? VMD_FILE_SLOT : "") + "\n"
                    + "vmd_camera_scale=" + number(vmdScale) + "\n"
                    + "vmd_camera_fov_bias=" + number(vmdFovBias) + "\n"
                    + "vmd_camera_loop=" + vmdLoop + "\n";
        }

        void store(SharedPreferences.Editor edit) {
            edit.putBoolean("camera_mouse_invert_y", invertY)
                    .putString("camera_mouse_sensitivity", number(sensitivity))
                    .putString("camera_motion_preset", preset)
                    .putString("camera_motion_speed", number(speed))
                    .putString("camera_orbit_speed", number(orbitSpeed))
                    .putString("camera_motion_duration", number(duration))
                    .putString("camera_motion_target_height", number(targetHeight))
                    .putString("camera_keyframe_segment_seconds", number(segmentSeconds))
                    .putBoolean("camera_keyframe_loop", keyframeLoop)
                    .putBoolean(CAMERA_VMD_IMPORTED, vmdImported)
                    .putString("camera_vmd_scale", number(vmdScale))
                    .putString("camera_vmd_fov_bias", number(vmdFovBias))
                    .putBoolean("camera_vmd_loop", vmdLoop);
        }
    }

    /** Maps a stored or user-typed preset onto the four names the parser knows. */
    static String presetName(String value) {
        if (value != null) {
            String text = value.trim().toLowerCase(Locale.ROOT);
            for (String preset : MOTION_PRESETS) {
                if (preset.equals(text)) return preset;
            }
            // ParseMotionPreset also accepts these two short forms. Recognising
            // them here keeps the round trip lossless: without it a preset that
            // reached the store from anywhere else would silently become orbit
            // the next time any row on this screen was touched.
            if ("dolly".equals(text)) return MOTION_PRESETS[1];
            if ("pan".equals(text)) return MOTION_PRESETS[3];
        }
        return MOTION_PRESETS[0];
    }

    static double getFreeCameraSmoothing(Context context) {
        return bounded(parse(preferences(context).getString("free_camera_smoothing", "0.3"), 0.3), 0.3, 0, 0.95);
    }

    static boolean setFreeCameraSmoothing(Context context, double value) {
        return preferences(context).edit().putString("free_camera_smoothing",
                number(bounded(value, 0.3, 0, 0.95))).commit();
    }

    static CameraMotion getCameraMotion(Context context) {
        SharedPreferences prefs = preferences(context);
        return new CameraMotion(
                prefs.getBoolean("camera_mouse_invert_y", false),
                parse(prefs.getString("camera_mouse_sensitivity", "0.1"), 0.1),
                prefs.getString("camera_motion_preset", MOTION_PRESETS[0]),
                parse(prefs.getString("camera_motion_speed", "1"), 1.0),
                parse(prefs.getString("camera_orbit_speed", "20"), 20.0),
                parse(prefs.getString("camera_motion_duration", "0"), 0),
                parse(prefs.getString("camera_motion_target_height", "1.2"), 1.2),
                parse(prefs.getString("camera_keyframe_segment_seconds", "3"), 3.0),
                prefs.getBoolean("camera_keyframe_loop", false),
                prefs.getBoolean(CAMERA_VMD_IMPORTED, false),
                parse(prefs.getString("camera_vmd_scale", "0.07"), 0.07),
                parse(prefs.getString("camera_vmd_fov_bias", "5"), 5.0),
                prefs.getBoolean("camera_vmd_loop", false));
    }

    // --------------------------------------------------------------------- MMD

    /**
     * The MMD director's settings, and the file slots it plays from.
     *
     * MMD has two ways to find material and {@code work} picks between them:
     * when it names a work folder under {@code BETTER_ENDFIELD_NEXT_MMD_ROOT}, the
     * native {@code ResolveSources} reads that folder's {@code set.ini}; while
     * it is empty, the director reads the three loose slots below instead. The
     * empty value is therefore not a placeholder but a selection in its own
     * right, and it is the default because a loose playback needs nothing
     * installed.
     *
     * The two forms are not merged on purpose. A work carries its own audio
     * offset, dancer count and per-file pairing, so a slot that happened to be
     * filled while a work was selected would be a second, contradictory answer
     * to the same question.
     *
     * The slot paths are written with the {@code %files%} placeholder, which the
     * camera configuration already resolves to the game's own files directory. A
     * slot that holds no file writes an empty value rather than dropping the key,
     * so the native reader sees the same key set on every launch.
     *
     * {@code body}, {@code face}, {@code terrain}, {@code motionScale} and
     * {@code clothMode} are the EIEM body-motion block, and they belong here
     * because body motion is what an MMD motion is: the director hands the file
     * to the character session, and that session refuses to start unless body
     * motion is enabled. Without them a page could offer to play a dance and only
     * the camera and the music would move.
     */
    record MmdSettings(boolean enabled, boolean loop, boolean music, double seekSeconds,
            double gain, double audioOffset, boolean body, boolean face, boolean terrain,
            double motionScale, int clothMode, boolean motionLoop, String slots, String work) {
        /** The native parser's cloth modes, in {@code vmd_cloth_mode} spelling. */
        static final String[] CLOTH_MODES = {"game", "stable", "freeze"};

        MmdSettings {
            seekSeconds = bounded(seekSeconds, 5, 0.5, 60);
            gain = bounded(gain, 1, 0, 1);
            audioOffset = bounded(audioOffset, 0, -600, 600);
            motionScale = bounded(motionScale, 1.0, 0.05, 5);
            clothMode = clothMode < 0 || clothMode >= CLOTH_MODES.length ? 1 : clothMode;
            slots = slots == null ? "" : slots;
            work = work == null ? "" : work;
        }

        String toIniLines() {
            return "mmd_enabled=" + enabled + "\n"
                    + "mmd_loop=" + loop + "\n"
                    + "mmd_music_enabled=" + music + "\n"
                    + "mmd_seek_seconds=" + number(seekSeconds) + "\n"
                    + "mmd_music_gain=" + number(gain) + "\n"
                    + "mmd_audio_offset=" + number(audioOffset) + "\n"
                    // Android has no companion overlay process, so the visibility
                    // key is not written. The gate stays on because on desktop it
                    // is also what arms the director's overlay loop.
                    + "mmd_overlay_enabled=true\n"
                    // A work folder name, or empty for the loose slots below.
                    + "mmd_work=" + work + "\n"
                    + slotLine("vmd_motion_file", MMD_SLOT_MOTION)
                    + slotLine("mmd_face_file", MMD_SLOT_FACE)
                    + slotLine("mmd_music_file", MMD_SLOT_MUSIC)
                    + "vmd_motion_loop=" + motionLoop + "\n"
                    + "vmd_body_enabled=" + body + "\n"
                    // Face and eyes ride the body session: EIEM drives the whole
                    // rig, so a face with no body is not a state the session has.
                    + "vmd_eyes_enabled=" + (body && face) + "\n"
                    + "vmd_face_enabled=" + (body && face) + "\n"
                    + "vmd_terrain_enabled=" + (body && terrain) + "\n"
                    + "vmd_motion_scale=" + number(motionScale) + "\n"
                    + "vmd_cloth_mode=" + CLOTH_MODES[clothMode] + "\n";
        }

        /** One slot's path, or an empty value when that slot holds no file. */
        private String slotLine(String key, String id) {
            String name = mmdSlotName(slots, id);
            return key + "="
                    + (name.isEmpty() ? "" : "%files%/" + MMD_SLOT_DIRECTORY + "/" + name)
                    + "\n";
        }

        void store(SharedPreferences.Editor edit) {
            edit.putBoolean("mmd_enabled", enabled)
                    .putBoolean("mmd_loop", loop)
                    .putBoolean("mmd_music_enabled", music)
                    .putString("mmd_seek_seconds", number(seekSeconds))
                    .putString("mmd_music_gain", number(gain))
                    .putString("mmd_audio_offset", number(audioOffset))
                    .putBoolean("mmd_body_enabled", body)
                    .putBoolean("mmd_face_enabled", face)
                    .putBoolean("mmd_terrain_enabled", terrain)
                    .putString("mmd_motion_scale", number(motionScale))
                    .putInt("mmd_cloth_mode", clothMode)
                    .putBoolean("mmd_motion_loop", motionLoop)
                    .putString(MMD_WORK, work);
        }
    }

    static MmdSettings getMmdSettings(Context context) {
        SharedPreferences prefs = preferences(context);
        return new MmdSettings(
                prefs.getBoolean("mmd_enabled", false),
                prefs.getBoolean("mmd_loop", false),
                prefs.getBoolean("mmd_music_enabled", true),
                parse(prefs.getString("mmd_seek_seconds", "5"), 5.0),
                parse(prefs.getString("mmd_music_gain", "1"), 1.0),
                parse(prefs.getString("mmd_audio_offset", "0"), 0.0),
                prefs.getBoolean("mmd_body_enabled", false),
                prefs.getBoolean("mmd_face_enabled", false),
                prefs.getBoolean("mmd_terrain_enabled", false),
                parse(prefs.getString("mmd_motion_scale", "1"), 1.0),
                prefs.getInt("mmd_cloth_mode", 1),
                prefs.getBoolean("mmd_motion_loop", false),
                prefs.getString(MMD_SLOTS, ""),
                prefs.getString(MMD_WORK, ""));
    }

    /**
     * Whether MMD has anything to play, which is also what decides whether the
     * page's transport controls are worth offering.
     *
     * The camera slot is included because it is a second consumer of the same
     * director: an MMD with only a camera motion is a valid playback, and the
     * desktop module treats it as one.
     */
    static boolean mmdHasMaterial(String slots, boolean cameraSlot) {
        return cameraSlot
                || !mmdSlotName(slots, MMD_SLOT_MOTION).isEmpty()
                || !mmdSlotName(slots, MMD_SLOT_FACE).isEmpty()
                || !mmdSlotName(slots, MMD_SLOT_MUSIC).isEmpty();
    }

    // ------------------------------------------------------------- MMD slots

    /**
     * Where the imported MMD files land inside the game's own files directory.
     *
     * The native director takes paths, and the settings app cannot write into the
     * game's data directory - different UIDs - so each slot travels through the
     * framework's remote file space and the game process copies it here once on
     * startup, exactly like the VMD camera slot next to it.
     */
    static final String MMD_SLOT_DIRECTORY = "betterendfield/mmd";

    /** The three files a loose (non-library) MMD playback is assembled from. */
    static final String MMD_SLOT_MOTION = "motion";
    static final String MMD_SLOT_FACE = "face";
    static final String MMD_SLOT_MUSIC = "music";

    private static final String MMD_SLOTS = "mmd_slots";

    /** The selected library work folder, empty while the loose slots are in use. */
    static final String MMD_WORK = "mmd_work";

    /** One imported slot: the remote name to read, its filename, and its length. */
    record MmdSlot(String id, String remote, String name, long bytes) {}

    /** The remote name a slot is published under. One live file per slot. */
    static String mmdSlotRemote(String id) {
        return "mmd." + id;
    }

    /** The native loaders' ceiling, so an import is refused here instead of there. */
    static long mmdSlotMaximumBytes() {
        return 64L * 1024 * 1024;
    }

    /**
     * Reduces a picked file's display name to something safe to write as a path.
     *
     * The name ends up inside a generated configuration and as a file name in the
     * game's directory, so anything that could escape a directory or split a line
     * is removed rather than escaped; an empty result falls back to the slot id,
     * which keeps the target deterministic.
     */
    static String sanitizeMmdSlotName(String raw, String fallback) {
        String name = raw == null ? "" : raw.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < name.length() && cleaned.length() < 96; ++i) {
            char c = name.charAt(i);
            boolean safe = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                    || c == '.' || c == '_' || c == '-' || c == ' ' || c == '(' || c == ')';
            cleaned.append(safe ? c : '_');
        }
        String result = cleaned.toString().trim();
        while (result.startsWith(".")) result = result.substring(1);
        return result.isEmpty() ? fallback : result;
    }

    static java.util.List<MmdSlot> mmdSlots(String encoded) {
        java.util.List<MmdSlot> slots = new java.util.ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return slots;
        try {
            org.json.JSONArray array = new org.json.JSONArray(encoded);
            for (int i = 0; i < array.length(); ++i) {
                org.json.JSONObject entry = array.getJSONObject(i);
                String id = entry.optString("id");
                String name = sanitizeMmdSlotName(entry.optString("name"), id);
                long bytes = entry.optLong("bytes");
                if (!id.isEmpty() && bytes > 0) {
                    slots.add(new MmdSlot(id, mmdSlotRemote(id), name, bytes));
                }
            }
        } catch (org.json.JSONException broken) {
            android.util.Log.w("BetterEndfieldNext.Mmd", "MMD slot record unreadable", broken);
        }
        return slots;
    }

    static String mmdSlotName(String encoded, String id) {
        for (MmdSlot slot : mmdSlots(encoded)) {
            if (slot.id().equals(id)) return slot.name();
        }
        return "";
    }

    static long mmdSlotBytes(String encoded, String id) {
        for (MmdSlot slot : mmdSlots(encoded)) {
            if (slot.id().equals(id)) return slot.bytes();
        }
        return 0L;
    }

    /** Adds or replaces one slot, keeping the rest of the record intact. */
    static String mmdSlotsWith(String encoded, String id, String name, long bytes) {
        java.util.List<MmdSlot> slots = mmdSlots(encoded);
        slots.removeIf(slot -> slot.id().equals(id));
        slots.add(new MmdSlot(id, mmdSlotRemote(id), sanitizeMmdSlotName(name, id), bytes));
        return encodeMmdSlots(slots);
    }

    static String mmdSlotsWithout(String encoded, String id) {
        java.util.List<MmdSlot> slots = mmdSlots(encoded);
        slots.removeIf(slot -> slot.id().equals(id));
        return encodeMmdSlots(slots);
    }

    private static String encodeMmdSlots(java.util.List<MmdSlot> slots) {
        org.json.JSONArray array = new org.json.JSONArray();
        for (MmdSlot slot : slots) {
            try {
                array.put(new org.json.JSONObject()
                        .put("id", slot.id())
                        .put("name", slot.name())
                        .put("bytes", slot.bytes()));
            } catch (org.json.JSONException impossible) {
                // put() only throws for a non-finite number, and these are a
                // String and a long.
            }
        }
        return array.toString();
    }

    static String getMmdSlots(Context context) {
        return preferences(context).getString(MMD_SLOTS, "");
    }

    static void setMmdSlots(Context context, String encoded) {
        preferences(context).edit().putString(MMD_SLOTS, encoded == null ? "" : encoded).commit();
    }

    // ----------------------------------------------------------- MMD library

    /**
     * Where the settings app keeps its own copy of an installed work.
     *
     * This is not where the game reads from: the two processes have separate
     * data directories, and the copy exists so a work can be re-published after
     * the framework service reconnects, without asking the user to pick the
     * folder again. The name is not the same as the game's directory because the
     * two live under different UIDs and there is no reason to make them look
     * alike from the outside.
     */
    static final String MMD_LIBRARY_DIRECTORY = "mmd";

    /** The installed-work index; read by the game process through the settings snapshot. */
    static final String MMD_WORKS = "installed_mmd_works";

    /** One published file of a work: the remote name, its plain name, its length. */
    record MmdWorkFile(String remote, String name, long bytes) {}

    /**
     * One installed work.
     *
     * The generation is a UUID and it doubles as the folder name the game scans,
     * which is what makes re-importing a work replace it atomically instead of
     * merging into whatever was there before. A user-visible name would have to
     * be unique, and enforcing that on a phone - where two works genuinely can
     * share a title - buys nothing the generation does not already give.
     */
    record MmdWork(String generation, String name, java.util.List<MmdWorkFile> files) {}

    /** The remote name one work file is published under; unique per work and file. */
    static String mmdWorkRemote(String generation, String name) {
        return "mmd-" + generation + "-" + name;
    }

    /** Per-file ceiling for a work; the whole work is bounded separately. */
    static long mmdWorkMaximumBytes() {
        return 512L * 1024 * 1024;
    }

    static String getMmdWork(Context context) {
        return preferences(context).getString(MMD_WORK, "");
    }

    /**
     * Selects a library work and rewrites the stored camera configuration.
     *
     * Selecting has to do both: the running game is switched by the {@code work}
     * command the page sends alongside this call, while the stored configuration
     * carries the same choice for the next launch. The rewrite is line-level
     * rather than a full regeneration because this also runs from the installer
     * when a work is removed - a path that has no access to the settings
     * screen's state and must not reset every other camera option to defaults.
     *
     * @return whether the rewritten configuration reached the running game
     */
    static boolean setMmdWork(Context context, String work) {
        String folder = work == null ? "" : work.trim();
        SharedPreferences prefs = preferences(context);
        if (folder.equals(prefs.getString(MMD_WORK, ""))) return true;
        prefs.edit().putString(MMD_WORK, folder).commit();
        String configuration = prefs.getString(CAMERA_CONFIGURATION, "");
        if (configuration.isEmpty()) return true;
        String next = withIniValue(configuration, "mmd_work", folder);
        if (next.equals(configuration)) return true;
        prefs.edit().putString(CAMERA_CONFIGURATION, next).commit();
        return ModuleCommandRouter.issue(context, "camera_config", next);
    }

    /**
     * Replaces one key's value inside a stored configuration string.
     *
     * The configuration is a line-oriented text and the whole string is what the
     * native side reloads, so a single-key change has to be expressed as a new
     * string. Working line by line - instead of regenerating the configuration
     * from the caller's view of the settings - is what lets a background
     * operation change its own key while every other setting is carried forward
     * untouched. A key that is not present is appended rather than ignored; the
     * native reader takes the last occurrence, so a missing line would otherwise
     * mean "the change silently did nothing".
     */
    private static String withIniValue(String configuration, String key, String value) {
        StringBuilder out = new StringBuilder(configuration.length() + 32);
        String prefix = key + "=";
        boolean seen = false;
        int position = 0;
        while (position < configuration.length()) {
            int end = configuration.indexOf('\n', position);
            if (end < 0) end = configuration.length();
            String line = configuration.substring(position, end);
            if (line.startsWith(prefix)) {
                if (!seen) {
                    out.append(prefix).append(value).append('\n');
                    seen = true;
                }
            } else {
                out.append(line).append('\n');
            }
            position = end + 1;
        }
        if (!seen) out.append(prefix).append(value).append('\n');
        return out.toString();
    }

    static java.util.List<MmdWork> getMmdWorks(Context context) {
        return mmdWorks(preferences(context).getString(MMD_WORKS, ""));
    }

    static void setMmdWorks(Context context, java.util.List<MmdWork> works) {
        preferences(context).edit().putString(MMD_WORKS, encodeMmdWorks(works)).commit();
    }

    /**
     * Reads the installed-work index, dropping entries that do not describe a
     * usable work rather than failing the whole list.
     *
     * A single corrupt entry must not hide the rest of the library, and the
     * generation is the one field the rest of the pipeline cannot work without -
     * it is the folder name, the remote-name prefix and the identity used to
     * replace a work - so an entry missing it, or carrying anything other than a
     * UUID, is skipped.
     */
    static java.util.List<MmdWork> mmdWorks(String encoded) {
        java.util.List<MmdWork> works = new java.util.ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return works;
        try {
            org.json.JSONArray array = new org.json.JSONArray(encoded);
            for (int i = 0; i < array.length(); ++i) {
                org.json.JSONObject entry = array.getJSONObject(i);
                String generation = entry.optString("generation");
                if (!generation.matches("[a-f0-9-]{36}")) continue;
                java.util.List<MmdWorkFile> files = new java.util.ArrayList<>();
                org.json.JSONArray listed = entry.optJSONArray("files");
                if (listed != null) {
                    for (int j = 0; j < listed.length(); ++j) {
                        org.json.JSONObject file = listed.getJSONObject(j);
                        String name = file.optString("name");
                        long bytes = file.optLong("bytes");
                        if (name.isEmpty() || bytes <= 0) continue;
                        files.add(new MmdWorkFile(mmdWorkRemote(generation, name), name, bytes));
                    }
                }
                works.add(new MmdWork(generation, entry.optString("name", generation), files));
            }
        } catch (org.json.JSONException broken) {
            android.util.Log.w("BetterEndfieldNext.Mmd", "MMD work index unreadable", broken);
        }
        return works;
    }

    private static String encodeMmdWorks(java.util.List<MmdWork> works) {
        org.json.JSONArray array = new org.json.JSONArray();
        for (MmdWork work : works) {
            try {
                org.json.JSONArray files = new org.json.JSONArray();
                for (MmdWorkFile file : work.files()) {
                    files.put(new org.json.JSONObject()
                            .put("name", file.name())
                            .put("bytes", file.bytes()));
                }
                array.put(new org.json.JSONObject()
                        .put("generation", work.generation())
                        .put("name", work.name())
                        .put("files", files));
            } catch (org.json.JSONException impossible) {
                // put() only throws for a non-finite number, and these are
                // strings and longs.
            }
        }
        return array.toString();
    }

    // ------------------------------------------------------------- VMD slot

    /**
     * Records a just-published VMD import. The payload itself is not copied into
     * preferences; only what the pages and the game process need to reason about
     * it: that it exists, what it was called, how long it is and when it arrived.
     */
    static void setVmdImport(Context context, String name, long bytes, long timeMillis) {
        preferences(context)
                .edit()
                .putBoolean(CAMERA_VMD_IMPORTED, true)
                .putString(CAMERA_VMD_NAME, name == null ? "" : name)
                .putLong(VMD_BYTES, bytes)
                .putLong(CAMERA_VMD_TIME, timeMillis)
                .commit();
    }

    static void clearVmdImport(Context context) {
        preferences(context)
                .edit()
                .putBoolean(CAMERA_VMD_IMPORTED, false)
                .remove(CAMERA_VMD_NAME)
                .remove(VMD_BYTES)
                .remove(CAMERA_VMD_TIME)
                .commit();
    }

    static boolean isVmdImported(Context context) {
        return preferences(context).getBoolean(CAMERA_VMD_IMPORTED, false);
    }

    static String getVmdName(Context context) {
        return preferences(context).getString(CAMERA_VMD_NAME, "");
    }

    static long getVmdBytes(Context context) {
        return preferences(context).getLong(VMD_BYTES, 0L);
    }

    static long getVmdTime(Context context) {
        return preferences(context).getLong(CAMERA_VMD_TIME, 0L);
    }

    /**
     * Accepts a file whose first 30 bytes are a VMD header, mirroring
     * {@code LoadVmdCamera} exactly: the two generations it knows, nothing else.
     *
     * The check lives here rather than in the settings screen so the same rule
     * can be driven from a plain JVM test. Getting it wrong is invisible until
     * the game refuses the file, and a wrong file that is accepted here is
     * reported by the native loader as "the file is not a VMD motion" - which
     * tells the user nothing about which step rejected it.
     */
    static boolean isVmdMotion(byte[] header) {
        if (header == null || header.length < VMD_HEADER_BYTES) return false;
        String text = new String(header, 0, VMD_HEADER_BYTES, java.nio.charset.StandardCharsets.ISO_8859_1);
        return text.startsWith(VMD_HEADER_0002) || text.startsWith(VMD_HEADER_LEGACY);
    }

    /** The native loader's ceiling, so an import is refused here instead of there. */
    static long vmdMaximumBytes() {
        return 64L * 1024 * 1024;
    }

    /**
     * What one write of the camera configuration did, for the page's status line.
     *
     * {@code firstEnable} is the one change a reload cannot carry. Whether the
     * game process loads the camera module at all is decided by the
     * configuration that process started with, so switching the camera on when
     * the stored configuration was empty still needs a restart - and the page
     * has to say so instead of claiming the change is already live.
     */
    record CameraWrite(boolean changed, boolean firstEnable, boolean delivered) {}

    static CameraWrite setCameraSettings(
            Context context,
            boolean disableDither,
            boolean freeCamera,
            boolean worldPause,
            boolean firstPerson,
            boolean hideHead,
            boolean fillNeck,
            double movementSpeed,
            double fieldOfView,
            double firstPersonFov,
            double eyeForward,
            double eyeHeight,
            double nearClip,
            boolean extendLookRange,
            FirstPersonAdvanced advanced,
            CameraMotion motion) {
        return setCameraSettings(context, disableDither, freeCamera, worldPause, firstPerson,
                hideHead, fillNeck, movementSpeed, fieldOfView, firstPersonFov, eyeForward,
                eyeHeight, nearClip, extendLookRange, advanced, motion,
                getFirstPersonGyro(context), getMmdSettings(context));
    }

    /**
     * The camera configuration, with the gyroscope block passed explicitly.
     *
     * The gyroscope is a second entry point because it is edited by its own
     * screen: the first-person page writes it, and every other camera write has
     * to carry the stored value forward rather than reset it. Overloading keeps
     * the many existing callers from having to know the sensor exists.
     */
    static CameraWrite setCameraSettings(
            Context context,
            boolean disableDither,
            boolean freeCamera,
            boolean worldPause,
            boolean firstPerson,
            boolean hideHead,
            boolean fillNeck,
            double movementSpeed,
            double fieldOfView,
            double firstPersonFov,
            double eyeForward,
            double eyeHeight,
            double nearClip,
            boolean extendLookRange,
            FirstPersonAdvanced advanced,
            CameraMotion motion,
            FirstPersonGyro gyro) {
        return setCameraSettings(context, disableDither, freeCamera, worldPause, firstPerson,
                hideHead, fillNeck, movementSpeed, fieldOfView, firstPersonFov, eyeForward,
                eyeHeight, nearClip, extendLookRange, advanced, motion, gyro,
                getMmdSettings(context));
    }

    /**
     * The camera configuration, with the gyroscope and the MMD director passed
     * explicitly.
     *
     * Both are second entry points for the same reason: each is edited by its own
     * screen, so every write from the others has to carry the stored value
     * forward rather than reset it. Overloading keeps the many existing callers
     * from having to know either feature exists.
     */
    static CameraWrite setCameraSettings(
            Context context,
            boolean disableDither,
            boolean freeCamera,
            boolean worldPause,
            boolean firstPerson,
            boolean hideHead,
            boolean fillNeck,
            double movementSpeed,
            double fieldOfView,
            double firstPersonFov,
            double eyeForward,
            double eyeHeight,
            double nearClip,
            boolean extendLookRange,
            FirstPersonAdvanced advanced,
            CameraMotion motion,
            FirstPersonGyro gyro,
            MmdSettings mmd) {
        eyeForward = bounded(eyeForward, 0.03, 0.0, 0.5);
        eyeHeight = bounded(eyeHeight, 0.05, -0.5, 0.5);
        nearClip = bounded(nearClip, 0.03, 0.001, 1.0);
        // The three scalars that go straight to the native parser's own clamps,
        // which are 0.5-100 (speed) and 20-120 (both FOVs). Without this the
        // text could carry a value the reader silently corrects - or "NaN",
        // which "%.4f" renders literally and the parser then hands to the float
        // that drives the camera position. The screen's speed slider starts at
        // 0.2, below the native floor, so that end of its travel has always
        // meant 0.5 in the game; this only makes the configuration say so.
        movementSpeed = bounded(movementSpeed, 5.0, 0.5, 100.0);
        fieldOfView = bounded(fieldOfView, 60.0, 20.0, 120.0);
        firstPersonFov = bounded(firstPersonFov, 75.0, 20.0, 120.0);
        // World pause is a free-camera sub-mode on desktop: its hotkey is only
        // read while the free camera is armed, so offering it alone would be a
        // switch that does nothing.
        boolean pause = worldPause && freeCamera;
        // The gyroscope does NOT force the free camera on.
        //
        // It used to: while the gyroscope's deltas had exactly one consumer -
        // StepFreeCamera, which reads them only with the free camera armed -
        // leaving the free camera off made the switch a lie, so turning the
        // gyroscope on turned on the camera it could reach. That is no longer the
        // arrangement. The native side now folds the same deltas into the game's
        // own first-person rotation, so the gyroscope has a consumer with the
        // free camera down (see ApplyFirstPersonLook in
        // native/modules/camera/module.cpp). Forcing the free camera on would now
        // be actively harmful: both consumers drain one accumulator, so the two
        // cameras would each steal half of the other's motion.
        //
        // The module still has to be running for any of this to exist, and the
        // first person is what the gyroscope is for, so it keeps the test.
        boolean globalFovEnabled = isGlobalFovEnabled(context);
        boolean any = disableDither || freeCamera || firstPerson || globalFovEnabled;
        if (gyro.enabled()) {
            firstPerson = true;
            any = true;
        }
        // MMD lives inside the camera module - the director is pumped from that
        // module's engine tick - so switching MMD on has to arm the same gate the
        // camera switches do, or the configuration would stay empty and there
        // would be no module in the process to read the MMD keys at all. It does
        // NOT drag the first-person camera on the way the gyroscope does: the
        // director asks the character session for the body motion and borrows a
        // camera of its own while it plays.
        if (mmd.enabled()) any = true;
        String previous = preferences(context).getString(CAMERA_CONFIGURATION, "");
        if (previous == null) previous = "";
        String configuration = any
                ? "schema_version=3\n"
                        + "enabled=true\n"
                        + "diagnostics=true\n"
                        + "disable_dither_enabled=" + disableDither + "\n"
                        + "free_camera_enabled=" + freeCamera + "\n"
                        + "pause_enabled=" + pause + "\n"
                        + "movement_speed=" + number(movementSpeed) + "\n"
                        + "field_of_view=" + number(fieldOfView) + "\n"
                        + "global_fov_enabled=" + globalFovEnabled + "\n"
                        + "global_fov=" + number(getGlobalFov(context)) + "\n"
                        + "free_camera_follow_character=" + isFreeCameraFollowCharacter(context) + "\n"
                        + "first_person_enabled=" + firstPerson + "\n"
                        + "first_person_fov=" + number(firstPersonFov) + "\n"
                        + "first_person_eye_forward=" + number(eyeForward) + "\n"
                        + "first_person_eye_height=" + number(eyeHeight) + "\n"
                        + "first_person_near_clip=" + number(nearClip) + "\n"
                        + "first_person_extend_look_range=" + extendLookRange + "\n"
                        + advanced.toIniLines()
                        + gyro.toIniLines()
                        + "toggle_hotkey=" + Hotkeys.FREE_CAMERA_NAME + "\n"
                        + "pause_hotkey=" + Hotkeys.WORLD_PAUSE_NAME + "\n"
                        + "first_person_hotkey=" + Hotkeys.FIRST_PERSON_NAME + "\n"
                        // "false" here would mean "ignore the look channel", not
                        // "do not hook a cursor". The key gates two different
                        // things: on Windows it arms the low-level mouse hook,
                        // but StepFreeCamera also skips the panel's drag deltas
                        // when it is off, because both feed g_mouse_dx/dy. There
                        // is no cursor to hook on a phone, yet the on-screen look
                        // pad and the gyroscope still have to steer the camera,
                        // so the flag has to stay on and the pointer terms simply
                        // stay zero.
                        + "free_camera_mouse_look=true\n"
                        + "free_camera_smoothing=" + number(getFreeCameraSmoothing(context)) + "\n"
                        + motion.toIniLines()
                        + mmd.toIniLines()
                        // The panel presses the exact codes the desktop module
                        // polls; pin them so a native default change cannot
                        // silently detach the on-screen buttons.
                        + "roll_left_hotkey=" + Hotkeys.ROLL_LEFT_NAME + "\n"
                        + "roll_right_hotkey=" + Hotkeys.ROLL_RIGHT_NAME + "\n"
                        + "fov_wide_hotkey=" + Hotkeys.FOV_WIDE_NAME + "\n"
                        + "fov_narrow_hotkey=" + Hotkeys.FOV_NARROW_NAME + "\n"
                        + "view_reset_hotkey=" + Hotkeys.VIEW_RESET_NAME + "\n"
                        + "motion_hotkey=" + Hotkeys.MOTION_NAME + "\n"
                        + "keyframe_add_hotkey=" + Hotkeys.KEYFRAME_ADD_NAME + "\n"
                        + "keyframe_play_hotkey=" + Hotkeys.KEYFRAME_PLAY_NAME + "\n"
                        + "keyframe_clear_hotkey=" + Hotkeys.KEYFRAME_CLEAR_NAME + "\n"
                        + "vmd_play_hotkey=" + Hotkeys.VMD_PLAY_NAME + "\n"
                : "";
        SharedPreferences.Editor edit = preferences(context)
                .edit()
                .putBoolean(CAMERA_DITHER, disableDither)
                .putBoolean(CAMERA_FREE, freeCamera)
                .putBoolean(CAMERA_PAUSE, pause)
                .putBoolean(CAMERA_FIRST_PERSON, firstPerson)
                .putBoolean(CAMERA_FP_HIDE_HEAD, hideHead)
                .putBoolean(CAMERA_FP_FILL_NECK, fillNeck)
                .putString(CAMERA_SPEED, number(movementSpeed))
                .putString(CAMERA_FOV, number(fieldOfView))
                .putString(CAMERA_FP_FOV, number(firstPersonFov))
                .putString(CAMERA_FP_EYE_FORWARD, number(eyeForward))
                .putString(CAMERA_FP_EYE_HEIGHT, number(eyeHeight))
                .putString(CAMERA_FP_NEAR_CLIP, number(nearClip))
                .putBoolean(CAMERA_FP_EXTEND_LOOK_RANGE, extendLookRange)
                .putString(CAMERA_CONFIGURATION, configuration);
        advanced.store(edit);
        motion.store(edit);
        gyro.store(edit);
        mmd.store(edit);
        edit.commit();
        if (configuration.equals(previous)) return new CameraWrite(false, false, false);
        // The native module also reads this string once, from the environment
        // the game process sets before loading the library - which is why a
        // parameter change used to cost a restart. Handing the same string to
        // the runtime command pump is what makes the change live: the game
        // process relays it and the camera module replays it through that same
        // boot-time entry point. An empty configuration is delivered too; it is
        // a valid instruction ("turn the camera module's features off") and the
        // native reload carries the exits that assignment alone cannot express.
        // The field-of-view slider reports on every step of a drag, and every step
        // used to reload the whole configuration: the native side rebuilds every
        // camera state and takes the free camera and the MMD director down to
        // apply one number. Nothing else in this text depends on the global_fov
        // line - the lens override reads it as a plain atomic - so a value-only
        // change travels on its own command instead. The text is still committed
        // above, so a restart still boots into the same field of view.
        boolean delivered = onlyGlobalFovChanged(previous, configuration)
                ? ModuleCommandRouter.issue(context, "global_fov", globalFovCommand(configuration))
                : ModuleCommandRouter.issue(context, "camera_config", configuration);
        return new CameraWrite(true, !configuration.isEmpty() && previous.isEmpty(), delivered);
    }

    /**
     * True when the only line that changed between two camera configurations is
     * the live field of view.
     *
     * <p>Both texts are built by {@link #setCameraSettings}, so their lines are in
     * the same order and the same count. Requiring that shape keeps a comparison
     * from matching two texts that were assembled by different code, which is
     * what would let a mismatched pair silently stop delivering either one.
     *
     * <p>An on/off change is deliberately not covered: switching the feature
     * decides whether the override exists at all, and only a reload settles that.
     */
    static boolean onlyGlobalFovChanged(String previous, String configuration) {
        if (previous == null || previous.isEmpty() || configuration.isEmpty()) return false;
        String[] before = previous.split("\n", -1);
        String[] after = configuration.split("\n", -1);
        if (before.length != after.length) return false;
        boolean differed = false;
        for (int index = 0; index < after.length; ++index) {
            if (after[index].equals(before[index])) continue;
            // Note the trailing '=': "global_fov_enabled=" is a different line,
            // and changing it has to fall through to the reload.
            if (!after[index].startsWith("global_fov=") || !before[index].startsWith("global_fov=")) return false;
            differed = true;
        }
        return differed;
    }

    /** The two fields the live field-of-view command carries, read out of the configuration just built. */
    static String globalFovCommand(String configuration) {
        String enabled = "false";
        String value = "60";
        for (String line : configuration.split("\n", -1)) {
            if (line.startsWith("global_fov_enabled=")) enabled = line.substring("global_fov_enabled=".length());
            else if (line.startsWith("global_fov=")) value = line.substring("global_fov=".length());
        }
        return "enabled=" + enabled + "\nvalue=" + value;
    }

    // ----------------------------------------------------------- sustained dash

    static boolean isSustainedDashEnabled(Context context) {
        return preferences(context).getBoolean(DASH_ENABLED, false);
    }

    static boolean isLiinoCleanDashEnabled(Context context) {
        return preferences(context).getBoolean(DASH_LIINO_CLEAN, false);
    }

    /** Desktop 'jsspsi_quality'. Off by default: it rewrites ~370 rider and 32
     * mount bones every frame, which is a measured 0.18 ms on a desktop core and
     * scales with mobile CPU. */
    static boolean isJsspsiQualityEnabled(Context context) {
        return preferences(context).getBoolean(DASH_JSSPSI_QUALITY, false);
    }

    static boolean isDashCharacterEnabled(Context context, String codename) {
        if ("jsspsi".equals(codename)) {
            return preferences(context).getBoolean(DASH_JSSPSI, false);
        }
        return preferences(context).getBoolean(
                "liino".equals(codename) ? DASH_LIINO : DASH_AGLINA, true);
    }

    static void setSustainedDashSettings(
            Context context,
            boolean enabled,
            boolean liinoClean,
            boolean aglina,
            boolean liino,
            boolean jsspsi,
            boolean jsspsiQuality) {
        StringBuilder characters = new StringBuilder();
        if (aglina) characters.append("aglina");
        if (liino) {
            if (characters.length() > 0) characters.append(',');
            characters.append("liino");
        }
        if (jsspsi) {
            if (characters.length() > 0) characters.append(',');
            characters.append("jsspsi");
        }
        // With no character selected the desktop module would arm every one of
        // them, because an absent key means "all" for older configurations.
        boolean active = enabled && characters.length() > 0;
        String configuration = active
                ? "schema_version=3\n"
                        + "enabled=true\n"
                        + "diagnostics=true\n"
                // The bone-pose overlay is not optional on Android: the banks ship
                // in the APK and are materialized before the module starts, so the
                // looping segment always runs from them rather than the native-only hold.
                        + "external_loop=true\n"
                        + "liino_clean=" + liinoClean + "\n"
                        + "jsspsi_quality=" + jsspsiQuality + "\n"
                        + "characters=" + characters + "\n"
                : "";
        preferences(context)
                .edit()
                .putBoolean(DASH_ENABLED, enabled)
                .putBoolean(DASH_LIINO_CLEAN, liinoClean)
                .putBoolean(DASH_AGLINA, aglina)
                .putBoolean(DASH_LIINO, liino)
                .putBoolean(DASH_JSSPSI, jsspsi)
                .putBoolean(DASH_JSSPSI_QUALITY, jsspsiQuality)
                .putString(ACTIONS_CONFIGURATION, configuration)
                .commit();
    }

    // -------------------------------------------------------------- appearance

    static boolean isAppearanceEnabled(Context context) {
        return preferences(context).getBoolean(APPEARANCE_ENABLED, false);
    }

    static boolean isAppearanceReplaceWeapon(Context context) {
        return preferences(context).getBoolean(APPEARANCE_REPLACE_WEAPON, true);
    }

    /** "ink" (the story ink & glow) or "default" (the vanilla Hongyuan look). */
    static String getAppearanceWeaponMode(Context context) {
        String mode = preferences(context).getString(APPEARANCE_WEAPON_MODE, APPEARANCE_MODE_INK);
        return APPEARANCE_MODE_DEFAULT.equals(mode) ? APPEARANCE_MODE_DEFAULT : APPEARANCE_MODE_INK;
    }

    static boolean isAppearanceReplaceCharacter(Context context) {
        return preferences(context).getBoolean(APPEARANCE_REPLACE_CHARACTER, true);
    }

    /** "ink", "unmasked" or "default" (keep the vanilla character form). */
    static String getAppearanceMode(Context context) {
        String mode = preferences(context).getString(APPEARANCE_CHARACTER_MODE, APPEARANCE_MODE_INK);
        if (APPEARANCE_MODE_UNMASKED.equals(mode)) return APPEARANCE_MODE_UNMASKED;
        if (APPEARANCE_MODE_DEFAULT.equals(mode)) return APPEARANCE_MODE_DEFAULT;
        return APPEARANCE_MODE_INK;
    }

    static boolean isAppearanceReplaceEffects(Context context) {
        return preferences(context).getBoolean(APPEARANCE_REPLACE_EFFECTS, true);
    }

    /**
     * One configuration string covering every desktop option, matching the
     * desktop WeaponAppearanceConfiguration.ToIni() layout. An empty
     * configuration keeps the module out of the game process.
     */
    static void setAppearanceSettings(Context context, boolean enabled,
            boolean replaceWeapon, String weaponMode,
            boolean replaceCharacter, String characterMode,
            boolean replaceEffects) {
        String weapon = APPEARANCE_MODE_DEFAULT.equals(weaponMode)
                ? APPEARANCE_MODE_DEFAULT : APPEARANCE_MODE_INK;
        String character;
        if (APPEARANCE_MODE_UNMASKED.equals(characterMode)) character = APPEARANCE_MODE_UNMASKED;
        else if (APPEARANCE_MODE_DEFAULT.equals(characterMode)) character = APPEARANCE_MODE_DEFAULT;
        else character = APPEARANCE_MODE_INK;
        String configuration = enabled
                ? "schema_version=1\n"
                        + "enabled=true\n"
                        + "replace_weapon=" + replaceWeapon + "\n"
                        + "weapon_mode=" + weapon + "\n"
                        + "replace_character=" + replaceCharacter + "\n"
                        + "character_mode=" + character + "\n"
                        + "replace_effects=" + replaceEffects + "\n"
                        + "effects_mode=" + (replaceEffects ? APPEARANCE_MODE_INK : APPEARANCE_MODE_DEFAULT) + "\n"
                : "";
        preferences(context)
                .edit()
                .putBoolean(APPEARANCE_ENABLED, enabled)
                .putBoolean(APPEARANCE_REPLACE_WEAPON, replaceWeapon)
                .putString(APPEARANCE_WEAPON_MODE, weapon)
                .putBoolean(APPEARANCE_REPLACE_CHARACTER, replaceCharacter)
                .putString(APPEARANCE_CHARACTER_MODE, character)
                .putBoolean(APPEARANCE_REPLACE_EFFECTS, replaceEffects)
                .putString(APPEARANCE_CONFIGURATION, configuration)
                .commit();
    }

    /**
     * Rewrites every configuration string from the stored switches. Used once per
     * launch of the settings screen so an upgrade that introduced a new key, or a
     * carried-over pre-3.3 enhancement choice, reaches the game without the user
     * having to touch each page.
     */
    static void republishConfigurations(Context context) {
        setInterfaceSettings(context, isHideUidEnabled(context), isHideHudEnabled(context));
        republishCameraConfiguration(context);
        setSustainedDashSettings(
                context,
                isSustainedDashEnabled(context),
                isLiinoCleanDashEnabled(context),
                isDashCharacterEnabled(context, "aglina"),
                isDashCharacterEnabled(context, "liino"),
                isDashCharacterEnabled(context, "jsspsi"),
                isJsspsiQualityEnabled(context));
        setAppearanceSettings(context,
                isAppearanceEnabled(context),
                isAppearanceReplaceWeapon(context),
                getAppearanceWeaponMode(context),
                isAppearanceReplaceCharacter(context),
                getAppearanceMode(context),
                isAppearanceReplaceEffects(context));
    }

    static synchronized void republishCameraConfiguration(Context context) {
        setCameraSettings(
                context,
                isDisableDitherEnabled(context),
                isFreeCameraEnabled(context),
                isWorldPauseEnabled(context),
                isFirstPersonEnabled(context),
                isFirstPersonHideHead(context),
                isFirstPersonFillNeck(context),
                parse(getCameraSpeed(context), 5.0),
                parse(getCameraFieldOfView(context), 60.0),
                parse(getFirstPersonFieldOfView(context), 75.0),
                parse(getFirstPersonEyeForward(context), 0.03),
                parse(getFirstPersonEyeHeight(context), 0.05),
                parse(getFirstPersonNearClip(context), 0.03),
                isFirstPersonExtendLookRange(context), getFirstPersonAdvanced(context),
                getCameraMotion(context));
        setSustainedDashSettings(
                context,
                isSustainedDashEnabled(context),
                isLiinoCleanDashEnabled(context),
                isDashCharacterEnabled(context, "aglina"),
                isDashCharacterEnabled(context, "liino"),
                isDashCharacterEnabled(context, "jsspsi"),
                isJsspsiQualityEnabled(context));
    }

    static double parse(String value, double fallback) {
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? parsed : fallback;
        } catch (NumberFormatException | NullPointerException invalid) {
            return fallback;
        }
    }

    static String number(double value) {
        String text = String.format(Locale.ROOT, "%.4f", value);
        return text.contains(".")
                ? text.replaceFirst("0+$", "").replaceFirst("\\.$", "") : text;
    }

    private static double bounded(double value, double fallback, double minimum, double maximum) {
        return Double.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
    }

    // -------------------------------------------------------------------- voice

    static String getVoiceCatalogs(Context context) {
        return preferences(context).getString(VOICE_CATALOGS, "");
    }

    static void setVoiceCatalogs(Context context, String catalogs) {
        preferences(context)
                .edit()
                .putString(VOICE_CATALOGS, catalogs)
                .remove("voice_language")
                .commit();
    }

    static String getVoiceRules(Context context) {
        SharedPreferences preferences = preferences(context);
        String rules = preferences.getString(VOICE_RULES, "");
        if (rules != null && !rules.isEmpty()) {
            return rules;
        }
        String legacy = preferences.getString(VOICE_CATALOGS, "");
        if (legacy == null || legacy.isEmpty()) {
            return "";
        }
        StringBuilder migrated = new StringBuilder();
        for (String item : legacy.split(",")) {
            String speaker = switch (item) {
                case "aglina" -> "chr_0013_aglina";
                case "liino" -> "chr_0035_liino";
                default -> "";
            };
            if (!speaker.isEmpty()) {
                if (migrated.length() > 0) migrated.append(';');
                migrated.append(speaker).append(":Japanese");
            }
        }
        return migrated.toString();
    }

    static void setVoiceRules(Context context, String rules) {
        preferences(context)
                .edit()
                .putString(VOICE_RULES, rules == null ? "" : rules)
                .remove(VOICE_CATALOGS)
                .remove("voice_language")
                .commit();
    }

    // -------------------------------------------------------------- login model

    static void setModelEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(MODEL_ENABLED, enabled).commit();
    }

    static boolean isModelRuntimeEnabled(Context context) {
        SharedPreferences settings = preferences(context);
        return settings.contains(MODEL_RUNTIME_ENABLED)
                ? settings.getBoolean(MODEL_RUNTIME_ENABLED, false)
                : settings.getBoolean(MODEL_ENABLED, false);
    }

    static void setModelRuntimeEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(MODEL_RUNTIME_ENABLED, enabled).commit();
    }

    static boolean isModelEnabled(Context context) {
        return preferences(context).getBoolean(MODEL_ENABLED, false);
    }

    static String getModelCharacter(Context context) {
        return preferences(context).getString(MODEL_CHARACTER, "chr_0013_aglina");
    }

    static String getModelAction(Context context) {
        return preferences(context).getString(MODEL_ACTION, "");
    }

    static boolean isModelFinalLoop(Context context) {
        return preferences(context).getBoolean(MODEL_FINAL_LOOP, true);
    }

    static boolean isModelForceLoop(Context context) {
        return preferences(context).getBoolean(MODEL_FORCE_LOOP, false);
    }

    static boolean isModelCrossfade(Context context) {
        return preferences(context).getBoolean(MODEL_CROSSFADE, false);
    }

    static String getModelLoopStart(Context context) {
        return preferences(context).getString(MODEL_LOOP_START, "0.968");
    }

    static String getModelLoopEnd(Context context) {
        return preferences(context).getString(MODEL_LOOP_END, "2.3760002");
    }

    static String getModelCrossfadeDuration(Context context) {
        return preferences(context).getString(MODEL_CROSSFADE_DURATION, "0.20");
    }

    static String getModelScale(Context context) {
        return preferences(context).getString(MODEL_SCALE, "1.0");
    }

    static boolean isLogoEnabled(Context context) {
        return preferences(context).getBoolean(LOGO_ENABLED, false);
    }

    static String getLogoColor(Context context) {
        return preferences(context).getString(LOGO_COLOR, "#FFC928");
    }

    static void setModelSettings(
            Context context,
            boolean enabled,
            String character,
            String action,
            boolean finalLoop,
            boolean forceLoop,
            boolean crossfade,
            String loopStart,
            String loopEnd,
            String crossfadeDuration,
            String scale,
            boolean logoEnabled,
            String logoColor,
            String configuration) {
        preferences(context)
                .edit()
                .putBoolean(MODEL_ENABLED, enabled)
                .putBoolean(MODEL_RUNTIME_ENABLED, enabled)
                .putString(MODEL_CHARACTER, character)
                .putString(MODEL_ACTION, action)
                .putBoolean(MODEL_FINAL_LOOP, finalLoop)
                .putBoolean(MODEL_FORCE_LOOP, forceLoop)
                .putBoolean(MODEL_CROSSFADE, crossfade)
                .putString(MODEL_LOOP_START, loopStart)
                .putString(MODEL_LOOP_END, loopEnd)
                .putString(MODEL_CROSSFADE_DURATION, crossfadeDuration)
                .putString(MODEL_SCALE, scale)
                .putBoolean(LOGO_ENABLED, logoEnabled)
                .putString(LOGO_COLOR, logoColor)
                .putString(MODEL_CONFIGURATION, configuration)
                .commit();
    }

    private static SharedPreferences preferences(Context context) {
        return FrameworkSettings.open(context);
    }
    static String getGlobalFieldOfView(Context context) { return number(getGlobalFov(context)); }
    static synchronized void saveGlobalFov(Context context, Boolean enabled, Double value) throws java.io.IOException {
        if (value != null && (!Double.isFinite(value) || value < 5 || value > 150))
            throw new java.io.IOException(Text.of("FOV 超出范围"));
        if (!applyGlobalFov(context, enabled == null ? isGlobalFovEnabled(context) : enabled,
                value == null ? getGlobalFov(context) : value))
            throw new java.io.IOException(Text.of("FOV 保存失败"));
    }
    static void setGlobalFovEnabled(Context context, boolean enabled) {
        try { saveGlobalFov(context,enabled,null); }
        catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }
    static void setGlobalFieldOfView(Context context, double value) {
        try { saveGlobalFov(context,null,value); }
        catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }

    static boolean isFirstPersonEnabled(Context context) {
        return preferences(context).getBoolean(CAMERA_FIRST_PERSON, false);
    }
    static boolean isFirstPersonHideHead(Context context) {
        return preferences(context).getBoolean(CAMERA_FP_HIDE_HEAD, true);
    }
    static boolean isFirstPersonFillNeck(Context context) {
        return preferences(context).getBoolean(CAMERA_FP_FILL_NECK, true);
    }
    static int choice(String[] choices, String value, int fallback) {
        for(int i=0;i<choices.length;i++) if(choices[i].equals(value)) return i;
        return fallback;
    }

}
