#pragma once
#include <filesystem>
#include <span>
#include <string>
#include <string_view>
#include <vector>
#include <cstdint>
#include <memory>

namespace BetterEndfield::CustomModel {
struct BemPackageInfo;
// Unity names an instance "<prefab>(Clone)"; the game's model pool may append
// "#<serial>" as well (device 2026-10-04: "chr_0003_endminf_postmodel(Clone)#27").
inline std::string_view ResourceBaseName(std::string_view name) {
    if (const auto hash=name.rfind('#');hash!=std::string_view::npos && hash+1<name.size() &&
        name.find_first_not_of("0123456789",hash+1)==std::string_view::npos) name=name.substr(0,hash);
    if (name.ends_with("(Clone)")) name.remove_suffix(7);
    return name;
}
struct ComponentIdentity {
    const char* name;
    uint32_t indices;
    const char* receiver_path = "";
    bool static_mesh = false;
};
struct CharacterAdapter {
    const char* id;
    const char* world_resource;
    const char* ui_resource;
    const char* default_package;
    bool union_texture_masks;
    std::span<const ComponentIdentity> components;
    const char* resource_id = "";
    const char* asset_path = "";
    uint32_t receiver_lod = 0;
    bool explicit_resource = false;
};
struct EnabledMod {
    const CharacterAdapter* adapter = nullptr;
    std::filesystem::path package;
    std::string appearance;
    bool skip_validation = false;
    bool loading_optimization = false;
    std::string selection_key;
    std::string parameters;
    std::string resource_id, package_id;
};
struct OwnedCharacterAdapter {
    std::string id, world, ui;
    std::vector<std::string> names;
    std::string resource_id, asset_path;
    std::vector<std::string> receiver_paths;
    std::vector<ComponentIdentity> components;
    CharacterAdapter adapter{};
};
// Platform-filtered 1.4 resource contract. Component IDs remain manifest-global
// here; the loader lowers them to resource-local IDs after selecting one entry.
struct ResourceBinding {
    std::string package_id, id, name, asset_path;
    uint32_t lod = 0;
    std::vector<uint32_t> component_ids;
};
std::vector<ResourceBinding> SelectResourceBindings(const BemPackageInfo& package,
    std::string_view platform);
struct ModRegistry {
    bool standalone_lod = false;
    bool skip_validation = false;
    bool hot_switch = false;
    // Payload move/release parsing; verified byte-identical, always on.
    bool loading_optimization = true;
    // Optional: fewer render-thread syncs while uploading (faster, higher peak).
    bool fast_loading = false;
    std::vector<EnabledMod> enabled;
    std::vector<std::string> diagnostics;
    std::vector<std::shared_ptr<OwnedCharacterAdapter>> owned_adapters;
    const EnabledMod* Match(std::string_view resource) const;
    std::shared_ptr<const OwnedCharacterAdapter> RetainAdapter(const CharacterAdapter* adapter) const;
};
// Resource names, not adapter pointers, survive a registry generation change.
// The caller must keep both registries alive while consuming this diff.
std::vector<std::string> ChangedResourceNames(const ModRegistry& before,const ModRegistry& after);
struct RegistryUpdatePlan {
    ModRegistry candidate;
    std::vector<std::string> changed_resources;
};
// Parse and validate without mutating the active registry or the previous plan.
bool PrepareResourceRegistryUpdate(std::string_view ini,const std::filesystem::path& root,
    std::string_view platform,const ModRegistry& active,RegistryUpdatePlan& output,std::string& error);
std::span<const CharacterAdapter> CharacterAdapters();
bool ParseModRegistry(std::string_view ini, const std::filesystem::path& package_root,
    ModRegistry& output, std::string& error);
// Builds one mixed-version resource contract for the Android runtime. Parsing
// replaces output only after a candidate registry has been validated.
bool ParseResourceModRegistry(std::string_view ini, const std::filesystem::path& package_root,
    ModRegistry& output, std::string& error, std::string_view platform);
}
