#include "mod_registry.h"
#include "bem.h"
#include <map>
#include <algorithm>
#include <set>

namespace BetterEndfield::CustomModel {
namespace {
std::string Trim(std::string_view s) {
    auto a=s.find_first_not_of(" \t\r\n"); if(a==s.npos) return {};
    return std::string(s.substr(a,s.find_last_not_of(" \t\r\n")-a+1));
}
bool Boolean(std::string_view s,bool& out) {
    if(s=="true"||s=="1") {out=true;return true;}
    if(s=="false"||s=="0") {out=false;return true;}
    return false;
}
std::string FileTimeIdentity(std::filesystem::file_time_type time) {
    // libc++ uses a 128-bit file-clock representation on Android. Preserve the
    // complete tick count without an ambiguous to_string or narrowing cast.
    auto ticks=time.time_since_epoch().count();
    if (!ticks) return "0";
    const bool negative=ticks<0; std::string digits;
    while (ticks) {
        const int digit=static_cast<int>(ticks%10);
        digits.push_back(static_cast<char>('0'+(digit<0?-digit:digit))); ticks/=10;
    }
    if (negative) digits.push_back('-');
    std::reverse(digits.begin(),digits.end()); return digits;
}
}
std::span<const CharacterAdapter> CharacterAdapters() { return {}; }
std::vector<ResourceBinding> SelectResourceBindings(const BemPackageInfo& package,
    std::string_view platform) {
    std::vector<ResourceBinding> result;
    if (package.minor < 4) return result;
    for (const auto& resource : package.resources) {
        if (std::find(resource.platforms.begin(), resource.platforms.end(), platform) == resource.platforms.end())
            continue;
        result.push_back({package.package_id, resource.id, resource.name, resource.asset_path,
                          resource.lod, resource.component_ids});
    }
    return result;
}
const EnabledMod* ModRegistry::Match(std::string_view resource) const {
    resource=ResourceBaseName(resource);
    for(const auto& mod:enabled) if(resource==mod.adapter->world_resource||resource==mod.adapter->ui_resource) return &mod;
    return nullptr;
}
std::vector<std::string> ChangedResourceNames(const ModRegistry& before,const ModRegistry& after) {
    const auto selections=[](const ModRegistry& registry) {
        std::map<std::string,std::string> result;
        for(const auto& mod:registry.enabled) {
            if(!mod.adapter) continue;
            for(const char* resource:{mod.adapter->world_resource,mod.adapter->ui_resource})
                if(resource && *resource) result.insert_or_assign(resource,mod.selection_key);
        }
        return result;
    };
    const auto old=selections(before),current=selections(after);
    std::vector<std::string> changed;
    for(const auto& [name,key]:old) {
        const auto found=current.find(name);
        if(found==current.end()||found->second!=key) changed.push_back(name);
    }
    for(const auto& [name,key]:current) if(!old.contains(name)) changed.push_back(name);
    std::sort(changed.begin(),changed.end());
    return changed;
}
bool ParseModRegistryImpl(std::string_view ini,const std::filesystem::path& root,ModRegistry& output,
    std::string& error,bool resource_mode,std::string_view platform) {
    error.clear();
    if(resource_mode && platform!="android-arm64" && platform!="windows-x64") {
        error="Unsupported BEM resource platform"; return false;
    }
    try {
        if(ini.size()>1024*1024) {error="Runtime configuration exceeds 1 MiB";return false;}
        if(ini.starts_with("\xef\xbb\xbf")) ini.remove_prefix(3);
        std::map<std::string,std::map<std::string,std::string>> sections; std::string section;
        while(!ini.empty()) {
            auto e=ini.find('\n'); auto line=Trim(ini.substr(0,e)); ini=e==ini.npos?std::string_view{}:ini.substr(e+1);
            if(line.empty()||line[0]=='#'||line[0]==';') continue;
            if(line.front()=='['&&line.back()==']') {section=line.substr(1,line.size()-2);continue;}
            auto equal=line.find('=');
            if(section.empty()||equal==line.npos||!sections[section].emplace(Trim(line.substr(0,equal)),Trim(line.substr(equal+1))).second) {
                error="Malformed or duplicate runtime configuration";return false;
            }
        }
        ModRegistry parsed;
        if(auto i=sections["CustomModel"].find("standalone_lod");i!=sections["CustomModel"].end()&&!Boolean(i->second,parsed.standalone_lod)) {
            error="standalone_lod must be boolean";return false;
        }
        if(auto i=sections["CustomModel"].find("skip_validation");i!=sections["CustomModel"].end()&&!Boolean(i->second,parsed.skip_validation)) {
            error="skip_validation must be boolean";return false;
        }
        if(auto i=sections["CustomModel"].find("hot_switch");i!=sections["CustomModel"].end()&&!Boolean(i->second,parsed.hot_switch)) {
            error="hot_switch must be boolean";return false;
        }
        // The former experimental loading_optimization key is now always on and ignored.
        if(auto i=sections["CustomModel"].find("fast_loading");i!=sections["CustomModel"].end()&&!Boolean(i->second,parsed.fast_loading)) {
            error="fast_loading must be boolean";return false;
        }
        if(parsed.skip_validation) parsed.diagnostics.push_back("Developer mode: model validation disabled; crashes and incorrect rendering are possible.");
        std::set<std::string> roles,resources,conflicts;
        std::set<std::string> package_ids,resource_conflicts;
        std::map<std::string,std::string> resource_owners;
        std::map<std::string,std::string> role_owners;
        for(const auto& [name,values]:sections) {
            if(!name.starts_with("Mod.")) continue;
            auto get=[&](const char* key) {auto i=values.find(key);return i==values.end()?std::string{}:i->second;};
            bool enabled=false;
            if(!get("enabled").empty()&&!Boolean(get("enabled"),enabled)) {error="Invalid enabled flag";return false;}
            if(!enabled) continue;
            auto file=get("package");
            auto path=std::filesystem::path(std::u8string_view(reinterpret_cast<const char8_t*>(file.data()),file.size()));
            auto ext = path.extension();
            if(file.empty()||(ext!=".bem"&&ext!=".zip"&&ext!=".BEM"&&ext!=".ZIP")) {parsed.diagnostics.push_back("Refused non-BEMv1 package: "+name);continue;}
            if(path.is_relative()) path=root/path;
            BemPackageInfo info; std::string why;
            if(!ReadBemPackageInfo(path,info,why,parsed.skip_validation)) {parsed.diagnostics.push_back("Package refused: "+name+": "+why);continue;}
            if(!resource_mode && info.minor>=4) {
                parsed.diagnostics.push_back("Package refused: "+name+": BEM 1.4 requires a resource-aware runtime");
                continue;
            }
            auto appearance=info.minor?get("options"):get("appearance");
            if(appearance.empty()) appearance=info.minor?info.default_options:info.default_appearance;
            if(!info.minor && std::find(info.appearances.begin(),info.appearances.end(),appearance)==info.appearances.end()) {
                parsed.diagnostics.push_back("Appearance removed; using package default: "+name);appearance=info.default_appearance;
            }
            std::string parameters;
            std::string drivers = get("drivers");
            if(!ResolveBemParameters(info,get("parameters"),parameters,why)) {
                parsed.diagnostics.push_back("Parameters removed or invalid; using package defaults: "+name+": "+why);
                parameters=info.default_parameters;
            }
            if(resource_mode && info.minor>=4) {
                auto bindings=SelectResourceBindings(info,platform);
#if defined(__ANDROID__)
                // A weapon authored only for the desktop keeps its
                // platform-neutral resource names installable; the runtime
                // resolves them through the sibling-LOD matcher.
                if(bindings.empty() && info.target_kind=="weapon") {
                    bindings=SelectResourceBindings(info,"windows-x64");
                }
#endif
                if(bindings.empty()) {
                    parsed.diagnostics.push_back("Package has no resource for "+std::string(platform)+": "+info.package_id);
                    continue;
                }
                if(!package_ids.insert(info.package_id).second) resource_conflicts.insert(info.package_id);
                std::error_code file_error;
                const auto stamp=std::filesystem::last_write_time(path,file_error);
                const auto bytes=std::filesystem::file_size(path,file_error);
                const auto canonical=path.lexically_normal();
                const std::string key=canonical.string()+"\n"+appearance+"\n"+parameters+"\n"+
                    (parsed.skip_validation?"unchecked":"checked")+"\n"+
                    FileTimeIdentity(stamp)+":"+std::to_string(bytes);
                for(const auto& binding:bindings) {
                    auto [owner,inserted]=resource_owners.emplace(binding.name,info.package_id);
                    if(!inserted) {resource_conflicts.insert(owner->second);resource_conflicts.insert(info.package_id);}
                    auto own=std::make_shared<OwnedCharacterAdapter>();
                    own->id=info.target_id; own->world=binding.name; own->ui=binding.name;
                    own->resource_id=binding.id; own->asset_path=binding.asset_path;
                    own->names.reserve(binding.component_ids.size());
                    own->receiver_paths.reserve(binding.component_ids.size());
                    for(auto id:binding.component_ids) {
                        own->names.push_back(info.component_names.at(id));
                        own->receiver_paths.push_back(info.component_paths.at(id));
                    }
                    for(size_t i=0;i<binding.component_ids.size();++i) {
                        const auto id=binding.component_ids[i];
                        own->components.push_back({own->names[i].c_str(),info.original_counts.at(id),
                            own->receiver_paths[i].c_str(),info.component_static.at(id)});
                    }
                    own->adapter={own->id.c_str(),own->world.c_str(),own->ui.c_str(),"",false,
                        own->components,own->resource_id.c_str(),own->asset_path.c_str(),binding.lod,true};
                    // Name every member: a positional aggregate initializer silently
                    // shifted resource_id into drivers when the struct gained a field.
                    EnabledMod mod;
                    mod.adapter=&own->adapter; mod.package=canonical; mod.appearance=appearance;
                    mod.skip_validation=parsed.skip_validation; mod.loading_optimization=parsed.loading_optimization;
                    mod.selection_key=key+"\nresource:"+binding.id; mod.parameters=parameters; mod.drivers=drivers;
                    mod.resource_id=binding.id; mod.package_id=info.package_id;
                    parsed.enabled.push_back(std::move(mod));
                    parsed.owned_adapters.push_back(std::move(own));
                }
                continue;
            }
            if(!resource_mode && (!roles.insert(info.character_id).second ||
                resources.contains(info.world_resource)||resources.contains(info.ui_resource))) {
                conflicts.insert(info.character_id); parsed.diagnostics.push_back("Conflicting enabled package: "+info.character_id); continue;
            }
            if(resource_mode) {
                if(!package_ids.insert(info.package_id).second) resource_conflicts.insert(info.package_id);
                auto [role_owner,role_inserted]=role_owners.emplace(info.character_id,info.package_id);
                if(!role_inserted) {
                    resource_conflicts.insert(role_owner->second);resource_conflicts.insert(info.package_id);
                }
                for(const auto& resource_name:std::set<std::string>{info.world_resource,info.ui_resource}) {
                    auto [owner,inserted]=resource_owners.emplace(resource_name,info.package_id);
                    if(!inserted) {resource_conflicts.insert(owner->second);resource_conflicts.insert(info.package_id);}
                }
            }
            resources.insert(info.world_resource); resources.insert(info.ui_resource);
            auto own=std::make_shared<OwnedCharacterAdapter>(); own->id=info.character_id;
            own->world=info.world_resource; own->ui=info.ui_resource; own->names=info.component_names;
            for(size_t i=0;i<own->names.size();++i) own->components.push_back({own->names[i].c_str(),info.original_counts[i]});
            own->adapter={own->id.c_str(),own->world.c_str(),own->ui.c_str(),"",false,own->components};
            std::error_code file_error;
            const auto stamp=std::filesystem::last_write_time(path,file_error);
            const auto bytes=std::filesystem::file_size(path,file_error);
            const auto canonical=path.lexically_normal();
            const std::string key=canonical.string()+"\n"+appearance+"\n"+parameters+"\n"+
                (parsed.skip_validation?"unchecked":"checked")+"\n"+
                (parsed.loading_optimization?"optimized":"normal")+"\n"+
                FileTimeIdentity(stamp)+":"+std::to_string(bytes);
            // Name every member: a positional aggregate initializer silently
            // shifted resource_id into drivers when the struct gained a field.
            EnabledMod mod;
            mod.adapter=&own->adapter; mod.package=canonical; mod.appearance=appearance;
            mod.skip_validation=parsed.skip_validation; mod.loading_optimization=parsed.loading_optimization;
            mod.selection_key=key; mod.parameters=parameters; mod.drivers=drivers;
            mod.resource_id={}; mod.package_id=info.package_id;
            parsed.enabled.push_back(std::move(mod));
            parsed.owned_adapters.push_back(std::move(own));
        }
        std::erase_if(parsed.enabled,[&](const auto& m){return resource_mode?
            resource_conflicts.contains(m.package_id):conflicts.contains(m.adapter->id);});
        for(const auto& id:resource_conflicts) parsed.diagnostics.push_back("Conflicting enabled package: "+id);
        output=std::move(parsed);return true;
    } catch(const std::exception& e) {error=e.what();return false;}
}
std::shared_ptr<const OwnedCharacterAdapter> ModRegistry::RetainAdapter(
    const CharacterAdapter* adapter) const {
    for (const auto& owner:owned_adapters)
        if (&owner->adapter==adapter) return owner;
    return {};
}
bool ParseModRegistry(std::string_view ini,const std::filesystem::path& root,ModRegistry& output,std::string& error) {
    return ParseModRegistryImpl(ini,root,output,error,false,{});
}
bool ParseResourceModRegistry(std::string_view ini,const std::filesystem::path& root,ModRegistry& output,
    std::string& error,std::string_view platform) {
    return ParseModRegistryImpl(ini,root,output,error,true,platform);
}
bool PrepareResourceRegistryUpdate(std::string_view ini,const std::filesystem::path& root,
    std::string_view platform,const ModRegistry& active,RegistryUpdatePlan& output,std::string& error) {
    RegistryUpdatePlan prepared;
    if(!ParseResourceModRegistry(ini,root,prepared.candidate,error,platform)) return false;
    if(prepared.candidate.hot_switch!=active.hot_switch ||
        prepared.candidate.skip_validation!=active.skip_validation ||
        prepared.candidate.fast_loading!=active.fast_loading) {
        error="Runtime model flags changed; restart required";return false;
    }
    for(const auto& message:prepared.candidate.diagnostics)
        if(!message.starts_with("Developer mode:")) {error=message;return false;}
    prepared.changed_resources=ChangedResourceNames(active,prepared.candidate);
    output=std::move(prepared);error.clear();return true;
}
}
