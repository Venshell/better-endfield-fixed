#pragma once
#include <algorithm>
#include <cmath>

namespace BetterEndfieldNext::FirstPerson {
enum class Suppression { None, NotRequested, DialogueUnavailable, Dialogue,
    CombatUnavailable, Combat, CombatCooldown, GameCamera, Ultimate, Cinematic };
struct PolicyInput {
    bool requested = false;
    bool game_camera = false, ultimate = false, cinematic = false;
    bool yield_dialogue = false, dialogue_known = false, dialogue = false;
    bool third_person_in_combat = false, combat_known = false, combat = false;
    float elapsed = 0, return_delay = .5f;
};
struct PolicyState { float remaining = 0; };
struct PolicyDecision { bool apply = false; Suppression reason = Suppression::NotRequested; };
inline PolicyDecision StepPolicy(PolicyState& state, const PolicyInput& input) {
    if (!input.requested) { state = {}; return {}; }
    const float elapsed = std::isfinite(input.elapsed) ? std::clamp(input.elapsed,0.f,.1f) : 0.f;
    const float delay = std::isfinite(input.return_delay) ? std::clamp(input.return_delay,0.f,10.f) : .5f;
    if (!input.third_person_in_combat) state.remaining = 0;
    else if (input.combat_known) {
        if (input.combat) state.remaining = delay;
        else state.remaining = std::max(0.f,state.remaining-elapsed);
    }
    // Game-owned shots take precedence over the configured dialogue/combat
    // handoff. The user's first-person request remains armed throughout.
    if (input.game_camera) return {false,Suppression::GameCamera};
    if (input.ultimate) return {false,Suppression::Ultimate};
    if (input.cinematic) return {false,Suppression::Cinematic};
    if (input.yield_dialogue) {
        if (!input.dialogue_known) return {false,Suppression::DialogueUnavailable};
        if (input.dialogue) return {false,Suppression::Dialogue};
    }
    if (input.third_person_in_combat) {
        if (!input.combat_known) return {false,Suppression::CombatUnavailable};
        if (input.combat) return {false,Suppression::Combat};
        if (state.remaining > .0001f) return {false,Suppression::CombatCooldown};
    }
    return {true,Suppression::None};
}
inline const char* SuppressionName(Suppression reason) {
    switch (reason) {
    case Suppression::None: return "first_person";
    case Suppression::NotRequested: return "not_requested";
    case Suppression::DialogueUnavailable: return "dialogue_unavailable";
    case Suppression::Dialogue: return "dialogue";
    case Suppression::CombatUnavailable: return "combat_unavailable";
    case Suppression::Combat: return "combat";
    case Suppression::CombatCooldown: return "combat_cooldown";
    case Suppression::GameCamera: return "game_camera";
    case Suppression::Ultimate: return "ultimate";
    case Suppression::Cinematic: return "cinematic";
    }
    return "unknown";
}
} // namespace BetterEndfieldNext::FirstPerson
