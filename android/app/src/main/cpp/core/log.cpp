#include "log.h"

#include <android/log.h>

#include <cstdio>
#include <cstdlib>
#include <mutex>
#include <string>
#include <unistd.h>

namespace betterendfieldnext {
namespace {

constexpr char kLogTag[] = "BetterEndfieldNext";
// The on-device journal tails these entries; keep it bounded so a chatty
// module cannot grow memory without limit. 512 because module init bursts
// (the UI module logs one line per resolved contract) must not evict a
// sibling module's failure evidence.
constexpr std::size_t kRingCapacity = 512;

std::mutex g_log_mutex;
std::string g_ring[kRingCapacity];
std::size_t g_ring_next = 0;
std::size_t g_ring_total = 0;
// Highest serial the relay has already copied out. The relay drains the whole
// ring roughly twice a second while modules initialize, but a single burst can
// still overrun 512 lines between two drains, and the ring would then have
// discarded lines the relay never saw. Dropping the oldest line is what a ring
// must do; what is not acceptable is claiming it was delivered.
std::size_t g_ring_read = 0;

void Remember(const char* component, const char* message) {
    std::lock_guard<std::mutex> lock(g_log_mutex);
    g_ring[g_ring_next] = std::string("[") + component + "] " + message;
    g_ring_next = (g_ring_next + 1) % kRingCapacity;
    ++g_ring_total;
    // Serial numbers are 1-based (CopyNativeLogSince prints index + 1), so the
    // oldest line still resident after this write is g_ring_total - capacity.
    const std::size_t oldest = g_ring_total > kRingCapacity
        ? g_ring_total - kRingCapacity + 1 : 1;
    if (g_ring_read + 1 < oldest) g_ring_read = oldest - 1;
}

void Write(int priority, const char* component, const char* message) {
    __android_log_print(priority, kLogTag, "[%s] %s", component, message);
    Remember(component, message);
    const char* diagnostics = std::getenv("BETTER_ENDFIELD_NEXT_DIAGNOSTICS_PATH");
    if (diagnostics != nullptr && *diagnostics != '\0') {
        static std::mutex mutex;
        std::lock_guard lock(mutex);
        if (FILE* file = std::fopen(diagnostics, "a")) {
            if (std::fseek(file, 0, SEEK_END) == 0 && std::ftell(file) > 1024 * 1024) {
                std::fclose(file);
                file = std::fopen(diagnostics, "w");
                if (!file) return;
            }
            std::fprintf(file, "[tid=%d][%s] %s\n", static_cast<int>(gettid()), component, message);
            std::fclose(file);
        }
    }
}

}  // namespace

void LogInfo(const char* component, const char* message) {
    // MIUI suppresses injected native INFO messages for this game process.
    // Keep alpha diagnostics visible without using fatal/error severity.
    Write(ANDROID_LOG_WARN, component, message);
}

void LogError(const char* component, const char* message) {
    Write(ANDROID_LOG_ERROR, component, message);
}

std::string CopyNativeLogSince(std::size_t& cursor) {
    std::lock_guard<std::mutex> lock(g_log_mutex);
    // A cursor from a previous session of the same process — the relay is
    // restarted when the module library is reloaded — can be larger than what
    // has been logged so far. Rewinding to 0 is wrong there: it re-delivers
    // every line of the new session, and the relay's serial filter would then
    // drop all of them as replays. Only a cursor that is behind the retained
    // window is stale; one that is simply ahead is reset to the window start.
    if (cursor + 1 < g_ring_read || cursor > g_ring_total) cursor = g_ring_read;
    // Never fall further behind than the ring retains, or the surviving lines
    // are silently skipped.
    if (g_ring_total - cursor > kRingCapacity) cursor = g_ring_total - kRingCapacity;
    std::string out;
    for (std::size_t i = cursor; i < g_ring_total; ++i) {
        // A monotonic serial lets the panel reject lines it has already
        // recorded (a file truncate resets the journal's byte offset, which
        // would otherwise replay the whole log every 250 ms).
        out += "#" + std::to_string(i + 1) + " " + g_ring[i % kRingCapacity] + "\n";
    }
    cursor = g_ring_total;
    return out;
}

}  // namespace betterendfieldnext
