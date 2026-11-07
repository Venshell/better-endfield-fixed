#include "../modules/custom_model/mod_registry.h"
#include <iostream>

using namespace BetterEndfieldNext::CustomModel;

int main() {
    ModRegistry active;
    active.hot_switch = true;
    active.diagnostics.push_back("active marker");
    std::string error;
    if (ParseModRegistry("[CustomModel]\nhot_switch=true\nhot_switch=false\n", {}, active, error) ||
        error.empty() || !active.hot_switch || active.diagnostics != std::vector<std::string>{"active marker"}) {
        std::cerr << "Malformed input changed the active registry\n";
        return 1;
    }
    if (!ParseModRegistry("[CustomModel]\nhot_switch=false\n", {}, active, error) ||
        !error.empty() || active.hot_switch || !active.diagnostics.empty()) {
        std::cerr << "Valid input did not publish the complete candidate\n";
        return 1;
    }
    std::cout << "PASS shared registry preserves active output on failure and atomically publishes a valid candidate\n";
}
