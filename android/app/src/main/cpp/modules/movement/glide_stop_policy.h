#pragma once

#include <cstdint>

namespace BetterEndfield::Movement {

// A soft stop may preserve an already-running glide for this fixed diagnostic
// safety budget. It does not promise that the player will land before expiry
// and is deliberately not configurable by callers. A hard deadline can end
// projection while the player is still airborne; any reported fault ends it
// immediately.
constexpr std::uint64_t kGlideNormalDrainMaxMs = 5000;

enum class GlideStopPhase : std::uint8_t {
    Running,
    NormalDrain,
    Finished,
};

enum class GlideStopFault : std::uint8_t {
    None,
    Identity,
    Read,
    Type,
    NonFinite,
    Budget,
    Exception,
    ClockRegression,
    InvalidState,
};

enum class GlideStopReason : std::uint8_t {
    None,
    SoftDeadlineWithoutDrain,
    DrainDeadline,
    HardDeadline,
    Landed,
    CandidateStopped,
    Fault,
};

struct GlideStopState {
    GlideStopPhase phase = GlideStopPhase::Running;
    bool has_last_time = false;
    std::uint64_t last_time_ms = 0;
    std::uint64_t drain_started_ms = 0;
    GlideStopReason finish_reason = GlideStopReason::None;
    GlideStopFault finish_fault = GlideStopFault::None;
};

struct GlideStopInput {
    std::uint64_t monotonic_time_ms = 0;
    bool soft_deadline_reached = false;
    bool hard_deadline_reached = false;
    bool candidate_active = false;
    bool identity_trusted = false;
    bool airborne_known = false;
    bool airborne = false;
    bool manually_collapsed = false;
    GlideStopFault fault = GlideStopFault::None;
};

struct GlideStopOutput {
    GlideStopState next_state{};
    GlideStopPhase phase = GlideStopPhase::Running;
    GlideStopReason reason = GlideStopReason::None;
    GlideStopFault fault = GlideStopFault::None;
    bool preserve_projection = false;
    bool allow_new_activation = false;
};

// Pure transition: callers provide the prior state and current observations;
// the result contains all state and permissions for the next step.
GlideStopOutput StepGlideStopPolicy(
    const GlideStopState& state,
    const GlideStopInput& input) noexcept;

}  // namespace BetterEndfield::Movement
