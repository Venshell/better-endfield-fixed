#include <BetterEndfieldNext/ThirdPartyModule.h>

namespace {
BE_Result BE_CALL Initialize(const BE_ThirdPartyHostV1*, const char*) { return BE_Result_Ok; }
BE_Result BE_CALL Configure(const char*) { return BE_Result_Ok; }
BE_Result BE_CALL Message(const char*, const char*) { return BE_Result_Ok; }
void BE_CALL Shutdown() {}
const BE_ThirdPartyModuleV1 module{sizeof(BE_ThirdPartyModuleV1), 1, "fixture.native",
                                    Initialize, Configure, Message, Shutdown};
}

extern "C" __declspec(dllexport) const BE_ThirdPartyModuleV1* BE_CALL BetterEndfield_GetThirdPartyModuleV1() {
    return &module;
}
