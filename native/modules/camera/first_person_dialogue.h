#pragma once

namespace BetterEndfieldNext::FirstPerson {
enum class DialogueManagerRead { Unavailable, Absent, Present };
using StaticFieldGet = void (*)(const void*,void*);

inline DialogueManagerRead ReadDialogueManager(StaticFieldGet get,const void* field,void*& manager) {
    manager=nullptr;
    if (!get || !field) return DialogueManagerRead::Unavailable;
    get(field,&manager);
    return manager ? DialogueManagerRead::Present : DialogueManagerRead::Absent;
}
} // namespace BetterEndfieldNext::FirstPerson
