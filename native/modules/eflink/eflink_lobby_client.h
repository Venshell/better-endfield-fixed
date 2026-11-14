#pragma once
// Client for lobby/server.py. The lobby lists rooms and then splices a raw
// EF Link TCP stream. It never sees the room password.
#include <nlohmann/json.hpp>

#include <arpa/inet.h>
#include <cerrno>
#include <fcntl.h>
#include <netdb.h>
#include <netinet/tcp.h>
#include <poll.h>
#include <sys/socket.h>
#include <unistd.h>

#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

namespace eflink::lobby {
// Baked-in internet lobby. "endfield" is the game id on that host, not an HTTP path.
inline constexpr const char* kEndpoint = "game.venshell.top:47617";
inline constexpr const char* kGame = "endfield";

struct Room {
    std::uint32_t id = 0;
    std::string name, host, version;
    int players = 0, maxPlayers = 0;
    bool password = false;
};

inline int dial(const std::string& endpoint, int timeoutMs, std::string& error) {
    const auto colon = endpoint.rfind(':');
    if (colon == std::string::npos || colon == 0 || colon + 1 >= endpoint.size()) {
        error = "大厅地址须为 host:port";
        return -1;
    }
    const auto host = endpoint.substr(0, colon);
    const auto portText = endpoint.substr(colon + 1);
    addrinfo hints{};
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;
    addrinfo* found = nullptr;
    if (::getaddrinfo(host.c_str(), portText.c_str(), &hints, &found) != 0 || found == nullptr) {
        error = "找不到大厅";
        return -1;
    }
    const int socketFd = ::socket(found->ai_family, found->ai_socktype, found->ai_protocol);
    if (socketFd < 0) {
        ::freeaddrinfo(found);
        error = "无法连接大厅";
        return -1;
    }
    const int flags = ::fcntl(socketFd, F_GETFL, 0);
    ::fcntl(socketFd, F_SETFL, flags | O_NONBLOCK);
    const int result = ::connect(socketFd, found->ai_addr, found->ai_addrlen);
    ::freeaddrinfo(found);
    if (result != 0 && errno != EINPROGRESS) {
        ::close(socketFd);
        error = "无法连接大厅";
        return -1;
    }
    pollfd wait{socketFd, POLLOUT, 0};
    if (::poll(&wait, 1, timeoutMs) <= 0 || (wait.revents & (POLLERR | POLLHUP | POLLNVAL)) != 0) {
        ::close(socketFd);
        error = "连接大厅超时";
        return -1;
    }
    int soError = 0;
    socklen_t length = sizeof soError;
    ::getsockopt(socketFd, SOL_SOCKET, SO_ERROR, &soError, &length);
    if (soError != 0) {
        ::close(socketFd);
        error = "无法连接大厅";
        return -1;
    }
    ::fcntl(socketFd, F_SETFL, flags);
    const int on = 1;
    ::setsockopt(socketFd, IPPROTO_TCP, TCP_NODELAY, &on, sizeof on);
    timeval timeout{8, 0};
    ::setsockopt(socketFd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof timeout);
    ::setsockopt(socketFd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof timeout);
    return socketFd;
}

inline bool writeLine(int socketFd, const nlohmann::json& payload) {
    const auto text = payload.dump() + "\n";
    std::size_t sent = 0;
    while (sent < text.size()) {
        const auto count = ::send(socketFd, text.data() + sent, text.size() - sent, MSG_NOSIGNAL);
        if (count <= 0) return false;
        sent += static_cast<std::size_t>(count);
    }
    return true;
}

inline bool readLine(int socketFd, std::string& line) {
    line.clear();
    char byte = 0;
    while (line.size() < 8192) {
        const auto count = ::recv(socketFd, &byte, 1, 0);
        if (count <= 0) return false;
        if (byte == '\n') return true;
        line.push_back(byte);
    }
    return false;
}

inline std::vector<Room> list(const std::string& endpoint, std::string& error) {
    const int socketFd = dial(endpoint, 4000, error);
    if (socketFd < 0) return {};
    std::vector<Room> rooms;
    std::string line;
    if (!writeLine(socketFd, {{"op", "list"}, {"game", kGame}}) || !readLine(socketFd, line)) {
        ::close(socketFd);
        error = "大厅没有返回房间";
        return {};
    }
    ::close(socketFd);
    const auto parsed = nlohmann::json::parse(line, nullptr, false);
    if (!parsed.is_object() || !parsed.contains("rooms") || !parsed["rooms"].is_array()) {
        error = "大厅房间列表无效";
        return {};
    }
    for (const auto& item : parsed["rooms"]) {
        if (!item.is_object()) continue;
        Room room;
        room.id = item.value("id", 0u);
        room.name = item.value("name", std::string{});
        room.host = item.value("host", std::string{});
        room.version = item.value("version", std::string{});
        room.players = item.value("players", 0);
        room.maxPlayers = item.value("max", 0);
        room.password = item.value("password", false);
        if (room.id != 0) rooms.push_back(std::move(room));
    }
    error.clear();
    return rooms;
}

// Returns the bridged game socket. The caller owns it. -1 on failure.
inline int join(const std::string& endpoint, std::uint32_t roomId, std::string& error) {
    const int socketFd = dial(endpoint, 4000, error);
    if (socketFd < 0) return -1;
    std::string line;
    if (!writeLine(socketFd, {{"op", "join"}, {"game", kGame}, {"room_id", roomId}}) || !readLine(socketFd, line)) {
        ::close(socketFd);
        error = "大厅没有接上房间";
        return -1;
    }
    const auto parsed = nlohmann::json::parse(line, nullptr, false);
    if (!parsed.is_object() || parsed.value("op", std::string{}) != "bridged") {
        ::close(socketFd);
        error = parsed.is_object() ? parsed.value("error", std::string{"大厅拒绝加入"}) : "大厅拒绝加入";
        return -1;
    }
    return socketFd;
}

class Host {
public:
    ~Host() { close(); }
    bool start(const std::string& endpoint, const std::string& name, const std::string& roomName,
               int maxPlayers, bool password, const std::string& version, std::string& error) {
        close();
        control_ = dial(endpoint, 4000, error);
        if (control_ < 0) return false;
        endpoint_ = endpoint;
        if (!writeLine(control_, {{"op", "host"}, {"game", kGame}, {"name", name}, {"room_name", roomName},
                {"max_players", maxPlayers}, {"password", password}, {"version", version}})) {
            error = "无法向大厅登记房间";
            close();
            return false;
        }
        std::string line;
        if (!readLine(control_, line)) {
            error = "大厅没有确认房间";
            close();
            return false;
        }
        const auto parsed = nlohmann::json::parse(line, nullptr, false);
        if (!parsed.is_object() || parsed.value("op", std::string{}) != "hosted") {
            error = "大厅拒绝开房";
            close();
            return false;
        }
        roomId_ = parsed.value("room_id", 0u);
        error.clear();
        return roomId_ != 0;
    }
    void beat(int players) {
        if (control_ < 0) return;
        writeLine(control_, {{"op", "beat"}, {"players", players}});
    }
    // -1 when no guest is waiting. A returned socket is already bridged.
    int pollGuest(std::string& error) {
        if (control_ < 0) return -1;
        pollfd wait{control_, POLLIN, 0};
        if (::poll(&wait, 1, 0) <= 0) return -1;
        std::string line;
        if (!readLine(control_, line)) {
            error = "大厅断开了";
            close();
            return -1;
        }
        const auto parsed = nlohmann::json::parse(line, nullptr, false);
        if (!parsed.is_object() || parsed.value("op", std::string{}) != "guest") return -1;
        const auto token = parsed.value("token", std::string{});
        const int socketFd = dial(endpoint_, 4000, error);
        if (socketFd < 0) return -1;
        std::string answer;
        if (!writeLine(socketFd, {{"op", "accept"}, {"game", kGame}, {"token", token}}) || !readLine(socketFd, answer)) {
            ::close(socketFd);
            error = "无法接上大厅里的客人";
            return -1;
        }
        const auto bridged = nlohmann::json::parse(answer, nullptr, false);
        if (!bridged.is_object() || bridged.value("op", std::string{}) != "bridged") {
            ::close(socketFd);
            error = "大厅没有把客人接进来";
            return -1;
        }
        error.clear();
        return socketFd;
    }
    std::uint32_t roomId() const { return roomId_; }
    void close() {
        if (control_ >= 0) ::close(control_);
        control_ = -1;
        roomId_ = 0;
    }
private:
    int control_ = -1;
    std::uint32_t roomId_ = 0;
    std::string endpoint_;
};
}  // namespace eflink::lobby
