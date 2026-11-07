#include "android_frame.h"
// File-based input relay between the panel's Java code and the native key
// latch. JNI cannot serve this role: Runtime.nativeLoad registers the library
// under the game's classloader, while the panel's bridge classes belong to the
// LSPosed module classloader, and Android forbids opening the same .so path
// twice under different classloaders. Plain files in the game's own files
// directory have no such boundary — both sides run in the same process.
//
// Protocol on the input file (one event per line, UTF-8):
//   "<vk> <action>\n"   latch a virtual key (VirtualKeyAction: 0/1/2)
//   "c <payload>\n"     submit a runtime command (single-slot pump)
//   "r\n"               release every latched key
//   "m <dx> <dy>\n"     accumulate a mouse-look delta (screen pixels, y down)
// The look deltas are summed rather than queued, so the panel can send a drag at
// whatever rate its gesture recogniser reports without the runtime having to
// keep up event by event.
// A command payload is itself multi-line, so the panel folds its newlines into
// U+001F to keep the event on one line; the "c" branch below unfolds it before
// handing it to the pump, which frames the payload by newlines itself. Sending
// those newlines raw would end the event after "BE_COMMAND_V1" and the pump
// would reject the fragment.
// The status file is rewritten whenever the runtime command status changes.

#include "android_virtual_keys.h"
#include "core/command_pump.h"
#include "core/log.h"

#include <algorithm>
#include <atomic>
#include <cerrno>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <cstdlib>
#include <string>
#include <thread>

#include <fcntl.h>
#include <unistd.h>

namespace betterendfieldnext {
namespace {

constexpr auto kPollInterval = std::chrono::milliseconds(10);
constexpr auto kStatusInterval = std::chrono::milliseconds(500);

// Reads every new byte of <path> since the last call into <buffer>. Reopens
// (and restarts from zero) when the file shrank underneath us, which is how a
// fresh session truncates the stream.
bool DrainInput(int& fd, long long& offset, const std::string& path,
        std::string& buffer) {
    if (fd < 0) {
        fd = open(path.c_str(), O_RDONLY | O_CLOEXEC);
        if (fd < 0) return false;
        offset = 0;
    }
    // A truncate resets the stream; detect it via the current size.
    off_t size = lseek(fd, 0, SEEK_END);
    if (size < offset) {
        close(fd);
        fd = open(path.c_str(), O_RDONLY | O_CLOEXEC);
        if (fd < 0) return false;
        offset = 0;
        size = lseek(fd, 0, SEEK_END);
    }
    if (size == offset) return true;
    if (lseek(fd, offset, SEEK_SET) < 0) return false;
    char chunk[512];
    for (;;) {
        ssize_t got = read(fd, chunk, sizeof(chunk));
        if (got <= 0) break;
        buffer.append(chunk, static_cast<size_t>(got));
        offset += got;
        if (static_cast<long long>(got) < static_cast<long long>(sizeof(chunk))) break;
    }
    return true;
}

void HandleLine(const std::string& line) {
    if (line.empty()) return;
    if (line[0] == 'r') {
        ReleaseAllVirtualKeys();
        return;
    }
    if (line[0] == 'c' && line.size() >= 3 && line[1] == ' ') {
        // The payload's own newlines arrive folded (see the header). Unfold them
        // here: the pump validates a full "BE_COMMAND_V1\n<generation>\n..." and
        // a raw newline would have ended this event at its first field.
        std::string payload = line.substr(2);
        std::replace(payload.begin(), payload.end(), '\x1f', '\n');
        SubmitRuntimeCommand(payload.c_str(), payload.size());
        return;
    }
    if (line[0] == 'm' && line.size() >= 3 && line[1] == ' ') {
        const char* first = line.c_str() + 2;
        char* end = nullptr;
        const long dx = std::strtol(first, &end, 10);
        if (end == nullptr || end == first || *end != ' ') return;
        const char* second = end + 1;
        const long dy = std::strtol(second, &end, 10);
        if (end == nullptr || end == second) return;
        // A screen cannot produce a 1000-pixel jump between two report events;
        // clamping keeps a garbled line from throwing the camera far away.
        AddAndroidLook(static_cast<int>(
            std::clamp(dx, -1000L, 1000L)), static_cast<int>(
            std::clamp(dy, -1000L, 1000L)));
        return;
    }
    char* end = nullptr;
    const long vk = std::strtol(line.c_str(), &end, 10);
    if (end == nullptr || end == line.c_str() || *end != ' ') return;
    const long action = std::strtol(end + 1, &end, 10);
    if (vk <= 0 || vk > 255) return;
    if (action < 0 || action > 2) return;  // VirtualKeyAction range
    SetVirtualKey(static_cast<int>(vk), static_cast<VirtualKeyAction>(action));
}

void WriteStatusFile(const std::string& path, const std::string& status) {
    const int fd = open(path.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
    if (fd < 0) return;
    ssize_t ignored = write(fd, status.data(), status.size());
    (void)ignored;
    close(fd);
}

// Appends the native log ring's new entries to the journal file the panel
// tails. Truncates first when the file has grown past the cap; already
// delivered entries are never replayed (the ring cursor is independent).
void AppendNativeLog(const std::string& path, std::size_t& cursor) {
    const int fd = open(path.c_str(), O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0644);
    if (fd < 0) return;
    if (lseek(fd, 0, SEEK_END) > 256 * 1024) ftruncate(fd, 0);
    const std::string fresh = CopyNativeLogSince(cursor);
    if (!fresh.empty()) {
        ssize_t ignored = write(fd, fresh.data(), fresh.size());
        (void)ignored;
    }
    close(fd);
}

void RelayLoop(const std::string& input, const std::string& status,
        const std::string& nativeLog) {
    LogInfo("relay", "input file relay active");
    WriteStatusFile(status, "relay alive; native runtime starting\n");
    // A fresh session must not replay the previous one's log.
    close(open(nativeLog.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644));
    int fd = -1;
    long long offset = 0;
    std::size_t logCursor = 0;
    std::string pending;
    std::string lastStatus;
    auto nextStatus = std::chrono::steady_clock::now();
    for (;;) {
        std::string buffer;
        DrainInput(fd, offset, input, buffer);
        pending += buffer;
        size_t newline;
        while ((newline = pending.find('\n')) != std::string::npos) {
            HandleLine(pending.substr(0, newline));
            pending.erase(0, newline + 1);
        }
        const auto now = std::chrono::steady_clock::now();
        if (now >= nextStatus) {
            nextStatus = now + kStatusInterval;
            const std::string status_now = CopyRuntimeCommandStatus();
            if (status_now != lastStatus) {
                lastStatus = status_now;
                WriteStatusFile(status, "relay alive; " + status_now + "\n");
            }
            AppendNativeLog(nativeLog, logCursor);
        }
        std::this_thread::sleep_for(kPollInterval);
    }
}

}  // namespace

void StartInputRelay() {
    const char* input = std::getenv("BETTER_ENDFIELD_NEXT_INPUT_FILE");
    const char* status = std::getenv("BETTER_ENDFIELD_NEXT_STATUS_FILE");
    const char* nativeLog = std::getenv("BETTER_ENDFIELD_NEXT_NATIVE_LOG");
    if (input == nullptr || input[0] == '\0' || status == nullptr || status[0] == '\0') {
        LogInfo("relay", "input file relay unavailable: paths not configured");
        return;
    }
    std::thread(RelayLoop, std::string(input), std::string(status),
            nativeLog != nullptr ? std::string(nativeLog) : std::string()).detach();
}

}  // namespace betterendfieldnext
