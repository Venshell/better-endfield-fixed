#include "../../shared/hooks/hook_chain.h"

#include <cassert>
#include <string>
#include <unordered_map>

using BetterEndfieldNext::Hooks::Backend;
using BetterEndfieldNext::Hooks::Chain;

namespace {

using Function = int (*)(int);
Function next_first = nullptr;
Function next_second = nullptr;
std::string calls;

int Original(int value) { return value; }
int First(int value) {
    calls += 'A';
    return next_first(value) + 10;
}
int Second(int value) {
    calls += 'B';
    return next_second(value) + 100;
}

} // namespace

int main() {
    std::unordered_map<void*, void*> entries;
    int prepares = 0;
    int enables = 0;
    Chain chain(Backend{
        [&](void* target, void* entry, void** original) {
            ++prepares;
            entries[target] = entry;
            *original = target;
            return true;
        },
        [&](void*) { ++enables; return true; },
        [&](void* target) { entries.erase(target); },
        [&](void*) {}});

    const auto target = reinterpret_cast<void*>(&Original);
    uint64_t first_handle = 0;
    uint64_t second_handle = 0;
    assert(chain.Create("first", target, reinterpret_cast<void*>(&First),
        reinterpret_cast<void**>(&next_first), &first_handle,
        Chain::Duplicate::Reject) == BE_Result_Ok);
    assert(chain.Create("second", target, reinterpret_cast<void*>(&Second),
        reinterpret_cast<void**>(&next_second), &second_handle,
        Chain::Duplicate::Reject) == BE_Result_Ok);
    assert(prepares == 1 && enables == 1);
    assert((chain.Modules(target) == std::vector<std::string>{"first", "second"}));
    auto entry = reinterpret_cast<Function>(entries.at(target));
    assert(entry(5) == 115 && calls == "AB");

    // A duplicate must leave the live caller's forwarding entry untouched.
    Function existing_next = next_first;
    uint64_t duplicate_handle = 99;
    assert(chain.Create("first", target, reinterpret_cast<void*>(&First),
        reinterpret_cast<void**>(&next_first), &duplicate_handle,
        Chain::Duplicate::Reject) == BE_Result_Conflict);
    assert(next_first == existing_next && duplicate_handle == 99);

    calls.clear();
    assert(chain.Disable(first_handle) == BE_Result_Ok);
    assert(entry(5) == 105 && calls == "B");
    assert((chain.Modules(target) == std::vector<std::string>{"second"}));
    assert(chain.Disable(first_handle) == BE_Result_Ok);

    calls.clear();
    assert(chain.DisableModule("second") == BE_Result_Ok);
    assert(entry(5) == 5 && calls.empty());
    assert(chain.Modules(target).empty());
    assert(chain.TargetOf(second_handle) == target);
}
