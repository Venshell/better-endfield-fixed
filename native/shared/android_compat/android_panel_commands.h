#pragma once

// The panel's command channel to the desktop modules, as those modules see it.
//
// Same arrangement as the virtual-key latch beside this header: the desktop
// sources are compiled unchanged, so whatever the Android host has to offer them
// arrives through the compatibility <Windows.h> chain. The implementation
// forwards to the runtime command pump
// (android/app/src/main/cpp/core/command_pump.cpp), which the settings app feeds
// through the framework's remote file space and the game process relays.
//
// Windows has no such channel: there the configuration is read once, from the
// environment, and every caller keeps its `#if !defined(_WIN32)` branch.

#if !defined(_WIN32)

#include <string>

namespace betterendfieldnext {

// Takes the pending command when, and only when, it names `command`, returning
// the text after the command line in `value`. The pump holds one slot shared by
// every module in the process, so this leaves another module's command in place
// instead of swallowing it. False means "nothing for this caller".
bool AcquirePanelCommand(const char* command, std::string& value);

// Reports an outcome back to whoever issued the command. Only "applied",
// "rejected" and "unsupported" are accepted; the value is readable from the
// settings screen's diagnostics page.
void AcknowledgePanelCommand(const char* status);

}  // namespace betterendfieldnext

#endif
