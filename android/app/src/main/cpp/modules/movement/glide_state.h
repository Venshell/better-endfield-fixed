#pragma once

#include <cstdint>

namespace BetterEndfield::Movement {

constexpr float kDefaultGlideDescentSpeed = 5.0f;
constexpr float kMinimumGlideDescentSpeed = 2.0f;
constexpr float kMaximumGlideDescentSpeed = 15.0f;
// Observed ordinary movement speed in the same PJX110 airborne trace.
constexpr float kManualGlideRecoverySpeed = 6.5f;

struct GlideConfig {
    bool enabled = false;
    float descent_speed = kDefaultGlideDescentSpeed;
};

struct GlideState {
    bool active = false;
    bool attached = false;
    std::uint64_t player_session = 0;
    std::uint64_t player_epoch = 0;
    bool has_last_press_token = false;
    std::uint64_t last_press_token = 0;
    bool has_last_auto_sequence = false;
    std::uint64_t last_auto_sequence = 0;
    bool auto_suppressed_until_landing = false;
    bool sustained_dash_airborne_origin = false;
    bool manual_reactivation_after_sustained_dash = false;
};

// Short-lived fact that PlayerController.Jump was invoked while grounded.
// This survives ordinary grounded FixedTick clearing until takeoff is observed.
struct GroundTakeoffState {
    std::uintptr_t entity_identity = 0;
    std::uint64_t player_epoch = 0;
    std::int64_t issued_steady_ns = 0;
    std::int64_t expires_steady_ns = 0;
    bool pending = false;
    bool saw_airborne = false;
};

bool RecordGroundTakeoff(GroundTakeoffState& state,
    std::uintptr_t entity_identity, std::uint64_t player_epoch,
    std::int64_t issued_steady_ns, std::int64_t expires_steady_ns) noexcept;
bool AdvanceGroundTakeoff(GroundTakeoffState& state,
    std::uintptr_t entity_identity, std::uint64_t player_epoch,
    std::int64_t now_steady_ns, bool airborne) noexcept;

struct GlideInput {
    bool has_press_token = false;
    std::uint64_t press_token = 0;
    std::uint64_t auto_sequence = 0;
    bool sustained_dash_airborne_source = false;
    std::uint64_t player_session = 0;
    std::uint64_t player_epoch = 0;
    bool airborne = false;
    GlideConfig config{};
};

enum class GlideTransition {
    NoChange,
    Activated,
    Deactivated,
    AutoActivated,
    Cleared,
    DuplicatePress,
    RejectedSession,
    RejectedConfiguration,
};

struct Velocity3 {
    float x = 0.0f;
    float y = 0.0f;
    float z = 0.0f;
};

// Advances pure glide state from an already-validated press event and player
// identity. A transition never consumes a first-jump event; callers only pass
// the independent press token after verifying it belongs to the glide action.
GlideTransition StepGlide(GlideState& state, const GlideInput& input) noexcept;

// Projects only the vertical component. On failure, output is left untouched.
bool ProjectGlideVelocity(
    const GlideState& state,
    const GlideConfig& config,
    bool airborne,
    const Velocity3& input,
    Velocity3& output) noexcept;

// Turns the existing horizontal momentum toward the game's world-space move
// vector during an automatically opened airborne dash. It never adds speed.
bool ProjectAutoGlideSteering(const GlideState& state, bool airborne,
    const Velocity3& velocity, const Velocity3& move_direction,
    float delta_time, Velocity3& output) noexcept;

// After dash glide is manually re-opened as ordinary glide, directional input
// restores near-zero horizontal motion or turns existing momentum at fixed speed.
bool ProjectManualGlideRecovery(const GlideState& state, bool airborne,
    const Velocity3& velocity, const Velocity3& move_direction,
    float delta_time, Velocity3& output) noexcept;

bool IsValidGlideDescentSpeed(float speed) noexcept;
bool IsValidGlideConfig(const GlideConfig& config) noexcept;

}  // namespace BetterEndfield::Movement
