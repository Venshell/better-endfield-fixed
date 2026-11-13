// Host-side checks for the POSIX port of the EF Link wire and room loop.
// In-process UDP broadcast fan-out is not the same on Linux as on Windows, so
// discovery is reported and direct TCP/UDP delivery is what must pass.
#include "link_net.h"
#include "link_session.h"
#include "link_version.h"

#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <limits>
#include <string>
#include <thread>
#include <unistd.h>

using namespace eflink;
namespace {
int failures = 0;
void check(bool condition, const char* what) {
    std::printf("%s %s\n", condition ? "[ok]  " : "[FAIL]", what);
    if (!condition) ++failures;
}
template <class F>
bool waitFor(F condition, int ms = 4000) {
    for (int i = 0; i < ms / 20; ++i) {
        if (condition()) return true;
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
    }
    return condition();
}
proto::PlayerInfo info(const char* model, const char* map) {
    proto::PlayerInfo player;
    player.modelPath = model;
    player.mapId = map;
    player.levelId = std::string(map) + "_lv001";
    player.skeleton = 1;
    player.bones = {"Bip001", "Bip001 Pelvis", "Bip001 Spine"};
    return player;
}
proto::PlayerState state(float x, std::uint32_t map) {
    proto::PlayerState pose;
    pose.flags = proto::StateValid | proto::StateAlive;
    pose.mapHash = map;
    pose.skeleton = 1;
    pose.position[0] = x;
    pose.position[1] = 100;
    pose.position[2] = -5;
    pose.rotation[3] = 1;
    pose.bones.assign(12, 0);
    pose.bones[3] = 32767;
    return pose;
}
const PeerView* find(const Snapshot& snapshot, std::uint16_t id) {
    for (const auto& peer : snapshot.peers) if (peer.id == id) return &peer;
    return nullptr;
}
}

int main() {
    check(std::string(EFLINK_VERSION).find("android") != std::string::npos, "android build identifies itself");
    proto::Writer writer;
    writer.str("中文中文", 5);
    proto::Reader reader(writer.bytes.data(), writer.bytes.size());
    check(reader.str(5) == "中", "UTF-8 truncation preserves complete characters");
    proto::PlayerState bad;
    bad.position[0] = std::numeric_limits<float>::quiet_NaN();
    proto::Writer payload;
    proto::write(payload, bad);
    bool rejected = false;
    try {
        proto::Reader broken(payload.bytes.data(), payload.bytes.size());
        proto::readPlayerState(broken);
    } catch (...) { rejected = true; }
    check(rejected, "nonfinite pose rejected");
    check(proto::quantize(2) == 32767 && proto::quantize(-2) == -32767, "bone rotations are bounded");
    check(proto::passwordProof(0, "secret") == proto::passwordProof(0, "secret"), "password proof is stable");
    check(proto::passwordProof(0, "secret") != proto::passwordProof(0, "other"), "password proof changes with the text");
    const auto encoded = proto::encode(proto::Beacon{7, 47615, 1, 8, true, "房间", "房主", "map", EFLINK_VERSION});
    proto::Reader beaconReader(encoded.data(), encoded.size());
    const auto header = proto::readUdpHeader(beaconReader);
    const auto beacon = proto::decodeBeacon(beaconReader, header.session);
    check(header.type == proto::Udp::Beacon && beacon.roomName == "房间" && beacon.password, "beacon round trip");

    nlohmann::json world = {
        {"map", "map01"}, {"level", "lv001"}, {"level_num", 1}, {"position", {1.0, 2.0, 3.0}},
        {"entities", nlohmann::json::array({
            {{"key", "mob-1"}, {"position", {4.0, 5.0, 6.0}}, {"hp", 10.0}, {"max_hp", 10.0}}
        })}
    };
    check(validWorld(world), "world snapshot schema accepts a minimal host world");
    world["position"] = {1.0, 2.0};
    check(!validWorld(world), "world snapshot schema rejects a short position");

    const auto port = static_cast<std::uint16_t>(20000 + (static_cast<unsigned>(::getpid()) % 20000));
    const auto discovery = static_cast<std::uint16_t>(port + 1);
    Net host, alice, bob;
    std::atomic<int> hostData = 0, bobData = 0, bobUnreliable = 0, alicePeerEvents = 0;
    std::string bobPayload;
    host.onData = [&](std::uint16_t, std::uint32_t channel, const unsigned char*, std::size_t) {
        if (channel == proto::fnv32("test.reliable")) ++hostData;
    };
    bob.onData = [&](std::uint16_t from, std::uint32_t channel, const unsigned char* data, std::size_t size) {
        if (channel == proto::fnv32("test.reliable") && from == 2) {
            ++bobData;
            bobPayload.assign(reinterpret_cast<const char*>(data), size);
        }
        if (channel == proto::fnv32("test.fast")) ++bobUnreliable;
    };
    alice.onPeer = [&](std::uint16_t, bool) { ++alicePeerEvents; };
    host.setName("Host");
    alice.setName("Alice");
    bob.setName("Bob");
    host.version = EFLINK_VERSION;
    alice.version = EFLINK_VERSION;
    bob.version = EFLINK_VERSION;
    host.setLocalInfo(info("chr_host.prefab", "map01"));
    alice.setLocalInfo(info("chr_alice.prefab", "map01"));
    bob.setLocalInfo(info("chr_bob.prefab", "map02"));
    check(host.start(discovery) && alice.start(discovery) && bob.start(discovery), "network threads start");

    host.host({"测试房间", "secret", 3, port});
    check(waitFor([&] { return host.currentRole() == Role::Host; }), "host opens a room");
    const bool discovered = waitFor([&] {
        for (const auto& room : alice.snapshot().rooms)
            if (room.beacon.roomName == "测试房间" && room.beacon.password && room.beacon.hostName == "Host") return true;
        return false;
    }, 1500);
    std::printf("%s in-process LAN discovery (optional on this OS)\n", discovered ? "[ok]  " : "[note]");

    alice.join("127.0.0.1:" + std::to_string(port), "wrong");
    check(waitFor([&] { return alice.currentRole() == Role::Offline && alice.snapshot().lastError == "密码错误"; }),
        "wrong password is rejected with a reason");
    alice.join("127.0.0.1:" + std::to_string(port), "secret");
    check(waitFor([&] { return alice.currentRole() == Role::Client; }), "alice joins with the password");
    bob.join("localhost:" + std::to_string(port), "secret");
    check(waitFor([&] { return bob.currentRole() == Role::Client; }), "bob joins by host name");
    check(alice.snapshot().localId != bob.snapshot().localId && alice.snapshot().localId >= 2, "members get distinct ids");

    Net carol;
    carol.setName("Carol");
    carol.version = EFLINK_VERSION;
    carol.start(discovery);
    carol.join("127.0.0.1:" + std::to_string(port), "secret");
    check(waitFor([&] { return carol.currentRole() == Role::Offline && carol.snapshot().lastError == "房间已满"; }),
        "a full room turns newcomers away");
    carol.stop();

    check(waitFor([&] {
        const auto snapshot = bob.snapshot();
        const auto* aliceView = find(snapshot, alice.snapshot().localId);
        const auto* hostView = find(snapshot, 1);
        return aliceView && aliceView->hasInfo && aliceView->info.name == "Alice" &&
            aliceView->info.modelPath == "chr_alice.prefab" && aliceView->info.bones.size() == 3 &&
            hostView && hostView->info.name == "Host";
    }), "player info reaches every member through the host");

    const auto aliceId = alice.snapshot().localId;
    // Unreliable poses are relayed only after the UDP hello. Wait for that,
    // then keep publishing the final sample until the other client observes it.
    check(waitFor([&] { return alice.snapshot().pongs > 0 && bob.snapshot().pongs > 0; }, 6000),
        "UDP heartbeat receives a real pong");
    const auto posed = state(9.f, proto::fnv32("map01"));
    check(waitFor([&] {
        alice.setLocalState(posed);
        const auto* aliceView = find(bob.snapshot(), aliceId);
        return aliceView && !aliceView->states.empty() && aliceView->states.back().state.position[0] == 9.f &&
            aliceView->states.back().state.bones.size() == 12;
    }), "pose updates are relayed client to client");
    host.setLocalState(state(42, proto::fnv32("map01")));
    check(waitFor([&] {
        const auto* hostView = find(alice.snapshot(), 1);
        return hostView && !hostView->states.empty() && hostView->states.back().state.position[0] == 42.f;
    }), "host pose reaches clients");

    const char text[] = "hello blocks";
    alice.sendData(proto::fnv32("test.reliable"), proto::kEveryone, text, sizeof text - 1, true);
    check(waitFor([&] { return bobData.load() == 1 && hostData.load() == 1 && bobPayload == text; }),
        "reliable channel data reaches host and other members");
    alice.sendData(proto::fnv32("test.reliable"), bob.snapshot().localId, text, sizeof text - 1, true);
    check(waitFor([&] { return bobData.load() == 2; }) && hostData.load() == 1, "directed data reaches only its recipient");
    for (int i = 0; i < 20 && bobUnreliable.load() == 0; ++i) {
        host.sendData(proto::fnv32("test.fast"), proto::kEveryone, "x", 1, false);
        std::this_thread::sleep_for(std::chrono::milliseconds(50));
    }
    check(bobUnreliable.load() > 0, "unreliable channel data arrives");

    alice.chat("大家好");
    check(waitFor([&] {
        for (const auto& line : bob.chatSince(0)) if (line.text == "大家好" && line.name == "Alice") return true;
        return false;
    }), "chat is relayed with the sender name");

    host.kick(bob.snapshot().localId);
    check(waitFor([&] { return bob.currentRole() == Role::Offline && bob.snapshot().lastError == "你被房主移出了房间"; }),
        "host kick disconnects its target with a reason");
    bob.join("127.0.0.1:" + std::to_string(port), "secret");
    check(waitFor([&] { return bob.currentRole() == Role::Client; }), "a disconnected player can rejoin");

    alice.leave();
    check(waitFor([&] { return find(bob.snapshot(), aliceId) == nullptr; }), "a departure reaches the remaining members");
    check(alicePeerEvents.load() > 0, "peer callbacks fire");

    {
        Net dave;
        std::atomic<int> daveFast = 0, bobFromDave = 0;
        dave.onData = [&](std::uint16_t, std::uint32_t channel, const unsigned char*, std::size_t) {
            if (channel == proto::fnv32("test.fast")) ++daveFast;
        };
        bob.onData = [&](std::uint16_t, std::uint32_t channel, const unsigned char*, std::size_t) {
            if (channel == proto::fnv32("test.slowpath")) ++bobFromDave;
        };
        dave.setName("Dave");
        dave.version = EFLINK_VERSION;
        dave.setLocalInfo(info("chr_dave.prefab", "map01"));
        dave.simulateTcpOnly(true);
        dave.start(discovery);
        dave.join("127.0.0.1:" + std::to_string(port), "secret");
        check(waitFor([&] { return dave.currentRole() == Role::Client; }), "a member without UDP joins over TCP");
        check(waitFor([&] { return dave.snapshot().tcpOnlyLinks == 1 && host.snapshot().tcpOnlyLinks == 1; }, 8000),
            "both ends report the link as TCP only");
        for (int i = 0; i < 40 && daveFast.load() == 0; ++i) {
            host.sendData(proto::fnv32("test.fast"), proto::kEveryone, "x", 1, false);
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
        }
        check(daveFast.load() > 0, "unreliable data reaches a TCP-only member");
        for (int i = 0; i < 40 && bobFromDave.load() == 0; ++i) {
            dave.sendData(proto::fnv32("test.slowpath"), proto::kEveryone, "y", 1, false);
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
        }
        check(bobFromDave.load() > 0, "unreliable data from a TCP-only member is relayed");
        const auto daveId = dave.snapshot().localId;
        for (int i = 0; i < 10; ++i) {
            dave.setLocalState(state(static_cast<float>(70 + i), proto::fnv32("map01")));
            std::this_thread::sleep_for(std::chrono::milliseconds(30));
        }
        check(waitFor([&] {
            const auto* daveView = find(bob.snapshot(), daveId);
            return daveView && !daveView->states.empty() && daveView->states.back().state.position[0] == 79.f;
        }), "poses of a TCP-only member reach the others");
        dave.leave();
        check(waitFor([&] { return find(bob.snapshot(), daveId) == nullptr; }), "the TCP-only member leaves cleanly");
        dave.stop();
    }

    host.leave();
    check(waitFor([&] { return bob.currentRole() == Role::Offline; }), "closing the room disconnects members");
    check(bob.snapshot().lastError == "房主关闭了房间", "members learn the host closed the room");
    host.stop();
    alice.stop();
    bob.stop();
    std::printf("%s\n", failures ? "FAILED" : "all link network checks passed");
    return failures ? 1 : 0;
}
