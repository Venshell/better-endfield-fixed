#include "../modules/camera/module.cpp"
#include <iostream>
#include <stdexcept>

using namespace BetterEndfieldNext::CameraModule;

namespace {
int renderer, source, clone;
void* bound_mesh = &clone;
bool offscreen = true;

void* GetMesh(void*) { return bound_mesh; }
void SetMesh(void*, void* mesh) { bound_mesh = mesh; }
bool GetOffscreen(void*) { return offscreen; }
void SetOffscreen(void*, bool value) { offscreen = value; }
void IgnoreOffscreen(void*, bool) {}
void* GetShadow(void*) { return nullptr; }
void Check(bool value, const char* message) {
    if (!value) throw std::runtime_error(message);
}
}

int main() {
    try {
        g_fp_engine.get_mesh = GetMesh;
        g_fp_engine.set_mesh = SetMesh;
        g_fp_engine.get_offscreen = GetOffscreen;
        g_fp_engine.get_shadow = GetShadow;
        FpPatch patch;
        patch.renderer.object = &renderer;
        patch.renderer.handle = 1;
        patch.source.object = &source;
        patch.source.handle = 1;
        patch.copy.object = &clone;
        patch.copy.handle = 1;
        patch.assigned = true;

        Check(!FpTryRestore(patch), "missing offscreen setter must defer restoration");
        Check(bound_mesh == &source && patch.copy.object == &clone && offscreen,
              "failed restoration released clone or hid the setter failure");

        g_fp_engine.set_offscreen = IgnoreOffscreen;
        Check(!FpTryRestore(patch), "setter with no effect must defer restoration");
        Check(patch.copy.object == &clone && offscreen,
              "unverified offscreen restoration released the clone");

        Check(!FpTryRestore(patch) && patch.restore_failures == 3,
              "restore failures exceeded the retry budget");

        g_fp_engine.set_offscreen = SetOffscreen;
        Check(!FpTryRestore(patch) && offscreen,
              "exhausted retry budget was ignored");
        patch.restore_failures = 0; // explicit lifecycle retry, as on shutdown
        Check(FpTryRestore(patch), "restoration lifecycle retry failed");
        Check(!offscreen && !patch.copy.object,
              "successful retry did not restore offscreen state and release clone");
        FpPatch shutdown_patch;
        shutdown_patch.renderer.object = &renderer;shutdown_patch.renderer.handle = 1;
        shutdown_patch.source.object = &source;shutdown_patch.source.handle = 1;
        shutdown_patch.copy.object = &clone;shutdown_patch.copy.handle = 1;
        shutdown_patch.assigned = true;
        bound_mesh = &clone;offscreen = true;
        g_fp_engine.set_offscreen = IgnoreOffscreen;
        g_fp_mesh_session.patches.push_back(std::move(shutdown_patch));
        FpCloseNeckCap();
        Check(g_fp_mesh_session.patches.empty(),
              "shutdown retained GC handles after bounded restoration attempts");
        std::cout << "first_person_restore: missing/no-op setter retained patch; bounded lifecycle retry restored state\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
