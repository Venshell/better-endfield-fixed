#include "../modules/camera/first_person_head_attachment.h"

#include <iostream>
#include <unordered_map>
#include <vector>

using BetterEndfieldNext::FirstPerson::AllBonesUnderHead;
using BetterEndfieldNext::FirstPerson::IsUnderHead;

int main() {
    int root, torso, neck, head, hat, ribbon, body_mesh, cycle;
    const std::unordered_map<void*, void*> parents{
        {&torso, &root}, {&neck, &torso}, {&head, &neck},
        {&hat, &head}, {&ribbon, &hat}, {&body_mesh, &torso},
        {&cycle, &cycle}};
    const auto parent = [&](void* node) -> void* {
        const auto found = parents.find(node);
        return found == parents.end() ? nullptr : found->second;
    };

    if (!IsUnderHead(&ribbon, &head, parent) ||
        IsUnderHead(&body_mesh, &head, parent) ||
        IsUnderHead(&cycle, &head, parent) ||
        IsUnderHead(nullptr, &head, parent)) return 1;
    if (!AllBonesUnderHead(std::vector<void*>{&head, &hat, &ribbon}, &head, parent) ||
        AllBonesUnderHead(std::vector<void*>{&hat, &torso}, &head, parent) ||
        AllBonesUnderHead(std::vector<void*>{&hat, nullptr}, &head, parent) ||
        AllBonesUnderHead(std::vector<void*>{}, &head, parent)) return 2;
    std::cout << "first_person_head_attachment: head-only, mixed body, empty and cyclic palettes passed\n";
}
