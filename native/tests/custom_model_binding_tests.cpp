// Tests the production v25 parser, palette/material preparation and commit
// rollback. No game or graphics device is touched.
#include "../modules/custom_model/module.cpp"
#include <iostream>
#include <cstdlib>
#include <sstream>
using namespace BetterEndfield::CustomModel;
namespace {
struct Fake {
    std::string name;
    std::vector<void*> array;
    void* bones=nullptr; void* poses=nullptr; void* materials=nullptr; void* mesh=nullptr;
    void* type=nullptr; void* filter=nullptr;
    bool enabled=true;
    void* parent=nullptr;
    Matrix4x4Raw matrix{};
    uint64_t scalar=0;
    std::map<int32_t,void*> textures;
    std::vector<uint8_t> data;
    int width=4, height=4, format=10;
    int32_t hide_flags=0;
};
std::vector<std::unique_ptr<Fake>> objects;
void* reject_material_renderer=nullptr;
bool rejected=false;
size_t render_sync_calls=0;
size_t roots=0;
std::map<uint32_t,void*> weak_objects;
std::set<uint32_t> strong_handles;
void* scene_renderers=nullptr;
uint32_t next_weak=10;
Fake* Make(std::string name={}) {
    auto p=std::make_unique<Fake>(); p->name=std::move(name);
    for (int i=0;i<4;++i) p->matrix.m[i*5]=1;
    auto* result=p.get(); objects.push_back(std::move(p)); return result;
}
Fake* Array(std::initializer_list<void*> values) { auto* n=Make(); n->array=values; return n; }
void Check(bool result,const char* message) { if (!result) { std::cerr<<message<<'\n'; std::exit(1); } }
void RegistryDeltaTests() {
    CharacterAdapter first{"first","world-a","ui-a","",false,{}};
    CharacterAdapter second{"second","world-b","world-b","",false,{}};
    const auto entry=[](const CharacterAdapter* adapter,std::string key) {
        EnabledMod mod;mod.adapter=adapter;mod.selection_key=std::move(key);return mod;
    };
    ModRegistry old,current;
    old.enabled.push_back(entry(&first,"same"));
    current.enabled.push_back(entry(&first,"same"));
    Check(ChangedResourceNames(old,current).empty(),"unchanged selection marked dirty");
    current.enabled.push_back(entry(&second,"added"));
    Check(ChangedResourceNames(old,current)==std::vector<std::string>{"world-b"},
        "new world/UI shared resource not discovered exactly once");
    current.enabled[0].selection_key="changed";
    Check(ChangedResourceNames(old,current)==std::vector<std::string>({"ui-a","world-a","world-b"}),
        "changed and added resources not ordered by name");
    old.enabled.push_back(entry(&second,"added"));current.enabled.erase(current.enabled.begin()+1);
    Check(ChangedResourceNames(old,current)==std::vector<std::string>({"ui-a","world-a","world-b"}),
        "disabled resource not discovered");
    std::cout<<"PASS: registry diff covers unchanged, changed, added and removed resources\n";
}
void* Scalar(uint64_t value) { auto* n=Make("scalar"); n->scalar=value; return n; }
void* BE_CALL InvokeFake(void*,const void* method,void* object,void** args,void** exception) {
    const auto key=std::string_view(static_cast<const MethodContract*>(method)->key);
    auto* n=static_cast<Fake*>(object);
    if (key=="object.get_type" || key=="type.get_element_type") return object;
    if (key=="transform.get_parent") return n->parent;
    if (key=="game_object.renderers") return object;
    if (key=="resources.find_all") return scene_renderers;
    if (key=="component.get_transform") return n->parent?n->parent:object;
    if (key=="transform.get_parent") return n->parent;
    if (key=="component.get_game_object") return object;
    if (key=="mesh.get_vertex_count") return Scalar(3);
    if (key=="mesh.get_sub_mesh_count") return Scalar(1);
    if (key=="mesh.get_index_count") return Scalar(6);
    if (key=="mesh.get_vertex_attribute_count") return Scalar(n->array.size());
    if (key=="mesh.get_vertex_attribute") return n->array.at(*static_cast<int*>(args[0]));
    if (key=="probe.material_shader") return Make("shader");
    if (key=="array.create") { auto* a=Make(); a->array.resize(*static_cast<int*>(args[1])); return a; }
    if (key=="array.get_length") return Scalar(n->array.size());
    if (key=="array.get_value") return n->array.at(*static_cast<int*>(args[0]));
    if (key=="array.set_value") { n->array.at(*static_cast<int*>(args[1]))=args[0]; return nullptr; }
    if (key=="array.clone") { auto* a=Make(); a->array=n->array; return a; }
    if (key=="object.get_name") return object;
    if (key=="object.is_alive") return Scalar(args[0]!=nullptr);
    if (key=="object.instance_id") return Scalar(static_cast<uint32_t>(reinterpret_cast<uintptr_t>(object)));
    if (key=="object.get_hide_flags") return Scalar(n->hide_flags);
    if (key=="object.set_hide_flags") { n->hide_flags=*static_cast<int32_t*>(args[0]); return nullptr; }
    if (key=="transform.local_to_world") return object;
    if (key=="mesh.get_bindposes") return n->poses;
    if (key=="component.get_component") return n->filter;
    if (key=="filter.get_shared_mesh") return n->mesh;
    if (key=="filter.set_shared_mesh") { n->mesh=args[0]; return nullptr; }
    if (key=="skinned.get_bones") return n->bones;
    if (key=="skinned.set_bones") { n->bones=args[0]; return nullptr; }
    if (key=="skinned.get_shared_mesh") return n->mesh;
    if (key=="skinned.set_shared_mesh") { n->mesh=args[0]; return nullptr; }
    if (key=="renderer.get_shared_materials") return n->materials;
    if (key=="renderer.set_shared_materials") {
        if (object==reject_material_renderer && !rejected) { rejected=true; *exception=object; }
        else n->materials=args[0];
        return nullptr;
    }
    if (key=="renderer.get_enabled") return Scalar(n->enabled);
    if (key=="renderer.set_enabled") { n->enabled=*static_cast<bool*>(args[0]); return nullptr; }
    if (key=="material.copy") { auto* donor=static_cast<Fake*>(args[0]); n->name=donor->name; n->textures=donor->textures; return nullptr; }
    if (key=="material.get_texture_property_ids") { auto* a=Array({}); for (auto [id,tex]:n->textures) a->array.push_back(Scalar(id)); return a; }
    if (key=="material.get_texture_by_id") return n->textures.at(*static_cast<int*>(args[0]));
    if (key=="material.set_texture_by_id") { n->textures[*static_cast<int*>(args[0])]=args[1]; return nullptr; }
    if (key=="texture2d.ctor") { n->width=*static_cast<int*>(args[0]); n->height=*static_cast<int*>(args[1]); n->format=*static_cast<int*>(args[2]); return nullptr; }
    if (key=="texture2d.load_raw_texture_data") { auto* raw=*static_cast<uint8_t**>(args[0]); n->data.assign(raw,raw+*static_cast<int*>(args[1])); return nullptr; }
    if (key=="texture2d.apply") return nullptr;
    if (key=="texture.get_native_texture_ptr") { ++render_sync_calls; return Scalar(1); }
    if (key=="texture.get_width") return Scalar(n->width);
    if (key=="texture.get_height") return Scalar(n->height);
    if (key=="texture.get_graphics_format") return Scalar(n->format);
    if (key=="graphics_format_utility.get_graphics_format") return Scalar(*static_cast<int*>(args[0]));
    if (key.starts_with("texture.get_")) return Scalar(0);
    if (key.starts_with("texture.set_")) return nullptr;
    if (key=="object.destroy") return nullptr;
    std::cerr<<"Unhandled fake call "<<key<<'\n'; std::exit(1);
}
void* BE_CALL UnboxFake(void*,void* value) {
    auto* n=static_cast<Fake*>(value); return n->name=="scalar"?static_cast<void*>(&n->scalar):&n->matrix;
}
int BE_CALL StringFake(void*,const void* value,char* output,size_t size) {
    const auto& text=static_cast<const Fake*>(value)->name;
    if (size<=text.size()) return 0;
    std::memcpy(output,text.c_str(),text.size()+1); return static_cast<int>(text.size());
}
uint32_t BE_CALL RootFake(void*,void* object,int) {
    const auto id=next_weak++; ++roots; weak_objects[id]=object; strong_handles.insert(id); return id;
}
void BE_CALL FreeFake(void*,uint32_t id) { --roots; strong_handles.erase(id); weak_objects.erase(id); }
uint32_t WeakNewFake(void* object,bool) { const auto id=next_weak++; weak_objects[id]=object; return id; }
void* WeakTargetFake(uint32_t id) { const auto it=weak_objects.find(id); return it==weak_objects.end()?nullptr:it->second; }
void BE_CALL WeakFreeFake(void*,uint32_t id) { if (strong_handles.erase(id)) --roots; weak_objects.erase(id); }
void* BE_CALL NewFake(void*,const void*) { return Make(); }
void StaticBindingTests() {
    struct Receiver { int state=1; bool reject_restore=false; };
    std::array<Receiver,3> receivers{{{}, {1,true}, {}}};
    Check(RestoreResource<Receiver>(receivers,
        [](Receiver& receiver) { if (receiver.reject_restore) return false; receiver.state=0; return true; },
        [](Receiver& receiver) { receiver.state=1; return true; })==CommitResult::Restored,
        "failed original restoration did not republish the prior model");
    for (const auto& receiver:receivers) Check(receiver.state==1,"partial original restoration leaked");
    receivers[1].reject_restore=false;
    Check(RestoreResource<Receiver>(receivers,
        [](Receiver& receiver) { receiver.state=0; return true; },
        [](Receiver& receiver) { receiver.state=1; return true; })==CommitResult::Committed,
        "complete original restoration failed");
    for (const auto& receiver:receivers) Check(receiver.state==0,"original restoration incomplete");
    BE_HostApiV1 host{};
    host.runtime_invoke=InvokeFake; host.object_unbox=UnboxFake;
    host.copy_managed_string=StringFake;
    host.gchandle_new=RootFake; host.gchandle_free=WeakFreeFake;
    g_host=&host;
    g_weak_new=WeakNewFake; g_weak_target=WeakTargetFake;
    auto* skinned_type=Make("SkinnedMeshRenderer");
    auto* static_type=Make("MeshRenderer");
    auto* filter_type=Make("MeshFilter");
    g_skinned_renderer_class.class_info=skinned_type;
    g_static_renderer_class.class_info=static_type;
    g_mesh_filter_class.type_object=filter_type;
    g_object_class=[](void* object)->void* { return static_cast<Fake*>(object)->type; };
    for (auto& method:g_methods) { method.method_info=&method; method.resolved=true; }
    auto* static_renderer=Make("static"); static_renderer->type=static_type;
    auto* filter=Make("filter"); static_renderer->filter=filter;
    auto* original=Make("original static mesh"); filter->mesh=original;
    auto* original_material=Array({Make("static material")});
    static_renderer->materials=original_material;
    auto* skinned=Make("skinned"); skinned->type=skinned_type;
    auto* original_skin=Make("original skin mesh"); skinned->mesh=original_skin;
    skinned->materials=Array({Make("skin material")});
    std::vector<PreparedBinding> bindings(2);
    bindings[0].renderer=static_renderer;
    bindings[0].original_mesh=original;
    bindings[0].custom_mesh=Make("replacement static mesh");
    bindings[0].original_materials=original_material;
    bindings[0].custom_materials=Array({Make("replacement static material")});
    bindings[1].renderer=skinned;
    bindings[1].original_mesh=original_skin;
    bindings[1].custom_mesh=Make("replacement skin mesh");
    bindings[1].original_materials=skinned->materials;
    bindings[1].custom_materials=Array({Make("replacement skin material")});
    reject_material_renderer=skinned; rejected=false;
    Check(CommitResource<PreparedBinding>(bindings,ApplyPreparedBinding,RestorePreparedBinding)==CommitResult::Restored,
        "static binding was not rolled back after a later failure");
    Check(filter->mesh==original && static_renderer->materials==original_material && static_renderer->enabled,
        "static MeshFilter or Renderer state was not restored");
    Check(skinned->mesh==original_skin && skinned->materials==bindings[1].original_materials,
        "skinned binding was not restored after the same failure");
    reject_material_renderer=nullptr;
    {
        auto* root=Make("world(Clone)");
        root->array.push_back(static_renderer);
        static_renderer->parent=root;
        auto* source=Make("source mesh");
        auto* replacement=Make("replacement mesh");
        auto* source_material=Make("source material");
        auto* replacement_material=Make("replacement material");
        auto* current_materials=Array({replacement_material}); current_materials->type=current_materials;
        static_renderer->materials=current_materials; filter->mesh=replacement;
        g_array_new_specific=[](void*,uintptr_t count)->void* { auto* array=Make(); array->array.resize(count); return array; };
        CompletedResource record;
        CharacterAdapter adapter{"selection-test","world","ui","",false,{}};
        auto owner=std::make_shared<OwnedCharacterAdapter>();owner->adapter=adapter;
        record.adapter_owner=owner;record.adapter=&owner->adapter;
        record.selection_key="old-selection";
        Check(record.root.Set(root),"root weak identity unavailable");
        CompletedBinding completed;
        completed.renderer_name="static";
        completed.enabled=true;
        completed.generated_mesh=true;
        Check(completed.renderer.Set(static_renderer) && completed.mesh.Set(replacement),"renderer weak identity unavailable");
        WeakObject custom_material;
        Check(custom_material.Set(replacement_material),"custom material weak identity unavailable");
        completed.materials.push_back(std::move(custom_material));
        completed.generated_materials.push_back(true);
        completed.original=std::make_unique<SavedOriginalBinding>();
        PreparedBinding original_binding;
        original_binding.renderer=static_renderer; original_binding.original_mesh=source; original_binding.custom_mesh=replacement;
        original_binding.original_materials=Array({source_material}); original_binding.original_enabled=true;
        Check(completed.original->Capture(original_binding,false),"original snapshot unavailable");
        record.bindings.push_back(std::move(completed));
        std::vector<PreparedBinding> restoration;
        Check(record.root.Get()==root,"root identity lookup failed");
        Check(record.bindings[0].renderer.Get()==static_renderer,"renderer identity lookup failed");
        Check(record.bindings[0].mesh.Get()==replacement,"custom mesh identity lookup failed");
        Check(record.bindings[0].original->mesh.Get()==source,"source mesh identity lookup failed");
        Check(GetRendererMesh(static_renderer)==replacement,"renderer current mesh unavailable");
        Check(PrepareOriginalRestoration(record,root,restoration),"owned renderer restoration rejected");
        filter->mesh=Make("foreign mesh");
        Check(!PrepareOriginalRestoration(record,root,restoration),"foreign renderer state accepted as owned");
        Check(restoration.empty(),"failed restoration exposed a stale prepared binding list");
        filter->mesh=replacement;
        g_completed.push_back(std::move(record));
        Check(!HasForeignCompletedResource(owner->adapter,"old-selection",root) &&
            HasForeignCompletedResource(owner->adapter,"new-selection",root),
            "same adapter with a new selection skipped original restoration");
        auto* clone_root=Make("world(Clone)#2");
        auto* clone_renderer=Make("static");clone_renderer->type=static_type;
        auto* clone_filter=Make("filter");clone_filter->mesh=replacement;
        clone_renderer->filter=clone_filter;clone_renderer->parent=clone_root;
        clone_renderer->materials=current_materials;clone_root->array.push_back(clone_renderer);
        Check(HasCompletedAssetLineage(owner->adapter,clone_root),
            "clone with generated mesh/material was treated as a pristine donor");
        CharacterAdapter next_generation=owner->adapter;
        Check(HasCompletedAssetLineage(next_generation,clone_root),
            "new registry adapter lost the previous generation's clone lineage");
        CharacterAdapter unrelated=next_generation;unrelated.id="unrelated";
        Check(!HasCompletedAssetLineage(unrelated,clone_root),
            "unrelated character inherited a completed model lineage");
        clone_filter->mesh=source;
        Check(HasCompletedAssetLineage(owner->adapter,clone_root),
            "material-only clone was treated as a pristine donor");
        g_completed[0].bindings[0].generated_materials[0]=false;
        Check(!HasCompletedAssetLineage(owner->adapter,clone_root),
            "unchanged shared material was treated as a generated asset");
        g_completed[0].bindings[0].generated_materials[0]=true;
        clone_filter->mesh=replacement;
        auto* clean_root=Make("world(clean)");
        auto* clean_renderer=Make("static");clean_renderer->type=static_type;
        auto* clean_filter=Make("filter");clean_filter->mesh=source;
        clean_renderer->filter=clean_filter;clean_renderer->materials=Array({source_material});
        clean_root->array.push_back(clean_renderer);
        Check(!HasCompletedAssetLineage(owner->adapter,clean_root),
            "pristine receiver was mistaken for a completed model clone");
        scene_renderers=Array({static_renderer,static_renderer,clone_renderer});
        g_renderer_class.type_object=static_type;
        ModRegistry disabled;std::vector<StaleModelRoot> discovered;
        const bool scene_scan=DiscoverStaleModelRoots(disabled,discovered);
        Check(scene_scan && discovered.size()==2 &&
            discovered[0].root->Get()==root && discovered[1].root->Get()==clone_root,
            "stale template and scene clone were not discovered exactly once");
        std::vector<PreparedStaleModelRoot> prepared_roots;
        Check(!PrepareStaleModelRestorations(disabled,prepared_roots) && prepared_roots.empty(),
            "unsupported clone preparation published a partial root plan");
        auto* full_scene=scene_renderers;
        scene_renderers=Array({static_renderer});
        Check(PrepareStaleModelRestorations(disabled,prepared_roots) &&
            prepared_roots.size()==1 && prepared_roots[0].root->Get()==root &&
            !prepared_roots[0].clone && prepared_roots[0].originals.size()==1,
            "complete template restoration plan was not prepared");
        Check(RestoreStaleModelRoots(prepared_roots)==CommitResult::Committed &&
            filter->mesh==source && static_renderer->materials!=current_materials,
            "prepared whole-scene restoration did not publish the original receiver");
        Check(ApplyPreparedBinding(prepared_roots[0].originals[0]) &&
            filter->mesh==replacement && static_renderer->materials==current_materials,
            "whole-scene test did not republish the previous model");
        Check(ApplyPreparedBinding(bindings[1]),"second root could not publish its previous model");
        PreparedStaleModelRoot second_root;
        second_root.root=std::make_unique<StrongReference>();
        Check(second_root.root->Set(clone_root),"second root strong identity unavailable");
        second_root.adapter_owner=owner;second_root.record_index=0;second_root.clone=true;
        second_root.originals.push_back(bindings[1]);
        prepared_roots.push_back(std::move(second_root));
        prepared_roots[1].clone=false;
        Check(RestoreStaleModelRoots(prepared_roots)==CommitResult::Restored &&
            filter->mesh==replacement && skinned->mesh==bindings[1].custom_mesh,
            "stale root identity changed receivers before rejection");
        prepared_roots[1].clone=true;
        reject_material_renderer=skinned;rejected=false;
        Check(RestoreStaleModelRoots(prepared_roots)==CommitResult::Restored &&
            filter->mesh==replacement && static_renderer->materials==current_materials &&
            skinned->mesh==bindings[1].custom_mesh && skinned->materials==bindings[1].custom_materials,
            "later root failure did not republish every touched old binding");
        reject_material_renderer=nullptr;
        Check(RestorePreparedBinding(bindings[1]),"second root test cleanup failed");
        prepared_roots.clear();scene_renderers=full_scene;
        discovered.clear();
        g_completed[0].bindings[0].generated_mesh=false;
        Check(DiscoverStaleModelRoots(disabled,discovered) && discovered.size()==2 &&
            discovered[0].root->Get()==root && discovered[1].root->Get()==clone_root,
            "material-only scene clone was omitted from stale discovery");
        discovered.clear();
        auto conflicting_owner=std::make_shared<OwnedCharacterAdapter>();
        conflicting_owner->adapter=unrelated;
        CompletedResource conflicting_root;
        conflicting_root.adapter_owner=conflicting_owner;conflicting_root.adapter=&conflicting_owner->adapter;
        conflicting_root.selection_key="other-selection";
        Check(conflicting_root.root.Set(root),"conflicting root identity unavailable");
        g_completed.push_back(std::move(conflicting_root));
        Check(!DiscoverStaleModelRoots(disabled,discovered) && discovered.empty(),
            "same root was silently assigned to two old owners");
        g_completed.pop_back();
        CompletedResource conflicting;
        conflicting.adapter_owner=conflicting_owner;conflicting.adapter=&conflicting_owner->adapter;
        conflicting.selection_key="other-selection";
        CompletedBinding conflicting_binding;
        WeakObject reused_material;
        Check(reused_material.Set(replacement_material),"conflicting material identity unavailable");
        conflicting_binding.materials.push_back(std::move(reused_material));
        conflicting_binding.generated_materials.push_back(true);
        conflicting.bindings.push_back(std::move(conflicting_binding));
        g_completed.push_back(std::move(conflicting));
        Check(!DiscoverStaleModelRoots(disabled,discovered) && discovered.empty(),
            "shared generated material was assigned to two old owners");
        g_completed.pop_back();
        discovered.clear();g_completed[0].bindings[0].generated_materials[0]=false;
        Check(DiscoverStaleModelRoots(disabled,discovered) && discovered.size()==1 &&
            discovered[0].root->Get()==root,
            "unchanged shared material created a stale scene clone");
        g_completed[0].bindings[0].generated_materials[0]=true;
        discovered.clear();g_completed[0].bindings[0].generated_mesh=true;
        Contract("component.get_game_object")->resolved=false;
        Check(!DiscoverStaleModelRoots(disabled,discovered) && discovered.empty(),
            "missing scene discovery contract yielded a partial result");
        Contract("component.get_game_object")->resolved=true;
        Check(IsCompletedResource(owner->adapter,root,"old-selection"),"completed root lost its selection identity");
        Check(!IsCompletedResource(owner->adapter,root,"new-selection"),"old selection was accepted as the new model");
        filter->mesh=Make("foreign mesh");
        Check(!IsCompletedResource(owner->adapter,root,"old-selection"),"changed renderer accepted by root identity alone");
        filter->mesh=replacement;
        g_enabled.store(true);
        auto* hide_flags=Contract("object.get_hide_flags");
        hide_flags->resolved=false;
        {
            ConstructionScope scope;
            OriginalRootTransition transition(0,scope);
            Check(!transition.Begin(root),"transition accepted an unpinnable previous mesh");
        }
        hide_flags->resolved=true;
        Check(filter->mesh==replacement,"unavailable pin changed the renderer");
        {
            ConstructionScope scope;
            OriginalRootTransition transition(0,scope);
            Check(transition.Begin(root),"old model could not enter transition");
            Check(filter->mesh==source,"transition did not restore original mesh");
            Check((replacement->hide_flags&kHideFlagDontUnloadUnusedAsset)!=0,
                "previous generated mesh was not retained during transition");
        }
        Check((replacement->hide_flags&kHideFlagDontUnloadUnusedAsset)==0,
            "previous generated mesh pin was not released");
        Check(filter->mesh==replacement && ArrayValue(static_renderer->materials,0)==replacement_material,
            "aborted transition did not republish previous model");
        Check(g_completed[0].root.Get()==root,"aborted transition lost active ownership");
        {
            ConstructionScope scope;
            Check(RestoreCompletedRoot(root,scope),"owned renderer original state was not restored");
            Check(scope.published,"restored resource was not published");
        }
        Check(filter->mesh==source && ArrayValue(static_renderer->materials,0)==source_material,
            "original mesh or material was not restored");
        Check(!g_completed[0].root.Get(),"restored root remained active");
        g_completed.clear();
        scene_renderers=nullptr;
        g_enabled.store(false);
        g_array_new_specific=nullptr;
    }
    Check(weak_objects.empty(),"restoration weak handles leaked");
    g_weak_new=nullptr; g_weak_target=nullptr;
    g_host=nullptr;
}
void ParserTests(const std::filesystem::path& path) {
    BemPocData bem; std::string error;
    Check(LoadBem(path,bem,error),error.c_str());
    Check(bem.header.version==1 && bem.components[0].bones.size()==2 && bem.components[0].draws.size()==2,"BEMv1 structure");
    std::ifstream f(path,std::ios::binary); std::vector<uint8_t> bytes((std::istreambuf_iterator<char>(f)),{});
    auto corrupt=bytes; corrupt.pop_back();
    Check(!ParseBem(corrupt,bem,error) && bem.components.empty(),"truncation accepted");
    corrupt=bytes; corrupt[8]=2; Check(!ParseBem(corrupt,bem,error),"unsupported major accepted");
    Check(ParseBem(bytes,bem,error),"valid package rejected after malformed data");
    Check(LoadBem(path,bem,error,{},nullptr,false,false,{},UINT64_MAX,true),error.c_str());
    Check(bem.payload_source && bem.textures.size()==2 &&
        bem.textures[0].Deferred() && bem.textures[1].Deferred(),
        "binding fixture did not use deferred texture payloads");
    BE_HostApiV1 host{};
    host.runtime_invoke=InvokeFake; host.object_unbox=UnboxFake; host.copy_managed_string=StringFake;
    host.object_new=NewFake; host.gchandle_new=RootFake; host.gchandle_free=FreeFake;
    g_host=&host; g_material_class.class_info=&host; g_texture2d_class.class_info=&host;
    g_object_class=[](void* object)->void* {
        auto* value=static_cast<Fake*>(object);return value->type?value->type:object;
    };
    auto* skinned_type=Make("SkinnedMeshRenderer type");
    g_skinned_renderer_class.class_info=skinned_type;
    g_array_new_specific=[](void*,uintptr_t size)->void* { auto* a=Make(); a->array.resize(size); return a; };
    for (auto& method:g_methods) { method.method_info=&method; method.resolved=true; }
    {
        ConstructionScope scope;
        auto* r0=Make("renderer0"); auto* r1=Make("renderer1");
        r0->type=skinned_type;r1->type=skinned_type;
        auto* m0=Make("mesh0"); auto* m1=Make("mesh1");
        auto* bone0=Make("bone0"); auto* bone1=Make("bone1");
        auto* pose0=Make(); auto* pose1=Make(); pose1->matrix.m[12]=2;
        m0->poses=Array({pose0}); m1->poses=Array({pose1});
        r0->bones=Array({bone0}); r1->bones=Array({bone1});
        r0->materials=Array({Make("material0")}); r1->materials=Array({Make("material1")});
        auto* original_texture=Make("original");
        for (auto* r:{r0,r1}) static_cast<Fake*>(ArrayValue(r->materials,0))->textures[7]=original_texture;
        r0->mesh=m0; r1->mesh=m1;
        std::vector<PreparedBinding> bindings(2);
        for (int i=0;i<2;++i) {
            auto* r=i?r1:r0; auto& b=bindings[i]; b.component_id=i; b.renderer=r;
            b.original_mesh=r->mesh; b.custom_mesh=Make("replacement"); b.original_bones=r->bones;
            b.original_materials=r->materials; b.custom_materials=r->materials;
        }
        void* poses=nullptr;
        Check(PreparePalette(bem.components[0],bindings[0],bindings,poses),"palette preparation failed");
        Check(ArrayValue(bindings[0].custom_bones,1)==bone1 && ArrayValue(poses,1)==pose1,"cross-source bone/pose pair lost");
        Check(r0->bones==bindings[0].original_bones,"palette published before commit");
        g_unsynced_upload_bytes=kRenderSyncBytes-1;
        Check(PrepareDrawMaterials(bem.components[0],bindings[0],bindings,bem),"draw materials failed");
        Check(render_sync_calls==1 && g_unsynced_upload_bytes<kRenderSyncBytes,
            "queued texture upload did not synchronize at the low-peak threshold");
        void* a=ArrayValue(bindings[0].custom_materials,0); void* b=ArrayValue(bindings[0].custom_materials,1);
        Check(a!=b && ObjectName(a)=="material0" && ObjectName(b)=="material1","donor materials not separated");
        auto* tex_a=static_cast<Fake*>(static_cast<Fake*>(a)->textures[7]);
        auto* tex_b=static_cast<Fake*>(static_cast<Fake*>(b)->textures[7]);
        Check(tex_a!=tex_b && tex_a->data[0]==1 && tex_b->data[0]==2 && original_texture->data.empty(),"same-name per-draw textures leaked across materials");
        auto repeated=bem.components[0]; repeated.draws[1]=repeated.draws[0]; repeated.material_names[1]=repeated.material_names[0];
        auto repeated_target=bindings[0];
        g_fast_loading=true;g_unsynced_upload_bytes=0;
        Check(PrepareDrawMaterials(repeated,repeated_target,bindings,bem),"repeated material preparation failed");
        Check(render_sync_calls==1,"fast loading synchronized below its upload threshold");
        g_fast_loading=false;
        auto* repeat_a=static_cast<Fake*>(ArrayValue(repeated_target.custom_materials,0));
        auto* repeat_b=static_cast<Fake*>(ArrayValue(repeated_target.custom_materials,1));
        Check(repeat_a!=repeat_b && repeat_a->textures[7]==repeat_b->textures[7],"same immutable payload/sampler not reused");
        auto bad_component=bem.components[0]; bad_component.bone_names[1]="wrong";
        auto temporary=bindings[0];
        Check(!PreparePalette(bad_component,temporary,bindings,poses),"wrong bone identity accepted");
        r1->matrix.m[12]=3; temporary=bindings[0];
        Check(!PreparePalette(bem.components[0],temporary,bindings,poses),"different mesh space accepted");
        r1->matrix.m[12]=0;
        reject_material_renderer=r1;
        Check(CommitResource<PreparedBinding>(bindings,ApplyPreparedBinding,RestorePreparedBinding)==CommitResult::Restored,"partial failure not rolled back");
        Check(r0->bones==bindings[0].original_bones && r0->mesh==m0 && r0->materials==bindings[0].original_materials,"rollback did not restore complete renderer");
        Check(CommitResource<PreparedBinding>(bindings,ApplyPreparedBinding,RestorePreparedBinding)==CommitResult::Committed,"commit rejected");
        scope.published=true;
    }
    Check(roots==0,"construction roots leaked");
    g_host=nullptr;
}
void RegistryTests(const std::filesystem::path& path) {
    auto filename=path.filename().u8string();
    const std::string config="[CustomModel]\nstandalone_lod=true\n[Mod.test]\nenabled=true\npackage="+
        std::string(reinterpret_cast<const char*>(filename.data()),filename.size())+"\nappearance=hidden\n";
    ModRegistry registry; std::string error;
    Check(ParseModRegistry(config,path.parent_path(),registry,error),error.c_str());
    ModRegistry moved=std::move(registry);
    Check(moved.standalone_lod && moved.enabled.size()==1 && moved.enabled[0].appearance=="hidden","appearance/LOD selection lost");
    Check(moved.Match("world(Clone)") && std::string(moved.Match("ui")->adapter->components[1].name)=="mesh1","owned adapter invalid after move");
    Check(ResourceBaseName("world(Clone)#27")=="world" && ResourceBaseName("world#27")=="world",
        "Android pooled resource name did not normalize");
    Check(ResourceBaseName("world(Clone)#27suffix")!="world" && ResourceBaseName("world#")!="world",
        "non-decimal pool suffix was accepted");
    ModRegistry active;RegistryUpdatePlan plan;
    Check(ParseResourceModRegistry(config,path.parent_path(),active,error,"android-arm64"),error.c_str());
    Check(PrepareResourceRegistryUpdate(config,path.parent_path(),"android-arm64",active,plan,error) &&
        plan.changed_resources.empty() && plan.candidate.enabled.size()==1,
        "unchanged candidate created a rebind request");
    Check(PrepareResourceRegistryUpdate("[CustomModel]\nstandalone_lod=true\n",path.parent_path(),
        "android-arm64",active,plan,error),error.c_str());
    Check(plan.candidate.enabled.empty() && plan.changed_resources==std::vector<std::string>({"ui","world"}),
        "disabled candidate failed to identify affected resources");
    Check(active.enabled.size()==1 && active.Match("world"),"candidate preparation changed active registry");
    Check(!PrepareResourceRegistryUpdate("[CustomModel]\nhot_switch=true\n",path.parent_path(),
        "android-arm64",active,plan,error) && error.find("restart required")!=std::string::npos,
        "latched hot-switch flag change accepted");
    Check(plan.candidate.enabled.empty() && plan.changed_resources.size()==2,
        "rejected flag change overwrote prior plan");
    Check(!PrepareResourceRegistryUpdate("[CustomModel]\n[Mod.bad]\nenabled=true\npackage=missing.bem\n",
        path.parent_path(),"android-arm64",active,plan,error) && !error.empty(),
        "missing BEM candidate accepted");
    Check(plan.changed_resources.size()==2 && active.enabled.size()==1,
        "failed candidate changed prior plan or active registry");
    Check(ParseModRegistry(config+"[Mod.duplicate]\nenabled=true\npackage="+
        std::string(reinterpret_cast<const char*>(filename.data()),filename.size())+"\n",path.parent_path(),registry,error),error.c_str());
    Check(registry.enabled.empty(),"conflicting enabled packages were not disabled");
}
void ProbeTests(const std::filesystem::path& output) {
    Check(!Contract("probe.texture_names") && !Contract("probe.texture_by_name"),"probe still requires stripped name APIs");
    Check(ProbeCanonicalPath("world(Clone)/child(Clone)")=="world/child(Clone)","clone root normalization changed children");
    NativeProbeState request;
    std::istringstream good("BE_NATIVE_PROBE_V1\ntest\nversion\n\nworld\tworld/mesh\tmesh\t6\tid:1\n");
    Check(ParseProbeRequest(good,request),"probe request rejected");
    NativeProbeState bad;
    std::istringstream traversal("BE_NATIVE_PROBE_V1\n../escape\nversion\n\nworld\tworld/mesh\tmesh\t6\tid:1\n");
    Check(!ParseProbeRequest(traversal,bad),"unsafe run ID accepted");
    bad={}; std::istringstream duplicate("BE_NATIVE_PROBE_V1\ntest\nversion\n\nworld\tworld/mesh\tmesh\t6\tid:1\nworld\tworld/mesh\tmesh\t6\tid:1\n");
    Check(!ParseProbeRequest(duplicate,bad),"duplicate probe identity accepted");
    Check(ProbeJson("a\n\"\\")=="\"a\\u000a\\\"\\\\\"","JSON escaping failed");
    std::filesystem::create_directories(output);
    g_probe={}; g_probe.run="fixture-"+std::to_string(GetTickCount64()); g_probe.manifest="version";
    g_probe.request=output/(g_probe.run+".request"); g_probe.output=output/(g_probe.run+".jsonl"); g_probe.active=true;
    std::ofstream(g_probe.request)<<"fixture";
    BE_HostApiV1 host{}; host.runtime_invoke=InvokeFake; host.object_unbox=UnboxFake;
    host.copy_managed_string=StringFake; host.gchandle_new=RootFake; host.gchandle_free=FreeFake;
    g_host=&host;
    for (auto& method:g_methods) { method.method_info=&method; method.resolved=true; }
    g_engine.resolved=true;
    g_engine.get_vertex_buffer_count=[](void*)->int32_t { return 3; };
    g_engine.get_vertex_buffer_stride=[](void*,int s)->int32_t { return s==0?16:12; };
    std::vector<Fake*> resources;
    for(const auto root:{"world","ui"}) {
        auto* asset=Make(std::string(root)+(std::string_view(root)=="ui"?"(Clone)":"")); auto* renderer=Make("mesh"); renderer->parent=asset; asset->array={renderer};
        auto* mesh=Make("mesh"); renderer->mesh=mesh;
        for(const auto a:std::array<VertexAttributeDescriptorRaw,6>{{{0,0,3,0},{1,3,4,0},{4,0,2,1},{6,3,4,1},{12,4,4,2},{13,6,4,2}}}) {
            auto* boxed=Make(); std::memcpy(&boxed->matrix,&a,sizeof(a)); mesh->array.push_back(boxed);
        }
        auto* bone=Make("bone"); bone->parent=asset; renderer->bones=Array({bone});
        auto* material=Make("material"); material->textures[7]=Make("texture"); renderer->materials=Array({material});
        g_probe.items.push_back({root,std::string(root)+"/mesh","mesh",std::string(root)+":1",6});
        resources.push_back(asset);
    }
    { ConstructionScope scope; CaptureNativeProbe(Make("unrelated")); }
    Check(!std::filesystem::exists(g_probe.output),"unrelated resource recorded");
    for(auto* asset:resources) {
        auto* renderer=static_cast<Fake*>(asset->array[0]); const auto mesh=renderer->mesh;
        { ConstructionScope scope; CaptureNativeProbe(asset); }
        Check(renderer->mesh==mesh && renderer->enabled,"probe changed resource");
        const auto size=std::filesystem::file_size(g_probe.output);
        { ConstructionScope scope; CaptureNativeProbe(asset); }
        Check(std::filesystem::file_size(g_probe.output)==size,"probe did not deduplicate");
    }
    Check(!g_probe.active && !std::filesystem::exists(g_probe.request),"probe did not disarm");
    Check(roots==0,"probe roots leaked");
    NativeProbeState sweep;
    std::istringstream sweep_request("BE_NATIVE_PROBE_SWEEP_V1\nsweep-test\nversion\n\nchr_0004_pelica_uimodel\nchr_0003_endminf_postmodel\n");
    Check(ParseProbeRequest(sweep_request,sweep) && sweep.sweep && sweep.resources.size()==2,"sweep request not parsed");
    NativeProbeState unsafe_sweep;
    std::istringstream unsafe_request("BE_NATIVE_PROBE_SWEEP_V1\nsweep-test\nversion\n\nchr_0004_pelica/../../uimodel\n");
    Check(!ParseProbeRequest(unsafe_request,unsafe_sweep),"unsafe sweep resource accepted");
    NativeProbeState persistent_request;
    std::istringstream persistent_text("BE_NATIVE_PROBE_SWEEP_PERSIST_V1\npersistent-test\nversion\n\nchr_0004_pelica_uimodel\n");
    Check(ParseProbeRequest(persistent_text,persistent_request) && persistent_request.sweep && persistent_request.persistent,"persistent request rejected");
    g_probe={};g_probe.sweep=true;g_probe.active=true;g_probe.run="sweep-"+std::to_string(GetTickCount64());g_probe.manifest="version";
    g_probe.output=output/(g_probe.run+".jsonl");g_probe.request=output/"native-probe.request";
    g_probe.resources={"chr_0004_pelica_uimodel","chr_0003_endminf_postmodel","chr_0009_unseen_uimodel"};
    for(size_t i=0;i<resources.size();++i) {
        auto* asset=resources[i]; asset->name=i?"chr_0003_endminf_postmodel":"chr_0004_pelica_uimodel";
        auto* renderer=static_cast<Fake*>(asset->array[0]); auto* mesh=static_cast<Fake*>(renderer->mesh);
        mesh->poses=Array({Make("bindpose")});
        { ConstructionScope scope;CaptureNativeProbe(asset); }
        Check(g_probe.done.contains(asset->name),"sweep did not discover renderer without offline target");
        const auto size=std::filesystem::file_size(g_probe.output);
        { ConstructionScope scope;CaptureNativeProbe(asset); }
        Check(std::filesystem::file_size(g_probe.output)==size,"sweep duplicate capture");
        Check(renderer->mesh==mesh && renderer->enabled,"sweep changed native renderer");
    }
    Check(g_probe.active,"sweep stopped before unobserved resources");
    std::ofstream(output/"native-probe.stop")<<g_probe.run;
    TickNativeProbe();Check(!g_probe.active && g_probe.sweep,"stop did not retain clean-session replacement pause");
    g_probe.persistent=true;g_probe.active=true;g_probe.done.clear();g_probe.attempts.clear();
    g_probe.run+="-persistent";g_probe.output=output/(g_probe.run+".jsonl");g_probe.resources={resources[0]->name};
    { ConstructionScope scope;CaptureNativeProbe(resources[0]); }
    Check(g_probe.done.size()==1 && g_probe.active,"persistent sweep disarmed after all resources in a process");
    Check(roots==0,"sweep roots leaked");
    g_host=nullptr;
    std::cout<<"PASS: probe request bounds, JSON escaping, read-only collection, deduplication, disarming, root release\n";
}
struct ProbeStartupContext { std::string catalog; bool armed=false; };
void ProbeDllStartup(const std::filesystem::path& dll,const std::filesystem::path& catalog) {
    auto encoded=std::filesystem::absolute(catalog).u8string();
    ProbeStartupContext context{std::string(reinterpret_cast<const char*>(encoded.data()),encoded.size())};
    BE_HostApiV1 host{}; host.abi_version=BETTER_ENDFIELD_MODULE_ABI_V1; host.context=&context;
    host.log=[](void* c,const char*,const char* message) { auto& state=*static_cast<ProbeStartupContext*>(c);
        if(std::string_view(message).starts_with("Native probe armed:")) state.armed=true;
        std::cout<<message<<'\n'; };
    host.copy_catalog_root=[](void* c,char* out,size_t size)->int { const auto& path=static_cast<ProbeStartupContext*>(c)->catalog;
        if(size<=path.size()) return 0; std::memcpy(out,path.c_str(),path.size()+1); return static_cast<int>(path.size()); };
    host.resolve_method=[](void*,const BE_MethodDescriptorV1*,BE_ResolvedMethodV1*) { return BE_Result_NotReady; };
    host.resolve_field=[](void*,const BE_FieldDescriptorV1*,BE_ResolvedFieldV1*) { return BE_Result_NotReady; };
    host.resolve_class=[](void*,const char*,const char*,const char*,BE_ResolvedClassV1*) { return BE_Result_NotReady; };
    host.create_hook=[](void*,const char*,void*,void*,void**) { Check(false,"startup check must never install hooks"); return BE_Result_Failed; };
    host.runtime_invoke=InvokeFake; host.object_unbox=UnboxFake; host.copy_managed_string=StringFake;
    host.object_new=NewFake; host.string_new=[](void*,const char* name)->void* { return Make(name); };
    host.gchandle_new=RootFake; host.gchandle_free=FreeFake;
    HMODULE library=LoadLibraryExW(std::filesystem::absolute(dll).c_str(),nullptr,LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR|LOAD_LIBRARY_SEARCH_DEFAULT_DIRS);
    Check(library!=nullptr,"probe DLL load failed");
    auto api=reinterpret_cast<BE_GetModuleApiV1Fn>(GetProcAddress(library,"BetterEndfield_GetModuleApiV1"));
    Check(api!=nullptr,"module API missing");
    Check(api()->initialize(&host)==BE_Result_ContractMismatch,"offline startup should stop at unavailable game contracts");
    Check(context.armed,"actual DLL did not arm the real request");
    FreeLibrary(library);
    std::cout<<"PASS: actual DLL export reads request via Host API before any game calls/hooks\n";
}
void ConfigurationCallbackDll(const std::filesystem::path& dll) {
    HMODULE library=LoadLibraryExW(std::filesystem::absolute(dll).c_str(),nullptr,
        LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR|LOAD_LIBRARY_SEARCH_DEFAULT_DIRS);
    Check(library!=nullptr,"configuration callback DLL load failed");
    auto exported=reinterpret_cast<BE_GetModuleApiV1Fn>(GetProcAddress(library,"BetterEndfield_GetModuleApiV1"));
    Check(exported!=nullptr,"module API missing");
    const auto* api=exported();
    Check(api->configuration_changed("[CustomModel]\npackages=ignored\n")==BE_Result_NotReady,
        "unimplemented hot switch was acknowledged");
    Check(api->configuration_changed("\nStandaloneLod=0\n  [CustomModel]  \npackages=ignored\n")==BE_Result_NotReady,
        "model update after an independent setting was acknowledged");
    Check(api->configuration_changed("StandaloneLod=1\n")==BE_Result_Ok,
        "independent LOD configuration was rejected");
    Check(api->configuration_changed("StandaloneLod=invalid\n")==BE_Result_InvalidArgument,
        "invalid LOD configuration accepted");
    Check(api->configuration_changed("StandaloneLod=0\nStandaloneLod=invalid\n")==BE_Result_InvalidArgument,
        "partially invalid LOD configuration accepted");
    std::string oversized(1024*1024+1,'x');
    Check(api->configuration_changed(oversized.c_str())==BE_Result_InvalidArgument,
        "oversized configuration accepted");
    FreeLibrary(library);
    std::cout<<"PASS: native callback rejects hot switch and preserves independent LOD settings\n";
}
}
int main(int argc,char** argv) {
    if (argc==2 && std::string_view(argv[1])=="--registry-delta") {RegistryDeltaTests();return 0;}
    if (argc==3 && std::string_view(argv[1])=="--registry-plan") {
        RegistryTests(argv[2]);
        std::cout<<"PASS: candidate registry parsing, flag latch and failure isolation\n";
        return 0;
    }
    if (argc==2 && std::string_view(argv[1])=="--static-binding") {
        StaticBindingTests();
        std::cout<<"PASS: static MeshFilter commit, rollback and completed-root restoration\n";
        return 0;
    }
    BemComponent alias_component;
    alias_component.bone_names.push_back("canonical");
    alias_component.bone_aliases_by_resource.push_back({
        std::vector<std::string>{"world_alias"}, std::vector<std::string>{"ui_alias"}});
    Check(alias_component.BoneNameMatchesForResource(0,"canonical",0) &&
        alias_component.BoneNameMatchesForResource(0,"canonical",1),"canonical bone rejected");
    Check(alias_component.BoneNameMatchesForResource(0,"world_alias",0) &&
        !alias_component.BoneNameMatchesForResource(0,"world_alias",1) &&
        alias_component.BoneNameMatchesForResource(0,"ui_alias",1) &&
        !alias_component.BoneNameMatchesForResource(0,"ui_alias",0),"resource alias leaked");
    if (argc==2 && std::string_view(argv[1])=="--resource-alias") {
        std::cout<<"PASS: world and UI bone aliases remain resource-scoped\n";
        return 0;
    }
    if(argc==3 && std::string_view(argv[1])=="--index32") {
        std::ifstream file(argv[2],std::ios::binary);
        std::vector<uint8_t> bytes((std::istreambuf_iterator<char>(file)),{});
        BemPocData bem;std::string error;
        Check(ParseBem(bytes,bem,error),error.c_str());
        const auto& c=bem.components[0];
        Check(c.info.vertex_count==65538 && c.info.index_element_size==4,"large fixture missing");
        uint32_t last=0;std::memcpy(&last,c.indices.data()+65537*4,4);Check(last==65537,"32-bit index truncated");
        static int format=-1,count=0,uploaded=0;
        g_engine.set_index_buffer_params=[](void*,int32_t n,int32_t f) { count=n;format=f; };
        g_engine.set_index_buffer_data=[](void*,const void*,int32_t,int32_t,int32_t n,int32_t element,int32_t) { Check(element==1,"byte upload changed");uploaded=n; };
        Check(UploadComponentIndices(nullptr,c),"32-bit upload rejected");
        Check(format==1 && count==static_cast<int>(c.info.index_count) && uploaded==static_cast<int>(c.indices.size()),"32-bit upload format/count mismatch");
        BemComponent small;small.info.index_count=3;small.info.index_element_size=2;small.indices.resize(6);
        Check(UploadComponentIndices(nullptr,small) && format==0 && count==3 && uploaded==6,"16-bit upload changed");
        small.indices.pop_back();Check(!UploadComponentIndices(nullptr,small),"malformed upload accepted");
        std::cout<<"PASS: uint32 parser bounds and production index upload; uint16 path preserved\n";return 0;
    }
    if(argc==3 && std::string_view(argv[1])=="--validate-package") {
        BemPocData package; std::string error;
        Check(LoadBem(argv[2],package,error),error.c_str());
        Check(package.header.version==1,"expected BEMv1 package");
        for(const auto& c:package.components)
            std::cout<<"C"<<c.info.component_id<<" vertices="<<c.info.vertex_count
                <<" indices="<<c.info.index_count<<" palette="<<c.bones.size()<<" draws="<<c.draws.size()<<'\n';
        std::cout<<"PASS: production parser accepted BEMv1 package; textures="<<package.textures.size()<<'\n';
        return 0;
    }
    if(argc==4 && std::string_view(argv[1])=="--probe-dll") { ProbeDllStartup(argv[2],argv[3]); return 0; }
    if(argc==3 && std::string_view(argv[1])=="--config-callback-dll") { ConfigurationCallbackDll(argv[2]); return 0; }
    if (argc==3 && std::string_view(argv[1])=="--probe") { ProbeTests(argv[2]); return 0; }
    Check(argc>=2,"pass synthetic BEMv1 package path");
    ParserTests(argv[1]); RegistryTests(argv[1]);
    std::cout<<"PASS: BEMv1 parser, exact donor identity, material isolation, rollback, ownership, appearance/LOD routing\n";
}
