// Exercise the actual stream-preparation implementation without starting Unity.
// Including the module gives this test access to its private conversion seam;
// no production conversion logic or engine-call mocks are duplicated here.
#include "../modules/camera/module.cpp"
#include <iostream>
#include <stdexcept>
using namespace BetterEndfieldNext::CameraModule;
void Check(bool value,const char* message){if(!value)throw std::runtime_error(message);}
int main(){try{
    FpSnapshot original;
    original.count=3;original.strides={44};original.offsets={0,12,24,40};
    original.attributes={{0,0,3,0},{1,0,3,0},{2,0,4,0},{13,10,1,0}};
    original.streams={std::vector<uint8_t>(132), {11,12,13,14,21,22,23,24,31,32,33,34}};
    original.strides.push_back(4);
    original.offsets.push_back(0);
    original.attributes.push_back({3,6,4,1});
    for(size_t i=0;i<3;++i){
        const float values[]{float(i),0,0,0,1,0,1,0,1,-1};
        std::memcpy(original.streams[0].data()+i*44,values,sizeof(values));
        const uint32_t skin=uint32_t(123+i);std::memcpy(original.streams[0].data()+i*44+40,&skin,4);
    }
    FpMesh::CapVertexResult caps;
    caps.status=FpMesh::CapVertexStatus::Ok;caps.source_map={2,0,1};caps.normals={{0,0,1},{0,0,1},{0,0,1}};
    caps.bounds.minimum={-1,-2,-3};caps.bounds.maximum={1,2,3};
    FpSnapshot expanded;FpBounds bounds{};std::string error;
    const FpBounds source_bounds{{0,0,0},{10,20,30}};
    Check(FpPrepareCapStreams(original,caps,source_bounds,expanded,bounds,error),"supported cap preparation failed");
    Check(expanded.count==6&&expanded.streams[0].size()==264,"cap stream size/count mismatch");
    Check(std::equal(original.streams[0].begin(),original.streams[0].end(),expanded.streams[0].begin()),"source prefix modified");
    for(size_t i=0;i<3;++i){
        auto* p=expanded.streams[0].data()+(3+i)*44;
        Check(FpLoad<uint32_t>(p+40)==123+caps.source_map[i],"skin attributes not copied exactly");
        Check(FpLoad<float>(p)==float(caps.source_map[i]),"position source mapping broken");
        Check(FpLoad<float>(p+12)==0&&FpLoad<float>(p+16)==0&&FpLoad<float>(p+20)==1,"cap normal incorrect");
        Check(FpLoad<float>(p+24)==1&&FpLoad<float>(p+28)==0&&FpLoad<float>(p+32)==0&&FpLoad<float>(p+36)==-1,"tangent projection/handedness incorrect");
    }
    Check(expanded.streams[1] == std::vector<uint8_t>({11,12,13,14,21,22,23,24,31,32,33,34,
        31,32,33,34,11,12,13,14,21,22,23,24}), "secondary stream bytes/source order changed");
    Check(bounds.center[0]==0&&bounds.extents[0]==10&&bounds.extents[1]==20&&bounds.extents[2]==30,
        "cap must not shrink the source mesh bounds");
    auto outside=caps;outside.bounds.minimum={-15,-2,-3};
    Check(FpPrepareCapStreams(original,outside,source_bounds,expanded,bounds,error)&&
        bounds.center[0]==-2.5f&&bounds.extents[0]==12.5f,
        "cap outside the source must expand their union");
    auto invalid_bounds=source_bounds;invalid_bounds.extents[0]=-1;
    Check(!FpPrepareCapStreams(original,caps,invalid_bounds,expanded,bounds,error),
        "invalid source bounds must not be uploaded");
    outside.bounds.minimum.x=2;
    Check(!FpPrepareCapStreams(original,outside,source_bounds,expanded,bounds,error),
        "reversed cap bounds must not be hidden by the source union");
    auto no_tangent=original;
    no_tangent.attributes.erase(no_tangent.attributes.begin()+2);
    no_tangent.offsets.erase(no_tangent.offsets.begin()+2);
    Check(FpPrepareCapStreams(no_tangent,caps,source_bounds,expanded,bounds,error), "missing optional tangent must remain supported");
    auto unsupported=original;unsupported.attributes[1].format=2;
    Check(!FpPrepareCapStreams(unsupported,caps,source_bounds,expanded,bounds,error),"packed normal incorrectly accepted");
    unsupported=original;unsupported.attributes[2].format=1;
    Check(!FpPrepareCapStreams(unsupported,caps,source_bounds,expanded,bounds,error),"packed tangent incorrectly accepted");
    auto parallel=original;float zero=0;
    for(size_t i=0;i<3;++i)std::memcpy(parallel.streams[0].data()+i*44+24,&zero,4);
    Check(!FpPrepareCapStreams(parallel,caps,source_bounds,expanded,bounds,error),"degenerate tangent projection accepted");
    unsupported=original;
    unsupported.strides={512,512,512,512};
    unsupported.streams.assign(4,std::vector<uint8_t>(3*512));
    caps.source_map.resize(100000);
    caps.normals.resize(100000,{0,0,1});
    Check(!FpPrepareCapStreams(unsupported,caps,source_bounds,expanded,bounds,error),"aggregate 128MiB budget bypassed");
    std::cout<<"cap_upload: exact stream prefix/skin/source copy, normals, tangent projection/handedness, Bounds, encoding rejection and total budget passed\n";
    return 0;
}catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}}
