#include "glide_stop_policy.h"

namespace BetterEndfield::Movement {
namespace {

bool IsValidPhase(GlideStopPhase phase) noexcept {
    return phase == GlideStopPhase::Running ||
        phase == GlideStopPhase::NormalDrain ||
        phase == GlideStopPhase::Finished;
}

bool IsValidFault(GlideStopFault fault) noexcept {
    return fault == GlideStopFault::None ||
        fault == GlideStopFault::Identity ||
        fault == GlideStopFault::Read ||
        fault == GlideStopFault::Type ||
        fault == GlideStopFault::NonFinite ||
        fault == GlideStopFault::Budget ||
        fault == GlideStopFault::Exception ||
        fault == GlideStopFault::ClockRegression ||
        fault == GlideStopFault::InvalidState;
}

bool IsValidReason(GlideStopReason reason) noexcept {
    return reason == GlideStopReason::None ||
        reason == GlideStopReason::SoftDeadlineWithoutDrain ||
        reason == GlideStopReason::DrainDeadline ||
        reason == GlideStopReason::HardDeadline ||
        reason == GlideStopReason::Landed ||
        reason == GlideStopReason::CandidateStopped ||
        reason == GlideStopReason::Fault;
}

bool IsConsistent(const GlideStopState& state) noexcept {
    if (!IsValidPhase(state.phase) || !IsValidReason(state.finish_reason) ||
        !IsValidFault(state.finish_fault)) {
        return false;
    }
    if (state.phase == GlideStopPhase::Finished) {
        return state.finish_reason != GlideStopReason::None &&
            ((state.finish_reason == GlideStopReason::Fault) ==
             (state.finish_fault != GlideStopFault::None));
    }
    if (state.finish_reason != GlideStopReason::None ||
        state.finish_fault != GlideStopFault::None) {
        return false;
    }
    if (state.phase == GlideStopPhase::NormalDrain) {
        return state.has_last_time && state.last_time_ms >= state.drain_started_ms;
    }
    return true;
}

GlideStopOutput MakeOutput(const GlideStopState& state) noexcept {
    GlideStopOutput output;
    output.next_state = state;
    output.phase = state.phase;
    output.reason = state.finish_reason;
    output.fault = state.finish_fault;
    return output;
}

GlideStopOutput Finish(
    GlideStopState state,
    GlideStopReason reason,
    GlideStopFault fault = GlideStopFault::None) noexcept {
    state.phase = GlideStopPhase::Finished;
    state.finish_reason = reason;
    state.finish_fault = fault;
    return MakeOutput(state);
}

GlideStopOutput FinishFault(
    GlideStopState state,
    GlideStopFault fault) noexcept {
    if (fault == GlideStopFault::None || !IsValidFault(fault)) {
        fault = GlideStopFault::Type;
    }
    return Finish(state, GlideStopReason::Fault, fault);
}

}  // namespace

GlideStopOutput StepGlideStopPolicy(
    const GlideStopState& prior_state,
    const GlideStopInput& input) noexcept {
    // A terminal state is immutable, including its original reason.
    if (prior_state.phase == GlideStopPhase::Finished &&
        IsConsistent(prior_state)) {
        return MakeOutput(prior_state);
    }

    GlideStopState state = prior_state;
    if (!IsConsistent(state)) {
        return FinishFault(state, GlideStopFault::InvalidState);
    }
    if (input.fault != GlideStopFault::None) {
        return FinishFault(state, input.fault);
    }
    if (state.has_last_time && input.monotonic_time_ms < state.last_time_ms) {
        return FinishFault(state, GlideStopFault::ClockRegression);
    }
    state.has_last_time = true;
    state.last_time_ms = input.monotonic_time_ms;

    if (input.hard_deadline_reached) {
        return Finish(state, GlideStopReason::HardDeadline);
    }
    if (input.manually_collapsed) {
        return Finish(state, GlideStopReason::CandidateStopped);
    }

    if (input.candidate_active && !input.identity_trusted) {
        return FinishFault(state, GlideStopFault::Identity);
    }
    if (input.candidate_active && !input.airborne_known) {
        return FinishFault(state, GlideStopFault::Read);
    }
    if (input.candidate_active && !input.airborne) {
        return Finish(state, GlideStopReason::Landed);
    }

    if (state.phase == GlideStopPhase::NormalDrain) {
        if (!input.candidate_active) {
            return Finish(state, GlideStopReason::CandidateStopped);
        }
        // Subtraction after an ordering check avoids start+duration overflow.
        const std::uint64_t elapsed_ms =
            input.monotonic_time_ms - state.drain_started_ms;
        if (elapsed_ms >= kGlideNormalDrainMaxMs) {
            return Finish(state, GlideStopReason::DrainDeadline);
        }
        GlideStopOutput output = MakeOutput(state);
        output.preserve_projection = true;
        return output;
    }

    if (input.soft_deadline_reached) {
        if (!input.candidate_active) {
            return Finish(state, GlideStopReason::SoftDeadlineWithoutDrain);
        }
        state.phase = GlideStopPhase::NormalDrain;
        state.drain_started_ms = input.monotonic_time_ms;
        GlideStopOutput output = MakeOutput(state);
        output.allow_new_activation = false;
        output.preserve_projection = true;
        return output;
    }

    GlideStopOutput output = MakeOutput(state);
    output.allow_new_activation = true;
    output.preserve_projection = input.candidate_active;
    return output;
}

}  // namespace BetterEndfield::Movement
