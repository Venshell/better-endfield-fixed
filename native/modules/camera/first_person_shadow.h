#pragma once

namespace BetterEndfieldNext::FirstPerson {
struct ShadowModeLease {
    enum class Refresh { Keep, Reassert, Relinquish, Unavailable };
    enum class Restore { Done, Write, Retry };
    static constexpr int kShadowsOnly = 3;
    int original = -1;
    unsigned failures = 0;
    bool owned = false, relinquished = false, blocked = false;

    bool Acquire(int current) {
        if (owned || relinquished || current < 0 || current == kShadowsOnly) return false;
        original = current; owned = true;
        return true;
    }
    Refresh Observe(int current) {
        if (!owned || relinquished) return Refresh::Relinquish;
        if (current < 0) return Refresh::Unavailable;
        if (current == kShadowsOnly) return Refresh::Keep;
        if (current == original) return Refresh::Reassert;
        owned = false; relinquished = true;
        return Refresh::Relinquish;
    }
    Restore PlanRestore(int current, bool alive) {
        if (!owned || !alive) { owned = false; return Restore::Done; }
        if (current < 0 || blocked) return Restore::Retry;
        if (current != kShadowsOnly) { owned = false; return Restore::Done; }
        return Restore::Write;
    }
    void RestoreResult(bool success) {
        if (success) { owned = false; failures = 0; }
        else if (++failures >= 3) blocked = true;
    }
};
} // namespace BetterEndfieldNext::FirstPerson
