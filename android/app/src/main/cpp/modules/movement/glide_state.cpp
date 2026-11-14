#include "glide_state.h"

#include <cmath>

namespace BetterEndfield::Movement {
namespace {

void ClearActiveState(GlideState& state) noexcept {
    state.active = false;
    state.sustained_dash_airborne_origin = false;
    state.manual_reactivation_after_sustained_dash = false;
}

void ClearAllState(GlideState& state) noexcept {
    state = {};
}

bool IsFinite(const Velocity3& velocity) noexcept {
    return std::isfinite(velocity.x) &&
        std::isfinite(velocity.y) &&
        std::isfinite(velocity.z);
}

}  // namespace

bool IsValidGlideDescentSpeed(float speed) noexcept {
    return std::isfinite(speed) &&
        speed >= kMinimumGlideDescentSpeed &&
        speed <= kMaximumGlideDescentSpeed;
}

bool IsValidGlideConfig(const GlideConfig& config) noexcept {
    return IsValidGlideDescentSpeed(config.descent_speed);
}

bool RecordGroundTakeoff(GroundTakeoffState& state,
        std::uintptr_t entity_identity, std::uint64_t player_epoch,
        std::int64_t issued_steady_ns, std::int64_t expires_steady_ns) noexcept {
    state = {};
    if (entity_identity == 0 || player_epoch == 0 || issued_steady_ns <= 0 ||
            expires_steady_ns <= issued_steady_ns) return false;
    state.entity_identity = entity_identity;
    state.player_epoch = player_epoch;
    state.issued_steady_ns = issued_steady_ns;
    state.expires_steady_ns = expires_steady_ns;
    state.pending = true;
    return true;
}

bool AdvanceGroundTakeoff(GroundTakeoffState& state,
        std::uintptr_t entity_identity, std::uint64_t player_epoch,
        std::int64_t now_steady_ns, bool airborne) noexcept {
    if (!state.pending) return false;
    if (entity_identity == 0 || entity_identity != state.entity_identity ||
            player_epoch == 0 || player_epoch != state.player_epoch ||
            now_steady_ns >= state.expires_steady_ns ||
            (!airborne && state.saw_airborne)) {
        state = {};
        return false;
    }
    if (airborne) state.saw_airborne = true;
    return true;
}

GlideTransition StepGlide(GlideState& state, const GlideInput& input) noexcept {
    if (!input.airborne) {
        const bool had_state = state.active || state.attached || state.has_last_press_token || state.has_last_auto_sequence;
        ClearAllState(state);
        return had_state ? GlideTransition::Cleared : GlideTransition::NoChange;
    }

    if (input.player_session == 0 || input.player_epoch == 0) {
        ClearAllState(state);
        return GlideTransition::RejectedSession;
    }

    const bool new_player = !state.attached ||
        state.player_session != input.player_session ||
        state.player_epoch != input.player_epoch;
    if (new_player) {
        ClearAllState(state);
        state.attached = true;
        state.player_session = input.player_session;
        state.player_epoch = input.player_epoch;
    }

    if (!IsValidGlideConfig(input.config)) {
        ClearActiveState(state);
        if (input.has_press_token) {
            state.has_last_press_token = true;
            state.last_press_token = input.press_token;
        }
        return GlideTransition::RejectedConfiguration;
    }

    if (!input.config.enabled) {
        const bool was_active = state.active;
        const bool duplicate = input.has_press_token && state.has_last_press_token &&
            state.last_press_token == input.press_token;
        ClearActiveState(state);
        if (input.has_press_token && !duplicate) {
            state.has_last_press_token = true;
            state.last_press_token = input.press_token;
        }
        if (was_active) return GlideTransition::Cleared;
        if (duplicate) return GlideTransition::DuplicatePress;
        return new_player ? GlideTransition::Cleared : GlideTransition::NoChange;
    }

    if (input.auto_sequence != 0) {
        if (state.has_last_auto_sequence &&
                state.last_auto_sequence == input.auto_sequence)
            return GlideTransition::DuplicatePress;
        state.has_last_auto_sequence = true;
        state.last_auto_sequence = input.auto_sequence;
        if (state.auto_suppressed_until_landing) return GlideTransition::NoChange;
        state.sustained_dash_airborne_origin =
            input.sustained_dash_airborne_source;
        state.manual_reactivation_after_sustained_dash = false;
        if (!state.active) {
            state.active = true;
            return GlideTransition::AutoActivated;
        }
        return GlideTransition::NoChange;
    }

    if (!input.has_press_token) {
        return new_player ? GlideTransition::Cleared : GlideTransition::NoChange;
    }

    if (state.has_last_press_token && state.last_press_token == input.press_token) {
        return GlideTransition::DuplicatePress;
    }
    state.has_last_press_token = true;
    state.last_press_token = input.press_token;

    const bool retracting_sustained_dash = state.active &&
        state.sustained_dash_airborne_origin;
    state.active = !state.active;
    if (!state.active) {
        state.auto_suppressed_until_landing = true;
        state.sustained_dash_airborne_origin = false;
        state.manual_reactivation_after_sustained_dash =
            retracting_sustained_dash ||
            state.manual_reactivation_after_sustained_dash;
    }
    return state.active ? GlideTransition::Activated : GlideTransition::Deactivated;
}

bool ProjectGlideVelocity(
    const GlideState& state,
    const GlideConfig& config,
    bool airborne,
    const Velocity3& input,
    Velocity3& output) noexcept {
    if (!airborne || !state.active || !config.enabled ||
        !IsValidGlideConfig(config) || !IsFinite(input)) {
        return false;
    }

    output = input;
    if (output.y < -config.descent_speed) {
        output.y = -config.descent_speed;
    }
    return true;
}

bool ProjectAutoGlideSteering(const GlideState& state, bool airborne,
        const Velocity3& velocity, const Velocity3& move_direction,
        float delta_time, Velocity3& output) noexcept {
    if (!state.active || !state.sustained_dash_airborne_origin || !airborne ||
            !IsFinite(velocity) || !IsFinite(move_direction) ||
            !std::isfinite(delta_time) || delta_time <= 0.0f ||
            delta_time > 0.1f) return false;
    const float speed = std::hypot(velocity.x, velocity.z);
    const float direction_length = std::hypot(move_direction.x,
        move_direction.z);
    if (!std::isfinite(speed) || speed <= 0.01f ||
            !std::isfinite(direction_length) ||
            direction_length <= 0.1f) return false;
    const float fraction = std::fmin(1.0f, delta_time * 3.0f);
    const float target_x = move_direction.x / direction_length * speed;
    const float target_z = move_direction.z / direction_length * speed;
    const float mixed_x = velocity.x + (target_x - velocity.x) * fraction;
    const float mixed_z = velocity.z + (target_z - velocity.z) * fraction;
    const float mixed_length = std::hypot(mixed_x, mixed_z);
    if (!std::isfinite(mixed_length) || mixed_length <= 0.01f) return false;
    output = velocity;
    output.x = mixed_x / mixed_length * speed;
    output.z = mixed_z / mixed_length * speed;
    return IsFinite(output);
}

bool ProjectManualGlideRecovery(const GlideState& state, bool airborne,
        const Velocity3& velocity, const Velocity3& move_direction,
        float delta_time, Velocity3& output) noexcept {
    if (!state.active || !state.manual_reactivation_after_sustained_dash ||
            !airborne || !IsFinite(velocity) || !IsFinite(move_direction) ||
            !std::isfinite(delta_time) || delta_time <= 0.0f ||
            delta_time > 0.1f)
        return false;
    const float speed = std::hypot(velocity.x, velocity.z);
    const float direction_length = std::hypot(move_direction.x,
        move_direction.z);
    if (!std::isfinite(speed) ||
            !std::isfinite(direction_length) || direction_length <= 0.1f)
        return false;
    output = velocity;
    if (speed < 1.0f) {
        output.x = move_direction.x / direction_length * kManualGlideRecoverySpeed;
        output.z = move_direction.z / direction_length * kManualGlideRecoverySpeed;
    } else {
        const float target_x = move_direction.x / direction_length;
        const float target_z = move_direction.z / direction_length;
        const float cross = velocity.x * target_z - velocity.z * target_x;
        const float dot = velocity.x * target_x + velocity.z * target_z;
        const float angle = dot < 0.0f && std::fabs(cross) < 0.00001f
            ? std::acos(-1.0f) : std::atan2(cross, dot);
        const float max_step = delta_time * 3.0f;
        const float step = std::fmax(-max_step, std::fmin(angle, max_step));
        output.x = velocity.x * std::cos(step) - velocity.z * std::sin(step);
        output.z = velocity.x * std::sin(step) + velocity.z * std::cos(step);
    }
    return IsFinite(output);
}

}  // namespace BetterEndfield::Movement
