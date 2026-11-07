#include "../../../android/app/src/main/cpp/core/hook_broker.h"

#include <cassert>
#include <string>

namespace {

using Function = int (*)(int);
void* prepared_entry = nullptr;
void* active_entry = nullptr;
int prepare_count = 0;
int commit_count = 0;
std::string calls;
Function next_one = nullptr;
Function next_two = nullptr;

int Original(int value) { return value; }
int One(int value) { calls += '1'; return next_one(value) + 1; }
int Two(int value) { calls += '2'; return next_two(value) + 10; }

} // namespace

extern "C" int DobbyPrepare(void* target, void* entry, void** original) {
    assert(target == reinterpret_cast<void*>(&Original));
    ++prepare_count;
    prepared_entry = entry;
    *original = target;
    return 0;
}

extern "C" int DobbyCommit(void* target) {
    assert(target == reinterpret_cast<void*>(&Original));
    ++commit_count;
    active_entry = prepared_entry;
    return 0;
}

extern "C" int DobbyDestroy(void*) { return 0; }

namespace betterendfieldnext {
void LogInfo(const char*, const char*) {}
void LogError(const char*, const char*) { assert(false); }
} // namespace betterendfieldnext

int main() {
    betterendfieldnext::HookBroker first;
    betterendfieldnext::HookBroker second;
    std::string error;
    assert(first.Initialize(error));
    assert(second.Initialize(error));
    void* first_stub = nullptr;
    void* second_stub = nullptr;
    assert(first.Install("module-one", reinterpret_cast<void*>(&Original),
        reinterpret_cast<void*>(&One), reinterpret_cast<void**>(&next_one),
        first_stub, error));
    assert(second.Install("module-two", reinterpret_cast<void*>(&Original),
        reinterpret_cast<void*>(&Two), reinterpret_cast<void**>(&next_two),
        second_stub, error));
    assert(prepare_count == 1 && commit_count == 1);
    auto entry = reinterpret_cast<Function>(active_entry);
    assert(entry(5) == 16 && calls == "12");

    void* duplicate_stub = nullptr;
    auto original_next = next_one;
    assert(!second.Install("module-one", reinterpret_cast<void*>(&Original),
        reinterpret_cast<void*>(&Two), reinterpret_cast<void**>(&next_one),
        duplicate_stub, error));
    assert(duplicate_stub == nullptr && next_one == original_next);

    calls.clear();
    assert(first.Remove(first_stub));
    assert(first_stub == nullptr && entry(5) == 15 && calls == "2");
    assert(!first.Remove(second_stub));
    assert(second_stub != nullptr);
    calls.clear();
    assert(second.Remove(second_stub));
    assert(second_stub == nullptr && entry(5) == 5 && calls.empty());
}
