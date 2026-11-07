#include "../modules/camera/first_person_cap_vertices.h"
#include <iostream>
#include <stdexcept>

using namespace BetterEndfieldNext::FirstPersonMesh;
void Check(bool condition, const char* message) {
    if (!condition) throw std::runtime_error(message);
}
int main() {
    try {
        std::vector<Vertex> vertices(4);
        vertices[0].position = {0, 0, 0}; vertices[1].position = {2, 0, 0};
        vertices[2].position = {0, 2, 0}; vertices[3].position = {0, 0, 3};
        const std::vector<Part> original{{{0, 1, 2}}, {{0, 2, 3}}};
        Result built;
        built.parts = {{{0, 0, 0, 0, 1, 2}}, {{0, 2, 3, 0, 2, 3}}};
        built.cap_triangles = 2;
        const auto result = BuildCapVertices(vertices, original, built);
        Check(result.status == CapVertexStatus::Ok, "valid caps must allocate independent vertices");
        Check(result.parts[0].indices == std::vector<uint32_t>({0, 0, 0, 4, 5, 6}), "first part original prefix must remain unchanged");
        Check(result.parts[1].indices == std::vector<uint32_t>({0, 2, 3, 7, 8, 9}), "second part must use disjoint appended vertices");
        Check(result.source_map == std::vector<uint32_t>({0, 1, 2, 0, 2, 3}), "source map must preserve every source vertex including skin attributes");
        Check(result.normals.size() == 6 && result.normals[0].z == 1 && result.normals[3].x == 1, "normals must follow each cap winding independently");
        Check(result.bounds.minimum.x == 0 && result.bounds.maximum.x == 2 && result.bounds.maximum.y == 2 && result.bounds.maximum.z == 3, "bounds must include original vertex positions");
        Check(original[0].indices == std::vector<uint32_t>({0, 1, 2}) && built.parts[0].indices == std::vector<uint32_t>({0, 0, 0, 0, 1, 2}), "inputs must remain unchanged");
        const auto budget = BuildCapVertices(vertices, original, built, 9);
        Check(budget.status == CapVertexStatus::VertexBudgetExceeded && budget.parts.empty() && budget.source_map.empty() && budget.normals.empty(), "vertex budget failure must not expose a partial rewrite");
        const auto index_budget = BuildCapVertices(vertices, original, built, 200000, 11);
        Check(index_budget.status == CapVertexStatus::IndexBudgetExceeded && index_budget.parts.empty(), "aggregate index budget must cover every part");
        Result degenerate = built;
        degenerate.parts[1].indices = {0, 2, 3, 0, 0, 3};
        const auto bad = BuildCapVertices(vertices, original, degenerate);
        Check(bad.status == CapVertexStatus::DegenerateCap && bad.parts.empty() && bad.source_map.empty() && bad.normals.empty(), "later degenerate cap must discard earlier rewritten caps");
        Result existing;
        existing.parts = built.parts; // Already present cap indices are part of the source mesh.
        const auto unchanged = BuildCapVertices(vertices, existing.parts, existing);
        Check(unchanged.status == CapVertexStatus::Ok && unchanged.source_map.empty() && unchanged.parts[0].indices == existing.parts[0].indices,
            "previously present caps must not be duplicated");
        auto mismatch = built;
        mismatch.cap_triangles = 1;
        Check(BuildCapVertices(vertices, original, mismatch).status == CapVertexStatus::CapCountMismatch, "cap metadata mismatch must be rejected");
        mismatch = built;
        mismatch.parts[0].indices.back() = 99;
        Check(BuildCapVertices(vertices, original, mismatch).status == CapVertexStatus::InvalidIndex, "out of range cap index accepted");
        mismatch = built;
        mismatch.parts[0].indices.pop_back();
        Check(BuildCapVertices(vertices, original, mismatch).status == CapVertexStatus::InvalidIndexCount, "incomplete triangle accepted");
        mismatch = built;
        mismatch.parts.pop_back();
        Check(BuildCapVertices(vertices, original, mismatch).status == CapVertexStatus::InvalidPartCount, "part count mismatch accepted");
        mismatch = built;
        mismatch.error = "source rejected";
        Check(BuildCapVertices(vertices, original, mismatch).status == CapVertexStatus::SourceFailure, "source failure accepted");
        auto invalid_vertices = vertices;
        invalid_vertices[1].position.x = std::numeric_limits<double>::quiet_NaN();
        Check(BuildCapVertices(invalid_vertices, original, built).status == CapVertexStatus::InvalidVertex, "non-finite bounds accepted");
        auto full_vertices = vertices;
        full_vertices.resize(200000);
        Check(BuildCapVertices(full_vertices, original, built, 300000).status == CapVertexStatus::VertexBudgetExceeded, "caller must not bypass hard vertex limit");
        Check(BuildCapVertices(vertices, original, built, 10, 12).status == CapVertexStatus::Ok, "exact budget must succeed");
        Result too_many_indices;
        too_many_indices.parts = {Part{std::vector<uint32_t>(6000003, 0)}};
        Check(BuildCapVertices(vertices, {Part{}}, too_many_indices, 200000, 7000000).status == CapVertexStatus::IndexBudgetExceeded,
            "caller must not bypass six-million hard index budget");
        std::vector<Vertex> ring;
        for (int layer = 0; layer < 2; ++layer) for (int corner = 0; corner < 8; ++corner) {
            const double angle = corner * 6.283185307179586 / 8;
            Vertex vertex;
            vertex.position = {.05 * std::cos(angle), layer ? 0 : -.3, .05 * std::sin(angle)};
            vertex.weight[0] = 1;
            ring.push_back(vertex);
        }
        Part sides;
        for (uint32_t corner = 0; corner < 8; ++corner) {
            const uint32_t next = (corner + 1) % 8;
            sides.indices.insert(sides.indices.end(), {corner, corner + 8, next + 8, corner, next + 8, next});
        }
        const auto actual_build = Build(ring, {sides}, {2}, {{0, 0, 0}, {0, 1, 0}, .1}, false, true);
        const auto actual_cap = BuildCapVertices(ring, {sides}, actual_build);
        Check(actual_cap.status == CapVertexStatus::Ok && actual_cap.source_map.size() == 18,
            "real open neck Build result must allocate six independent cap triangles");
        for (size_t corner = 0; corner < actual_cap.source_map.size(); ++corner) {
            Check(actual_cap.source_map[corner] < ring.size() && Finite(actual_cap.normals[corner]) &&
                std::abs(Length(actual_cap.normals[corner]) - 1) < 1e-12, "real cap source or normal invalid");
        }
        std::cout << "first_person_cap_vertices: multi-part, source map, normals, bounds, budgets, rejection, existing caps and Build integration passed\n";
        return 0;
    } catch (const std::exception& error) { std::cerr << error.what() << '\n'; return 1; }
}
