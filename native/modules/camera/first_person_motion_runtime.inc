// Included after first_person_facing_runtime.inc inside the camera namespace.
// Axis extraction adapted from RenoDX Endfield Enhancer camera_motion.hpp (MIT).
// Managed arrays and Matrix4x4 fields are resolved from runtime metadata; this
// path deliberately has no dependency on the mesh GPU-readback engine.
struct FpMotionBindings {
    size_t (*array_length)(void*) = nullptr;
    uint32_t (*array_bytes)(void*) = nullptr;
    size_t (*array_header)() = nullptr;
    int32_t (*value_size)(const void*, uint32_t*) = nullptr;
    size_t header = 0;
    size_t matrix_size = 0;
    int32_t matrix_offsets[3][3]{};
    bool attempted = false;
    bool ready = false;

    bool Init() {
        if (attempted) return ready;
        attempted = true;
        if (!g_host || !g_host->resolve_class || !g_host->resolve_field ||
            g_value_type_header <= 0) return false;
        auto game = GetModuleHandleW(L"GameAssembly.dll");
        if (!game) return false;
        array_length = reinterpret_cast<decltype(array_length)>(GetProcAddress(game, "il2cpp_array_length"));
        array_bytes = reinterpret_cast<decltype(array_bytes)>(GetProcAddress(game, "il2cpp_array_get_byte_length"));
        array_header = reinterpret_cast<decltype(array_header)>(GetProcAddress(game, "il2cpp_array_object_header_size"));
        value_size = reinterpret_cast<decltype(value_size)>(GetProcAddress(game, "il2cpp_class_value_size"));
        if (!array_length || !array_bytes || !array_header || !value_size) return false;
        header = FpValue<size_t>(0, array_header);
        if (header < sizeof(void*) * 2 || header > 128) return false;
        BE_ResolvedClassV1 matrix{};
        if (g_host->resolve_class(g_host->context, "UnityEngine.CoreModule.dll",
                "UnityEngine", "Matrix4x4", &matrix) != BE_Result_Ok || !matrix.class_info) return false;
        uint32_t alignment = 0;
        const int32_t size = FpValue<int32_t>(0, value_size, matrix.class_info, &alignment);
        if (size < 9 * static_cast<int32_t>(sizeof(float)) || size > 256) return false;
        matrix_size = static_cast<size_t>(size);
        for (int row = 0; row < 3; ++row) {
            for (int column = 0; column < 3; ++column) {
                const char name[]{'m', static_cast<char>('0' + row), static_cast<char>('0' + column), '\0'};
                BE_FieldDescriptorV1 descriptor{"UnityEngine.CoreModule.dll", "UnityEngine", "Matrix4x4", name, "System.Single"};
                BE_ResolvedFieldV1 field{};
                if (g_host->resolve_field(g_host->context, &descriptor, &field) != BE_Result_Ok ||
                    !field.field_info) return false;
                const int32_t offset = field.offset - g_value_type_header;
                if (offset < 0 || static_cast<size_t>(offset) + sizeof(float) > matrix_size) return false;
                matrix_offsets[row][column] = offset;
            }
        }
        ready = true;
        return true;
    }

    bool Array(void* array, size_t stride, size_t& count) const {
        if (!array || !stride) return false;
        count = FpValue<size_t>(0, array_length, array);
        return count > 0 && count <= 512 &&
            FpValue<uint32_t>(0, array_bytes, array) == count * stride;
    }

    bool Direction(void* poses, size_t index, FpMath::Vec3 input, FpMath::Vec3& output) const {
        const auto* base = static_cast<const uint8_t*>(poses) + header + index * matrix_size;
        float values[3]{};
        for (int row = 0; row < 3; ++row) {
            float coefficient[3]{};
            for (int column = 0; column < 3; ++column) {
                if (!FpCopy(&coefficient[column], base + matrix_offsets[row][column], sizeof(float))) return false;
            }
            values[row] = coefficient[0] * input.x + coefficient[1] * input.y + coefficient[2] * input.z;
        }
        output = {values[0], values[1], values[2]};
        return FpMath::Finite(output);
    }
};

struct FpMotionSession {
    FpRoot model, head, body;
    FpMotionBindings bindings;
    FpMath::Vec3 head_forward{}, head_up{}, body_forward{}, body_up{};
    bool attempted = false;
    bool head_axes = false;
    bool body_axes = false;
};
FpMotionSession g_fp_motion;

void FpMotionReset() {
    g_fp_motion = FpMotionSession{};
}

bool FpMotionAlive(void* object) {
    if (!object) return false;
    void* parameters[]{object, nullptr};
    bool destroyed = true;
    return Unbox(Invoke(Contract("unity.object.op_equality"), nullptr, parameters), destroyed) && !destroyed;
}

bool FpMotionRotation(void* transform, FpMath::Quat& rotation) {
    return FpMotionAlive(transform) &&
        Unbox(Invoke(Contract("unity.transform.rotation.get"), transform, nullptr), rotation) && FpMath::Unit(rotation);
}

void FpMotionResolveAxes() {
    auto& session = g_fp_motion;
    session.attempted = true;
    if (!session.bindings.Init()) {
        Log("First person motion: array/matrix metadata unavailable; keeping control view.");
        return;
    }
    FpRoot ancestor(Invoke(Contract("unity.transform.parent.get"), session.head.object, nullptr));
    for (int depth = 0; ancestor && depth < 8; ++depth) {
        if (!FpMotionAlive(ancestor.object)) break;
        const std::string name = LowerAscii(ObjectName(ancestor.object));
        if (name.find("spine") != std::string::npos || name == "chest") {
            session.body = FpRoot(ancestor.object);
            break;
        }
        ancestor = FpRoot(Invoke(Contract("unity.transform.parent.get"), ancestor.object, nullptr));
    }
    FpMath::Quat model_rotation{};
    if (!FpMotionRotation(session.model.object, model_rotation)) return;
    std::vector<HeadPartProbe> parts;
    std::string tree;
    int visited = 0, logged = 0;
    ScanPartNodes(session.model.object, 0, visited, parts, tree, logged);
    for (const auto& part : parts) {
        if (session.head_axes && (session.body_axes || !session.body)) break;
        if (!part.skinned || !part.renderer) continue;
        FpRoot renderer(part.renderer);
        if (!renderer || !FpMotionAlive(renderer.object)) continue;
        FpRoot bones(Invoke(Contract("unity.skinned_mesh_renderer.bones.get"), renderer.object, nullptr));
        FpRoot mesh(Invoke(Contract("unity.skinned_mesh_renderer.shared_mesh.get"), renderer.object, nullptr));
        FpRoot poses(mesh ? Invoke(Contract("unity.mesh.bindposes.get"), mesh.object, nullptr) : nullptr);
        FpRoot game_object(Invoke(Contract("unity.component.game_object"), renderer.object, nullptr));
        FpRoot transform(game_object ? Invoke(Contract("unity.game_object.transform"), game_object.object, nullptr) : nullptr);
        size_t count = 0, pose_count = 0;
        FpMath::Quat renderer_rotation{};
        const auto& bindings = session.bindings;
        if (!bones || !poses || !transform || !bindings.Array(bones.object, sizeof(void*), count) ||
            !bindings.Array(poses.object, bindings.matrix_size, pose_count) || count != pose_count ||
            !FpMotionRotation(transform.object, renderer_rotation)) continue;
        const FpMath::Quat model_to_mesh = FpMath::Quat{-renderer_rotation.x, -renderer_rotation.y,
            -renderer_rotation.z, renderer_rotation.w} * model_rotation;
        const FpMath::Vec3 mesh_forward = FpMath::Rotate(model_to_mesh, {0, 0, 1});
        const FpMath::Vec3 mesh_up = FpMath::Rotate(model_to_mesh, {0, 1, 0});
        for (size_t index = 0; index < count; ++index) {
            void* bone = nullptr;
            if (!FpCopy(&bone, static_cast<const uint8_t*>(bones.object) + bindings.header + index * sizeof(void*), sizeof(bone))) break;
            const bool is_head = bone == session.head.object;
            const bool is_body = session.body && bone == session.body.object;
            if ((!is_head || session.head_axes) && (!is_body || session.body_axes)) continue;
            FpMath::Vec3 forward{}, up{};
            FpMath::Quat check{};
            if (!bindings.Direction(poses.object, index, mesh_forward, forward) ||
                !bindings.Direction(poses.object, index, mesh_up, up) ||
                !FpMath::FacingRotation(forward, up, &check)) continue;
            if (is_head) {
                session.head_forward = forward;
                session.head_up = up;
                session.head_axes = true;
            }
            if (is_body) {
                session.body_forward = forward;
                session.body_up = up;
                session.body_axes = true;
            }
        }
    }
    Log(std::string("First person motion: bind axes head=") + (session.head_axes ? "ready" : "unavailable") +
        ", body=" + (session.body_axes ? "ready" : "unavailable") + ".");
}

// The CameraState owner calls this only after its action gate permits first
// person. Off/exit clears roots; the returned offset never writes Unity camera
// transforms, so it cannot feed the last rendered view back into player input.
FpMath::Quat FpMotionApply(FpMath::Quat control, int mode, float strength) {
    if (mode < 1 || mode > 3 || !std::isfinite(strength) || strength <= 0 ||
        !FpMath::Unit(control) || g_fp_facing.interaction_alignment) {
        FpMotionReset();
        return control;
    }
    FpRoot model(FindModelTransform());
    void* head = g_first_person.head;
    if (!model || !FpMotionAlive(model.object) || !FpMotionAlive(head) || head == model.object ||
        LowerAscii(ObjectName(head)).find("head") == std::string::npos) {
        FpMotionReset();
        return control;
    }
    if (g_fp_motion.model.object != model.object || g_fp_motion.head.object != head) {
        FpMotionReset();
        g_fp_motion.model = FpRoot(model.object);
        g_fp_motion.head = FpRoot(head);
    }
    auto& session = g_fp_motion;
    if (!session.model || !session.head) return control;
    if (!session.attempted) FpMotionResolveAxes();
    const bool use_head = mode != 1;
    if (use_head ? !session.head_axes : !session.body_axes) return control;
    FpMath::Quat bone{}, facing{}, offset{};
    if (!FpMotionRotation(use_head ? session.head.object : session.body.object, bone) ||
        !FpMath::FacingRotation(FpMath::Rotate(bone, use_head ? session.head_forward : session.body_forward),
            FpMath::Rotate(bone, use_head ? session.head_up : session.body_up), &facing)) return control;
    float reference_yaw = g_fp_facing.state.held_yaw;
    if (!g_fp_facing.state.attached) {
        FpMath::Quat model_rotation{};
        if (!FpMotionRotation(session.model.object, model_rotation)) return control;
        const auto forward = FpMath::Rotate(model_rotation, {0, 0, 1});
        reference_yaw = std::atan2(forward.x, forward.z) * 57.295779513f;
    }
    if (!BetterEndfieldNext::FirstPerson::AnimationTarget(
            static_cast<BetterEndfieldNext::FirstPerson::AnimationMode>(mode), facing, reference_yaw, strength, offset)) return control;
    const auto result = control * offset;
    return FpMath::Unit(result) ? result : control;
}
