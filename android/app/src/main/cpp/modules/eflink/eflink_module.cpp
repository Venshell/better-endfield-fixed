#include "modules/eflink/eflink_module.h"

#include "core/hook_broker.h"
#include "core/log.h"
#include "core/runtime.h"
#include "eflink_lobby_client.h"
#include "link_net.h"
#include "link_session.h"
#include "link_version.h"

#include <nlohmann/json.hpp>

#include <algorithm>
#include <atomic>
#include <cctype>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iterator>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace betterendfieldnext {
namespace {

using TickFn = void (*)(void*, float, void*);
using Json = nlohmann::json;

struct Vec3 { float x, y, z; };
struct Quat { float x, y, z, w; };

constexpr auto kSampleInterval = std::chrono::milliseconds(50);

EflinkModule* g_module = nullptr;
TickFn g_next_tick = nullptr;

void OnTailLateTick(void* instance, float delta, void* method);

float YawDegrees(const float rotation[4]) {
    const float x = rotation[0], y = rotation[1], z = rotation[2], w = rotation[3];
    return std::atan2(2.f * (w * y + x * z), 1.f - 2.f * (y * y + z * z)) * 57.2957795f;
}

std::string explainLobby(std::string code) {
    if (code == "login-required") return "请登录";
    if (code == "not-mainland") return "目前仅限中国大陆网络";
    if (code == "qq-mail-only") return "目前仅支持 QQ 邮箱";
    if (code == "slow-down") return "同一网络 60 秒内只能发送一次验证码";
    if (code == "mail-busy") return "该邮箱发送过于频繁，请稍后再试";
    if (code == "mail-daily") return "今日验证码发送已达上限";
    if (code == "verify-limit") return "当前网络验证次数过多，请稍后再试";
    if (code == "same-network") return "请在申请验证码的同一网络下完成验证";
    if (code == "tls-required") return "联机服务器需要加密连接";
    if (code == "bad-code" || code == "code-expired") return "验证码无效或已过期";
    if (code == "mail-failed") return "验证码邮件发送失败";
    return code;
}

std::string normalizeLobby(std::string value) {
    while (!value.empty() && std::isspace(static_cast<unsigned char>(value.front()))) value.erase(value.begin());
    while (!value.empty() && std::isspace(static_cast<unsigned char>(value.back()))) value.pop_back();
    if (value.rfind("https://", 0) == 0) value.erase(0, 8);
    else if (value.rfind("http://", 0) == 0) value.erase(0, 7);
    if (const auto slash = value.find('/'); slash != std::string::npos) value.resize(slash);
    if (value.empty()) return eflink::lobby::kEndpoint;
    const auto colon = value.rfind(':');
    if (colon != std::string::npos && value.find(':') == colon && colon > 0 && colon + 1 < value.size()) {
        try {
            const auto port = std::stoul(value.substr(colon + 1));
            if (port >= 1 && port <= 65535) return value.substr(0, colon) + ":" + std::to_string(port);
        } catch (...) {}
        return eflink::lobby::kEndpoint;
    }
    return value + ":47617";
}

class LinkRuntime {
public:
    bool Start(Il2CppRuntime& runtime, const std::string& config_text, const std::string& directory) {
        std::lock_guard lock(mutex_);
        if (started_) return true;
        try {
            directory_ = directory;
            game_runtime_ = &runtime;
            std::filesystem::create_directories(directory_);
        } catch (const std::exception& error) {
            LogError("eflink.core", error.what());
            return false;
        }
        Json config = Json::parse(config_text, nullptr, false);
        if (!config.is_object()) config = Json::object();
        const auto name = config.value("player_name", std::string{});
        player_name_ = name.empty() ? "玩家" : name.substr(0, 64);
        room_name_ = config.value("room_name", std::string{});
        port_ = static_cast<std::uint16_t>(std::clamp(config.value("port", 47615), 1024, 65535));
        if (port_ == 47616) port_ = 47615;
        max_players_ = static_cast<unsigned>(std::clamp(config.value("max_players", 8), 2, 16));
        lobby_endpoint_ = normalizeLobby(config.value("lobby", std::string{}));
        internet_enabled_ = config.value("internet", false);
        account_token_ = config.value("account", std::string{});
        account_email_ = config.value("email", std::string{});
        account_name_ = config.value("username", std::string{});
        net_.version = EFLINK_VERSION;
        net_.setName(player_name_);
        net_.onData = [this](std::uint16_t from, std::uint32_t channel, const unsigned char* data, std::size_t size) {
            if (channel == eflink::worldChannel) session_.receive(from, data, size);
        };
        if (!net_.start()) {
            LogError("eflink.core", "network thread did not start");
            return false;
        }
        Resolve(runtime);
        started_ = true;
        running_.store(true);
        worker_ = std::thread([this] { Loop(); });
        LogInfo("eflink.core", "LAN room stack started");
        return true;
    }

    void Sample(Il2CppRuntime& runtime) {
        if (!started_) return;
        const auto now = std::chrono::steady_clock::now();
        if (now - last_sample_ < kSampleInterval) {
            Pump();
            return;
        }
        last_sample_ = now;
        const auto role = net_.currentRole();
        if (role != eflink::Role::Host && role != eflink::Role::Client) {
            Pump();
            return;
        }
        try {
            eflink::proto::PlayerState pose;
            if (ReadPose(runtime, pose)) {
                pose_ready_.store(true);
                net_.setLocalState(std::move(pose));
                eflink::proto::PlayerInfo info;
                {
                    std::lock_guard lock(mutex_);
                    info.name = player_name_;
                    info.modelPath = model_path_;
                    info.bones = bone_names_;
                    info.skeleton = skeleton_;
                }
                net_.setLocalInfo(std::move(info));
            }
        } catch (const std::exception& error) {
            LogError("eflink.core", error.what());
        } catch (...) {
            LogError("eflink.core", "pose sample failed");
        }
        Pump();
    }

    bool hooked() const { return hooked_.load(); }
    void MarkHooked() { hooked_.store(true); }

private:
    struct Method {
        betterendfieldnext::ResolvedMethod resolved;
    };

    void Resolve(Il2CppRuntime& runtime) {
        auto take = [&](const char* assembly, const char* space, const char* type,
                        const char* name, int parameters) {
            return runtime.ResolveMethod(assembly, space, type, name, parameters);
        };
        main_character_ = take("Gameplay.Beyond.dll", "Beyond.Gameplay.Core", "PlayerController", "GetMainCharacter", 0);
        model_component_ = take("Gameplay.Beyond.dll", "Beyond.Gameplay.Core", "Entity", "get_modelCom", 0);
        model_object_ = take("Gameplay.Beyond.dll", "Beyond.Gameplay.View", "BaseModelComponent", "GetModelGo", 0);
        object_transform_ = take("UnityEngine.CoreModule.dll", "UnityEngine", "GameObject", "get_transform", 0);
        position_ = take("UnityEngine.CoreModule.dll", "UnityEngine", "Transform", "get_position", 0);
        rotation_ = take("UnityEngine.CoreModule.dll", "UnityEngine", "Transform", "get_rotation", 0);
        local_rotation_ = take("UnityEngine.CoreModule.dll", "UnityEngine", "Transform", "get_localRotation", 0);
        local_position_ = take("UnityEngine.CoreModule.dll", "UnityEngine", "Transform", "get_localPosition", 0);
        child_count_ = take("UnityEngine.CoreModule.dll", "UnityEngine", "Transform", "get_childCount", 0);
        child_ = take("UnityEngine.CoreModule.dll", "UnityEngine", "Transform", "GetChild", 1);
        object_name_ = take("UnityEngine.CoreModule.dll", "UnityEngine", "Object", "get_name", 0);
        tail_tick_ = take("Gameplay.Beyond.dll", "Beyond.Gameplay.View", "CameraManager", "TailLateTick", 1);
        const bool pose = main_character_.entry && model_component_.entry && model_object_.entry &&
            object_transform_.entry && position_.entry && rotation_.entry;
        LogInfo("eflink.core", pose ? "character pose contracts resolved" : "character pose contracts missing; room list still works");
    }

    void* Call(Il2CppRuntime& runtime, const ResolvedMethod& method, void* instance, void** parameters) const {
        if (!method.info) return nullptr;
        void* exception = nullptr;
        void* result = runtime.Invoke(method.info, instance, parameters, &exception);
        return exception ? nullptr : result;
    }
    template <typename T>
    bool Read(Il2CppRuntime& runtime, const ResolvedMethod& method, void* instance, T& value) const {
        void* boxed = Call(runtime, method, instance, nullptr);
        void* raw = boxed ? runtime.Unbox(boxed) : nullptr;
        if (!raw) return false;
        std::memcpy(&value, raw, sizeof(T));
        return true;
    }

    bool ReadPose(Il2CppRuntime& runtime, eflink::proto::PlayerState& pose) {
        if (!main_character_.entry || !position_.entry || !rotation_.entry) return false;
        void* character = Call(runtime, main_character_, nullptr, nullptr);
        void* component = character ? Call(runtime, model_component_, character, nullptr) : nullptr;
        void* object = component ? Call(runtime, model_object_, component, nullptr) : nullptr;
        void* transform = object ? Call(runtime, object_transform_, object, nullptr) : nullptr;
        if (!transform) return false;
        Vec3 position{};
        Quat rotation{};
        if (!Read(runtime, position_, transform, position) || !Read(runtime, rotation_, transform, rotation)) return false;
        if (!std::isfinite(position.x) || !std::isfinite(position.y) || !std::isfinite(position.z)) return false;
        pose.flags = eflink::proto::StateValid | eflink::proto::StateAlive;
        pose.position[0] = position.x;
        pose.position[1] = position.y;
        pose.position[2] = position.z;
        pose.rotation[0] = rotation.x;
        pose.rotation[1] = rotation.y;
        pose.rotation[2] = rotation.z;
        pose.rotation[3] = rotation.w;
        pose.timeMs = static_cast<std::uint32_t>(eflink::nowMs());
        CollectBones(runtime, character, transform, pose);
        pose.skeleton = skeleton_;
        return true;
    }

    void DropBones() {
        bone_transforms_.clear();
        bone_names_.clear();
        bone_character_ = nullptr;
        bone_root_ = nullptr;
    }

    void CollectBones(Il2CppRuntime& runtime, void* character, void* root, eflink::proto::PlayerState& pose) {
        if (!child_.entry || !child_count_.entry || !local_rotation_.entry || !object_name_.entry) return;
        const auto now = std::chrono::steady_clock::now();
        const bool changed = character != bone_character_ || root != bone_root_;
        if (changed || bone_transforms_.empty() || now - last_bone_scan_ > std::chrono::seconds(2)) {
            const auto previous = bone_names_;
            bone_transforms_.clear();
            bone_names_.clear();
            bone_character_ = character;
            bone_root_ = root;
            int visited = 0;
            Walk(runtime, root, 0, visited);
            if (bone_names_ != previous) skeleton_ = static_cast<std::uint16_t>(skeleton_ + 1);
            last_bone_scan_ = now;
            if (void* named = Call(runtime, object_name_, root, nullptr)) model_path_ = runtime.CopyString(named);
        }
        for (std::size_t index = 0; index < bone_transforms_.size() && pose.bones.size() / 4 < eflink::proto::kMaxBones; ++index) {
            Quat local{};
            if (!Read(runtime, local_rotation_, bone_transforms_[index], local)) {
                DropBones();
                return;
            }
            for (float value : {local.x, local.y, local.z, local.w})
                pose.bones.push_back(eflink::proto::quantize(value));
            if (index == 0 && local_position_.entry) {
                Vec3 hips{};
                if (Read(runtime, local_position_, bone_transforms_[index], hips)) {
                    pose.rootOffset[0] = hips.x;
                    pose.rootOffset[1] = hips.y;
                    pose.rootOffset[2] = hips.z;
                }
            }
        }
    }

    void Walk(Il2CppRuntime& runtime, void* transform, int depth, int& visited) {
        if (!transform || depth > 12 || visited > 256 || bone_transforms_.size() >= eflink::proto::kMaxBones) return;
        ++visited;
        if (void* named = Call(runtime, object_name_, transform, nullptr)) {
            const auto name = runtime.CopyString(named);
            if (name.rfind("Bip001", 0) == 0 && !name.ends_with("Nub")) {
                bone_transforms_.push_back(transform);
                bone_names_.push_back(name);
            }
        }
        int children = 0;
        if (!Read(runtime, child_count_, transform, children) || children <= 0 || children > 64) return;
        for (int index = 0; index < children && bone_transforms_.size() < eflink::proto::kMaxBones; ++index) {
            void* parameters[]{&index};
            Walk(runtime, Call(runtime, child_, transform, parameters), depth + 1, visited);
        }
    }

    void Pump() {
        std::lock_guard lock(session_mutex_);
        try { session_.update(net_); }
        catch (const std::exception& error) { LogError("eflink.core", error.what()); }
        catch (...) {}
    }

    void Loop() {
        while (running_.load()) {
            try {
                Poll();
                MaintainLobby();
                if (!hooked_.load()) Pump();
                Publish();
            } catch (const std::exception& error) {
                LogError("eflink.core", error.what());
            } catch (...) {
                LogError("eflink.core", "room loop failed");
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(200));
        }
    }

    static bool splitLobby(const std::string& endpoint, std::string& host, std::uint16_t& port) {
        const auto colon = endpoint.rfind(':');
        if (colon == std::string::npos || colon == 0) return false;
        try {
            const auto value = std::stoul(endpoint.substr(colon + 1));
            if (value == 0 || value > 65535) return false;
            host = endpoint.substr(0, colon);
            port = static_cast<std::uint16_t>(value);
            return true;
        } catch (...) {
            return false;
        }
    }

    // Internet rooms are addressed by the account the player registered.
    // A LAN room keeps the name typed on the LAN settings row.
    std::string displayedName(bool internet) {
        std::lock_guard lock(mutex_);
        if (internet && !account_name_.empty()) return account_name_.substr(0, 64);
        auto name = player_name_.substr(0, 64);
        return name.empty() ? std::string("玩家") : name;
    }

    void applyDisplayedName(bool internet) {
        net_.setName(displayedName(internet));
    }

    void Poll() {
        const auto path = std::filesystem::path(directory_) / "command.json";
        std::error_code ignored;
        if (!std::filesystem::is_regular_file(path, ignored)) return;
        std::string text;
        {
            std::ifstream input(path);
            text.assign(std::istreambuf_iterator<char>(input), std::istreambuf_iterator<char>());
        }
        std::filesystem::remove(path, ignored);
        auto command = Json::parse(text, nullptr, false);
        if (!command.is_object()) return;
        const auto sequence = command.value("seq", static_cast<std::uint64_t>(0));
        if (!sequence || sequence <= command_sequence_) return;
        command_sequence_ = sequence;
        const auto op = command.value("op", std::string{});
        if (op == "host") {
            eflink::HostOptions options;
            options.roomName = command.value("room_name", room_name_);
            options.password = command.value("password", std::string{});
            options.maxPlayers = static_cast<unsigned>(std::clamp(command.value("max_players", static_cast<int>(max_players_)), 2, 16));
            auto port = command.value("port", static_cast<int>(port_));
            if (port < 1024 || port > 65535 || port == 47616) port = port_;
            options.port = static_cast<std::uint16_t>(port);
            const auto network = command.value("network", std::string{"lan"});
            options.internet = network == "internet";
            if (network == "internet" && lobby_endpoint_.empty()) {
                std::lock_guard lock(mutex_);
                lobby_error_ = "连不上联机服务器";
                internet_room_.store(false);
                return;
            }
            if (network == "internet" && !internet_enabled_) {
                std::lock_guard lock(mutex_);
                lobby_error_ = "互联网联机未启用";
                internet_room_.store(false);
                return;
            }
            if (network == "internet" && account_token_.empty()) {
                std::lock_guard lock(mutex_);
                lobby_error_ = "请登录";
                internet_room_.store(false);
                return;
            }
            internet_room_.store(network == "internet");
            if (command.contains("player_name") && command["player_name"].is_string()) {
                auto name = command["player_name"].get<std::string>().substr(0, 64);
                if (!name.empty()) {
                    std::lock_guard lock(mutex_);
                    player_name_ = name;
                }
            }
            applyDisplayedName(network == "internet");
            net_.host(std::move(options));
        } else if (op == "join") {
            internet_room_.store(false);
            applyDisplayedName(false);
            net_.join(command.value("address", std::string{}), command.value("password", std::string{}));
        } else if (op == "join_lobby") {
            if (!internet_enabled_ || account_token_.empty()) {
                std::lock_guard lock(mutex_);
                lobby_error_ = account_token_.empty() ? "请登录" : "互联网联机未启用";
                return;
            }
            std::uint32_t roomId = command.value("room_id", 0u);
            std::string failure, relay;
            const int bridged = lobby_endpoint_.empty() ? -1 : eflink::lobby::join(lobby_endpoint_, roomId, relay, failure, account_token_);
            if (bridged < 0) {
                std::lock_guard lock(mutex_);
                lobby_error_ = failure.empty() ? "连不上联机服务器" : explainLobby(failure);
            } else {
                internet_room_.store(true);
                internet_room_id_ = roomId;
                applyDisplayedName(true);
                std::string host;
                std::uint16_t relayPort = 0;
                splitLobby(lobby_endpoint_, host, relayPort);
                net_.adoptServer(bridged, command.value("password", std::string{}), "lobby",
                    eflink::lobby::relayBytes(relay), host, relayPort);
            }
        } else if (op == "leave") {
            internet_room_.store(false);
            internet_room_id_ = 0;
            net_.leave();
        } else if (op == "refresh") {
            net_.refresh();
        } else if (op == "chat") {
            auto line = command.value("text", std::string{});
            if (!line.empty() && line.size() <= 1024) net_.chat(std::move(line));
        } else if (op == "name") {
            auto name = command.value("player_name", std::string{});
            if (name.empty()) {
                std::lock_guard lock(mutex_);
                name = player_name_;
            }
            name = name.substr(0, 64);
            if (name.empty()) name = "玩家";
            {
                std::lock_guard lock(mutex_);
                player_name_ = name;
            }
            if (!internet_room_.load()) net_.setName(name);
        }
    }

    void MaintainLobby() {
        if (!internet_enabled_ || lobby_endpoint_.empty()) return;
        const auto now = std::chrono::steady_clock::now();
        if (now - last_lobby_list_ > std::chrono::seconds(2)) {
            last_lobby_list_ = now;
            if (!account_token_.empty()) {
                std::string failure;
                const auto listed = eflink::lobby::list(lobby_endpoint_, failure, account_token_);
                Json rooms = Json::array();
                for (const auto& room : listed) {
                    if (lobby_host_ && room.id == lobby_host_->roomId()) continue;
                    rooms.push_back({{"name", room.name}, {"host", room.host}, {"room_id", room.id},
                        {"players", room.players}, {"max", room.maxPlayers}, {"password", room.password},
                        {"version", room.version}, {"lobby", true}, {"address", ""}});
                }
                std::lock_guard lock(mutex_);
                lobby_rooms_ = std::move(rooms);
                lobby_error_ = failure.empty() ? std::string{} : explainLobby(failure);
            }
        }
        const auto snapshot = net_.snapshot();
        if (snapshot.role == eflink::Role::Host && internet_room_.load()) {
            if (!lobby_host_) {
                auto host = std::make_unique<eflink::lobby::Host>();
                std::string failure;
                std::string name;
                {
                    std::lock_guard lock(mutex_);
                    name = player_name_;
                }
                const auto roomName = snapshot.roomName.empty() ? room_name_ : snapshot.roomName;
                if (!account_name_.empty()) name = account_name_;
                if (account_token_.empty() || !host->start(lobby_endpoint_, name, roomName, static_cast<int>(snapshot.maxPlayers),
                        snapshot.password, net_.version, failure, account_token_)) {
                    std::lock_guard lock(mutex_);
                    lobby_error_ = explainLobby(failure.empty() ? "请登录" : failure);
                } else {
                    std::string relayHost;
                    std::uint16_t relayPort = 0;
                    if (splitLobby(lobby_endpoint_, relayHost, relayPort)) {
                        net_.setUdpRelay(relayHost, relayPort, eflink::lobby::relayBytes(host->udpToken()));
                    }
                    lobby_host_ = std::move(host);
                    std::lock_guard lock(mutex_);
                    lobby_error_.clear();
                }
            }
            if (lobby_host_) {
                lobby_host_->beat(static_cast<int>(std::max<std::size_t>(1, snapshot.peers.size())));
                std::string failure;
                for (;;) {
                    const auto guest = lobby_host_->pollGuest(failure);
                    if (guest.lost || lobby_host_->roomId() == 0) {
                        lobby_host_.reset();
                        net_.clearUdpRelay();
                        if (!failure.empty()) {
                            std::lock_guard lock(mutex_);
                            lobby_error_ = explainLobby(failure);
                        }
                        break;
                    }
                    if (guest.socket < 0) break;
                    net_.adoptIncoming(guest.socket, "lobby", eflink::lobby::relayBytes(guest.relay));
                }
            }
        } else if (lobby_host_) {
            lobby_host_.reset();
            net_.clearUdpRelay();
        }
    }

    void Publish() {
        const auto snapshot = net_.snapshot();
        Json players = Json::array();
        const eflink::PeerView* self = nullptr;
        for (const auto& peer : snapshot.peers) if (peer.local) self = &peer;
        for (const auto& peer : snapshot.peers) {
            Json item{{"id", peer.id}, {"name", peer.hasInfo ? peer.info.name : ""},
                {"local", peer.local}, {"host", peer.host}, {"map", peer.hasInfo ? peer.info.mapId : ""}};
            if (!peer.states.empty()) {
                const auto& pose = peer.states.back().state;
                item["x"] = pose.position[0];
                item["y"] = pose.position[1];
                item["z"] = pose.position[2];
                item["yaw"] = YawDegrees(pose.rotation);
                item["bones"] = pose.bones.size() / 4;
                if (self && !self->states.empty() && !peer.local) {
                    const auto& origin = self->states.back().state.position;
                    const float dx = pose.position[0] - origin[0];
                    const float dy = pose.position[1] - origin[1];
                    const float dz = pose.position[2] - origin[2];
                    item["distance"] = std::sqrt(dx * dx + dy * dy + dz * dz);
                }
            }
            players.push_back(std::move(item));
        }
        Json rooms = Json::array();
        for (const auto& room : snapshot.rooms) {
            rooms.push_back({{"name", room.beacon.roomName}, {"host", room.beacon.hostName},
                {"address", room.address}, {"players", room.beacon.players},
                {"max", room.beacon.maxPlayers}, {"password", room.beacon.password},
                {"version", room.beacon.version}});
        }
        Json chat = Json::array();
        const auto lines = net_.chatSince(0);
        const auto begin = lines.size() > 20 ? lines.end() - 20 : lines.begin();
        for (auto it = begin; it != lines.end(); ++it) {
            chat.push_back({{"name", it->name}, {"text", it->text}, {"system", it->system}});
        }
        Json lobbyRooms = Json::array();
        std::string lobbyError;
        {
            std::lock_guard lock(mutex_);
            lobbyRooms = lobby_rooms_;
            lobbyError = lobby_error_;
        }
        for (const auto& room : lobbyRooms) rooms.push_back(room);
        const char* role = "offline";
        if (snapshot.role == eflink::Role::Connecting) role = "connecting";
        else if (snapshot.role == eflink::Role::Host) role = "host";
        else if (snapshot.role == eflink::Role::Client) role = "client";
        Json body{{"role", role}, {"room_name", snapshot.roomName}, {"local_id", snapshot.localId},
            {"error", snapshot.lastError.empty() ? lobbyError : snapshot.lastError},
            {"players", std::move(players)}, {"rooms", std::move(rooms)},
            {"chat", std::move(chat)}, {"pose_ready", pose_ready_.load()},
            {"udp", snapshot.udpLinks}, {"tcp_only", snapshot.tcpOnlyLinks},
            {"network", internet_room_.load() ? "internet" : (std::string(role) == "offline" ? "" : "lan")},
            {"lobby_ready", !lobby_endpoint_.empty()},
            {"server", lobby_endpoint_.substr(0, lobby_endpoint_.rfind(':'))},
            {"account", account_name_}, {"account_email", account_email_},
            {"room_number", lobby_host_ ? lobby_host_->roomId() : internet_room_id_},
            {"version", EFLINK_VERSION}};
        const auto text = body.dump();
        const auto path = std::filesystem::path(directory_) / "status.json";
        const auto temporary = std::filesystem::path(directory_) / "status.json.tmp";
        {
            std::ofstream output(temporary, std::ios::binary | std::ios::trunc);
            output << text;
        }
        std::error_code ignored;
        std::filesystem::rename(temporary, path, ignored);
    }

    std::mutex mutex_;
    std::mutex session_mutex_;
    std::atomic<Il2CppRuntime*> game_runtime_{nullptr};
    eflink::Net net_;
    eflink::Session session_;
    std::string directory_, player_name_ = "玩家", room_name_, model_path_, lobby_endpoint_, lobby_error_;
    std::string account_token_, account_email_, account_name_;
    bool internet_enabled_ = false;
    std::uint32_t internet_room_id_ = 0;
    Json lobby_rooms_ = Json::array();
    std::unique_ptr<eflink::lobby::Host> lobby_host_;
    std::atomic<bool> internet_room_{false};
    std::chrono::steady_clock::time_point last_lobby_list_{};
    std::uint16_t port_ = 47615;
    unsigned max_players_ = 8;
    std::uint64_t command_sequence_ = 0;
    std::uint16_t skeleton_ = 1;
    std::vector<void*> bone_transforms_;
    std::vector<std::string> bone_names_;
    void* bone_character_ = nullptr;
    void* bone_root_ = nullptr;
    std::chrono::steady_clock::time_point last_sample_{};
    std::chrono::steady_clock::time_point last_bone_scan_{};
    std::atomic<bool> running_{false}, started_{false}, hooked_{false}, pose_ready_{false};
    std::thread worker_;
    ResolvedMethod main_character_{}, model_component_{}, model_object_{}, object_transform_{};
    ResolvedMethod position_{}, rotation_{}, local_rotation_{}, local_position_{};
    ResolvedMethod child_count_{}, child_{}, object_name_{}, tail_tick_{};
    HookBroker hooks_;
    void* hook_stub_ = nullptr;

    friend class betterendfieldnext::EflinkModule;
    friend void OnTailLateTick(void*, float, void*);
};

LinkRuntime& Runtime() {
    static auto* runtime = new LinkRuntime;
    return *runtime;
}

void OnTailLateTick(void* instance, float delta, void* method) {
    if (g_next_tick) g_next_tick(instance, delta, method);
    if (g_module == nullptr) return;
    auto* runtime = Runtime().game_runtime_.load();
    if (runtime != nullptr) Runtime().Sample(*runtime);
}

}  // namespace

const char* EflinkModule::Id() const { return "eflink.core"; }

ModuleResult EflinkModule::Start(Il2CppRuntime& runtime) {
    const char* config = std::getenv("BETTER_ENDFIELD_NEXT_EFLINK_CONFIG");
    const char* directory = std::getenv("BETTER_ENDFIELD_NEXT_EFLINK_DIR");
    if (config == nullptr || config[0] == '\0' || directory == nullptr || directory[0] == '\0') {
        return {false, "EF Link configuration was not published"};
    }
    g_module = this;
    if (!Runtime().Start(runtime, config, directory)) {
        return {false, "EF Link network did not start"};
    }
    auto& link = Runtime();
    if (link.tail_tick_.entry) {
        std::string error;
        if (link.hooks_.Initialize(error) &&
            link.hooks_.Install("eflink.core", link.tail_tick_.entry, reinterpret_cast<void*>(&OnTailLateTick),
                reinterpret_cast<void**>(&g_next_tick), link.hook_stub_, error)) {
            link.MarkHooked();
            LogInfo("eflink.core", "CameraManager.TailLateTick chained for pose sampling");
        } else {
            LogError("eflink.core", error.empty() ? "pose hook was not installed" : error.c_str());
        }
    }
    return {true, "EF Link protocol ready (rooms, direct connect, pose sample)"};
}

}  // namespace betterendfieldnext
