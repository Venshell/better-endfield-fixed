#pragma once

#include <vector>

namespace BetterEndfieldNext::FirstPerson {

// A renderer can be hidden whole only when every bone belongs to the head.
// Mixed head/body palettes remain visible for the existing mesh-patch path.
template <class Parent>
bool IsUnderHead(void* bone, void* head, Parent&& parent) {
    if (!bone || !head) return false;
    for (int depth = 0; bone && depth < 32; ++depth) {
        if (bone == head) return true;
        bone = parent(bone);
    }
    return false;
}

template <class Parent>
bool AllBonesUnderHead(const std::vector<void*>& bones, void* head, Parent&& parent) {
    if (bones.empty() || !head) return false;
    for (void* bone : bones) {
        if (!IsUnderHead(bone, head, parent)) return false;
    }
    return true;
}

} // namespace BetterEndfieldNext::FirstPerson
