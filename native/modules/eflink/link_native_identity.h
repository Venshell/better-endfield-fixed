// Ported from EF Link (AGPL-3.0).
// Upstream: https://github.com/Venshell/EFLink @ 956a0d5d4b900f17ba06d7b30bc15d24770eb3a1 (1.6.6),
// fork of https://github.com/guchang233/EFLink.
// Wire layout is unchanged. Platform calls live in eflink_platform.h.
#pragma once
#include <cstdint>

namespace eflink {
// A GC handle preserves a managed address, not the lifetime of a pooled Entity.
// Compare the allocation UID as well as its server identity before any mutation.
struct NativeIdentity {
    std::uint32_t instance=0,generation=0;
    std::uint64_t server=0;
    bool captured=false;
    bool matches(const NativeIdentity& current) const {
        return captured&&current.captured&&instance==current.instance&&
            generation==current.generation&&server==current.server;
    }
};
}
