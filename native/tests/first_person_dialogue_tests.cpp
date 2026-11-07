#include "../modules/camera/first_person_dialogue.h"
#include <iostream>

using namespace BetterEndfieldNext::FirstPerson;
namespace {
void* value=nullptr;
void Get(const void*,void* output) { *static_cast<void**>(output)=value; }
}
int main() {
    int field=1;
    void* manager=reinterpret_cast<void*>(1);
    if (ReadDialogueManager(nullptr,&field,manager)!=DialogueManagerRead::Unavailable || manager) return 1;
    value=nullptr;
    if (ReadDialogueManager(Get,&field,manager)!=DialogueManagerRead::Absent || manager) return 2;
    value=&field;
    if (ReadDialogueManager(Get,&field,manager)!=DialogueManagerRead::Present || manager!=value) return 3;
    if (ReadDialogueManager(Get,nullptr,manager)!=DialogueManagerRead::Unavailable || manager) return 4;
    std::cout << "first_person_dialogue: static manager absent, present and unavailable passed\n";
}
