#include "android_panel_commands.h"

#if !defined(_WIN32)

#include "core/command_pump.h"

namespace betterendfieldnext {

bool AcquirePanelCommand(const char* command, std::string& value) {
    return AcquireRuntimeCommand(command, value);
}

void AcknowledgePanelCommand(const char* status) {
    AcknowledgeRuntimeCommand(status);
}

}  // namespace betterendfieldnext

#endif
