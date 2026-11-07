#pragma once
#include "first_person_math.h"

// Rotation decomposition follows camera_motion.hpp from RenoDX Endfield
// Enhancer (MIT). Platform sampling and session ownership remain local.
namespace BetterEndfieldNext::FirstPerson {
enum class AnimationMode { Off, Body, Head, Realistic };

inline bool AnimationTarget(AnimationMode mode, FirstPersonMath::Quat facing,
    float reference_yaw, float strength, FirstPersonMath::Quat& result) {
    using namespace FirstPersonMath;
    result = {0, 0, 0, 1};
    if (!Unit(facing) || !std::isfinite(reference_yaw) || !std::isfinite(strength)) return false;
    if (mode == AnimationMode::Off || strength <= 0) return true;
    facing = AxisAngle({0, 1, 0}, -reference_yaw) * facing;
    if (mode != AnimationMode::Realistic) {
        const Vec3 forward = Rotate(facing, {0, 0, 1});
        const float yaw = std::atan2(forward.x, forward.z) * 57.295779513f;
        const float pitch = mode == AnimationMode::Head
            ? -std::atan2(forward.y, std::hypot(forward.x, forward.z)) * 57.295779513f : 0;
        facing = AxisAngle({0, 1, 0}, yaw) * AxisAngle({1, 0, 0}, pitch);
    }
    result = BlendRotation(result, facing, strength);
    return Unit(result);
}
} // namespace BetterEndfieldNext::FirstPerson
