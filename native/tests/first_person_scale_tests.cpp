#include "../modules/camera/first_person_scale.h"
#include <iostream>
using namespace BetterEndfieldNext::FirstPerson;
int main() {
    HeadScaleLease lease;
    if (!lease.Acquire({2,-3,4}) || !SameHeadScale(lease.written,{.002f,-.003f,.004f})) return 1;
    if (!lease.ShouldRestore(lease.written,true) || !SameHeadScale(lease.original,{2,-3,4})) return 2;
    lease.RestoreResult(false);lease.RestoreResult(false);lease.RestoreResult(false);
    if (!lease.blocked || !lease.owned || !SameHeadScale(lease.original,{2,-3,4})) return 3;
    HeadScaleLease silent;
    if (!silent.Acquire({2,-3,4}) ||
        silent.CompleteRestore(true,true,silent.written) || !silent.owned || silent.failures!=1 ||
        !silent.CompleteRestore(true,true,silent.original) || silent.owned) return 7;
    HeadScaleLease unreadable;
    if (!unreadable.Acquire({2,-3,4}) ||
        unreadable.CompleteRestore(true,false,unreadable.original) || !unreadable.owned) return 8;
    HeadScaleLease external;
    external.Acquire({1,1,1});
    if (external.ShouldRestore({.5f,.5f,.5f},true) || external.owned) return 4;
    HeadScaleLease destroyed;
    destroyed.Acquire({1,1,1});
    if (destroyed.ShouldRestore(destroyed.written,false) || destroyed.owned) return 5;
    HeadScaleLease invalid;
    if (invalid.Acquire({0,1,1})) return 6;
    std::cout<<"first_person_scale: nonuniform scale, verified restore, failure budget, external ownership and destruction passed\n";
}
