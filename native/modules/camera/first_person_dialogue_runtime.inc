// Included after FpRoot. This adapter only samples dialogue ownership; it does
// not implement the upstream NPC-focus/angle restoration feature. Contracts
// are evidenced by camera_dialogue.hpp at the audited RenoDX commit.
struct FpDialogueSampler {
    MethodContract playing{"dialogue.playing",
        {"Gameplay.Beyond.dll","Beyond.Gameplay.Core","DialogManager",
            "get_isPlaying",nullptr,"System.Boolean",0}};
    MethodContract preparing{"dialogue.preparing",
        {"Gameplay.Beyond.dll","Beyond.Gameplay.Core","DialogManager",
            "get_isPreparing",nullptr,"System.Boolean",0}};
    BE_ResolvedFieldV1 manager_field{};
    BetterEndfieldNext::FirstPerson::StaticFieldGet static_get = nullptr;
    bool attempted = false;
    bool ready = false;
    unsigned reports = 0;

    bool Unavailable(const char* stage) {
        if (reports < 8) {
            ++reports;
            Log(std::string("First person dialogue state unavailable: ") + stage);
        }
        return false;
    }
    bool Resolve(MethodContract& method) {
        BE_ResolvedMethodV1 resolved{};
        if (g_host->resolve_method(g_host->context,&method.descriptor,&resolved) != BE_Result_Ok ||
            !resolved.method_info) return Unavailable(method.key);
        method.method_info=resolved.method_info;
        method.resolved=true;
        return true;
    }
    bool Init() {
        if (attempted) return ready;
        attempted=true;
        if (!g_host || !g_host->resolve_method || !g_host->resolve_field ||
            !g_host->runtime_invoke ||
            !g_host->object_unbox || !g_host->gchandle_new || !g_host->gchandle_free)
            return Unavailable("host capabilities");
        const BE_FieldDescriptorV1 descriptor{"Gameplay.Beyond.dll","Beyond.Gameplay.Core",
            "GameWorld","dialogManager","Beyond.Gameplay.Core.DialogManager"};
        if (g_host->resolve_field(g_host->context,&descriptor,&manager_field) != BE_Result_Ok ||
            !manager_field.field_info) return Unavailable("GameWorld.dialogManager metadata");
        const auto assembly=GetModuleHandleW(L"GameAssembly.dll");
        if (!assembly) return Unavailable("runtime module");
        static_get=reinterpret_cast<decltype(static_get)>(
            GetProcAddress(assembly,"il2cpp_field_static_get_value"));
        if (!static_get) return Unavailable("static field getter");
        using FieldFlags = uint32_t(*)(const void*);
        const auto flags=reinterpret_cast<FieldFlags>(GetProcAddress(assembly,"il2cpp_field_get_flags"));
        // FIELD_ATTRIBUTE_STATIC. A renamed instance field must not be read
        // through the null owner used for the real GameWorld static field.
        if (!flags || !(flags(manager_field.field_info) & 0x10u))
            return Unavailable("dialogManager is not a verified static field");
        ready=Resolve(playing) && Resolve(preparing);
        return ready;
    }
    bool Sample(bool& active) {
        // `active` is meaningful only when this method returns true.
        active=false;
        if (!Init()) return false;
        void* manager_object=nullptr;
        const auto manager_read=BetterEndfieldNext::FirstPerson::ReadDialogueManager(
            static_get,manager_field.field_info,manager_object);
        if (manager_read==BetterEndfieldNext::FirstPerson::DialogueManagerRead::Unavailable)
            return Unavailable("dialogue manager read unavailable");
        // GameWorld has no dialogue owner before a manager is created. The
        // upstream dialogue path treats this state as inactive as well.
        if (manager_read==BetterEndfieldNext::FirstPerson::DialogueManagerRead::Absent) return true;
        FpRoot manager(manager_object);
        if (!manager) return Unavailable("dialogue manager could not be rooted");
        bool is_playing=false,is_preparing=false;
        if (!Unbox(Invoke(&playing,manager.object,nullptr),is_playing) ||
            !Unbox(Invoke(&preparing,manager.object,nullptr),is_preparing))
            return Unavailable("dialogue getter failed");
        active=is_playing || is_preparing;
        return true;
    }
};
FpDialogueSampler g_fp_dialogue;

bool FpDialogueActive(bool& active) {
    return g_fp_dialogue.Sample(active);
}
