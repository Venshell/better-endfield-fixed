#pragma once

#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <span>
#include <string>
#include <utility>
#include <vector>

namespace BetterEndfieldNext::FirstPersonHeadwear {

inline bool SafeMeshName(const std::string& name) {
    return !name.empty() && name.size() <= 160 &&
        std::all_of(name.begin(), name.end(), [](unsigned char c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') || c == '_';
        });
}

inline bool PrimaryRendererName(const std::string& renderer, const std::string& mesh) {
    if (renderer == mesh) return true;
    const auto suffix = mesh.rfind('_');
    return suffix != std::string::npos && suffix + 1 < mesh.size() &&
        mesh.substr(0, suffix) == renderer &&
        std::all_of(mesh.begin() + suffix + 1, mesh.end(), [](unsigned char c) { return c >= '0' && c <= '9'; });
}

struct Fixture {
    struct Draw { uint32_t first_index, index_count, base_vertex; };
    std::string mesh_name;
    uint32_t vertex_count = 0;
    uint32_t index_count = 0;
    uint32_t bone_count = 0;
    uint32_t hidden_triangles = 0;
    uint32_t original_index_crc = 0;
    uint32_t index_element_size = 0;
    std::vector<Draw> draws;
    std::array<uint32_t, 3> strides{};
    std::vector<std::array<int32_t, 4>> attributes;
    std::array<std::vector<uint8_t>, 3> streams;
    std::vector<uint8_t> indices;
};

// MeshData keeps complete draw descriptors after source CPU buffers disappear.
// Index width is read from Mesh.indexFormat, independently of CPU byte length.
inline bool MatchesDrawMetadata(const Fixture& fixture,
    std::span<const std::array<int64_t, 4>> descriptors, uint32_t index_element_size) {
    if (descriptors.size() != fixture.draws.size() ||
        index_element_size != fixture.index_element_size) return false;
    for (size_t i=0;i<descriptors.size();++i) {
        const auto& sub=descriptors[i];
        const auto& draw=fixture.draws[i];
        if (sub[0] != 0 || sub[1] != draw.first_index || sub[2] != draw.index_count ||
            sub[3] != draw.base_vertex) return false;
    }
    return true;
}

inline uint32_t Read32(std::span<const uint8_t> bytes, size_t offset) {
    return uint32_t(bytes[offset]) | (uint32_t(bytes[offset + 1]) << 8) |
           (uint32_t(bytes[offset + 2]) << 16) | (uint32_t(bytes[offset + 3]) << 24);
}

inline uint32_t Crc32(std::span<const uint8_t> bytes) {
    uint32_t crc = 0xffffffffu;
    for (uint8_t byte : bytes) {
        crc ^= byte;
        for (int bit = 0; bit < 8; ++bit)
            crc = (crc >> 1) ^ (0xedb88320u & (0u - (crc & 1u)));
    }
    return ~crc;
}

inline bool Parse(std::span<const uint8_t> bytes, Fixture& out, std::string& error) {
    constexpr size_t kHeader = 84;
    constexpr std::array<uint8_t, 8> kMagic{'B', 'E', 'H', 'W', 'M', 'E', 'S', 'H'};
    if (bytes.size() < kHeader || bytes.size() > 16u * 1024u * 1024u ||
        !std::equal(kMagic.begin(), kMagic.end(), bytes.begin())) {
        error = "fixture header or size invalid";
        return false;
    }
    auto word = [&](size_t n) { return Read32(bytes, 8 + n * 4); };
    const uint32_t version = word(0), header_size = word(1);
    const uint32_t vertices = word(2), indices = word(3), bones = word(4);
    const uint32_t hidden = word(5), attributes = word(6), streams = word(7);
    const std::array<uint32_t, 3> sizes{word(8), word(9), word(10)};
    const std::array<uint32_t, 3> strides{word(11), word(12), word(13)};
    const uint32_t payload_crc = word(14), original_crc = word(15), name_size = word(16);
    const uint32_t index_size = word(17), draws = word(18);
    const bool rigid = strides[2] == 4;
    const uint32_t expected_attributes = (strides[1] == 16 ? 4u : 3u) + (rigid ? 1u : 2u);
    if (version != 3 || vertices == 0 || vertices > 262144 || indices < 6 ||
        indices > 1200000 || indices % 3 != 0 || bones == 0 || bones > 256 ||
        (index_size != 2 && index_size != 4) || draws == 0 || draws > 8 ||
        (index_size == 2 && vertices > 65535) ||
        hidden == 0 || hidden > indices / 3 || attributes != expected_attributes || streams != 3 ||
        name_size == 0 || name_size > 160 ||
        strides[0] != 16 || (strides[1] != 8 && strides[1] != 16) ||
        (strides[2] != 4 && strides[2] != 12) || original_crc == 0 ||
        header_size != kHeader + attributes * 16 + draws * 12 + name_size || header_size > bytes.size()) {
        error = "fixture mesh contract differs";
        return false;
    }
    size_t payload_size = indices * index_size;
    for (size_t i = 0; i < 3; ++i) {
        if (sizes[i] != vertices * strides[i]) {
            error = "fixture stream size differs";
            return false;
        }
        payload_size += sizes[i];
    }
    if (header_size + payload_size != bytes.size() ||
        Crc32(bytes.subspan(header_size)) != payload_crc) {
        error = "fixture length or checksum differs";
        return false;
    }
    constexpr std::array<std::array<int32_t, 4>, 6> kAttributes{{
        {0, 0, 3, 0}, {1, 0, 1, 0}, {4, 0, 2, 1},
        {5, 0, 2, 1}, {12, 4, 4, 2}, {13, 6, 4, 2}}};
    Fixture parsed;
    parsed.mesh_name.assign(reinterpret_cast<const char*>(bytes.data() + kHeader + attributes * 16 + draws * 12), name_size);
    if (!SafeMeshName(parsed.mesh_name)) {
        error = "fixture mesh name invalid";
        return false;
    }
    parsed.vertex_count = vertices;
    parsed.index_count = indices;
    parsed.bone_count = bones;
    parsed.hidden_triangles = hidden;
    parsed.original_index_crc = original_crc;
    parsed.index_element_size = index_size;
    parsed.strides = strides;
    for (size_t i = 0; i < attributes; ++i) {
        std::array<int32_t, 4> descriptor{};
        for (size_t j = 0; j < 4; ++j)
            descriptor[j] = static_cast<int32_t>(Read32(bytes, kHeader + i * 16 + j * 4));
        size_t expected = i;
        if (strides[1] == 8 && expected >= 3) ++expected;
        if (rigid && expected >= 4) ++expected;
        if (descriptor != kAttributes[expected]) {
            error = "fixture vertex attribute differs";
            return false;
        }
        parsed.attributes.push_back(descriptor);
    }
    uint32_t covered = 0;
    for (size_t i = 0; i < draws; ++i) {
        const size_t offset = kHeader + attributes * 16 + i * 12;
        Fixture::Draw draw{Read32(bytes, offset), Read32(bytes, offset + 4), Read32(bytes, offset + 8)};
        if (draw.first_index != covered || draw.index_count == 0 || draw.index_count % 3 != 0 ||
            draw.index_count > indices - covered || draw.base_vertex != 0) {
            error = "fixture draw partition differs";
            return false;
        }
        covered += draw.index_count;
        parsed.draws.push_back(draw);
    }
    if (covered != indices) {
        error = "fixture draws do not cover index buffer";
        return false;
    }
    size_t offset = header_size;
    for (size_t i = 0; i < 3; ++i) {
        parsed.streams[i].assign(bytes.begin() + offset, bytes.begin() + offset + sizes[i]);
        offset += sizes[i];
    }
    for (size_t vertex = 0; vertex < vertices; ++vertex) {
        for (size_t influence = 0; influence < 4; ++influence) {
            const uint8_t index = parsed.streams[2][vertex * strides[2] + (rigid ? 0 : 8) + influence];
            if (index >= bones || (rigid && influence != 0 && index != 0)) {
                error = "fixture skin index exceeds bone count";
                return false;
            }
        }
    }
    parsed.indices.assign(bytes.begin() + offset, bytes.end());
    for (size_t i = 0; i < parsed.indices.size(); i += index_size) {
        const uint32_t index = index_size == 4 ? Read32(parsed.indices, i) :
            parsed.indices[i] | (uint32_t(parsed.indices[i + 1]) << 8);
        if (index >= vertices) {
            error = "fixture index exceeds vertex count";
            return false;
        }
    }
    out = std::move(parsed);
    error.clear();
    return true;
}
}
