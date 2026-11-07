#include "../modules/camera/first_person_facing.h"
#include <cmath>
#include <iostream>
#include <limits>
#include <stdexcept>

using namespace BetterEndfieldNext::FirstPerson;
void Check(bool value, const char* message) {
    if (!value) throw std::runtime_error(message);
}
bool Near(float a, float b) { return std::abs(a - b) < .001f; }
// held_yaw is written as view_yaw + lateral_yaw, so the body's *offset from the
// view* is the only meaningful quantity while standing: an assertion on the
// absolute yaw would only pass for a view of zero and would silently encode the
// frozen-in-world-space behaviour this state machine must not have.
float Offset(const FacingResult& result, float view_yaw) {
    return std::remainder(result.yaw - view_yaw, 360.f);
}
int main() {
    try {
        FacingState state;
        StepFacing(state, {true, 0, {0, 0, 0}, 60, .016f});
        auto result = StepFacing(state, {true, 50, {0, 0, 0}, 60, .016f});
        Check(result.mode == FacingMode::Entity && Near(Offset(result, 50), 0),
            "standing side look within the limit must keep body yaw on the view");
        result = StepFacing(state, {true, 80, {0, 0, 0}, 60, .016f});
        Check(Near(Offset(result, 80), 0),
            "standing side look must clamp the body to the limit, not to the raw view");
        Check(Near(result.yaw, 80),
            "a body inside the limit must track the view rather than freeze in world space");
        state = {};
        for (int frame = 0; frame < 8; ++frame)
            result = StepFacing(state, {true, 0, {1, 0, 0}, 60, .1f});
        Check(result.mode == FacingMode::Visual && Near(result.yaw, 45),
            "right strafe must converge to 45 degrees");
        for (int frame = 0; frame < 8; ++frame)
            result = StepFacing(state, {true, 0, {-1, 0, 0}, 60, .1f});
        Check(Near(result.yaw, -45), "strafe reversal must converge to -45 degrees");
        for (int frame = 0; frame < 8; ++frame)
            result = StepFacing(state, {true, 0, {0, 0, -1}, 60, .1f});
        Check(Near(result.yaw, 0), "pure backpedal must face the view");
        state = {};
        StepFacing(state, {true, 179, {}, 60, .016f});
        result = StepFacing(state, {true, -179, {}, 60, .016f});
        // The assertion belongs on the body's offset from the *current* view:
        // 179 -> -179 is a 2 degree step across the wrap, and a body with no
        // strafe offset has to land on -179 rather than lagging on the stale
        // 179-relative anchor.
        Check(Near(Offset(result, -179), 0) && Near(result.yaw, -179),
            "crossing the yaw wrap must not spin the body");
        FacingState slow, stalled;
        const auto ordinary = StepFacing(slow, {true, 0, {1, 0, 0}, 60, .1f});
        const auto bounded = StepFacing(stalled, {true, 0, {1, 0, 0}, 60, 30.f});
        Check(Near(ordinary.yaw, bounded.yaw), "a stalled frame must use the 100ms bound");
        result = StepFacing(state, {true, std::numeric_limits<float>::quiet_NaN(), {}, 60, .01f});
        Check(result.mode == FacingMode::Inactive && !state.attached,
            "invalid input must release the facing session");
        StepFacing(state, {true, 0, {1, 0, 0}, 60, .1f});
        result = StepFacing(state, {false, 0, {}, 60, .01f});
        Check(result.mode == FacingMode::Inactive && !state.attached,
            "disabled facing must release its state");

        // Stopping after a strafe must not freeze the body on the world yaw the
        // walk ended on: the offset has to stay measured from the view so the
        // character keeps following the camera while standing.
        {
            FacingState stopped;
            FacingResult held{};
            float view = 0;
            for (int frame = 0; frame < 8; ++frame)
                held = StepFacing(stopped, {true, view += 10.f, {1, 0, 0}, 60, .1f});
            Check(held.mode == FacingMode::Visual && Near(Offset(held, view), 45),
                "a right strafe must carry a +45 offset relative to the view");
            for (int frame = 0; frame < 8; ++frame)
                held = StepFacing(stopped, {true, view += 5.f, {0, 0, 0}, 60, .1f});
            Check(held.mode == FacingMode::Entity,
                "standing after a strafe must report the entity mode");
            // The decay is asymptotic, so the assertion is that the offset
            // shrinks toward zero and the body keeps following the view rather
            // than sitting on one absolute world yaw.
            float previous = std::abs(Offset(held, view));
            Check(previous < 45.f && previous > 0.f,
                "standing must start decaying the strafe offset");
            for (int frame = 0; frame < 20; ++frame) {
                held = StepFacing(stopped, {true, view, {0, 0, 0}, 60, .1f});
                const float offset = std::abs(Offset(held, view));
                Check(offset <= previous, "the strafe offset must decay monotonically");
                previous = offset;
            }
            Check(previous < 1.f, "the strafe offset must decay to the view while standing");
            // Asymptotic decay leaves a sub-0.01 degree residue; the tolerance
            // reflects what the display can show rather than exact equality.
            Check(std::abs(Offset(held, view)) < .05f,
                "a settled stance must sit on the view yaw");
            // The offset has to be spent before the next step, otherwise the
            // walk branch restarts its ease from the leftover and the body
            // visibly snaps as the player starts moving again.
            const auto resumed = StepFacing(stopped, {true, view, {1, 0, 0}, 60, .1f});
            Check(std::abs(Offset(resumed, view)) < 20,
                "resuming a walk must not snap the body from a stale offset");
        }
        {
            // The offset a strafe leaves is spent over ~0.35s of standing, so a
            // stationary frame immediately after the walk still shows the pose.
            FacingState pose;
            FacingResult held{};
            for (int frame = 0; frame < 8; ++frame)
                held = StepFacing(pose, {true, 0, {1, 0, 0}, 60, .1f});
            const float walking = held.yaw;
            held = StepFacing(pose, {true, 0, {0, 0, 0}, 60, .016f});
            Check(held.yaw < walking && held.yaw > 30,
                "the first standing frame must hold the strafe pose without snapping");
        }
        const BetterEndfieldNext::FirstPersonMath::Quat identity{0, 0, 0, 1};
        const auto external_change = BetterEndfieldNext::FirstPersonMath::AxisAngle({0, 1, 0}, .2f);
        Check(SameOwnedRotation(identity, {-identity.x, -identity.y, -identity.z, -identity.w}),
            "equivalent quaternion signs must preserve ownership");
        Check(!SameOwnedRotation(identity, external_change),
            "even a small later animation rotation must prevent our restore");
        std::cout << "first_person_facing: thresholds, strafe, reversal, backpedal, wrap, "
                     "time bounds, stop-and-release and release passed\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
