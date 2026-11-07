#include "BetterEndfieldNext/ModuleApi.h"
#include "first_person_math.h"
#include "camera_follow.h"
#include "first_person_facing.h"
#include "first_person_motion.h"
#include "first_person_readback.h"
#include "first_person_cap_vertices.h"
#include "first_person_policy.h"
#include "first_person_dialogue.h"
#include "first_person_transition.h"
#include "first_person_scale.h"
#include "first_person_mesh.h"
#if defined(__ANDROID__)
#include "first_person_profiles.h"
#endif
#include "first_person_shadow.h"
#include "first_person_retry.h"
#include "first_person_head_attachment.h"
// MMD playback and the EIEM DirectVmd body port. These are imported from
// upstream unchanged; the camera module owns the wiring, not the implementations.
#include "BetterEndfieldNext/PoseLease.h"
#include "BetterEndfieldNext/CustomModelGeometry.h"
#include "BetterEndfieldNext/LocalMusic.h"
#include "../../shared/motion/character_pose.h"
#include "../../shared/motion/character_mapping.h"
#include "../../shared/input/hotkey.h"
#include "camera_file_worker.h"
#include "mmd_library.h"
#include "mmd_overlay_protocol.h"
#include "eiem/eiem_body.h"

#include <Windows.h>
#if defined(__ANDROID__)
#include "android_frame.h"
#include "android_camera.h"
#include "android_panel_commands.h"
#endif

#include <algorithm>
#include <atomic>
#include <cctype>
#include <chrono>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <deque>
#include <filesystem>
#include <fstream>
#include <memory>
#include <mutex>
#include <new>
#include <sstream>
#include <string>
#include <string_view>
#include <thread>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>

#if defined(__ANDROID__)
namespace betterendfieldnext {
bool AndroidProbeHeadwearFixture(void* renderer, void* source_mesh);
bool AndroidHeadwearFixtureAvailable(void* renderer, void* source_mesh);
void AndroidWarmHeadwearLayout();
void AndroidDiscardHeadwearFixtures();
void AndroidPruneHeadwearFixtures(void* const* renderers, size_t count);
bool AndroidHeadwearFixtureOwns(void* renderer, void* mesh);
bool AndroidRestoreHeadwearFixture();
}
#endif

namespace BetterEndfieldNext::CameraModule {
namespace {

constexpr char kModuleId[] = "betterendfieldnext.camera";

enum class ModuleState : uint8_t {
    Created,
    Resolving,
    Ready,
    Active,
    Disabled,
    ContractMismatch,
    Failed,
    Stopped,
};

struct CameraConfiguration {
    int schema_version = 0;
    bool enabled = false;
    bool free_camera_enabled = false;
    bool disable_dither_enabled = false;
    bool pause_enabled = false;
    bool first_person_camera_enabled = false;
    bool first_person_hide_head = true;
    bool first_person_fill_neck_hole = true;
    // Eye anchor and lens, ported from the upstream enhancer's CameraEyeHeight /
    // CameraEyeForward and its near-clip override. The upstream defaults are
    // 0.05 / 0.03 / 0.03.
    float first_person_eye_height = 0.05f;
    float first_person_eye_forward = 0.03f;
    float first_person_near_clip = 0.03f;
    // Widens the vertical look range past the game's own pitch clamp.
    bool first_person_extend_look_range = false;
    float first_person_look_up_limit = 89.0f;
    float first_person_look_down_limit = 89.0f;
    bool first_person_movement = false;
    float first_person_side_look_limit = 60.0f;
    int first_person_animation_mode = 0;
    float first_person_animation_strength = 0.35f;
    bool first_person_yield_dialogue = false;
    bool first_person_third_person_in_combat = false;
    float first_person_transition_seconds = 0.0f;
    bool first_person_external_head_scale = false;
    bool diagnostics = true;
    float movement_speed = 5.0f;
    float field_of_view = 60.0f;
    bool global_fov_enabled = false;
    float global_fov = 60.0f;
    bool free_camera_follow_character = false;
    float first_person_fov = 75.0f;
    float first_person_neck_plug_scale = 1.0f;
    int toggle_key = '9';
    int pause_key = '8';
    int first_person_key = VK_OEM_MINUS;
    // Free camera look, motion presets, keyframes and VMD camera playback.
    bool mouse_look = true;
    bool mouse_invert_y = false;
    float mouse_sensitivity = 0.1f;
    // First-person gyroscope consumption. The gyroscope never originates in
    // native code: it arrives as relay look deltas (the same channel a finger
    // drives) and is normally folded into the free camera's target. These
    // tunables let those deltas also drive the *game's own* first-person
    // rotation instead, so the gyroscope works with the free camera off.
    // The scale is applied to raw relay pixels; the game's RotateCamera*
    // parameter units are not knowable statically, hence the on-device
    // calibration loop described in docs/CAMERA_FIRST_PERSON_GYRO_PLAN_20261001.md.
    bool first_person_gyro_look = false;
    float first_person_gyro_horizontal = 1.0f;
    float first_person_gyro_vertical = 1.0f;
    bool first_person_gyro_invert_horizontal = false;
    bool first_person_gyro_invert_vertical = false;
    float first_person_gyro_deadzone = 0.0f;
    float smoothing = 0.3f;
    int motion_preset = 0;
    float motion_speed = 1.0f;
    float orbit_speed = 20.0f;
    float motion_duration = 0.0f;
    float motion_target_height = 1.2f;
    float keyframe_segment_seconds = 3.0f;
    bool keyframe_loop = false;
    std::string keyframe_file;
    std::string vmd_camera_file;
    float vmd_camera_scale = 0.07f;
    float vmd_camera_fov_bias = 5.0f;
    bool vmd_camera_loop = false;
    // EIEM DirectVmd body motion (native/modules/camera/eiem) and MMD playback.
    // The field names and defaults are upstream's so one config file means the
    // same thing on both forks.
    std::string vmd_motion_file;
    bool vmd_body_enabled = false;
    bool vmd_eyes_enabled = false;
    bool vmd_face_enabled = false;
    bool vmd_motion_loop = false;
    bool vmd_terrain_enabled = false; // EIEM grounder terrain follow
    int vmd_cloth_mode = 1;           // EiemBody::ClothMode: 0 game, 1 stable, 2 freeze
    float vmd_motion_scale = 1.0f;    // EIEM displacement multiplier
    float vmd_motion_weight = 1.0f;
    int vmd_motion_key = 0;
    int vmd_motion_pause_key = 0;
    int vmd_motion_stop_key = 0;
    // MMD playback: one clock for motion, the VMD camera and the music track.
    bool mmd_enabled = true;
    // The overlay host is a Windows companion process. The flags are still parsed
    // and published on Android so one shared configuration file means the same
    // thing on both platforms; the Android build simply has no window to show.
    bool mmd_overlay_enabled = true;
    bool mmd_overlay_visible = false;
    bool mmd_loop = false;
    bool mmd_music_enabled = true;
    float mmd_seek_seconds = 5.0f;
    float mmd_music_gain = 1.0f;
    float mmd_audio_offset = 0.0f;
    std::string mmd_work;
    std::string mmd_music_file;
    std::string mmd_face_file;
    int mmd_play_key = 0x100 | VK_RETURN; // numpad Enter
    int mmd_stop_key = VK_ADD;
    int mmd_seek_back_key = VK_NUMPAD4;
    int mmd_seek_forward_key = VK_NUMPAD6;
    int mmd_camera_mode_key = VK_NUMPAD5;
    int mmd_overlay_key = VK_SUBTRACT;
    int roll_left_key = VK_NUMPAD7;
    int roll_right_key = VK_NUMPAD9;
    int fov_wide_key = VK_NUMPAD1;
    int fov_narrow_key = VK_NUMPAD3;
    int view_reset_key = VK_NUMPAD5;
    int motion_key = VK_NUMPAD8;
    int keyframe_add_key = VK_NUMPAD0;
    int keyframe_play_key = VK_NUMPAD2;
    int keyframe_clear_key = VK_NUMPAD4;
    int vmd_play_key = VK_NUMPAD6;
};

struct Vector3 {
    float x = 0.0f;
    float y = 0.0f;
    float z = 0.0f;
};

struct Quaternion {
    float x = 0.0f;
    float y = 0.0f;
    float z = 0.0f;
    float w = 1.0f;
};

struct MethodContract {
    const char* key;
    BE_MethodDescriptorV1 descriptor;
    // Some managed signatures differ only in how IL2CPP reports a byref
    // parameter. The alternate text is only consulted when the primary
    // descriptor does not resolve.
    const char* alternate_parameter_types = nullptr;
    void* pointer = nullptr;
    const void* method_info = nullptr;
    bool resolved = false;
};

struct FieldContract {
    const char* key;
    BE_FieldDescriptorV1 descriptor;
    BE_ResolvedFieldV1 resolved{};
    bool ready = false;
};

const BE_HostApiV1* g_host = nullptr;
std::atomic<ModuleState> g_state{ModuleState::Created};
std::atomic_bool g_free_camera_enabled{false};
std::atomic_bool g_disable_dither_enabled{false};
std::atomic_bool g_pause_enabled{false};
std::atomic_bool g_first_person_camera_enabled{false};
std::atomic_bool g_first_person_hide_head{true};
std::atomic_bool g_first_person_fill_neck_hole{true};
std::atomic<float> g_first_person_eye_height{0.05f};
std::atomic<float> g_first_person_eye_forward{0.03f};
std::atomic<float> g_first_person_near_clip{0.03f};
std::atomic_bool g_first_person_extend_look_range{false};
std::atomic<float> g_first_person_look_up_limit{89.0f};
std::atomic<float> g_first_person_look_down_limit{89.0f};
std::atomic_bool g_first_person_movement{false};
std::atomic<float> g_first_person_side_look_limit{60.0f};
std::atomic_int g_first_person_animation_mode{0};
std::atomic<float> g_first_person_animation_strength{0.35f};
std::atomic_bool g_first_person_yield_dialogue{false};
std::atomic_bool g_first_person_third_person_in_combat{false};
std::atomic<float> g_first_person_transition_seconds{0.0f};
std::atomic_bool g_first_person_external_head_scale{false};
std::atomic_bool g_diagnostics_enabled{true};
std::atomic<float> g_movement_speed{5.0f};
std::atomic<float> g_field_of_view{60.0f};
std::atomic_bool g_global_fov_enabled{false};
std::atomic<float> g_global_fov{60.0f};
#if defined(__ANDROID__)
std::atomic_bool g_android_global_fov_ready{false};
uint64_t g_android_pump_generation = 0;
bool AndroidCameraReady() {
    const auto state = g_state.load(std::memory_order_acquire);
    return betterendfieldnext::OnAndroidFrameThread() &&
        (state == ModuleState::Ready || state == ModuleState::Active || state == ModuleState::Disabled);
}
void AndroidCameraFrame(bool suspend);
#endif
std::atomic_bool g_free_camera_follow_character{false};
#if defined(__ANDROID__)
// Latest FOV the Android settings page asked for, applied on the Unity thread.
std::atomic<float> g_android_fov_request{0.0f};
#endif
std::atomic<float> g_first_person_fov{75.0f};
std::atomic<float> g_first_person_neck_plug_scale{1.0f};
std::atomic_int g_toggle_key{'9'};
std::atomic_int g_pause_key{'8'};
std::atomic_int g_first_person_key{VK_OEM_MINUS};
std::atomic_bool g_mouse_look_enabled{true};
std::atomic_bool g_mouse_invert_y{false};
std::atomic<float> g_mouse_sensitivity{0.1f};
std::atomic_bool g_first_person_gyro_look{false};
std::atomic<float> g_first_person_gyro_horizontal{1.0f};
std::atomic<float> g_first_person_gyro_vertical{1.0f};
std::atomic_bool g_first_person_gyro_invert_horizontal{false};
std::atomic_bool g_first_person_gyro_invert_vertical{false};
std::atomic<float> g_free_smoothing{0.3f};
std::atomic_int g_motion_preset{0};
std::atomic<float> g_motion_speed{1.0f};
std::atomic<float> g_orbit_speed{20.0f};
std::atomic<float> g_motion_duration{0.0f};
std::atomic<float> g_motion_target_height{1.2f};
std::atomic<float> g_keyframe_segment_seconds{3.0f};
std::atomic_bool g_keyframe_loop{false};
std::atomic<float> g_vmd_camera_scale{0.07f};
std::atomic<float> g_vmd_camera_fov_bias{5.0f};
std::atomic_bool g_vmd_camera_loop{false};
std::mutex g_vmd_path_mutex;
std::string g_vmd_camera_file;
std::atomic_int g_roll_left_key{VK_NUMPAD7};
std::atomic_int g_roll_right_key{VK_NUMPAD9};
std::atomic_int g_fov_wide_key{VK_NUMPAD1};
std::atomic_int g_fov_narrow_key{VK_NUMPAD3};
std::atomic_int g_view_reset_key{VK_NUMPAD5};
std::atomic_int g_motion_key{VK_NUMPAD8};
std::atomic_int g_keyframe_add_key{VK_NUMPAD0};
std::atomic_int g_keyframe_play_key{VK_NUMPAD2};
std::atomic_int g_keyframe_clear_key{VK_NUMPAD4};
std::atomic_int g_vmd_play_key{VK_NUMPAD6};

// ---------------------------------------------------------------------------
// MMD director and EIEM body-motion state
//
// The values mirror upstream's so the two forks read one configuration file the
// same way. The camera-file worker, the file generation counter and the
// director flag are declared by free_camera_runtime.inc, which owns the
// VMD camera path.
// ---------------------------------------------------------------------------
constexpr int kVkNumpadEnter = BetterEndfieldNext::Input::kNumpadEnter | VK_RETURN;
enum MmdRequest : uint32_t {
    MmdRequestPlayPause = 1u << 0,
    MmdRequestStop = 1u << 1,
    MmdRequestCameraMode = 1u << 2,
};
std::atomic_bool g_mmd_enabled{true};
std::atomic_bool g_mmd_loop_default{false};
std::atomic_bool g_mmd_music_enabled{true};
std::atomic<float> g_mmd_seek_seconds{5.0f};
std::atomic<float> g_mmd_music_gain{1.0f};
std::atomic<float> g_mmd_audio_offset{0.0f};
std::string g_mmd_work;        // guarded by g_vmd_path_mutex
std::string g_mmd_music_file;  // guarded by g_vmd_path_mutex
std::string g_mmd_face_file;   // guarded by g_vmd_path_mutex
std::string g_vmd_motion_file; // guarded by g_vmd_path_mutex
std::string g_keyframe_file;   // guarded by g_vmd_path_mutex
std::atomic_uint64_t g_asset_config_generation{0};
std::atomic_uint64_t g_mmd_work_generation{0};
std::atomic_uint32_t g_mmd_requests{0};
std::atomic_int g_mmd_seek_steps{0};
std::atomic_int g_mmd_play_key{kVkNumpadEnter};
std::atomic_int g_mmd_stop_key{VK_ADD};
std::atomic_int g_mmd_seek_back_key{VK_NUMPAD4};
std::atomic_int g_mmd_seek_forward_key{VK_NUMPAD6};
std::atomic_int g_mmd_camera_mode_key{VK_NUMPAD5};
std::atomic_int g_mmd_overlay_key{VK_SUBTRACT};
// Read by the overlay host inside mmd_director_runtime.inc. That body compiles
// out on Android, but the desktop build of this file needs the definitions, so
// they live here next to the key that toggles them.
std::atomic_bool g_mmd_overlay_enabled{true};
std::atomic_bool g_mmd_overlay_initial_visible{false};
// Written by the MMD key table, which is surveyed out under Android (the panel
// drives MMD by command, and the default keys collide with its own). Kept
// defined because the desktop build writes it from that same table.
[[maybe_unused]] std::atomic_bool g_mmd_overlay_toggle_request{false};
// Body-motion hotkeys (EIEM DirectVmd). Zero means unbound, which is the
// upstream default: the character preview is switched on from the panel.
std::atomic_bool g_character_preview_enabled{false};
std::atomic_int g_character_play_key{0};
std::atomic_int g_character_pause_key{0};
std::atomic_int g_character_stop_key{0};

using CameraTickFn = void(__fastcall*)(void* instance, void* method);
CameraTickFn g_original_camera_tick = nullptr;

using TailLateTickFn = void(__fastcall*)(void* instance, float deltaTime, void* method);
TailLateTickFn g_original_tail_late_tick = nullptr;

using TimeUnscaledDeltaFn = float(__fastcall*)(void* method);
TimeUnscaledDeltaFn g_original_time_unscaled_delta = nullptr;

// UnityEngine.Rendering.RenderPipelineManager::DoRenderLoop_Internal is a static
// method the engine itself calls once per rendered frame, handing it the native
// render loop pointer. It keeps being called no matter what the game's own
// gameplay and camera code is doing, which is what makes it the pump that
// survives a frozen world; see PumpFromEngineTick.
using RenderLoopFn = void(__fastcall*)(void* pipeline, void* loop_pointer,
    void* render_requests, void* method);
RenderLoopFn g_original_render_loop = nullptr;

// CinemachineBrain::PushStateToUnityCamera(Cinemachine.CameraState& state) is the
// last stage of the Cinemachine pipeline. Rewriting the state there is the only
// place where the pose cannot be overwritten later in the frame.
using PushStateFn = void(__fastcall*)(void* instance, void* state, void* method);
PushStateFn g_original_push_state = nullptr;
bool g_push_state_hook_ready = false;
std::atomic_uint64_t g_push_state_calls{0};
std::atomic_uint64_t g_push_state_patches{0};

bool g_free_camera_contract_ready = false;
bool g_dither_contract_ready = false;
bool g_time_heartbeat_contract_ready = false;
bool g_first_person_contract_ready = false;
bool g_first_person_gyro_contract_ready = false;
bool g_render_loop_hook_ready = false;

bool g_free_camera_active = false;
std::atomic_bool g_toggle_request{false};
std::atomic_bool g_pause_request{false};
// When the pause hotkey latched, and whether the stall it would mean has already
// been reported. Only the input thread writes them; see the watchdog in
// InputThreadMain.
std::atomic_uint64_t g_pause_request_ms{0};
std::atomic_bool g_pause_stall_logged{false};
std::atomic_bool g_force_exit_request{false};
std::atomic_bool g_motion_request{false};
std::atomic_bool g_keyframe_add_request{false};
std::atomic_bool g_keyframe_play_request{false};
std::atomic_bool g_keyframe_clear_request{false};
// Save/load keyframe files. These are driven by the MMD director's keyframe
// commands; the keys themselves stay unbound by default, as upstream.
std::atomic_int g_keyframe_save_key{0};
std::atomic_int g_keyframe_load_key{0};
std::atomic_bool g_keyframe_save_request{false};
std::atomic_bool g_keyframe_load_request{false};
std::atomic_bool g_vmd_play_request{false};
// Mouse look input, accumulated by the input thread's low-level mouse hook.
std::atomic_bool g_free_camera_running{false};
std::atomic_bool g_mouse_capture{false};
std::atomic_int g_mouse_dx{0};
std::atomic_int g_mouse_dy{0};
std::atomic_int g_mouse_wheel{0};

std::atomic_bool g_first_person_active{false};
std::atomic_bool g_first_person_toggle_request{false};
std::atomic_bool g_first_person_exit_request{false};

std::atomic_bool g_input_thread_stop{false};
std::thread g_input_thread;

void* g_active_camera = nullptr;
uint32_t g_active_camera_root = 0;
Vector3 g_original_position{};
float g_original_fov = 60.0f;
float g_original_time_scale = 1.0f;
bool g_changed_time_scale = false;

// ---------------------------------------------------------------------------
// First-person tuning constants. The eye anchor follows the head bone; the two
// eye offsets and the near-clip override are configuration
// (first_person_eye_height / first_person_eye_forward /
// first_person_near_clip), and the vertical look-range multipliers are what
// first_person_extend_look_range selects. Those multipliers are the upstream
// enhancer's values.
// ---------------------------------------------------------------------------
constexpr float kFirstPersonLookUpRange = 1.10f;
constexpr float kFirstPersonLookDownRange = 1.50f;
constexpr size_t kMaxFieldOffset = 0x400;
constexpr int32_t kHumanBodyBonesHead = 11;

// Value type metadata offsets in IL2CPP are relative to the boxed layout, which
// carries an object header in front of the inline data. The header size is
// probed through UnityEngine.Vector3.x (the first field of a 12 byte struct)
// instead of being hardcoded, so the geometry below survives Unity upgrades.
int32_t g_value_type_header = 0;

struct CameraStateLayout {
    int32_t raw_position = -1;              // CameraState body relative
    int32_t raw_orientation = -1;
    int32_t position_correction = -1;
    int32_t orientation_correction = -1;
    int32_t lens = -1;                      // CameraState.Lens, body relative
    int32_t lens_field_of_view = -1;        // LensSettings body relative
    int32_t lens_near_clip = -1;
    int32_t lens_dutch = -1;
    bool ready = false;
};
CameraStateLayout g_state_layout;

BE_ResolvedClassV1 g_snapshot_controller_class{};
BE_ResolvedClassV1 g_animator_class{};
BE_ResolvedClassV1 g_skinned_mesh_renderer_class{};
BE_ResolvedClassV1 g_mesh_renderer_class{};
BE_ResolvedClassV1 g_renderer_class{};

namespace FpMesh = BetterEndfieldNext::FirstPersonMesh;
namespace FpMath = BetterEndfieldNext::FirstPersonMath;

// Renderer pointers inside a probe are only valid during the scan that produced
// them. Bound mesh patches keep GC handles separately because the game can
// rebuild character parts (clothes, hair, LOD levels) at any time.
struct HeadPartProbe {
    std::string object_name;
    std::string mesh_name;
    int32_t vertex_count = -1;
    bool skinned = false;
    bool matched = false;
    bool head_attached = false;
    // Role classification, ported from the upstream enhancer. A dedicated head
    // mesh is collapsed whole; a body mesh only loses the triangles whose three
    // vertices are dominated by head/neck skin, which is the lip around the neck
    // opening that no whole-part hide reaches.
    bool dedicated_head = false;
    bool body_skin = false;
    void* renderer = nullptr;
};

struct FirstPersonSession {
#if defined(__ANDROID__)
    std::string model_id;
#endif
    void* character = nullptr;
    void* head = nullptr;
    uint32_t head_handle = 0;
    void* neck = nullptr;
    uint32_t neck_handle = 0;
    void* snapshot_controller = nullptr;
    uint32_t snapshot_handle = 0;
    // The main camera manager. Unlike the snapshot controller, this one exists
    // for the whole of ordinary play: the module already hooks its TailLateTick,
    // so the instance is captured there and held here for the first-person look
    // path, which must drive the game's own aim rather than the snapshot camera.
    void* camera_manager = nullptr;
    uint32_t camera_manager_handle = 0;
    bool head_hide_applied = false;
    Vector3 last_eye{};
    bool last_eye_valid = false;
};
FirstPersonSession g_first_person;

uint64_t g_first_person_pump_frames = 0;
uint64_t g_first_person_reassert_frames = 0;
bool g_first_person_head_logged = false;
bool g_first_person_health_warned = false;

MethodContract g_contracts[]{
    {"camera.process_dither",
        {"Gameplay.Beyond.dll", "Beyond.Gameplay.View", "CameraMono",
            "_ProcessDitherByPitch", nullptr, "System.Void", 0}},
    {"camera.force_clear_dither",
        {"Gameplay.Beyond.dll", "Beyond.Gameplay.View", "CameraMono",
            "ForceClearDither", nullptr, "System.Void", 0}},
    {"camera_manager.tail_late_tick",
        {"Gameplay.Beyond.dll", "Beyond.Gameplay.View", "CameraManager",
            "TailLateTick", nullptr, "System.Void", 1}},
    {"player_controller.get_main_character",
        {"Gameplay.Beyond.dll", "Beyond.Gameplay.Core", "PlayerController",
            "GetMainCharacter", nullptr, nullptr, 0}},
    {"entity.get_model_com",
        {"Gameplay.Beyond.dll", "Beyond.Gameplay.Core", "Entity",
            "get_modelCom", nullptr, nullptr, 0}},
    {"base_model_component.get_model_go",
        {"Gameplay.Beyond.dll", "Beyond.Gameplay.View", "BaseModelComponent",
            "GetModelGo", nullptr, "UnityEngine.GameObject", 0}},
    {"cinemachine.push_state",
        {"Cinemachine.dll", "Cinemachine", "CinemachineBrain",
            "PushStateToUnityCamera", "Cinemachine.CameraState&", "System.Void", 1},
        "Cinemachine.CameraState"},
    {"snapshot.set_first_person",
        {"Gameplay.Beyond.dll", "Beyond.Gameplay.View", "SnapshotCameraController",
            "SetFirstPerson", "System.Boolean", "System.Void", 1}},
    {"snapshot.show_char",
        {"Gameplay.Beyond.dll", "Beyond.Gameplay.View", "SnapshotCameraController",
            "_ShowChar", nullptr, "System.Void", 0}},
    {"camera_manager.on_input",
        {"Gameplay.Beyond.dll", "Beyond.Gameplay.View", "CameraManager",
            "OnInput", "System.Single|System.Single", "System.Void", 2}},
    {"unity.camera.main",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Camera", "get_main",
            nullptr, "UnityEngine.Camera", 0}},
    {"unity.camera.fov.get",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Camera", "get_fieldOfView",
            nullptr, "System.Single", 0}},
    {"unity.camera.fov.set",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Camera", "set_fieldOfView",
            "System.Single", "System.Void", 1}},
    {"unity.camera.orthographic.get",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Camera", "get_orthographic",
            nullptr, "System.Boolean", 0}},
    {"unity.screen.width.get",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Screen", "get_width",
            nullptr, "System.Int32", 0}},
    {"unity.screen.height.get",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Screen", "get_height",
            nullptr, "System.Int32", 0}},
    {"unity.component.transform",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Component", "get_transform",
            nullptr, "UnityEngine.Transform", 0}},
    {"unity.component.game_object",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Component", "get_gameObject",
            nullptr, "UnityEngine.GameObject", 0}},
    {"unity.game_object.transform",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "GameObject", "get_transform",
            nullptr, "UnityEngine.Transform", 0}},
    {"unity.game_object.get_component",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "GameObject", "GetComponent",
            "System.Type", "UnityEngine.Component", 1}},
    {"unity.game_object.find_with_tag",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "GameObject", "FindWithTag",
            "System.String", "UnityEngine.GameObject", 1}},
    {"unity.object.find_object_of_type",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Object", "FindObjectOfType",
            "System.Type|System.Boolean", "UnityEngine.Object", 2}},
    {"unity.object.name.get",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Object", "get_name",
            nullptr, "System.String", 0}},
    {"unity.skinned_mesh_renderer.shared_mesh.get",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "SkinnedMeshRenderer",
            "get_sharedMesh", nullptr, "UnityEngine.Mesh", 0}},
    {"unity.mesh.vertex_count.get",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Mesh", "get_vertexCount",
            nullptr, "System.Int32", 0}},
    {"unity.object.destroy",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Object", "Destroy",
            "UnityEngine.Object", "System.Void", 1}},
    {"unity.mesh.bindposes.get",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Mesh", "get_bindposes",
            nullptr, "UnityEngine.Matrix4x4[]", 0}},
    {"unity.skinned_mesh_renderer.bones.get",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "SkinnedMeshRenderer",
            "get_bones", nullptr, "UnityEngine.Transform[]", 0}},
    {"unity.renderer.shadow_casting_mode.get",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Renderer",
            "get_shadowCastingMode", nullptr,
            "UnityEngine.Rendering.ShadowCastingMode", 0}},
    {"unity.renderer.shadow_casting_mode.set",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Renderer",
            "set_shadowCastingMode", "UnityEngine.Rendering.ShadowCastingMode",
            "System.Void", 1}},
    {"unity.transform.parent.get",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Transform", "get_parent",
            nullptr, "UnityEngine.Transform", 0}},
    {"unity.object.op_equality",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Object", "op_Equality",
            "UnityEngine.Object|UnityEngine.Object", "System.Boolean", 2}},
    {"unity.animator.get_bone_transform",
        {"UnityEngine.AnimationModule.dll", "UnityEngine", "Animator",
            "GetBoneTransform", "UnityEngine.HumanBodyBones",
            "UnityEngine.Transform", 1}},
    {"unity.transform.position.get",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Transform", "get_position",
            nullptr, "UnityEngine.Vector3", 0}},
    {"unity.transform.position.set",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Transform", "set_position",
            "UnityEngine.Vector3", "System.Void", 1}},
    {"unity.transform.rotation.get",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Transform", "get_rotation",
            nullptr, "UnityEngine.Quaternion", 0}},
    {"unity.transform.rotation.set",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Transform", "set_rotation",
            "UnityEngine.Quaternion", "System.Void", 1}},
    {"unity.transform.forward",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Transform", "get_forward",
            nullptr, "UnityEngine.Vector3", 0}},
    {"unity.transform.right",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Transform", "get_right",
            nullptr, "UnityEngine.Vector3", 0}},
    {"unity.transform.up",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Transform", "get_up",
            nullptr, "UnityEngine.Vector3", 0}},
    {"unity.transform.child_count.get",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Transform", "get_childCount",
            nullptr, "System.Int32", 0}},
    {"unity.transform.get_child",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Transform", "GetChild",
            "System.Int32", "UnityEngine.Transform", 1}},
    {"unity.transform.find",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Transform", "Find",
            "System.String", "UnityEngine.Transform", 1}},
    {"unity.time.scale.get",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Time", "get_timeScale",
            nullptr, "System.Single", 0}},
    {"unity.time.scale.set",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Time", "set_timeScale",
            "System.Single", "System.Void", 1}},
    {"unity.time.unscaled_delta.get",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Time",
            "get_unscaledDeltaTime", nullptr, "System.Single", 0}},
    // The per-frame engine tick (see PumpFromEngineTick). Its third parameter is
    // List<Camera.RenderRequest>, whose IL2CPP type name is not worth pinning:
    // leaving the parameter list empty matches on name and arity alone, which is
    // unambiguous for this method.
    {"unity.render_loop",
        {"UnityEngine.CoreModule.dll", "UnityEngine.Rendering",
            "RenderPipelineManager", "DoRenderLoop_Internal", nullptr,
            "System.Void", 3}},
};

FieldContract g_fields[]{
    {"unity.vector3.x",
        {"UnityEngine.CoreModule.dll", "UnityEngine", "Vector3", "x",
            "System.Single"}},
    {"cinemachine.state.raw_position",
        {"Cinemachine.dll", "Cinemachine", "CameraState", "RawPosition",
            "UnityEngine.Vector3"}},
    {"cinemachine.state.raw_orientation",
        {"Cinemachine.dll", "Cinemachine", "CameraState", "RawOrientation",
            "UnityEngine.Quaternion"}},
    {"cinemachine.state.position_correction",
        {"Cinemachine.dll", "Cinemachine", "CameraState", "PositionCorrection",
            "UnityEngine.Vector3"}},
    {"cinemachine.state.orientation_correction",
        {"Cinemachine.dll", "Cinemachine", "CameraState", "OrientationCorrection",
            "UnityEngine.Quaternion"}},
    {"cinemachine.state.lens",
        {"Cinemachine.dll", "Cinemachine", "CameraState", "Lens",
            "Cinemachine.LensSettings"}},
    {"cinemachine.lens.field_of_view",
        {"Cinemachine.dll", "Cinemachine", "LensSettings", "FieldOfView",
            "System.Single"}},
    {"cinemachine.lens.near_clip",
        {"Cinemachine.dll", "Cinemachine", "LensSettings", "NearClipPlane",
            "System.Single"}},
    {"cinemachine.lens.dutch",
        {"Cinemachine.dll", "Cinemachine", "LensSettings", "Dutch",
            "System.Single"}},
    {"snapshot.is_first_person",
        {"Gameplay.Beyond.dll", "Beyond.Gameplay.View", "SnapshotCameraController",
            "<isFirstPerson>k__BackingField", "System.Boolean"}},
};

MethodContract* Contract(std::string_view key) {
    for (auto& contract : g_contracts) {
        if (contract.key == key) {
            return &contract;
        }
    }
    return nullptr;
}

FieldContract* Field(std::string_view key) {
    for (auto& field : g_fields) {
        if (field.key == key) {
            return &field;
        }
    }
    return nullptr;
}

void Log(const std::string& message) {
    if (g_host && g_host->log) {
        g_host->log(g_host->context, kModuleId, message.c_str());
    }
}

void* Invoke(const MethodContract* method, void* instance, void** parameters) {
    if (!method || !method->method_info || !g_host || !g_host->runtime_invoke) {
        return nullptr;
    }
    void* exception = nullptr;
    void* result = g_host->runtime_invoke(g_host->context, method->method_info,
        instance, parameters, &exception);
    return exception ? nullptr : result;
}

bool InvokeVoid(const MethodContract* method, void* instance, void** parameters) {
    if (!method || !method->method_info || !g_host || !g_host->runtime_invoke) {
        return false;
    }
    void* exception = nullptr;
    g_host->runtime_invoke(g_host->context, method->method_info,
        instance, parameters, &exception);
    return exception == nullptr;
}

template <typename T>
bool Unbox(void* boxed, T& value) {
    if (!boxed || !g_host || !g_host->object_unbox) {
        return false;
    }
    void* raw = g_host->object_unbox(g_host->context, boxed);
    if (!raw) {
        return false;
    }
    std::memcpy(&value, raw, sizeof(T));
    return true;
}

bool GetValue(const MethodContract* method, void* instance, Vector3& value) {
    return Unbox(Invoke(method, instance, nullptr), value);
}

bool GetValue(const MethodContract* method, void* instance, float& value) {
    return Unbox(Invoke(method, instance, nullptr), value);
}

bool GetValue(const MethodContract* method, void* instance, int& value) {
    return Unbox(Invoke(method, instance, nullptr), value);
}

bool GetValue(const MethodContract* method, void* instance, bool& value) {
    return Unbox(Invoke(method, instance, nullptr), value);
}

bool SetValue(const MethodContract* method, void* instance, Vector3 value) {
    void* parameters[1]{&value};
    return InvokeVoid(method, instance, parameters);
}

bool SetValue(const MethodContract* method, void* instance, float value) {
    void* parameters[1]{&value};
    return InvokeVoid(method, instance, parameters);
}

Vector3 Add(Vector3 left, Vector3 right) {
    return {left.x + right.x, left.y + right.y, left.z + right.z};
}

Vector3 Subtract(Vector3 left, Vector3 right) {
    return {left.x - right.x, left.y - right.y, left.z - right.z};
}

Vector3 Scale(Vector3 value, float scale) {
    return {value.x * scale, value.y * scale, value.z * scale};
}

Vector3 Normalize(Vector3 value) {
    const float length = std::sqrt(
        value.x * value.x + value.y * value.y + value.z * value.z);
    return length > 0.0001f ? Scale(value, 1.0f / length) : Vector3{};
}

float Magnitude(Vector3 value) {
    return std::sqrt(value.x * value.x + value.y * value.y + value.z * value.z);
}

bool IsFinite(Vector3 value) {
    return std::isfinite(value.x) && std::isfinite(value.y) &&
        std::isfinite(value.z);
}

bool IsUnitQuaternion(Quaternion value) {
    if (!std::isfinite(value.x) || !std::isfinite(value.y) ||
        !std::isfinite(value.z) || !std::isfinite(value.w)) {
        return false;
    }
    const float length = value.x * value.x + value.y * value.y +
        value.z * value.z + value.w * value.w;
    return length > 0.5f && length < 1.5f;
}

// Rotates a vector by a unit quaternion without touching managed code.
Vector3 RotateVector(Quaternion rotation, Vector3 vector) {
    const Vector3 axis{rotation.x, rotation.y, rotation.z};
    const Vector3 doubled_cross{
        2.0f * (axis.y * vector.z - axis.z * vector.y),
        2.0f * (axis.z * vector.x - axis.x * vector.z),
        2.0f * (axis.x * vector.y - axis.y * vector.x)};
    return {
        vector.x + rotation.w * doubled_cross.x +
            (axis.y * doubled_cross.z - axis.z * doubled_cross.y),
        vector.y + rotation.w * doubled_cross.y +
            (axis.z * doubled_cross.x - axis.x * doubled_cross.z),
        vector.z + rotation.w * doubled_cross.z +
            (axis.x * doubled_cross.y - axis.y * doubled_cross.x)};
}

std::string ReadManagedString(void* managed_string) {
    if (!managed_string || !g_host || !g_host->copy_managed_string) return {};
    char buffer[256]{};
    g_host->copy_managed_string(g_host->context, managed_string, buffer, sizeof(buffer));
    return std::string(buffer);
}

std::string ObjectName(void* object) {
    if (!object) return "<null>";
    return ReadManagedString(Invoke(Contract("unity.object.name.get"), object, nullptr));
}

// UnityEngine.Object::op_Equality is the safe way to tell whether a scene
// object is still alive: it compares the cached native pointers instead of
// dereferencing them, so a destroyed object cannot crash the check.
bool IsObjectAlive(void* object) {
    if (!object) {
        return false;
    }
    void* parameters[2]{object, nullptr};
    bool destroyed = false;
    if (!Unbox(Invoke(Contract("unity.object.op_equality"), nullptr, parameters),
            destroyed)) {
        // Without the contract the object cannot be verified; keeping the anchor
        // is safer than dropping it every frame.
        return true;
    }
    return !destroyed;
}

bool KeyDown(int key) {
    if (key == VK_OEM_MINUS) {
        return ((GetAsyncKeyState(VK_OEM_MINUS) & 0x8000) != 0) ||
               ((GetAsyncKeyState(VK_SUBTRACT) & 0x8000) != 0);
    }
    return (GetAsyncKeyState(key) & 0x8000) != 0;
}

bool GameWindowHasFocus() {
    HWND foreground = GetForegroundWindow();
    if (!foreground) {
        return false;
    }
    DWORD process_id = 0;
    GetWindowThreadProcessId(foreground, &process_id);
    return process_id == GetCurrentProcessId();
}

#if defined(_WIN32)
LRESULT CALLBACK FreeCameraMouseHook(int code, WPARAM message, LPARAM data);
#endif

// Implemented in mmd_director_runtime.inc, which is included further down: the
// input thread below already needs it, so it is declared here at global scope
// alongside that file's other entry points.
void PumpMmdOverlayHost(bool mmd_enabled);

struct HotkeyRequest {
    std::atomic_int* key;
    std::atomic_bool* request;
    bool was_down = false;
};

// How long a latched pause request may wait for a main thread pump before the
// input thread calls it a stall. Long enough that a hitch is not reported.
constexpr std::uint64_t kPauseDrainStallMs = 1500;

void InputThreadMain() {
    HotkeyRequest playback_keys[]{
        {&g_motion_key, &g_motion_request},
        {&g_keyframe_add_key, &g_keyframe_add_request},
        {&g_keyframe_play_key, &g_keyframe_play_request},
        {&g_keyframe_clear_key, &g_keyframe_clear_request},
        // Both default to 0: the camera path is saved and loaded from the panel,
        // and a binding only exists if a configuration assigns one.
        {&g_keyframe_save_key, &g_keyframe_save_request},
        {&g_keyframe_load_key, &g_keyframe_load_request},
        {&g_vmd_play_key, &g_vmd_play_request},
    };
    // The MMD keys carry no request flag of their own: the index below picks the
    // action, which keeps six bindings in one table instead of six globals.
    HotkeyRequest mmd_keys[]{
        {&g_mmd_play_key, nullptr},
        {&g_mmd_stop_key, nullptr},
        {&g_mmd_camera_mode_key, nullptr},
        {&g_mmd_seek_back_key, nullptr},
        {&g_mmd_seek_forward_key, nullptr},
        {&g_mmd_overlay_key, nullptr},
    };
#if defined(_WIN32)
    // The mouse hook has no Android representation; see the input loop below.
    HHOOK mouse_hook = nullptr;
#endif
    bool toggle_was_down = false;
    bool pause_was_down = false;
    bool first_person_was_down = false;
    while (!g_input_thread_stop.load(std::memory_order_acquire)) {
        const bool free_enabled = g_free_camera_enabled.load(std::memory_order_acquire) &&
            g_free_camera_contract_ready;
        const bool first_person_enabled = g_first_person_camera_enabled.load(std::memory_order_acquire) &&
            g_first_person_contract_ready;
        const int toggle_key = g_toggle_key.load(std::memory_order_relaxed);
        const int pause_key = g_pause_key.load(std::memory_order_relaxed);
        const int first_person_key = g_first_person_key.load(std::memory_order_relaxed);
        // The MMD keys must reach the director even when both cameras are off, so
        // the focus test admits the MMD feature too. Every other binding still ANDs
        // its own enable flag below, so widening this cannot fire them early.
        const bool mmd_enabled = g_mmd_enabled.load(std::memory_order_acquire);
        const bool focused = (free_enabled || first_person_enabled || mmd_enabled) &&
            GameWindowHasFocus();

        const bool toggle_down = focused && free_enabled && KeyDown(toggle_key);
        const bool pause_down = focused && free_enabled && KeyDown(pause_key);
        const bool first_person_key_down = KeyDown(first_person_key) ||
            (first_person_key == VK_OEM_MINUS && KeyDown(VK_SUBTRACT)) ||
            (first_person_key == VK_SUBTRACT && KeyDown(VK_OEM_MINUS));
        const bool first_person_down = focused && first_person_enabled && first_person_key_down;

        if (toggle_down && !toggle_was_down) {
            g_toggle_request.store(true, std::memory_order_release);
        }
        if (pause_down && !pause_was_down) {
            g_pause_request_ms.store(GetTickCount64(), std::memory_order_release);
            g_pause_stall_logged.store(false, std::memory_order_release);
            g_pause_request.store(true, std::memory_order_release);
        }
        if (first_person_down && !first_person_was_down) {
            g_first_person_toggle_request.store(true, std::memory_order_release);
        }
        toggle_was_down = toggle_down;
        pause_was_down = pause_down;
        first_person_was_down = first_person_down;
        for (HotkeyRequest& binding : playback_keys) {
            const bool down = focused && free_enabled &&
                KeyDown(binding.key->load(std::memory_order_relaxed));
            if (down && !binding.was_down) {
                binding.request->store(true, std::memory_order_release);
            }
            binding.was_down = down;
        }
        // MMD: the press edge is translated straight into the director's request
        // word, which the game thread drains in Mmd::Pump. Nothing here calls Unity.
        //
        // Surveyed off under Android on purpose. The panel drives MMD through the
        // runtime command pump ("mmd <verb>"), not by pressing keys, and the
        // module's own MMD defaults are the numpad block - NUMPAD4, NUMPAD5 and
        // NUMPAD6 - which are exactly the codes this app's panel presses for
        // "clear keyframe", "reset view" and "replay VMD". Polling both would make
        // one panel tap fire two unrelated actions, so on this platform the key
        // table is inert and the command pump is the only MMD input.
#if !defined(__ANDROID__)
        std::size_t mmd_index = 0;
        for (HotkeyRequest& binding : mmd_keys) {
            const bool down = focused && mmd_enabled &&
                KeyDown(binding.key->load(std::memory_order_relaxed));
            if (down && !binding.was_down) {
                switch (mmd_index) {
                case 0: g_mmd_requests.fetch_or(MmdRequestPlayPause, std::memory_order_acq_rel); break;
                case 1: g_mmd_requests.fetch_or(MmdRequestStop, std::memory_order_acq_rel); break;
                case 2: g_mmd_requests.fetch_or(MmdRequestCameraMode, std::memory_order_acq_rel); break;
                case 3: g_mmd_seek_steps.fetch_sub(1, std::memory_order_acq_rel); break;
                case 4: g_mmd_seek_steps.fetch_add(1, std::memory_order_acq_rel); break;
                default: g_mmd_overlay_toggle_request.store(true, std::memory_order_release); break;
                }
            }
            binding.was_down = down;
            ++mmd_index;
        }
#else
        // Keep the table's no-op reference so the array is not an unused local.
        (void)mmd_keys;
#endif
        // Windows only: keeps the companion overlay window in step with the game.
        // The body is empty under Android, which has no companion process.
        PumpMmdOverlayHost(mmd_enabled);

        // A pause request is only ever drained by a pump running on the game's
        // main thread. If every pump stopped - which is exactly what freezing the
        // world used to do - the request stays latched and the same hotkey can no
        // longer thaw it. Report that condition instead of leaving the panel
        // showing a key it sent and nothing else.
        const std::uint64_t pause_since = g_pause_request_ms.load(std::memory_order_acquire);
        if (pause_since != 0 &&
            g_pause_request.load(std::memory_order_acquire) &&
            GetTickCount64() - pause_since > kPauseDrainStallMs &&
            !g_pause_stall_logged.exchange(true, std::memory_order_acq_rel)) {
            Log("World pause request undrained for 1.5 s: no camera pump is running, "
                "so the world stays frozen until one does.");
        }

#if defined(_WIN32)
        // The low-level mouse hook only exists while the free camera runs in the
        // focused game window; its callbacks arrive through this thread's queue.
        const bool capture = focused && free_enabled &&
            g_mouse_look_enabled.load(std::memory_order_relaxed) &&
            g_free_camera_running.load(std::memory_order_acquire);
        g_mouse_capture.store(capture, std::memory_order_relaxed);
        if (capture && !mouse_hook) {
            mouse_hook = SetWindowsHookExW(WH_MOUSE_LL, &FreeCameraMouseHook,
                GetModuleHandleW(nullptr), 0);
        } else if (!capture && mouse_hook) {
            UnhookWindowsHookEx(mouse_hook);
            mouse_hook = nullptr;
        }
        // The hook belongs to this thread, so its callbacks are delivered through
        // this queue and pumping it is what makes mouse look work. The 5 ms
        // timeout doubles as the loop's throttle.
        MsgWaitForMultipleObjects(0, nullptr, FALSE, 5, QS_ALLINPUT);
        MSG message{};
        while (PeekMessageW(&message, nullptr, 0, 0, PM_REMOVE)) {
            TranslateMessage(&message);
            DispatchMessageW(&message);
        }
#else
        // There is no mouse to capture here. The in-game panel presses virtual
        // keys, which the latch above already polls, and nothing accumulates into
        // the mouse deltas, so mouse look contributes nothing to the free camera
        // and the rest of the controls are unchanged. The sleep keeps the
        // throttle that the message wait provided on Windows; without it this
        // thread would spin.
        Sleep(5);
#endif
    }
    g_mouse_capture.store(false, std::memory_order_relaxed);
#if defined(_WIN32)
    if (mouse_hook) {
        UnhookWindowsHookEx(mouse_hook);
    }
#endif
}

void ReleaseCameraRoot() {
    if (g_active_camera_root && g_host && g_host->gchandle_free) {
        g_host->gchandle_free(g_host->context, g_active_camera_root);
    }
    g_active_camera_root = 0;
    if (!g_free_camera_active && !g_first_person_active.load(std::memory_order_acquire)) {
        g_active_camera = nullptr;
    }
}

void RestoreWorldPause(const char* reason) {
    if (!g_changed_time_scale) {
        return;
    }
    const bool restored = SetValue(Contract("unity.time.scale.set"), nullptr,
        g_original_time_scale);
    g_changed_time_scale = false;
    Log(std::string("World time restored: ") + reason +
        (restored ? " (ok)" : " (failed)"));
}

void ExitFirstPerson(const char* reason, bool immediate=false);

// Implemented in free_camera_runtime.inc.
void ExitFreeCamera(const char* reason);
bool EnterFreeCamera();
void ApplyFreeCamera();
void PumpFreeCameraRequests();

void PollCameraFileResults();
void ApplyAndroidCameraValues();
bool MmdDirectorCameraActive();

void PumpFreeCameraControl() {
#if defined(__ANDROID__)
    ++g_android_pump_generation;
#endif
    PollCameraFileResults();
    ApplyAndroidCameraValues();
    const bool pause_allowed = g_pause_enabled.load(std::memory_order_acquire);
    const bool pause_pressed = g_pause_request.exchange(false, std::memory_order_acq_rel);
    if (!pause_allowed) RestoreWorldPause("pause feature disabled");
    else if (pause_pressed) {
        if (g_changed_time_scale) RestoreWorldPause("toggle hotkey");
        else if (GetValue(Contract("unity.time.scale.get"), nullptr, g_original_time_scale) &&
                 SetValue(Contract("unity.time.scale.set"), nullptr, 0.0f)) {
            g_changed_time_scale = true;
            Log("World time paused independently of camera mode.");
        }
    }
    const bool allowed = g_free_camera_enabled.load(std::memory_order_acquire) &&
        g_free_camera_contract_ready;

    if (!allowed) {
        g_keyframe_save_request.store(false,std::memory_order_release);
        g_keyframe_load_request.store(false,std::memory_order_release);
        g_motion_request.store(false,std::memory_order_release);
        g_keyframe_add_request.store(false,std::memory_order_release);
        g_keyframe_play_request.store(false,std::memory_order_release);
        g_keyframe_clear_request.store(false,std::memory_order_release);
        g_vmd_play_request.store(false,std::memory_order_release);
        g_toggle_request.store(false, std::memory_order_release);
        g_pause_request.store(false, std::memory_order_release);
        // The MMD director runs its VMD camera on this machinery even while the
        // free camera feature (hotkeys, controls) is off.
        const bool forced = g_force_exit_request.exchange(false, std::memory_order_acq_rel);
        if (!MmdDirectorCameraActive() && (g_free_camera_active || forced)) {
            ExitFreeCamera("feature disabled");
        }
        return;
    }

#if defined(__ANDROID__)
    if (!g_first_person_active.load(std::memory_order_acquire))
        betterendfieldnext::AndroidWarmHeadwearLayout();
#endif
    g_force_exit_request.store(false, std::memory_order_release);
    if (g_toggle_request.exchange(false, std::memory_order_acq_rel)) {
        if (g_free_camera_active) {
            ExitFreeCamera("toggle hotkey");
        } else {
            EnterFreeCamera();
        }
    }
    PumpFreeCameraRequests();
}

void PumpFreeCamera() {
    PumpFreeCameraControl();
    if (g_free_camera_active) {
        ApplyFreeCamera();
    }
}

// ---------------------------------------------------------------------------
// First-Person Camera
//
// The pose is applied at the very last stage of the Cinemachine pipeline, in
// CinemachineBrain::PushStateToUnityCamera(Cinemachine.CameraState&): the state
// that is about to be handed to the Unity camera is rewritten in place, so no
// later system can overwrite it. Head hiding uses the game's own first-person
// path (SnapshotCameraController) instead of touching meshes, which keeps the
// feature independent from renderer and vertex layout changes.
// ---------------------------------------------------------------------------

bool ReadBytes(const void* base, int32_t offset, void* destination, size_t size) {
    if (!base || offset < 0 || static_cast<size_t>(offset) > kMaxFieldOffset) {
        return false;
    }
    std::memcpy(destination, static_cast<const uint8_t*>(base) + offset, size);
    return true;
}

bool WriteBytes(void* base, int32_t offset, const void* source, size_t size) {
    if (!base || offset < 0 || static_cast<size_t>(offset) > kMaxFieldOffset) {
        return false;
    }
    std::memcpy(static_cast<uint8_t*>(base) + offset, source, size);
    return true;
}

// Normalizes one metadata field offset into a struct body relative offset. The
// arithmetic is intentionally repeated per nesting level: every value type
// carries its own boxed header, so CameraState.Lens needs one subtraction and
// LensSettings.FieldOfView needs another.
bool StructFieldOffset(std::string_view key, int32_t& offset) {
    const FieldContract* field = Field(key);
    if (!field || !field->ready || field->resolved.offset <= 0) {
        return false;
    }
    const int32_t normalized = field->resolved.offset - g_value_type_header;
    if (normalized < 0 || static_cast<size_t>(normalized) > kMaxFieldOffset) {
        return false;
    }
    offset = normalized;
    return true;
}

bool BuildCameraStateLayout() {
    CameraStateLayout layout;
    const bool complete =
        StructFieldOffset("cinemachine.state.raw_position", layout.raw_position) &&
        StructFieldOffset("cinemachine.state.raw_orientation", layout.raw_orientation) &&
        StructFieldOffset("cinemachine.state.position_correction", layout.position_correction) &&
        StructFieldOffset("cinemachine.state.orientation_correction", layout.orientation_correction) &&
        StructFieldOffset("cinemachine.state.lens", layout.lens) &&
        StructFieldOffset("cinemachine.lens.field_of_view", layout.lens_field_of_view) &&
        StructFieldOffset("cinemachine.lens.near_clip", layout.lens_near_clip) &&
        StructFieldOffset("cinemachine.lens.dutch", layout.lens_dutch);
    if (!complete) {
        Log("First person: CameraState layout could not be resolved on this build.");
        return false;
    }
    layout.ready = true;
    g_state_layout = layout;
    char buffer[256];
    std::snprintf(buffer, sizeof(buffer),
        "First person: CameraState layout resolved (header=0x%X rawPos=0x%X rawRot=0x%X "
        "lens=0x%X fov=0x%X nearClip=0x%X dutch=0x%X)",
        g_value_type_header, layout.raw_position, layout.raw_orientation,
        layout.lens, layout.lens_field_of_view, layout.lens_near_clip,
        layout.lens_dutch);
    Log(buffer);
    return true;
}

bool ReadBoolField(void* instance, std::string_view key, bool& value) {
    const FieldContract* field = Field(key);
    if (!instance || !field || !field->ready || field->resolved.offset <= 0) {
        return false;
    }
    value = *reinterpret_cast<const uint8_t*>(
        static_cast<const uint8_t*>(instance) + field->resolved.offset) != 0;
    return true;
}

void* FindSnapshotCameraController() {
    if (g_first_person.snapshot_controller) {
        if (IsObjectAlive(g_first_person.snapshot_controller)) {
            return g_first_person.snapshot_controller;
        }
        if (g_first_person.snapshot_handle && g_host && g_host->gchandle_free) {
            g_host->gchandle_free(g_host->context, g_first_person.snapshot_handle);
        }
        g_first_person.snapshot_handle = 0;
        g_first_person.snapshot_controller = nullptr;
    }
    if (!g_snapshot_controller_class.type_object) {
        return nullptr;
    }
    bool include_inactive = true;
    void* parameters[2]{g_snapshot_controller_class.type_object, &include_inactive};
    void* controller = Invoke(Contract("unity.object.find_object_of_type"),
        nullptr, parameters);
    if (!controller) {
        return nullptr;
    }
    g_first_person.snapshot_controller = controller;
    g_first_person.snapshot_handle = g_host && g_host->gchandle_new
        ? g_host->gchandle_new(g_host->context, controller, 0)
        : 0;
    Log("First person: SnapshotCameraController found (" + ObjectName(controller) + ").");
    return controller;
}

// ---------------------------------------------------------------------------
// First-person head hiding
//
// The reference implementation hides the head, the hair and the head
// accessories, while the body, the arms, the equipment and the shadows of the
// character stay untouched. The game exposes exactly that granularity:
// BaseModelComponent.SetVisibleByNameContainsStr keeps its own hidden-name set
// and re-applies it whenever parts or LOD levels change. Driving the feature by
// part names instead of mesh buffers keeps it free of vertex, index and field
// layout assumptions, so it keeps working across game updates.
//
// Matched parts the mesh patch cannot process fall back to shadow-only
// rendering (Renderer.shadowCastingMode = ShadowsOnly): not drawn in cameras,
// shadows kept, no mesh assumptions. This covers plain MeshRenderers, skinned
// renderers whose patch attempts are exhausted, and builds without GPU
// readback. The original shadow-casting mode is restored on exit.
// ---------------------------------------------------------------------------

constexpr const char* kHeadPartTokens[]{
    "head", "face", "hair", "brow", "eyelid", "eyes", "iris", "mouth", "horn"};

constexpr int kMaxPartScanDepth = 6;
constexpr int kMaxPartScanNodes = 256;
constexpr int kMaxPartLogEntries = 32;

std::string LowerAscii(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(),
        [](unsigned char character) {
            return static_cast<char>(std::tolower(character));
        });
    return value;
}

bool MatchesHeadPartToken(const std::string& name) {
    if (name.empty()) {
        return false;
    }
    const std::string lower = LowerAscii(name);
    for (const char* token : kHeadPartTokens) {
        if (lower.find(token) != std::string::npos) {
            return true;
        }
    }
    return false;
}

void ReadMeshProbe(void* mesh, HeadPartProbe& probe) {
    if (!mesh) {
        return;
    }
    probe.mesh_name = ObjectName(mesh);
    const MethodContract* vertex_count = Contract("unity.mesh.vertex_count.get");
    int count = 0;
    if (vertex_count && vertex_count->resolved &&
        GetValue(vertex_count, mesh, count)) {
        probe.vertex_count = count;
    }
}

// Probe skinned meshes first so the existing mesh path keeps its data. Other
// renderer subtypes may also carry head ornaments and use shadow-only hiding.
void ReadPartComponents(void* game_object, HeadPartProbe& probe) {
    const MethodContract* get_component = Contract("unity.game_object.get_component");
    if (!game_object || !get_component || !get_component->resolved) {
        return;
    }
    if (g_skinned_mesh_renderer_class.type_object) {
        void* parameters[1]{g_skinned_mesh_renderer_class.type_object};
        void* renderer = Invoke(get_component, game_object, parameters);
        if (renderer) {
            probe.skinned = true;
            probe.renderer = renderer;
            ReadMeshProbe(Invoke(
                Contract("unity.skinned_mesh_renderer.shared_mesh.get"), renderer, nullptr),
                probe);
            return;
        }
    }
    // The shadow-only fallback only needs a Renderer reference.
    if (g_mesh_renderer_class.type_object) {
        void* parameters[1]{g_mesh_renderer_class.type_object};
        probe.renderer = Invoke(get_component, game_object, parameters);
    }
    // A head ornament can also be a particle, trail or other Renderer subtype.
    if (!probe.renderer && g_renderer_class.type_object) {
        void* parameters[1]{g_renderer_class.type_object};
        probe.renderer = Invoke(get_component, game_object, parameters);
    }
}

void ScanPartNodes(void* transform, int depth, int& visited,
    std::vector<HeadPartProbe>& parts, std::string& tree, int& logged) {
    if (!transform || depth > kMaxPartScanDepth || visited >= kMaxPartScanNodes) {
        return;
    }
    ++visited;
    HeadPartProbe probe;
    probe.object_name = ObjectName(transform);
    ReadPartComponents(
        Invoke(Contract("unity.component.game_object"), transform, nullptr), probe);
    probe.matched = MatchesHeadPartToken(probe.object_name) ||
        MatchesHeadPartToken(probe.mesh_name);
    const std::string role = LowerAscii(probe.object_name);
    probe.dedicated_head = FpMesh::IsDedicatedHeadMesh(role);
    probe.body_skin = FpMesh::IsBodyMesh(role);

    if (probe.renderer || probe.matched) {
        if (logged < kMaxPartLogEntries) {
            ++logged;
            std::string line(static_cast<size_t>(depth) * 2, ' ');
            line += probe.object_name;
            if (!probe.mesh_name.empty()) {
                line += " [mesh='" + probe.mesh_name + "'";
                if (probe.vertex_count >= 0) {
                    line += ", " + std::to_string(probe.vertex_count) + " vertices";
                }
                line += "]";
            }
            if (probe.matched) {
                line += " <-- head part";
            }
            tree += "\n  " + line;
        } else if (logged == kMaxPartLogEntries) {
            ++logged;
            tree += "\n  ...";
        }
    }
    parts.push_back(probe);

    const MethodContract* child_count = Contract("unity.transform.child_count.get");
    const MethodContract* get_child = Contract("unity.transform.get_child");
    int children = 0;
    if (!child_count || !get_child || !GetValue(child_count, transform, children)) {
        return;
    }
    for (int index = 0; index < children && visited < kMaxPartScanNodes; ++index) {
        void* parameters[1]{&index};
        ScanPartNodes(Invoke(get_child, transform, parameters), depth + 1, visited,
            parts, tree, logged);
    }
}

void* FindModelComponent() {
    void* entity = Invoke(Contract("player_controller.get_main_character"),
        nullptr, nullptr);
    return entity ? Invoke(Contract("entity.get_model_com"), entity, nullptr)
                  : nullptr;
}

void* FindModelObject() {
    void* model_component = FindModelComponent();
    return model_component
        ? Invoke(Contract("base_model_component.get_model_go"), model_component,
            nullptr)
        : nullptr;
}

void* FindModelTransform() {
    void* model_object = FindModelObject();
    return model_object
        ? Invoke(Contract("unity.game_object.transform"), model_object, nullptr)
        : nullptr;
}

#include "first_person_runtime.inc"
#include "free_camera_runtime.inc"
#include "character_motion_runtime.inc"
#include "mmd_director_runtime.inc"

void ReleaseHeadTransform() {
#if defined(__ANDROID__)
    g_first_person.model_id.clear();
#endif
    if (g_first_person.head_handle && g_host && g_host->gchandle_free) {
        g_host->gchandle_free(g_host->context, g_first_person.head_handle);
    }
    g_first_person.head_handle = 0;
    g_first_person.head = nullptr;
    if (g_first_person.neck_handle && g_host && g_host->gchandle_free) {
        g_host->gchandle_free(g_host->context, g_first_person.neck_handle);
    }
    g_first_person.neck_handle = 0;
    g_first_person.neck = nullptr;
    g_first_person.last_eye_valid = false;
    g_first_person_head_logged = false;
}

// The neck bone defines the search frame for the real opening. Missing neck
// metadata disables cap detection; head hiding can still use the head palette.
void BindNeckBone(void* head) {
    if (g_first_person.neck) {
        return;
    }
    void* parent = head
        ? Invoke(Contract("unity.transform.parent.get"), head, nullptr)
        : nullptr;
    if (!parent) {
        return;
    }
    std::string name = LowerAscii(ObjectName(parent));
    if (name.find("neck") == std::string::npos) {
        return;
    }
    g_first_person.neck = parent;
    g_first_person.neck_handle = g_host && g_host->gchandle_new
        ? g_host->gchandle_new(g_host->context, parent, 0)
        : 0;
}

bool LooksLikeHeadBone(void* transform) {
    const std::string name = ObjectName(transform);
    std::string lower = name;
    std::transform(lower.begin(), lower.end(), lower.begin(),
        [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return lower.find("head") != std::string::npos;
}

void* FindHeadBoneRecursive(void* current, int depth, int& visited) {
    if (!current || depth > 12 || visited >= kMaxPartScanNodes) {
        return nullptr;
    }
    ++visited;
    if (LooksLikeHeadBone(current)) {
        return current;
    }
    int child_count = 0;
    if (!GetValue(Contract("unity.transform.child_count.get"), current, child_count)) {
        return nullptr;
    }
    for (int index = 0; index < child_count && visited < kMaxPartScanNodes; ++index) {
        void* parameters[1]{&index};
        void* child = Invoke(Contract("unity.transform.get_child"), current, parameters);
        if (child) {
            if (void* found = FindHeadBoneRecursive(child, depth + 1, visited)) {
                return found;
            }
        }
    }
    return nullptr;
}

void* TryHumanoidHeadBone(void* model_root) {
    if (!model_root || !g_animator_class.type_object) {
        return nullptr;
    }
    void* game_object = Invoke(Contract("unity.component.game_object"), model_root, nullptr);
    if (!game_object) {
        return nullptr;
    }
    void* find_parameters[1]{g_animator_class.type_object};
    void* animator = Invoke(Contract("unity.game_object.get_component"),
        game_object, find_parameters);
    if (!animator) {
        return nullptr;
    }
    int32_t bone = kHumanBodyBonesHead;
    void* parameters[1]{&bone};
    void* head = Invoke(Contract("unity.animator.get_bone_transform"), animator, parameters);
    // The humanoid enum value is verified by name instead of being trusted, so a
    // shifted enum on another game build degrades into the name search below.
    return head && LooksLikeHeadBone(head) ? head : nullptr;
}

void* ResolvePlayerHeadTransform() {
    void* model_root = nullptr;
    void* entity = Invoke(Contract("player_controller.get_main_character"), nullptr, nullptr);
    if (entity) {
        void* model_component = Invoke(Contract("entity.get_model_com"), entity, nullptr);
        void* model_object = model_component
            ? Invoke(Contract("base_model_component.get_model_go"), model_component, nullptr)
            : nullptr;
        if (model_object) {
            model_root = Invoke(Contract("unity.game_object.transform"),
                model_object, nullptr);
        }
    }
    if (!model_root) {
        void* player_tag = g_host->string_new(g_host->context, "Player");
        void* parameters[1]{player_tag};
        void* player = Invoke(Contract("unity.game_object.find_with_tag"), nullptr, parameters);
        if (player) {
            model_root = Invoke(Contract("unity.game_object.transform"),
                player, nullptr);
        }
    }
    if (!model_root) {
        return nullptr;
    }

    if (void* head = TryHumanoidHeadBone(model_root)) {
        return head;
    }
    void* head_path = g_host->string_new(g_host->context,
        "Root/Bip001/Bip001_Pelvis/Bip001_Spine/Bip001_Spine1/Bip001_Spine2/Bip001_Neck/Bip001_Head");
    void* find_parameters[1]{head_path};
    if (void* head = Invoke(Contract("unity.transform.find"), model_root, find_parameters)) {
        return head;
    }
    int visited = 0;
    if (void* head = FindHeadBoneRecursive(model_root, 0, visited)) {
        return head;
    }
    // Last resort: anchor to the model root so the view still follows the body.
    return model_root;
}

void TryBindHeadTransform() {
    if (g_first_person.head) {
        return;
    }
    void* head = ResolvePlayerHeadTransform();
    if (!head) {
        if (!g_first_person_head_logged) {
            g_first_person_head_logged = true;
            Log("First person: the player model is not loaded yet; retrying.");
        }
        return;
    }
    g_first_person.head = head;
    g_first_person.head_handle = g_host && g_host->gchandle_new
        ? g_host->gchandle_new(g_host->context, head, 0)
        : 0;
    BindNeckBone(head);
    Log("First person: head anchor bound to \"" + ObjectName(head) + "\".");
}

#include "first_person_facing_runtime.inc"
#include "first_person_motion_runtime.inc"
#include "first_person_dialogue_runtime.inc"
#include "first_person_combat_runtime.inc"
#include "first_person_retract_runtime.inc"
#include "first_person_scale_runtime.inc"

void FpRefreshHiddenParts(bool hide, bool allow_mesh_work = true) {
    if (!hide || !g_first_person_hide_head.load(std::memory_order_relaxed)) {
        if (g_first_person.head_hide_applied || !g_fp_mesh_session.patches.empty() || !g_fp_shadow_hides.empty())
            ReleaseHeadPartHide();
        g_fp_external_scale.Release();
        return;
    }
    if (g_first_person_external_head_scale.load(std::memory_order_relaxed)) {
        if (g_first_person.head_hide_applied || !g_fp_mesh_session.patches.empty() || !g_fp_shadow_hides.empty())
            ReleaseHeadPartHide();
        // Failed restoration still owns its renderer: do not mix mesh and
        // skeleton-scale strategies in the same session.
        if (!g_first_person_active.load(std::memory_order_relaxed) && g_fp_external_scale.applied) return;
        if (g_fp_mesh_session.patches.empty() && g_fp_shadow_hides.empty()) g_fp_external_scale.Apply();
        return;
    }
    if (!g_fp_external_scale.Release()) return;
    if (!g_first_person.head_hide_applied) {
        ExitGameFirstPersonMode();
        ApplyHeadPartHide();
        FpHideDedicatedHeadParts();
    }
    if (allow_mesh_work) EnsureNeckCap();
}

// Rewrites the CameraState that Cinemachine is about to push to the Unity
// camera. Android uses the final game orientation; Windows retains its existing
// raw-orientation calculation and correction behavior.
void ApplyFirstPersonState(void* state) {
    if (!g_state_layout.ready || !IsObjectAlive(g_first_person.head)) {
        FpFacingUpdate(false, {}, 60);
        FpMotionReset();
        return;
    }

#if defined(__ANDROID__)
    Quaternion orientation{}, game_correction{};
    float dutch=0;
    if (!ReadBytes(state, g_state_layout.raw_orientation, &orientation,sizeof(orientation)) ||
        !ReadBytes(state, g_state_layout.orientation_correction,&game_correction,sizeof(game_correction)) ||
        !ReadBytes(state,g_state_layout.lens+g_state_layout.lens_dutch,&dutch,sizeof(dutch)) ||
        !FpMath::Unit({orientation.x,orientation.y,orientation.z,orientation.w}) ||
        !FpMath::Unit({game_correction.x,game_correction.y,game_correction.z,game_correction.w}) ||
        !std::isfinite(dutch)) {
        FpFacingUpdate(false, {}, 60);
        FpMotionReset();
        return;
    }
    const FpMath::Quat control{orientation.x,orientation.y,orientation.z,orientation.w};
    const FpMath::Quat correction{game_correction.x,game_correction.y,game_correction.z,game_correction.w};
    const auto roll=FpMath::AxisAngle({0,0,1},dutch);
    FpMath::Quat basis=control*correction*roll;
    FpMath::Quat applied_correction=correction;
    if (g_first_person_extend_look_range.load(std::memory_order_relaxed)) {
        const float extra_pitch=FpMath::ExpandLookPitch(basis,
            kFirstPersonLookUpRange,kFirstPersonLookDownRange);
        if (extra_pitch!=0.f) {
            applied_correction=correction*FpMath::AxisAngle({1,0,0},extra_pitch);
            basis=control*applied_correction*roll;
        }
    }
    FpFacingUpdate(!g_free_camera_active && g_first_person_movement.load(std::memory_order_relaxed),
        FpMath::Rotate(basis,{0,0,1}),g_first_person_side_look_limit.load(std::memory_order_relaxed));
    // Facing may rotate the actor and therefore move the head in this push.
    Vector3 head{};
    if (!GetValue(Contract("unity.transform.position.get"),
            g_first_person.head, head) || !IsFinite(head)) {
        FpFacingUpdate(false, {}, 60);
        FpMotionReset();
        return;
    }
#else
    Quaternion orientation{};
    if (!ReadBytes(state, g_state_layout.raw_orientation, &orientation,
            sizeof(orientation)) || !FpMath::Unit({orientation.x,orientation.y,orientation.z,orientation.w})) {
        FpFacingUpdate(false, {}, 60);
        FpMotionReset();
        return;
    }
    Vector3 head{};
    if (!GetValue(Contract("unity.transform.position.get"),
            g_first_person.head, head) || !IsFinite(head)) {
        FpFacingUpdate(false, {}, 60);
        FpMotionReset();
        return;
    }
#endif

    // Eye anchor, ported from the upstream enhancer. The forward offset is
    // applied along the horizontal projection of the view direction, so looking
    // down moves the eye towards the face without dragging it downwards; the
    // height offset stays world-vertical.
#if defined(__ANDROID__)
    const auto final_forward=FpMath::Rotate(basis,{0,0,1});
    const Vector3 forward{final_forward.x,final_forward.y,final_forward.z};
#else
    const Vector3 forward = RotateVector(orientation, Vector3{0.0f, 0.0f, 1.0f});
#endif
    const float planar_length =
        std::sqrt(forward.x * forward.x + forward.z * forward.z);
    const Vector3 planar = planar_length > 0.001f
        ? Vector3{forward.x / planar_length, 0.0f, forward.z / planar_length}
        : Vector3{0.0f, 0.0f, 1.0f};
    const float eye_height = g_first_person_eye_height.load(std::memory_order_relaxed);
    const float eye_forward = g_first_person_eye_forward.load(std::memory_order_relaxed);
    const Vector3 eye = Add(head, Add(Vector3{0.0f, eye_height, 0.0f},
        Scale(planar, eye_forward)));

#if defined(__ANDROID__)
    // Preserve the game correction unless Android's optional animation replaces
    // it with a measured final world orientation.
    const int animation_mode = g_first_person_animation_mode.load(std::memory_order_relaxed);
    if (animation_mode != 0 && FpFacingAnimationAllowed()) {
        const auto animated = FpMotionApply(basis,animation_mode,
            g_first_person_animation_strength.load(std::memory_order_relaxed));
        const float norm=control.x*control.x+control.y*control.y+control.z*control.z+control.w*control.w;
        const auto inverse_roll=FpMath::Quat{-roll.x,-roll.y,-roll.z,roll.w};
        applied_correction=FpMath::Quat{-control.x/norm,-control.y/norm,-control.z/norm,control.w/norm} * animated * inverse_roll;
    } else FpMotionReset();
    Quaternion rotation_correction{applied_correction.x,applied_correction.y,
        applied_correction.z,applied_correction.w};
#else
    // Preserve the existing Windows first-person pose behavior.
    Quaternion rotation_correction{0.0f, 0.0f, 0.0f, 1.0f};
    if (g_first_person_extend_look_range.load(std::memory_order_relaxed)) {
        const FpMath::Quat view{orientation.x, orientation.y, orientation.z,
            orientation.w};
        const float extra_pitch = FpMath::ExpandLookPitch(view,
            kFirstPersonLookUpRange, kFirstPersonLookDownRange);
        if (extra_pitch != 0.0f) {
            const float half = extra_pitch * 0.008726646259971648f;
            rotation_correction = Quaternion{std::sin(half), 0.0f, 0.0f,
                std::cos(half)};
        }
    }
    const FpMath::Quat control{orientation.x,orientation.y,orientation.z,orientation.w};
    const FpMath::Quat pitch{rotation_correction.x,rotation_correction.y,rotation_correction.z,rotation_correction.w};
    const auto view = control * pitch;
    FpFacingUpdate(!g_free_camera_active && g_first_person_movement.load(std::memory_order_relaxed),
        FpMath::Rotate(view,{0,0,1}),g_first_person_side_look_limit.load(std::memory_order_relaxed));
    const int animation_mode = g_first_person_animation_mode.load(std::memory_order_relaxed);
    if (animation_mode != 0 && FpFacingAnimationAllowed()) {
        const auto animated = FpMotionApply(view,animation_mode,
            g_first_person_animation_strength.load(std::memory_order_relaxed));
        const float norm=control.x*control.x+control.y*control.y+control.z*control.z+control.w*control.w;
        const auto correction = FpMath::Quat{-control.x/norm,-control.y/norm,-control.z/norm,control.w/norm} * animated;
        rotation_correction = {correction.x,correction.y,correction.z,correction.w};
    } else FpMotionReset();
#endif

    // Apply the user's vertical boundary to the final visible orientation,
    // after range expansion and animation. Keep the game's look state intact.
    const float up_limit = g_first_person_look_up_limit.load(std::memory_order_relaxed);
    const float down_limit = g_first_person_look_down_limit.load(std::memory_order_relaxed);
    if (up_limit < 89.0f || down_limit < 89.0f) {
        const FpMath::Quat current_correction{rotation_correction.x,
            rotation_correction.y,rotation_correction.z,rotation_correction.w};
        const auto limited = FpMath::LimitViewPitch(control * current_correction,
            up_limit,down_limit);
        const float norm=control.x*control.x+control.y*control.y+control.z*control.z+control.w*control.w;
        const auto final_correction = FpMath::Quat{-control.x/norm,-control.y/norm,-control.z/norm,control.w/norm} * limited;
        rotation_correction = {final_correction.x,final_correction.y,
            final_correction.z,final_correction.w};
    }

    const float field_of_view = g_first_person_fov.load(std::memory_order_relaxed);
    const float near_clip = g_first_person_near_clip.load(std::memory_order_relaxed);
    const Vector3 no_correction{};
    const int32_t lens = g_state_layout.lens;

    WriteBytes(state, g_state_layout.raw_position, &eye, sizeof(eye));
    WriteBytes(state, g_state_layout.position_correction, &no_correction,
        sizeof(no_correction));
    WriteBytes(state, g_state_layout.orientation_correction,
        &rotation_correction, sizeof(rotation_correction));
    WriteBytes(state, lens + g_state_layout.lens_field_of_view, &field_of_view,
        sizeof(field_of_view));
    WriteBytes(state, lens + g_state_layout.lens_near_clip, &near_clip,
        sizeof(near_clip));

    g_first_person.last_eye = eye;
    g_first_person.last_eye_valid = true;
    g_push_state_patches.fetch_add(1, std::memory_order_relaxed);
}

#include "first_person_coordinator.inc"

// Confirms that the patched state actually reached the Unity camera. A large
// distance means the offsets or the hook are wrong on this game build, which is
// reported instead of silently producing a broken view.
void FirstPersonHealthCheck() {
    if (!g_first_person.last_eye_valid) {
        return;
    }
    void* camera = Invoke(Contract("unity.camera.main"), nullptr, nullptr);
    void* transform = camera
        ? Invoke(Contract("unity.component.transform"), camera, nullptr)
        : nullptr;
    Vector3 camera_position{};
    if (!transform ||
        !GetValue(Contract("unity.transform.position.get"), transform, camera_position)) {
        return;
    }
    const float distance = Magnitude(Subtract(camera_position, g_first_person.last_eye));
    const uint64_t patches = g_push_state_patches.load(std::memory_order_relaxed);
    const uint64_t calls = g_push_state_calls.load(std::memory_order_relaxed);
    if (g_diagnostics_enabled.load(std::memory_order_relaxed)) {
        char buffer[256];
        std::snprintf(buffer, sizeof(buffer),
            "First person pose check: eyeDistance=%.2f (pushCalls=%llu patches=%llu)",
            distance, static_cast<unsigned long long>(calls),
            static_cast<unsigned long long>(patches));
        Log(buffer);
    }
    if (calls == 0) {
        if (!g_first_person_health_warned) {
            g_first_person_health_warned = true;
            Log("First person: the CameraState hook is installed but never called on "
                "this build; the camera may be driven by a different pipeline.");
        }
    } else if (distance > 1.5f && !g_first_person_health_warned) {
        g_first_person_health_warned = true;
        Log("First person: the Unity camera is not following the pushed state; the "
            "CameraState offsets are likely wrong on this build.");
    }
}

bool EnterFirstPerson() {
    if (!g_state_layout.ready || !g_push_state_hook_ready) {
        Log("First person camera is unavailable: the Cinemachine CameraState hook "
            "is not active on this build.");
        return false;
    }
    if (g_free_camera_active) {
        ExitFreeCamera("switching to first person");
    }
    void* camera = Invoke(Contract("unity.camera.main"), nullptr, nullptr);
    if (!camera) {
        Log("First person camera could not capture the active Unity camera.");
        return false;
    }
    g_active_camera = camera;
    g_active_camera_root = g_host && g_host->gchandle_new
        ? g_host->gchandle_new(g_host->context, camera, 0)
        : 0;
    float original_fov = 0.0f;
    GetValue(Contract("unity.camera.fov.get"), camera, original_fov);

    g_first_person.character = nullptr;
    g_first_person_head_logged = false;
    g_first_person_pump_frames = 0;
    g_fp_perspective.visual_exit_deadline=0;
    TryBindHeadTransform();

    g_first_person.last_eye_valid = false;
    g_first_person_reassert_frames = 0;
    g_first_person_health_warned = false;
    // The look accumulator is shared with the free camera, and while the first
    // person is down the gyroscope keeps folding deltas into it (the relay drains
    // on every tick regardless of which camera is armed). EnterFreeCamera clears
    // it for the same reason; without the same here, the first active frame would
    // dump every delta that piled up while the first person was off as one jump -
    // exactly the "camera throws itself sideways the moment first person starts"
    // seen on device.
    ClearMouseInput();
    g_first_person_active.store(true, std::memory_order_release);
    char buffer[256];
    std::snprintf(buffer, sizeof(buffer),
        "First person camera enabled (hotkey=0x%X, fov=%.1f, sourceFov=%.1f, "
        "eyeForward=%.2f, eyeHeight=%.2f, nearClip=%.3f, extendLookRange=%s).",
        g_first_person_key.load(std::memory_order_relaxed),
        g_first_person_fov.load(std::memory_order_relaxed), original_fov,
        g_first_person_eye_forward.load(std::memory_order_relaxed),
        g_first_person_eye_height.load(std::memory_order_relaxed),
        g_first_person_near_clip.load(std::memory_order_relaxed),
        g_first_person_extend_look_range.load(std::memory_order_relaxed)
            ? "on" : "off");
    Log(buffer);
    return true;
}

void ExitFirstPerson(const char* reason, bool immediate) {
    FpFacingRelease();
    FpMotionReset();
    if (!g_first_person_active.load(std::memory_order_acquire)) {
        return;
    }
    g_first_person_active.store(false, std::memory_order_release);
    if (!immediate && g_fp_perspective.transition.initialized && g_fp_perspective.transition.first_person &&
        g_first_person_transition_seconds.load(std::memory_order_relaxed)>0) {
        g_fp_perspective.visual_exit_deadline=GetTickCount64()+1100;
    } else { FpRefreshHiddenParts(false); FpResetPerspective(); }
    if (g_first_person.snapshot_handle && g_host && g_host->gchandle_free) {
        g_host->gchandle_free(g_host->context, g_first_person.snapshot_handle);
    }
    g_first_person.snapshot_handle = 0;
    g_first_person.snapshot_controller = nullptr;
    ReleaseHeadTransform();
    g_first_person.character = nullptr;
    const uint64_t patches = g_push_state_patches.load(std::memory_order_relaxed);
    ReleaseCameraRoot();
    Log(std::string("First person camera disabled: ") + reason +
        " (patched frames=" + std::to_string(patches) + ").");
}

// Follows leader switches and respawns: the head anchor and the game's own
// first-person state belong to a character, so both are rebound.
bool RefreshFirstPersonTarget() {
    bool carry_hidden = false;
    void* character = Invoke(Contract("player_controller.get_main_character"),
        nullptr, nullptr);
    if (character && character != g_first_person.character) {
        if (g_first_person.character) {
            carry_hidden = g_fp_perspective.applied || g_fp_perspective.transition.blending;
            FpFacingRelease();
            FpMotionReset();
            g_fp_external_scale.Release(true);
            FpResetPerspective();
            Log("First person: main character changed; rebinding the eye anchor.");
            ReleaseHeadTransform();
            ReleaseHeadPartHide();
            ReleaseNeckCap();
        }
        g_first_person.character = character;
    }
    if (g_first_person.head && !IsObjectAlive(g_first_person.head)) {
        Log("First person: the eye anchor was destroyed; rebinding.");
        ReleaseHeadTransform();
    }
    TryBindHeadTransform();
    return carry_hidden;
}

void PumpFirstPerson() {
    // A failed exit restore keeps the scale lease alive. Retry only after the
    // visual transition has released the head; Release owns the retry budget.
    if (!g_first_person_active.load(std::memory_order_acquire) &&
        !g_fp_perspective.visual_exit_deadline && !g_fp_perspective.transition.blending) {
#if defined(__ANDROID__)
        betterendfieldnext::AndroidRestoreHeadwearFixture();
#endif
        if (g_fp_external_scale.lease.owned) g_fp_external_scale.Release();
        if (std::any_of(g_fp_mesh_session.patches.begin(),g_fp_mesh_session.patches.end(),
                [](const FpPatch& patch){return patch.restore_failures<3;})) ReleaseNeckCap();
    }
    if (g_fp_perspective.visual_exit_deadline && GetTickCount64()>=g_fp_perspective.visual_exit_deadline) {
        FpRefreshHiddenParts(false); FpResetPerspective();
    }
    const bool allowed = g_first_person_camera_enabled.load(std::memory_order_acquire) &&
        g_first_person_contract_ready;
    if (!allowed) {
        g_first_person_toggle_request.store(false, std::memory_order_release);
        if (g_first_person_active.load(std::memory_order_acquire) ||
            g_first_person_exit_request.exchange(false, std::memory_order_acq_rel)) {
            ExitFirstPerson("feature disabled",true);
        }
        return;
    }
    g_first_person_exit_request.store(false, std::memory_order_release);
    if (g_first_person_toggle_request.exchange(false, std::memory_order_acq_rel)) {
        if (g_first_person_active.load(std::memory_order_acquire)) {
            ExitFirstPerson("toggle hotkey");
        } else {
            EnterFirstPerson();
        }
    }
    if (!g_first_person_active.load(std::memory_order_acquire)) {
        if (!g_fp_shadow_hides.empty()) FpReleaseShadowHides();
        return;
    }
    const bool carry_hidden = RefreshFirstPersonTarget();
    FpRefreshHiddenParts(g_fp_perspective.applied || g_fp_perspective.transition.blending || carry_hidden);
    if (++g_first_person_pump_frames % 30 == 0) {
        FirstPersonHealthCheck();
    }
}

// Pumping the queued control requests and the free camera pose means calling
// managed methods, so it cannot happen on the module's own input thread: it has
// to ride a callback the engine or the game makes on the main thread. The game's
// camera code is not one of those while Time.timeScale is 0 - that is the state
// the world pause puts it in, and it is why the world could be frozen and then
// never thawed. Every main thread tick therefore feeds the same pump, and the
// re-entrancy guard stops managed calls made from inside it from re-entering.
thread_local bool t_in_engine_tick = false;

#if !defined(_WIN32)
// A device has no cursor for the low-level mouse hook to watch, so the panel's
// look pad is what moves the free camera's aim: it sends screen-space drag
// deltas through the input relay, the compat layer accumulates them, and they
// are folded into the same counters the hook fills on Windows. Everything
// downstream - sensitivity, inversion, the pitch clamp, the behavior during a
// playback - is therefore the shared desktop code, unchanged.
//
// Drained on every tick rather than only while the free camera is armed, so a
// drag made with the camera off cannot be handed over as one jump when it comes
// up. EnterFreeCamera clears the result anyway, which is what makes the arming
// moment clean even for the deltas that arrive between the last tick and it.
void FoldPanelLookInput() {
    int dx = 0;
    int dy = 0;
    betterendfieldnext::TakeAndroidLook(dx, dy);
    g_mouse_dx.fetch_add(dx, std::memory_order_relaxed);
    g_mouse_dy.fetch_add(dy, std::memory_order_relaxed);
}

// The gyroscope drives the module's own first-person rotation.
//
// It never originates here: the phone's gyroscope is folded into look deltas by
// the settings app and arrives over the same relay channel as a finger drag, so
// by the time these deltas exist nothing distinguishes "gyroscope" from "touch".
// That is by design - it is what lets the gyroscope steer a camera this module
// does not own the orientation of. The consequence, however, is that the deltas
// have exactly one consumer below: the free camera's target, which StepFreeCamera
// reads only while the free camera is armed. So today a gyroscope with the free
// camera off has nowhere to go.
//
// This adds the missing consumer. It does not reroute or reinterpret the free
// camera path - that path is left exactly as it was, and the two are mutually
// exclusive because they would otherwise both drain the same accumulator and
// would each steal half of the other's motion.
//
// The injection point is CameraManager::OnInput(float, float) - the game's own
// main-camera look entry. Two earlier candidates were tried and both proved
// wrong on device:
//   * SnapshotCameraController.RotateCamera* - the snapshot controller is a
//     photography mode. It only exists while ActivateSnapshotCamera() has run,
//     so during ordinary play (including the module's first person) there is no
//     instance to call and the deltas were silently dropped.
//   * snapshot.is_first_person - the game's native first-person flag. It is the
//     right flag for the game's *own* first person, but the module's first
//     person does not set it; it rewrites the pushed Cinemachine CameraState and
//     keeps the game's orientation authoritative. Gating on that flag therefore
//     shut the door during the module's first person too.
//
// CameraManager is the object that owns that authoritative orientation: the
// module already hooks its TailLateTick, so the instance is captured there and
// held in g_first_person.camera_manager. Feeding OnInput the gyroscope's deltas
// is exactly what the player's own touch does, so body follow, pitch clamping
// and camera blend keep working and touch adds to the gyroscope instead of being
// overwritten.
void ApplyFirstPersonLook() {
    if (!g_first_person_gyro_look.load(std::memory_order_acquire) ||
        !g_first_person_gyro_contract_ready) {
        return;
    }
    // The free camera takes precedence and is asked first, exactly as the pump
    // orders these two consumers: while it is armed the accumulator is its and
    // this must not touch it.
    if (g_free_camera_active) {
        return;
    }
    // The module's own first person is the camera this gyroscope exists for. It
    // is entered with the module's first-person hotkey, and while it is active
    // the game's main camera orientation is authoritative (the module only moves
    // the position). The deltas therefore go to the game's own look input, not
    // to the snapshot camera - the snapshot controller is a photography mode and
    // is not even present during ordinary play, which is why the earlier
    // RotateCamera* approach found no instance to call.
    const bool module_first_person = g_first_person_active.load(std::memory_order_acquire);
    if (!module_first_person) {
        // No camera claims the deltas, so they stay put for the free camera to
        // find when it arms.
        return;
    }
    const int dx = g_mouse_dx.exchange(0, std::memory_order_relaxed);
    const int dy = g_mouse_dy.exchange(0, std::memory_order_relaxed);
    if (dx == 0 && dy == 0) {
        return;
    }
    const MethodContract* on_input = Contract("camera_manager.on_input");
    if (!on_input || !on_input->resolved) {
        return;
    }
    void* manager = g_first_person.camera_manager;
    if (!manager || !IsObjectAlive(manager)) {
        return;
    }
    // The relay deltas are screen-space pixel deltas produced by the app-side
    // GyroscopeController (sensor rate scaled into pixels, per-axis inversion
    // already applied there). CameraManager::OnInput does not consume pixels,
    // though: its two floats are the same screen-percentage deltas a finger
    // drag produces (the free-look controller names them deltaScreenPercentageX
    // / deltaScreenPercentageY), and its per-axis DragSpeed + acceleration
    // config then turns that percentage into rotation. Feeding raw pixels was
    // therefore two wrongs at once - the unit (pixel vs percentage) and the
    // scale (a slow turn quantized to +-1 pixel lands on the controller's
    // speedMinThreshold and is snap-quantized into the "turns in fixed steps
    // like a d-pad" seen on device). The pixels are divided by the live Unity
    // render resolution here so a drag of the full screen width is one full
    // unit of input, matching what the touch path hands the same method. The
    // only remaining transform is the sign the game's look convention expects:
    // positive dy (drag down) maps to negative pitch (look up).
    int screen_w = 0;
    int screen_h = 0;
    const MethodContract* width_get = Contract("unity.screen.width.get");
    const MethodContract* height_get = Contract("unity.screen.height.get");
    if (width_get && width_get->resolved) {
        GetValue(width_get, nullptr, screen_w);
    }
    if (height_get && height_get->resolved) {
        GetValue(height_get, nullptr, screen_h);
    }
    if (screen_w <= 0 || screen_h <= 0) {
        // No live resolution yet (or the Screen contracts are missing). Feeding
        // OnInput a raw pixel would re-introduce the snap-quantization, so drop
        // the delta rather than mis-scale it. Rate-limited: an idle frame is not
        // a finding.
        static uint64_t s_screen_log = 0;
        if (s_screen_log++ % 120 == 0) {
            Log("First person gyro: screen resolution unavailable, delta dropped.");
        }
        return;
    }
    const float look_x = static_cast<float>(dx) / static_cast<float>(screen_w);
    const float look_y = static_cast<float>(-dy) / static_cast<float>(screen_h);
    if (look_x != 0.0f || look_y != 0.0f) {
        void* arguments[2]{const_cast<float*>(&look_x), const_cast<float*>(&look_y)};
        InvokeVoid(on_input, manager, arguments);
    }
}

#if defined(__ANDROID__)
// ---------------------------------------------------------------------------
// MMD, as the settings app sees it (android_camera.h).
//
// Same arrangement as the configuration reload below: the app only queues, this
// drains one command per tick, and the director executes it in Mmd::Pump on the
// observed frame thread. Nothing here reaches into Unity.
// ---------------------------------------------------------------------------
constexpr std::size_t kMmdCommandQueueLimit = 64;

// "<verb> [argument]". Textual, so the Java side never has to mirror the native
// enum values; this function is the one place the vocabulary is defined.
bool ParseMmdPanelCommand(std::string_view text,
    BetterEndfieldNext::MmdOverlayProtocol::Command& command) {
    using Type = BetterEndfieldNext::MmdOverlayProtocol::CommandType;
    while (!text.empty() && (text.back() == '\r' || text.back() == '\n')) text.remove_suffix(1);
    const std::size_t space = text.find(' ');
    const std::string_view verb = text.substr(0, space);
    std::string_view rest =
        space == std::string_view::npos ? std::string_view() : text.substr(space + 1);
    while (!rest.empty() && rest.front() == ' ') rest.remove_prefix(1);
    if (verb.empty()) return false;
    command = BetterEndfieldNext::MmdOverlayProtocol::Command{};
    if (verb == "play_pause") { command.type = static_cast<uint32_t>(Type::PlayPause); return true; }
    if (verb == "stop") { command.type = static_cast<uint32_t>(Type::Stop); return true; }
    if (verb == "loop") { command.type = static_cast<uint32_t>(Type::ToggleLoop); return true; }
    if (verb == "seek" || verb == "seek_absolute") {
        double seconds = 0.0;
        try {
            seconds = std::stod(std::string(rest));
        } catch (...) {
            return false;
        }
        if (!std::isfinite(seconds)) return false;
        command.type = static_cast<uint32_t>(
            verb == "seek" ? Type::SeekRelative : Type::SeekAbsolute);
        command.value = seconds;
        return true;
    }
    if (verb == "camera") {
        // Anything that is not an explicit index means "next mode".
        command.type = static_cast<uint32_t>(Type::CameraMode);
        command.argument =
            rest.size() == 1 && rest[0] >= '0' && rest[0] <= '2' ? rest[0] - '0' : -1;
        return true;
    }
    if (verb == "work") {
        // No argument clears the selection.
        if (rest.size() >= sizeof(command.text)) return false;
        command.type = static_cast<uint32_t>(Type::SelectWork);
        std::memcpy(command.text, rest.data(), rest.size());
        command.text[rest.size()] = '\0';
        return true;
    }
    return false;
}

// Bounded, so a panel that keeps posting cannot grow the queue without limit
// while the game thread is stalled.
bool QueueMmdCommand(const BetterEndfieldNext::MmdOverlayProtocol::Command& command) {
    std::lock_guard<std::mutex> lock(Mmd::android_mutex);
    if (Mmd::android_commands.size() >= kMmdCommandQueueLimit) return false;
    Mmd::android_commands.push_back(command);
    return true;
}

// One command per tick, exactly like the configuration reload above.
void DrainMmdPanelCommand() {
    std::string value;
    if (!betterendfieldnext::AcquirePanelCommand("mmd", value)) {
        return;
    }
    BetterEndfieldNext::MmdOverlayProtocol::Command command{};
    if (!ParseMmdPanelCommand(value, command)) {
        betterendfieldnext::AcknowledgePanelCommand("rejected");
        Log("MMD panel command rejected: " + value);
        return;
    }
    betterendfieldnext::AcknowledgePanelCommand(QueueMmdCommand(command) ? "applied" : "rejected");
}

#endif

// The camera configuration used to be read once, from the environment the host
// set before loading this library, so every parameter change cost a game
// restart. It is now also delivered through the runtime command pump: the
// settings app writes the same configuration string the boot path would have
// carried, the panel's poller hands it to the pump, and this drains it on the
// engine tick.
//
// The reload reuses ConfigurationChanged - the exact entry point the host calls
// at boot. That matters more than it looks: the function already stores every
// key as one idempotent block under its own mutex, and it already knows the two
// transitions that cannot be expressed by assignment (the free camera and the
// first-person camera being switched off, which request an exit). A reload
// therefore cannot leave a half-applied configuration behind, and it cannot
// invent behavior the boot path does not have.
//
// What a reload cannot do is add the module to a process that never started it:
// whether this library runs the camera module at all is decided by the boot
// configuration, so the first time the camera is switched on still needs a
// restart. Everything after that is live.
BE_Result BE_CALL ConfigurationChanged(const char* raw_configuration);

// Defined beside the key=value parsers because it reads the same text a
// configuration does. Declared here so the engine tick below drains it in the
// same pass as the reload it exists to spare.
void DrainGlobalFovCommand();

void DrainConfigurationReload() {
    std::string configuration;
    if (!betterendfieldnext::AcquirePanelCommand("camera_config", configuration)) {
        return;
    }
    const BE_Result applied = ConfigurationChanged(configuration.c_str());
    const bool ok = applied == BE_Result_Ok;
    betterendfieldnext::AcknowledgePanelCommand(ok ? "applied" : "rejected");
    char summary[160];
    std::snprintf(summary, sizeof(summary),
        "Camera configuration reloaded from the settings app: %zu bytes, %s "
        "without restarting the game.", configuration.size(), ok ? "applied" : "rejected");
    Log(summary);
}
#endif

void PumpFromEngineTick(const char* source) {
#if defined(__ANDROID__)
    if (!AndroidCameraReady()) return;
#endif
#if !defined(_WIN32)
    // All three drained ahead of the re-entrancy guard: they carry input and
    // configuration that must not be skipped just because this tick was entered
    // from inside another one.
    DrainConfigurationReload();
    DrainGlobalFovCommand();
    DrainMmdPanelCommand();
    FoldPanelLookInput();
#endif
    if (t_in_engine_tick) {
        return;
    }
    const bool pending = g_pause_request.load(std::memory_order_acquire) ||
        g_toggle_request.load(std::memory_order_acquire);
    t_in_engine_tick = true;
    PumpFreeCameraControl();
    // MMD director and the EIEM DirectVmd body port. Both are thread pinned and
    // re-entrancy guarded inside their own modules, so one pump here covers every
    // tick that reaches this function.
    PumpMmdDirector();
    PumpCharacterMotion();
    if (g_free_camera_active) {
        ApplyFreeCameraHeartbeat();
    }
    // Runs after the free camera has had its chance at the accumulator, so the
    // two consumers can never both take the same deltas.
#if !defined(_WIN32)
    ApplyFirstPersonLook();
#endif
    // Unconditional, rate-limited heartbeat. Every other candidate in this
    // function is gated on something ("first person is active", "the gyroscope
    // is on") and a journal of gates that never opened cannot distinguish "the
    // feature is broken" from "the pump stopped being called at all". This line
    // answers that one question and nothing else.
    {
        static std::atomic<uint64_t> s_pump_ticks{0};
        const uint64_t tick = s_pump_ticks.fetch_add(1, std::memory_order_relaxed);
        if (tick % 900 == 0) {
            char line[192];
            std::snprintf(line, sizeof(line),
                "Camera tick heartbeat: source=%s ticks=%llu module_fp=%d fp_enabled=%d "
                "free=%d gyro_look=%d",
                source, static_cast<unsigned long long>(tick),
                g_first_person_active.load(std::memory_order_relaxed) ? 1 : 0,
                g_first_person_camera_enabled.load(std::memory_order_relaxed) ? 1 : 0,
                g_free_camera_active ? 1 : 0,
                g_first_person_gyro_look.load(std::memory_order_relaxed) ? 1 : 0);
            Log(line);
        }
    }
    t_in_engine_tick = false;
    // Which tick drained a request is the one fact a device journal needs when a
    // hotkey looks dead. Requests are user driven, so this costs a line per tap.
    if (pending && !g_pause_request.load(std::memory_order_acquire) &&
        !g_toggle_request.load(std::memory_order_acquire)) {
        Log(std::string("Camera control request drained from the ") + source + ".");
    }
}

// Time.unscaledDeltaTime keeps being read while Time.timeScale is 0, so it is
// one of the heartbeat ticks for the hotkeys and for the free camera while the
// world is frozen. Not every build reads it on every frame, which is why the
// render loop below is pumped too.
float __fastcall DetourTimeUnscaledDelta(void* method) {
    const float result = g_original_time_unscaled_delta
        ? g_original_time_unscaled_delta(method)
        : 0.0f;
    if (g_time_heartbeat_contract_ready) {
        PumpFromEngineTick("unscaled time heartbeat");
    }
    return result;
}

// The engine calls DoRenderLoop_Internal for every rendered frame it hands to the
// Scriptable Render Pipeline, and the game keeps rendering while its world is
// frozen. That makes this the tick the paused world cannot silence, and the one
// that always gets the thaw hotkey through.
void __fastcall DetourRenderLoop(void* pipeline, void* loop_pointer,
        void* render_requests, void* method) {
    if (g_original_render_loop) {
        g_original_render_loop(pipeline, loop_pointer, render_requests, method);
    }
    if (g_render_loop_hook_ready) {
        PumpFromEngineTick("render loop");
    }
}

void __fastcall DetourPushState(void* instance, void* state, void* method) {
#if defined(__ANDROID__)
    if (!AndroidCameraReady()) {
        if (g_original_push_state) g_original_push_state(instance, state, method);
        return;
    }
#endif
    g_push_state_calls.fetch_add(1, std::memory_order_relaxed);
    // A character switch can arrive inside this camera update, before the
    // tail-tick pump runs. Hide the new head before Cinemachine pushes a frame.
    if (state && !g_free_camera_active &&
        g_first_person_active.load(std::memory_order_acquire) &&
        RefreshFirstPersonTarget()) {
        FpRefreshHiddenParts(true, false);
    }
    ScopedGlobalFovState global_fov_scope(instance,state,
        g_fp_perspective.visual_exit_deadline != 0);
    if (state && !g_free_camera_active && (g_first_person_camera_enabled.load(std::memory_order_acquire) ||
        g_fp_perspective.visual_exit_deadline)) {
        FpCoordinateState(instance,state);
    } else if (state && g_free_camera_active && g_state_layout.ready &&
        BrainDrivesActiveCamera(instance)) {
        ApplyFreeCameraState(state);
    }
    if (g_original_push_state) {
        g_original_push_state(instance, state, method);
    }
}

void __fastcall DetourTailLateTick(void* instance, float deltaTime, void* method) {
    if (g_original_tail_late_tick) {
        g_original_tail_late_tick(instance, deltaTime, method);
    }
#if defined(__ANDROID__)
    if (!AndroidCameraReady()) return;
#endif
    // The hook is on CameraManager::TailLateTick, so `instance` is the game's
    // main camera manager - the object that owns the look input the first-person
    // gyroscope must feed. Captured here (with a gchandle to keep it pinned)
    // rather than looked up by FindObjectOfType, because the module already
    // receives it on every frame for free.
    if (instance && instance != g_first_person.camera_manager) {
        if (g_first_person.camera_manager_handle && g_host && g_host->gchandle_free) {
            g_host->gchandle_free(g_host->context, g_first_person.camera_manager_handle);
        }
        g_first_person.camera_manager = instance;
        g_first_person.camera_manager_handle = g_host && g_host->gchandle_new
            ? g_host->gchandle_new(g_host->context, instance, 0)
            : 0;
    }
    // TailLateTick runs at the very tail of the frame. The first-person pose is
    // applied inside the Cinemachine push itself, so this only drives the toggle,
    // the eye anchor bookkeeping and the free camera.
    PumpFreeCameraControl();
    PumpFirstPerson();
    if (g_free_camera_active) {
        ApplyFreeCamera();
    }
}

void __fastcall DetourCameraTick(void* instance, void* method) {
#if defined(__ANDROID__)
    if (!AndroidCameraReady()) {
        if (g_original_camera_tick) g_original_camera_tick(instance, method);
        return;
    }
#endif
    FpFacingRestoreBeforeTick();
    if (g_original_camera_tick) {
        g_original_camera_tick(instance, method);
    }
    if (g_disable_dither_enabled.load(std::memory_order_acquire) &&
        g_dither_contract_ready) {
        InvokeVoid(Contract("camera.force_clear_dither"), instance, nullptr);
    }
    // If TailLateTick is not hooked, fall back to the camera tick for the pumps.
    if (!g_original_tail_late_tick) {
        PumpFreeCamera();
        PumpFirstPerson();
    }
}

#if defined(__ANDROID__)
void AndroidCameraFrame(bool suspend) {
    if (!AndroidCameraReady()) return;
    static uint64_t previous_generation = 0;
    if (suspend) {
        g_toggle_request.store(false);
        g_pause_request.store(false);
        g_motion_request.store(false);
        g_keyframe_add_request.store(false);
        g_keyframe_play_request.store(false);
        g_keyframe_clear_request.store(false);
        g_vmd_play_request.store(false);
        g_first_person_toggle_request.store(false);
        ExitFirstPerson("Android Activity lost focus", true);
        Mmd::Stop("Android Activity paused");
        RequestCharacterMotion(4u);
        PumpCharacterMotion();
        ExitFreeCamera("Android Activity lost focus");
        RestoreWorldPause("Android Activity lost focus");
    } else {
      if (previous_generation == g_android_pump_generation) {
        // No game-side pump ran since the last rendered frame. This remains on
        // Unity's thread even when gameplay ticks stop at timeScale == 0.
        PumpFromEngineTick("Android render frame");
        PumpFirstPerson();
      }
    }
    previous_generation = g_android_pump_generation;
    const unsigned capabilities = (g_free_camera_contract_ready && g_free_camera_enabled.load() ? 1u : 0u) |
        (g_pause_enabled.load() ? 8u : 0u) |
        (g_first_person_contract_ready && g_first_person_camera_enabled.load() ? 2u : 0u) |
        (g_dither_contract_ready && g_disable_dither_enabled.load() ? 4u : 0u);
    const unsigned active = (g_free_camera_active ? 1u : 0u) |
        (g_changed_time_scale ? 8u : 0u) |
        (g_first_person_active.load() ? 2u : 0u);
    betterendfieldnext::PublishAndroidCameraState(capabilities, active);
}
#endif

std::string Trim(std::string_view value) {
    while (!value.empty() && std::isspace(static_cast<unsigned char>(value.front()))) {
        value.remove_prefix(1);
    }
    while (!value.empty() && std::isspace(static_cast<unsigned char>(value.back()))) {
        value.remove_suffix(1);
    }
    return std::string(value);
}

bool ParseBoolean(std::string_view value, bool default_value = false) {
    std::string text = Trim(value);
    std::transform(text.begin(), text.end(), text.begin(),
        [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    if (text == "1" || text == "true" || text == "yes" || text == "on") {
        return true;
    }
    if (text == "0" || text == "false" || text == "no" || text == "off") {
        return false;
    }
    return default_value;
}

float ParseFloat(std::string_view value, float default_value) {
    std::string text = Trim(value);
    if (text.empty()) {
        return default_value;
    }
    char* end = nullptr;
    const float parsed = std::strtof(text.c_str(), &end);
    return end && end != text.c_str() && std::isfinite(parsed)
        ? parsed
        : default_value;
}

// The settings app sends the live field of view as the same flat key=value text
// a camera configuration uses, so one value means one thing on both paths.
// Deliberately not ParseBoolean: its fallback would turn a misspelt key such as
// "enable=" into a silent "off", and a switch that quietly does nothing is the
// exact failure this channel exists to rule out. Both keys are required, because
// a request carrying only one of them would leave the other at whatever the
// previous request happened to be.
bool ParseGlobalFovCommand(std::string_view text, bool& enabled, float& fov) {
    bool have_enabled = false;
    bool have_value = false;
    std::size_t start = 0;
    while (start < text.size()) {
        const std::size_t newline = text.find('\n', start);
        const std::string_view raw_line = text.substr(start,
            newline == std::string_view::npos ? std::string_view::npos : newline - start);
        start = newline == std::string_view::npos ? text.size() : newline + 1;
        const std::string line = Trim(raw_line);
        if (line.empty()) {
            continue;
        }
        const std::size_t equals = line.find('=');
        if (equals == std::string::npos) {
            return false;
        }
        const std::string key = Trim(line.substr(0, equals));
        const std::string raw = Trim(line.substr(equals + 1));
        if (key == "enabled") {
            if (raw == "1" || raw == "true" || raw == "yes" || raw == "on") {
                enabled = true;
            } else if (raw == "0" || raw == "false" || raw == "no" || raw == "off") {
                enabled = false;
            } else {
                return false;
            }
            have_enabled = true;
        } else if (key == "value") {
            char* end = nullptr;
            const float parsed = std::strtof(raw.c_str(), &end);
            if (!end || end == raw.c_str() || !std::isfinite(parsed)) {
                return false;
            }
            fov = parsed;
            have_value = true;
        } else {
            // An unknown key is a malformed request rather than a no-op: the
            // settings app and this parser are supposed to spell the same way.
            return false;
        }
    }
    return have_enabled && have_value;
}

// The field-of-view slider reports on every step of a drag, so a value-only
// change has to reach the camera without reloading the configuration: a reload
// rebuilds every camera state to change one number, and it takes the free camera
// and the MMD director down with it while it does. This writes the two atomics
// the lens override already reads (see ScopedGlobalFovState) and nothing else -
// no camera state, no layout, no hotkey. Switching the feature on or off is
// deliberately not accepted here; that decides whether the module runs at all,
// which a reload has to settle.
#if !defined(_WIN32)
void DrainGlobalFovCommand() {
    std::string text;
    if (!betterendfieldnext::AcquirePanelCommand("global_fov", text)) {
        return;
    }
    bool enabled = false;
    float fov = g_global_fov.load(std::memory_order_relaxed);
    if (!ParseGlobalFovCommand(text, enabled, fov) ||
        !std::isfinite(fov) || fov < kFreeMinFov || fov > kFreeMaxFov) {
        betterendfieldnext::AcknowledgePanelCommand("rejected");
        Log("Global FOV rejected: expected enabled=<bool> and value=<5..150>.");
        return;
    }
    // Neither of these can be created on demand: the push hook has to have been
    // installed and the camera state layout resolved when the configuration
    // loaded. Accepting the value without them would report success for an
    // override that never runs.
    if (!g_push_state_hook_ready || !g_state_layout.ready) {
        betterendfieldnext::AcknowledgePanelCommand("rejected");
        Log("Global FOV unavailable: the camera state or the push hook was not resolved.");
        return;
    }
    // The lens override reads the flag first and the value second, so publish the
    // value first: an enabled flag must never be visible ahead of the degree it
    // is enabling.
    g_global_fov.store(fov, std::memory_order_release);
    g_global_fov_enabled.store(enabled, std::memory_order_release);
    betterendfieldnext::AcknowledgePanelCommand("applied");
    char summary[128];
    std::snprintf(summary, sizeof(summary),
        "Global FOV set to %.1f degrees without reloading the configuration.", fov);
    Log(summary);
}

#endif

int ParseVirtualKey(std::string_view value, int fallback) {
    std::string key = Trim(value);
    std::transform(key.begin(), key.end(), key.begin(),
        [](unsigned char c) { return static_cast<char>(std::toupper(c)); });
    if (key == "-" || key == "MINUS" || key == "OEM_MINUS") {
        return VK_OEM_MINUS;
    }
    if (key == "SUBTRACT" || key == "NUMPAD-" || key == "NUMPAD_MINUS" || key == "NUMPADSUBTRACT") {
        return VK_SUBTRACT;
    }
    if (key.size() == 1 && std::isalnum(static_cast<unsigned char>(key[0]))) {
        return static_cast<unsigned char>(key[0]);
    }
    if (key.size() > 1 && key.front() == 'F') {
        const int number = std::atoi(key.c_str() + 1);
        if (number >= 1 && number <= 24) {
            return VK_F1 + number - 1;
        }
    }
    constexpr std::string_view numpad_prefix = "NUMPAD";
    if (key.size() == numpad_prefix.size() + 1 &&
        key.starts_with(numpad_prefix) && key.back() >= '0' && key.back() <= '9') {
        return VK_NUMPAD0 + key.back() - '0';
    }
    return fallback;
}

int ParseMotionPreset(std::string_view value, int fallback) {
    std::string text = Trim(value);
    std::transform(text.begin(), text.end(), text.begin(),
        [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    if (text == "orbit") return 0;
    if (text == "dolly_zoom" || text == "dolly") return 1;
    if (text == "crane") return 2;
    if (text == "truck" || text == "pan") return 3;
    return fallback;
}

std::string Unquote(std::string_view value) {
    std::string text = Trim(value);
    if (text.size() >= 2 && text.front() == '"' && text.back() == '"') {
        text = text.substr(1, text.size() - 2);
    }
    return text;
}

CameraConfiguration ParseConfiguration(const char* raw_configuration) {
    CameraConfiguration config;
    if (!raw_configuration) {
        return config;
    }
    std::string_view text(raw_configuration);
    bool in_section = true;
    size_t line_start = 0;
    while (line_start < text.size()) {
        size_t line_end = text.find_first_of("\r\n", line_start);
        if (line_end == std::string_view::npos) line_end = text.size();
        const std::string line = Trim(text.substr(line_start, line_end - line_start));
        line_start = text.find_first_not_of("\r\n", line_end);
        if (line.empty() || line.front() == ';' || line.front() == '#') continue;
        if (line.front() == '[' && line.back() == ']') {
            in_section = line == "[betterendfieldnext.camera]";
            continue;
        }
        if (!in_section) continue;
        const size_t equals = line.find('=');
        if (equals == std::string::npos) continue;
        const std::string key = Trim(line.substr(0, equals));
        const std::string value = Trim(line.substr(equals + 1));
        if (key == "schema_version") config.schema_version = std::atoi(value.c_str());
        else if (key == "enabled") config.enabled = ParseBoolean(value, config.enabled);
        else if (key == "free_camera_enabled") config.free_camera_enabled = ParseBoolean(value, config.free_camera_enabled);
        else if (key == "disable_dither_enabled") config.disable_dither_enabled = ParseBoolean(value, config.disable_dither_enabled);
        else if (key == "pause_enabled") config.pause_enabled = ParseBoolean(value, config.pause_enabled);
        else if (key == "first_person_camera_enabled" || key == "first_person_enabled") config.first_person_camera_enabled = ParseBoolean(value, config.first_person_camera_enabled);
        else if (key == "first_person_hide_head") config.first_person_hide_head = ParseBoolean(value, config.first_person_hide_head);
        else if (key == "first_person_fill_neck_hole") config.first_person_fill_neck_hole = ParseBoolean(value, config.first_person_fill_neck_hole);
        else if (key == "first_person_eye_height") config.first_person_eye_height = ParseFloat(value, config.first_person_eye_height);
        else if (key == "first_person_eye_forward") config.first_person_eye_forward = ParseFloat(value, config.first_person_eye_forward);
        else if (key == "first_person_near_clip") config.first_person_near_clip = ParseFloat(value, config.first_person_near_clip);
        else if (key == "first_person_extend_look_range") config.first_person_extend_look_range = ParseBoolean(value, config.first_person_extend_look_range);
        else if (key == "first_person_look_up_limit") config.first_person_look_up_limit = ParseFloat(value, config.first_person_look_up_limit);
        else if (key == "first_person_look_down_limit") config.first_person_look_down_limit = ParseFloat(value, config.first_person_look_down_limit);
        else if (key == "first_person_movement") config.first_person_movement = ParseBoolean(value, config.first_person_movement);
        else if (key == "first_person_side_look_limit") config.first_person_side_look_limit = ParseFloat(value, config.first_person_side_look_limit);
        else if (key == "first_person_animation_mode") {
            const float mode=ParseFloat(value,0);
            config.first_person_animation_mode=(mode>=0 && mode<=3 && mode==std::floor(mode)) ? static_cast<int>(mode) : 0;
        }
        else if (key == "first_person_animation_strength") config.first_person_animation_strength = ParseFloat(value, config.first_person_animation_strength);
        else if (key == "first_person_yield_dialogue") config.first_person_yield_dialogue = ParseBoolean(value, config.first_person_yield_dialogue);
        else if (key == "first_person_third_person_in_combat") config.first_person_third_person_in_combat = ParseBoolean(value, config.first_person_third_person_in_combat);
        else if (key == "first_person_transition_seconds") config.first_person_transition_seconds = ParseFloat(value, config.first_person_transition_seconds);
        else if (key == "first_person_external_head_scale") config.first_person_external_head_scale = ParseBoolean(value, config.first_person_external_head_scale);
        else if (key == "first_person_neck_plug_scale") config.first_person_neck_plug_scale = ParseFloat(value, config.first_person_neck_plug_scale);
        else if (key == "diagnostics") config.diagnostics = ParseBoolean(value, config.diagnostics);
        else if (key == "movement_speed") config.movement_speed = ParseFloat(value, config.movement_speed);
        else if (key == "field_of_view") config.field_of_view = ParseFloat(value, config.field_of_view);
        else if (key == "global_fov_enabled") config.global_fov_enabled = ParseBoolean(value, config.global_fov_enabled);
        else if (key == "global_fov") config.global_fov = ParseFloat(value, config.global_fov);
        else if (key == "free_camera_follow_character") config.free_camera_follow_character = ParseBoolean(value, config.free_camera_follow_character);
        else if (key == "first_person_fov") config.first_person_fov = ParseFloat(value, config.first_person_fov);
        else if (key == "toggle_hotkey") config.toggle_key = ParseVirtualKey(value, config.toggle_key);
        else if (key == "pause_hotkey") config.pause_key = ParseVirtualKey(value, config.pause_key);
        else if (key == "first_person_hotkey") config.first_person_key = ParseVirtualKey(value, config.first_person_key);
        else if (key == "free_camera_mouse_look") config.mouse_look = ParseBoolean(value, config.mouse_look);
        else if (key == "mouse_invert_y") config.mouse_invert_y = ParseBoolean(value, config.mouse_invert_y);
        else if (key == "mouse_sensitivity") config.mouse_sensitivity = ParseFloat(value, config.mouse_sensitivity);
        // First-person gyroscope. These are informational unless the gyroscope
        // is on, and the gyroscope itself is armed on the app side - native only
        // decides what happens to the deltas once they arrive.
        else if (key == "first_person_gyro_look") config.first_person_gyro_look = ParseBoolean(value, config.first_person_gyro_look);
        else if (key == "first_person_gyro_horizontal") config.first_person_gyro_horizontal = ParseFloat(value, config.first_person_gyro_horizontal);
        else if (key == "first_person_gyro_vertical") config.first_person_gyro_vertical = ParseFloat(value, config.first_person_gyro_vertical);
        else if (key == "first_person_gyro_invert_horizontal") config.first_person_gyro_invert_horizontal = ParseBoolean(value, config.first_person_gyro_invert_horizontal);
        else if (key == "first_person_gyro_invert_vertical") config.first_person_gyro_invert_vertical = ParseBoolean(value, config.first_person_gyro_invert_vertical);
        else if (key == "first_person_gyro_deadzone") config.first_person_gyro_deadzone = ParseFloat(value, config.first_person_gyro_deadzone);
        else if (key == "free_camera_smoothing") config.smoothing = ParseFloat(value, config.smoothing);
        else if (key == "motion_preset") config.motion_preset = ParseMotionPreset(value, config.motion_preset);
        else if (key == "motion_speed") config.motion_speed = ParseFloat(value, config.motion_speed);
        else if (key == "orbit_speed") config.orbit_speed = ParseFloat(value, config.orbit_speed);
        else if (key == "motion_duration") config.motion_duration = ParseFloat(value, config.motion_duration);
        else if (key == "motion_target_height") config.motion_target_height = ParseFloat(value, config.motion_target_height);
        else if (key == "keyframe_segment_seconds") config.keyframe_segment_seconds = ParseFloat(value, config.keyframe_segment_seconds);
        else if (key == "keyframe_loop") config.keyframe_loop = ParseBoolean(value, config.keyframe_loop);
        else if (key == "keyframe_file") config.keyframe_file = Unquote(value);
        else if (key == "vmd_camera_file") config.vmd_camera_file = Unquote(value);
        else if (key == "vmd_camera_scale") config.vmd_camera_scale = ParseFloat(value, config.vmd_camera_scale);
        else if (key == "vmd_camera_fov_bias") config.vmd_camera_fov_bias = ParseFloat(value, config.vmd_camera_fov_bias);
        else if (key == "vmd_camera_loop") config.vmd_camera_loop = ParseBoolean(value, config.vmd_camera_loop);
        else if (key == "roll_left_hotkey") config.roll_left_key = ParseVirtualKey(value, config.roll_left_key);
        else if (key == "roll_right_hotkey") config.roll_right_key = ParseVirtualKey(value, config.roll_right_key);
        else if (key == "fov_wide_hotkey") config.fov_wide_key = ParseVirtualKey(value, config.fov_wide_key);
        else if (key == "fov_narrow_hotkey") config.fov_narrow_key = ParseVirtualKey(value, config.fov_narrow_key);
        else if (key == "view_reset_hotkey") config.view_reset_key = ParseVirtualKey(value, config.view_reset_key);
        else if (key == "motion_hotkey") config.motion_key = ParseVirtualKey(value, config.motion_key);
        else if (key == "keyframe_add_hotkey") config.keyframe_add_key = ParseVirtualKey(value, config.keyframe_add_key);
        else if (key == "keyframe_play_hotkey") config.keyframe_play_key = ParseVirtualKey(value, config.keyframe_play_key);
        else if (key == "keyframe_clear_hotkey") config.keyframe_clear_key = ParseVirtualKey(value, config.keyframe_clear_key);
        else if (key == "vmd_play_hotkey") config.vmd_play_key = ParseVirtualKey(value, config.vmd_play_key);
        // MMD. The key names are the desktop build's, so one configuration file
        // drives both platforms.
        else if (key == "mmd_enabled") config.mmd_enabled = ParseBoolean(value, config.mmd_enabled);
        else if (key == "mmd_overlay_enabled") config.mmd_overlay_enabled = ParseBoolean(value, config.mmd_overlay_enabled);
        else if (key == "mmd_overlay_visible") config.mmd_overlay_visible = ParseBoolean(value, config.mmd_overlay_visible);
        else if (key == "mmd_loop") config.mmd_loop = ParseBoolean(value, config.mmd_loop);
        else if (key == "mmd_music_enabled") config.mmd_music_enabled = ParseBoolean(value, config.mmd_music_enabled);
        else if (key == "mmd_seek_seconds") config.mmd_seek_seconds = ParseFloat(value, config.mmd_seek_seconds);
        else if (key == "mmd_music_gain") config.mmd_music_gain = ParseFloat(value, config.mmd_music_gain);
        else if (key == "mmd_audio_offset") config.mmd_audio_offset = ParseFloat(value, config.mmd_audio_offset);
        else if (key == "mmd_work") config.mmd_work = Unquote(value);
        else if (key == "mmd_music_file") config.mmd_music_file = Unquote(value);
        else if (key == "mmd_face_file") config.mmd_face_file = Unquote(value);
        else if (key == "mmd_play_hotkey") config.mmd_play_key = ParseVirtualKey(value, config.mmd_play_key);
        else if (key == "mmd_stop_hotkey") config.mmd_stop_key = ParseVirtualKey(value, config.mmd_stop_key);
        else if (key == "mmd_seek_back_hotkey") config.mmd_seek_back_key = ParseVirtualKey(value, config.mmd_seek_back_key);
        else if (key == "mmd_seek_forward_hotkey") config.mmd_seek_forward_key = ParseVirtualKey(value, config.mmd_seek_forward_key);
        else if (key == "mmd_camera_mode_hotkey") config.mmd_camera_mode_key = ParseVirtualKey(value, config.mmd_camera_mode_key);
        else if (key == "mmd_overlay_hotkey") config.mmd_overlay_key = ParseVirtualKey(value, config.mmd_overlay_key);
        // The EIEM body-motion block. The fields travelled with the file import,
        // but nothing parsed them, so body/face/terrain/cloth could not be reached
        // from any configuration - the MMD director read them and always found
        // its defaults. Spellings are upstream's, so one configuration file stays
        // valid on both platforms.
        else if (key == "vmd_motion_file") config.vmd_motion_file = Unquote(value);
        else if (key == "vmd_body_enabled") config.vmd_body_enabled = ParseBoolean(value, config.vmd_body_enabled);
        else if (key == "vmd_eyes_enabled") config.vmd_eyes_enabled = ParseBoolean(value, config.vmd_eyes_enabled);
        else if (key == "vmd_face_enabled") config.vmd_face_enabled = ParseBoolean(value, config.vmd_face_enabled);
        else if (key == "vmd_motion_loop") config.vmd_motion_loop = ParseBoolean(value, config.vmd_motion_loop);
        else if (key == "vmd_terrain_enabled") config.vmd_terrain_enabled = ParseBoolean(value, config.vmd_terrain_enabled);
        else if (key == "vmd_cloth_mode") {
            const std::string mode = LowerAscii(Unquote(value));
            config.vmd_cloth_mode = mode == "game" ? 0 : mode == "freeze" ? 2 : 1;
        }
        else if (key == "vmd_motion_scale") config.vmd_motion_scale = ParseFloat(value, config.vmd_motion_scale);
        else if (key == "vmd_motion_weight") config.vmd_motion_weight = ParseFloat(value, config.vmd_motion_weight);
    }
    config.mmd_seek_seconds = std::clamp(config.mmd_seek_seconds, 0.5f, 60.0f);
    config.movement_speed = std::clamp(config.movement_speed, 0.5f, 100.0f);
    config.field_of_view = std::clamp(config.field_of_view, 20.0f, 120.0f);
    config.global_fov = std::isfinite(config.global_fov)
        ? std::clamp(config.global_fov, 5.0f, 150.0f) : 60.0f;
    config.first_person_fov = std::clamp(config.first_person_fov, 20.0f, 120.0f);
    config.first_person_neck_plug_scale =
        std::clamp(config.first_person_neck_plug_scale, 0.2f, 3.0f);
    config.first_person_eye_height =
        std::clamp(config.first_person_eye_height, -0.5f, 0.5f);
    config.first_person_eye_forward =
        std::clamp(config.first_person_eye_forward, 0.0f, 0.5f);
    config.first_person_near_clip =
        std::clamp(config.first_person_near_clip, 0.001f, 1.0f);
    config.first_person_side_look_limit = std::clamp(config.first_person_side_look_limit, 0.f, 90.f);
    config.first_person_animation_strength = std::clamp(config.first_person_animation_strength, 0.f, 1.f);
    config.first_person_transition_seconds = std::clamp(config.first_person_transition_seconds, 0.f, 1.f);
    config.mouse_sensitivity = std::clamp(config.mouse_sensitivity, 0.01f, 2.0f);
    // The upper bound is deliberately wide: the game's RotateCamera* units are
    // not knowable without a device, and a bound tight enough to be "sane" would
    // block the calibration. The app-side clamp is the real one.
    config.first_person_gyro_horizontal = std::clamp(config.first_person_gyro_horizontal, -1000.0f, 1000.0f);
    config.first_person_gyro_vertical = std::clamp(config.first_person_gyro_vertical, -1000.0f, 1000.0f);
    config.first_person_gyro_deadzone = std::clamp(config.first_person_gyro_deadzone, 0.0f, 0.25f);
    config.smoothing = std::clamp(config.smoothing, 0.0f, 0.95f);
    config.motion_speed = std::clamp(config.motion_speed, -20.0f, 20.0f);
    config.orbit_speed = std::clamp(config.orbit_speed, -180.0f, 180.0f);
    config.motion_duration = std::clamp(config.motion_duration, 0.0f, 600.0f);
    config.motion_target_height = std::clamp(config.motion_target_height, -5.0f, 5.0f);
    config.keyframe_segment_seconds = std::clamp(config.keyframe_segment_seconds, 0.2f, 60.0f);
    config.vmd_camera_scale = std::clamp(config.vmd_camera_scale, 0.001f, 10.0f);
    config.vmd_camera_fov_bias = std::clamp(config.vmd_camera_fov_bias, -60.0f, 60.0f);
    return config;
}

bool ResolveContracts() {
    if (!g_host || !g_host->resolve_method) {
        return false;
    }
    for (auto& contract : g_contracts) {
        BE_ResolvedMethodV1 resolved{};
        BE_Result status = g_host->resolve_method(g_host->context,
            &contract.descriptor, &resolved);
        if ((status != BE_Result_Ok || !resolved.method_pointer ||
                !resolved.method_info) &&
            contract.alternate_parameter_types) {
            BE_MethodDescriptorV1 alternate = contract.descriptor;
            alternate.parameter_types = contract.alternate_parameter_types;
            status = g_host->resolve_method(g_host->context, &alternate, &resolved);
        }
        if (status == BE_Result_Ok && resolved.method_pointer &&
            resolved.method_info) {
            contract.pointer = resolved.method_pointer;
            contract.method_info = resolved.method_info;
            contract.resolved = true;
            Log(std::string("Resolved method contract: ") + contract.key);
        } else {
            Log(std::string("Method contract not found: ") + contract.key);
        }
    }

    if (g_host->resolve_field) {
        for (auto& field : g_fields) {
            BE_ResolvedFieldV1 resolved{};
            if (g_host->resolve_field(g_host->context, &field.descriptor, &resolved) ==
                    BE_Result_Ok && resolved.offset > 0) {
                field.resolved = resolved;
                field.ready = true;
                Log(std::string("Resolved field contract: ") + field.key +
                    " (offset=0x" + [] (int32_t offset) {
                        char text[16];
                        std::snprintf(text, sizeof(text), "%X", offset);
                        return std::string(text);
                    }(resolved.offset) + ")");
            } else {
                Log(std::string("Field contract not found: ") + field.key);
            }
        }
    }

    // The metadata offset of the first field of a value type is exactly the size
    // of the boxed object header, which is what the CameraState arithmetic needs.
    const FieldContract* probe = Field("unity.vector3.x");
    if (probe && probe->ready && probe->resolved.offset > 0 &&
        probe->resolved.offset <= 0x20) {
        g_value_type_header = probe->resolved.offset;
    } else {
        g_value_type_header = 0;
        Log("First person: the value type header probe failed; assuming metadata "
            "offsets are struct relative.");
    }

    if (g_host->resolve_class) {
        BE_ResolvedClassV1 snapshot_class{};
        if (g_host->resolve_class(g_host->context, "Gameplay.Beyond.dll",
                "Beyond.Gameplay.View", "SnapshotCameraController",
                &snapshot_class) == BE_Result_Ok) {
            g_snapshot_controller_class = snapshot_class;
            Log("Resolved class: Beyond.Gameplay.View.SnapshotCameraController");
        } else {
            Log("Class not found: Beyond.Gameplay.View.SnapshotCameraController");
        }
        BE_ResolvedClassV1 animator_class{};
        if (g_host->resolve_class(g_host->context, "UnityEngine.AnimationModule.dll",
                "UnityEngine", "Animator", &animator_class) == BE_Result_Ok) {
            g_animator_class = animator_class;
        }
        BE_ResolvedClassV1 skinned_class{};
        if (g_host->resolve_class(g_host->context, "UnityEngine.CoreModule.dll",
                "UnityEngine", "SkinnedMeshRenderer", &skinned_class) == BE_Result_Ok) {
            g_skinned_mesh_renderer_class = skinned_class;
            Log("Resolved class: UnityEngine.SkinnedMeshRenderer");
        } else {
            Log("Class not found: UnityEngine.SkinnedMeshRenderer");
        }
        BE_ResolvedClassV1 mesh_renderer_class{};
        if (g_host->resolve_class(g_host->context, "UnityEngine.CoreModule.dll",
                "UnityEngine", "MeshRenderer", &mesh_renderer_class) == BE_Result_Ok) {
            g_mesh_renderer_class = mesh_renderer_class;
        }
        BE_ResolvedClassV1 renderer_class{};
        if (g_host->resolve_class(g_host->context, "UnityEngine.CoreModule.dll",
                "UnityEngine", "Renderer", &renderer_class) == BE_Result_Ok) {
            g_renderer_class = renderer_class;
        }

    }

    BuildCameraStateLayout();

    const auto ready = [](std::string_view key) {
        const MethodContract* method = Contract(key);
        return method && method->resolved;
    };
    g_dither_contract_ready = ready("camera.process_dither") &&
        ready("camera.force_clear_dither");
    g_free_camera_contract_ready = ready("camera.process_dither") &&
        ready("unity.camera.main") && ready("unity.camera.fov.get") &&
        ready("unity.camera.fov.set") && ready("unity.component.transform") &&
        ready("unity.transform.position.get") &&
        ready("unity.transform.position.set") &&
        ready("unity.transform.forward") && ready("unity.transform.right") &&
        ready("unity.transform.up") && ready("unity.transform.rotation.set") &&
        ready("unity.time.scale.get") &&
        ready("unity.time.scale.set");
    g_time_heartbeat_contract_ready = ready("unity.time.unscaled_delta.get");
    g_first_person_contract_ready =
        (ready("camera_manager.tail_late_tick") || ready("camera.process_dither")) &&
        ready("cinemachine.push_state") &&
        ready("player_controller.get_main_character") &&
        ready("entity.get_model_com") &&
        ready("base_model_component.get_model_go") &&
        ready("unity.game_object.transform") &&
        ready("unity.object.name.get") &&
        ready("unity.transform.position.get") &&
        ready("unity.transform.child_count.get") &&
        ready("unity.transform.get_child") &&
        ready("unity.transform.find") &&
        ready("unity.camera.main") &&
        ready("unity.camera.fov.get") &&
        g_state_layout.ready;

    const FieldContract* first_person_flag = Field("snapshot.is_first_person");
    const bool photo_mode_exit_ready = ready("snapshot.set_first_person") &&
        ready("snapshot.show_char") && ready("unity.object.find_object_of_type") &&
        first_person_flag && first_person_flag->ready &&
        g_snapshot_controller_class.type_object;
    const bool head_part_probe_ready = ready("unity.game_object.get_component") &&
        ready("unity.skinned_mesh_renderer.shared_mesh.get") &&
        ready("unity.mesh.vertex_count.get") &&
        g_skinned_mesh_renderer_class.type_object;
    // Whether the gyroscope can drive the first-person camera at all. Kept
    // separate from the first-person contract: the camera itself works without
    // this method, and a build that lacks it must fail the gyroscope silently
    // rather than take the first-person camera down with it. It needs the main
    // camera manager's look entry (CameraManager::OnInput) and the tail-late
    // tick hook that captures the manager's instance.
    //
    // Assigned before the summary log below. It used to sit after it, which made
    // the journal print "first_person_gyro=unavailable" on every launch while the
    // path itself was armed - a log that lied about the exact fact an operator
    // would read to decide whether the feature was even present.
    g_first_person_gyro_contract_ready = ready("camera_manager.on_input") &&
        ready("camera_manager.tail_late_tick");

    Log(std::string("Camera feature contracts: free_camera=") +
        (g_free_camera_contract_ready ? "ready" : "unavailable") +
        ", first_person=" + (g_first_person_contract_ready ? "ready" : "unavailable") +
        ", first_person_gyro=" + (g_first_person_gyro_contract_ready ? "ready" : "unavailable") +
        ", anti_dither=" + (g_dither_contract_ready ? "ready" : "unavailable") +
        ", time_heartbeat=" +
        (g_time_heartbeat_contract_ready ? "ready" : "unavailable") +
        ", render_loop=" + (ready("unity.render_loop") ? "ready" : "unavailable"));
    const bool neck_cap_ready = ready("unity.skinned_mesh_renderer.bones.get") &&
        ready("unity.mesh.bindposes.get") && ready("unity.object.destroy");
    Log(std::string("First person optional contracts: photo_mode_exit=") +
        (photo_mode_exit_ready ? "ready" : "unavailable") +
        ", head_part_probe=" + (head_part_probe_ready ? "ready" : "unavailable") +
        ", neck_cap=" + (neck_cap_ready ? "ready" : "unavailable") +
        ", shadow_mode=" +
        ((ready("unity.renderer.shadow_casting_mode.get") &&
            ready("unity.renderer.shadow_casting_mode.set"))
            ? "ready" : "unavailable") +
        ", humanoid_head_bone=" +
        ((ready("unity.animator.get_bone_transform") &&
            ready("unity.game_object.get_component") &&
            g_animator_class.type_object) ? "ready" : "unavailable"));
    return g_free_camera_contract_ready || g_dither_contract_ready ||
        g_first_person_contract_ready;
}

bool InstallHook() {
    MethodContract* tick = Contract("camera.process_dither");
    if (!tick || !tick->resolved || !g_host || !g_host->create_hook) {
        return false;
    }
    if (g_host->create_hook(g_host->context, kModuleId, tick->pointer,
        reinterpret_cast<void*>(&DetourCameraTick),
        reinterpret_cast<void**>(&g_original_camera_tick)) != BE_Result_Ok) {
        return false;
    }

    MethodContract* tail_tick = Contract("camera_manager.tail_late_tick");
    if (tail_tick && tail_tick->resolved) {
        if (g_host->create_hook(g_host->context, kModuleId, tail_tick->pointer,
            reinterpret_cast<void*>(&DetourTailLateTick),
            reinterpret_cast<void**>(&g_original_tail_late_tick)) == BE_Result_Ok) {
            Log("Successfully installed CameraManager::TailLateTick hook.");
        } else {
            Log("Warning: Failed to install CameraManager::TailLateTick hook; using CameraMono tick fallback.");
        }
    }

    MethodContract* push_state = Contract("cinemachine.push_state");
    if (push_state && push_state->resolved) {
        if (g_host->create_hook(g_host->context, kModuleId, push_state->pointer,
            reinterpret_cast<void*>(&DetourPushState),
            reinterpret_cast<void**>(&g_original_push_state)) == BE_Result_Ok) {
            g_push_state_hook_ready = true;
            Log("Successfully installed CinemachineBrain::PushStateToUnityCamera hook.");
        } else {
            Log("Failed to install the Cinemachine CameraState hook; the first-person "
                "camera stays unavailable.");
        }
    }

    MethodContract* heartbeat = Contract("unity.time.unscaled_delta.get");
    if (heartbeat && heartbeat->resolved) {
        if (g_host->create_hook(g_host->context, kModuleId, heartbeat->pointer,
            reinterpret_cast<void*>(&DetourTimeUnscaledDelta),
            reinterpret_cast<void**>(&g_original_time_unscaled_delta)) != BE_Result_Ok) {
            Log("Failed to install unscaled time heartbeat hook; camera hook fallback remains active.");
        }
    }

    // The pump that has to survive a frozen world. Optional on purpose: a game
    // build without a Scriptable Render Pipeline still gets the camera tick and
    // the unscaled time heartbeat, so a missing contract must not disable the
    // module.
    MethodContract* render_loop = Contract("unity.render_loop");
    if (render_loop && render_loop->resolved) {
        if (g_host->create_hook(g_host->context, kModuleId, render_loop->pointer,
            reinterpret_cast<void*>(&DetourRenderLoop),
            reinterpret_cast<void**>(&g_original_render_loop)) == BE_Result_Ok) {
            g_render_loop_hook_ready = true;
            Log("Successfully installed RenderPipelineManager::DoRenderLoop_Internal pump hook.");
        } else {
            Log("Warning: failed to install the render loop pump hook; the world pause "
                "resume depends on the camera tick and the unscaled time heartbeat.");
        }
    }
    return true;
}

BE_Result BE_CALL Initialize(const BE_HostApiV1* host) {
    if (!host || host->abi_version != BETTER_ENDFIELD_NEXT_MODULE_ABI_V1 ||
        !host->resolve_method || !host->create_hook || !host->runtime_invoke ||
        !host->object_unbox || !host->log) {
        return BE_Result_InvalidArgument;
    }
    g_host = host;
    g_state.store(ModuleState::Resolving, std::memory_order_release);
    if (!ResolveContracts()) {
        g_state.store(ModuleState::ContractMismatch, std::memory_order_release);
        return BE_Result_ContractMismatch;
    }
    if (!InstallHook()) {
        g_state.store(ModuleState::Failed, std::memory_order_release);
        Log("Failed to install camera update hook.");
        return BE_Result_Failed;
    }
    // The MMD/EIEM runtime. The camera file worker and the local music channel
    // are optional: a build without them keeps the free camera and first person.
    try {
        g_camera_files.Start();
    } catch (const std::exception& error) {
        Log(std::string("Camera file worker unavailable: ") + error.what());
    }
    StartMmd();
    StartCharacterMotion();
    g_input_thread_stop.store(false, std::memory_order_release);
    g_input_thread = std::thread(InputThreadMain);
    g_state.store(ModuleState::Ready, std::memory_order_release);
#if defined(__ANDROID__)
    const auto global_ready = [](const char* key) { const auto* c = Contract(key); return c && c->resolved; };
    g_android_global_fov_ready.store(g_push_state_hook_ready && g_state_layout.ready &&
        global_ready("unity.camera.main") && global_ready("unity.camera.orthographic.get") &&
        global_ready("unity.component.game_object") && global_ready("unity.object.op_equality"), std::memory_order_release);
    betterendfieldnext::SetAndroidFrameClient(betterendfieldnext::FrameClient::Camera, &AndroidCameraFrame);
#endif
    Log("BetterEndfieldNext.Camera module initialized successfully.");
    return BE_Result_Ok;
}

BE_Result BE_CALL ConfigurationChanged(const char* raw_configuration) {
    const CameraConfiguration config = ParseConfiguration(raw_configuration);
    // The character motion config is handed over whole, so the MMD director and
    // the EIEM body port read exactly the values this fork parsed.
    PublishCharacterConfiguration(config);
    g_character_play_key.store(config.vmd_motion_key, std::memory_order_release);
    g_character_pause_key.store(config.vmd_motion_pause_key, std::memory_order_release);
    g_character_stop_key.store(config.vmd_motion_stop_key, std::memory_order_release);
    const bool free_camera = config.enabled && config.free_camera_enabled;
    const bool anti_dither = config.enabled && config.disable_dither_enabled;
    const bool first_person = config.enabled && config.first_person_camera_enabled;
    const bool was_free = g_free_camera_enabled.load(std::memory_order_acquire);
    const bool was_first_person = g_first_person_camera_enabled.load(std::memory_order_acquire);

    g_free_camera_enabled.store(free_camera, std::memory_order_release);
    g_disable_dither_enabled.store(anti_dither, std::memory_order_release);
    g_pause_enabled.store(config.pause_enabled, std::memory_order_release);
    g_first_person_camera_enabled.store(first_person, std::memory_order_release);
    g_first_person_hide_head.store(config.first_person_hide_head, std::memory_order_release);
    g_first_person_fill_neck_hole.store(config.first_person_fill_neck_hole,
        std::memory_order_release);
    g_first_person_eye_height.store(config.first_person_eye_height,
        std::memory_order_release);
    g_first_person_eye_forward.store(config.first_person_eye_forward,
        std::memory_order_release);
    g_first_person_near_clip.store(config.first_person_near_clip,
        std::memory_order_release);
    g_first_person_extend_look_range.store(config.first_person_extend_look_range,
        std::memory_order_release);
    g_first_person_look_up_limit.store(std::clamp(config.first_person_look_up_limit,0.0f,89.0f),
        std::memory_order_release);
    g_first_person_look_down_limit.store(std::clamp(config.first_person_look_down_limit,0.0f,89.0f),
        std::memory_order_release);
    g_first_person_movement.store(config.first_person_movement, std::memory_order_relaxed);
    g_first_person_side_look_limit.store(config.first_person_side_look_limit, std::memory_order_relaxed);
    g_first_person_animation_mode.store(config.first_person_animation_mode, std::memory_order_relaxed);
    g_first_person_animation_strength.store(config.first_person_animation_strength, std::memory_order_relaxed);
    g_first_person_yield_dialogue.store(config.first_person_yield_dialogue, std::memory_order_relaxed);
    g_first_person_third_person_in_combat.store(config.first_person_third_person_in_combat, std::memory_order_relaxed);
    g_first_person_transition_seconds.store(config.first_person_transition_seconds, std::memory_order_relaxed);
    g_first_person_external_head_scale.store(config.first_person_external_head_scale, std::memory_order_relaxed);
    g_diagnostics_enabled.store(config.diagnostics, std::memory_order_release);
    g_movement_speed.store(config.movement_speed, std::memory_order_release);
    g_field_of_view.store(config.field_of_view, std::memory_order_release);
    const auto* projection = Contract("unity.camera.orthographic.get");
    const auto* brain_object = Contract("unity.component.game_object");
    g_global_fov_enabled.store(config.enabled && config.global_fov_enabled &&
        g_push_state_hook_ready && g_state_layout.ready &&
        projection && projection->resolved && brain_object && brain_object->resolved,
        std::memory_order_release);
    if (config.enabled && config.global_fov_enabled && !g_global_fov_enabled.load())
        Log("Global FOV unavailable: camera state or projection contract was not resolved.");
    g_global_fov.store(config.global_fov, std::memory_order_release);
    g_free_camera_follow_character.store(config.free_camera_follow_character, std::memory_order_release);
    g_first_person_fov.store(config.first_person_fov, std::memory_order_release);
    g_first_person_neck_plug_scale.store(config.first_person_neck_plug_scale,
        std::memory_order_release);
    g_toggle_key.store(config.toggle_key, std::memory_order_release);
    g_pause_key.store(config.pause_key, std::memory_order_release);
    g_first_person_key.store(config.first_person_key, std::memory_order_release);
    g_mouse_look_enabled.store(config.mouse_look, std::memory_order_release);
    g_mouse_invert_y.store(config.mouse_invert_y, std::memory_order_release);
    g_mouse_sensitivity.store(config.mouse_sensitivity, std::memory_order_release);
    g_first_person_gyro_look.store(config.first_person_gyro_look, std::memory_order_release);
    g_first_person_gyro_horizontal.store(config.first_person_gyro_horizontal, std::memory_order_release);
    g_first_person_gyro_vertical.store(config.first_person_gyro_vertical, std::memory_order_release);
    g_first_person_gyro_invert_horizontal.store(config.first_person_gyro_invert_horizontal, std::memory_order_release);
    g_first_person_gyro_invert_vertical.store(config.first_person_gyro_invert_vertical, std::memory_order_release);
    g_free_smoothing.store(config.smoothing, std::memory_order_release);
    g_motion_preset.store(config.motion_preset, std::memory_order_release);
    g_motion_speed.store(config.motion_speed, std::memory_order_release);
    g_orbit_speed.store(config.orbit_speed, std::memory_order_release);
    g_motion_duration.store(config.motion_duration, std::memory_order_release);
    g_motion_target_height.store(config.motion_target_height, std::memory_order_release);
    g_keyframe_segment_seconds.store(config.keyframe_segment_seconds,
        std::memory_order_release);
    g_keyframe_loop.store(config.keyframe_loop, std::memory_order_release);
    g_vmd_camera_scale.store(config.vmd_camera_scale, std::memory_order_release);
    g_vmd_camera_fov_bias.store(config.vmd_camera_fov_bias, std::memory_order_release);
    g_vmd_camera_loop.store(config.vmd_camera_loop, std::memory_order_release);
    // MMD: the director reads its clock settings, its keys and its sources off
    // these, so a reload has to publish them the same way the camera does.
    g_mmd_enabled.store(config.enabled && config.mmd_enabled, std::memory_order_release);
    g_mmd_overlay_enabled.store(config.mmd_overlay_enabled, std::memory_order_release);
    g_mmd_overlay_initial_visible.store(config.mmd_overlay_visible, std::memory_order_release);
    g_mmd_loop_default.store(config.mmd_loop, std::memory_order_release);
    g_mmd_music_enabled.store(config.mmd_music_enabled, std::memory_order_release);
    g_mmd_seek_seconds.store(config.mmd_seek_seconds, std::memory_order_release);
    g_mmd_music_gain.store(config.mmd_music_gain, std::memory_order_release);
    g_mmd_audio_offset.store(config.mmd_audio_offset, std::memory_order_release);
    g_mmd_play_key.store(config.mmd_play_key, std::memory_order_release);
    g_mmd_stop_key.store(config.mmd_stop_key, std::memory_order_release);
    g_mmd_seek_back_key.store(config.mmd_seek_back_key, std::memory_order_release);
    g_mmd_seek_forward_key.store(config.mmd_seek_forward_key, std::memory_order_release);
    g_mmd_camera_mode_key.store(config.mmd_camera_mode_key, std::memory_order_release);
    g_mmd_overlay_key.store(config.mmd_overlay_key, std::memory_order_release);
    {
        std::lock_guard<std::mutex> lock(g_vmd_path_mutex);
        g_vmd_camera_file = config.vmd_camera_file;
        g_vmd_motion_file = config.vmd_motion_file;
        g_mmd_music_file = config.mmd_music_file;
        g_mmd_face_file = config.mmd_face_file;
        g_keyframe_file = config.keyframe_file;
#if defined(__ANDROID__)
        // The settings app cannot know the game's data path, so an unset camera
        // path falls back to the one the MMD library root implies.
        if (g_keyframe_file.empty()) {
            const char* root = std::getenv("BETTER_ENDFIELD_NEXT_MMD_ROOT");
            if (root && *root) {
                g_keyframe_file = MmdLibrary::ToUtf8(
                    MmdLibrary::FromUtf8(root).parent_path() / "camera-path.becam");
            }
        }
#endif
        if (g_mmd_work != config.mmd_work) {
            g_mmd_work = config.mmd_work;
            g_mmd_work_generation.fetch_add(1, std::memory_order_acq_rel);
        }
        g_asset_config_generation.fetch_add(1, std::memory_order_acq_rel);
    }
    g_roll_left_key.store(config.roll_left_key, std::memory_order_release);
    g_roll_right_key.store(config.roll_right_key, std::memory_order_release);
    g_fov_wide_key.store(config.fov_wide_key, std::memory_order_release);
    g_fov_narrow_key.store(config.fov_narrow_key, std::memory_order_release);
    g_view_reset_key.store(config.view_reset_key, std::memory_order_release);
    g_motion_key.store(config.motion_key, std::memory_order_release);
    g_keyframe_add_key.store(config.keyframe_add_key, std::memory_order_release);
    g_keyframe_play_key.store(config.keyframe_play_key, std::memory_order_release);
    g_keyframe_clear_key.store(config.keyframe_clear_key, std::memory_order_release);
    g_vmd_play_key.store(config.vmd_play_key, std::memory_order_release);

    if (was_free && !free_camera) {
        g_force_exit_request.store(true, std::memory_order_release);
    }
    if (was_first_person && !first_person) {
        g_first_person_exit_request.store(true, std::memory_order_release);
    }

    g_state.store(free_camera || anti_dither || first_person || g_global_fov_enabled.load()
        ? ModuleState::Active
        : ModuleState::Disabled, std::memory_order_release);

    char buffer[384];
    std::snprintf(buffer, sizeof(buffer),
        "Camera configuration applied: enabled=%s, free_camera=%s, first_person=%s, "
        "hide_head=%s, fill_neck_hole=%s, neck_plug_scale=%.2f, anti_dither=%s, "
        "pause_enabled=%s, free_hotkey_vk=%d, fp_hotkey_vk=%d, fp_fov=%.1f",
        config.enabled ? "true" : "false", free_camera ? "true" : "false",
        first_person ? "true" : "false",
        config.first_person_hide_head ? "true" : "false",
        config.first_person_fill_neck_hole ? "true" : "false",
        config.first_person_neck_plug_scale,
        anti_dither ? "true" : "false",
        config.pause_enabled ? "true" : "false", config.toggle_key,
        config.first_person_key, config.first_person_fov);
    Log(buffer);
    std::snprintf(buffer, sizeof(buffer),
        "Free camera extras: mouse_look=%s, sensitivity=%.2f, smoothing=%.2f, "
        "motion_preset=%d, motion_speed=%.2f, orbit_speed=%.1f, keyframe_segment=%.1f, "
        "vmd_file=%s, vmd_scale=%.3f",
        config.mouse_look ? "true" : "false", config.mouse_sensitivity,
        config.smoothing, config.motion_preset, config.motion_speed,
        config.orbit_speed, config.keyframe_segment_seconds,
        config.vmd_camera_file.empty() ? "<none>" : "set", config.vmd_camera_scale);
    Log(buffer);
    return BE_Result_Ok;
}

void BE_CALL Shutdown() {
#if defined(__ANDROID__)
    betterendfieldnext::SetAndroidFrameClient(betterendfieldnext::FrameClient::Camera, nullptr);
    betterendfieldnext::PublishAndroidCameraState(0, 0);
    g_android_global_fov_ready.store(false, std::memory_order_release);
#endif
    g_free_camera_enabled.store(false, std::memory_order_release);
    g_global_fov_enabled.store(false, std::memory_order_release);
    g_disable_dither_enabled.store(false, std::memory_order_release);
    g_first_person_camera_enabled.store(false, std::memory_order_release);
    g_input_thread_stop.store(true, std::memory_order_release);
    if (g_input_thread.joinable()) {
        g_input_thread.join();
    }
    ExitFirstPerson("shutdown",true);
    FpFacingClose();
    g_fp_external_scale.Close();
    FpRefreshHiddenParts(false);
    FpCloseNeckCap();
    FpReleaseShadowHides(true);
    g_fp_sync_readback.Close();
    FpResetPerspective();
    g_fp_retract=FpRetractSampler{};
    ExitFreeCamera("shutdown");
    // The MMD/EIEM half. The pose lease has to be dropped before the file worker
    // that feeds it stops, and the director releases its music channel here too.
    g_character_preview_enabled.store(false, std::memory_order_release);
    StopCharacterMotion();
    StopMmd();
    g_camera_files.Stop();
    ReleaseCameraRoot();
    if (g_host && g_host->release_module_hooks) {
        g_host->release_module_hooks(g_host->context, kModuleId);
    }
    g_push_state_hook_ready = false;
    g_render_loop_hook_ready = false;
    g_original_push_state = nullptr;
    g_original_tail_late_tick = nullptr;
    g_original_camera_tick = nullptr;
    g_original_time_unscaled_delta = nullptr;
    g_original_render_loop = nullptr;
    g_free_camera_active = false;
    g_first_person_active.store(false, std::memory_order_release);
    g_state.store(ModuleState::Stopped, std::memory_order_release);
    g_host = nullptr;
}

const BE_ModuleApiV1 kApi{
    {kModuleId, "Camera Enhancements", "1.6.0", BETTER_ENDFIELD_NEXT_MODULE_ABI_V1},
    &Initialize,
    &ConfigurationChanged,
    &Shutdown};

} // namespace
} // namespace BetterEndfieldNext::CameraModule

#if defined(__ANDROID__)
// ---------------------------------------------------------------------------
// MMD as the host sees it. android_camera.h declares these in the
// `betterendfieldnext` namespace, which is not the camera module's namespace, while
// the module's MMD state lives in the anonymous namespace above. This pair is
// the forwarding bridge between the two; the definitions have to sit in a named
// namespace to be reachable from anywhere else.
// ---------------------------------------------------------------------------
namespace BetterEndfieldNext::CameraModule {
bool QueueMmdCommandForAndroid(unsigned type, int argument, double value,
    const std::string& text) {
    const bool camera_tool = type >= 8 && type <= 14;
    if ((!g_mmd_enabled.load() && !(camera_tool && g_free_camera_enabled.load())) ||
        type == 0 || type > 15 || !std::isfinite(value) ||
        text.size() >= MmdOverlayProtocol::kTextCapacity ||
        (type == 6 && !text.empty() && !MmdLibrary::PlainFolderName(text))) return false;
    MmdOverlayProtocol::Command command{};
    command.type = type;
    command.argument = argument;
    command.value = value;
    const std::size_t length = std::min(text.size(), sizeof(command.text) - 1);
    std::memcpy(command.text, text.data(), length);
    command.text[length] = '\0';
    return QueueMmdCommand(command);
}

std::string MmdStatusForAndroid() {
    std::lock_guard<std::mutex> lock(Mmd::android_mutex);
    return Mmd::android_status;
}

// The camera module's live counters, in the same id=state shape the runtime
// status string uses. It sits next to the MMD bridge for the same reason: the
// state lives in the anonymous namespace above, and this is the named
// namespace the JNI layer can reach.
std::string CameraValuesStatusForAndroid() {
    return "camera.speed=" + std::to_string(g_movement_speed.load()) + "\n" +
        "camera.fov=" + std::to_string(g_field_of_view.load()) + "\n" +
        "camera.global_fov_ready=" + (g_android_global_fov_ready.load(std::memory_order_acquire) ? "1\n" : "0\n") +
        "camera.global_fov_enabled=" + (g_global_fov_enabled.load() ? "1\n" : "0\n") +
        "camera.global_fov=" + std::to_string(g_global_fov.load()) + "\n";
}
}  // namespace BetterEndfieldNext::CameraModule

namespace betterendfieldnext {
bool AndroidGlobalFov(bool enabled, float fov) {
    using namespace BetterEndfieldNext::CameraModule;
    if (!g_android_global_fov_ready.load(std::memory_order_acquire) ||
        !std::isfinite(fov) || fov < 5.0f || fov > 150.0f) return false;
    g_global_fov.store(fov, std::memory_order_release);
    g_global_fov_enabled.store(enabled, std::memory_order_release);
    return true;
}
void AndroidCameraValues(float speed, float fov) {
    using namespace BetterEndfieldNext::CameraModule;
    if (std::isfinite(speed)) g_movement_speed.store(std::clamp(speed, 0.2f, 60.0f));
    if (std::isfinite(fov)) {
        fov = std::clamp(fov, 20.0f, 120.0f);
        g_field_of_view.store(fov);
        g_android_fov_request.store(fov);
    }
}
// The JNI layer may queue directly instead of going through the panel pump; both
// land in the same queue and are executed by Mmd::Pump on the frame thread.
bool AndroidMmdCommand(unsigned type, int argument, double value, const std::string& text) {
    return BetterEndfieldNext::CameraModule::QueueMmdCommandForAndroid(type, argument, value, text);
}

std::string AndroidMmdStatus() {
    return BetterEndfieldNext::CameraModule::MmdStatusForAndroid();
}

std::string AndroidCameraValuesStatus() {
    return BetterEndfieldNext::CameraModule::CameraValuesStatusForAndroid();
}
}  // namespace betterendfieldnext
#endif

BE_EXPORT const BE_ModuleApiV1* BE_CALL BetterEndfieldNext_GetModuleApiV1() {
    return &BetterEndfieldNext::CameraModule::kApi;
}
