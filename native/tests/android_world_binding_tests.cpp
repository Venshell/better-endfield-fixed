// Drives the production Android world adapter with small managed-object stand-ins.
#include "BetterEndfield/ModuleApi.h"
#include "../modules/custom_model/bem.h"
#include "../modules/custom_model/mod_registry.h"
#include "../modules/custom_model/texture_binding_policy.h"
#include <cmath>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <type_traits>
#include <unordered_map>

namespace {
struct Matrix4x4Raw { float m[16]{}; };
struct Slot { void* texture=nullptr; };
struct Object {
    std::string name,path;
    std::vector<void*> array;
    std::vector<Slot> slots;
    void *mesh=nullptr,*bones=nullptr,*materials=nullptr,*renderers=nullptr,*transforms=nullptr;
    Matrix4x4Raw matrix{};
};
std::vector<std::unique_ptr<Object>> objects;
Object* Make(std::string name={},std::string path={}) {
    auto value=std::make_unique<Object>(); value->name=std::move(name); value->path=std::move(path);
    for (int i=0;i<4;++i) value->matrix.m[i*5]=1;
    auto* result=value.get(); objects.push_back(std::move(value)); return result;
}
Object* Array(std::initializer_list<void*> values) { auto* result=Make(); result->array=values; return result; }
Object *ui_donor=nullptr,*transform_type=Make("TransformType"),*renderer_type=Make("RendererType");
std::vector<std::string> logs;
void Check(bool value,const char* message) { if (!value) throw std::runtime_error(message); }
}
namespace betterendfield {
void* AndroidLoadUiDonor(const char*,void*&,uint32_t&) { return ui_donor; }
void AndroidReleaseUiDonor(void*&,uint32_t&) {}
}
namespace BetterEndfield::CustomModel {
struct PreparedBinding {
    uint32_t component_id=0;
    void *renderer=nullptr,*original_mesh=nullptr,*original_bones=nullptr,*original_materials=nullptr;
    void *custom_mesh=nullptr,*custom_bones=nullptr,*custom_materials=nullptr;
    bool original_enabled=true,custom_enabled=true,change_shadow=false;
    int32_t original_shadow=0,custom_shadow=0;
    void* original_shadow_mesh=nullptr;
    std::vector<std::string> bone_names;
};
std::vector<PreparedBinding> ui_sources;
BE_HostApiV1 host{};
const BE_HostApiV1* g_host=&host;
BE_ResolvedClassV1 g_skinned_renderer_class{};
struct Construction { bool failed=false; } construction;
Construction* g_construction=&construction;
void Log(const std::string& value) { logs.push_back(value); }
void* RootTemporary(void* value) { return value; }
std::string ObjectName(void* value) { return value?static_cast<Object*>(value)->name:std::string{}; }
std::string BuildTransformPath(void* value) { return value?static_cast<Object*>(value)->path:std::string{}; }
bool ResourceRelativePath(void* asset,void* component,std::string& path) {
    if (!asset || !component) return false;
    const auto& full=static_cast<Object*>(component)->path;
    const auto root=static_cast<Object*>(asset)->name+"/";
    const auto at=full.find(root);
    if (at==std::string::npos || (at && full[at-1]!='/')) return false;
    path=full.substr(at+root.size());
    return !path.empty();
}
int ArrayLength(void* value) { return value?static_cast<int>(static_cast<Object*>(value)->array.size()):0; }
void* ArrayValue(void* value,int index) { return static_cast<Object*>(value)->array.at(index); }
const char* Contract(const char* key) { return key; }
void* Invoke(const char* key,void* value,void** args) {
    auto* object=static_cast<Object*>(value); const std::string_view name=key;
    if (name=="game_object.renderers") return args[0]==transform_type?object->transforms:object->renderers;
    if (name=="skinned.get_shared_mesh") return object->mesh;
    if (name=="skinned.get_bones") return object->bones;
    if (name=="renderer.get_shared_materials") return object->materials;
    return nullptr;
}
template<class T> bool InvokeValue(const char*,void* value,void**,T& output) {
    if (!value) return false;
    if constexpr(std::is_same_v<T,Matrix4x4Raw>) output=static_cast<Object*>(value)->matrix;
    else output=0;
    return true;
}
bool GetRendererEnabled(void*,bool& enabled) { enabled=true; return true; }
bool SameMeshSpace(void* a,void* b) {
    const auto& left=static_cast<Object*>(a)->matrix;
    const auto& right=static_cast<Object*>(b)->matrix;
    for (int i=0;i<16;++i) if (std::abs(left.m[i]-right.m[i])>0.0001f) return false;
    return true;
}
std::vector<Slot> ReadMaterialTextureSlots(void* material) { return static_cast<Object*>(material)->slots; }
void* NewArrayLike(void* original,int count) {
    if (!original || count<=0 || count>256) return nullptr;
    auto* result=Make(); result->array.resize(count); return result;
}
bool SetArrayValue(void* array,int index,void* object) {
    if (!array || !object || index<0 || index>=ArrayLength(array)) return false;
    static_cast<Object*>(array)->array[index]=object; return true;
}
bool ReadCompletedAndroidDonor(const CharacterAdapter&,std::string_view,const BemPocData&,void*,std::vector<PreparedBinding>&) { return false; }
bool PrepareResource(const CharacterAdapter&,const BemPocData&,void*,std::vector<PreparedBinding>& output) {
    output=ui_sources; return true;
}
#include "../../android/app/src/main/cpp/modules/custom_model/world_resource_adapter.inc"
}
namespace {
using namespace BetterEndfield::CustomModel;
struct Fixture {
    std::array<ComponentIdentity,1> identities{{{"Body_lod0",3}}};
    CharacterAdapter adapter{"chr_test","chr_test_postmodel","chr_test_uimodel","",false,identities};
    BemPocData bem;
    Object* world=Make(adapter.world_resource);
    Object* world_renderer=Make("Body_lod1","chr_test_postmodel/Mesh_all/lod1/Body_lod1");
    Object* ui_renderer=Make("Body_lod0","chr_test_uimodel/Mesh_all/lod0/Body_lod0");
    Object* ui_bone=Make("Bone","chr_test_uimodel/Root/Bone");
    Object* world_bone=Make("Bone","chr_test_postmodel/Root/Bone");
    Object* ui_material=Make("Material");
    Object* world_material=Make("Material");
    std::vector<PreparedBinding> bindings;
    Fixture() {
        ui_donor=Make(adapter.ui_resource); logs.clear();
        world->renderers=Array({world_renderer}); world->transforms=Array({world_bone});
        world_renderer->mesh=Make("Body_lod1"); world_renderer->bones=Array({world_bone});
        world_renderer->materials=Array({world_material});
        PreparedBinding source;
        source.renderer=ui_renderer; source.original_mesh=Make("Body_lod0");
        source.original_materials=Array({ui_material}); source.original_bones=Array({ui_bone});
        source.custom_mesh=Make("Replacement"); source.custom_materials=Array({Make("Copy")});
        source.custom_bones=Array({ui_bone}); ui_sources={source};
        bem.components.resize(1); bem.components[0].bone_names={"Bone"};
    }
    void KeepTexture() {
        bem.components[0].keep_material_overrides={{0,0,1}};
        bem.components[0].keep_material_names={ui_material->name};
        bem.textures.resize(1); bem.textures[0].original_name="OriginalTexture";
        world_material->slots={{Make("OriginalTexture")}};
    }
    bool Run() { bindings.clear(); return PrepareAndroidWorldResource(adapter,"selection",bem,world,bindings); }
};
}
int main() {
    try {
        host.resolve_class=[](void*,const char*,const char*,const char*,BE_ResolvedClassV1* type)->BE_Result {
            type->type_object=transform_type; return BE_Result_Ok;
        };
        g_skinned_renderer_class.type_object=renderer_type;
        { Fixture f; Check(f.Run(),"baseline world binding failed");
          Check(ArrayValue(f.bindings[0].custom_bones,0)==f.world_bone,"world binding retained UI bone"); }
        { Fixture f; f.world_renderer->path="Scene/Party/chr_test_postmodel/Mesh_all/lod1/Body_lod1";
          f.world_bone->path="Scene/Party/chr_test_postmodel/Root/Bone";
          Check(f.Run(),"scene parent polluted resource-relative world identity"); }
        { Fixture f; f.world_renderer->path="Scene/Unrelated/Mesh_all/lod1/Body_lod1";
          Check(!f.Run(),"renderer outside world root was accepted"); }
        { Fixture f; f.KeepTexture(); f.world_material->slots.push_back(f.world_material->slots.front());
          Check(f.Run(),"shared texture in two shader properties was rejected"); }
        { Fixture f; f.KeepTexture(); f.world_material->slots.push_back({Make("OriginalTexture")});
          Check(!f.Run(),"distinct same-name textures were accepted"); }
        { Fixture f; f.KeepTexture(); f.ui_material->name="M_actor_typhoea_face_01";
          f.world_material->name="M_actor_lod_typhoea_face_01";
          f.bem.components[0].keep_material_names={f.ui_material->name}; f.world_material->slots.clear();
          Check(f.Run(),"valid LOD material counterpart was rejected");
          f.world_material->name="M_actor_lod_other_face_01";
          Check(!f.Run(),"unrelated LOD material was accepted"); }
        std::cout<<"Android world binding tests passed\n";
    } catch (const std::exception& error) {
        std::cerr<<error.what()<<'\n'; return 1;
    }
}
