#include "first_person_look_probe.h"
#include "core/log.h"

namespace betterendfieldnext {
namespace {

// Field names worth asking about by name. The runtime has no field-name
// enumerator, so a candidate list is the honest way to test a layout: each name
// is a question, and a missing one is reported as missing rather than hidden.
constexpr const char* kLookFieldNames[]{
    "lookSensitivity", "lookSensitivityY", "looksensitivity", "mouseSensitivity",
    "aimSensitivity", "sensitivity", "lookAxis", "aimAxis", "moveAxis",
    "lookDelta", "aimDelta", "lookInput", "lookDirection", "aimDirection",
    "lookYaw", "lookPitch", "yaw", "pitch", "targetYaw", "targetPitch",
    "firstPerson", "isFirstPerson", "firstPersonYaw", "firstPersonPitch",
    "cameraYaw", "cameraPitch", "heightOffset", "headOffset",
};

void ReportClass(const Il2CppRuntime& runtime, const char* assembly,
        const char* namespaze, const char* klass) {
    const std::string described = runtime.DescribeClass(assembly, namespaze, klass);
    // Logcat truncates a line at roughly 4 KB, and a full DescribeClass dump of
    // a real controller exceeds that: the first run lost every method after
    // RotateCameraVertical. Split on the entry separators so each line stays
    // well inside the limit instead of being silently cut.
    std::size_t start = 0;
    constexpr std::size_t kChunk = 1000;
    while (start < described.size()) {
        std::size_t end = std::min(described.size(), start + kChunk);
        if (end < described.size()) {
            const std::size_t pipe = described.rfind(" | ", end);
            if (pipe != std::string::npos && pipe > start) end = pipe;
        }
        LogInfo("fp_look_probe", described.substr(start, end - start).c_str());
        start = end;
    }
}

void ReportFieldNames(const Il2CppRuntime& runtime, const char* assembly,
        const char* namespaze, const char* klass) {
    // Not a substitute for the method dump: field offsets are the part that
    // matters when reading a class the runtime cannot enumerate, and answering
    // "does this candidate name exist" is the most the runtime supports without
    // a field-name getter.
    std::string present;
    for (const char* name : kLookFieldNames) {
        const ResolvedField field = runtime.ResolveField(assembly, namespaze, klass, name);
        if (!field.info) continue;
        char offset[32];
        std::snprintf(offset, sizeof(offset), " %s=0x%x", name, field.offset);
        present += offset;
    }
    LogInfo("fp_look_probe", (std::string("fields ") + klass + " present:" +
        (present.empty() ? " (none)" : present)).c_str());
}

}  // namespace

ModuleResult FirstPersonLookProbe::Start(Il2CppRuntime& runtime) {
    // Read only. Nothing here hooks, writes, or holds a reference past Start.
    // The component name matters: it is what a reader greps for, and it must
    // not collide with the camera module's own "betterendfieldnext.camera" lines,
    // which are numerous enough to bury a short probe report.
    LogInfo("fp_look_probe", "probing the game's own first-person look entry point");

    // The controller the camera module already locates for the first-person
    // toggle. It is enumerated first so it can be compared against the main
    // camera controllers below; the method dump makes clear it is a snapshot /
    // photography camera (ActivateSnapshotCamera, SetAperture, ...), not the
    // camera the module's first person rides on.
    ReportClass(runtime, "Gameplay.Beyond.dll", "Beyond.Gameplay.View",
        "SnapshotCameraController");
    ReportFieldNames(runtime, "Gameplay.Beyond.dll", "Beyond.Gameplay.View",
        "SnapshotCameraController");

    // The main camera controllers. The module's own first person rewrites the
    // pushed Cinemachine CameraState (ApplyFirstPersonState) and keeps the game's
    // orientation authoritative, so the orientation a gyroscope must feed belongs
    // to these two, not to the snapshot controller. CameraManager is the class
    // the module already hooks (TailLateTick) and CameraMono is its per-camera
    // counterpart (the dither tick hook).
    ReportClass(runtime, "Gameplay.Beyond.dll", "Beyond.Gameplay.View",
        "CameraManager");
    ReportFieldNames(runtime, "Gameplay.Beyond.dll", "Beyond.Gameplay.View",
        "CameraManager");
    ReportClass(runtime, "Gameplay.Beyond.dll", "Beyond.Gameplay.View",
        "CameraMono");
    ReportFieldNames(runtime, "Gameplay.Beyond.dll", "Beyond.Gameplay.View",
        "CameraMono");

    // The player controller owns the move axis the body-follow code reads, so an
    // aim/look axis would sit beside it.
    ReportClass(runtime, "Gameplay.Beyond.dll", "Beyond.Gameplay.Core",
        "PlayerController");
    ReportFieldNames(runtime, "Gameplay.Beyond.dll", "Beyond.Gameplay.Core",
        "PlayerController");

    // The movement component carries the input record used for body follow; an
    // input struct here would be the natural aim carrier.
    ReportClass(runtime, "Gameplay.Beyond.dll", "Beyond.Gameplay.Core",
        "MovementComponent");
    ReportClass(runtime, "Gameplay.Beyond.dll", "Beyond.Gameplay.Core", "MoveInput");

    LogInfo("fp_look_probe", "probe complete; no state was modified");
    return {true, "first-person look probe logged its enumeration"};
}

}  // namespace betterendfieldnext
