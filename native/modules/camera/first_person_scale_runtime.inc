// Explicit external-mod compatibility. Never scale the model-root eye fallback.
struct FpExternalHeadScale {
    MethodContract get{"head_scale.get",{"UnityEngine.CoreModule.dll","UnityEngine","Transform","get_localScale",nullptr,"UnityEngine.Vector3",0}};
    MethodContract set{"head_scale.set",{"UnityEngine.CoreModule.dll","UnityEngine","Transform","set_localScale","UnityEngine.Vector3","System.Void",1}};
    FpRoot head;
    BetterEndfieldNext::FirstPerson::HeadScaleLease lease;
    bool attempted=false, ready=false, applied=false;
    unsigned reports=0;
    void Report(const char* reason) {
        if (reports<8) { ++reports; Log(std::string("First person external head scale: ")+reason); }
    }
    bool Init() {
        if (attempted) return ready;
        attempted=true;
        if (!g_host || !g_host->resolve_class || !g_host->gchandle_new || !g_host->gchandle_free) return false;
        for (auto* method : {&get,&set}) {
            BE_ResolvedMethodV1 resolved{};
            if (g_host->resolve_method(g_host->context,&method->descriptor,&resolved)!=BE_Result_Ok || !resolved.method_info) {
                Report("scale contract unavailable"); return false;
            }
            method->method_info=resolved.method_info; method->resolved=true;
        }
        BE_ResolvedClassV1 vector{};
        using ValueSize=int32_t(*)(const void*,uint32_t*);
        auto game=GetModuleHandleW(L"GameAssembly.dll");
        auto size=game ? reinterpret_cast<ValueSize>(GetProcAddress(game,"il2cpp_class_value_size")) : nullptr;
        uint32_t alignment=0;
        ready=size && g_host->resolve_class(g_host->context,"UnityEngine.CoreModule.dll","UnityEngine","Vector3",&vector)==BE_Result_Ok
            && vector.class_info && FpValue<int32_t>(0,size,vector.class_info,&alignment)==sizeof(FpMath::Vec3);
        return ready;
    }
    bool Alive(bool& alive) {
        alive=false;
        if (!head.object) return true;
        void* args[]{head.object,nullptr}; bool destroyed=true;
        if (!Unbox(Invoke(Contract("unity.object.op_equality"),nullptr,args),destroyed)) return false;
        alive=!destroyed; return true;
    }
    bool Value(FpMath::Vec3& value) {
        return Unbox(Invoke(&get,head.object,nullptr),value)&&FpMath::Finite(value);
    }
    bool Write(FpMath::Vec3 value) { void* args[]{&value}; return InvokeVoid(&set,head.object,args); }
    bool Release(bool lifecycle=false) {
        if (lifecycle && lease.blocked) { lease.blocked=false; lease.failures=0; }
        if (lease.owned) {
            if (lease.blocked) return false;
            bool alive=false; FpMath::Vec3 current{};
            if (!Alive(alive) || (alive && !Value(current))) {
                lease.RestoreResult(false); Report("restore read failed; ownership retained"); return false;
            }
            if (lease.ShouldRestore(current,alive)) {
                const bool written=Write(lease.original);
                FpMath::Vec3 observed{};
                const bool read_back=written&&Value(observed);
                const bool restored=lease.CompleteRestore(written,read_back,observed);
                if (!restored) { Report("restore write/readback failed; ownership retained"); return false; }
            }
        }
        if (lease.owned) return false;
        head.Reset(); applied=false;
        return true;
    }
    void Close() {
        if (!Release(true)) {
            for (unsigned retry = 0; retry < 2 && !Release(); ++retry) {}
        }
        if (head.object) {
            Report("shutdown restore exhausted; previous scale could not be verified");
            head.Reset(); lease={}; applied=false;
        }
    }
    bool Apply() {
        if (!Init()) return false;
        void* target=g_first_person.head;
        if (head.object!=target && !Release()) return false;
        if (!target || target==FindModelTransform()) return false;
        // Exact known bone tokens; substring matches such as a renderer named
        // "headwear" are not sufficient authority for a skeleton write.
        const auto name=LowerAscii(ObjectName(target));
        if (name!="head" && name!="bip_head" && name!="bip001_head" &&
            name!="bip001 head" && name!="j_head" && name!="head_m") return false;
        if (head.object==target && applied) {
            bool alive=false; FpMath::Vec3 current{};
            if (!Alive(alive) || !alive || !Value(current)) return false;
            if (lease.owned && !BetterEndfieldNext::FirstPerson::SameHeadScale(current,lease.written)) {
                lease={}; Report("external owner changed scale; leaving its value untouched");
            }
            return lease.owned;
        }
        head=FpRoot(target);
        bool alive=false; FpMath::Vec3 current{};
        if (!head || !Alive(alive) || !alive || !Value(current) || !lease.Acquire(current)) return false;
        applied=true;
        if (!Write(lease.written)) { Release(); Report("scale write rejected"); return false; }
        FpMath::Vec3 written{};
        if (!Value(written) || !BetterEndfieldNext::FirstPerson::SameHeadScale(written,lease.written)) {
            Release(); Report("scale verification failed"); return false;
        }
        return true;
    }
};
FpExternalHeadScale g_fp_external_scale;
