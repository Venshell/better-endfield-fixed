#pragma once
#include "first_person_mesh.h"
namespace BetterEndfieldNext::FirstPersonMesh {
enum class CapVertexStatus { Ok, SourceFailure, InvalidVertexCount, InvalidPartCount,
    InvalidIndexCount, InvalidIndex, InvalidVertex, CapCountMismatch,
    VertexBudgetExceeded, IndexBudgetExceeded, DegenerateCap };
struct CapVertexBounds { Point minimum, maximum; };
struct CapVertexResult {
    CapVertexStatus status = CapVertexStatus::SourceFailure;
    std::vector<Part> parts;
    std::vector<uint32_t> source_map;
    std::vector<Point> normals;
    CapVertexBounds bounds;
};
// Build preserves the source index prefix and appends its cap triangles. Each
// cap corner receives a new vertex so changing its normal never changes skin.
// source_map tells the runtime to copy every source stream before replacing
// only the new normal. No interpolated weights or synthetic positions exist.
inline CapVertexResult BuildCapVertices(const std::vector<Vertex>& vertices,
    const std::vector<Part>& original_parts, const Result& built,
    size_t vertex_budget = 200000, size_t index_budget = 6000000) {
    const auto fail = [](CapVertexStatus status) {
        CapVertexResult failure;
        failure.status = status;
        return failure;
    };
    if (!built.error.empty()) return fail(CapVertexStatus::SourceFailure);
    if (vertices.empty() || vertices.size() > 200000) return fail(CapVertexStatus::InvalidVertexCount);
    if (original_parts.size() != built.parts.size()) return fail(CapVertexStatus::InvalidPartCount);
    vertex_budget = std::min<size_t>(vertex_budget, 200000);
    index_budget = std::min<size_t>(index_budget, 6000000);
    size_t total_indices = 0, added_indices = 0;
    for (size_t part = 0; part < built.parts.size(); ++part) {
        const auto& original = original_parts[part].indices;
        const auto& updated = built.parts[part].indices;
        if (original.size() % 3 || updated.size() % 3 || updated.size() < original.size())
            return fail(CapVertexStatus::InvalidIndexCount);
        if (updated.size() > index_budget - total_indices) return fail(CapVertexStatus::IndexBudgetExceeded);
        total_indices += updated.size();
        added_indices += updated.size() - original.size();
        for (auto index : original) if (index >= vertices.size()) return fail(CapVertexStatus::InvalidIndex);
        for (auto index : updated) if (index >= vertices.size()) return fail(CapVertexStatus::InvalidIndex);
    }
    if (added_indices / 3 != built.cap_triangles) return fail(CapVertexStatus::CapCountMismatch);
    if (vertices.size() > vertex_budget || added_indices > vertex_budget - vertices.size())
        return fail(CapVertexStatus::VertexBudgetExceeded);
    for (const auto& vertex : vertices)
        if (!Finite(vertex.position) || Length(vertex.position) > 100000) return fail(CapVertexStatus::InvalidVertex);
    CapVertexResult result;
    result.status = CapVertexStatus::Ok;
    result.source_map.reserve(added_indices);
    result.normals.reserve(added_indices);
    result.parts = built.parts;
    if (!vertices.empty()) {
        result.bounds.minimum = vertices.front().position;
        result.bounds.maximum = vertices.front().position;
    }
    for (const auto& vertex : vertices) {
        result.bounds.minimum.x = std::min(result.bounds.minimum.x, vertex.position.x);
        result.bounds.minimum.y = std::min(result.bounds.minimum.y, vertex.position.y);
        result.bounds.minimum.z = std::min(result.bounds.minimum.z, vertex.position.z);
        result.bounds.maximum.x = std::max(result.bounds.maximum.x, vertex.position.x);
        result.bounds.maximum.y = std::max(result.bounds.maximum.y, vertex.position.y);
        result.bounds.maximum.z = std::max(result.bounds.maximum.z, vertex.position.z);
    }
    for (size_t part = 0; part < result.parts.size(); ++part) {
        auto& indices = result.parts[part].indices;
        for (size_t index = original_parts[part].indices.size(); index < indices.size(); index += 3) {
            const auto a = vertices[indices[index]].position;
            const auto b = vertices[indices[index + 1]].position;
            const auto c = vertices[indices[index + 2]].position;
            const auto cross = Cross(b - a, c - a);
            const double area = Length(cross);
            if (!Finite(cross) || !std::isfinite(area) || area < 1e-12) return fail(CapVertexStatus::DegenerateCap);
            const auto normal = cross * (1 / area);
            for (size_t corner = 0; corner < 3; ++corner) {
                const uint32_t source = indices[index + corner];
                indices[index + corner] = static_cast<uint32_t>(vertices.size() + result.source_map.size());
                result.source_map.push_back(source);
                result.normals.push_back(normal);
            }
        }
    }
    return result;
}
} // namespace BetterEndfieldNext::FirstPersonMesh
