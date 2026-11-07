#pragma once
#include "first_person_math.h"

namespace BetterEndfieldNext::FirstPerson {
inline bool SameHeadScale(FirstPersonMath::Vec3 a, FirstPersonMath::Vec3 b) {
    return FirstPersonMath::Finite(a) && FirstPersonMath::Finite(b) &&
        std::abs(a.x-b.x)<1e-7f && std::abs(a.y-b.y)<1e-7f && std::abs(a.z-b.z)<1e-7f;
}
struct HeadScaleLease {
    FirstPersonMath::Vec3 original{}, written{};
    bool owned=false, blocked=false;
    unsigned failures=0;
    bool Acquire(FirstPersonMath::Vec3 value) {
        if (owned || blocked || !FirstPersonMath::Finite(value)) return false;
        for (float component : {value.x,value.y,value.z})
            if (std::abs(component)<1e-5f || std::abs(component)>1000.f) return false;
        original=value; written=value*.001f; owned=true;
        return true;
    }
    bool ShouldRestore(FirstPersonMath::Vec3 current, bool alive) {
        if (!owned) return false;
        if (!alive || !SameHeadScale(current,written)) { *this={}; return false; }
        return !blocked;
    }
    void RestoreResult(bool success) {
        if (success) *this={};
        else if (++failures>=3) blocked=true;
    }
    bool CompleteRestore(bool write_ok, bool read_ok, FirstPersonMath::Vec3 observed) {
        const bool restored=write_ok && read_ok && SameHeadScale(observed,original);
        RestoreResult(restored);
        return restored;
    }
};
} // namespace BetterEndfieldNext::FirstPerson
