#pragma once
#include "first_person_math.h"

namespace BetterEndfieldNext::FirstPerson {
struct PerspectivePose {
    FirstPersonMath::Vec3 position{};
    FirstPersonMath::Quat rotation{0,0,0,1};
    float fov = 60, near_clip = .03f;
};
struct TransitionState {
    bool initialized = false, first_person = false, blending = false;
    float elapsed = 0;
    PerspectivePose origin{}, last{};
};
inline bool ValidPose(const PerspectivePose& pose) {
    return FirstPersonMath::Finite(pose.position) && FirstPersonMath::Unit(pose.rotation)
        && std::isfinite(pose.fov) && pose.fov>0 && pose.fov<180
        && std::isfinite(pose.near_clip) && pose.near_clip>0;
}
inline PerspectivePose StepTransition(TransitionState& state, const PerspectivePose& target,
    bool first_person, float elapsed, float duration) {
    if (!ValidPose(target)) { state={}; return target; }
    duration = std::isfinite(duration) ? std::clamp(duration,0.f,1.f) : 0.f;
    elapsed = std::isfinite(elapsed) ? std::clamp(elapsed,0.f,.1f) : 0.f;
    if (!state.initialized) {
        state.initialized=true; state.first_person=first_person; state.last=target;
        return target;
    }
    if (first_person != state.first_person) {
        state.origin=state.last; state.elapsed=0; state.first_person=first_person;
        state.blending=duration>0;
    } else if (state.blending) state.elapsed=std::min(state.elapsed+elapsed,duration);
    if (!state.blending || duration==0 || state.elapsed>=duration) {
        state.blending=false; state.last=target; return target;
    }
    const float t=state.elapsed/duration;
    const float alpha=t*t*(3.f-2.f*t);
    state.last.position=state.origin.position+(target.position-state.origin.position)*alpha;
    state.last.rotation=FirstPersonMath::BlendRotation(state.origin.rotation,target.rotation,alpha);
    state.last.fov=state.origin.fov+(target.fov-state.origin.fov)*alpha;
    state.last.near_clip=state.origin.near_clip+(target.near_clip-state.origin.near_clip)*alpha;
    return state.last;
}
} // namespace BetterEndfieldNext::FirstPerson
