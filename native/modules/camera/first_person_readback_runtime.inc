// Standard managed GraphicsBuffer copy/read path. No Unity native descriptor
// aliases or platform-specific offsets. Included after FpRoot and FpCopy.
struct FpSynchronousReadback {
    enum Method { Count, Stride, Target, Construct, Copy, Read, Dispose,
        VertexBuffer, IndexBuffer, VertexTarget, SetVertexTarget,
        IndexTarget, SetIndexTarget, MethodCount };
    MethodContract methods[MethodCount]{
        {"buffer.count", {"UnityEngine.CoreModule.dll","UnityEngine","GraphicsBuffer","get_count",nullptr,"System.Int32",0}},
        {"buffer.stride", {"UnityEngine.CoreModule.dll","UnityEngine","GraphicsBuffer","get_stride",nullptr,"System.Int32",0}},
        {"buffer.target", {"UnityEngine.CoreModule.dll","UnityEngine","GraphicsBuffer","get_target",nullptr,"UnityEngine.GraphicsBuffer.Target",0}},
        {"buffer.ctor", {"UnityEngine.CoreModule.dll","UnityEngine","GraphicsBuffer",".ctor","UnityEngine.GraphicsBuffer.Target|System.Int32|System.Int32","System.Void",3}},
        {"buffer.copy", {"UnityEngine.CoreModule.dll","UnityEngine","Graphics","CopyBuffer","UnityEngine.GraphicsBuffer|UnityEngine.GraphicsBuffer","System.Void",2}},
        {"buffer.read", {"UnityEngine.CoreModule.dll","UnityEngine","GraphicsBuffer","InternalGetData","System.Array|System.Int32|System.Int32|System.Int32|System.Int32","System.Void",5}},
        {"buffer.dispose", {"UnityEngine.CoreModule.dll","UnityEngine","GraphicsBuffer","Dispose",nullptr,"System.Void",0}},
        {"mesh.vertex_buffer", {"UnityEngine.CoreModule.dll","UnityEngine","Mesh","GetVertexBuffer","System.Int32","UnityEngine.GraphicsBuffer",1}},
        {"mesh.index_buffer", {"UnityEngine.CoreModule.dll","UnityEngine","Mesh","GetIndexBuffer",nullptr,"UnityEngine.GraphicsBuffer",0}},
        {"mesh.vertex_target", {"UnityEngine.CoreModule.dll","UnityEngine","Mesh","get_vertexBufferTarget",nullptr,"UnityEngine.GraphicsBuffer.Target",0}},
        {"mesh.set_vertex_target", {"UnityEngine.CoreModule.dll","UnityEngine","Mesh","set_vertexBufferTarget","UnityEngine.GraphicsBuffer.Target","System.Void",1}},
        {"mesh.index_target", {"UnityEngine.CoreModule.dll","UnityEngine","Mesh","get_indexBufferTarget",nullptr,"UnityEngine.GraphicsBuffer.Target",0}},
        {"mesh.set_index_target", {"UnityEngine.CoreModule.dll","UnityEngine","Mesh","set_indexBufferTarget","UnityEngine.GraphicsBuffer.Target","System.Void",1}}
    };
    BE_ResolvedClassV1 buffer_class{}, byte_class{};
    void* (*array_new)(const void*, size_t) = nullptr;
    size_t (*array_header)() = nullptr;
    uint32_t (*array_bytes)(void*) = nullptr;
    size_t header = 0;
    bool attempted = false, ready = false;
    unsigned reports = 0;
    struct Pending { FpRoot root; bool mesh = false; unsigned attempts = 0; };
    Pending pending[3];

    bool DisposeObject(void* object, bool mesh) {
        if (!object) return true;
        if (!mesh) return InvokeVoid(&methods[Dispose],object,nullptr);
        void* args[]{object};
        return InvokeVoid(Contract("unity.object.destroy"),nullptr,args);
    }
    bool Release(FpRoot& root, bool mesh = false) {
        if (!root.object) return true;
        if (DisposeObject(root.object,mesh)) { root.Reset(); return true; }
        for (auto& entry : pending) if (!entry.root.object) {
            entry.root=std::move(root); entry.mesh=mesh; entry.attempts=1;
            return Fail("disposal deferred; new captures suspended");
        }
        // At most a capture mesh and its two buffers can exist at once. New
        // captures are rejected while any cleanup is pending.
        return Fail("disposal capacity invariant");
    }
    bool Drain() {
        bool clean=true;
        for (auto& entry : pending) if (entry.root.object) {
            if (entry.attempts < 3) {
                ++entry.attempts;
                if (DisposeObject(entry.root.object,entry.mesh)) entry.root.Reset();
            }
            if (entry.root.object) clean=false;
        }
        return clean;
    }
    void Close() {
        // No later capture is guaranteed to arrive after a deferred dispose.
        // Retry within the same bounded budget before the host API goes away.
        for (unsigned attempt = 0; attempt < 3 && !Drain(); ++attempt) {}
        for (auto& entry : pending) if (entry.root.object) {
            Fail("shutdown disposal exhausted; releasing GC root for finalizer");
            entry.root.Reset();
        }
    }
    struct Lease {
        FpSynchronousReadback& owner;
        FpRoot& root;
        bool mesh = false;
        ~Lease() { owner.Release(root,mesh); }
    };

    bool Fail(const char* stage) {
        if (reports++ < 8) Log(std::string("First person synchronous readback unavailable: ") + stage);
        return false;
    }
    void ProbeNativeCandidates(HMODULE game) {
        using Resolve = void*(*)(const char*);
        const auto resolve=reinterpret_cast<Resolve>(GetProcAddress(game,"il2cpp_resolve_icall"));
        if (!resolve) { Log("First person readback icall probe: resolver unavailable."); return; }
        const char* names[]{
            "UnityEngine.Mesh::GetIndexBufferImpl",
            "UnityEngine.Mesh::get_indexBufferTarget",
            "UnityEngine.Mesh::set_indexBufferTarget",
            "UnityEngine.GraphicsBuffer::InternalGetData"
        };
        for (const char* name:names) {
            void* pointer=FpValue<void*>(nullptr,resolve,name);
            const char* scope="unverified";
#if defined(_WIN32)
            if (pointer) {
                MEMORY_BASIC_INFORMATION info{};
                const bool mapped=VirtualQuery(pointer,&info,sizeof(info))==sizeof(info);
                const DWORD protection=info.Protect&0xffu;
                const bool executable=mapped&&info.State==MEM_COMMIT&&
                    !(info.Protect&PAGE_GUARD)&&
                    (protection==PAGE_EXECUTE || protection==PAGE_EXECUTE_READ ||
                     protection==PAGE_EXECUTE_READWRITE || protection==PAGE_EXECUTE_WRITECOPY);
                const bool known=mapped&&(info.AllocationBase==game ||
                    info.AllocationBase==GetModuleHandleW(L"UnityPlayer.dll"));
                scope=executable&&known?"known executable module":"outside verified executable module";
            }
#endif
            Log(std::string("First person readback icall probe: ")+name+
                (pointer?" resolved, ":" unavailable, ")+scope+"; no invocation.");
        }
    }
    bool Init() {
        if (attempted) return ready;
        attempted = true;
        if (!g_host || !g_host->resolve_method || !g_host->resolve_class || !g_host->object_new ||
            !g_host->gchandle_new || !g_host->gchandle_free) return Fail("host capabilities");
        bool valid = true;
        for (auto& method : methods) {
            BE_ResolvedMethodV1 resolved{};
            if (g_host->resolve_method(g_host->context, &method.descriptor, &resolved) != BE_Result_Ok || !resolved.method_info) {
                Fail(method.key); valid = false;
            } else { method.method_info = resolved.method_info; method.resolved = true; }
        }
        auto game = GetModuleHandleW(L"GameAssembly.dll");
        if (!game) return Fail("IL2CPP module");
        if (!valid) ProbeNativeCandidates(game);
        array_new = reinterpret_cast<decltype(array_new)>(GetProcAddress(game, "il2cpp_array_new"));
        array_header = reinterpret_cast<decltype(array_header)>(GetProcAddress(game, "il2cpp_array_object_header_size"));
        array_bytes = reinterpret_cast<decltype(array_bytes)>(GetProcAddress(game, "il2cpp_array_get_byte_length"));
        if (!array_new || !array_header || !array_bytes) return Fail("array exports");
        header = FpValue<size_t>(0, array_header);
        if (header < 16 || header > 128) return Fail("array header");
        if (g_host->resolve_class(g_host->context,"UnityEngine.CoreModule.dll","UnityEngine","GraphicsBuffer",&buffer_class) != BE_Result_Ok ||
            g_host->resolve_class(g_host->context,"mscorlib.dll","System","Byte",&byte_class) != BE_Result_Ok)
            return Fail("managed classes");
        ready = valid && buffer_class.class_info && byte_class.class_info;
        return ready;
    }
    bool ReadBuffer(void* buffer, size_t expected, std::vector<uint8_t>& bytes) {
        int count = 0, stride = 0, target = 0;
        if (!GetValue(&methods[Count],buffer,count) || !GetValue(&methods[Stride],buffer,stride) ||
            !GetValue(&methods[Target],buffer,target) || !(target & 4)) return Fail("source CopySource contract");
        const auto plan = BetterEndfieldNext::FirstPerson::PlanReadback(count,stride,expected);
        if (plan.error != BetterEndfieldNext::FirstPerson::ReadbackError::None) return Fail("source byte budget");
        FpRoot staging(g_host->object_new(g_host->context,buffer_class.class_info));
        Lease staging_lease{*this,staging};
        if (!staging) return Fail("staging root");
        int destination = 8;
        void* constructor[]{&destination,&count,&stride};
        bool ok = InvokeVoid(&methods[Construct],staging.object,constructor);
        if (ok) {
            void* copy[]{buffer,staging.object};
            ok = InvokeVoid(&methods[Copy],nullptr,copy);
        }
        if (ok) {
            FpRoot array(FpValue<void*>(nullptr,array_new,byte_class.class_info,expected));
            ok = array && FpValue<uint32_t>(0,array_bytes,array.object) == expected;
            if (ok) {
                int zero = 0, length = static_cast<int>(expected), element_size = 1;
                void* read[]{array.object,&zero,&zero,&length,&element_size};
                ok = InvokeVoid(&methods[Read],staging.object,read);
                if (ok) {
                    bytes.resize(expected);
                    ok = FpCopy(bytes.data(),static_cast<uint8_t*>(array.object)+header,expected);
                }
            }
        }
        // Also dispose an allocated object when its constructor raised after
        // acquiring a native buffer. Unity Dispose accepts a null native handle.
        const bool disposed = Release(staging);
        if (!ok || !disposed) { bytes.clear(); return Fail("copy/read/dispose"); }
        return true;
    }
    bool ReadMesh(void* mesh, int stream, bool index, size_t expected, std::vector<uint8_t>& bytes) {
        if (!Init() || !Drain()) return false;
        auto& get_target = methods[index ? IndexTarget : VertexTarget];
        auto& set_target = methods[index ? SetIndexTarget : SetVertexTarget];
        int original = 0;
        if (!GetValue(&get_target,mesh,original)) return Fail("mesh target read");
        int requested = original | 4;
        void* target_args[]{&requested};
        const bool changed = requested != original;
        // mesh is an unassigned temporary capture clone. A setter failure
        // cannot mutate the original renderer mesh or lose its target flags.
        if (changed && !InvokeVoid(&set_target,mesh,target_args)) return Fail("mesh CopySource request");
        void* buffer_args[]{&stream};
        FpRoot buffer(Invoke(&methods[index ? IndexBuffer : VertexBuffer],mesh,index ? nullptr : buffer_args));
        Lease buffer_lease{*this,buffer};
        bool ok = buffer && ReadBuffer(buffer.object,expected,bytes);
        if (buffer.object) ok = Release(buffer) && ok;
        if (!ok) { bytes.clear(); return Fail("mesh buffer acquisition/restoration"); }
        return true;
    }
};
FpSynchronousReadback g_fp_sync_readback;
