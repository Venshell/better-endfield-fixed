#pragma once

#include "core/hook_broker.h"
#include "core/runtime.h"
#include "modules/module.h"
#include "modules/movement/glide_state.h"
#include "modules/movement/glide_stop_policy.h"

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <mutex>
#include <thread>

namespace betterendfieldnext {

// Default-off movement module. The product path accepts a typed config at
// construction; state observation and the write probes require explicit opt-in.
class GlideMovementModule final : public Module {
public:
    struct Vector3 { float x; float y; float z; };
    explicit GlideMovementModule(
        BetterEndfield::Movement::GlideConfig product_config = {});
    ~GlideMovementModule() override;
    const char* Id() const override { return "betterendfield.glide_movement"; }
    ModuleResult Start(Il2CppRuntime& runtime) override;

private:
    enum class Mode : uint8_t { observe, dry_run_probe, write_probe, product };
    struct PlayerSnapshot {
        void* entity = nullptr;
        void* movement = nullptr;
        bool airborne = false;
        uint32_t entity_handle = 0;
        uint32_t movement_handle = 0;
    };
    enum class SnapshotResult : uint8_t {
        ok,
        invalid_controller,
        identity_read_failed,
        root_failed,
        air_state_read_failed,
    };
    enum class PipelineVerification : uint8_t {
        verified,
        other_pipeline,
        observer_finished,
        invalid_pipeline_type,
        player_entity_read_failed,
        player_entity_invalid,
        player_entity_root_failed,
        player_movement_read_failed,
        player_movement_invalid,
        player_movement_root_failed,
        pipeline_movement_read_failed,
        pipeline_movement_invalid,
        pipeline_movement_root_failed,
        player_identity_changed,
        air_state_read_failed,
        air_state_invalid,
    };
    using JumpFn = void (*)(void* instance, const void* method_info);
    using FixedTickFn = void (*)(void* instance, float delta_time,
        const void* method_info);
    using CalcVelocityFallingFn = Vector3 (*)(void* instance,
        float delta_time, const void* method_info);

    static void JumpDetour(void* instance, const void* method_info);
    static void FixedTickDetour(void* instance, float delta_time,
        const void* method_info);
    static Vector3 CalcVelocityFallingDetour(void* instance,
        float delta_time, const void* method_info);
    static GlideMovementModule* EnterCallback();
    static void LeaveCallback(GlideMovementModule* module);
    SnapshotResult CapturePlayer(PlayerSnapshot& snapshot, bool read_airborne);
    void ReleasePlayerSnapshot(PlayerSnapshot& snapshot);
    void ObserveJump(void* instance, const PlayerSnapshot& before);
    void ObserveFixedTick(void* instance);
    bool ResolvePlayer(void*& entity, void*& movement, bool& airborne);
    bool ReadAirborne(void* movement, bool& airborne);
    PipelineVerification VerifyPipeline(void* pipeline, bool& airborne,
        void*& entity, void*& movement);
    Vector3 ObserveCalcVelocity(void* pipeline, float delta_time,
        const Vector3& original);
    enum class IdentityUpdate : uint8_t {
        unchanged,
        first_binding,
        rebound,
        changed,
        root_failed,
    };
    enum class FinishCause : uint8_t {
        Unknown,
        PolicyTerminal,
        HardDeadline,
        CandidateStopped,
        Identity,
        Read,
        Type,
        NonFinite,
        Budget,
        Exception,
    };
    IdentityUpdate UpdateIdentity(void* entity, void* movement, bool airborne);
    void TimerLoop();
    uint32_t CountCallback() noexcept;
    uint32_t CountGetterCall() noexcept;
    static bool IsProbeMode(Mode mode) noexcept;
    BetterEndfield::Movement::GlideConfig EffectiveConfig() const noexcept;
    void Finish(const char* reason, FinishCause cause = FinishCause::Unknown);
    BetterEndfield::Movement::GlideStopOutput StepStopPolicyLocked(
        int64_t steady_ns, bool identity_trusted, bool airborne_known,
        bool airborne, bool manually_collapsed,
        BetterEndfield::Movement::GlideStopFault fault);
    void LogStopPolicy(const BetterEndfield::Movement::GlideStopOutput& output,
        const char* source);
    void LogTransition(int64_t steady_ns, uint64_t token,
        BetterEndfield::Movement::GlideTransition transition,
        const char* reason);

    HookBroker broker_;
    Mode mode_ = Mode::observe;
    Il2CppRuntime* runtime_ = nullptr;
    void* jump_stub_ = nullptr;
    void* fixed_tick_stub_ = nullptr;
    void* calc_velocity_stub_ = nullptr;
    ResolvedMethod jump_{};
    ResolvedMethod fixed_tick_{};
    ResolvedMethod get_main_character_{};
    ResolvedMethod get_movement_component_{};
    ResolvedMethod get_is_in_air_{};
    ResolvedMethod calc_velocity_falling_{};
    ResolvedMethod get_move_comp_{};
    ResolvedMethod get_velocity_{};
    ResolvedMethod get_move_vector_{};
    ResolvedField input_field_{};
    ResolvedClass move_input_class_{};
    ResolvedClass player_controller_class_{};
    ResolvedClass entity_class_{};
    ResolvedClass movement_component_class_{};
    ResolvedClass air_pipeline_class_{};

    BetterEndfield::Movement::GlideState candidate_{};
    const BetterEndfield::Movement::GlideConfig product_config_;
    void* current_entity_ = nullptr;
    void* current_movement_ = nullptr;
    uint32_t entity_handle_ = 0;
    uint32_t movement_handle_ = 0;
    uint64_t player_epoch_ = 0;
    uint64_t next_token_ = 0;
    BetterEndfield::Movement::GroundTakeoffState grounded_takeoff_{};
    uint64_t last_auto_event_sequence_ = 0;
    bool any_jump_seen_ = false;
    bool saw_airborne_ = false;
    bool rebind_used_ = false;
    uint32_t log_lines_ = 0;
    bool has_pending_calc_comparison_ = false;
    uint32_t calc_pending_overwrites_ = 0;
    uint64_t calc_coalesced_total_ = 0;
    uint64_t calc_sequence_ = 0;
    Vector3 calc_returned_velocity_{};
    uint64_t write_total_ = 0;
    uint64_t would_write_total_ = 0;
    uint32_t write_log_samples_ = 0;
    bool has_write_log_sample_ = false;
    float last_logged_write_input_y_ = 0.0f;
    uint64_t comparison_total_ = 0;
    uint32_t comparison_log_samples_ = 0;
    bool has_comparison_log_sample_ = false;
    bool has_logged_comparison_y_ = false;
    float last_logged_comparison_y_ = 0.0f;
    uint64_t other_pipeline_total_ = 0;
    uint32_t state_transition_logs_emitted_ = 0;
    uint64_t state_transition_logs_dropped_ = 0;
    uint32_t auto_steering_log_samples_ = 0;

    std::atomic<uint32_t> callbacks_{0};
    std::atomic<uint32_t> getter_calls_{0};
    std::atomic<uint32_t> in_flight_{0};
    std::atomic<int64_t> deadline_ns_{0};
    std::atomic<int64_t> maximum_deadline_ns_{0};
    std::atomic<int64_t> stop_hard_deadline_ns_{0};
    std::atomic_bool stop_soft_pending_{false};
    std::atomic_bool sampling_started_{false};
    std::atomic_bool installation_complete_{false};
    std::atomic_bool finished_{false};
    std::chrono::steady_clock::time_point installed_at_{};
    std::chrono::steady_clock::time_point sample_started_at_{};
    std::mutex observation_mutex_;
    BetterEndfield::Movement::GlideStopState stop_state_{};
    uint64_t normal_wait_ms_ = 0;
    bool stop_log_initialized_ = false;
    uint32_t stop_log_samples_ = 0;
    BetterEndfield::Movement::GlideStopPhase logged_stop_phase_ =
        BetterEndfield::Movement::GlideStopPhase::Running;
    BetterEndfield::Movement::GlideStopReason logged_stop_reason_ =
        BetterEndfield::Movement::GlideStopReason::None;
    BetterEndfield::Movement::GlideStopFault logged_stop_fault_ =
        BetterEndfield::Movement::GlideStopFault::None;
    std::mutex in_flight_mutex_;
    std::condition_variable in_flight_condition_;
    std::mutex timer_mutex_;
    std::condition_variable timer_condition_;
    std::thread timer_thread_;

    static std::atomic<GlideMovementModule*> active_;
    static std::atomic_bool lifetime_claimed_;
    static std::mutex active_mutex_;
    static JumpFn jump_original_;
    static FixedTickFn fixed_tick_original_;
    static CalcVelocityFallingFn calc_velocity_falling_original_;
};

}  // namespace betterendfieldnext
