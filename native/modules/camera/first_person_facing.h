#pragma once

#include "first_person_math.h"

// Lateral turn interpolation is adapted from RenoDX Endfield Enhancer
// camera_movement.hpp (MIT); attribution is in THIRD_PARTY_NOTICES.md.

namespace BetterEndfieldNext::FirstPerson {
enum class FacingMode { Inactive, Entity, Visual };
struct FacingInput {
    bool active = false;
    float view_yaw = 0;
    FirstPersonMath::Vec3 movement{};
    float side_limit = 60;
    float elapsed = 0;
};
struct FacingState {
    bool attached = false;
    float held_yaw = 0;
    float lateral_yaw = 0;
    float target = 0;
    float turn_start = 0;
    float turn_time = .35f;
    float velocity = 0;
    float start_velocity = 0;
};
struct FacingResult {
    FacingMode mode = FacingMode::Inactive;
    float yaw = 0;
};

inline bool SameOwnedRotation(FirstPersonMath::Quat current, FirstPersonMath::Quat written) {
    if (!FirstPersonMath::Unit(current) || !FirstPersonMath::Unit(written)) return false;
    // A quaternion and its negation encode the same pose. A dot-product
    // threshold admits visible later animation changes and could overwrite
    // another owner's write during conditional restoration.
    const auto components_match = [](FirstPersonMath::Quat a, FirstPersonMath::Quat b) {
        constexpr float tolerance = 2e-5f;
        return std::abs(a.x-b.x) <= tolerance && std::abs(a.y-b.y) <= tolerance &&
            std::abs(a.z-b.z) <= tolerance && std::abs(a.w-b.w) <= tolerance;
    };
    return components_match(current,written) ||
        components_match(current,{-written.x,-written.y,-written.z,-written.w});
}

inline FacingResult StepFacing(FacingState& state, const FacingInput& input) {
    if (!input.active || !std::isfinite(input.view_yaw) ||
        !FirstPersonMath::Finite(input.movement) ||
        !std::isfinite(input.side_limit) || !std::isfinite(input.elapsed)) {
        state = {};
        return {};
    }
    if (!state.attached) state.held_yaw = input.view_yaw;
    const float elapsed = std::clamp(input.elapsed, 0.f, .1f);
    if (std::hypot(input.movement.x, input.movement.z) > .01f) {
        const float target = FirstPersonMath::LateralFacingYaw(input.movement, {0, 0, 1});
        if (std::abs(target - state.target) > .1f) {
            state.turn_start = state.lateral_yaw;
            state.start_velocity = state.velocity;
            state.turn_time = 0;
        }
        state.target = target;
        if (state.turn_time < .35f) {
            state.turn_time = std::min(state.turn_time + elapsed, .35f);
            const float t = state.turn_time / .35f;
            state.lateral_yaw = state.turn_start + (target - state.turn_start) * t * t * (3.f - 2.f * t)
                + .35f * state.start_velocity * t * (1.f - t) * (1.f - t);
            state.velocity = (target - state.turn_start) * (6.f * t * (1.f - t) / .35f)
                + state.start_velocity * (1.f - 4.f * t + 3.f * t * t);
        } else {
            state.lateral_yaw += (target - state.lateral_yaw) * (-std::expm1(-16.f * elapsed));
            state.velocity = 16.f * (target - state.lateral_yaw);
        }
        state.held_yaw = input.view_yaw + state.lateral_yaw;
        state.attached = true;
        return {FacingMode::Visual, state.held_yaw};
    }
    const float limit = std::clamp(input.side_limit, 0.f, 90.f);
    // Standing still is the one state where the body must not outlive the
    // strafe that produced its offset.
    //
    // The walk branch writes held_yaw = view_yaw + lateral_yaw, so lateral_yaw
    // is measured from the view. This branch used to clamp that offset against
    // view_yaw but never write the clamped value back into lateral_yaw; it only
    // derived lateral_yaw from held_yaw and reset target to 0. The offset then
    // sat still in the state while the view kept moving, so on a device the body
    // froze on the absolute world yaw the walk happened to end on and stopped
    // following the view at all -- the character turned to a fixed direction and
    // stayed there. Restoring target = 0 on the next walk then eased the body
    // back from that frozen yaw, which is the visible snap.
    //
    // Keeping the offset view-relative and easing it out closes both: the body
    // holds the pose the walk ended in and then unwinds to the view over the
    // same 0.35s the walk used, so the next step starts from zero and has
    // nothing to snap away from.
    //
    // The rate is 1/0.35 to match the walk's own turn_time. Reusing the walk
    // branch's 16/s steady-state tail was wrong here: at 60Hz that removes
    // 34.8 of a 45 degree offset in the first 16ms frame, which is a snap in
    // the opposite direction from the one being fixed.
    //
    // The release starts from lateral_yaw itself. state.target still holds the
    // strafe target the walk branch was easing toward (45 for a strafe), so
    // treating it as this branch's origin would subtract the offset away
    // instead of decaying it.
    constexpr float kReleaseSeconds = .35f;
    state.lateral_yaw = std::remainder(state.lateral_yaw, 360.f);
    state.lateral_yaw = std::clamp(state.lateral_yaw, -limit, limit);
    state.lateral_yaw *= 1.f - -std::expm1(-elapsed / kReleaseSeconds);
    state.velocity = state.start_velocity = 0;
    state.target = 0;
    state.turn_time = .35f;
    state.held_yaw = input.view_yaw + state.lateral_yaw;
    state.attached = true;
    return {FacingMode::Entity, state.held_yaw};
}
} // namespace BetterEndfieldNext::FirstPerson
