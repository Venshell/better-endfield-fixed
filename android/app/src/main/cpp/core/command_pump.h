#pragma once
#include <cstddef>
#include <string>

namespace betterendfieldnext {
bool SubmitRuntimeCommand(const char* payload, size_t size);
// Takes the pending command only when it names the caller's own command, and
// returns everything after the command line in `value`. The slot is shared by
// every module in the process, so a consumer that took whatever happened to be
// there would starve the others: the panel's camera reload and the model
// replacement command ride this same pump, and whichever hooked function ran
// first would swallow the other module's command. Another module's command is
// left in the slot, untouched, and returns false.
bool AcquireRuntimeCommand(const char* command, std::string& value);
void AcknowledgeRuntimeCommand(const char* status);
std::string CopyRuntimeCommandStatus();
}
