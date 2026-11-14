#pragma once

#include <cstdint>

namespace BetterEndfield::Movement {

enum class AutoGlideSource : std::uint8_t {
    GroundJump,
    SustainedDashAirborne,
};

constexpr bool CanConsumeAutoGlideSource(AutoGlideSource source,
        bool grounded_jump_seen, bool airborne) noexcept {
    return airborne && (source == AutoGlideSource::SustainedDashAirborne ||
        (source == AutoGlideSource::GroundJump && grounded_jump_seen));
}

// One bounded handoff slot shared by the action and movement modules. The
// identity is an opaque token only; the bridge never retains or dereferences it.
struct AutoGlideEvent {
    std::uintptr_t entity_identity = 0;
    std::uint64_t sequence = 0;
    std::int64_t issued_steady_ns = 0;
    std::int64_t expires_steady_ns = 0;
    AutoGlideSource source = AutoGlideSource::GroundJump;
};

bool PublishAutoGlideEvent(AutoGlideEvent event) noexcept;
bool ReadAutoGlideEvent(std::uint64_t after_sequence, std::int64_t now_steady_ns,
    AutoGlideEvent& event) noexcept;

// Movement owns the manual retract transition. Actions consumes this bounded
// notification only to stop the matching airborne pose overlay.
struct GlideCollapseEvent {
    std::uintptr_t entity_identity = 0;
    std::uint64_t auto_sequence = 0;
    std::int64_t issued_steady_ns = 0;
    std::int64_t expires_steady_ns = 0;
};

bool PublishGlideCollapseEvent(GlideCollapseEvent event) noexcept;
bool ReadGlideCollapseEvent(std::uint64_t after_auto_sequence,
    std::int64_t now_steady_ns, GlideCollapseEvent& event) noexcept;

}  // namespace BetterEndfield::Movement
