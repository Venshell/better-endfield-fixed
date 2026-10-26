#include "../modules/custom_model/bem.h"
#include "../modules/custom_model/bem_rewrite.h"
#include "../modules/custom_model/mod_registry.h"
#include "../modules/custom_model/generic_model_matcher.h"
#include <algorithm>
#include <filesystem>
#include <iostream>
#include <string>

using namespace BetterEndfield::CustomModel;

int main(int argc, char** argv) {
    if (argc != 2) return 2;
    const std::filesystem::path root(argv[1]);
    const auto valid = root / "multi-resource-valid.bem";
    std::string error;
    BemPackageInfo info;
    if (!ReadBemPackageInfo(valid, info, error) || info.minor != 4 ||
        info.resources.size() != 3 || info.target_id != "chr_synthetic") {
        std::cerr << "BEM 1.4 metadata: " << error << '\n'; return 1;
    }
    namespace matching = BetterEndfield::CustomModel::GenericMatching;
    if (!matching::ReceiverLodAgrees("Mesh_all/lod1/blade",1) ||
        matching::ReceiverLodAgrees("Mesh_all/lod0/blade",1)) {
        std::cerr << "Explicit receiver LOD contract differs\n"; return 1;
    }
    int receiver_a=0,receiver_b=0,mesh_a=0,mesh_b=0;
    std::vector<matching::Candidate> candidates{
        {{"chr_synthetic","weapon","Mesh/blade",matching::Region::Explicit},
          &receiver_a,{matching::DonorOrigin::Pristine,&mesh_a,"blade",3,true,{}}},
        {{"chr_synthetic","weapon","Elsewhere/blade",matching::Region::Explicit},
          &receiver_b,{matching::DonorOrigin::Pristine,&mesh_b,"blade",3,true,{}}}};
    const matching::Request exact{"chr_synthetic","weapon","blade",
        matching::Region::Explicit,3,true,"Mesh/blade",true};
    const auto matched=matching::SelectUnique(candidates,exact,[](const auto&){return true;});
    if (matched.status!=matching::MatchStatus::Matched || matched.index!=0) {
        std::cerr << "Explicit receiver path was not unique\n"; return 1;
    }
    auto missing_path=exact; missing_path.verified_receiver_path={};
    missing_path.verified_receiver_path_present=false;
    if (matching::SelectUnique(candidates,missing_path,[](const auto&){return true;}).status!=
        matching::MatchStatus::InvalidIndex) {
        std::cerr << "Explicit receiver accepted an omitted path\n"; return 1;
    }
    candidates[0].key.path="";
    auto root_path=exact; root_path.verified_receiver_path={};
    if (matching::SelectUnique(candidates,root_path,[](const auto&){return true;}).status!=
        matching::MatchStatus::Matched) {
        std::cerr << "Verified resource-root renderer was rejected\n"; return 1;
    }
    candidates[0].key.path="Mesh/blade";
    candidates[1].key.path="Mesh/blade";
    if (matching::SelectUnique(candidates,exact,[](const auto&){return true;}).status!=
        matching::MatchStatus::InvalidIndex) {
        std::cerr << "Explicit receiver accepted duplicate path\n"; return 1;
    }
    const auto android = SelectResourceBindings(info, "android-arm64");
    const auto windows = SelectResourceBindings(info, "windows-x64");
    BemPackageInfo windows_only;
    if(!ReadBemPackageInfo(root/"multi-resource-windows-only.bem",windows_only,error) ||
        !SelectResourceBindings(windows_only,"android-arm64").empty()) {
        std::cerr << "Windows-only resource appeared on Android: " << error << '\n'; return 1;
    }
    if (android.size() != 2 || windows.size() != 3 ||
        android[0].package_id != info.package_id ||
        android[0].id != "body" || android[1].id != "weapon" ||
        android[1].lod != 1 || android[1].component_ids != std::vector<uint32_t>{2} ||
        android[1].name != "weapon" ||
        android[1].asset_path != "assets/synthetic/weapon.prefab" ||
        !SelectResourceBindings(info, "unknown-platform").empty()) {
        std::cerr << "Platform resource binding differs\n"; return 1;
    }
    for (const auto& resource : info.resources) {
        BemPocData parsed;
        if (!LoadBem(valid, parsed, error, "detail:show", nullptr, false, true,
                     "length:1000", UINT64_MAX, false, resource.id) ||
            parsed.components.size() != 1 || parsed.header.component_count != 1 ||
            parsed.components[0].info.component_id != 0) {
            std::cerr << "Selected resource " << resource.id << ": " << error << '\n'; return 1;
        }
        if ((resource.id == "weapon") != parsed.components[0].static_mesh) {
            std::cerr << "Renderer kind differs\n"; return 1;
        }
    }
    const auto converted=root/"multi-resource-rewrite-test.bem";
    if(std::filesystem::exists(converted)) {
        std::cerr << "Rewrite test output already exists\n"; return 1;
    }
    std::string report;
    if(!RewriteBemTextures(valid,converted,[](const auto&,auto&,auto&){},[]{},report,error)) {
        std::cerr << "BEM 1.4 texture rewrite: " << error << '\n'; return 1;
    }
    struct RemoveRewritten {
        std::filesystem::path path;
        ~RemoveRewritten() { std::error_code ignored; std::filesystem::remove(path,ignored); }
    } remove_rewritten{converted};
    for(const auto& resource:info.resources) {
        BemPocData parsed;
        if(!LoadBem(converted,parsed,error,info.default_options,nullptr,false,false,{},UINT64_MAX,false,resource.id) ||
            parsed.components.size()!=1) {
            std::cerr << "Converted resource " << resource.id << ": " << error << '\n'; return 1;
        }
    }
    BemPocData unscoped;
    if (LoadBem(valid, unscoped, error) ||
        error.find("resource-aware runtime") == std::string::npos) {
        std::cerr << "Legacy runtime accepted BEM 1.4\n"; return 1;
    }
    for (const auto* mode : {"", "skip_validation=true\n"}) {
        ModRegistry registry;
        const std::string config = std::string("[CustomModel]\n") + mode +
            "[Mod.test]\nenabled=true\npackage=multi-resource-valid.bem\n";
        if (!ParseModRegistry(config, root, registry, error) || !registry.enabled.empty() ||
            std::none_of(registry.diagnostics.begin(), registry.diagnostics.end(), [](const auto& message) {
                return message.find("BEM 1.4 requires a resource-aware runtime") != std::string::npos;
            })) {
            std::cerr << "Legacy registry accepted BEM 1.4: " << error << '\n'; return 1;
        }
    }
    const std::string resource_config =
        "[CustomModel]\n[Mod.test]\nenabled=true\npackage=multi-resource-valid.bem\n";
    ModRegistry resource_registry;
    if (ParseResourceModRegistry(resource_config, root, resource_registry, error, "unknown-platform") ||
        error != "Unsupported BEM resource platform") {
        std::cerr << "Resource registry accepted unsupported platform\n"; return 1;
    }
    if (!ParseResourceModRegistry(resource_config, root, resource_registry, error, "android-arm64") ||
        resource_registry.enabled.size() != 2 ||
        resource_registry.Match("body(Clone)") != &resource_registry.enabled[0] ||
        resource_registry.Match("weapon(Clone)#27") != &resource_registry.enabled[1] ||
        resource_registry.Match("ultimate") != nullptr ||
        resource_registry.enabled[1].resource_id != "weapon" ||
        resource_registry.enabled[1].adapter->receiver_lod != 1 ||
        !resource_registry.enabled[1].adapter->components[0].static_mesh ||
        std::string(resource_registry.enabled[1].adapter->components[0].receiver_path) != "Mesh/blade") {
        std::cerr << "Resource registry did not bind Android metadata: " << error << '\n'; return 1;
    }
    const auto* retained_adapter=resource_registry.enabled[0].adapter;
    auto retained_owner=resource_registry.RetainAdapter(retained_adapter);
    if (!retained_owner || &retained_owner->adapter!=retained_adapter) {
        std::cerr << "Resource adapter has no retained owner\n"; return 1;
    }
    if (ParseResourceModRegistry("[Mod.broken]\nenabled=true\nenabled=false\n",
            root, resource_registry, error, "android-arm64") ||
        error != "Malformed or duplicate runtime configuration" ||
        resource_registry.enabled.size()!=2 ||
        resource_registry.enabled[0].adapter!=retained_adapter ||
        resource_registry.Match("body(Clone)")!=&resource_registry.enabled[0]) {
        std::cerr << "Invalid candidate replaced the active resource registry\n"; return 1;
    }
    if (!ParseResourceModRegistry("[CustomModel]\n",root,resource_registry,error,"android-arm64") ||
        !resource_registry.enabled.empty() ||
        &retained_owner->adapter!=retained_adapter ||
        std::string(retained_adapter->world_resource)!="body") {
        std::cerr << "Retained adapter expired after registry replacement\n"; return 1;
    }
    if (!ParseResourceModRegistry(resource_config,root,resource_registry,error,"android-arm64") ||
        resource_registry.enabled.size()!=2) {
        std::cerr << "Resource registry could not be restored\n"; return 1;
    }
    ModRegistry duplicate_registry;
    if (!ParseResourceModRegistry(resource_config+
            "[Mod.duplicate]\nenabled=true\npackage=multi-resource-valid.bem\n",
            root, duplicate_registry, error, "android-arm64") ||
        !duplicate_registry.enabled.empty() ||
        std::none_of(duplicate_registry.diagnostics.begin(),duplicate_registry.diagnostics.end(),
            [](const auto& message){return message.find("Conflicting enabled package")!=std::string::npos;})) {
        std::cerr << "Resource registry accepted duplicate package: " << error << '\n'; return 1;
    }
    const auto second_config = resource_config+
        "[Mod.second]\nenabled=true\npackage=multi-resource-conflict.bem\n";
    if (!ParseResourceModRegistry(second_config, root, duplicate_registry, error, "android-arm64") ||
        !duplicate_registry.enabled.empty()) {
        std::cerr << "Resource registry accepted shared roots across packages: " << error << '\n'; return 1;
    }
    const auto disjoint_config = resource_config+
        "[Mod.second]\nenabled=true\npackage=multi-resource-disjoint.bem\n";
    if (!ParseResourceModRegistry(disjoint_config, root, duplicate_registry, error, "android-arm64") ||
        duplicate_registry.enabled.size() != 4 ||
        !duplicate_registry.Match("other-body(Clone)") ||
        !duplicate_registry.Match("other-weapon")) {
        std::cerr << "Resource registry rejected independent package: " << error << '\n'; return 1;
    }
    BemPackageInfo legacy_info;
    if (!ReadBemPackageInfo(root / "legacy-install-fixture.bem", legacy_info, error) ||
        legacy_info.minor >= 4) {
        std::cerr << "Legacy registry fixture unavailable: " << error << '\n'; return 1;
    }
    const std::string legacy_section =
        "[Mod.legacy]\nenabled=true\npackage=legacy-install-fixture.bem\n";
    if (!ParseResourceModRegistry(resource_config+legacy_section, root, duplicate_registry,
            error, "android-arm64") || duplicate_registry.enabled.size() != 3 ||
        !duplicate_registry.Match(legacy_info.world_resource) ||
        !duplicate_registry.Match("weapon")) {
        std::cerr << "Mixed BEM 1.0/1.4 registry failed: " << error << '\n'; return 1;
    }
    if (!duplicate_registry.RetainAdapter(duplicate_registry.Match(legacy_info.world_resource)->adapter) ||
        !duplicate_registry.RetainAdapter(duplicate_registry.Match("weapon")->adapter)) {
        std::cerr << "Mixed-version adapters lack lifetime owners\n"; return 1;
    }
    if (!ParseResourceModRegistry(second_config+legacy_section, root, duplicate_registry,
            error, "android-arm64") || duplicate_registry.enabled.size() != 1 ||
        !duplicate_registry.Match(legacy_info.world_resource) ||
        duplicate_registry.Match("body")) {
        std::cerr << "Independent legacy package was removed with conflicting 1.4 pair: "
                  << error << '\n'; return 1;
    }
    const std::string legacy_conflict_config = legacy_section+
        "[Mod.clash]\nenabled=true\npackage=multi-resource-legacy-conflict.bem\n";
    if (!ParseResourceModRegistry(legacy_conflict_config, root, duplicate_registry,
            error, "android-arm64") || !duplicate_registry.enabled.empty()) {
        std::cerr << "Legacy and explicit packages shared a root: " << error << '\n'; return 1;
    }
    if (!ParseResourceModRegistry(legacy_conflict_config+
            "[Mod.disjoint]\nenabled=true\npackage=multi-resource-disjoint.bem\n",
            root, duplicate_registry, error, "android-arm64") ||
        duplicate_registry.enabled.size() != 2 ||
        !duplicate_registry.Match("other-weapon")) {
        std::cerr << "Independent package was lost after mixed-version root conflict: "
                  << error << '\n'; return 1;
    }
    for (const auto* name : {"cross-resource-donor-invalid.bem",
                              "downgraded-header-invalid.bem",
                              "uppercase-resource-invalid.bem"}) {
        if (ReadBemPackageInfo(root / name, info, error, true)) {
            std::cerr << "Invalid package accepted: " << name << '\n'; return 1;
        }
        if (std::string_view(name) == "uppercase-resource-invalid.bem" &&
            error.find("Resource root must be normalized lowercase") == std::string::npos) {
            std::cerr << "Uppercase root rejected for another reason: " << error << '\n'; return 1;
        }
    }
    std::cout << "BEM 1.4 format checks passed\n";
    return 0;
}
