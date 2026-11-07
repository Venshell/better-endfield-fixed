#pragma once
#include <cstddef>
#include <cstdint>

namespace BetterEndfieldNext::FirstPerson {
enum class ReadbackError { None, InvalidSize, OutOfRange, BudgetExceeded };
struct ReadbackPlan {
    ReadbackError error = ReadbackError::None;
    uint64_t buffer_bytes = 0;
    size_t read_bytes = 0;
};
inline ReadbackPlan PlanReadback(int count, int stride, size_t expected) {
    if (count <= 0 || stride <= 0 || !expected) return {ReadbackError::InvalidSize};
    const uint64_t bytes = uint64_t(count) * uint64_t(stride);
    if (bytes > 128ull * 1024 * 1024 || expected > 128ull * 1024 * 1024)
        return {ReadbackError::BudgetExceeded};
    if (expected > bytes) return {ReadbackError::OutOfRange};
    return {ReadbackError::None, bytes, expected};
}
} // namespace BetterEndfieldNext::FirstPerson
