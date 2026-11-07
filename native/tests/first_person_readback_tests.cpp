#include "../modules/camera/first_person_readback.h"
#include <iostream>
using namespace BetterEndfieldNext::FirstPerson;
int main() {
    auto plan = PlanReadback(100, 4, 40);
    if (plan.error != ReadbackError::None || plan.buffer_bytes != 400 || plan.read_bytes != 40) {
        std::cerr << "copy plan must retain the whole source size for a prefix read\n"; return 1;
    }
    if (PlanReadback(10,4,44).error != ReadbackError::OutOfRange ||
        PlanReadback(-1,4,4).error != ReadbackError::InvalidSize ||
        PlanReadback(1,4,0).error != ReadbackError::InvalidSize ||
        PlanReadback(0x7fffffff,0x7fffffff,4).error != ReadbackError::BudgetExceeded) {
        std::cerr << "invalid and over-budget buffers must fail before allocation\n"; return 1;
    }
    std::cout << "first_person_readback: full-size staging, prefix read and allocation bounds passed\n";
}
