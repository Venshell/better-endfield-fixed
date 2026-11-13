#pragma once

#include "modules/module.h"

namespace betterendfieldnext {

// LAN rooms from the EF Link protocol, sampled on the game thread.
class EflinkModule final : public Module {
public:
    const char* Id() const override;
    ModuleResult Start(Il2CppRuntime& runtime) override;
};

}  // namespace betterendfieldnext
