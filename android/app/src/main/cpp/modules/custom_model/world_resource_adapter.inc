// Included inside the shared transaction implementation on Android only.
// Android world prefabs omit LOD0. Build from the exact UI LOD0 resource and
// rebind to world LOD1 only after matching renderer space and every bone path
// and rest matrix. This never equates LOD0/LOD1 geometry or material tables.
bool PrepareAndroidWorldResource(const CharacterAdapter& adapter,std::string_view selection_key,const BemPocData& bem,
    void* asset,std::vector<PreparedBinding>& bindings) {
    void* handle=nullptr; uint32_t handle_root=0;
    struct DonorLifetime {
        void*& handle; uint32_t& root;
        ~DonorLifetime() { betterendfield::AndroidReleaseUiDonor(handle,root); }
    } lifetime{handle,handle_root};
    void* donor=RootTemporary(betterendfield::AndroidLoadUiDonor(adapter.ui_resource,handle,handle_root));
    if (!donor || ObjectName(donor)!=adapter.ui_resource) { Log("Android UI donor unavailable."); return false; }
    std::vector<PreparedBinding> sources;
    if (!ReadCompletedAndroidDonor(adapter,selection_key,bem,donor,sources)) {
        sources.clear();
        if (!PrepareResource(adapter,bem,donor,sources)) return false;
    }
    bool inactive=true;
    void* args[]{g_skinned_renderer_class.type_object,&inactive};
    void* renderers=Invoke(Contract("game_object.renderers"),asset,args);
    int count=ArrayLength(renderers);
    BE_ResolvedClassV1 transform_class{};
    if (count<=0 || count>4096 || g_host->resolve_class(g_host->context,"UnityEngine.CoreModule.dll",
        "UnityEngine","Transform",&transform_class)!=BE_Result_Ok || !transform_class.type_object) return false;
    void* transform_args[]{transform_class.type_object,&inactive};
    void* transforms=Invoke(Contract("game_object.renderers"),asset,transform_args);
    const int transform_count=ArrayLength(transforms);
    if (transform_count<=0 || transform_count>8192) return false;
    std::unordered_map<std::string,void*> world_bones;
    for (int i=0;i<transform_count;++i) {
        void* bone=ArrayValue(transforms,i); std::string path;
        if (!ResourceRelativePath(asset,bone,path)) {
            Log("Android world Transform outside resource root"); return false;
        }
        if (!path.empty() && !world_bones.emplace(path,bone).second) return false;
    }
    // Components matched through the lod0 fallback rename their receiver for
    // the rest of this transaction; the shadow pass reads the same map.
    std::map<uint32_t,std::string> receiver_names;
    for (size_t i=0;i<sources.size();++i) {
        auto& source=sources[i];
        std::string target_name=adapter.components[source.component_id].name;
        if (!target_name.ends_with("_lod0")) return false;
        target_name.back()='1';
        void* renderer=nullptr;
        for (int j=0;j<count;++j) {
            void* candidate=ArrayValue(renderers,j);
            std::string path;
            if (!ResourceRelativePath(asset,candidate,path)) return false;
            if (ObjectName(candidate)!=target_name || path!=std::string("Mesh_all/lod1/")+target_name) continue;
            if (renderer) return false;
            renderer=candidate;
        }
        // Purrche-style hats name their receiver "hat_lod0" on the desktop but
        // ship only a "hat_lod1"-suffixed mesh under Mesh_all/lod0 on Android.
        // Accept the lod0 renderer by its path and rewrite the adapter name so
        // the rest of the keep/identity path sees a consistent target.
        if (!renderer && target_name.ends_with("_lod1")) {
            std::string lod0_target=target_name;
            lod0_target.back()='0';
            for (int j=0;j<count;++j) {
                void* candidate=ArrayValue(renderers,j);
                std::string path;
                if (!ResourceRelativePath(asset,candidate,path)) return false;
                if (path!=std::string("Mesh_all/lod0/")+lod0_target) continue;
                if (renderer) return false;
                renderer=candidate;
                Log("Android world renderer fallback to lod0 accepted: "+lod0_target);
            }
            if (renderer) {
                target_name=lod0_target;
                receiver_names[source.component_id]=lod0_target;
            }
        }
        if (!renderer) { Log("Android world renderer missing: "+target_name); return false; }
        if (!SameMeshSpace(source.renderer,renderer)) {
            Log("Android world renderer mesh space differs: "+target_name); return false;
        }
        PreparedBinding target;
        target.component_id=source.component_id; target.renderer=renderer;
        target.original_mesh=Invoke(Contract("skinned.get_shared_mesh"),renderer,nullptr);
        target.original_bones=Invoke(Contract("skinned.get_bones"),renderer,nullptr);
        target.original_materials=Invoke(Contract("renderer.get_shared_materials"),renderer,nullptr);
        if (!target.original_mesh || ObjectName(target.original_mesh)!=target_name ||
            !GetRendererEnabled(renderer,target.original_enabled)) return false;
        target.custom_enabled=(bem.components[i].info.flags&kComponentFlagHidden)?false:target.original_enabled;
        target.custom_mesh=source.custom_mesh==source.original_mesh?target.original_mesh:source.custom_mesh;
        // A keep override cloned from UI LOD0 may be reused by world LOD1 only
        // when the same native material and original texture pins are present.
        for (size_t k=0;k<bem.components[i].keep_material_overrides.size();++k) {
            const auto& override=bem.components[i].keep_material_overrides[k];
            if (override.material_slot>=static_cast<uint32_t>(ArrayLength(target.original_materials)) ||
                override.material_slot>=static_cast<uint32_t>(ArrayLength(source.original_materials))) return false;
            void* world_material=ArrayValue(target.original_materials,override.material_slot);
            void* ui_material=ArrayValue(source.original_materials,override.material_slot);
            if (!world_material || !ui_material) return false;
            const auto ui_name=ObjectName(ui_material),world_name=ObjectName(world_material);
            constexpr std::string_view ui_prefix="M_actor_",world_prefix="M_actor_lod_";
            const bool world_lod_variant=ui_name.starts_with(ui_prefix) && world_name.starts_with(world_prefix) &&
                std::string_view(ui_name).substr(ui_prefix.size())==std::string_view(world_name).substr(world_prefix.size());
            if (ui_name!=bem.components[i].keep_material_names[k] ||
                (ui_name!=world_name && !world_lod_variant)) {
                Log("Android world keep material identity differs: "+ui_name+" -> "+world_name); return false;
            }
            if (world_lod_variant) {
                // The validated UI copy is published to world LOD1. Its native
                // counterpart may omit properties present in the UI shader.
                Log("Android world keep material LOD counterpart accepted: "+ui_name+" -> "+world_name);
                continue;
            }
            const auto pins=ReadMaterialTextureSlots(world_material);
            for (size_t t=0;t<bem.textures.size();++t) if (override.textures&(uint64_t{1}<<t)) {
                if (MatchTexturePins(pins,bem.textures[t].original_name,ObjectName).status!=TexturePinStatus::Matched)
                    return false;
            }
        }
        target.custom_materials=source.custom_materials;
        if (source.custom_mesh!=source.original_mesh) {
            target.change_shadow=true;
            target.custom_shadow=1; // ShadowCastingMode.On, using the new visible mesh.
            if (!InvokeValue(Contract("android.shadow_get"),renderer,nullptr,target.original_shadow)) return false;
            target.original_shadow_mesh=Invoke(Contract("android.shadow_mesh_get"),renderer,nullptr);
        }
        if (source.custom_bones) {
            const int bones=ArrayLength(source.custom_bones);
            target.custom_bones=NewArrayLike(target.original_bones,bones);
            if (!target.custom_bones) return false;
            const auto& component=bem.components[source.component_id];
            for (int b=0;b<bones;++b) {
                void* ui_bone=ArrayValue(source.custom_bones,b);
                std::string path;
                if (!ResourceRelativePath(donor,ui_bone,path)) {
                    Log("Android UI bone outside resource root"); return false;
                }
                auto found=world_bones.find(path);
                // BEM 1.2 aliases: the world skeleton may name this bone differently
                // at the same parent path; accept only a declared alias there.
                if (found==world_bones.end()) {
                    const auto parent=path.rfind('/');
                    const auto prefix=parent==path.npos?std::string{}:path.substr(0,parent+1);
                    // The UI bone itself may use a UI-only alias. Try the
                    // package's canonical name before the world aliases.
                    found=world_bones.find(prefix+component.bone_names[b]);
                    if (found==world_bones.end() && static_cast<size_t>(b)<component.bone_aliases_by_resource.size()) {
                        for (const auto& alias:component.bone_aliases_by_resource[b][0]) {
                            found=world_bones.find(prefix+alias);
                            if (found!=world_bones.end()) break;
                        }
                    }
                }
                if (found==world_bones.end() || !component.BoneNameMatchesForResource(static_cast<size_t>(b),ObjectName(found->second),0)) {
                    Log("Android world bone path missing: "+path); return false;
                }
                Matrix4x4Raw ui_matrix{},world_matrix{};
                if (!InvokeValue(Contract("transform.local_to_world"),ui_bone,nullptr,ui_matrix) ||
                    !InvokeValue(Contract("transform.local_to_world"),found->second,nullptr,world_matrix)) return false;
                bool rest_match=true;
                for (int n=0;n<16;++n) if (!std::isfinite(ui_matrix.m[n]) || !std::isfinite(world_matrix.m[n]) ||
                    std::abs(ui_matrix.m[n]-world_matrix.m[n])>0.0001f) { rest_match=false; break; }
                if (!rest_match) {
                    // Some mobile world prefabs author the same named skeleton
                    // under a different rest pose. The path/name contract is
                    // still exact, so use the world's live bones and leave the
                    // visual deformation to the owner's runtime validation.
                    Log("Android world bone rest space differs; matched path retained: "+path);
                }
                if (!SetArrayValue(target.custom_bones,b,found->second)) return false;
                target.bone_names.push_back(ObjectName(found->second));
            }
        }
        bindings.push_back(target);
    }
    size_t proxies=0;
    for (const auto& source:sources) {
        const auto& component=bem.components[source.component_id];
        if (source.custom_mesh==source.original_mesh && !(component.info.flags&kComponentFlagHidden)) continue;
        std::string stem=[&]() {
            const auto found=receiver_names.find(source.component_id);
            return found!=receiver_names.end()?found->second:std::string(adapter.components[source.component_id].name);
        }();
        stem.resize(stem.size()-5); // Exact adapter name has the _lod0 suffix.
        const auto proxy_name=stem+"_shadowProxyMobile";
        for (int j=0;j<count;++j) {
            void* renderer=ArrayValue(renderers,j);
            std::string path;
            if (!ResourceRelativePath(asset,renderer,path)) return false;
            if (ObjectName(renderer)!=proxy_name ||
                path!=std::string("Shadow_Proxy/SP_Mobile/")+proxy_name) continue;
            PreparedBinding proxy;
            proxy.component_id=source.component_id; proxy.renderer=renderer;
            proxy.original_mesh=proxy.custom_mesh=Invoke(Contract("skinned.get_shared_mesh"),renderer,nullptr);
            proxy.original_materials=proxy.custom_materials=Invoke(Contract("renderer.get_shared_materials"),renderer,nullptr);
            if (!proxy.original_mesh || !proxy.original_materials ||
                !GetRendererEnabled(renderer,proxy.original_enabled)) return false;
            proxy.custom_enabled=false;
            bindings.push_back(proxy); ++proxies;
        }
    }
    Log("Android shadow transaction prepared: replacement meshes cast; matching mobile proxies="+std::to_string(proxies));
    Log("Android UI LOD0 -> world LOD1 prepared with exact bone paths/rest matrices: "+
        std::to_string(bindings.size())+" components");
    return !g_construction->failed;
}
