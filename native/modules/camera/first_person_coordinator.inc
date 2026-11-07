// One owner for effective perspective, transition output and visual lifetime.
// User-requested first person remains separate from temporary game ownership.
struct FpPerspectiveCoordinator {
    FpRoot camera;
    BetterEndfieldNext::FirstPerson::PolicyState policy;
    BetterEndfieldNext::FirstPerson::PolicyDecision decision;
    BetterEndfieldNext::FirstPerson::TransitionState transition;
    uint64_t last_time=0, visual_exit_deadline=0;
    unsigned reports=0;
    bool applied=false;
};
FpPerspectiveCoordinator g_fp_perspective;

bool FpReadPerspective(void* state,BetterEndfieldNext::FirstPerson::PerspectivePose& pose) {
    FpMath::Vec3 position{},correction{};
    FpMath::Quat rotation{},rotation_correction{};
    if (!ReadBytes(state,g_state_layout.raw_position,&position,sizeof(position)) ||
        !ReadBytes(state,g_state_layout.position_correction,&correction,sizeof(correction)) ||
        !ReadBytes(state,g_state_layout.raw_orientation,&rotation,sizeof(rotation)) ||
        !ReadBytes(state,g_state_layout.orientation_correction,&rotation_correction,sizeof(rotation_correction)) ||
        !ReadBytes(state,g_state_layout.lens+g_state_layout.lens_field_of_view,&pose.fov,sizeof(pose.fov)) ||
        !ReadBytes(state,g_state_layout.lens+g_state_layout.lens_near_clip,&pose.near_clip,sizeof(pose.near_clip))) return false;
    pose.position=position+correction;
    pose.rotation=rotation*rotation_correction;
    return BetterEndfieldNext::FirstPerson::ValidPose(pose);
}
void FpWritePerspective(void* state,const BetterEndfieldNext::FirstPerson::PerspectivePose& pose) {
    const FpMath::Vec3 zero{}; const FpMath::Quat identity{0,0,0,1};
    WriteBytes(state,g_state_layout.raw_position,&pose.position,sizeof(pose.position));
    WriteBytes(state,g_state_layout.position_correction,&zero,sizeof(zero));
    WriteBytes(state,g_state_layout.raw_orientation,&pose.rotation,sizeof(pose.rotation));
    WriteBytes(state,g_state_layout.orientation_correction,&identity,sizeof(identity));
    WriteBytes(state,g_state_layout.lens+g_state_layout.lens_field_of_view,&pose.fov,sizeof(pose.fov));
    WriteBytes(state,g_state_layout.lens+g_state_layout.lens_near_clip,&pose.near_clip,sizeof(pose.near_clip));
}
void FpResetPerspective() {
    g_fp_perspective.camera.Reset();
    g_fp_perspective.policy={}; g_fp_perspective.decision={};
    g_fp_perspective.transition={}; g_fp_perspective.last_time=0;
    g_fp_perspective.visual_exit_deadline=0;
    g_fp_perspective.applied=false;
}
void FpCoordinateState(void* brain,void* state) {
    using namespace BetterEndfieldNext::FirstPerson;
    if (!state || !g_state_layout.ready || g_free_camera_active) return;
    FpRoot camera(Invoke(Contract("unity.camera.main"),nullptr,nullptr));
    if (!camera) {
        FpResetPerspective();
        FpFacingUpdate(false,{},60); FpMotionReset(); FpRefreshHiddenParts(false);
        return;
    }
    const auto* object=Contract("unity.component.game_object");
    FpRoot brain_object(Invoke(object,brain,nullptr));
    FpRoot camera_object(Invoke(object,camera.object,nullptr));
    if (!brain_object || !camera_object || brain_object.object!=camera_object.object) return;
    auto& owner=g_fp_perspective;
    if (owner.camera.object!=camera.object) {
        FpResetPerspective(); owner.camera=std::move(camera);
    }
    PerspectivePose native_pose{};
    if (!FpReadPerspective(state,native_pose)) {
        FpFacingUpdate(false,{},60); FpMotionReset(); FpRefreshHiddenParts(false);
        FpResetPerspective(); return;
    }
    const uint64_t now=GetTickCount64();
    const float elapsed=owner.last_time && now>=owner.last_time ? float(now-owner.last_time)*.001f : 0.f;
    owner.last_time=now;
    PolicyInput input;
    input.requested=g_first_person_active.load(std::memory_order_acquire);
    input.yield_dialogue=g_first_person_yield_dialogue.load(std::memory_order_relaxed);
    input.third_person_in_combat=g_first_person_third_person_in_combat.load(std::memory_order_relaxed);
    input.elapsed=elapsed;
    if (input.requested) {
        g_fp_retract.Sample(input.game_camera,input.ultimate,input.cinematic);
    }
    if (input.requested && input.yield_dialogue) input.dialogue_known=FpDialogueActive(input.dialogue);
    if (input.requested && input.third_person_in_combat) input.combat_known=FpCombatActive(input.combat);
    const auto decision=StepPolicy(owner.policy,input);
    if (decision.reason!=owner.decision.reason && owner.reports<32) {
        ++owner.reports;
        Log(std::string("First person perspective reason=")+SuppressionName(decision.reason));
    }
    owner.decision=decision;
    const auto before=g_push_state_patches.load(std::memory_order_relaxed);
    if (decision.apply) ApplyFirstPersonState(state);
    else { FpFacingUpdate(false,{},60); FpMotionReset(); g_first_person.last_eye_valid=false; }
    const bool applied=g_push_state_patches.load(std::memory_order_relaxed)!=before;
    owner.applied=applied;
    PerspectivePose target=native_pose;
    if (applied && !FpReadPerspective(state,target)) {
        FpWritePerspective(state,native_pose); owner.transition={}; owner.applied=false; FpRefreshHiddenParts(false); return;
    }
    const bool game_owns=input.requested && !applied;
    const float seconds=game_owns ? 0.f : g_first_person_transition_seconds.load(std::memory_order_relaxed);
    const auto output=StepTransition(owner.transition,target,applied,elapsed,seconds);
    if (owner.transition.blending) {
        FpWritePerspective(state,output);
        if (applied) g_first_person.last_eye={output.position.x,output.position.y,output.position.z};
    }
    const bool keep_hidden=applied || owner.transition.blending;
    FpRefreshHiddenParts(keep_hidden, false);
    if (!keep_hidden) owner.visual_exit_deadline=0;
}
