#pragma once
// POSIX stand-in for the Winsock, adapter and clock calls in EF Link's link_net.h.
// The wire format does not use this file. Android arm64 and Linux are little-endian,
// the same byte order the Windows client writes.
#include <arpa/inet.h>
#include <fcntl.h>
#include <ifaddrs.h>
#include <net/if.h>
#include <netdb.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>

#include <chrono>
#include <cstdint>
#include <cstring>
#include <string>
#include <thread>
#include <utility>
#include <vector>
#include <cerrno>

namespace eflink {
inline std::uint64_t nowMs() {
    return static_cast<std::uint64_t>(std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count());
}

using SOCKET = int;
inline constexpr SOCKET INVALID_SOCKET = -1;
inline void closesocket(SOCKET socket) { if (socket != INVALID_SOCKET) ::close(socket); }

namespace platform {
inline int LastError() { return errno; }
// Non-blocking connect reports EINPROGRESS here. Windows reports WSAEWOULDBLOCK
// for the same moment, and the room loop treats that as "connection in flight".
inline bool Busy(int error) {
    return error == EAGAIN || error == EWOULDBLOCK || error == EINTR || error == EINPROGRESS;
}
inline int Send(SOCKET socket, const void* data, int length) {
    return ::send(socket, data, static_cast<size_t>(length), MSG_NOSIGNAL);
}
inline void NonBlocking(SOCKET socket) {
    const int flags = ::fcntl(socket, F_GETFL, 0);
    if (flags >= 0) ::fcntl(socket, F_SETFL, flags | O_NONBLOCK);
}
inline void SleepMs(int milliseconds) {
    std::this_thread::sleep_for(std::chrono::milliseconds(milliseconds));
}

struct Adapter {
    std::string name;
    std::uint32_t ip_host = 0;
    int prefix = 0;
};

inline std::vector<Adapter> Adapters() {
    std::vector<Adapter> out;
    ifaddrs* list = nullptr;
    if (::getifaddrs(&list) != 0) return out;
    for (ifaddrs* item = list; item != nullptr; item = item->ifa_next) {
        if (item->ifa_addr == nullptr || item->ifa_addr->sa_family != AF_INET) continue;
        if ((item->ifa_flags & IFF_UP) == 0 || (item->ifa_flags & IFF_LOOPBACK) != 0) continue;
        const auto ip = ntohl(reinterpret_cast<sockaddr_in*>(item->ifa_addr)->sin_addr.s_addr);
        int prefix = 0;
        if (item->ifa_netmask != nullptr && item->ifa_netmask->sa_family == AF_INET) {
            auto mask = ntohl(reinterpret_cast<sockaddr_in*>(item->ifa_netmask)->sin_addr.s_addr);
            while (mask & 0x80000000u) { ++prefix; mask <<= 1; }
        }
        out.push_back({item->ifa_name != nullptr ? item->ifa_name : "", ip, prefix});
    }
    ::freeifaddrs(list);
    return out;
}

inline std::vector<std::pair<std::string, std::string>> LocalAddresses() {
    std::vector<std::pair<std::string, std::string>> out;
    for (const auto& adapter : Adapters()) {
        if ((adapter.ip_host >> 24) == 127) continue;
        if ((adapter.ip_host >> 16) == 0xA9FEu) continue;
        in_addr address{};
        address.s_addr = htonl(adapter.ip_host);
        char text[32]{};
        if (::inet_ntop(AF_INET, &address, text, sizeof text) == nullptr) continue;
        out.emplace_back(text, adapter.name);
    }
    return out;
}
}  // namespace platform
}  // namespace eflink
