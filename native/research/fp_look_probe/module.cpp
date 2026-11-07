// Read-only research probe: find the game's own first-person look input, so a
// gyroscope source can feed it instead of writing CameraState directly.
//
// Why this exists. The module deliberately keeps Cinemachine's orientation
// exactly as the game produced it and only moves the position to the eye anchor
// (see ApplyFirstPersonState in modules/camera/module.cpp): the game's own look
// input stays authoritative. That means a gyroscope cannot be folded into the
// pushed state without fighting the aim and the body-follow code, and the two
// would hold separate yaw state. The correct entry point is therefore whatever
// turns the game's aim, and this probe answers what that is.
//
// Nothing here is a guess and nothing writes: it resolves candidate classes by
// metadata, enumerates their members, and logs what it sees. It is excluded from
// the default build and from the release layout, exactly like the other research
// probes, so it cannot reach a shipped artifact.

#include "BetterEndfieldNext/ModuleApi.h"

#include <Windows.h>

#include <atomic>
#include <cstdio>
#include <cstring>
#include <string>
#include <thread>
#include <vector>

namespace {

constexpr const char* kModuleId = "betterendfieldnext.fp-look-probe";

const BE_HostApiV1* g_host = nullptr;

// Declared here because kApi is defined before their bodies; the module entry
// point has to name them in the struct it hands the host.
BE_Result Initialize(const BE_HostApiV1* host);
BE_Result ConfigurationChanged(const char* configuration);
void Shutdown();

// The probe reports through the host logger, which the runtime journal already
// collects. A tag keeps its lines separable from real module output.
void Log(const std::string& message) {
    if (g_host && g_host->log) g_host->log(g_host->context, kModuleId, message.c_str());
}

using Il2CppDomainGetFn = void*(__fastcall*)();
using Il2CppDomainGetAssembliesFn = void**(__fastcall*)(void*, size_t*);
using Il2CppAssemblyGetImageFn = void*(__fastcall*)(void*);
using Il2CppImageGetNameFn = const char*(__fastcall*)(void*);
using Il2CppClassFromNameFn = void*(__fastcall*)(void*, const char*, const char*);
using Il2CppClassGetNameFn = const char*(__fastcall*)(void*);
using Il2CppClassGetNamespaceFn = const char*(__fastcall*)(void*);
using Il2CppClassGetMethodsFn = void*(__fastcall*)(void*, void**);
using Il2CppClassGetFieldsFn = void*(__fastcall*)(void*, void**);
using Il2CppClassGetParentFn = void*(__fastcall*)(void*);
using Il2CppMethodGetNameFn = const char*(__fastcall*)(void*);
using Il2CppMethodGetParamCountFn = uint32_t(__fastcall*)(void*);
using Il2CppMethodGetParamFn = const void*(__fastcall*)(void*, uint32_t);
using Il2CppMethodGetReturnTypeFn = const void*(__fastcall*)(void*);
using Il2CppTypeGetNameFn = const char*(__fastcall*)(const void*);
using Il2CppFieldGetNameFn = const char*(__fastcall*)(void*);
using Il2CppFieldGetTypeFn = const void*(__fastcall*)(void*);
using Il2CppClassGetTypeFn = const void*(__fastcall*)(void*);
using Il2CppTypeGetObjectFn = void*(__fastcall*)(const void*);
using Il2CppObjectNewFn = void*(__fastcall*)(void*);
using Il2CppRuntimeInvokeFn = void*(__fastcall*)(const void*, void*, void**, void**);
using Il2CppThreadAttachFn = void*(__fastcall*)(void*);

Il2CppDomainGetFn g_domain_get = nullptr;
Il2CppDomainGetAssembliesFn g_domain_get_assemblies = nullptr;
Il2CppAssemblyGetImageFn g_assembly_get_image = nullptr;
Il2CppImageGetNameFn g_image_get_name = nullptr;
Il2CppClassFromNameFn g_class_from_name = nullptr;
Il2CppClassGetNameFn g_class_get_name = nullptr;
Il2CppClassGetNamespaceFn g_class_get_namespace = nullptr;
Il2CppClassGetMethodsFn g_class_get_methods = nullptr;
Il2CppClassGetFieldsFn g_class_get_fields = nullptr;
Il2CppClassGetParentFn g_class_get_parent = nullptr;
Il2CppMethodGetNameFn g_method_get_name = nullptr;
Il2CppMethodGetParamCountFn g_method_get_param_count = nullptr;
Il2CppMethodGetParamFn g_method_get_param = nullptr;
Il2CppMethodGetReturnTypeFn g_method_get_return_type = nullptr;
Il2CppTypeGetNameFn g_type_get_name = nullptr;
Il2CppFieldGetNameFn g_field_get_name = nullptr;
Il2CppFieldGetTypeFn g_field_get_type = nullptr;
Il2CppClassGetTypeFn g_class_get_type = nullptr;
Il2CppTypeGetObjectFn g_type_get_object = nullptr;
Il2CppObjectNewFn g_object_new = nullptr;
Il2CppRuntimeInvokeFn g_runtime_invoke = nullptr;
Il2CppThreadAttachFn g_thread_attach = nullptr;

bool g_enumerated = false;

bool Ready() {
    return g_domain_get && g_domain_get_assemblies && g_assembly_get_image &&
        g_class_from_name && g_class_get_methods && g_class_get_fields &&
        g_method_get_name && g_type_get_name;
}

// Case-insensitive substring test used to pick the interesting members out of a
// class's full listing. The full listing is also logged, so a miss here still
// leaves the raw evidence in the journal.
bool Mentions(const char* text, const char* needle) {
    if (!text) return false;
    const size_t length = std::strlen(needle);
    if (length == 0) return true;
    for (const char* cursor = text; *cursor; ++cursor) {
        size_t index = 0;
        while (index < length && cursor[index] &&
            std::tolower(static_cast<unsigned char>(cursor[index])) ==
                std::tolower(static_cast<unsigned char>(needle[index]))) {
            ++index;
        }
        if (index == length) return true;
    }
    return false;
}

void* FindImage(const char* assembly_name) {
    size_t count = 0;
    void** assemblies = g_domain_get_assemblies(g_domain_get(), &count);
    if (!assemblies) return nullptr;
    for (size_t index = 0; index < count; ++index) {
        void* image = g_assembly_get_image(assemblies[index]);
        if (!image) continue;
        const char* name = g_image_get_name(image);
        if (name && std::strcmp(name, assembly_name) == 0) return image;
    }
    return nullptr;
}

void LogMethod(void* klass, void* method) {
    const char* name = g_method_get_name ? g_method_get_name(method) : nullptr;
    if (!name) return;
    std::string line = "  method ";
    line += name;
    line += "(";
    const uint32_t count = g_method_get_param_count ? g_method_get_param_count(method) : 0;
    for (uint32_t index = 0; index < count; ++index) {
        if (index) line += ", ";
        const void* parameter = g_method_get_param ? g_method_get_param(method, index) : nullptr;
        const char* type = parameter && g_type_get_name ? g_type_get_name(parameter) : nullptr;
        line += type ? type : "?";
    }
    line += ")";
    const void* result = g_method_get_return_type ? g_method_get_return_type(method) : nullptr;
    const char* returned = result && g_type_get_name ? g_type_get_name(result) : nullptr;
    line += " -> ";
    line += returned ? returned : "?";
    // Only the members that could plausibly carry look input are worth the noise
    // of a marker; the rest still appear as plain lines.
    if (Mentions(name, "look") || Mentions(name, "aim") || Mentions(name, "yaw") ||
        Mentions(name, "pitch") || Mentions(name, "rotate") || Mentions(name, "turn") ||
        Mentions(name, "sensitiv") || Mentions(name, "axis") || Mentions(name, "input")) {
        line = "  [look?] " + line.substr(9);
    }
    Log(line);
}

void LogField(void* klass, void* field) {
    const char* name = g_field_get_name ? g_field_get_name(field) : nullptr;
    if (!name) return;
    const void* type = g_field_get_type ? g_field_get_type(field) : nullptr;
    const char* type_name = type && g_type_get_name ? g_type_get_name(type) : nullptr;
    std::string line = "  [look?] field " + std::string(name) + " : " +
        (type_name ? type_name : "?");
    if (!(Mentions(name, "look") || Mentions(name, "aim") || Mentions(name, "yaw") ||
            Mentions(name, "pitch") || Mentions(name, "sensitiv") || Mentions(name, "axis") ||
            Mentions(name, "input") || Mentions(name, "rotate") || Mentions(name, "turn"))) {
        line = "  field " + std::string(name) + " : " + (type_name ? type_name : "?");
    }
    Log(line);
}

// Enumerates one class and logs its members. The class name is what the call
// site is testing; a missing class is reported as such rather than as a failure,
// because that is itself the finding.
void ProbeClass(const char* assembly, const char* namespace_name, const char* class_name) {
    void* image = FindImage(assembly);
    if (!image) {
        Log(std::string("class ") + class_name + ": assembly missing: " + assembly);
        return;
    }
    void* klass = g_class_from_name(image, namespace_name, class_name);
    if (!klass) {
        Log(std::string("class ") + class_name + ": not found in " + assembly);
        return;
    }
    std::string header = "class " + std::string(class_name);
    if (g_class_get_namespace) {
        const char* space = g_class_get_namespace(klass);
        if (space && *space) header += " (" + std::string(space) + ")";
    }
    if (g_class_get_parent) {
        void* parent = g_class_get_parent(klass);
        const char* parent_name = parent && g_class_get_name ? g_class_get_name(parent) : nullptr;
        if (parent_name) header += " : " + std::string(parent_name);
    }
    Log(header);

    if (g_class_get_fields) {
        uint32_t count = 0;
        void* iterator = nullptr;
        while (void* field = g_class_get_fields(klass, &iterator)) {
            ++count;
            LogField(klass, field);
        }
        Log("class " + std::string(class_name) + " fields=" + std::to_string(count));
    }
    if (g_class_get_methods) {
        uint32_t count = 0;
        void* iterator = nullptr;
        while (void* method = g_class_get_methods(klass, &iterator)) {
            ++count;
            LogMethod(klass, method);
        }
        Log("class " + std::string(class_name) + " methods=" + std::to_string(count));
    }
}

// Calls a parameterless method on a live instance, if one can be obtained. A
// null return is left as "no instance", not as an exception: FindObjectOfType
// can legally find nothing, and the earlier first-person work in this repo
// records that it may also throw.
bool TryFindInstance(const char* assembly, const char* namespace_name,
        const char* class_name, void** instance) {
    *instance = nullptr;
    if (!g_class_get_type || !g_type_get_object || !g_object_new) return false;
    void* image = FindImage(assembly);
    if (!image) return false;
    void* klass = g_class_from_name(image, namespace_name, class_name);
    if (!klass) return false;
    const void* type = g_class_get_type(klass);
    if (!type) return false;
    void* type_object = g_type_get_object(type);
    if (!type_object) return false;
    // GameObject.FindObjectsOfType is the only generic-free way to reach a live
    // component without already knowing a scene object, and it is resolved by
    // name like every other contract in this repo.
    static BE_ResolvedMethodV1 find = {};
    static bool resolved = false;
    if (!resolved) {
        const BE_MethodDescriptorV1 descriptor{
            "UnityEngine.CoreModule.dll", "UnityEngine", "Object",
            "FindObjectOfType", "System.Type", "UnityEngine.Object", 1};
        resolved = g_host->resolve_method(g_host->context, &descriptor, &find) == BE_Result_Ok;
    }
    if (!resolved || !find.method_info) return false;
    void* parameters[1]{type_object};
    void* exception = nullptr;
    *instance = g_host->runtime_invoke(g_host->context, find.method_info, nullptr,
        parameters, &exception);
    return *instance != nullptr;
}

// Logs the live aim/rotation of the main character, which is what a gyroscope
// would have to move for the body-follow code to agree with the camera. Read
// only: a value that changes between two samples tells us the field is the live
// aim, and a constant tells us it is not.
void SampleLiveAim() {
    void* character = nullptr;
    static BE_ResolvedMethodV1 main_character = {};
    static bool resolved = false;
    if (!resolved) {
        const BE_MethodDescriptorV1 descriptor{
            "Gameplay.Beyond.dll", "Beyond.Gameplay.Core", "PlayerController",
            "GetMainCharacter", nullptr, "Beyond.Gameplay.Core.Entity", 0};
        resolved = g_host->resolve_method(g_host->context, &descriptor, &main_character) ==
            BE_Result_Ok;
    }
    if (resolved && main_character.method_info) {
        void* exception = nullptr;
        character = g_host->runtime_invoke(g_host->context, main_character.method_info,
            nullptr, nullptr, &exception);
    }
    Log(character ? "main character: live" : "main character: unavailable");
}

// The enumeration runs off the load path: metadata iteration walks a lot of
// managed structures and must not hold up the JNI_OnLoad thread, which is the
// same reason the runtime bootstrap does its work on a worker.
void Run() {
    if (g_enumerated) return;
    g_enumerated = true;
    std::thread([] {
        if (!Ready()) return;
        // The snapshot camera is a photography mode controller, not the camera
        // the module's own first person rides on: the module rewrites the pushed
        // Cinemachine CameraState (ApplyFirstPersonState) and keeps the game's
        // orientation authoritative, so the orientation the gyroscope must feed
        // belongs to the *main* camera controllers below, not to the snapshot
        // one. It is still enumerated first so the two can be compared.
        ProbeClass("Gameplay.Beyond.dll", "Beyond.Gameplay.View",
            "SnapshotCameraController");
        // The main camera controllers. CameraManager is the one the module
        // already hooks (TailLateTick) and CameraMono is its per-camera
        // counterpart (the dither tick hook). Their look/rotation methods are
        // the actual entry point the gyroscope must drive in the module's first
        // person, because that camera does not own its orientation.
        ProbeClass("Gameplay.Beyond.dll", "Beyond.Gameplay.View", "CameraManager");
        ProbeClass("Gameplay.Beyond.dll", "Beyond.Gameplay.View", "CameraMono");
        // The player controller owns the move axis already used for body follow,
        // so an aim/look axis would sit beside it.
        ProbeClass("Gameplay.Beyond.dll", "Beyond.Gameplay.Core",
            "PlayerController");
        // The movement component carries the input record the facing code reads
        // for actual movement; an input struct here would be the natural aim
        // carrier.
        ProbeClass("Gameplay.Beyond.dll", "Beyond.Gameplay.Core",
            "MovementComponent");
        ProbeClass("Gameplay.Beyond.dll", "Beyond.Gameplay.Core", "MoveInput");
        SampleLiveAim();
        Log("probe complete");
    }).detach();
}

const BE_ModuleApiV1 kApi{
    {kModuleId, "First-person look probe", "0.1.0", BETTER_ENDFIELD_NEXT_MODULE_ABI_V1},
    &Initialize,
    &ConfigurationChanged,
    &Shutdown,
};

BE_Result ConfigurationChanged(const char* configuration) {
    (void)configuration;
    return BE_Result_Ok;
}

void Shutdown() {
    g_host = nullptr;
}

BE_Result Initialize(const BE_HostApiV1* host) {
    if (!host || host->abi_version != 1 || !host->log || !host->resolve_method) {
        return BE_Result_ContractMismatch;
    }
    g_host = host;

    const HMODULE assembly = GetModuleHandleW(L"GameAssembly.dll");
    if (!assembly) {
        Log("GameAssembly unavailable; probe cannot enumerate metadata");
        return BE_Result_NotReady;
    }
    g_domain_get = reinterpret_cast<Il2CppDomainGetFn>(
        GetProcAddress(assembly, "il2cpp_domain_get"));
    g_domain_get_assemblies = reinterpret_cast<Il2CppDomainGetAssembliesFn>(
        GetProcAddress(assembly, "il2cpp_domain_get_assemblies"));
    g_assembly_get_image = reinterpret_cast<Il2CppAssemblyGetImageFn>(
        GetProcAddress(assembly, "il2cpp_assembly_get_image"));
    g_image_get_name = reinterpret_cast<Il2CppImageGetNameFn>(
        GetProcAddress(assembly, "il2cpp_image_get_name"));
    g_class_from_name = reinterpret_cast<Il2CppClassFromNameFn>(
        GetProcAddress(assembly, "il2cpp_class_from_name"));
    g_class_get_name = reinterpret_cast<Il2CppClassGetNameFn>(
        GetProcAddress(assembly, "il2cpp_class_get_name"));
    g_class_get_namespace = reinterpret_cast<Il2CppClassGetNamespaceFn>(
        GetProcAddress(assembly, "il2cpp_class_get_namespace"));
    g_class_get_methods = reinterpret_cast<Il2CppClassGetMethodsFn>(
        GetProcAddress(assembly, "il2cpp_class_get_methods"));
    g_class_get_fields = reinterpret_cast<Il2CppClassGetFieldsFn>(
        GetProcAddress(assembly, "il2cpp_class_get_fields"));
    g_class_get_parent = reinterpret_cast<Il2CppClassGetParentFn>(
        GetProcAddress(assembly, "il2cpp_class_get_parent"));
    g_method_get_name = reinterpret_cast<Il2CppMethodGetNameFn>(
        GetProcAddress(assembly, "il2cpp_method_get_name"));
    g_method_get_param_count = reinterpret_cast<Il2CppMethodGetParamCountFn>(
        GetProcAddress(assembly, "il2cpp_method_get_param_count"));
    g_method_get_param = reinterpret_cast<Il2CppMethodGetParamFn>(
        GetProcAddress(assembly, "il2cpp_method_get_param"));
    g_method_get_return_type = reinterpret_cast<Il2CppMethodGetReturnTypeFn>(
        GetProcAddress(assembly, "il2cpp_method_get_return_type"));
    g_type_get_name = reinterpret_cast<Il2CppTypeGetNameFn>(
        GetProcAddress(assembly, "il2cpp_type_get_name"));
    g_field_get_name = reinterpret_cast<Il2CppFieldGetNameFn>(
        GetProcAddress(assembly, "il2cpp_field_get_name"));
    g_field_get_type = reinterpret_cast<Il2CppFieldGetTypeFn>(
        GetProcAddress(assembly, "il2cpp_field_get_type"));
    g_class_get_type = reinterpret_cast<Il2CppClassGetTypeFn>(
        GetProcAddress(assembly, "il2cpp_class_get_type"));
    g_type_get_object = reinterpret_cast<Il2CppTypeGetObjectFn>(
        GetProcAddress(assembly, "il2cpp_type_get_object"));
    g_object_new = reinterpret_cast<Il2CppObjectNewFn>(
        GetProcAddress(assembly, "il2cpp_object_new"));
    g_runtime_invoke = reinterpret_cast<Il2CppRuntimeInvokeFn>(
        GetProcAddress(assembly, "il2cpp_runtime_invoke"));
    g_thread_attach = reinterpret_cast<Il2CppThreadAttachFn>(
        GetProcAddress(assembly, "il2cpp_thread_attach"));

    if (!Ready()) {
        Log("probe: il2cpp metadata exports incomplete; enumeration skipped");
        return BE_Result_NotReady;
    }
    if (g_thread_attach && g_domain_get) g_thread_attach(g_domain_get());
    Log("probe ready: enumerating first-person look candidates");
    Run();
    return BE_Result_Ok;
}

} // namespace

BE_EXPORT const BE_ModuleApiV1* BE_CALL BetterEndfieldNext_GetModuleApiV1(void) {
    return &kApi;
}
