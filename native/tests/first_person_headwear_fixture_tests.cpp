#include "../modules/camera/first_person_headwear_fixture.h"
#include <cassert>
#include <fstream>
#include <iterator>
using namespace BetterEndfieldNext::FirstPersonHeadwear;
namespace {
void Write(std::vector<uint8_t>& bytes, size_t offset, uint32_t value) {
    for (size_t i=0;i<4;++i) bytes[offset+i]=static_cast<uint8_t>(value>>(i*8));
}
void Word(std::vector<uint8_t>& bytes,size_t n,uint32_t value) { Write(bytes,8+n*4,value); }
void Checksum(std::vector<uint8_t>& bytes) { Word(bytes,14,Crc32(std::span<const uint8_t>(bytes).subspan(Read32(bytes,12)))); }
std::vector<uint8_t> Make(uint32_t vertices=6,uint32_t index_size=2,uint32_t draws=1,bool compact=false,bool rigid=false) {
    const std::string name="S_actor_test_cloth_01_lod0";
    std::vector<std::array<int32_t,4>> attrs{{0,0,3,0},{1,0,1,0},{4,0,2,1}};
    if (!compact) attrs.push_back({5,0,2,1});
    if (!rigid) attrs.push_back({12,4,4,2});
    attrs.push_back({13,6,4,2});
    uint32_t s1=compact?8:16,s2=rigid?4:12;
    size_t header=84+attrs.size()*16+draws*12+name.size();
    std::vector<uint8_t> b(header+vertices*(16+s1+s2)+6*index_size);
    std::copy_n("BEHWMESH",8,b.begin());
    Word(b,0,3);Word(b,1,header);Word(b,2,vertices);Word(b,3,6);Word(b,4,4);Word(b,5,1);
    Word(b,6,attrs.size());Word(b,7,3);Word(b,8,vertices*16);Word(b,9,vertices*s1);Word(b,10,vertices*s2);
    Word(b,11,16);Word(b,12,s1);Word(b,13,s2);Word(b,15,123);Word(b,16,name.size());Word(b,17,index_size);Word(b,18,draws);
    for(size_t i=0;i<attrs.size();++i) for(size_t j=0;j<4;++j) Write(b,84+i*16+j*4,attrs[i][j]);
    size_t offset=84+attrs.size()*16;
    for(uint32_t i=0;i<draws;++i) { Write(b,offset+i*12,i*3);Write(b,offset+i*12+4,draws==1?6:3); }
    std::copy(name.begin(),name.end(),b.begin()+offset+draws*12);
    Checksum(b);return b;
}
}
int main(int argc,char** argv) {
    Fixture parsed; std::string error;
    for(int arg=1;arg<argc;++arg) { std::ifstream file(argv[arg],std::ios::binary);assert(file);
        const std::vector<uint8_t> b(std::istreambuf_iterator<char>{file},{});assert(Parse(b,parsed,error)); }
    auto b=Make();const auto valid=b;assert(Parse(b,parsed,error));
    assert(parsed.draws.size()==1&&parsed.index_element_size==2);
    std::vector<std::array<int64_t,4>> source_draws{{0,0,6,0}};
    assert(MatchesDrawMetadata(parsed,source_draws,2)); // No CPU bytes needed for unreadable assets.
    assert(!MatchesDrawMetadata(parsed,source_draws,4)); // Equal counts, wrong index encoding.
    assert(!MatchesDrawMetadata(parsed,source_draws,0)); // Unknown format fails closed.
    source_draws[0][1]=1;assert(!MatchesDrawMetadata(parsed,source_draws,2));
    source_draws[0]={0,0,6,1};assert(!MatchesDrawMetadata(parsed,source_draws,2));
    source_draws[0]={1,0,6,0};assert(!MatchesDrawMetadata(parsed,source_draws,2));
    b.back()^=1;assert(!Parse(b,parsed,error));
    b=valid;Word(b,5,2);assert(Parse(b,parsed,error)); // Whole geometry may retain a body bone in its palette.
    Word(b,5,3);assert(!Parse(b,parsed,error));
    b=valid;Word(b,0,2);assert(!Parse(b,parsed,error));
    b=valid;Word(b,4,257);assert(!Parse(b,parsed,error));
    b=valid;Word(b,2,262145);assert(!Parse(b,parsed,error));
    b=valid;b[84+6*16+12]='/';assert(!Parse(b,parsed,error));
    b=valid;b[Read32(b,12)+6*44]=6;Checksum(b);assert(!Parse(b,parsed,error));
    b=valid;b[Read32(b,12)+6*32+8]=4;Checksum(b);assert(!Parse(b,parsed,error));
    b=valid;b.pop_back();assert(!Parse(b,parsed,error));
    assert(parsed.vertex_count==6); // A rejected fixture cannot publish a partial result.
    b=Make(6,2,1,true);assert(Parse(b,parsed,error));assert(parsed.strides[1]==8);
    b=Make(6,2,1,true,true);assert(Parse(b,parsed,error));assert(parsed.strides[2]==4);
    b[Read32(b,12)+6*24+1]=1;Checksum(b);assert(!Parse(b,parsed,error));
    b=Make(6,2,1,false,true);assert(Parse(b,parsed,error));
    b=Make(70000,4,2);size_t ib=Read32(b,12)+70000*44;
    Write(b,ib+12,69999);Checksum(b);assert(Parse(b,parsed,error));
    assert(parsed.vertex_count==70000&&parsed.draws.size()==2&&parsed.index_element_size==4);
    source_draws={{0,0,3,0},{0,3,3,0}};
    assert(MatchesDrawMetadata(parsed,source_draws,4));
    std::swap(source_draws[0],source_draws[1]);assert(!MatchesDrawMetadata(parsed,source_draws,4));
    auto wide=b;Write(b,ib+12,70000);Checksum(b);assert(!Parse(b,parsed,error));
    b=wide;Write(b,84+6*16+12,0);assert(!Parse(b,parsed,error)); // overlapping slot
    b=wide;Write(b,84+6*16+12,6);assert(!Parse(b,parsed,error)); // gap
    b=wide;Write(b,84+6*16+8,1);assert(!Parse(b,parsed,error)); // unsupported base vertex
    b=wide;Write(b,84+6*16+4,2);assert(!Parse(b,parsed,error)); // non triangle count
    b=wide;Write(b,84+6*16+16,0xffffffffu);assert(!Parse(b,parsed,error)); // overflowing range
    b=wide;Word(b,18,9);assert(!Parse(b,parsed,error));
    b=wide;Word(b,17,1);assert(!Parse(b,parsed,error));
    b=wide;Word(b,3,1200003);assert(!Parse(b,parsed,error));
    b.assign(16*1024*1024+1,0);assert(!Parse(b,parsed,error));
    assert(PrimaryRendererName("S_actor_fur_lod1", "S_actor_fur_lod1_11"));
    assert(PrimaryRendererName("S_actor_fur_lod1", "S_actor_fur_lod1"));
    assert(!PrimaryRendererName("shadowProxyMobile", "S_actor_fur_lod1_11"));
    assert(!PrimaryRendererName("S_actor_fur_lod1", "S_actor_fur_lod1_a"));
    assert(!PrimaryRendererName("S_actor_fur_lod1", "S_actor_fur_lod1_"));
}
