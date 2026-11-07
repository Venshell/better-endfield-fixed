#include "../modules/camera/first_person_motion.h"
#include <iostream>
#include <stdexcept>

using namespace BetterEndfieldNext::FirstPerson;
namespace Math = BetterEndfieldNext::FirstPersonMath;
void Check(bool value, const char* message) { if (!value) throw std::runtime_error(message); }
bool Same(Math::Quat a, Math::Quat b) {
    return std::abs(a.x*b.x+a.y*b.y+a.z*b.z+a.w*b.w) > .99999f;
}
int main() {
    try {
        const Math::Quat identity{0,0,0,1};
        const auto roll = Math::AxisAngle({0,0,1}, 30);
        Math::Quat result{};
        Check(AnimationTarget(AnimationMode::Head, roll, 0, 1, result) && Same(result, identity),
            "Head mode must discard head roll");
        Check(AnimationTarget(AnimationMode::Realistic, roll, 0, 1, result) && Same(result, roll),
            "Realistic mode must preserve head roll");
        const auto pitch = Math::AxisAngle({1,0,0}, 25);
        Check(AnimationTarget(AnimationMode::Body, pitch, 0, 1, result) && Same(result, identity),
            "Body mode must discard pitch");
        Check(AnimationTarget(AnimationMode::Head, pitch, 0, 1, result) && Same(result, pitch),
            "Head mode must preserve pitch");
        const auto yaw = Math::AxisAngle({0,1,0}, 70);
        Check(AnimationTarget(AnimationMode::Realistic, yaw, 70, 1, result) && Same(result, identity),
            "reference body yaw must not be applied to camera twice");
        Check(AnimationTarget(AnimationMode::Realistic, roll, 0, 0, result) && Same(result, identity),
            "zero motion strength must preserve control view");
        Check(!AnimationTarget(AnimationMode::Head, {0,0,0,0}, 0, 1, result),
            "invalid bone rotation must be rejected");
        std::cout << "first_person_motion: modes, roll, pitch, reference yaw, strength and invalid bone passed\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n'; return 1;
    }
}
