#pragma once
#include "modules/module.h"
#include "core/runtime.h"

namespace betterendfieldnext {

// Read-only research probe that answers where the game's own first-person look
// input lives, so a gyroscope source can feed that instead of writing the pushed
// CameraState. Enabled only when the debug system property
// debug.betterendfield.fp_look_probe is set, and it only reads metadata: no
// hooks are installed and no game state is written.
class FirstPersonLookProbe final : public Module {
public:
    const char* Id() const override { return "betterendfield.fp_look_probe"; }
    ModuleResult Start(Il2CppRuntime& runtime) override;
};

}  // namespace betterendfieldnext
