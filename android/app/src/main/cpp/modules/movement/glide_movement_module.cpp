#include "modules/movement/glide_movement_module.h"
#include "BetterEndfield/AutoGlideBridge.h"

#include "core/log.h"

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <exception>
#include <limits>
#include <string>
#include <type_traits>

namespace betterendfieldnext {
namespace {

using BetterEndfield::Movement::GlideConfig;
using BetterEndfield::Movement::GlideInput;
using BetterEndfield::Movement::GlideTransition;

constexpr char kAssembly[] = "Gameplay.Beyond.dll";
constexpr char kNamespace[] = "Beyond.Gameplay.Core";
constexpr auto kStartupLimit = std::chrono::seconds(240);
constexpr auto kObservationLimit = std::chrono::seconds(60);
constexpr auto kMaximumRunTime = kStartupLimit + kObservationLimit;
constexpr uint32_t kCallbackLimit = 30000;
constexpr uint32_t kGetterCallLimit = 150000;
constexpr uint32_t kMaximumLogLines = 256;
constexpr uint32_t kMaximumWriteLogSamples = 32;
constexpr uint32_t kMaximumCompareLogSamples = 32;
constexpr uint32_t kWriteModeStateLogLimit = kMaximumLogLines -
    kMaximumWriteLogSamples - kMaximumCompareLogSamples - 1 - 3 - 1 - 1;
constexpr uint32_t kMaximumStopLogSamples = 3;
constexpr float kLogVelocityYChange = 0.5f;
constexpr char kAirPipeline[] = "MovePipelineAir";

const char* StopPhaseName(BetterEndfield::Movement::GlideStopPhase phase) {
    using BetterEndfield::Movement::GlideStopPhase;
    switch (phase) {
        case GlideStopPhase::Running: return "Running";
        case GlideStopPhase::NormalDrain: return "NormalDrain";
        case GlideStopPhase::Finished: return "Finished";
    }
    return "Invalid";
}

const char* StopReasonName(BetterEndfield::Movement::GlideStopReason reason) {
    using BetterEndfield::Movement::GlideStopReason;
    switch (reason) {
        case GlideStopReason::None: return "None";
        case GlideStopReason::SoftDeadlineWithoutDrain: return "SoftDeadlineWithoutDrain";
        case GlideStopReason::DrainDeadline: return "DrainDeadline";
        case GlideStopReason::HardDeadline: return "HardDeadline";
        case GlideStopReason::Landed: return "Landed";
        case GlideStopReason::CandidateStopped: return "CandidateStopped";
        case GlideStopReason::Fault: return "Fault";
    }
    return "Invalid";
}

const char* StopFaultName(BetterEndfield::Movement::GlideStopFault fault) {
    using BetterEndfield::Movement::GlideStopFault;
    switch (fault) {
        case GlideStopFault::None: return "None";
        case GlideStopFault::Identity: return "Identity";
        case GlideStopFault::Read: return "Read";
        case GlideStopFault::Type: return "Type";
        case GlideStopFault::NonFinite: return "NonFinite";
        case GlideStopFault::Budget: return "Budget";
        case GlideStopFault::Exception: return "Exception";
        case GlideStopFault::ClockRegression: return "ClockRegression";
        case GlideStopFault::InvalidState: return "InvalidState";
    }
    return "Invalid";
}

static_assert(sizeof(void*) == 8, "glide movement hook requires a 64-bit ABI");
static_assert(std::is_standard_layout_v<GlideMovementModule::Vector3>);
static_assert(sizeof(GlideMovementModule::Vector3) == 3 * sizeof(float));
static_assert(alignof(GlideMovementModule::Vector3) == alignof(float));
static_assert(offsetof(GlideMovementModule::Vector3, x) == 0);
static_assert(offsetof(GlideMovementModule::Vector3, y) == sizeof(float));
static_assert(offsetof(GlideMovementModule::Vector3, z) == 2 * sizeof(float));

int64_t SteadyNanoseconds(std::chrono::steady_clock::time_point value) {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
        value.time_since_epoch()).count();
}

struct LocalGcHandle {
    Il2CppRuntime* runtime = nullptr;
    uint32_t value = 0;

    ~LocalGcHandle() {
        if (runtime != nullptr && value != 0) runtime->FreeGcHandle(value);
    }

    bool Hold(Il2CppRuntime& owner, void* object) {
        runtime = &owner;
        value = owner.NewGcHandle(object, true);
        return value != 0;
    }
};

const char* TransitionName(GlideTransition transition) {
    switch (transition) {
        case GlideTransition::NoChange: return "NoChange";
        case GlideTransition::Activated: return "Activated";
        case GlideTransition::AutoActivated: return "AutoActivated";
        case GlideTransition::Deactivated: return "Deactivated";
        case GlideTransition::Cleared: return "Cleared";
        case GlideTransition::DuplicatePress: return "DuplicatePress";
        case GlideTransition::RejectedSession: return "RejectedSession";
        case GlideTransition::RejectedConfiguration: return "RejectedConfiguration";
    }
    return "Unknown";
}

}  // namespace

std::atomic<GlideMovementModule*> GlideMovementModule::active_{nullptr};
std::atomic_bool GlideMovementModule::lifetime_claimed_{false};
std::mutex GlideMovementModule::active_mutex_;
GlideMovementModule::JumpFn GlideMovementModule::jump_original_ = nullptr;
GlideMovementModule::FixedTickFn GlideMovementModule::fixed_tick_original_ = nullptr;
GlideMovementModule::CalcVelocityFallingFn
    GlideMovementModule::calc_velocity_falling_original_ = nullptr;

GlideMovementModule::GlideMovementModule(
        BetterEndfield::Movement::GlideConfig product_config)
    : product_config_(product_config) {}

bool GlideMovementModule::IsProbeMode(Mode mode) noexcept {
    return mode == Mode::dry_run_probe || mode == Mode::write_probe;
}

BetterEndfield::Movement::GlideConfig
GlideMovementModule::EffectiveConfig() const noexcept {
    if (mode_ == Mode::product) return product_config_;
    return {true, BetterEndfield::Movement::kDefaultGlideDescentSpeed};
}

uint32_t GlideMovementModule::CountCallback() noexcept {
    uint32_t current = callbacks_.load(std::memory_order_relaxed);
    while (current != UINT32_MAX && !callbacks_.compare_exchange_weak(current,
            current + 1, std::memory_order_acq_rel,
            std::memory_order_relaxed)) {}
    return current == UINT32_MAX ? current : current + 1;
}

uint32_t GlideMovementModule::CountGetterCall() noexcept {
    uint32_t current = getter_calls_.load(std::memory_order_relaxed);
    while (current != UINT32_MAX && !getter_calls_.compare_exchange_weak(
            current, current + 1, std::memory_order_acq_rel,
            std::memory_order_relaxed)) {}
    return current == UINT32_MAX ? current : current + 1;
}

GlideMovementModule::~GlideMovementModule() {
    if (jump_stub_ != nullptr || fixed_tick_stub_ != nullptr ||
            calc_velocity_stub_ != nullptr ||
            timer_thread_.joinable()) {
        Finish("module_destroyed", FinishCause::Unknown);
    }
    timer_condition_.notify_all();
    if (timer_thread_.joinable()) timer_thread_.join();
    std::unique_lock lock(in_flight_mutex_);
    in_flight_condition_.wait(lock, [this] {
        return in_flight_.load(std::memory_order_acquire) == 0;
    });
    if (runtime_ != nullptr) {
        if (entity_handle_ != 0) runtime_->FreeGcHandle(entity_handle_);
        if (movement_handle_ != 0) runtime_->FreeGcHandle(movement_handle_);
    }
}

ModuleResult GlideMovementModule::Start(Il2CppRuntime& runtime) {
    const char* dry_run_probe = std::getenv(
        "BETTER_ENDFIELD_GLIDE_DRY_RUN_PROBE");
    const char* write_probe = std::getenv("BETTER_ENDFIELD_GLIDE_WRITE_PROBE");
    const char* state_observer = std::getenv(
        "BETTER_ENDFIELD_GLIDE_STATE_OBSERVER");
    const bool product_requested = product_config_.enabled;
    const bool any_probe_requested =
        (dry_run_probe != nullptr && std::string(dry_run_probe) == "1") ||
        (write_probe != nullptr && std::string(write_probe) == "1") ||
        (state_observer != nullptr && std::string(state_observer) == "1");
    if (product_requested && any_probe_requested)
        return {false,
            "glide product configuration conflicts with an explicit probe; no hooks installed"};
    if (product_requested &&
            !BetterEndfield::Movement::IsValidGlideConfig(product_config_))
        return {false, "glide product configuration rejected: descent speed must be finite and within 2..15 m/s"};
    mode_ = dry_run_probe != nullptr && std::string(dry_run_probe) == "1"
        ? Mode::dry_run_probe
        : write_probe != nullptr && std::string(write_probe) == "1"
            ? Mode::write_probe
            : state_observer != nullptr && std::string(state_observer) == "1"
                ? Mode::observe
                : product_config_.enabled ? Mode::product : Mode::observe;
    if (mode_ == Mode::observe &&
            (state_observer == nullptr || std::string(state_observer) != "1"))
        return {false, "glide movement disabled; no hooks installed"};
    jump_ = runtime.ResolveMethodExact(kAssembly, kNamespace,
        "PlayerController", "Jump", "", "System.Void", 0);
    fixed_tick_ = runtime.ResolveMethodExact(kAssembly, kNamespace,
        "MovementComponent", "FixedTick", "System.Single", "System.Void", 1);
    get_main_character_ = runtime.ResolveMethodExact(kAssembly, kNamespace,
        "PlayerController", "GetMainCharacter", "",
        "Beyond.Gameplay.Core.Entity", 0);
    get_movement_component_ = runtime.ResolveMethodExact(kAssembly, kNamespace,
        "Entity", "get_movementComponent", "",
        "Beyond.Gameplay.Core.MovementComponent", 0);
    get_is_in_air_ = runtime.ResolveMethodExact(kAssembly, kNamespace,
        "MovementComponent", "get_isInAir", "", "System.Boolean", 0);
    player_controller_class_ = runtime.ResolveClass(kAssembly, kNamespace,
        "PlayerController");
    entity_class_ = runtime.ResolveClass(kAssembly, kNamespace, "Entity");
    movement_component_class_ = runtime.ResolveClass(kAssembly, kNamespace,
        "MovementComponent");

    if (mode_ != Mode::observe) {
        calc_velocity_falling_ = runtime.ResolveMethodExact(kAssembly,
            kNamespace, kAirPipeline, "CalcVelocityFalling", "System.Single",
            "UnityEngine.Vector3", 1);
        get_move_comp_ = runtime.ResolveMethodExact(kAssembly, kNamespace,
            "MovePipeline", "get_moveComp", "",
            "Beyond.Gameplay.Core.MovementComponent", 0);
        if (mode_ == Mode::write_probe) {
            get_velocity_ = runtime.ResolveMethodExact(kAssembly, kNamespace,
                "MovementComponent", "get_velocity", "",
                "UnityEngine.Vector3", 0);
        }
        if (mode_ == Mode::product) {
            get_move_vector_ = runtime.ResolveMethodExact(kAssembly, kNamespace,
                "MoveInput", "get_moveVector", "", "UnityEngine.Vector3", 0);
            input_field_ = runtime.ResolveField(kAssembly, kNamespace,
                "MovementComponent", "input");
            move_input_class_ = runtime.ResolveClass(kAssembly, kNamespace,
                "MoveInput");
        }
        air_pipeline_class_ = runtime.ResolveClass(kAssembly, kNamespace,
            kAirPipeline);
    }

    if (jump_.info == nullptr || jump_.entry == nullptr ||
            fixed_tick_.info == nullptr || fixed_tick_.entry == nullptr ||
            get_main_character_.info == nullptr || get_main_character_.entry == nullptr ||
            get_movement_component_.info == nullptr ||
            get_movement_component_.entry == nullptr ||
            get_is_in_air_.info == nullptr || get_is_in_air_.entry == nullptr ||
            player_controller_class_.info == nullptr || entity_class_.info == nullptr ||
            movement_component_class_.info == nullptr) {
        return {false, mode_ == Mode::observe
            ? "glide state observer exact method or type contract missing"
            : mode_ == Mode::dry_run_probe
                ? "glide dry-run probe exact method or type contract missing"
                : mode_ == Mode::write_probe
                    ? "glide write probe exact method or type contract missing"
                    : "glide product exact method or type contract missing"};
    }

    if (mode_ != Mode::observe && (
            calc_velocity_falling_.info == nullptr ||
            calc_velocity_falling_.entry == nullptr ||
            get_move_comp_.info == nullptr || get_move_comp_.entry == nullptr ||
            (mode_ == Mode::write_probe &&
                (get_velocity_.info == nullptr || get_velocity_.entry == nullptr)) ||
            air_pipeline_class_.info == nullptr)) {
        return {false, mode_ == Mode::dry_run_probe
            ? "glide dry-run probe exact method or runtime type contract missing"
            : mode_ == Mode::write_probe
                ? "glide write probe exact method or runtime type contract missing"
                : "glide product exact method or runtime type contract missing"};
    }

#if !defined(__aarch64__) || !defined(BE_GLIDE_CALC_VECTOR3_RETURN_ABI_VERIFIED)
    if (mode_ != Mode::observe) {
        return {false,
            mode_ == Mode::dry_run_probe
                ? "glide dry-run probe disabled until the Android ARM64 Vector3 return ABI is verified"
                : mode_ == Mode::write_probe
                    ? "glide write probe disabled until the Android ARM64 Vector3 return ABI is verified"
                    : "glide product mode disabled until the Android ARM64 Vector3 return ABI is verified"};
    }
#endif

    std::string error;
    if (!broker_.Initialize(error)) {
        return {false, "hook broker initialization failed: " + error};
    }
    runtime_ = &runtime;
    installed_at_ = std::chrono::steady_clock::now();
    if (mode_ != Mode::product) {
        maximum_deadline_ns_.store(
            SteadyNanoseconds(installed_at_ + kMaximumRunTime),
            std::memory_order_release);
        deadline_ns_.store(SteadyNanoseconds(installed_at_ + kStartupLimit),
            std::memory_order_release);
    }
    GlideMovementModule* expected = nullptr;
    if (!active_.compare_exchange_strong(expected, this,
            std::memory_order_acq_rel)) {
        runtime_ = nullptr;
        return {false, "another glide state observer is already active"};
    }
    bool lifetime_available = false;
    if (!lifetime_claimed_.compare_exchange_strong(lifetime_available, true,
            std::memory_order_acq_rel)) {
        active_.store(nullptr, std::memory_order_release);
        runtime_ = nullptr;
        return {false, "glide state observer hooks cannot be restarted"};
    }

    jump_original_ = nullptr;
    fixed_tick_original_ = nullptr;
    calc_velocity_falling_original_ = nullptr;
    if (!broker_.Install(Id(), jump_.entry,
            reinterpret_cast<void*>(&JumpDetour),
            reinterpret_cast<void**>(&jump_original_), jump_stub_, error) ||
            jump_original_ == nullptr) {
        broker_.Remove(jump_stub_);
        active_.store(nullptr, std::memory_order_release);
        // Keep the original trampoline and lifetime claim after a hook attempt:
        // an already-entered relay may still be finishing its detour.
        runtime_ = nullptr;
        return {false, "PlayerController.Jump hook installation failed: " + error};
    }
    if (!broker_.Install(Id(), fixed_tick_.entry,
            reinterpret_cast<void*>(&FixedTickDetour),
            reinterpret_cast<void**>(&fixed_tick_original_), fixed_tick_stub_, error) ||
            fixed_tick_original_ == nullptr) {
        broker_.Remove(fixed_tick_stub_);
        broker_.Remove(jump_stub_);
        active_.store(nullptr, std::memory_order_release);
        // The Jump relay can still be in flight after removal. Retain its
        // original pointer and prohibit restart for this process lifetime.
        runtime_ = nullptr;
        return {false, "MovementComponent.FixedTick hook installation failed; Jump hook rolled back: " + error};
    }

    if (mode_ != Mode::observe &&
            (!broker_.Install(Id(), calc_velocity_falling_.entry,
                reinterpret_cast<void*>(&CalcVelocityFallingDetour),
                reinterpret_cast<void**>(&calc_velocity_falling_original_),
                calc_velocity_stub_, error) ||
             calc_velocity_falling_original_ == nullptr)) {
        broker_.Remove(calc_velocity_stub_);
        broker_.Remove(fixed_tick_stub_);
        broker_.Remove(jump_stub_);
        active_.store(nullptr, std::memory_order_release);
        runtime_ = nullptr;
        return {false,
            "CalcVelocityFalling hook installation failed; movement hooks rolled back: " + error};
    }

    installation_complete_.store(true, std::memory_order_release);
    log_lines_ = 1;
    const char* start_log = mode_ == Mode::observe
        ? "read-only candidate observer armed; startup_s=240 observation_s=60 callback_limit=30000 getter_limit=150000 log_limit=256"
        : mode_ == Mode::dry_run_probe
            ? "mode=dry_run_probe shadow=1; CalcVelocityFalling projection is diagnostic only and always returns original velocity; write=0 startup_s=240 observation_s=60 callback_limit=30000 getter_limit=150000 log_limit=256"
            : mode_ == Mode::write_probe
                ? "mode=write_probe candidate observer armed; CalcVelocityFalling caps verified descent at -5; startup_s=240 observation_s=60 callback_limit=30000 getter_limit=150000 log_limit=256"
                : "mode=product enabled=1 CalcVelocityFalling caps verified descent at configured speed; timer=0 probe_deadline=0 log_limit=256";
    char product_start_log[192]{};
    if (mode_ == Mode::product) {
        std::snprintf(product_start_log, sizeof(product_start_log),
            "mode=product enabled=1 descent_speed_mps=%.3f timer_thread=0 probe_deadline=0 callback_budget=none getter_budget=none log_limit=256",
            static_cast<double>(product_config_.descent_speed));
        start_log = product_start_log;
    }
    LogInfo(Id(), start_log);
    try {
        if (mode_ != Mode::product)
            timer_thread_ = std::thread(&GlideMovementModule::TimerLoop, this);
    } catch (const std::exception&) {
        Finish("timer_start_failed", FinishCause::Exception);
        return {false, mode_ == Mode::observe
            ? "glide state observer timer could not start; both hooks removed"
            : "glide movement timer could not start; all installed hooks removed"};
    }
    return {true, mode_ == Mode::observe
        ? "read-only glide candidate observer armed"
        : mode_ == Mode::dry_run_probe
            ? "glide dry-run shadow armed; projected velocity is diagnostic only and original velocity is always returned"
            : mode_ == Mode::write_probe
                ? "glide write probe armed with verified ARM64 Vector3 return ABI"
                : "glide product movement owner armed with validated configuration"};
}

GlideMovementModule* GlideMovementModule::EnterCallback() {
    std::lock_guard lock(active_mutex_);
    GlideMovementModule* module = active_.load(std::memory_order_acquire);
    if (module != nullptr) {
        module->in_flight_.fetch_add(1, std::memory_order_acq_rel);
    }
    return module;
}

void GlideMovementModule::LeaveCallback(GlideMovementModule* module) {
    if (module != nullptr &&
            module->in_flight_.fetch_sub(1, std::memory_order_acq_rel) == 1) {
        std::lock_guard lock(module->in_flight_mutex_);
        module->in_flight_condition_.notify_all();
    }
}

void GlideMovementModule::JumpDetour(void* instance, const void* method_info) {
    GlideMovementModule* observer = EnterCallback();
    PlayerSnapshot snapshot{};
    SnapshotResult before = SnapshotResult::identity_read_failed;
    const char* failure_reason = nullptr;
    FinishCause failure_cause = FinishCause::Unknown;
    if (observer != nullptr &&
            observer->installation_complete_.load(std::memory_order_acquire) &&
            !observer->finished_.load(std::memory_order_acquire)) {
        const uint32_t callback = observer->CountCallback();
        const auto now = std::chrono::steady_clock::now();
        if (callback > kCallbackLimit && IsProbeMode(observer->mode_)) {
            failure_reason = "callback_limit";
            failure_cause = FinishCause::Budget;
        } else if (SteadyNanoseconds(now) >= observer->deadline_ns_.load(
                std::memory_order_acquire) &&
                observer->mode_ == Mode::observe) {
            failure_reason = observer->sampling_started_.load(
                std::memory_order_acquire) ? "observation_timeout" : "startup_timeout";
        } else {
            try {
                std::lock_guard lock(observer->observation_mutex_);
                if (!observer->finished_.load(std::memory_order_acquire)) {
                    if (observer->runtime_ == nullptr || instance == nullptr ||
                            !observer->runtime_->IsInstanceOf(instance,
                                observer->player_controller_class_)) {
                        before = SnapshotResult::invalid_controller;
                    } else {
                        before = observer->CapturePlayer(snapshot, true);
                    }
                }
            } catch (...) {
                failure_reason = "jump_pre_snapshot_exception";
                failure_cause = FinishCause::Exception;
            }
        }
    }

    // The original is invoked exactly once and outside observation_mutex_.
    const JumpFn original = jump_original_;
    if (original == nullptr) {
        if (observer != nullptr) {
            observer->Finish("jump_original_missing", FinishCause::Unknown);
            observer->ReleasePlayerSnapshot(snapshot);
            LeaveCallback(observer);
        }
        return;
    }
    try {
        original(instance, method_info);
    } catch (...) {
        const std::exception_ptr original_exception = std::current_exception();
        if (observer != nullptr) {
            try { observer->Finish("jump_original_exception", FinishCause::Exception); } catch (...) {}
            try { observer->ReleasePlayerSnapshot(snapshot); } catch (...) {}
            try { LeaveCallback(observer); } catch (...) {}
        }
        std::rethrow_exception(original_exception);
    }

    if (observer == nullptr) return;
    if (failure_reason != nullptr) {
        observer->Finish(failure_reason, failure_cause);
    } else if (before != SnapshotResult::ok &&
            observer->installation_complete_.load(std::memory_order_acquire) &&
            !observer->finished_.load(std::memory_order_acquire)) {
        switch (before) {
            case SnapshotResult::invalid_controller:
                observer->Finish("jump_controller_identity_untrusted", FinishCause::Identity); break;
            case SnapshotResult::root_failed:
                observer->Finish("jump_pre_snapshot_root_failed", FinishCause::Identity); break;
            case SnapshotResult::air_state_read_failed:
                observer->Finish("jump_pre_air_state_read_failed", FinishCause::Read); break;
            case SnapshotResult::identity_read_failed:
                observer->Finish("jump_pre_player_identity_read_failed", FinishCause::Read); break;
            case SnapshotResult::ok: break;
        }
    } else if (before == SnapshotResult::ok &&
            observer->installation_complete_.load(std::memory_order_acquire) &&
            !observer->finished_.load(std::memory_order_acquire)) {
        try {
            observer->ObserveJump(instance, snapshot);
        } catch (...) {
            observer->Finish("jump_post_snapshot_exception", FinishCause::Exception);
        }
    }
    observer->ReleasePlayerSnapshot(snapshot);
    LeaveCallback(observer);
}

void GlideMovementModule::FixedTickDetour(void* instance, float delta_time,
        const void* method_info) {
    GlideMovementModule* observer = EnterCallback();
    const FixedTickFn original = fixed_tick_original_;
    if (original == nullptr) {
        if (observer != nullptr) {
            observer->Finish("fixed_tick_original_missing", FinishCause::Unknown);
            LeaveCallback(observer);
        }
        return;
    }
    try {
        original(instance, delta_time, method_info);
    } catch (...) {
        const std::exception_ptr original_exception = std::current_exception();
        if (observer != nullptr) {
            try { observer->Finish("fixed_tick_original_exception", FinishCause::Exception); } catch (...) {}
            try { LeaveCallback(observer); } catch (...) {}
        }
        std::rethrow_exception(original_exception);
    }
    if (observer == nullptr) return;
    try {
        if (observer->installation_complete_.load(std::memory_order_acquire))
            observer->ObserveFixedTick(instance);
    } catch (...) {
        observer->Finish("fixed_tick_observation_exception", FinishCause::Exception);
    }
    LeaveCallback(observer);
}

GlideMovementModule::Vector3 GlideMovementModule::CalcVelocityFallingDetour(
        void* instance, float delta_time, const void* method_info) {
    GlideMovementModule* module = EnterCallback();
    const CalcVelocityFallingFn original = calc_velocity_falling_original_;
    if (original == nullptr) {
        if (module != nullptr) {
            try { module->Finish("calc_original_missing", FinishCause::Unknown); } catch (...) {}
            LeaveCallback(module);
        }
        return {};
    }

    Vector3 result{};
    try {
        // Preserve the managed method's exception behavior and invoke it once.
        result = original(instance, delta_time, method_info);
    } catch (...) {
        const std::exception_ptr original_exception = std::current_exception();
        if (module != nullptr) {
            try { module->Finish("calc_original_exception", FinishCause::Exception); } catch (...) {}
            try { LeaveCallback(module); } catch (...) {}
        }
        std::rethrow_exception(original_exception);
    }

    const Vector3 original_result = result;

    if (module == nullptr ||
            !module->installation_complete_.load(std::memory_order_acquire) ||
            module->finished_.load(std::memory_order_acquire) ||
            module->mode_ == Mode::observe) {
        LeaveCallback(module);
        return result;
    }
    try {
        result = module->ObserveCalcVelocity(instance, delta_time, result);
    } catch (...) {
        module->Finish("calc_observation_exception", FinishCause::Exception);
    }
    Vector3 final_result = original_result;
    {
        // This lock linearizes the final output choice against Finish's first
        // terminal publication. It is deliberately separate from the
        // observation lock and is released before leaving the callback.
        std::lock_guard lock(active_mutex_);
        if (!module->finished_.load(std::memory_order_acquire) &&
                module->installation_complete_.load(std::memory_order_acquire) &&
                (module->mode_ == Mode::write_probe ||
                    module->mode_ == Mode::product)) {
            final_result = result;
        }
    }
    LeaveCallback(module);
    return final_result;
}

GlideMovementModule::SnapshotResult GlideMovementModule::CapturePlayer(
        PlayerSnapshot& snapshot, bool read_airborne) {
    Il2CppRuntime* runtime = runtime_;
    if (runtime == nullptr || (CountGetterCall() > kGetterCallLimit &&
            IsProbeMode(mode_)))
        return SnapshotResult::identity_read_failed;
    void* exception = nullptr;
    snapshot.entity = runtime->Invoke(get_main_character_.info, nullptr,
        nullptr, &exception);
    if (exception != nullptr || snapshot.entity == nullptr ||
            !runtime->IsInstanceOf(snapshot.entity, entity_class_))
        return SnapshotResult::identity_read_failed;
    snapshot.entity_handle = runtime->NewGcHandle(snapshot.entity, true);
    if (snapshot.entity_handle == 0) return SnapshotResult::root_failed;

    if (CountGetterCall() > kGetterCallLimit && IsProbeMode(mode_))
        return SnapshotResult::identity_read_failed;
    exception = nullptr;
    snapshot.movement = runtime->Invoke(get_movement_component_.info,
        snapshot.entity, nullptr, &exception);
    if (exception != nullptr || snapshot.movement == nullptr ||
            !runtime->IsInstanceOf(snapshot.movement, movement_component_class_))
        return SnapshotResult::identity_read_failed;
    snapshot.movement_handle = runtime->NewGcHandle(snapshot.movement, true);
    if (snapshot.movement_handle == 0) return SnapshotResult::root_failed;

    if (read_airborne) {
        if (CountGetterCall() > kGetterCallLimit && IsProbeMode(mode_))
            return SnapshotResult::air_state_read_failed;
        exception = nullptr;
        void* boxed = runtime->Invoke(get_is_in_air_.info, snapshot.movement,
            nullptr, &exception);
        if (exception != nullptr || boxed == nullptr)
            return SnapshotResult::air_state_read_failed;
        void* raw = runtime->Unbox(boxed);
        if (raw == nullptr) return SnapshotResult::air_state_read_failed;
        uint8_t value = 0;
        std::memcpy(&value, raw, sizeof(value));
        if (value > 1) return SnapshotResult::air_state_read_failed;
        snapshot.airborne = value != 0;
    }
    return SnapshotResult::ok;
}

void GlideMovementModule::ReleasePlayerSnapshot(PlayerSnapshot& snapshot) {
    Il2CppRuntime* runtime = runtime_;
    if (runtime != nullptr) {
        if (snapshot.entity_handle != 0) runtime->FreeGcHandle(snapshot.entity_handle);
        if (snapshot.movement_handle != 0) runtime->FreeGcHandle(snapshot.movement_handle);
    }
    snapshot = {};
}

bool GlideMovementModule::ResolvePlayer(void*& entity, void*& movement,
        bool& airborne) {
    Il2CppRuntime* runtime = runtime_;
    if (runtime == nullptr || (CountGetterCall() > kGetterCallLimit &&
            IsProbeMode(mode_))) return false;
    void* exception = nullptr;
    entity = runtime->Invoke(get_main_character_.info, nullptr, nullptr, &exception);
    if (exception != nullptr || entity == nullptr ||
            !runtime->IsInstanceOf(entity, entity_class_)) return false;
    LocalGcHandle entity_root;
    if (!entity_root.Hold(*runtime, entity)) return false;

    if (CountGetterCall() > kGetterCallLimit && IsProbeMode(mode_))
        return false;
    exception = nullptr;
    movement = runtime->Invoke(get_movement_component_.info, entity, nullptr,
        &exception);
    if (exception != nullptr || movement == nullptr ||
            !runtime->IsInstanceOf(movement, movement_component_class_)) return false;
    LocalGcHandle movement_root;
    if (!movement_root.Hold(*runtime, movement)) return false;
    return ReadAirborne(movement, airborne);
}

bool GlideMovementModule::ReadAirborne(void* movement, bool& airborne) {
    Il2CppRuntime* runtime = runtime_;
    if (runtime == nullptr || movement == nullptr ||
            (CountGetterCall() > kGetterCallLimit && IsProbeMode(mode_)))
        return false;
    void* exception = nullptr;
    void* boxed = runtime->Invoke(get_is_in_air_.info, movement, nullptr,
        &exception);
    if (exception != nullptr || boxed == nullptr) return false;
    void* raw = runtime->Unbox(boxed);
    if (raw == nullptr) return false;
    uint8_t value = 0;
    std::memcpy(&value, raw, sizeof(value));
    if (value > 1) return false;
    airborne = value != 0;
    return true;
}

GlideMovementModule::PipelineVerification
GlideMovementModule::VerifyPipeline(void* pipeline, bool& airborne,
        void*& entity, void*& movement) {
    Il2CppRuntime* runtime = runtime_;
    if (runtime == nullptr || pipeline == nullptr ||
            !runtime->IsInstanceOf(pipeline, air_pipeline_class_))
        return PipelineVerification::invalid_pipeline_type;
    auto invoke = [this, runtime](const ResolvedMethod& method, void* instance,
            void** parameters, void** exception) -> void* {
        if (CountGetterCall() > kGetterCallLimit && IsProbeMode(mode_))
            return nullptr;
        *exception = nullptr;
        return runtime->Invoke(method.info, instance, parameters, exception);
    };

    void* exception = nullptr;
    entity = invoke(get_main_character_, nullptr, nullptr, &exception);
    if (exception != nullptr || entity == nullptr)
        return PipelineVerification::player_entity_read_failed;
    if (!runtime->IsInstanceOf(entity, entity_class_))
        return PipelineVerification::player_entity_invalid;
    LocalGcHandle entity_root;
    if (!entity_root.Hold(*runtime, entity))
        return PipelineVerification::player_entity_root_failed;

    exception = nullptr;
    void* player_movement = invoke(get_movement_component_, entity, nullptr,
        &exception);
    if (exception != nullptr || player_movement == nullptr)
        return PipelineVerification::player_movement_read_failed;
    if (!runtime->IsInstanceOf(player_movement, movement_component_class_))
        return PipelineVerification::player_movement_invalid;
    LocalGcHandle player_movement_root;
    if (!player_movement_root.Hold(*runtime, player_movement))
        return PipelineVerification::player_movement_root_failed;

    exception = nullptr;
    void* pipeline_movement = invoke(get_move_comp_, pipeline, nullptr,
        &exception);
    if (exception != nullptr || pipeline_movement == nullptr)
        return PipelineVerification::pipeline_movement_read_failed;
    if (!runtime->IsInstanceOf(pipeline_movement, movement_component_class_))
        return PipelineVerification::pipeline_movement_invalid;
    LocalGcHandle pipeline_movement_root;
    if (!pipeline_movement_root.Hold(*runtime, pipeline_movement))
        return PipelineVerification::pipeline_movement_root_failed;

    movement = player_movement;
    {
        std::lock_guard lock(observation_mutex_);
        if (finished_.load(std::memory_order_acquire))
            return PipelineVerification::observer_finished;
        if (current_entity_ != entity || current_movement_ != player_movement)
            return PipelineVerification::player_identity_changed;
        if (pipeline_movement != player_movement)
            return PipelineVerification::other_pipeline;
    }

    exception = nullptr;
    void* boxed_air = invoke(get_is_in_air_, player_movement, nullptr,
        &exception);
    if (exception != nullptr || boxed_air == nullptr)
        return PipelineVerification::air_state_read_failed;
    void* raw_air = runtime->Unbox(boxed_air);
    if (raw_air == nullptr)
        return PipelineVerification::air_state_read_failed;
    uint8_t air_value = 0;
    std::memcpy(&air_value, raw_air, sizeof(air_value));
    if (air_value > 1) return PipelineVerification::air_state_invalid;
    airborne = air_value != 0;

    std::lock_guard lock(observation_mutex_);
    if (finished_.load(std::memory_order_acquire))
        return PipelineVerification::observer_finished;
    if (current_entity_ != entity || current_movement_ != player_movement)
        return PipelineVerification::player_identity_changed;
    movement = player_movement;
    return PipelineVerification::verified;
}

GlideMovementModule::Vector3 GlideMovementModule::ObserveCalcVelocity(
        void* pipeline, float delta_time, const Vector3& original) {
    const uint32_t callback = CountCallback();
    const auto now = std::chrono::steady_clock::now();
    if (callback > kCallbackLimit && IsProbeMode(mode_)) {
        Finish("callback_limit", FinishCause::Budget);
        return original;
    }
    const int64_t now_ns = SteadyNanoseconds(now);
    if (now_ns >= deadline_ns_.load(std::memory_order_acquire) &&
            mode_ == Mode::observe) {
        Finish(sampling_started_.load(std::memory_order_acquire)
            ? "observation_timeout" : "startup_timeout");
        return original;
    }
    if (!std::isfinite(original.x) || !std::isfinite(original.y) ||
            !std::isfinite(original.z) ||
            (mode_ != Mode::observe && !std::isfinite(delta_time))) {
        Finish("nonfinite_calc_velocity", FinishCause::NonFinite);
        return original;
    }
    bool candidate_active = false;
    bool soft_deadline = false;
    bool candidate_inactive_soft_stop = false;
    BetterEndfield::Movement::GlideStopOutput inactive_stop{};
    {
        std::lock_guard lock(observation_mutex_);
        candidate_active = candidate_.active;
        const int64_t locked_now_ns = SteadyNanoseconds(
            std::chrono::steady_clock::now());
        soft_deadline = IsProbeMode(mode_) &&
            (locked_now_ns >= deadline_ns_.load(std::memory_order_acquire) ||
                stop_soft_pending_.load(std::memory_order_acquire));
        if (!candidate_active && IsProbeMode(mode_) && soft_deadline) {
            inactive_stop = StepStopPolicyLocked(locked_now_ns, false, false, false,
                false, BetterEndfield::Movement::GlideStopFault::None);
            LogStopPolicy(inactive_stop, "CalcVelocityFalling");
            candidate_inactive_soft_stop = true;
        }
    }
    if (!candidate_active) {
        if (candidate_inactive_soft_stop &&
                inactive_stop.phase == BetterEndfield::Movement::GlideStopPhase::Finished)
            Finish("stop_policy_terminal", FinishCause::PolicyTerminal);
        return original;
    }
    const uint32_t getters_before = getter_calls_.load(std::memory_order_acquire);
    bool airborne = false;
    void* verified_entity = nullptr;
    void* verified_movement = nullptr;
    const PipelineVerification verification = VerifyPipeline(pipeline,
        airborne, verified_entity,
        verified_movement);
    if (getter_calls_.load(std::memory_order_acquire) > kGetterCallLimit &&
            IsProbeMode(mode_)) {
        Finish("getter_call_limit", FinishCause::Budget);
        return original;
    }
    if (verification == PipelineVerification::other_pipeline) {
        bool identity_changed = false;
        {
            std::lock_guard lock(observation_mutex_);
            if (finished_.load(std::memory_order_acquire)) return original;
            identity_changed = current_entity_ != verified_entity ||
                current_movement_ != verified_movement;
            if (!identity_changed) {
                ++other_pipeline_total_;
                if (other_pipeline_total_ == 1 &&
                        log_lines_ < kMaximumLogLines - 1) {
                    char line[112]{};
                    std::snprintf(line, sizeof(line),
                        "other_pipeline callback=%u total=%llu; write_skipped=1",
                        callback,
                        static_cast<unsigned long long>(other_pipeline_total_));
                    LogInfo(Id(), line);
                    ++log_lines_;
                }
                return original;
            }
        }
        if (mode_ != Mode::product)
            Finish("bound_player_identity_changed", FinishCause::Identity);
        return original;
    }
    if (verification != PipelineVerification::verified) {
        if (mode_ == Mode::product && verification ==
                PipelineVerification::player_identity_changed)
            return original;
        const char* reason = "pipeline_verification_failed";
        FinishCause cause = FinishCause::Unknown;
        switch (verification) {
            case PipelineVerification::observer_finished:
                return original;
            case PipelineVerification::invalid_pipeline_type:
                reason = "pipeline_type_invalid";
                cause = FinishCause::Type;
                break;
            case PipelineVerification::player_entity_read_failed:
                reason = "player_entity_read_failed";
                cause = FinishCause::Read;
                break;
            case PipelineVerification::player_entity_invalid:
                reason = "player_entity_type_invalid";
                cause = FinishCause::Type;
                break;
            case PipelineVerification::player_entity_root_failed:
                reason = "player_entity_root_failed";
                cause = FinishCause::Identity;
                break;
            case PipelineVerification::player_movement_read_failed:
                reason = "player_movement_read_failed";
                cause = FinishCause::Read;
                break;
            case PipelineVerification::player_movement_invalid:
                reason = "player_movement_type_invalid";
                cause = FinishCause::Type;
                break;
            case PipelineVerification::player_movement_root_failed:
                reason = "player_movement_root_failed";
                cause = FinishCause::Identity;
                break;
            case PipelineVerification::pipeline_movement_read_failed:
                reason = "pipeline_movement_read_failed";
                cause = FinishCause::Read;
                break;
            case PipelineVerification::pipeline_movement_invalid:
                reason = "pipeline_movement_type_invalid";
                cause = FinishCause::Type;
                break;
            case PipelineVerification::pipeline_movement_root_failed:
                reason = "pipeline_movement_root_failed";
                cause = FinishCause::Identity;
                break;
            case PipelineVerification::player_identity_changed:
                reason = "bound_player_identity_changed";
                cause = FinishCause::Identity;
                break;
            case PipelineVerification::air_state_read_failed:
                reason = "air_state_read_failed";
                cause = FinishCause::Read;
                break;
            case PipelineVerification::air_state_invalid:
                reason = "air_state_invalid";
                cause = FinishCause::Type;
                break;
            case PipelineVerification::verified:
            case PipelineVerification::other_pipeline:
                break;
        }
        Finish(reason, cause);
        return original;
    }

    BetterEndfield::Movement::GlideStopOutput calc_stop{};
    bool calc_stop_terminal = false;
    bool stop_projection_allowed = true;
    {
        std::lock_guard lock(observation_mutex_);
        if (finished_.load(std::memory_order_acquire)) return original;
        auto locked_now = std::chrono::steady_clock::now();
        int64_t locked_now_ns = SteadyNanoseconds(locked_now);
        soft_deadline = IsProbeMode(mode_) &&
            (locked_now_ns >= deadline_ns_.load(std::memory_order_acquire) ||
                stop_soft_pending_.load(std::memory_order_acquire));
        if (IsProbeMode(mode_) && !soft_deadline &&
                !sampling_started_.load(std::memory_order_acquire)) {
            sample_started_at_ = locked_now;
            sampling_started_.store(true, std::memory_order_release);
            deadline_ns_.store(SteadyNanoseconds(locked_now + kObservationLimit),
                std::memory_order_release);
            timer_condition_.notify_all();
        }
        soft_deadline = IsProbeMode(mode_) &&
            (locked_now_ns >= deadline_ns_.load(std::memory_order_acquire) ||
                stop_soft_pending_.load(std::memory_order_acquire));
        const bool stop_policy_active = IsProbeMode(mode_) &&
            (soft_deadline || stop_state_.phase !=
                BetterEndfield::Movement::GlideStopPhase::Running);
        if (stop_policy_active) {
            calc_stop = StepStopPolicyLocked(locked_now_ns, true, true, airborne,
                false, BetterEndfield::Movement::GlideStopFault::None);
            LogStopPolicy(calc_stop, mode_ == Mode::write_probe
                ? "write_probe_calc_velocity" : "dry_run_calc_velocity");
            calc_stop_terminal = calc_stop.phase ==
                BetterEndfield::Movement::GlideStopPhase::Finished;
            stop_projection_allowed = calc_stop.preserve_projection;
        }
    }
    if (calc_stop_terminal) {
        Finish("stop_policy_terminal", FinishCause::PolicyTerminal);
        return original;
    }

    Vector3 projected = original;
    {
        std::lock_guard lock(observation_mutex_);
        const auto config = EffectiveConfig();
        if (finished_.load(std::memory_order_acquire) || !candidate_.active ||
                !stop_projection_allowed ||
                current_entity_ != verified_entity ||
                current_movement_ != verified_movement ||
                !airborne || !BetterEndfield::Movement::IsValidGlideConfig(config))
            return original;
        BetterEndfield::Movement::Velocity3 output{};
        const BetterEndfield::Movement::Velocity3 input{
            original.x, original.y, original.z};
        if (!BetterEndfield::Movement::ProjectGlideVelocity(candidate_, config,
                airborne, input, output)) return original;
        if (mode_ == Mode::product &&
                (candidate_.sustained_dash_airborne_origin ||
                    candidate_.manual_reactivation_after_sustained_dash) &&
                input_field_.info != nullptr && move_input_class_.info != nullptr &&
                get_move_vector_.info != nullptr) {
            void* move_input = runtime_->ReadFieldObject(input_field_,
                verified_movement);
            if (move_input != nullptr &&
                    runtime_->IsInstanceOf(move_input, move_input_class_)) {
                LocalGcHandle input_root;
                if (input_root.Hold(*runtime_, move_input)) {
                    void* exception = nullptr;
                    void* boxed = runtime_->Invoke(get_move_vector_.info,
                        move_input, nullptr, &exception);
                    if (exception == nullptr && boxed != nullptr) {
                        void* raw = runtime_->Unbox(boxed);
                        if (raw != nullptr) {
                            Vector3 direction{};
                            std::memcpy(&direction, raw, sizeof(direction));
                            BetterEndfield::Movement::Velocity3 turned{};
                            const bool auto_steered =
                                BetterEndfield::Movement::ProjectAutoGlideSteering(
                                    candidate_, airborne, output,
                                    {direction.x, direction.y, direction.z},
                                    delta_time, turned);
                            const bool manual_recovered = !auto_steered &&
                                BetterEndfield::Movement::ProjectManualGlideRecovery(
                                    candidate_, airborne, output,
                                    {direction.x, direction.y, direction.z},
                                    delta_time, turned);
                            if (auto_steered || manual_recovered) {
                                output = turned;
                                if (auto_steering_log_samples_ < 8 &&
                                        log_lines_ < kMaximumLogLines - 1) {
                                    char line[256]{};
                                    std::snprintf(line, sizeof(line),
                                        "%s sample=%u direction=(%.2f,%.2f) original_xz=(%.2f,%.2f) returned_xz=(%.2f,%.2f)",
                                        auto_steered ? "auto_steering" : "manual_glide_recovery",
                                        auto_steering_log_samples_ + 1,
                                        static_cast<double>(direction.x),
                                        static_cast<double>(direction.z),
                                        static_cast<double>(original.x),
                                        static_cast<double>(original.z),
                                        static_cast<double>(output.x),
                                        static_cast<double>(output.z));
                                    LogInfo(Id(), line);
                                    ++auto_steering_log_samples_;
                                    ++log_lines_;
                                }
                            }
                        }
                    }
                }
            }
        }
        projected = {output.x, output.y, output.z};
        if (mode_ == Mode::dry_run_probe &&
                (projected.x != original.x || projected.y != original.y ||
                    projected.z != original.z)) {
            ++would_write_total_;
            if (would_write_total_ == 1 &&
                    log_lines_ < kMaximumLogLines - 1) {
                char line[192]{};
                std::snprintf(line, sizeof(line),
                    "dry_run_would_write callback=%u would_write_total=%llu other_pipeline_total=%llu projected_y=%.3f returned=original write=0",
                    callback,
                    static_cast<unsigned long long>(would_write_total_),
                    static_cast<unsigned long long>(other_pipeline_total_),
                    static_cast<double>(projected.y));
                LogInfo(Id(), line);
                ++log_lines_;
            }
            return original;
        }
        if (projected.y != original.y && mode_ == Mode::write_probe) {
            ++write_total_;
            if (has_pending_calc_comparison_ &&
                    calc_pending_overwrites_ !=
                        std::numeric_limits<uint32_t>::max()) {
                ++calc_pending_overwrites_;
                ++calc_coalesced_total_;
            }
            ++calc_sequence_;
            calc_returned_velocity_ = projected;
            has_pending_calc_comparison_ = true;
            const bool first_sample = !has_write_log_sample_;
            const bool vertical_change = has_write_log_sample_ &&
                std::fabs(original.y - last_logged_write_input_y_) >=
                    kLogVelocityYChange;
            if (write_log_samples_ < kMaximumWriteLogSamples &&
                    (first_sample || vertical_change) &&
                    log_lines_ < kMaximumLogLines - 1) {
                char line[256]{};
                std::snprintf(line, sizeof(line),
                    "write seq=%llu sample=%u total=%llu callback=%u getters=%u dt=%.6f original=(%.3f,%.3f,%.3f) returned=(%.3f,%.3f,%.3f)",
                    static_cast<unsigned long long>(calc_sequence_),
                    write_log_samples_ + 1,
                    static_cast<unsigned long long>(write_total_), callback,
                    getter_calls_.load(std::memory_order_acquire) - getters_before,
                    static_cast<double>(delta_time),
                    static_cast<double>(original.x),
                    static_cast<double>(original.y),
                    static_cast<double>(original.z),
                    static_cast<double>(projected.x),
                    static_cast<double>(projected.y),
                    static_cast<double>(projected.z));
                LogInfo(Id(), line);
                ++write_log_samples_;
                has_write_log_sample_ = true;
                last_logged_write_input_y_ = original.y;
                ++log_lines_;
            }
        }
    }
    return mode_ == Mode::dry_run_probe ? original : projected;
}

GlideMovementModule::IdentityUpdate GlideMovementModule::UpdateIdentity(
        void* entity, void* movement,
        bool airborne) {
    if (entity == current_entity_ && movement == current_movement_)
        return IdentityUpdate::unchanged;
    Il2CppRuntime* runtime = runtime_;
    if (runtime == nullptr || player_epoch_ == UINT64_MAX)
        return IdentityUpdate::root_failed;

    const bool first_binding = current_entity_ == nullptr &&
        current_movement_ == nullptr;
    const bool allow_rebind = !first_binding && (mode_ == Mode::product ||
        (!any_jump_seen_ && !saw_airborne_ && !candidate_.active &&
            !rebind_used_ && !airborne));
    if (!first_binding && !allow_rebind) {
        grounded_takeoff_ = {};
        GlideInput clear{};
        clear.player_session = 1;
        clear.player_epoch = player_epoch_;
        clear.airborne = false;
        clear.config = EffectiveConfig();
        const auto transition = BetterEndfield::Movement::StepGlide(
            candidate_, clear);
        LogTransition(SteadyNanoseconds(std::chrono::steady_clock::now()), 0,
            transition, "clear_reason=player_identity_changed");
        return IdentityUpdate::changed;
    }

    // Root both new objects before publishing the new binding. On any failure
    // the old identity and its roots remain intact until fail-closed teardown.
    const uint32_t entity_handle = runtime->NewGcHandle(entity, true);
    if (entity_handle == 0) return IdentityUpdate::root_failed;
    const uint32_t movement_handle = runtime->NewGcHandle(movement, true);
    if (movement_handle == 0) {
        runtime->FreeGcHandle(entity_handle);
        return IdentityUpdate::root_failed;
    }
    const uint32_t old_entity_handle = entity_handle_;
    const uint32_t old_movement_handle = movement_handle_;
    grounded_takeoff_ = {};
    entity_handle_ = entity_handle;
    movement_handle_ = movement_handle;
    current_entity_ = entity;
    current_movement_ = movement;
    ++player_epoch_;
    GlideInput input{};
    input.player_session = 1;
    input.player_epoch = player_epoch_;
    input.airborne = airborne;
    input.config = EffectiveConfig();
    const auto transition = BetterEndfield::Movement::StepGlide(candidate_, input);
    const auto now = std::chrono::steady_clock::now();
    if (old_entity_handle != 0) runtime->FreeGcHandle(old_entity_handle);
    if (old_movement_handle != 0) runtime->FreeGcHandle(old_movement_handle);
    if (allow_rebind && IsProbeMode(mode_)) {
        rebind_used_ = true;
        sample_started_at_ = now;
        const int64_t requested_deadline = SteadyNanoseconds(
            now + kObservationLimit);
        deadline_ns_.store(std::min(requested_deadline,
            maximum_deadline_ns_.load(std::memory_order_acquire)),
            std::memory_order_release);
        sampling_started_.store(true, std::memory_order_release);
        timer_condition_.notify_all();
        LogTransition(SteadyNanoseconds(now), 0, transition, "rebind");
    } else {
        LogTransition(SteadyNanoseconds(now), 0, transition,
            allow_rebind ? "product_rebind" : "player_bound");
    }
    return allow_rebind ? IdentityUpdate::rebound : IdentityUpdate::first_binding;
}

void GlideMovementModule::ObserveJump(void* instance,
        const PlayerSnapshot& before) {
    if (finished_.load(std::memory_order_acquire)) return;
    const auto now = std::chrono::steady_clock::now();
    const int64_t now_ns = SteadyNanoseconds(now);
    if (now_ns >= deadline_ns_.load(std::memory_order_acquire) &&
            mode_ == Mode::observe) {
        Finish(sampling_started_.load(std::memory_order_acquire)
            ? "observation_timeout" : "startup_timeout");
        return;
    }
    std::unique_lock lock(observation_mutex_);
    if (finished_.load(std::memory_order_acquire)) return;
    bool soft_deadline = false;
    auto locked_now = std::chrono::steady_clock::now();
    int64_t locked_now_ns = SteadyNanoseconds(locked_now);
    soft_deadline = IsProbeMode(mode_) &&
        (locked_now_ns >= deadline_ns_.load(std::memory_order_acquire) ||
            stop_soft_pending_.load(std::memory_order_acquire));
    Il2CppRuntime* runtime = runtime_;
    if (runtime == nullptr || instance == nullptr ||
            !runtime->IsInstanceOf(instance, player_controller_class_)) {
        lock.unlock();
        Finish("jump_controller_identity_changed_after_original", FinishCause::Identity);
        return;
    }
    PlayerSnapshot after{};
    SnapshotResult after_result = SnapshotResult::identity_read_failed;
    try {
        after_result = CapturePlayer(after, false);
    } catch (...) {
        lock.unlock();
        ReleasePlayerSnapshot(after);
        Finish("jump_post_identity_capture_exception", FinishCause::Exception);
        return;
    }
    if (after_result != SnapshotResult::ok) {
        lock.unlock();
        ReleasePlayerSnapshot(after);
        Finish(after_result == SnapshotResult::root_failed
            ? "jump_post_identity_root_failed"
            : "jump_post_player_identity_read_failed",
            after_result == SnapshotResult::root_failed
                ? FinishCause::Identity : FinishCause::Read);
        return;
    }
    const bool same_identity = after.entity == before.entity &&
        after.movement == before.movement;
    ReleasePlayerSnapshot(after);
    if (!same_identity) {
        lock.unlock();
        Finish("jump_player_or_movement_changed_during_original", FinishCause::Identity);
        return;
    }

    const IdentityUpdate identity = UpdateIdentity(before.entity,
        before.movement, before.airborne);
    if (identity == IdentityUpdate::root_failed) {
        lock.unlock();
        Finish("player_identity_root_failed", FinishCause::Identity);
        return;
    }
    if (identity == IdentityUpdate::changed) {
        lock.unlock();
        Finish("player_identity_changed", FinishCause::Identity);
        return;
    }
    // Identity confirmation and any deadline extension are complete. Take one
    // fresh locked timestamp for sampling, cutoff decisions, policy, and logs.
    locked_now = std::chrono::steady_clock::now();
    locked_now_ns = SteadyNanoseconds(locked_now);
    soft_deadline = IsProbeMode(mode_) &&
        (locked_now_ns >= deadline_ns_.load(std::memory_order_acquire) ||
            stop_soft_pending_.load(std::memory_order_acquire));
    if (IsProbeMode(mode_) && !soft_deadline &&
            !sampling_started_.load(std::memory_order_acquire)) {
        sample_started_at_ = locked_now;
        sampling_started_.store(true, std::memory_order_release);
        deadline_ns_.store(SteadyNanoseconds(locked_now + kObservationLimit),
            std::memory_order_release);
        timer_condition_.notify_all();
    }
    // The first-sample deadline may have just been installed; use the same
    // locked timestamp to refresh the final callback decision.
    soft_deadline = IsProbeMode(mode_) &&
        (locked_now_ns >= deadline_ns_.load(std::memory_order_acquire) ||
            stop_soft_pending_.load(std::memory_order_acquire));
    const bool stop_policy_active = IsProbeMode(mode_) &&
        (soft_deadline || stop_state_.phase !=
            BetterEndfield::Movement::GlideStopPhase::Running);
    if (stop_policy_active) {
        const auto stop = StepStopPolicyLocked(locked_now_ns, true, true,
            before.airborne, false,
            BetterEndfield::Movement::GlideStopFault::None);
        LogStopPolicy(stop, mode_ == Mode::write_probe
            ? "write_probe_jump" : "dry_run_jump");
        if (stop.phase == BetterEndfield::Movement::GlideStopPhase::Finished) {
            lock.unlock();
            Finish("stop_policy_terminal", FinishCause::PolicyTerminal);
            return;
        }
    }
    if (before.airborne) saw_airborne_ = true;
    if (!before.airborne) {
        BetterEndfield::Movement::RecordGroundTakeoff(grounded_takeoff_,
            reinterpret_cast<std::uintptr_t>(before.entity), player_epoch_,
            locked_now_ns, locked_now_ns + 1000000000LL);
        const char* reason = any_jump_seen_ ? "ground_jump" : "ground_first_jump";
        any_jump_seen_ = true;
        GlideInput grounded_input{};
        grounded_input.player_session = 1;
        grounded_input.player_epoch = player_epoch_;
        grounded_input.airborne = false;
        grounded_input.config = EffectiveConfig();
        LogTransition(locked_now_ns, 0,
            BetterEndfield::Movement::StepGlide(candidate_, grounded_input), reason);
        return;
    }
    any_jump_seen_ = true;

    const bool stop_mode = IsProbeMode(mode_) && stop_state_.phase !=
            BetterEndfield::Movement::GlideStopPhase::Running;
    if (stop_mode && !candidate_.active) return;

    if (next_token_ == UINT64_MAX) {
        lock.unlock();
        Finish("press_token_exhausted", FinishCause::Budget);
        return;
    }
    const uint64_t token = ++next_token_;
    GlideInput input{};
    input.has_press_token = true;
    input.press_token = token;
    input.player_session = 1;
    input.player_epoch = player_epoch_;
    input.airborne = true;
    input.config = EffectiveConfig();
    const bool retracting_sustained_dash = mode_ == Mode::product &&
        candidate_.active && candidate_.sustained_dash_airborne_origin &&
        candidate_.has_last_auto_sequence;
    const uint64_t retract_auto_sequence = candidate_.last_auto_sequence;
    const auto transition = BetterEndfield::Movement::StepGlide(candidate_, input);
    LogTransition(locked_now_ns, token, transition,
        "airborne_jump_pre_state");
    if (retracting_sustained_dash &&
            transition == GlideTransition::Deactivated) {
        BetterEndfield::Movement::GlideCollapseEvent collapse{};
        collapse.entity_identity = reinterpret_cast<std::uintptr_t>(before.entity);
        collapse.auto_sequence = retract_auto_sequence;
        collapse.issued_steady_ns = locked_now_ns;
        collapse.expires_steady_ns = locked_now_ns + 1000000000LL;
        if (!BetterEndfield::Movement::PublishGlideCollapseEvent(collapse))
            LogInfo(Id(), "auto glide manual collapse: visual handoff rejected");
    }
    if (stop_mode && transition == GlideTransition::Deactivated) {
        const auto stop = StepStopPolicyLocked(locked_now_ns, true,
            true, true, true,
            BetterEndfield::Movement::GlideStopFault::None);
        LogStopPolicy(stop, mode_ == Mode::write_probe
            ? "write_probe_jump_collapse" : "dry_run_jump_collapse");
        lock.unlock();
        Finish("stop_policy_candidate_stopped", FinishCause::CandidateStopped);
    }
}

void GlideMovementModule::ObserveFixedTick(void* instance) {
    if (finished_.load(std::memory_order_acquire)) return;
    const uint32_t callback = CountCallback();
    const auto now = std::chrono::steady_clock::now();
    if (callback > kCallbackLimit && IsProbeMode(mode_)) { Finish("callback_limit", FinishCause::Budget); return; }
    const int64_t now_ns = SteadyNanoseconds(now);
    if (now_ns >= deadline_ns_.load(std::memory_order_acquire) &&
            mode_ == Mode::observe) {
        Finish(sampling_started_.load(std::memory_order_acquire)
            ? "observation_timeout" : "startup_timeout");
        return;
    }
    std::unique_lock lock(observation_mutex_);
    if (finished_.load(std::memory_order_acquire)) return;
    bool soft_deadline = false;
    auto locked_now = std::chrono::steady_clock::now();
    int64_t locked_now_ns = SteadyNanoseconds(locked_now);
    soft_deadline = IsProbeMode(mode_) &&
        (locked_now_ns >= deadline_ns_.load(std::memory_order_acquire) ||
            stop_soft_pending_.load(std::memory_order_acquire));
    void* entity = nullptr;
    void* movement = nullptr;
    bool airborne = false;
    if (!ResolvePlayer(entity, movement, airborne)) {
        lock.unlock();
        Finish("player_identity_or_air_state_read_failed", FinishCause::Read);
        return;
    }
    const IdentityUpdate identity = UpdateIdentity(entity, movement, airborne);
    if (identity == IdentityUpdate::root_failed) {
        lock.unlock();
        Finish("player_identity_root_failed", FinishCause::Identity);
        return;
    }
    if (identity == IdentityUpdate::changed) {
        lock.unlock();
        Finish("player_identity_changed", FinishCause::Identity);
        return;
    }
    // ResolvePlayer and identity rebinding can take time or move the deadline.
    // Re-sample under the lock before extending or acting on the cutoff.
    locked_now = std::chrono::steady_clock::now();
    locked_now_ns = SteadyNanoseconds(locked_now);
    const bool grounded_takeoff_valid =
        BetterEndfield::Movement::AdvanceGroundTakeoff(grounded_takeoff_,
            reinterpret_cast<std::uintptr_t>(entity), player_epoch_,
            locked_now_ns, airborne);
    soft_deadline = IsProbeMode(mode_) &&
        (locked_now_ns >= deadline_ns_.load(std::memory_order_acquire) ||
            stop_soft_pending_.load(std::memory_order_acquire));
    if (IsProbeMode(mode_) && !soft_deadline &&
            !sampling_started_.load(std::memory_order_acquire)) {
        sample_started_at_ = locked_now;
        sampling_started_.store(true, std::memory_order_release);
        deadline_ns_.store(SteadyNanoseconds(locked_now + kObservationLimit),
            std::memory_order_release);
        timer_condition_.notify_all();
    }
    soft_deadline = IsProbeMode(mode_) &&
        (locked_now_ns >= deadline_ns_.load(std::memory_order_acquire) ||
            stop_soft_pending_.load(std::memory_order_acquire));
    const bool stop_policy_active = IsProbeMode(mode_) &&
        (soft_deadline || stop_state_.phase !=
            BetterEndfield::Movement::GlideStopPhase::Running);
    if (stop_policy_active) {
        const auto stop = StepStopPolicyLocked(locked_now_ns, true, true, airborne,
            false, BetterEndfield::Movement::GlideStopFault::None);
        LogStopPolicy(stop, mode_ == Mode::write_probe
            ? "write_probe_fixed_tick" : "dry_run_fixed_tick");
        if (stop.phase == BetterEndfield::Movement::GlideStopPhase::Finished) {
            lock.unlock();
            Finish("stop_policy_terminal", FinishCause::PolicyTerminal);
            return;
        }
    }
    if (airborne) saw_airborne_ = true;
    if (instance != movement) return;

    if (mode_ == Mode::write_probe && has_pending_calc_comparison_) {
        const uint64_t sequence = calc_sequence_;
        const Vector3 returned = calc_returned_velocity_;
        const uint32_t coalesced = calc_pending_overwrites_;
        has_pending_calc_comparison_ = false;
        calc_pending_overwrites_ = 0;
        bool available = false;
        FinishCause comparison_failure = FinishCause::Unknown;
        Vector3 actual{};
        if (CountGetterCall() <= kGetterCallLimit ||
                !IsProbeMode(mode_)) {
            void* exception = nullptr;
            void* boxed = runtime_->Invoke(get_velocity_.info, movement,
                nullptr, &exception);
            if (exception == nullptr && boxed != nullptr) {
                void* raw = runtime_->Unbox(boxed);
                if (raw != nullptr) {
                    std::memcpy(&actual, raw, sizeof(actual));
                    available = std::isfinite(actual.x) &&
                        std::isfinite(actual.y) && std::isfinite(actual.z);
                    if (!available) comparison_failure = FinishCause::NonFinite;
                } else {
                    comparison_failure = FinishCause::Read;
                }
            } else {
                comparison_failure = FinishCause::Read;
            }
        } else {
            comparison_failure = FinishCause::Budget;
        }
        ++comparison_total_;
        const bool first_sample = !has_comparison_log_sample_;
        const bool vertical_change = available &&
            (!has_logged_comparison_y_ ||
                std::fabs(actual.y - last_logged_comparison_y_) >=
                    kLogVelocityYChange);
        if (comparison_log_samples_ < kMaximumCompareLogSamples &&
                (first_sample || vertical_change) &&
                log_lines_ < kMaximumLogLines - 1) {
            char line[240]{};
            if (available) {
                std::snprintf(line, sizeof(line),
                    "downstream_compare seq=%llu sample=%u total=%llu coalesced=%u returned=(%.3f,%.3f,%.3f) fixed_tick_velocity=(%.3f,%.3f,%.3f) delta_y=%.3f",
                    static_cast<unsigned long long>(sequence),
                    comparison_log_samples_ + 1,
                    static_cast<unsigned long long>(comparison_total_),
                    coalesced,
                    static_cast<double>(returned.x),
                    static_cast<double>(returned.y),
                    static_cast<double>(returned.z),
                    static_cast<double>(actual.x), static_cast<double>(actual.y),
                    static_cast<double>(actual.z),
                    static_cast<double>(actual.y - returned.y));
            } else {
                std::snprintf(line, sizeof(line),
                    "downstream_compare seq=%llu sample=%u total=%llu coalesced=%u fixed_tick_velocity=unavailable",
                    static_cast<unsigned long long>(sequence),
                    comparison_log_samples_ + 1,
                    static_cast<unsigned long long>(comparison_total_), coalesced);
            }
            LogInfo(Id(), line);
            ++comparison_log_samples_;
            has_comparison_log_sample_ = true;
            if (available) {
                has_logged_comparison_y_ = true;
                last_logged_comparison_y_ = actual.y;
            }
            ++log_lines_;
        }
        if (comparison_failure != FinishCause::Unknown) {
            lock.unlock();
            Finish(comparison_failure == FinishCause::Budget
                ? "getter_call_limit" : comparison_failure == FinishCause::NonFinite
                    ? "downstream_velocity_nonfinite"
                    : "downstream_velocity_read_failed",
                comparison_failure);
            return;
        }
    }

    GlideInput input{};
    input.player_session = 1;
    input.player_epoch = player_epoch_;
    input.airborne = airborne;
    input.config = EffectiveConfig();
    if (mode_ == Mode::product && airborne) {
        BetterEndfield::Movement::AutoGlideEvent event{};
        if (ReadAutoGlideEvent(last_auto_event_sequence_, locked_now_ns, event) &&
                event.entity_identity == reinterpret_cast<std::uintptr_t>(entity) &&
                BetterEndfield::Movement::CanConsumeAutoGlideSource(
                    event.source, grounded_takeoff_valid, airborne) &&
                (event.source != BetterEndfield::Movement::AutoGlideSource::GroundJump ||
                    (event.entity_identity == grounded_takeoff_.entity_identity &&
                        event.issued_steady_ns >= grounded_takeoff_.issued_steady_ns)) &&
                event.sequence != 0) {
            input.auto_sequence = event.sequence;
            input.sustained_dash_airborne_source =
                event.source == BetterEndfield::Movement::AutoGlideSource::SustainedDashAirborne;
            last_auto_event_sequence_ = event.sequence;
            if (event.source == BetterEndfield::Movement::AutoGlideSource::GroundJump)
                grounded_takeoff_ = {};
        }
    }
    const auto transition = BetterEndfield::Movement::StepGlide(candidate_, input);
    if (transition != GlideTransition::NoChange) {
        LogTransition(locked_now_ns, 0, transition,
            airborne ? "fixed_tick_state" : "landed_or_cleared");
    }
}

void GlideMovementModule::LogTransition(int64_t steady_ns, uint64_t token,
        GlideTransition transition, const char* reason) {
    const uint32_t state_log_limit = mode_ == Mode::write_probe
        ? kWriteModeStateLogLimit
        : mode_ == Mode::dry_run_probe
            ? kMaximumLogLines - 4 : kMaximumLogLines - 3;
    if (state_transition_logs_emitted_ >= state_log_limit ||
            log_lines_ >= kMaximumLogLines - 1) {
        ++state_transition_logs_dropped_;
        return;
    }
    const std::string line = "steady_ns=" + std::to_string(steady_ns) +
        " token=" + std::to_string(token) +
        " transition=" + TransitionName(transition) +
        " active=" + (candidate_.active ? "1" : "0") +
        " reason=" + reason;
    LogInfo(Id(), line.c_str());
    ++state_transition_logs_emitted_;
    ++log_lines_;
}

BetterEndfield::Movement::GlideStopOutput
GlideMovementModule::StepStopPolicyLocked(int64_t steady_ns,
        bool identity_trusted, bool airborne_known, bool airborne,
        bool manually_collapsed,
        BetterEndfield::Movement::GlideStopFault fault) {
    using namespace BetterEndfield::Movement;
    GlideStopInput input{};
    input.monotonic_time_ms = steady_ns < 0 ? 0 :
        static_cast<uint64_t>(steady_ns / 1000000);
    input.soft_deadline_reached = stop_soft_pending_.load(
        std::memory_order_acquire) || steady_ns >= deadline_ns_.load(
            std::memory_order_acquire);
    int64_t hard_deadline = stop_hard_deadline_ns_.load(
        std::memory_order_acquire);
    if (hard_deadline == 0 && input.soft_deadline_reached) {
        hard_deadline = deadline_ns_.load(std::memory_order_acquire) +
            static_cast<int64_t>(kGlideNormalDrainMaxMs * 1000000ULL);
        const int64_t absolute_hard = maximum_deadline_ns_.load(
            std::memory_order_acquire);
        if (absolute_hard != 0)
            hard_deadline = std::min(hard_deadline, absolute_hard);
    }
    input.hard_deadline_reached = hard_deadline != 0 &&
        steady_ns >= hard_deadline;
    input.candidate_active = candidate_.active;
    input.identity_trusted = identity_trusted;
    input.airborne_known = airborne_known;
    input.airborne = airborne;
    input.manually_collapsed = manually_collapsed;
    input.fault = fault;

    const GlideStopPhase prior_phase = stop_state_.phase;
    auto output = StepGlideStopPolicy(stop_state_, input);
    stop_state_ = output.next_state;
    if (prior_phase != GlideStopPhase::NormalDrain &&
            output.phase == GlideStopPhase::NormalDrain) {
        normal_wait_ms_ = 0;
        stop_hard_deadline_ns_.store(hard_deadline, std::memory_order_release);
        stop_soft_pending_.store(true, std::memory_order_release);
        deadline_ns_.store(hard_deadline, std::memory_order_release);
        timer_condition_.notify_all();
    } else if ((output.phase == GlideStopPhase::NormalDrain ||
                output.phase == GlideStopPhase::Finished) &&
            prior_phase == GlideStopPhase::NormalDrain &&
            stop_state_.has_last_time &&
            stop_state_.last_time_ms >=
                stop_state_.drain_started_ms) {
        normal_wait_ms_ = stop_state_.last_time_ms -
            stop_state_.drain_started_ms;
    }
    return output;
}

void GlideMovementModule::LogStopPolicy(
        const BetterEndfield::Movement::GlideStopOutput& output,
        const char* source) {
    if (mode_ == Mode::observe ||
            stop_log_samples_ >= kMaximumStopLogSamples ||
            log_lines_ >= kMaximumLogLines - 1)
        return;
    if (stop_log_initialized_ &&
            logged_stop_phase_ == output.phase &&
            logged_stop_reason_ == output.reason &&
            logged_stop_fault_ == output.fault) return;
    char line[240]{};
    std::snprintf(line, sizeof(line),
        "stop_policy mode=%s source=%s phase=%s reason=%s fault=%s normal_wait_ms=%llu",
        mode_ == Mode::write_probe ? "write_probe" : "dry_run_probe",
        source, StopPhaseName(output.phase), StopReasonName(output.reason),
        StopFaultName(output.fault),
        static_cast<unsigned long long>(normal_wait_ms_));
    LogInfo(Id(), line);
    ++log_lines_;
    ++stop_log_samples_;
    stop_log_initialized_ = true;
    logged_stop_phase_ = output.phase;
    logged_stop_reason_ = output.reason;
    logged_stop_fault_ = output.fault;
}

void GlideMovementModule::TimerLoop() {
    std::unique_lock lock(timer_mutex_);
    while (!finished_.load(std::memory_order_acquire)) {
        const int64_t captured_deadline = deadline_ns_.load(
            std::memory_order_acquire);
        const auto expiry = std::chrono::steady_clock::time_point(
            std::chrono::duration_cast<std::chrono::steady_clock::duration>(
                std::chrono::nanoseconds(captured_deadline)));
        const bool interrupted = timer_condition_.wait_until(lock, expiry,
            [this, captured_deadline] {
                return finished_.load(std::memory_order_acquire) ||
                    deadline_ns_.load(std::memory_order_acquire) !=
                        captured_deadline;
            });
        if (interrupted) continue;
        if (finished_.load(std::memory_order_acquire)) return;
        lock.unlock();
        bool stale_deadline = false;
        bool soft_pending_started = false;
        bool hard_deadline_reached = false;
        {
            // Deadline extensions and soft-pending publication share this lock.
            // Recheck the captured value after acquiring it so a timeout from an
            // older deadline cannot override a newly extended sampling window.
            std::lock_guard observation_lock(observation_mutex_);
            if (finished_.load(std::memory_order_acquire)) return;
            const int64_t locked_now_ns = SteadyNanoseconds(
                std::chrono::steady_clock::now());
            const int64_t current_deadline = deadline_ns_.load(
                std::memory_order_acquire);
            stale_deadline = current_deadline != captured_deadline ||
                locked_now_ns < captured_deadline ||
                locked_now_ns < current_deadline;
            if (!stale_deadline) {
                if (mode_ != Mode::observe &&
                        !stop_soft_pending_.load(std::memory_order_acquire)) {
                    int64_t hard_deadline = captured_deadline +
                        static_cast<int64_t>(
                            BetterEndfield::Movement::kGlideNormalDrainMaxMs *
                            1000000ULL);
                    const int64_t absolute_hard = maximum_deadline_ns_.load(
                        std::memory_order_acquire);
                    if (absolute_hard != 0)
                        hard_deadline = std::min(hard_deadline, absolute_hard);
                    stop_hard_deadline_ns_.store(hard_deadline,
                        std::memory_order_release);
                    stop_soft_pending_.store(true, std::memory_order_release);
                    deadline_ns_.store(hard_deadline, std::memory_order_release);
                    BetterEndfield::Movement::GlideStopOutput pending{};
                    pending.next_state = stop_state_;
                    pending.phase = stop_state_.phase;
                    pending.reason = stop_state_.finish_reason;
                    pending.fault = stop_state_.finish_fault;
                    LogStopPolicy(pending, "TimerSoftPending");
                    soft_pending_started = true;
                } else {
                    hard_deadline_reached = true;
                }
            }
        }
        if (stale_deadline || soft_pending_started) {
            timer_condition_.notify_all();
            lock.lock();
            continue;
        }
        if (hard_deadline_reached) {
            const char* reason = sampling_started_.load(std::memory_order_acquire)
                ? "observation_timeout" : "startup_timeout";
            Finish(mode_ != Mode::observe ? "stop_policy_hard_deadline" : reason,
                mode_ != Mode::observe ? FinishCause::HardDeadline
                    : FinishCause::Unknown);
            return;
        }
        lock.lock();
    }
}

void GlideMovementModule::Finish(const char* reason, FinishCause cause) {
    {
        std::lock_guard lock(active_mutex_);
        if (finished_.load(std::memory_order_acquire)) return;
        finished_.store(true, std::memory_order_release);
        installation_complete_.store(false, std::memory_order_release);
        GlideMovementModule* active = this;
        active_.compare_exchange_strong(active, nullptr,
            std::memory_order_acq_rel);
    }
    timer_condition_.notify_all();
    const bool calc_velocity_removed = broker_.Remove(calc_velocity_stub_);
    const bool jump_removed = broker_.Remove(jump_stub_);
    const bool fixed_tick_removed = broker_.Remove(fixed_tick_stub_);
    std::lock_guard lock(observation_mutex_);
    if (runtime_ != nullptr) {
        if (entity_handle_ != 0) runtime_->FreeGcHandle(entity_handle_);
        if (movement_handle_ != 0) runtime_->FreeGcHandle(movement_handle_);
    }
    entity_handle_ = 0;
    movement_handle_ = 0;
    current_entity_ = nullptr;
    current_movement_ = nullptr;
    if (IsProbeMode(mode_) &&
            stop_state_.phase !=
                BetterEndfield::Movement::GlideStopPhase::Finished) {
        const int64_t now_ns = SteadyNanoseconds(
            std::chrono::steady_clock::now());
        using BetterEndfield::Movement::GlideStopFault;
        const bool hard_stop = cause == FinishCause::HardDeadline;
        bool manually_collapsed = cause == FinishCause::CandidateStopped;
        GlideStopFault fault = GlideStopFault::InvalidState;
        switch (cause) {
            case FinishCause::Unknown:
                fault = GlideStopFault::InvalidState;
                break;
            case FinishCause::PolicyTerminal:
                fault = GlideStopFault::InvalidState;
                break;
            case FinishCause::HardDeadline:
                fault = GlideStopFault::None;
                stop_hard_deadline_ns_.store(now_ns, std::memory_order_release);
                break;
            case FinishCause::CandidateStopped:
                fault = GlideStopFault::None;
                break;
            case FinishCause::Identity:
                fault = GlideStopFault::Identity;
                break;
            case FinishCause::Read:
                fault = GlideStopFault::Read;
                break;
            case FinishCause::Type:
                fault = GlideStopFault::Type;
                break;
            case FinishCause::NonFinite:
                fault = GlideStopFault::NonFinite;
                break;
            case FinishCause::Budget:
                fault = GlideStopFault::Budget;
                break;
            case FinishCause::Exception:
                fault = GlideStopFault::Exception;
                break;
        }
        const auto stop = StepStopPolicyLocked(now_ns, false, false, false,
            manually_collapsed,
            hard_stop ? GlideStopFault::None : fault);
        LogStopPolicy(stop, "Finish");
    }
    GlideInput clear{};
    clear.player_session = 1;
    clear.player_epoch = player_epoch_;
    clear.airborne = false;
    clear.config = EffectiveConfig();
    const auto clear_transition = BetterEndfield::Movement::StepGlide(
        candidate_, clear);
    if (clear_transition != GlideTransition::NoChange) {
        LogTransition(SteadyNanoseconds(std::chrono::steady_clock::now()), 0,
            clear_transition, "clear_reason=observer_finished");
    }
    const auto now = std::chrono::steady_clock::now();
    const bool sampled = sampling_started_.load(std::memory_order_acquire);
    const int64_t elapsed_ms = sampled
        ? std::chrono::duration_cast<std::chrono::milliseconds>(
            now - sample_started_at_).count()
        : std::chrono::duration_cast<std::chrono::milliseconds>(
            now - installed_at_).count();
    if (log_lines_ < kMaximumLogLines) {
        std::string line = std::string("ended steady_ns=") +
            std::to_string(SteadyNanoseconds(now)) + " token=0 transition=End active=" +
            (candidate_.active ? "1" : "0") + " reason=" + reason +
            " elapsed_ms=" + std::to_string(elapsed_ms) +
            " callbacks=" + std::to_string(callbacks_.load(std::memory_order_acquire)) +
            " getters=" + std::to_string(getter_calls_.load(std::memory_order_acquire)) +
            " jump_removed=" + (jump_removed ? "1" : "0") +
            " fixed_tick_removed=" + (fixed_tick_removed ? "1" : "0");
        if (mode_ == Mode::write_probe) {
            line += " calc_velocity_removed=" +
                std::string(calc_velocity_removed ? "1" : "0") +
                " write_total=" + std::to_string(write_total_) +
                " write_sampled=" + std::to_string(write_log_samples_) +
                " write_sample_cap_reached=" +
                    ((write_log_samples_ >= kMaximumWriteLogSamples &&
                        write_total_ > write_log_samples_) ? "1" : "0") +
                " compare_total=" + std::to_string(comparison_total_) +
                " compare_sampled=" + std::to_string(comparison_log_samples_) +
                " compare_sample_cap_reached=" +
                    ((comparison_log_samples_ >= kMaximumCompareLogSamples &&
                        comparison_total_ > comparison_log_samples_) ? "1" : "0") +
                " coalesced_total=" + std::to_string(calc_coalesced_total_) +
                " comparison_pending=" +
                    (has_pending_calc_comparison_ ? "1" : "0") +
                " pending_coalesced=" +
                    std::to_string(calc_pending_overwrites_) +
                " other_pipeline_total=" +
                    std::to_string(other_pipeline_total_) +
                " state_transition_logs_dropped=" +
                    std::to_string(state_transition_logs_dropped_);
        } else if (mode_ == Mode::dry_run_probe) {
            line += " dry_run=1 would_write_total=" +
                std::to_string(would_write_total_) +
                " other_pipeline_total=" +
                std::to_string(other_pipeline_total_) +
                " calc_velocity_removed=" +
                    (calc_velocity_removed ? "1" : "0");
        }
        if (IsProbeMode(mode_)) {
            line += " stop_phase=" + std::string(StopPhaseName(
                    stop_state_.phase)) +
                " stop_reason=" + StopReasonName(stop_state_.finish_reason) +
                " stop_fault=" + StopFaultName(stop_state_.finish_fault) +
                " normal_wait_ms=" + std::to_string(normal_wait_ms_);
        }
        if (mode_ == Mode::dry_run_probe) {
            line += " startup_limit_s=240 observation_limit_s=60 callback_limit=30000 getter_limit=150000 log_limit=256";
        }
        LogInfo(Id(), line.c_str());
        ++log_lines_;
    }
}

}  // namespace betterendfieldnext
