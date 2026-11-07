// Included after first_person_runtime.inc. The pure facing core is included by
// module.cpp at file scope. Managed calls stay behind exact metadata contracts.
// Behaviour adapted from RenoDX camera_movement.hpp (MIT); see notices.
namespace FpFacingDetail {
enum Method : size_t {
    Player, Axes, Started, Released, Paused, Cinematic, Movement, Controller,
    ScriptControlled, Blocked, Animated, AnimatorComponent, Animator,
    ComponentTransform, Find, Parent, LocalRotation, WorldRotation,
    SetLocalRotation, SetWorldRotation, Rotator, EntityRotation, SetFacing,
    Alive, Count
};
struct AxesValue { float x = 0, y = 0; };
constexpr const char* kGame = "Gameplay.Beyond.dll";
constexpr const char* kUnity = "UnityEngine.CoreModule.dll";
constexpr const char* kCore = "Beyond.Gameplay.Core";
constexpr const char* kView = "Beyond.Gameplay.View";
const BE_MethodDescriptorV1 kMethods[] = {
    {kGame,"Beyond.Gameplay","GameInstance","get_playerController",nullptr,"Beyond.Gameplay.Core.PlayerController",0},
    {kGame,kCore,"PlayerController","get_moveAxis",nullptr,"UnityEngine.Vector2",0},
    {kGame,kCore,"Entity","get_markStarted",nullptr,"System.Boolean",0},
    {kGame,kCore,"Entity","get_markReleased",nullptr,"System.Boolean",0},
    {kGame,kCore,"Entity","get_isPaused",nullptr,"System.Boolean",0},
    {kGame,kCore,"Entity","get_inCinematic",nullptr,"System.Boolean",0},
    {kGame,kCore,"Entity","get_movementComponent",nullptr,"Beyond.Gameplay.Core.MovementComponent",0},
    {kGame,kCore,"Entity","get_baseController",nullptr,"Beyond.Gameplay.Core.BaseController",0},
    {kGame,kCore,"CharacterController","get_isScriptControlled",nullptr,"System.Boolean",0},
    {kGame,kCore,"MovementComponent","get_blockGroundedMove",nullptr,"System.Boolean",0},
    {kGame,kCore,"MoveInput","get_hasAnimatedMove",nullptr,"System.Boolean",0},
    {kGame,kCore,"Entity","get_animatorCom",nullptr,"Beyond.Gameplay.View.ComplexAnimatorComponent",0},
    {kGame,kView,"ComplexAnimatorComponent","get_animator",nullptr,"UnityEngine.Animator",0},
    {kUnity,"UnityEngine","Component","get_transform",nullptr,"UnityEngine.Transform",0},
    {kUnity,"UnityEngine","Transform","Find","System.String","UnityEngine.Transform",1},
    {kUnity,"UnityEngine","Transform","get_parent",nullptr,"UnityEngine.Transform",0},
    {kUnity,"UnityEngine","Transform","get_localRotation",nullptr,"UnityEngine.Quaternion",0},
    {kUnity,"UnityEngine","Transform","get_rotation",nullptr,"UnityEngine.Quaternion",0},
    {kUnity,"UnityEngine","Transform","set_localRotation","UnityEngine.Quaternion","System.Void",1},
    {kUnity,"UnityEngine","Transform","set_rotation","UnityEngine.Quaternion","System.Void",1},
    {kGame,kCore,"Entity","get_rotateCom",nullptr,"Beyond.Gameplay.Core.RotatorComponent",0},
    {kGame,kCore,"Entity","get_rotation",nullptr,"UnityEngine.Quaternion",0},
    {kGame,kCore,"RotatorComponent","SetRotation","UnityEngine.Quaternion","System.Void",1},
    {kUnity,"UnityEngine","Object","op_Implicit","UnityEngine.Object","System.Boolean",1}
};
static_assert(sizeof(kMethods)/sizeof(kMethods[0]) == Count);
struct Runtime {
    BE_ResolvedMethodV1 methods[Count]{};
    BE_ResolvedFieldV1 input{};
    BE_ResolvedClassV1 controller_class{};
    const void* (*object_class)(void*) = nullptr;
    bool (*assignable)(const void*,const void*) = nullptr;
    int32_t (*value_size)(const void*,uint32_t*) = nullptr;
    bool attempted = false, ready = false, owns_visual = false;
    bool restore_failed = false;
    unsigned restore_attempts = 0;
    bool interaction_alignment = false;
    unsigned failures = 0;
    FpRoot entity, skeleton;
    FpMath::Quat original{0,0,0,1}, written{0,0,0,1};
    BetterEndfieldNext::FirstPerson::FacingState state{};
    uint64_t last_time = 0;

    void Failure(const char* stage) {
        if (failures++ < 8) Log(std::string("First person facing: ") + stage);
        // Saturate to avoid eventually wrapping and reopening the log budget.
        if (failures > 8) failures = 8;
    }
    bool Init() {
        if (attempted) return ready;
        attempted = true;
        if (!g_host || !g_host->resolve_method || !g_host->resolve_class ||
            !g_host->resolve_field || !g_host->field_get_value_object ||
            !g_host->runtime_invoke || !g_host->gchandle_new ||
            !g_host->gchandle_free || !g_host->object_unbox || !g_host->string_new) {
            Failure("required host API unavailable; feature disabled"); return false;
        }
        const auto assembly = GetModuleHandleW(L"GameAssembly.dll");
        if (!assembly) { Failure("runtime unavailable; feature disabled"); return false; }
        object_class = reinterpret_cast<decltype(object_class)>(GetProcAddress(assembly,"il2cpp_object_get_class"));
        assignable = reinterpret_cast<decltype(assignable)>(GetProcAddress(assembly,"il2cpp_class_is_assignable_from"));
        value_size = reinterpret_cast<decltype(value_size)>(GetProcAddress(assembly,"il2cpp_class_value_size"));
        if (!object_class || !assignable || !value_size) {
            Failure("type validation exports missing; feature disabled"); return false;
        }
        for (size_t i=0; i<Count; ++i) {
            if (g_host->resolve_method(g_host->context,&kMethods[i],&methods[i]) != BE_Result_Ok ||
                !methods[i].method_info) {
                Failure(kMethods[i].method_name); return false;
            }
        }
        BE_FieldDescriptorV1 field{kGame,kCore,"MovementComponent","input","Beyond.Gameplay.Core.MoveInput"};
        if (g_host->resolve_field(g_host->context,&field,&input) != BE_Result_Ok || !input.field_info ||
            g_host->resolve_class(g_host->context,kGame,kCore,"CharacterController",&controller_class) != BE_Result_Ok ||
            !controller_class.class_info) {
            Failure("movement input/controller metadata mismatch"); return false;
        }
        static_assert(sizeof(AxesValue) <= INT32_MAX && sizeof(FpMath::Quat) <= INT32_MAX);
        for (const auto& item : {std::pair<const char*,int32_t>{"Vector2",static_cast<int32_t>(sizeof(AxesValue))},
                std::pair<const char*,int32_t>{"Quaternion",static_cast<int32_t>(sizeof(FpMath::Quat))}}) {
            BE_ResolvedClassV1 type{}; uint32_t alignment=0;
            if (g_host->resolve_class(g_host->context,kUnity,"UnityEngine",item.first,&type) != BE_Result_Ok ||
                !type.class_info || value_size(type.class_info,&alignment) != item.second) {
                Failure("Unity value type size mismatch"); return false;
            }
        }
        ready = true;
        Log("First person facing: managed contracts ready.");
        return true;
    }
    bool Call(Method method,void* instance,void** parameters,void*& result) {
        result = nullptr;
        if (!ready || !methods[method].method_info) return false;
        void* exception=nullptr;
        result=g_host->runtime_invoke(g_host->context,methods[method].method_info,instance,parameters,&exception);
        if (exception) { Failure(kMethods[method].method_name); return false; }
        return true;
    }
    void* Object(Method method,void* instance,void** parameters=nullptr) {
        void* result=nullptr;
        return Call(method,instance,parameters,result) ? result : nullptr;
    }
    template<class T> bool Value(Method method,void* instance,T& value) {
        void* result=nullptr;
        if (!Call(method,instance,nullptr,result)) return false;
        if (!Unbox(result,value)) { Failure(kMethods[method].method_name); return false; }
        return true;
    }
    bool Set(Method method,void* instance,FpMath::Quat value) {
        if (!FpMath::Unit(value)) return false;
        void* parameters[]{&value}; void* ignored=nullptr;
        return Call(method,instance,parameters,ignored);
    }
    bool IsAlive(void* object,bool& alive) {
        alive=false;
        if (!object) return true;
        void* parameters[]{object}; void* result=nullptr;
        return Call(Alive,nullptr,parameters,result) && Unbox(result,alive);
    }
    static bool Same(FpMath::Quat a,FpMath::Quat b) {
        return BetterEndfieldNext::FirstPerson::SameOwnedRotation(a,b);
    }
    bool Restore() {
        if (!owns_visual) return true;
        if (restore_failed) return false;
        if (++restore_attempts > 3) {
            restore_failed=true;
            Failure("restore retry budget exhausted; target retained, facing suspended");
            return false;
        }
        bool alive=false;
        if (!IsAlive(skeleton.object,alive)) { Failure("restore liveness unavailable"); return false; }
        if (!alive) { owns_visual=false; restore_attempts=0; return true; }
        FpMath::Quat current{};
        if (!Value(LocalRotation,skeleton.object,current) || !FpMath::Unit(current)) {
            Failure("restore local rotation unavailable"); return false;
        }
        // Another owner (animation/mod) has replaced our pose: relinquish it.
        if (!Same(current,written)) { owns_visual=false; restore_attempts=0; return true; }
        if (!Set(SetLocalRotation,skeleton.object,original)) {
            Failure("restore write rejected; retaining target for retry"); return false;
        }
        owns_visual=false;
        restore_attempts=0;
        return true;
    }
    bool Release() {
        state={}; last_time=0;
        if (!Restore()) return false;
        entity.Reset(); skeleton.Reset();
        return true;
    }
};
} // namespace FpFacingDetail
FpFacingDetail::Runtime g_fp_facing;

void FpFacingRestoreBeforeTick() {
    if (g_fp_facing.ready) g_fp_facing.Restore();
}
void FpFacingRelease() {
    g_fp_facing.interaction_alignment=false;
    // Explicit lifecycle release grants one new bounded recovery budget. The
    // ordinary inactive per-frame path below never reopens an exhausted one.
    if (g_fp_facing.restore_failed) {
        g_fp_facing.restore_failed=false;
        g_fp_facing.restore_attempts=0;
    }
    g_fp_facing.Release();
}
void FpFacingClose() {
    auto& runtime=g_fp_facing;
    if (runtime.restore_failed) {
        runtime.restore_failed=false;
        runtime.restore_attempts=0;
    }
    for (unsigned retry=0; retry<3; ++retry) if (runtime.Release()) return;
    runtime.Failure("shutdown rotation restore exhausted; previous pose could not be verified");
    runtime.entity.Reset(); runtime.skeleton.Reset(); runtime.owns_visual=false;
}
// Shared eligibility owns the action-state contract for facing and animation.
// Sampling holds temporary managed roots but never writes or restores a pose.
bool FpFacingEligible(FpRoot& entity) {
    using namespace FpFacingDetail;
    auto& runtime=g_fp_facing;
    runtime.interaction_alignment=false;
    if (!runtime.Init() || runtime.restore_failed) return false;
    entity=FpRoot(Invoke(Contract("player_controller.get_main_character"),nullptr,nullptr));
    bool started=false,released=true,paused=true,cinematic=true;
    if (!entity || !runtime.Value(Started,entity.object,started) || !started ||
        !runtime.Value(Released,entity.object,released) || released ||
        !runtime.Value(Paused,entity.object,paused) || paused ||
        !runtime.Value(Cinematic,entity.object,cinematic) || cinematic) {
        return false;
    }
    FpRoot controller(runtime.Object(Controller,entity.object));
    FpRoot movement(runtime.Object(Movement,entity.object));
    FpRoot input(movement ? g_host->field_get_value_object(g_host->context,runtime.input.field_info,movement.object) : nullptr);
    if (!controller || !movement || !input ||
        !runtime.assignable(runtime.controller_class.class_info,runtime.object_class(controller.object))) {
        return false;
    }
    bool scripted=true,blocked=true,animated=true;
    if (!runtime.Value(ScriptControlled,controller.object,scripted) ||
        !runtime.Value(Blocked,movement.object,blocked) ||
        !runtime.Value(Animated,input.object,animated)) return false;
    runtime.interaction_alignment=scripted || blocked;
    return !runtime.interaction_alignment && !animated;
}
bool FpFacingAnimationAllowed() {
    FpRoot entity;
    return FpFacingEligible(entity);
}
void FpFacingUpdate(bool active,FpMath::Vec3 view,float limit) {
    using namespace FpFacingDetail;
    auto& runtime=g_fp_facing;
    runtime.interaction_alignment=false;
    if (!active || !FpMath::Finite(view) || std::hypot(view.x,view.z)<.001f) {
        runtime.Release(); return;
    }
    FpRoot entity;
    if (!FpFacingEligible(entity)) { runtime.Release(); return; }
    FpRoot component(runtime.Object(AnimatorComponent,entity.object));
    FpRoot animator(component ? runtime.Object(Animator,component.object) : nullptr);
    FpRoot transform(animator ? runtime.Object(ComponentTransform,animator.object) : nullptr);
    FpRoot path(g_host->string_new(g_host->context,"Root"));
    bool alive=false;
    if (!transform || !path || !runtime.IsAlive(transform.object,alive) || !alive) { runtime.Release(); return; }
    void* find_args[]{path.object};
    FpRoot skeleton(runtime.Object(Find,transform.object,find_args));
    if (!skeleton || !runtime.IsAlive(skeleton.object,alive) || !alive ||
        skeleton.object==transform.object || runtime.Object(Parent,skeleton.object)!=transform.object) {
        runtime.Release(); return;
    }
    if (runtime.entity.object!=entity.object || runtime.skeleton.object!=skeleton.object) {
        if (!runtime.Release()) return;
        runtime.entity=std::move(entity); runtime.skeleton=std::move(skeleton);
    }
    // Restore before sampling this frame, including multiple pushes per tick.
    if (!runtime.Restore()) return;
    FpMath::Quat local{},world{};
    if (!runtime.Value(LocalRotation,runtime.skeleton.object,local) || !FpMath::Unit(local) ||
        !runtime.Value(WorldRotation,runtime.skeleton.object,world) || !FpMath::Unit(world)) {
        runtime.Failure("invalid skeleton rotation"); runtime.Release(); return;
    }
    FpRoot player(runtime.Object(Player,nullptr)); AxesValue axes{};
    if (!player || !runtime.Value(Axes,player.object,axes)) { runtime.Release(); return; }
    const uint64_t now=GetTickCount64();
    const float elapsed=runtime.last_time && now>=runtime.last_time ?
        std::min(float(now-runtime.last_time)*.001f,.1f) : 0.f;
    runtime.last_time=now;
    const float yaw=std::atan2(view.x,view.z)*57.295779513f;
    const auto result=BetterEndfieldNext::FirstPerson::StepFacing(runtime.state,
        {true,yaw,{axes.x,0,axes.y},limit,elapsed});
    if (result.mode==BetterEndfieldNext::FirstPerson::FacingMode::Inactive) { runtime.Release(); return; }
    if (result.mode==BetterEndfieldNext::FirstPerson::FacingMode::Entity) {
        FpRoot rotator(runtime.Object(Rotator,runtime.entity.object)); FpMath::Quat rotation{};
        if (!rotator || !runtime.Value(EntityRotation,runtime.entity.object,rotation) || !FpMath::Unit(rotation)) {
            runtime.Release(); return;
        }
        const auto forward=FpMath::Rotate(rotation,{0,0,1});
        const float delta=std::remainder(result.yaw-std::atan2(forward.x,forward.z)*57.295779513f,360.f);
        if (std::abs(delta)>.01f && !runtime.Set(SetFacing,rotator.object,FpMath::AxisAngle({0,1,0},delta)*rotation))
            runtime.Release();
        return;
    }
    const auto forward=FpMath::Rotate(world,{0,0,1});
    const float delta=std::remainder(result.yaw-std::atan2(forward.x,forward.z)*57.295779513f,360.f);
    const auto wanted=FpMath::AxisAngle({0,1,0},delta)*world;
    runtime.original=local;
    // Predict the resulting local rotation so a failed readback still leaves
    // an ownership value with which recovery can safely compare next tick.
    const FpMath::Quat inverse_world{-world.x,-world.y,-world.z,world.w};
    runtime.written=local*inverse_world*wanted;
    runtime.owns_visual=true;
    runtime.restore_attempts=0;
    if (!runtime.Set(SetWorldRotation,runtime.skeleton.object,wanted)) { runtime.Release(); return; }
    FpMath::Quat written{};
    if (runtime.Value(LocalRotation,runtime.skeleton.object,written) && FpMath::Unit(written)) runtime.written=written;
    else { runtime.Failure("visual write readback failed; recovery retained"); runtime.state={}; }
}
