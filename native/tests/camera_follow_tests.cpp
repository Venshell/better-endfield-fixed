#include "../modules/camera/camera_follow.h"
#include <cassert>
#include <cmath>
#include <cstdio>

struct Point { float x = 0, y = 0, z = 0; };

int main() {
    BetterEndfieldNext::CameraFollow::Anchor<Point> anchor;
    Point delta{};
    assert(!anchor.Sample(true, true, 1, 10, true, {1, 2, 3}, delta));
    assert(anchor.Sample(true, true, 1, 10, true, {3, 5, 7}, delta));
    assert(delta.x == 2 && delta.y == 3 && delta.z == 4);
    assert(!anchor.Sample(true, true, 2, 20, true, {50, 50, 50}, delta));
    assert(anchor.Sample(true, true, 2, 20, true, {51, 50, 50}, delta));
    assert(delta.x == 1);
    assert(!anchor.Sample(true, true, 2, 20, true, {200, 50, 50}, delta));
    assert(anchor.Sample(true, true, 2, 20, true, {201, 50, 50}, delta));
    assert(delta.x == 1);
    assert(!anchor.Sample(false, true, 2, 20, true, {201, 50, 50}, delta));
    assert(!anchor.Sample(true, true, 2, 20, true, {201, 50, 50}, delta));
    assert(!anchor.Sample(true, true, 2, 20, true,
        {std::nanf(""), 0, 0}, delta));
    std::puts("PASS: free camera follow anchor");
}
