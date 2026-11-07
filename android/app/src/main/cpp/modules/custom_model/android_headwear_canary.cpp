#include "android_mesh_builder.h"
#include "mesh_skin_metadata_adapter.h"
#include "mesh_layout_probe.h"
#include "core/log.h"
#include "first_person_headwear_fixture.h"

#include <array>
#include <cmath>
#include <cstring>
#include <cstdlib>
#include <fstream>
#include <iterator>
#include <stdexcept>
#include <vector>

namespace betterendfieldnext {
namespace {
constexpr char kLog[] = "headwear_canary";
Il2CppRuntime* canary_runtime = nullptr;
struct ActivePatch {
    void* renderer = nullptr;
    void* source = nullptr;
    void* copy = nullptr;
    void* prior_shadow = nullptr;
    uintptr_t renderer_native = 0;
    uintptr_t source_native = 0;
    uintptr_t copy_native = 0;
    bool prior_offscreen = false;
    bool assigned = false;
    unsigned restore_failures = 0;
    size_t fixture_bytes = 0;
    std::array<uint32_t, 4> roots{};
};
// The manifest has up to twelve primary mixed renderers across resident LODs.
// Sixteen leases permit those meshes while retaining the 64 MiB byte ceiling.
constexpr size_t kMaxActivePatches = 16;
constexpr size_t kMaxActiveFixtureBytes = 64u * 1024u * 1024u;
std::vector<ActivePatch> patches;

struct ManagedRoot {
    Il2CppRuntime& runtime;
    uint32_t handle = 0;
    ManagedRoot(Il2CppRuntime& owner, void* object) : runtime(owner) {
        if (object) handle = runtime.NewGcHandle(object, false);
    }
    ~ManagedRoot() { if (handle) runtime.FreeGcHandle(handle); }
    explicit operator bool() const { return handle != 0; }
};

void* Call(Il2CppRuntime& runtime, const ResolvedMethod& method, void* instance,
    void** args = nullptr) {
    if (!method.info) throw std::runtime_error("managed method unavailable");
    void* exception = nullptr;
    void* value = runtime.Invoke(method.info, instance, args, &exception);
    if (exception) throw std::runtime_error("managed invocation failed");
    return value;
}

template<class T> T Value(Il2CppRuntime& runtime, const ResolvedMethod& method,
    void* instance, void** args = nullptr) {
    void* raw = runtime.Unbox(Call(runtime, method, instance, args));
    if (!raw) throw std::runtime_error("managed value unavailable");
    T value{};
    std::memcpy(&value, raw, sizeof(value));
    return value;
}

ResolvedMethod Method(Il2CppRuntime& runtime, const char* type, const char* name,
    const char* args, const char* result, int count) {
    auto method = runtime.ResolveMethodExact("UnityEngine.CoreModule.dll", "UnityEngine",
        type, name, args, result, count);
    if (!method.info) throw std::runtime_error(std::string(type) + "." + name + " unavailable");
    return method;
}

uintptr_t NativePointer(Il2CppRuntime& runtime, const ResolvedField& cached, void* mesh) {
    void* boxed = runtime.ReadFieldObject(cached, mesh);
    void* raw = runtime.Unbox(boxed);
    uintptr_t pointer = 0;
    if (raw) std::memcpy(&pointer, raw, sizeof(pointer));
    return pointer;
}

std::vector<uint8_t> ReadFixture(const char* path) {
    if (!path || !*path) throw std::runtime_error("fixture path unavailable");
    std::ifstream file(path, std::ios::binary | std::ios::ate);
    if (!file) throw std::runtime_error("fixture file unavailable");
    const auto size = file.tellg();
    if (size <= 0 || size > 16 * 1024 * 1024) throw std::runtime_error("fixture file size invalid");
    std::vector<uint8_t> bytes(static_cast<size_t>(size));
    file.seekg(0);
    if (!file.read(reinterpret_cast<char*>(bytes.data()), size))
        throw std::runtime_error("fixture file short read");
    return bytes;
}

bool Restore(Il2CppRuntime& runtime, ActivePatch& active, bool retain = false) {
    if (!active.copy) return true;
    auto get_mesh = Method(runtime, "SkinnedMeshRenderer", "get_sharedMesh", "", "UnityEngine.Mesh", 0);
    auto set_mesh = Method(runtime, "SkinnedMeshRenderer", "set_sharedMesh", "UnityEngine.Mesh", "System.Void", 1);
    auto get_shadow = Method(runtime, "Renderer", "get_shadowProxyMesh", "", "UnityEngine.Mesh", 0);
    auto set_shadow = Method(runtime, "Renderer", "set_shadowProxyMesh", "UnityEngine.Mesh", "System.Void", 1);
    auto get_offscreen = Method(runtime, "SkinnedMeshRenderer", "get_updateWhenOffscreen", "", "System.Boolean", 0);
    auto set_offscreen = Method(runtime, "SkinnedMeshRenderer", "set_updateWhenOffscreen", "System.Boolean", "System.Void", 1);
    auto cached = runtime.ResolveField("UnityEngine.CoreModule.dll", "UnityEngine", "Object", "m_CachedPtr");
    if (!cached.info) throw std::runtime_error("renderer liveness field unavailable on restore");
    const bool renderer_alive = active.renderer_native &&
        NativePointer(runtime, cached, active.renderer) == active.renderer_native;
    if (renderer_alive && active.assigned) {
        void* current = Call(runtime, get_mesh, active.renderer);
        if (current == active.copy) {
            void* args[]{active.source};
            Call(runtime, set_mesh, active.renderer, args);
            if (Call(runtime, get_mesh, active.renderer) != active.source)
                throw std::runtime_error("source mesh restore rejected");
        }
        // Each field is its own lease. An external LOD replacement of
        // sharedMesh must not strand our shadow proxy/offscreen values.
        {
            if (Call(runtime, get_shadow, active.renderer) == active.source) {
                void* args[]{active.prior_shadow};
                Call(runtime, set_shadow, active.renderer, args);
                if (Call(runtime, get_shadow, active.renderer) != active.prior_shadow)
                    throw std::runtime_error("shadow proxy restore rejected");
            }
            if (!active.prior_offscreen && Value<bool>(runtime, get_offscreen, active.renderer)) {
                bool value = false;
                void* args[]{&value};
                Call(runtime, set_offscreen, active.renderer, args);
                if (Value<bool>(runtime, get_offscreen, active.renderer))
                    throw std::runtime_error("offscreen restore rejected");
            }
        }
    }
    if (retain && renderer_alive &&
        Call(runtime, get_mesh, active.renderer) == active.source &&
        NativePointer(runtime, cached, active.source) == active.source_native &&
        NativePointer(runtime, cached, active.copy) == active.copy_native) {
        active.assigned = false;
        active.restore_failures = 0;
        LogInfo(kLog, "source renderer restored; detached mesh retained for next entry");
        return true;
    }
    auto destroy = Method(runtime, "Object", "DestroyImmediate",
        "UnityEngine.Object|System.Boolean", "System.Void", 2);
    bool allow = false;
    void* args[]{active.copy, &allow};
    Call(runtime, destroy, nullptr, args);
    for (uint32_t handle : active.roots) if (handle) runtime.FreeGcHandle(handle);
    active = {};
    LogInfo(kLog, "source mesh, shadow proxy and offscreen state restored; detached mesh destroyed");
    return true;
}

void BindRetained(Il2CppRuntime& runtime, ActivePatch& active) {
    auto get_mesh = Method(runtime, "SkinnedMeshRenderer", "get_sharedMesh", "", "UnityEngine.Mesh", 0);
    auto set_mesh = Method(runtime, "SkinnedMeshRenderer", "set_sharedMesh", "UnityEngine.Mesh", "System.Void", 1);
    auto get_shadow = Method(runtime, "Renderer", "get_shadowProxyMesh", "", "UnityEngine.Mesh", 0);
    auto set_shadow = Method(runtime, "Renderer", "set_shadowProxyMesh", "UnityEngine.Mesh", "System.Void", 1);
    auto get_offscreen = Method(runtime, "SkinnedMeshRenderer", "get_updateWhenOffscreen", "", "System.Boolean", 0);
    auto set_offscreen = Method(runtime, "SkinnedMeshRenderer", "set_updateWhenOffscreen", "System.Boolean", "System.Void", 1);
    const auto cached = runtime.ResolveField("UnityEngine.CoreModule.dll", "UnityEngine", "Object", "m_CachedPtr");
    if (!cached.info || !active.renderer_native || !active.source_native || !active.copy_native ||
        NativePointer(runtime, cached, active.renderer) != active.renderer_native ||
        NativePointer(runtime, cached, active.source) != active.source_native ||
        NativePointer(runtime, cached, active.copy) != active.copy_native ||
        Call(runtime, get_mesh, active.renderer) != active.source)
        throw std::runtime_error("retained patch source or renderer changed");
    void* prior_shadow = Call(runtime, get_shadow, active.renderer);
    const bool prior_offscreen = Value<bool>(runtime, get_offscreen, active.renderer);
    const uint32_t shadow_root = prior_shadow ? runtime.NewGcHandle(prior_shadow, false) : 0;
    if (prior_shadow && !shadow_root)
        throw std::runtime_error("retained shadow proxy root unavailable");
    if (active.roots[3]) runtime.FreeGcHandle(active.roots[3]);
    active.roots[3] = shadow_root;
    active.prior_shadow = prior_shadow;
    active.prior_offscreen = prior_offscreen;
    active.assigned = true;
    void* shadow_args[]{active.source};
    Call(runtime, set_shadow, active.renderer, shadow_args);
    if (Call(runtime, get_shadow, active.renderer) != active.source)
        throw std::runtime_error("retained shadow proxy assignment rejected");
    bool on = true;
    void* offscreen_args[]{&on};
    Call(runtime, set_offscreen, active.renderer, offscreen_args);
    if (!Value<bool>(runtime, get_offscreen, active.renderer))
        throw std::runtime_error("retained offscreen assignment rejected");
    void* mesh_args[]{active.copy};
    Call(runtime, set_mesh, active.renderer, mesh_args);
    if (Call(runtime, get_mesh, active.renderer) != active.copy ||
        Call(runtime, get_shadow, active.renderer) != active.source)
        throw std::runtime_error("retained renderer assignment rejected");
}

bool Probe(Il2CppRuntime& runtime, void* renderer, void* source, bool bind) {
    for (auto it = patches.begin(); it != patches.end(); ++it) {
        if (it->renderer != renderer) continue;
        if (it->source == source) {
            if (it->assigned &&
                Call(runtime, Method(runtime, "SkinnedMeshRenderer", "get_sharedMesh", "",
                    "UnityEngine.Mesh", 0), renderer) == it->copy) return true;
            if (!it->assigned) {
                try {
                    BindRetained(runtime, *it);
                    LogInfo(kLog, "retained patch rebound without mesh reconstruction");
                    return true;
                } catch (...) {
                    Restore(runtime, *it);
                    patches.erase(it);
                    throw;
                }
            }
        }
        Restore(runtime, *it);
        patches.erase(it);
        break;
    }
    if (patches.size() >= kMaxActivePatches)
        throw std::runtime_error("headwear active patch budget exceeded");
    auto name = Method(runtime, "Object", "get_name", "", "System.String", 0);
    const std::string source_name = runtime.CopyString(Call(runtime, name, source));
    if (!BetterEndfieldNext::FirstPersonHeadwear::SafeMeshName(source_name))
        throw std::runtime_error("source mesh name unsafe for catalog lookup");
    const char* directory = std::getenv("BETTER_ENDFIELD_NEXT_HEADWEAR_DIRECTORY");
    if (!directory) throw std::runtime_error("headwear directory unavailable");
    const std::string path = std::string(directory) + "/" + source_name + ".behw";
    const auto bytes = ReadFixture(path.c_str());
    size_t active_bytes = 0;
    for (const auto& patch : patches) active_bytes += patch.fixture_bytes;
    if (bytes.size() > kMaxActiveFixtureBytes - active_bytes)
        throw std::runtime_error("headwear aggregate fixture budget exceeded");
    BetterEndfieldNext::FirstPersonHeadwear::Fixture fixture;
    std::string error;
    if (!BetterEndfieldNext::FirstPersonHeadwear::Parse(bytes, fixture, error))
        throw std::runtime_error(error);
    if (source_name != fixture.mesh_name)
        throw std::runtime_error("source mesh name differs");
    auto get_shadow = Method(runtime, "Renderer", "get_shadowProxyMesh", "", "UnityEngine.Mesh", 0);
    auto set_shadow = Method(runtime, "Renderer", "set_shadowProxyMesh", "UnityEngine.Mesh", "System.Void", 1);
    auto get_renderer_mesh = Method(runtime, "SkinnedMeshRenderer", "get_sharedMesh", "", "UnityEngine.Mesh", 0);
    auto set_renderer_mesh = Method(runtime, "SkinnedMeshRenderer", "set_sharedMesh", "UnityEngine.Mesh", "System.Void", 1);
    auto get_offscreen = Method(runtime, "SkinnedMeshRenderer", "get_updateWhenOffscreen", "", "System.Boolean", 0);
    auto set_offscreen = Method(runtime, "SkinnedMeshRenderer", "set_updateWhenOffscreen", "System.Boolean", "System.Void", 1);
    (void)set_shadow;
    (void)set_renderer_mesh;
    (void)set_offscreen;
    if (Call(runtime, get_renderer_mesh, renderer) != source)
        throw std::runtime_error("renderer source mesh changed during probe");
    void* prior_shadow = Call(runtime, get_shadow, renderer);
    const bool prior_offscreen = Value<bool>(runtime, get_offscreen, renderer);
    LogInfo(kLog, prior_shadow ? "shadow proxy getter ready; original proxy present" :
        "shadow proxy getter ready; no original proxy");
    LogInfo(kLog, prior_offscreen ? "updateWhenOffscreen originally true" :
        "updateWhenOffscreen originally false");
    auto vertices = Method(runtime, "Mesh", "get_vertexCount", "", "System.Int32", 0);
    auto index_format = Method(runtime, "Mesh", "get_indexFormat", "", "UnityEngine.Rendering.IndexFormat", 0);
    auto verify_draws = [&](void* mesh) {
        std::vector<std::array<int64_t,4>> descriptors;
        const int format=Value<int>(runtime,index_format,mesh);
        const uint32_t width=format==0 ? 2u : format==1 ? 4u : 0u;
        if (!AndroidReadMeshSubmeshes(mesh,descriptors) ||
            !BetterEndfieldNext::FirstPersonHeadwear::MatchesDrawMetadata(fixture,descriptors,width))
            throw std::runtime_error("mesh submesh metadata or index format differs");
    };
    auto attribute_count = Method(runtime, "Mesh", "get_vertexAttributeCount", "", "System.Int32", 0);
    auto attribute = Method(runtime, "Mesh", "GetVertexAttribute", "System.Int32",
        "UnityEngine.Rendering.VertexAttributeDescriptor", 1);
    if (Value<int>(runtime, vertices, source) != static_cast<int>(fixture.vertex_count) ||
        Value<int>(runtime, attribute_count, source) != static_cast<int>(fixture.attributes.size()))
        throw std::runtime_error("source mesh counts differ");
    verify_draws(source);
    LogInfo(kLog,("source MeshData draw contract PASS mesh="+source_name+
        " draws="+std::to_string(fixture.draws.size())+
        " index_element_size="+std::to_string(fixture.index_element_size)).c_str());
    for (int i = 0; i < static_cast<int>(fixture.attributes.size()); ++i) {
        void* args[]{&i};
        if (Value<std::array<int32_t, 4>>(runtime, attribute, source, args) !=
            fixture.attributes[static_cast<size_t>(i)])
            throw std::runtime_error("source vertex declaration differs");
    }
    std::vector<int32_t> strides;
    if (!AndroidReadMeshStrides(source, strides) ||
        strides != std::vector<int32_t>(fixture.strides.begin(), fixture.strides.end()))
        throw std::runtime_error("source stream strides differ");
    auto get_poses = Method(runtime, "Mesh", "get_bindposes", "", "UnityEngine.Matrix4x4[]", 0);
    auto set_poses = Method(runtime, "Mesh", "set_bindposes", "UnityEngine.Matrix4x4[]", "System.Void", 1);
    auto poses = Call(runtime, get_poses, source);
    ManagedRoot pose_root(runtime, poses);
    auto length = runtime.ResolveMethodExact("mscorlib.dll", "System", "Array", "get_Length", "",
        "System.Int32", 0);
    if (!pose_root || Value<int>(runtime, length, poses) != static_cast<int>(fixture.bone_count))
        throw std::runtime_error("source bindpose palette differs");
    const auto blend_shapes = Method(runtime, "Mesh", "get_blendShapeCount", "", "System.Int32", 0);
    if (Value<int>(runtime, blend_shapes, source) != 0)
        throw std::runtime_error("source blendshapes require a separate reconstruction contract");
    auto get_bones = Method(runtime, "SkinnedMeshRenderer", "get_bones", "", "UnityEngine.Transform[]", 0);
    auto bones = Call(runtime, get_bones, renderer);
    ManagedRoot bone_root(runtime, bones);
    if (!bone_root || Value<int>(runtime, length, bones) != static_cast<int>(fixture.bone_count))
        throw std::runtime_error("renderer bone palette count differs");
    const auto cached = runtime.ResolveField("UnityEngine.CoreModule.dll", "UnityEngine", "Object", "m_CachedPtr");
    if (!cached.info) throw std::runtime_error("native Mesh pointer field unavailable");
    MeshSkinMetadataAdapter skin(CachedLoadedUnityMeshLayout());
    uint32_t influences = 0;
    if (!skin.LayoutVerified() || !skin.Read(NativePointer(runtime, cached, source), influences))
        throw std::runtime_error("source skin metadata unavailable");
    if (fixture.strides[2] == 4 && influences != 1)
        throw std::runtime_error("rigid skin source must use exactly one influence");
    const auto destroy = Method(runtime, "Object", "DestroyImmediate",
        "UnityEngine.Object|System.Boolean", "System.Void", 2);
    const auto recalculate = Method(runtime, "Mesh", "RecalculateBounds", "", "System.Void", 0);
    const auto has_weights = Method(runtime, "Mesh", "HasBoneWeights", "", "System.Boolean", 0);
    const bool expected_has_weights = fixture.strides[2] == 4 ?
        Value<bool>(runtime, has_weights, source) : true;
    // Source game meshes have discarded CPU skin data. Cloning them enters
    // Unity's skin conversion with no CPU buffer (PJX110 crash 2026-10-01).
    // Construct owned readable storage from the validated offline fixture.
    const auto constructor = Method(runtime, "Mesh", ".ctor", "", "System.Void", 0);
    const auto upload = Method(runtime, "Mesh", "UploadMeshData", "System.Boolean", "System.Void", 1);
    const auto mesh_class = runtime.ResolveClass("UnityEngine.CoreModule.dll", "UnityEngine", "Mesh");
    if (!mesh_class.info) throw std::runtime_error("Mesh class unavailable");
    void* detached = runtime.NewObject(mesh_class.info);
    if (!detached || detached == source)
        throw std::runtime_error("independent Mesh allocation unavailable");
    ManagedRoot detached_root(runtime, detached);
    if (!detached_root) throw std::runtime_error("detached Mesh allocation failed");
    bool created = true;
    try {
        Call(runtime, constructor, detached);
        const uintptr_t pointer = NativePointer(runtime, cached, detached);
        uint32_t cloned_influences = 0;
        if (!pointer || pointer == NativePointer(runtime, cached, source) ||
            !skin.Write(pointer, influences, true))
            throw std::runtime_error("owned Mesh skin initialization rejected");
        BetterEndfieldNext::CustomModel::BemComponent component;
        component.info.component_id = 0; // Internal mesh submission, no BEM component identity.
        component.info.original_index_count = fixture.index_count;
        component.info.vertex_count = fixture.vertex_count;
        component.info.index_count = fixture.index_count;
        component.info.max_bone = fixture.bone_count - 1;
        component.info.stream_count = 3;
        component.info.stride0 = fixture.strides[0];
        component.info.stride1 = fixture.strides[1];
        component.info.stride2 = fixture.strides[2];
        component.info.index_element_size = fixture.index_element_size;
        // Preserve source submesh order; sharedMaterials stays on the Renderer.
        for (const auto& draw : fixture.draws)
            component.draws.push_back({draw.first_index, draw.index_count, 0, 0, 0, 0, 0});
        component.attributes = std::move(fixture.attributes);
        component.streams = std::move(fixture.streams);
        component.indices = std::move(fixture.indices);
        if (!AndroidSubmitMesh(detached, component))
            throw std::runtime_error("detached MeshData submit/readback rejected");
        if (!skin.Read(pointer, cloned_influences) || cloned_influences != influences)
            throw std::runtime_error("submitted Mesh skin metadata differs");
        void* pose_args[]{poses};
        Call(runtime, set_poses, detached, pose_args);
        Call(runtime, recalculate, detached);
        if (fixture.hidden_triangles == fixture.index_count / 3) {
            // Fully degenerate geometry may produce empty calculated bounds.
            // Preserve the source culling bounds for its retained shadow mesh.
            auto get_bounds = Method(runtime, "Mesh", "get_bounds", "", "UnityEngine.Bounds", 0);
            auto set_bounds = Method(runtime, "Mesh", "set_bounds", "UnityEngine.Bounds", "System.Void", 1);
            auto bounds = Value<std::array<float, 6>>(runtime, get_bounds, source);
            if (!std::all_of(bounds.begin(), bounds.end(), [](float x) { return std::isfinite(x); }) ||
                bounds[3] <= 0 || bounds[4] <= 0 || bounds[5] <= 0)
                throw std::runtime_error("source whole-head bounds invalid");
            void* bounds_args[]{bounds.data()};
            Call(runtime, set_bounds, detached, bounds_args);
        }
        if (Value<bool>(runtime, has_weights, detached) != expected_has_weights ||
            Value<int>(runtime, length, Call(runtime, get_poses, detached)) !=
                static_cast<int>(fixture.bone_count))
            throw std::runtime_error("detached skin or bindposes unavailable");
        // Match the common CustomModel builder: CPU byte verification does
        // not initialize/verify GPU skin buffers. Upload before publishing.
        LogInfo(kLog, "owned Mesh CPU validation PASS; uploading GPU data with CPU storage retained");
        bool discard_cpu = false;
        void* upload_args[]{&discard_cpu};
        Call(runtime, upload, detached, upload_args);
        if (!skin.Read(pointer, cloned_influences) || cloned_influences != influences ||
            Value<bool>(runtime, has_weights, detached) != expected_has_weights ||
            Value<int>(runtime, vertices, detached) != static_cast<int>(fixture.vertex_count) ||
            Value<int>(runtime, length, Call(runtime, get_poses, detached)) !=
                static_cast<int>(fixture.bone_count))
            throw std::runtime_error("uploaded Mesh skin or geometry differs");
        verify_draws(detached);
        for (int i = 0; i < static_cast<int>(component.attributes.size()); ++i) {
            void* args[]{&i};
            if (Value<std::array<int32_t, 4>>(runtime, attribute, detached, args) !=
                component.attributes[static_cast<size_t>(i)])
                throw std::runtime_error("uploaded Mesh vertex declaration differs");
        }
        std::vector<int32_t> uploaded_strides;
        if (!AndroidReadMeshStrides(detached, uploaded_strides) || uploaded_strides != strides)
            throw std::runtime_error("uploaded Mesh strides differ");
        void* uploaded_poses = Call(runtime, get_poses, detached);
        ManagedRoot uploaded_pose_root(runtime, uploaded_poses);
        auto item = runtime.ResolveMethodExact("mscorlib.dll", "System", "Array", "GetValue",
            "System.Int32", "System.Object", 1);
        if (!uploaded_pose_root || !item.info)
            throw std::runtime_error("uploaded bindpose readback unavailable");
        for (int i = 0; i < static_cast<int>(fixture.bone_count); ++i) {
            void* args[]{&i};
            if (Value<std::array<float, 16>>(runtime, item, poses, args) !=
                Value<std::array<float, 16>>(runtime, item, uploaded_poses, args))
                throw std::runtime_error("uploaded bindpose contents differ");
        }
        auto bounds_method = Method(runtime, "Mesh", "get_bounds", "", "UnityEngine.Bounds", 0);
        const auto bounds = Value<std::array<float, 6>>(runtime, bounds_method, detached);
        if (!std::all_of(bounds.begin(), bounds.end(), [](float value) { return std::isfinite(value); }) ||
            bounds[3] <= 0 || bounds[4] <= 0 || bounds[5] <= 0)
            throw std::runtime_error("uploaded Mesh bounds invalid");
        const std::string bound_log = "uploaded Mesh bounds center=" + std::to_string(bounds[0]) + "," +
            std::to_string(bounds[1]) + "," + std::to_string(bounds[2]) + " extents=" +
            std::to_string(bounds[3]) + "," + std::to_string(bounds[4]) + "," + std::to_string(bounds[5]);
        LogInfo(kLog, bound_log.c_str());
        LogInfo(kLog, "owned Mesh UploadMeshData(false) returned; post-upload skin/counts PASS");
        const std::string result = "detached Mesh PASS " + fixture.mesh_name +
            " hidden_triangles=" + std::to_string(fixture.hidden_triangles);
        LogInfo(kLog, result.c_str());
    } catch (...) {
        if (created) {
            bool allow = false;
            void* args[]{detached, &allow};
            void* exception = nullptr;
            runtime.Invoke(destroy.info, nullptr, args, &exception);
        }
        throw;
    }
    if (bind) {
        ActivePatch pending;
        pending.renderer = renderer;
        pending.source = source;
        pending.copy = detached;
        pending.renderer_native = NativePointer(runtime, cached, renderer);
        pending.source_native = NativePointer(runtime, cached, source);
        pending.copy_native = NativePointer(runtime, cached, detached);
        if (!pending.renderer_native || !pending.source_native || !pending.copy_native) {
            bool allow = false;
            void* args[]{detached, &allow};
            Call(runtime, destroy, nullptr, args);
            throw std::runtime_error("patch native identity unavailable");
        }
        pending.assigned = true;
        pending.prior_shadow = prior_shadow;
        pending.prior_offscreen = prior_offscreen;
        pending.fixture_bytes = bytes.size();
        const std::array<void*, 4> objects{renderer, source, detached, prior_shadow};
        for (size_t i = 0; i < objects.size(); ++i)
            if (objects[i]) pending.roots[i] = runtime.NewGcHandle(objects[i], false);
        if (!pending.roots[0] || !pending.roots[1] || !pending.roots[2] ||
            (prior_shadow && !pending.roots[3])) {
            for (uint32_t handle : pending.roots) if (handle) runtime.FreeGcHandle(handle);
            bool allow = false;
            void* args[]{detached, &allow};
            Call(runtime, destroy, nullptr, args);
            throw std::runtime_error("persistent patch roots unavailable");
        }
        patches.push_back(pending);
        auto& active = patches.back();
        try {
            void* shadow_args[]{source};
            Call(runtime, set_shadow, renderer, shadow_args);
            if (Call(runtime, get_shadow, renderer) != source)
                throw std::runtime_error("source shadow proxy assignment rejected");
            bool on = true;
            void* offscreen_args[]{&on};
            Call(runtime, set_offscreen, renderer, offscreen_args);
            if (!Value<bool>(runtime, get_offscreen, renderer))
                throw std::runtime_error("offscreen assignment rejected");
            void* mesh_args[]{detached};
            Call(runtime, set_renderer_mesh, renderer, mesh_args);
            if (Call(runtime, get_renderer_mesh, renderer) != detached ||
                Call(runtime, get_shadow, renderer) != source)
                throw std::runtime_error("detached renderer assignment rejected");
        } catch (...) {
            try { Restore(runtime, active); patches.pop_back(); }
            catch (const std::exception& error) { LogError(kLog, error.what()); }
            throw;
        }
        const std::string result = "patch assigned " + fixture.mesh_name +
            "; original source mesh retained as shadow proxy";
        LogInfo(kLog, result.c_str());
    } else {
        bool allow = false;
        void* args[]{detached, &allow};
        Call(runtime, destroy, nullptr, args);
        LogInfo(kLog, "detached Mesh destroyed; original Renderer unchanged");
    }
    return true;
}
}

void ConfigureHeadwearCanary(Il2CppRuntime& runtime) { canary_runtime = &runtime; }
void AndroidWarmHeadwearLayout() {
    if (canary_runtime && std::getenv("BETTER_ENDFIELD_NEXT_HEADWEAR_DIRECTORY"))
        WarmLoadedUnityMeshLayout();
}

bool AndroidHeadwearFixtureAvailable(void* renderer, void* source_mesh) {
    const char* directory = std::getenv("BETTER_ENDFIELD_NEXT_HEADWEAR_DIRECTORY");
    if (!canary_runtime || !renderer || !source_mesh || !directory) return false;
    try {
        auto name_method = Method(*canary_runtime, "Object", "get_name", "", "System.String", 0);
        const auto name = canary_runtime->CopyString(Call(*canary_runtime, name_method, source_mesh));
        if (!BetterEndfieldNext::FirstPersonHeadwear::SafeMeshName(name)) return false;
        // Generated candidates are the primary world Renderer, not an extra
        // shadowProxyMobile component sharing that same source mesh.
        if (!BetterEndfieldNext::FirstPersonHeadwear::PrimaryRendererName(
            canary_runtime->CopyString(Call(*canary_runtime, name_method, renderer)), name)) return false;
        std::ifstream file(std::string(directory) + "/" + name + ".behw", std::ios::binary | std::ios::ate);
        return file && file.tellg() > 0 && file.tellg() <= 16 * 1024 * 1024;
    } catch (const std::exception& error) {
        LogError(kLog, error.what());
        return false;
    }
}

void AndroidPruneHeadwearFixtures(void* const* renderers, size_t count) {
    if (!canary_runtime) return;
    for (auto it = patches.begin(); it != patches.end();) {
        bool present = false;
        for (size_t i = 0; i < count; ++i) if (renderers[i] == it->renderer) present = true;
        if (present) {
            try {
                const auto cached = canary_runtime->ResolveField("UnityEngine.CoreModule.dll",
                    "UnityEngine", "Object", "m_CachedPtr");
                if (!cached.info) throw std::runtime_error("prune liveness field unavailable");
                if (NativePointer(*canary_runtime, cached, it->renderer) == it->renderer_native &&
                    NativePointer(*canary_runtime, cached, it->source) == it->source_native &&
                    NativePointer(*canary_runtime, cached, it->copy) == it->copy_native &&
                    Call(*canary_runtime, Method(*canary_runtime, "SkinnedMeshRenderer", "get_sharedMesh",
                        "", "UnityEngine.Mesh", 0), it->renderer) ==
                        (it->assigned ? it->copy : it->source)) {
                    ++it; continue;
                }
            } catch (const std::exception& error) {
                LogError(kLog, error.what());
                ++it; continue;
            }
        }
        if (it->restore_failures >= 3) { ++it; continue; }
        try { Restore(*canary_runtime, *it); it = patches.erase(it); }
        catch (const std::exception& error) {
            ++it->restore_failures;
            LogError(kLog, error.what());
            ++it;
        }
    }
}

bool AndroidProbeHeadwearFixture(void* renderer, void* source_mesh) {
    if (!canary_runtime || !renderer || !source_mesh) return false;
    try { return Probe(*canary_runtime, renderer, source_mesh, true); }
    catch (const std::exception& error) {
        LogError(kLog, error.what());
        return false;
    }
}
bool AndroidHeadwearFixtureOwns(void* renderer, void* mesh) {
    return std::any_of(patches.begin(), patches.end(), [&](const auto& patch) {
        return patch.assigned && patch.renderer == renderer && patch.copy && patch.copy == mesh;
    });
}
bool AndroidRestoreHeadwearFixture() {
    if (!canary_runtime) return true;
    for (auto it = patches.begin(); it != patches.end();) {
        if (!it->assigned) { ++it; continue; }
        if (it->restore_failures >= 3) { ++it; continue; }
        try {
            Restore(*canary_runtime, *it, true);
            if (it->copy) ++it;
            else it = patches.erase(it);
        }
        catch (const std::exception& error) {
            ++it->restore_failures;
            LogError(kLog, error.what());
            ++it;
        }
    }
    return std::none_of(patches.begin(), patches.end(),
        [](const ActivePatch& patch) { return patch.assigned; });
}
void AndroidDiscardHeadwearFixtures() {
    if (!canary_runtime) return;
    for (auto it = patches.begin(); it != patches.end();) {
        if (it->assigned) { ++it; continue; }
        try { Restore(*canary_runtime, *it); it = patches.erase(it); }
        catch (const std::exception& error) {
            LogError(kLog, error.what());
            ++it;
        }
    }
}
}
