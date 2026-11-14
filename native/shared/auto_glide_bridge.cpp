#include "BetterEndfield/AutoGlideBridge.h"

#include <mutex>

namespace BetterEndfield::Movement {
namespace {

std::mutex g_event_mutex;
AutoGlideEvent g_latest_event{};
GlideCollapseEvent g_latest_collapse{};

}  // namespace

bool PublishAutoGlideEvent(AutoGlideEvent event) noexcept {
    if (event.entity_identity == 0 || event.sequence == 0 ||
            event.issued_steady_ns <= 0 ||
            event.expires_steady_ns <= event.issued_steady_ns) return false;
    std::lock_guard lock(g_event_mutex);
    if (event.sequence <= g_latest_event.sequence) return false;
    g_latest_event = event;
    return true;
}

bool ReadAutoGlideEvent(std::uint64_t after_sequence,
        std::int64_t now_steady_ns, AutoGlideEvent& event) noexcept {
    std::lock_guard lock(g_event_mutex);
    if (g_latest_event.sequence <= after_sequence ||
            g_latest_event.expires_steady_ns <= now_steady_ns) return false;
    event = g_latest_event;
    return true;
}

bool PublishGlideCollapseEvent(GlideCollapseEvent event) noexcept {
    if (event.entity_identity == 0 || event.auto_sequence == 0 ||
            event.issued_steady_ns <= 0 ||
            event.expires_steady_ns <= event.issued_steady_ns) return false;
    std::lock_guard lock(g_event_mutex);
    if (event.auto_sequence <= g_latest_collapse.auto_sequence) return false;
    g_latest_collapse = event;
    return true;
}

bool ReadGlideCollapseEvent(std::uint64_t after_auto_sequence,
        std::int64_t now_steady_ns, GlideCollapseEvent& event) noexcept {
    std::lock_guard lock(g_event_mutex);
    if (g_latest_collapse.auto_sequence <= after_auto_sequence ||
            g_latest_collapse.expires_steady_ns <= now_steady_ns) return false;
    event = g_latest_collapse;
    return true;
}

}  // namespace BetterEndfield::Movement
