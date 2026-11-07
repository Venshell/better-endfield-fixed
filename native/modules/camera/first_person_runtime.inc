// Included inside CameraModule's anonymous namespace, after the model scanner.
// Engine bindings are resolved by name; no Unity native object layouts are read.
namespace FpMesh = BetterEndfieldNext::FirstPersonMesh;
#if defined(__ANDROID__)
namespace FpProfiles = BetterEndfieldNext::FirstPersonProfiles;

std::string FpModelName(std::string name) {
    const auto hash=name.rfind('#');
    if(hash!=name.npos && hash+1<name.size() &&
        name.find_first_not_of("0123456789",hash+1)==name.npos) name.resize(hash);
    while(name.ends_with("(Clone)")) name.resize(name.size()-7);
    return name;
}
const FpProfiles::Profile* FpCurrentProfile() {
    return FpProfiles::LookupProfile(g_first_person.model_id);
}
void FpRefreshProfile() {
    auto* model=FindModelObject();
    const std::string name=model&&IsObjectAlive(model)?FpModelName(ObjectName(model)):std::string{};
    const std::string id=FpProfiles::LookupProfile(name)?name:std::string{};
    if(id!=g_first_person.model_id) {
        g_first_person.model_id=id;
        if(!id.empty()) Log("First person bone profile: "+id);
    }
}
uint8_t FpBoneKind(void* bone,void* model_root) {
    if(!bone||!model_root||!IsObjectAlive(bone)) return 0;
    if(bone==g_first_person.neck) return 2;
    const auto* profile=FpCurrentProfile();
    std::vector<std::string> reverse;
    for(int depth=0;bone&&depth<64;++depth) {
        if(bone==g_first_person.head && bone!=model_root) return 1;
        if(bone==model_root) {
            if(!profile) return 0;
            std::string path;
            for(auto it=reverse.rbegin();it!=reverse.rend();++it) {
                if(!path.empty()) path+='/';
                path+=*it;
            }
            return static_cast<uint8_t>(FpProfiles::MatchBonePath(profile,path).semantic);
        }
        if(!IsObjectAlive(bone)) return 0;
        if(profile) reverse.push_back(FpModelName(ObjectName(bone)));
        bone=Invoke(Contract("unity.transform.parent.get"),bone,nullptr);
    }
    return 0; // Detached bones and nested foreign actors are never inferred.
}
#endif

// Structured exception handling is a Windows-only facility and clang/AArch64 has
// no equivalent. On Android these calls run unguarded, like every other managed
// call in the Android port; the non-null check on the resolved binding is what
// keeps an unresolved contract from being called at all.
template<class Fn,class... Args>
bool FpCall(Fn fn,Args... args) {
    if(!fn) return false;
#if defined(_WIN32)
    __try {fn(args...);return true;} __except(EXCEPTION_EXECUTE_HANDLER) {return false;}
#else
    fn(args...);return true;
#endif
}
template<class T,class Fn,class... Args>
T FpValue(T fallback,Fn fn,Args... args) {
    if(!fn) return fallback;
#if defined(_WIN32)
    __try {return fn(args...);} __except(EXCEPTION_EXECUTE_HANDLER) {return fallback;}
#else
    (void)fallback;return fn(args...);
#endif
}
bool FpCopy(void* destination,const void* source,size_t size) {
    if(!destination||!source) return false;
#if defined(_WIN32)
    __try {std::memcpy(destination,source,size);return true;} __except(EXCEPTION_EXECUTE_HANDLER) {return false;}
#else
    std::memcpy(destination,source,size);return true;
#endif
}
struct FpRoot {
    void* object=nullptr; uint32_t handle=0;
    FpRoot()=default;
    explicit FpRoot(void* p):object(p) {if(p&&g_host&&g_host->gchandle_new) handle=g_host->gchandle_new(g_host->context,p,1);}
    FpRoot(const FpRoot&)=delete; FpRoot& operator=(const FpRoot&)=delete;
    FpRoot(FpRoot&& other) noexcept:object(other.object),handle(other.handle) {other.object=nullptr;other.handle=0;}
    FpRoot& operator=(FpRoot&& other) noexcept {
        if(this!=&other) {Reset();object=other.object;handle=other.handle;other.object=nullptr;other.handle=0;}return *this;
    }
    void Reset() {if(handle&&g_host)g_host->gchandle_free(g_host->context,handle);object=nullptr;handle=0;}
    ~FpRoot(){Reset();}
    explicit operator bool() const {return object&&handle;}
};
struct FpHeadBoneArray {
    size_t (*length)(void*) = nullptr;
    uint32_t (*bytes)(void*) = nullptr;
    size_t (*header_size)() = nullptr;
    size_t header = 0;
    bool attempted = false;
    bool ready = false;

    bool Init() {
        if (attempted) return ready;
        attempted = true;
        auto game = GetModuleHandleW(L"GameAssembly.dll");
        if (!game) return false;
        length = reinterpret_cast<decltype(length)>(GetProcAddress(game, "il2cpp_array_length"));
        bytes = reinterpret_cast<decltype(bytes)>(GetProcAddress(game, "il2cpp_array_get_byte_length"));
        header_size = reinterpret_cast<decltype(header_size)>(GetProcAddress(game, "il2cpp_array_object_header_size"));
        if (!length || !bytes || !header_size) return false;
        header = FpValue<size_t>(0, header_size);
        ready = header >= sizeof(void*) * 2 && header <= 128;
        return ready;
    }

    bool Read(void* renderer, std::vector<void*>& bones) {
        if (!Init()) return false;
        FpRoot array(Invoke(Contract("unity.skinned_mesh_renderer.bones.get"), renderer, nullptr));
        if (!array) return false;
        const size_t count = FpValue<size_t>(0, length, array.object);
        if (!count || count > 512 || FpValue<uint32_t>(0, bytes, array.object) != count * sizeof(void*))
            return false;
        bones.resize(count);
        return FpCopy(bones.data(), static_cast<const uint8_t*>(array.object) + header,
            count * sizeof(void*));
    }
};
FpHeadBoneArray g_fp_head_bones;

bool FpIsHeadAttachment(const HeadPartProbe& part) {
    if (!part.renderer || !g_first_person.head) return false;
#if defined(__ANDROID__)
    // Shadow proxies carry the original full silhouette. Never hide them as a
    // visible head accessory, even if their palette contains only head bones.
    for(const auto* name:{&part.object_name,&part.mesh_name}) {
        const auto lower=LowerAscii(*name);
        if(lower.find("shadowproxy")!=std::string::npos ||
           lower.find("shadow_proxy")!=std::string::npos) return false;
    }
#endif
    const auto parent=[](void* bone) {
        return Invoke(Contract("unity.transform.parent.get"),bone,nullptr);
    };
    if (part.skinned) {
        std::vector<void*> bones;
#if defined(__ANDROID__)
        if(!g_fp_head_bones.Read(part.renderer, bones)) return false;
        void* model_root=FindModelTransform();
        if(!model_root) return BetterEndfieldNext::FirstPerson::AllBonesUnderHead(
            bones,g_first_person.head,parent);
        return model_root && std::all_of(bones.begin(),bones.end(),[&](void* bone) {
            return FpBoneKind(bone,model_root)==1;
        });
#else
        return g_fp_head_bones.Read(part.renderer,bones) &&
            BetterEndfieldNext::FirstPerson::AllBonesUnderHead(bones,g_first_person.head,parent);
#endif
    }
    void* game_object = Invoke(Contract("unity.component.game_object"), part.renderer, nullptr);
    void* transform = game_object
        ? Invoke(Contract("unity.game_object.transform"), game_object, nullptr) : nullptr;
#if defined(__ANDROID__)
    void* model_root=FindModelTransform();
    return model_root ? FpBoneKind(transform,model_root)==1 :
        BetterEndfieldNext::FirstPerson::IsUnderHead(transform,g_first_person.head,parent);
#else
    return BetterEndfieldNext::FirstPerson::IsUnderHead(transform,g_first_person.head,parent);
#endif
}
#if defined(__ANDROID__)
bool FpIsPrimaryCharacterPart(const HeadPartProbe& part, void* model) {
    void* object=Invoke(Contract("unity.component.game_object"),part.renderer,nullptr);
    void* current=object ? Invoke(Contract("unity.game_object.transform"),object,nullptr) : nullptr;
    // Catalog geometry belongs to the model's direct Mesh_all subtree. Shadow
    // proxy renderers and nested summon skeletons may share names and Meshes.
    for(int depth=0;current&&current!=model&&depth<32;++depth) {
        void* parent=Invoke(Contract("unity.transform.parent.get"),current,nullptr);
        if(parent==model) return ObjectName(current)=="Mesh_all";
        current=parent;
    }
    return false;
}
#endif
struct FpAttribute {int attribute=0,format=0,dimension=0,stream=0;};
struct FpBounds {float center[3]{},extents[3]{};};
struct FpSubmesh {FpBounds bounds;int topology=0,index_start=0,index_count=0,base_vertex=0,first_vertex=0,vertex_count=0;};
static_assert(sizeof(FpSubmesh)==48);
#include "first_person_readback_runtime.inc"

struct FpEngine {
    using Resolve=void*(*)(const char*);
    using Query=int(*)(void*);
    using QueryAt=int(*)(void*,int);
    using BufferAt=void*(*)(void*,int);
    Resolve resolve=nullptr;
    Query vertex_count=nullptr,stream_count=nullptr,submesh_count=nullptr,index_format=nullptr;
    QueryAt format=nullptr,dimension=nullptr,stream=nullptr,offset=nullptr,stride=nullptr;
    bool(*has_attribute)(void*,int)=nullptr;
    void*(*clone)(void*)=nullptr;
    void(*get_submesh)(void*,int,FpSubmesh*)=nullptr;
    void(*set_submesh)(void*,int,const FpSubmesh*,int)=nullptr;
    void(*set_index_params)(void*,int,int)=nullptr;
    void(*set_index_data)(void*,const void*,int,int,int,int,int)=nullptr;
    void(*upload)(void*,bool)=nullptr;
    void*(*get_mesh)(void*)=nullptr;
    void(*set_mesh)(void*,void*)=nullptr;
    void*(*get_shadow)(void*)=nullptr;
    void(*set_shadow)(void*,void*)=nullptr;
    bool(*get_offscreen)(void*)=nullptr;
    void(*set_offscreen)(void*,bool)=nullptr;
    size_t(*array_length)(void*)=nullptr;
    uint32_t(*array_bytes)(void*)=nullptr;
    size_t(*array_header)()=nullptr;
    int32_t(*value_size)(const void*,uint32_t*)=nullptr;
    size_t header=0;
    bool attempted=false,ready=false;

    template<class T> bool Bind(T& slot,const char* name) {
        slot=reinterpret_cast<T>(FpValue<void*>(nullptr,resolve,name));
        if(!slot)Log(std::string("First person mesh: missing engine binding ")+name);
        return slot!=nullptr;
    }
    bool CheckField(const char* ns,const char* type,const char* name,int expected) {
        BE_FieldDescriptorV1 desc{"UnityEngine.CoreModule.dll",ns,type,name,nullptr};
        BE_ResolvedFieldV1 field{};
        return g_host->resolve_field(g_host->context,&desc,&field)==BE_Result_Ok && field.field_info && field.offset==expected;
    }
    bool Init() {
        if(attempted)return ready;attempted=true;
        auto game=GetModuleHandleW(L"GameAssembly.dll");if(!game)return false;
        resolve=reinterpret_cast<Resolve>(GetProcAddress(game,"il2cpp_resolve_icall"));
        array_length=reinterpret_cast<decltype(array_length)>(GetProcAddress(game,"il2cpp_array_length"));
        array_bytes=reinterpret_cast<decltype(array_bytes)>(GetProcAddress(game,"il2cpp_array_get_byte_length"));
        array_header=reinterpret_cast<decltype(array_header)>(GetProcAddress(game,"il2cpp_array_object_header_size"));
        value_size=reinterpret_cast<decltype(value_size)>(GetProcAddress(game,"il2cpp_class_value_size"));
        if(!resolve||!array_length||!array_bytes||!array_header||!value_size)return false;
        header=FpValue<size_t>(0,array_header);
        if(header<16||header>128)return false;
        BE_ResolvedFieldV1 first{};
        BE_FieldDescriptorV1 d{"UnityEngine.CoreModule.dll","UnityEngine","Vector3","x","System.Single"};
        if(g_host->resolve_field(g_host->context,&d,&first)!=BE_Result_Ok||first.offset<0)return false;
        const int boxed=first.offset;
        const char* fields[]={"<bounds>k__BackingField","<topology>k__BackingField","<indexStart>k__BackingField","<indexCount>k__BackingField","<baseVertex>k__BackingField","<firstVertex>k__BackingField","<vertexCount>k__BackingField"};
        const int offsets[]={0,24,28,32,36,40,44};
        for(int i=0;i<7;++i) if(!CheckField("UnityEngine.Rendering","SubMeshDescriptor",fields[i],boxed+offsets[i])) {
            Log("First person mesh: SubMeshDescriptor ABI mismatch.");return false;
        }
        bool ok=true;
#define FP_BIND(slot,name) ok=Bind(slot,name)&&ok
        FP_BIND(vertex_count,"UnityEngine.Mesh::get_vertexCount");
        FP_BIND(stream_count,"UnityEngine.Mesh::get_vertexBufferCount");
        FP_BIND(submesh_count,"UnityEngine.Mesh::get_subMeshCount");
        FP_BIND(index_format,"UnityEngine.Mesh::get_indexFormat");
        FP_BIND(has_attribute,"UnityEngine.Mesh::HasVertexAttribute");
        FP_BIND(format,"UnityEngine.Mesh::GetVertexAttributeFormat");
        FP_BIND(dimension,"UnityEngine.Mesh::GetVertexAttributeDimension");
        FP_BIND(stream,"UnityEngine.Mesh::GetVertexAttributeStream");
        FP_BIND(offset,"UnityEngine.Mesh::GetVertexAttributeOffset");
        FP_BIND(stride,"UnityEngine.Mesh::GetVertexBufferStride");
        FP_BIND(clone,"UnityEngine.Object::Internal_CloneSingle");
        FP_BIND(get_submesh,"UnityEngine.Mesh::GetSubMesh_Injected");
        FP_BIND(set_submesh,"UnityEngine.Mesh::SetSubMesh_Injected");
        FP_BIND(set_index_params,"UnityEngine.Mesh::SetIndexBufferParams");
        FP_BIND(set_index_data,"UnityEngine.Mesh::InternalSetIndexBufferData");
        FP_BIND(upload,"UnityEngine.Mesh::UploadMeshDataImpl");
        FP_BIND(get_mesh,"UnityEngine.SkinnedMeshRenderer::get_sharedMesh");
        FP_BIND(set_mesh,"UnityEngine.SkinnedMeshRenderer::set_sharedMesh");
        FP_BIND(get_shadow,"UnityEngine.Renderer::get_shadowProxyMesh");
        FP_BIND(set_shadow,"UnityEngine.Renderer::set_shadowProxyMesh");
        FP_BIND(get_offscreen,"UnityEngine.SkinnedMeshRenderer::get_updateWhenOffscreen");
        FP_BIND(set_offscreen,"UnityEngine.SkinnedMeshRenderer::set_updateWhenOffscreen");
#undef FP_BIND
        ready=g_fp_sync_readback.Init()&&ok;
        Log(std::string("First person mesh: named GPU readback/clone bindings ")+(ready?"ready.":"unavailable."));
        return ready;
    }
};
FpEngine g_fp_engine;

struct FpSnapshot {
    int count=0,index_format=0;
    std::vector<FpAttribute> attributes;
    std::vector<int> strides,offsets;
    std::vector<std::vector<uint8_t>> streams;
    std::vector<uint8_t> indices;
    std::vector<FpSubmesh> submeshes;
};
int FpFormatSize(int format) {const int sizes[]={4,2,1,1,2,2,1,1,2,2,4,4};return format>=0&&format<12?sizes[format]:0;}
bool FpReadMeshSnapshot(void* mesh,FpSnapshot& snapshot) {
    auto& e=g_fp_engine;
    snapshot.count=FpValue<int>(-1,e.vertex_count,mesh);
    int streams=FpValue<int>(-1,e.stream_count,mesh),subs=FpValue<int>(-1,e.submesh_count,mesh);
    snapshot.index_format=FpValue<int>(-1,e.index_format,mesh);
    if(snapshot.count<=0||snapshot.count>200000||streams<=0||streams>4||subs<=0||subs>64||snapshot.index_format<0||snapshot.index_format>1)return false;
    int managed_count=0;
    if(!GetValue(Contract("unity.mesh.vertex_count.get"),mesh,managed_count)||managed_count!=snapshot.count)return false;
    snapshot.strides.resize(streams);snapshot.streams.resize(streams);
    for(int s=0;s<streams;++s) {
        auto stride=FpValue<int>(-1,e.stride,mesh,s);
        if(stride<=0||stride>512)return false;snapshot.strides[s]=stride;
    }
    for(int attr=0;attr<14;++attr) if(FpValue<bool>(false,e.has_attribute,mesh,attr)) {
        FpAttribute a{attr,FpValue<int>(-1,e.format,mesh,attr),FpValue<int>(-1,e.dimension,mesh,attr),FpValue<int>(-1,e.stream,mesh,attr)};
        int offset=FpValue<int>(-1,e.offset,mesh,attr),size=FpFormatSize(a.format);
        if(!size||a.dimension<1||a.dimension>4||a.stream<0||a.stream>=streams||offset<0||offset+size*a.dimension>snapshot.strides[a.stream])return false;
        snapshot.attributes.push_back(a);snapshot.offsets.push_back(offset);
    }
    size_t stream_bytes=0;
    for(int s=0;s<streams;++s) {
        const size_t bytes=size_t(snapshot.count)*snapshot.strides[s];
        if(bytes>128*1024*1024-stream_bytes)return false;
        stream_bytes+=bytes;
    }
    for(int s=0;s<streams;++s)
        if(!g_fp_sync_readback.ReadMesh(mesh,s,false,size_t(snapshot.count)*snapshot.strides[s],snapshot.streams[s]))return false;
    snapshot.submeshes.resize(subs);
    size_t index_count=0;
    for(int s=0;s<subs;++s) {
        auto& sub=snapshot.submeshes[s];
        if(!FpCall(e.get_submesh,mesh,s,&sub)||sub.topology!=0||sub.index_start<0||sub.index_count<0||sub.index_count%3||sub.base_vertex<0)return false;
        index_count=std::max(index_count,size_t(sub.index_start)+size_t(sub.index_count));
    }
    if(!index_count||index_count>6000000)return false;
    return g_fp_sync_readback.ReadMesh(mesh,0,true,index_count*(snapshot.index_format?4:2),snapshot.indices);
}
bool FpReadMesh(void* mesh,FpSnapshot& snapshot) {
    if (!mesh || !g_fp_sync_readback.Init() || !g_fp_sync_readback.Drain()) return false;
    void* object=FpValue<void*>(nullptr,g_fp_engine.clone,mesh);
    if (!object || object==mesh) return false;
    FpRoot capture(object);
    FpSynchronousReadback::Lease capture_lease{g_fp_sync_readback,capture,true};
    if (!capture) return false;
    const bool ok=FpReadMeshSnapshot(capture.object,snapshot);
    return g_fp_sync_readback.Release(capture,true)&&ok;
}
template<class T> T FpLoad(const uint8_t* p) {T value{};std::memcpy(&value,p,sizeof(T));return value;}
const uint8_t* FpChannel(const FpSnapshot& s,int attr,size_t vertex,FpAttribute& out) {
    for(size_t i=0;i<s.attributes.size();++i)if(s.attributes[i].attribute==attr) {
        out=s.attributes[i];return s.streams[out.stream].data()+vertex*s.strides[out.stream]+s.offsets[i];
    }
    return nullptr;
}
bool FpDecode(const FpSnapshot& snapshot,std::vector<FpMesh::Vertex>& vertices,std::vector<FpMesh::Part>& parts) {
    vertices.resize(snapshot.count);
    for(int i=0;i<snapshot.count;++i) {
        auto& v=vertices[i];FpAttribute pos,weights,bones;
        auto p=FpChannel(snapshot,0,i,pos),w=FpChannel(snapshot,12,i,weights),b=FpChannel(snapshot,13,i,bones);
        if(!p||pos.format!=0||pos.dimension!=3||!b||bones.dimension<1||bones.dimension>4||
            (bones.format!=6&&bones.format!=8&&bones.format!=10))return false;
        if(w&&(weights.dimension!=bones.dimension||(weights.format!=0&&weights.format!=2&&weights.format!=4)))return false;
        if(!w&&bones.dimension!=1)return false;
        v.position={FpLoad<float>(p),FpLoad<float>(p+4),FpLoad<float>(p+8)};
        for(int j=0;j<bones.dimension;++j) {
            const auto q=b+j*FpFormatSize(bones.format);
            v.bone[j]=bones.format==6?*q:bones.format==8?FpLoad<uint16_t>(q):FpLoad<uint32_t>(q);
            if(!w)v.weight[j]=1;
            else {const auto qweight=w+j*FpFormatSize(weights.format);v.weight[j]=weights.format==0?FpLoad<float>(qweight):weights.format==2?double(*qweight)/255:double(FpLoad<uint16_t>(qweight))/65535;}
        }
    }
    for(const auto& sub:snapshot.submeshes) {
        FpMesh::Part part;part.indices.reserve(sub.index_count);
        for(int i=0;i<sub.index_count;++i) {
            size_t at=size_t(sub.index_start+i)*(snapshot.index_format?4:2);
            uint64_t id=(snapshot.index_format?FpLoad<uint32_t>(&snapshot.indices[at]):FpLoad<uint16_t>(&snapshot.indices[at]))+uint64_t(sub.base_vertex);
            if(id>=vertices.size())return false;part.indices.push_back(uint32_t(id));
        }
        parts.push_back(std::move(part));
    }
    return true;
}
template<class T> bool FpArray(void* array,std::vector<T>& output) {
    auto& e=g_fp_engine;
    size_t n=FpValue<size_t>(0,e.array_length,array);
    if(!n||n>4096||FpValue<uint32_t>(0,e.array_bytes,array)!=n*sizeof(T))return false;
    output.resize(n);return FpCopy(output.data(),static_cast<uint8_t*>(array)+e.header,n*sizeof(T));
}
using FpMatrix=std::array<float,16>;
bool FpBindOrigin(const FpMatrix& m,FpMesh::Point& origin) {
    FpMesh::Point a{m[0],m[1],m[2]},b{m[4],m[5],m[6]},c{m[8],m[9],m[10]},t{m[12],m[13],m[14]};
    double det=FpMesh::Dot(a,FpMesh::Cross(b,c));if(!std::isfinite(det)||std::abs(det)<1e-10)return false;
    origin={-FpMesh::Dot(t,FpMesh::Cross(b,c))/det,-FpMesh::Dot(a,FpMesh::Cross(t,c))/det,-FpMesh::Dot(a,FpMesh::Cross(b,t))/det};
    return FpMesh::Finite(origin);
}
bool FpPalette(void* renderer,void* mesh,std::vector<void*>& bones,std::vector<uint8_t>& kinds,FpMesh::Frame& frame) {
    FpRoot bone_array(Invoke(Contract("unity.skinned_mesh_renderer.bones.get"),renderer,nullptr));
    FpRoot bind_array(Invoke(Contract("unity.mesh.bindposes.get"),mesh,nullptr));
    std::vector<FpMatrix> matrices;
    if(!bone_array||!bind_array||!FpArray(bone_array.object,bones)||!FpArray(bind_array.object,matrices)||matrices.size()!=bones.size())return false;
    kinds.resize(bones.size());int head=-1,neck=-1;
#if defined(__ANDROID__)
    void* model_root=FindModelTransform();
#endif
    for(size_t i=0;i<bones.size();++i) {
        if(bones[i]==g_first_person.head)head=int(i);
        if(bones[i]==g_first_person.neck)neck=int(i);
#if defined(__ANDROID__)
        kinds[i]=FpBoneKind(bones[i],model_root);
#else
        auto current=bones[i];
        for(int depth=0;current&&depth<32;++depth) {
            if(current==g_first_person.head){kinds[i]=1;break;}
            current=Invoke(Contract("unity.transform.parent.get"),current,nullptr);
        }
        if(bones[i]==g_first_person.neck)kinds[i]=2;
#endif
    }
    if(head>=0&&neck>=0) {
        FpMesh::Point h,n;
        if(FpBindOrigin(matrices[head],h)&&FpBindOrigin(matrices[neck],n)) {
            frame.origin=n;frame.axis=h-n;frame.length=FpMesh::Length(frame.axis);
        }
    }
    return true;
}
struct FpPatch {
    FpRoot renderer,source,copy,shadow;
    std::vector<void*> bones;
    bool offscreen=false,assigned=false;
    unsigned restore_failures=0;
};
struct FpMeshSession {
    std::vector<FpPatch> patches;
    BetterEndfieldNext::FirstPerson::RetryBudget attempts;
    uint64_t last_scan=0;
    bool scanned=false,fill=false;
    bool tree_logged=false;
    float range=1;
};
FpMeshSession g_fp_mesh_session;

// ShadowCastingMode.ShadowsOnly: the renderer draws nothing in cameras but
// keeps casting shadows. This is the renderer-level equivalent of what the
// mesh patch does for matched head parts (triangles removed, shadow proxy
// kept), used wherever the mesh patch cannot reach: non-skinned renderers
// and skinned ones whose patch attempts are exhausted.
constexpr int kShadowCastingShadowsOnly=BetterEndfieldNext::FirstPerson::ShadowModeLease::kShadowsOnly;
struct FpShadowHide { FpRoot renderer; BetterEndfieldNext::FirstPerson::ShadowModeLease lease; };
std::vector<FpShadowHide> g_fp_shadow_hides;
unsigned g_fp_shadow_reports=0;
void FpShadowReport(const char* reason) {
    if(g_fp_shadow_reports<8) {++g_fp_shadow_reports;Log(std::string("First person shadow hide: ")+reason);}
}

int FpGetShadowMode(void* renderer) {
    // Resolved through the metadata contract system like every other managed
    // call in this module: the raw icall table is partial on Android (the GPU
    // patch engine proved that), while runtime_invoke contracts all resolve.
    const MethodContract* get=Contract("unity.renderer.shadow_casting_mode.get");
    if(!get||!get->resolved)return -1;
    int mode=-1;
    return GetValue(get,renderer,mode)?mode:-1;
}
bool FpSetShadowMode(void* renderer,int mode) {
    const MethodContract* set=Contract("unity.renderer.shadow_casting_mode.set");
    if(!set||!set->resolved)return false;
    int value=mode;
    void* parameters[1]{&value};
    return InvokeVoid(set,renderer,parameters);
}
void FpApplyShadowHide(const HeadPartProbe& part) {
    if(!part.renderer)return;
    for(auto& hide:g_fp_shadow_hides)if(hide.renderer.object==part.renderer) {
        const auto action=hide.lease.Observe(FpGetShadowMode(part.renderer));
        if(action==BetterEndfieldNext::FirstPerson::ShadowModeLease::Refresh::Reassert &&
            !FpSetShadowMode(part.renderer,kShadowCastingShadowsOnly)) FpShadowReport("reassertion rejected");
        if(action==BetterEndfieldNext::FirstPerson::ShadowModeLease::Refresh::Relinquish)
            FpShadowReport("another owner changed shadow mode; preserving its value");
        return;
    }
    if(g_fp_shadow_hides.size()>=static_cast<size_t>(kMaxPartScanNodes))return;
    const int original=FpGetShadowMode(part.renderer);
    FpRoot renderer(part.renderer);
    BetterEndfieldNext::FirstPerson::ShadowModeLease lease;
    if(!renderer||!lease.Acquire(original))return;
    // Acquire the GC root before writing; a failed setter may still have
    // applied the value and must remain restorable on exit.
    g_fp_shadow_hides.push_back(FpShadowHide{std::move(renderer),lease});
    if(!FpSetShadowMode(part.renderer,kShadowCastingShadowsOnly)) {
        FpShadowReport("initial shadow-only write rejected; restoration lease retained");return;
    }
    Log("First person mesh: "+part.object_name+" set to shadow-only rendering (mesh patch unavailable; shadow retained).");
}
void FpReleaseShadowHides(bool closing=false) {
    std::vector<FpShadowHide> retained;
    for(auto& hide:g_fp_shadow_hides) {
        if(closing) {hide.lease.blocked=false;hide.lease.failures=0;}
        const unsigned budget=closing?3:1;
        for(unsigned attempt=0;attempt<budget&&hide.lease.owned;++attempt) {
            const bool alive=IsObjectAlive(hide.renderer.object);
            const int current=alive?FpGetShadowMode(hide.renderer.object):-1;
            const auto action=hide.lease.PlanRestore(current,alive);
            if(action==BetterEndfieldNext::FirstPerson::ShadowModeLease::Restore::Done)break;
            if(action==BetterEndfieldNext::FirstPerson::ShadowModeLease::Restore::Retry) {
                if(!hide.lease.blocked)hide.lease.RestoreResult(false);
                continue;
            }
            const bool restored=FpSetShadowMode(hide.renderer.object,hide.lease.original)&&
                FpGetShadowMode(hide.renderer.object)==hide.lease.original;
            hide.lease.RestoreResult(restored);
        }
        if(hide.lease.owned) {
            if(closing) FpShadowReport("shutdown restore exhausted; previous shadow mode could not be verified");
            else {if(hide.lease.blocked)FpShadowReport("restore retry budget exhausted");retained.push_back(std::move(hide));}
        }
    }
    g_fp_shadow_hides=std::move(retained);
}

void FpDestroy(void* mesh) {if(mesh&&IsObjectAlive(mesh)){void* p[]={mesh};InvokeVoid(Contract("unity.object.destroy"),nullptr,p);}}
bool FpRestore(FpPatch& patch) {
    auto& e=g_fp_engine;
    if(patch.assigned&&patch.renderer&&IsObjectAlive(patch.renderer.object)) {
        void* current=FpValue<void*>(nullptr,e.get_mesh,patch.renderer.object);
        if(current==patch.copy.object) {
            if(!IsObjectAlive(patch.source.object)||!FpCall(e.set_mesh,patch.renderer.object,patch.source.object)||
               FpValue<void*>(nullptr,e.get_mesh,patch.renderer.object)!=patch.source.object)return false;
        }
        if((current==patch.copy.object||current==patch.source.object)&&
           !patch.offscreen&&FpValue<bool>(false,e.get_offscreen,patch.renderer.object)&&
           (!FpCall(e.set_offscreen,patch.renderer.object,false)||
            FpValue<bool>(true,e.get_offscreen,patch.renderer.object)))return false;
        if(FpValue<void*>(nullptr,e.get_shadow,patch.renderer.object)==patch.source.object) {
            if(patch.shadow.object&&!IsObjectAlive(patch.shadow.object))return false;
            if(!FpCall(e.set_shadow,patch.renderer.object,patch.shadow.object)||FpValue<void*>(nullptr,e.get_shadow,patch.renderer.object)!=patch.shadow.object)return false;
        }
    }
    FpDestroy(patch.copy.object);patch.copy.Reset();return true;
}
bool FpTryRestore(FpPatch& patch) {
    if (patch.restore_failures>=3) return false;
    if (FpRestore(patch)) return true;
    if (++patch.restore_failures==3)
        Log("First person mesh: restore retry budget exhausted; clone retained.");
    return false;
}
void ReleaseNeckCap() {
#if defined(__ANDROID__)
    betterendfieldnext::AndroidRestoreHeadwearFixture();
#endif
    FpReleaseShadowHides();
    std::vector<FpPatch> retained;
    for(auto& patch:g_fp_mesh_session.patches)if(!FpTryRestore(patch))retained.push_back(std::move(patch));
    g_fp_mesh_session.patches=std::move(retained);g_fp_mesh_session.attempts.Clear();g_fp_mesh_session.scanned=false;
    if(!g_fp_mesh_session.patches.empty())Log("First person mesh: restoration deferred; keeping assigned meshes alive.");
}
void FpCloseNeckCap() {
#if defined(__ANDROID__)
    for(unsigned attempt=0;attempt<3;++attempt)
        if(betterendfieldnext::AndroidRestoreHeadwearFixture())break;
    betterendfieldnext::AndroidDiscardHeadwearFixtures();
#endif
    for (auto& patch:g_fp_mesh_session.patches) patch.restore_failures=0;
    for (unsigned attempt=0;attempt<3&&!g_fp_mesh_session.patches.empty();++attempt) ReleaseNeckCap();
    if (!g_fp_mesh_session.patches.empty()) {
        Log("First person mesh: shutdown restore exhausted; original renderer state unverified.");
        for (auto& patch:g_fp_mesh_session.patches) {
            // A clone still assigned to a renderer must not be destroyed. Once
            // host APIs go away, release handles explicitly rather than leave
            // them dangling through module unload.
            if (!patch.assigned) FpDestroy(patch.copy.object);
            patch.copy.Reset();patch.shadow.Reset();patch.source.Reset();patch.renderer.Reset();
        }
        g_fp_mesh_session.patches.clear();
    }
}
bool FpSameVertices(const FpSnapshot& a,const FpSnapshot& b) {
    return a.count==b.count&&a.strides==b.strides&&a.offsets==b.offsets&&a.attributes.size()==b.attributes.size()&&
        std::equal(a.attributes.begin(),a.attributes.end(),b.attributes.begin(),[](const auto& x,const auto& y){return x.attribute==y.attribute&&x.format==y.format&&x.dimension==y.dimension&&x.stream==y.stream;})&&a.streams==b.streams;
}
#include "first_person_cap_upload.inc"

bool FpBuildPatch(const HeadPartProbe& part,FpPatch& patch,bool fill,float range) {
    auto& e=g_fp_engine;
    patch.renderer=FpRoot(part.renderer);patch.source=FpRoot(FpValue<void*>(nullptr,e.get_mesh,part.renderer));
    patch.shadow=FpRoot(FpValue<void*>(nullptr,e.get_shadow,part.renderer));
    if(!patch.renderer||!patch.source||(patch.shadow.object&&!patch.shadow))return false;
    patch.offscreen=FpValue<bool>(false,e.get_offscreen,part.renderer);
    FpMesh::Frame frame;std::vector<uint8_t> kinds;
    if(!FpPalette(part.renderer,patch.source.object,patch.bones,kinds,frame))return false;
    // A matcher hit or an upstream-style dedicated-head role collapses the whole
    // part. A body mesh is kept: it only loses the triangles whose three
    // vertices are dominated by head/neck skin, which is what closes the neck
    // opening from the body side.
    const bool whole=part.matched||part.dedicated_head;
    if(!whole&&!std::any_of(kinds.begin(),kinds.end(),[](auto k){return k!=0;}))return false;
    FpSnapshot original;
    if(!FpReadMesh(patch.source.object,original)) {Log("First person mesh: GPU capture failed for "+part.object_name);return false;}
    std::vector<FpMesh::Vertex> vertices;std::vector<FpMesh::Part> parts;
    if(!FpDecode(original,vertices,parts)){Log("First person mesh: unsupported vertex encoding for "+part.object_name);return false;}
    auto result=FpMesh::Build(vertices,parts,kinds,frame,whole,fill&&!whole,range,part.body_skin);
    if(!result.error.empty()){Log("First person mesh: "+result.error+" in "+part.object_name);return false;}
    if(!result.hidden_triangles&&!result.cap_triangles)return false;
    auto caps=FpMesh::BuildCapVertices(vertices,parts,result);
    if(caps.status!=FpMesh::CapVertexStatus::Ok) {
        Log("First person mesh: cap allocation rejected ("+std::to_string(static_cast<int>(caps.status))+") for "+part.object_name);return false;
    }
    const bool extend=!caps.source_map.empty();
    FpSnapshot expanded;
    FpBounds cap_bounds{},source_bounds{};
    if(extend) {
        std::string error;
        if(!g_fp_cap_upload.Init()) {Log("First person mesh: cap upload API/ABI unavailable for "+part.object_name);return false;}
        // Expanding vertex streams without extending each BlendShape frame
        // would leave shape data with the old vertex count. Keep this strictly
        // cap-only so ordinary whole-head removal can still use such meshes.
        if(FpValue<int>(-1,g_fp_cap_upload.blend_shape_count,patch.source.object)!=0) {
            Log("First person mesh: cap expansion requires a mesh without BlendShapes for "+part.object_name);return false;
        }
        if(!FpCall(g_fp_cap_upload.get_bounds,patch.source.object,&source_bounds)||!FpCapBoundsEqual(source_bounds,source_bounds))return false;
        if(!FpPrepareCapStreams(original,caps,source_bounds,expanded,cap_bounds,error)) {Log("First person mesh: "+error+" for "+part.object_name);return false;}
    }
    const FpSnapshot& expected=extend?expanded:original;
    patch.copy=FpRoot(FpValue<void*>(nullptr,e.clone,patch.source.object));
    if(!patch.copy||patch.copy.object==patch.source.object){
        if(patch.copy.object&&patch.copy.object!=patch.source.object)FpDestroy(patch.copy.object);
        patch.copy.Reset();return false;
    }
    if(extend&&FpValue<int>(-1,g_fp_cap_upload.blend_shape_count,patch.copy.object)!=0) {
        FpDestroy(patch.copy.object);patch.copy.Reset();return false;
    }
    std::vector<uint32_t> indices;std::vector<FpSubmesh> subs=original.submeshes;
    for(size_t s=0;s<result.parts.size();++s) {
        auto& sub=subs[s];const auto& part_indices=caps.parts[s].indices;
        sub.index_start=int(indices.size());sub.index_count=int(part_indices.size());sub.base_vertex=0;
        sub.first_vertex=0;sub.vertex_count=0;
        if(extend)sub.bounds=cap_bounds;
        if(!part_indices.empty()) {auto minmax=std::minmax_element(part_indices.begin(),part_indices.end());sub.first_vertex=int(*minmax.first);sub.vertex_count=int(*minmax.second-*minmax.first+1);}
        indices.insert(indices.end(),part_indices.begin(),part_indices.end());
    }
    int index_format=original.index_format;
    if(expected.count>65535)index_format=1;
    size_t element=index_format?4:2;
    std::vector<uint8_t> bytes(indices.size()*element);
    for(size_t i=0;i<indices.size();++i) {
        if(index_format)std::memcpy(bytes.data()+i*4,&indices[i],4);
        else {uint16_t v=uint16_t(indices[i]);std::memcpy(bytes.data()+i*2,&v,2);}
    }
    // Only the detached clone is mutated. Opaque original streams remain byte
    // identical; cap vertices copy all channels before changing normal/tangent.
    bool ok=(!extend||FpUploadCapStreams(patch.copy.object,expected))&&FpCall(e.set_index_params,patch.copy.object,int(indices.size()<=6000000-3?indices.size()+3:indices.size()-3),index_format)&&
        FpCall(e.set_index_params,patch.copy.object,int(indices.size()),index_format)&&
        FpCall(e.set_index_data,patch.copy.object,bytes.data(),0,0,int(indices.size()),int(element),0);
    ok=ok&&FpRestoreSubmeshCount(patch.copy.object,int(subs.size()));
    for(size_t s=0;ok&&s<subs.size();++s)ok=FpCall(e.set_submesh,patch.copy.object,int(s),&subs[s],10);
    if(extend)ok=ok&&FpCall(g_fp_cap_upload.set_bounds,patch.copy.object,&cap_bounds);
    ok=ok&&FpCall(e.upload,patch.copy.object,false);
    FpSnapshot verified,source_after;
    ok=ok&&FpReadMesh(patch.copy.object,verified)&&FpSameVertices(expected,verified)&&verified.index_format==index_format&&verified.indices==bytes&&
        FpReadMesh(patch.source.object,source_after)&&FpSameVertices(original,source_after)&&source_after.index_format==original.index_format&&source_after.indices==original.indices;
    if(extend)ok=ok&&FpValue<int>(-1,g_fp_cap_upload.blend_shape_count,patch.copy.object)==0&&
        FpValue<int>(-1,g_fp_cap_upload.blend_shape_count,patch.source.object)==0;
    if(extend&&ok) {
        FpBounds read_bounds{},source_bounds_after{};
        ok=FpCall(g_fp_cap_upload.get_bounds,patch.copy.object,&read_bounds)&&FpCapBoundsEqual(read_bounds,cap_bounds)&&
            FpCall(g_fp_cap_upload.get_bounds,patch.source.object,&source_bounds_after)&&FpCapBoundsEqual(source_bounds,source_bounds_after);
    }
    if(ok) {
        ok=verified.submeshes.size()==subs.size();
        for(size_t index=0;ok&&index<subs.size();++index) {
            const auto& actual=verified.submeshes[index];const auto& wanted=subs[index];
            ok=actual.index_start==wanted.index_start&&actual.index_count==wanted.index_count&&actual.base_vertex==0&&
                actual.first_vertex==wanted.first_vertex&&actual.vertex_count==wanted.vertex_count&&
                (!extend||FpCapBoundsEqual(actual.bounds,wanted.bounds));
        }
    }
    std::vector<void*> bones_now;std::vector<uint8_t> kinds_now;FpMesh::Frame unused;
    ok=ok&&FpPalette(part.renderer,patch.copy.object,bones_now,kinds_now,unused)&&bones_now==patch.bones&&
        FpValue<void*>(nullptr,e.get_mesh,part.renderer)==patch.source.object&&
        FpValue<void*>(nullptr,e.get_shadow,part.renderer)==patch.shadow.object;
    FpRoot original_bind(Invoke(Contract("unity.mesh.bindposes.get"),patch.source.object,nullptr));
    FpRoot copied_bind(Invoke(Contract("unity.mesh.bindposes.get"),patch.copy.object,nullptr));
    std::vector<FpMatrix> original_matrices,copied_matrices;
    ok=ok&&original_bind&&copied_bind&&FpArray(original_bind.object,original_matrices)&&
        FpArray(copied_bind.object,copied_matrices)&&original_matrices==copied_matrices;
    if(!ok){Log("First person mesh: clone verification failed for "+part.object_name);FpDestroy(patch.copy.object);patch.copy.Reset();return false;}
    patch.assigned=true;
    void* shadow=patch.shadow.object?patch.shadow.object:patch.source.object;
    ok=FpCall(e.set_shadow,part.renderer,shadow)&&FpValue<void*>(nullptr,e.get_shadow,part.renderer)==shadow&&
        FpCall(e.set_offscreen,part.renderer,true)&&FpCall(e.set_mesh,part.renderer,patch.copy.object)&&
        FpValue<void*>(nullptr,e.get_mesh,part.renderer)==patch.copy.object;
    if(!ok){Log("First person mesh: renderer binding failed for "+part.object_name);FpTryRestore(patch);return false;}
    Log("First person mesh: "+part.object_name+", hidden="+std::to_string(result.hidden_triangles)+
        ", cap="+std::to_string(result.cap_triangles)+", rings="+std::to_string(result.rings)+", GPU verified, original shadow retained.");
    return true;
}
void FpHideDedicatedHeadParts() {
    if (!g_first_person.head) return;
    auto model = FindModelTransform();
    if (!model) return;
    std::vector<HeadPartProbe> parts;
    std::string tree;
    int visited = 0;
    int logged = kMaxPartLogEntries;
    ScanPartNodes(model, 0, visited, parts, tree, logged);
    for (const auto& part : parts)
        if (part.renderer && part.dedicated_head) FpApplyShadowHide(part);
}
void EnsureNeckCap() {
#if defined(__ANDROID__)
    FpRefreshProfile();
#endif
    // The mesh-patch engine depends on icalls that do not all exist on every
    // platform (Android's icall table is partial). When it fails to init the
    // shadow-only fallback below still covers every matched part by itself,
    // so Init failure must not leave this function.
    if(!g_first_person_hide_head.load()||!g_first_person.head)return;
    auto& state=g_fp_mesh_session;auto& e=g_fp_engine;
    const bool readback=g_fp_engine.Init();
    const bool fill=g_first_person_fill_neck_hole.load();const float range=g_first_person_neck_plug_scale.load();
    if(state.scanned&&(fill!=state.fill||range!=state.range))ReleaseNeckCap();
    if(state.scanned&&g_first_person_pump_frames-state.last_scan<30)return;
    state.scanned=true;state.last_scan=g_first_person_pump_frames;state.fill=fill;state.range=range;
    for(auto it=state.patches.begin();it!=state.patches.end();) {
        bool stale=!IsObjectAlive(it->renderer.object)||FpValue<void*>(nullptr,e.get_mesh,it->renderer.object)!=it->copy.object;
        if(!stale) {
            FpRoot bones(Invoke(Contract("unity.skinned_mesh_renderer.bones.get"),it->renderer.object,nullptr));
            std::vector<void*> current;stale=!bones||!FpArray(bones.object,current)||current!=it->bones;
        }
        if(stale&&FpTryRestore(*it)) {
            state.attempts.Forget(it->renderer.object);
            it=state.patches.erase(it);
        } else ++it;
    }
    auto model=FindModelTransform();if(!model)return;
    std::vector<HeadPartProbe> parts;std::string tree;int visited=0;
    // Log the part tree once per session: it names every renderer and mesh in
    // the model, which is how a head part that tokens miss gets diagnosed.
    int logged=state.tree_logged?kMaxPartLogEntries+1:0;
    state.tree_logged=true;
    ScanPartNodes(model,0,visited,parts,tree,logged);
    if(!tree.empty())Log("First person part tree:"+tree);
    // Dedicated head renderers need no geometry readback to become invisible.
    // Claim their shadow-only leases before catalog patches, which may take
    // several frames on a newly selected character. Otherwise hair is visible
    // between the character switch and the fallback pass below.
    for(const auto& part:parts) if(part.renderer&&part.dedicated_head)
        FpApplyShadowHide(part);
    // Dedicated head renderers keep their existing shadow-only owner. Only
    // mixed geometry needs a local triangle patch from the Android catalog.
    for(auto& part:parts) if(part.renderer&&!part.matched&&!part.dedicated_head)
        part.head_attached=FpIsHeadAttachment(part);
#if defined(__ANDROID__)
    bool catalog_processed = false;
    if(std::getenv("BETTER_ENDFIELD_NEXT_HEADWEAR_DIRECTORY")) {
        std::vector<void*> renderers;
        for(const auto& part:parts) if(part.renderer) renderers.push_back(part.renderer);
        betterendfieldnext::AndroidPruneHeadwearFixtures(renderers.data(),renderers.size());
        for(const auto& part:parts) if(part.renderer && part.vertex_count>0 &&
            !part.matched && !part.dedicated_head && !part.head_attached &&
            FpIsPrimaryCharacterPart(part,model)) {
            auto* source=FpValue<void*>(nullptr,e.get_mesh,part.renderer);
            if(source && !betterendfieldnext::AndroidHeadwearFixtureOwns(part.renderer,source) &&
                betterendfieldnext::AndroidHeadwearFixtureAvailable(part.renderer,source) &&
                state.attempts.Try(part.renderer,source)) {
                if (!betterendfieldnext::AndroidProbeHeadwearFixture(part.renderer,source))
                    Log("First person headwear fixture: binding failed; source retained.");
                catalog_processed = true;
                state.last_scan = g_first_person_pump_frames - 30;
                break;
            }
        }
    }
#endif
    state.attempts.Prune([&](void* renderer) {
        return std::any_of(parts.begin(),parts.end(),[&](const auto& part) {
            return part.renderer==renderer;
        });
    });
    if(readback
#if defined(__ANDROID__)
        && !catalog_processed
#endif
    ) for(const auto& part:parts) {
        if(part.head_attached)continue;
        if(!part.renderer||part.vertex_count<=0)continue;
        if(std::any_of(state.patches.begin(),state.patches.end(),[&](const auto& p){return p.renderer.object==part.renderer;}))continue;
        auto source=FpValue<void*>(nullptr,e.get_mesh,part.renderer);if(!source)continue;
#if defined(__ANDROID__)
        if(betterendfieldnext::AndroidHeadwearFixtureOwns(part.renderer,source))continue;
#endif
        if(!state.attempts.Try(part.renderer,source))continue;
        FpPatch patch;
        try {
            if(FpBuildPatch(part,patch,fill,range))state.patches.push_back(std::move(patch));
            else if(patch.copy&&patch.assigned)state.patches.push_back(std::move(patch));
        } catch(const std::exception& error) {
            Log(std::string("First person mesh: capture/build aborted: ")+error.what());
            if(!FpTryRestore(patch))state.patches.push_back(std::move(patch));
        }
        // Process one renderer per frame to avoid capturing every LOD at once.
        state.last_scan=g_first_person_pump_frames-30;
        break;
    }
    // Matched parts the mesh patch cannot process fall back to shadow-only
    // rendering: not drawn in cameras, shadows kept, no mesh assumptions.
    // Skinned parts still owned by the patch pipeline (source readable,
    // retries left) are left alone; everything else matched is covered.
    for(const auto& part:parts) {
        if(!part.renderer||!(part.matched||part.dedicated_head||part.head_attached))continue;
#if defined(__ANDROID__)
        if(betterendfieldnext::AndroidHeadwearFixtureOwns(part.renderer,
            FpValue<void*>(nullptr,e.get_mesh,part.renderer)))continue;
#endif
        if(std::any_of(state.patches.begin(),state.patches.end(),
            [&](const auto& p){return p.renderer.object==part.renderer;}))continue;
        if(readback&&!part.head_attached&&part.skinned&&part.vertex_count>0) {
            auto source=FpValue<void*>(nullptr,e.get_mesh,part.renderer);
            if(source&&!state.attempts.Exhausted(part.renderer))continue;
        }
        FpApplyShadowHide(part);
    }
}
bool ApplyHeadPartHide() {g_first_person.head_hide_applied=true;return true;}
void ReleaseHeadPartHide() {
    g_first_person.head_hide_applied=false;
    ReleaseNeckCap();
}

void ExitGameFirstPersonMode() {
    void* controller=FindSnapshotCameraController();
    bool active=false;
    if(!controller||!ReadBoolField(controller,"snapshot.is_first_person",active)||!active)return;
    bool off=false;void* parameters[]{&off};
    if(InvokeVoid(Contract("snapshot.set_first_person"),controller,parameters))
        InvokeVoid(Contract("snapshot.show_char"),controller,nullptr);
}
