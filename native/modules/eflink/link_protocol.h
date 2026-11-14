// Ported from EF Link (AGPL-3.0).
// Upstream: https://github.com/Venshell/EFLink @ 956a0d5d4b900f17ba06d7b30bc15d24770eb3a1 (1.6.6),
// fork of https://github.com/guchang233/EFLink.
// Wire layout is unchanged. Platform calls live in eflink_platform.h.
#pragma once
// EF Link wire format. Little-endian, bounds-checked, no game dependencies.
//
// Transport:
//   UDP discovery port  BEACON / DISCOVER broadcasts between all instances on the LAN.
//   TCP game port       reliable, ordered frames: join handshake, player info, chat,
//                       reliable module data. Frame = u32 length | u8 type | payload.
//   UDP game port       unreliable datagrams: player state and unreliable module data.
// The room host is the hub: every client talks only to the host, which relays.
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <stdexcept>
#include <string>
#include <string_view>
#include <type_traits>
#include <vector>

namespace eflink::proto {
constexpr std::uint32_t kMagic=0x4B4C4645u; // "EFLK"
constexpr std::uint16_t kProtocol=1;
constexpr std::uint16_t kProtocolSecure=2;
constexpr std::uint16_t kDefaultPort=47615;
constexpr std::uint16_t kDiscoveryPort=47616;
constexpr std::size_t kMaxDatagram=1200;
constexpr std::size_t kMaxFrame=1u<<20;
constexpr std::size_t kMaxReliableData=256u*1024;
constexpr std::size_t kMaxUnreliableData=1024;
constexpr unsigned kMaxBones=96;
constexpr unsigned kMaxPlayers=16;
constexpr std::uint16_t kHostId=1;
constexpr std::uint16_t kEveryone=0xFFFF;

enum class Udp:std::uint8_t {Beacon=1,Discover=2,Hello=3,Welcome=4,State=5,Data=6,Ping=7,Pong=8};
// State: a player state over TCP when UDP cannot get through (TCP-only port forwarding).
enum class Tcp:std::uint8_t {Hello=1,Welcome=2,Reject=3,PlayerInfo=4,PlayerLeave=5,Chat=6,Data=7,Bye=8,KeepAlive=9,State=10,Challenge=11};

inline std::uint32_t fnv32(std::string_view text) {
    std::uint32_t hash=2166136261u;for(unsigned char c:text) {hash^=c;hash*=16777619u;}return hash;
}
inline std::uint64_t fnv64(std::string_view text) {
    std::uint64_t hash=14695981039346656037ull;for(unsigned char c:text) {hash^=c;hash*=1099511628211ull;}return hash;
}
// Protocol 1 LAN lock, kept so a Windows EF Link 1.6.6 client can enter the same room.
// The 8 bytes are stable for a given password, so they can be replayed and guessed offline.
inline std::uint64_t passwordProof(std::uint32_t roomId,std::string_view password) {
    return password.empty()?0:fnv64(std::to_string(roomId)+":"+std::string(password));
}

inline void sha256(const unsigned char* message,std::size_t length,unsigned char out[32]) {
    constexpr std::uint32_t k[64]={
        0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
        0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
        0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
        0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
        0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
        0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
        0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
        0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2};
    auto rotr=[](std::uint32_t x,std::uint32_t n){return (x>>n)|(x<<(32-n));};
    std::uint32_t h0=0x6a09e667,h1=0xbb67ae85,h2=0x3c6ef372,h3=0xa54ff53a,h4=0x510e527f,h5=0x9b05688c,h6=0x1f83d9ab,h7=0x5be0cd19;
    const auto bitLength=static_cast<std::uint64_t>(length)*8;
    const std::size_t padded=((length+9+63)/64)*64;
    std::vector<unsigned char> block(padded,0);
    if(length) std::memcpy(block.data(),message,length);
    block[length]=0x80;
    for(int i=0;i<8;++i) block[padded-1-static_cast<std::size_t>(i)]=static_cast<unsigned char>(bitLength>>(8*i));
    for(std::size_t offset=0;offset<padded;offset+=64) {
        std::uint32_t w[64]{};
        for(int i=0;i<16;++i) {
            const auto* p=block.data()+offset+static_cast<std::size_t>(i)*4;
            w[i]=(std::uint32_t(p[0])<<24)|(std::uint32_t(p[1])<<16)|(std::uint32_t(p[2])<<8)|p[3];
        }
        for(int i=16;i<64;++i) {
            const auto s0=rotr(w[i-15],7)^rotr(w[i-15],18)^(w[i-15]>>3);
            const auto s1=rotr(w[i-2],17)^rotr(w[i-2],19)^(w[i-2]>>10);
            w[i]=w[i-16]+s0+w[i-7]+s1;
        }
        auto a=h0,b=h1,c=h2,d=h3,e=h4,f=h5,g=h6,h=h7;
        for(int i=0;i<64;++i) {
            const auto S1=rotr(e,6)^rotr(e,11)^rotr(e,25);
            const auto ch=(e&f)^((~e)&g);
            const auto temp1=h+S1+ch+k[i]+w[i];
            const auto S0=rotr(a,2)^rotr(a,13)^rotr(a,22);
            const auto maj=(a&b)^(a&c)^(b&c);
            h=g;g=f;f=e;e=d+temp1;d=c;c=b;b=a;a=temp1+S0+maj;
        }
        h0+=a;h1+=b;h2+=c;h3+=d;h4+=e;h5+=f;h6+=g;h7+=h;
    }
    const std::uint32_t words[8]={h0,h1,h2,h3,h4,h5,h6,h7};
    for(int i=0;i<8;++i) {
        out[i*4]=static_cast<unsigned char>(words[i]>>24);
        out[i*4+1]=static_cast<unsigned char>(words[i]>>16);
        out[i*4+2]=static_cast<unsigned char>(words[i]>>8);
        out[i*4+3]=static_cast<unsigned char>(words[i]);
    }
}

inline void hmacSha256(const unsigned char* key,std::size_t keyLen,const unsigned char* data,std::size_t dataLen,unsigned char out[32]) {
    unsigned char keyBlock[64]{};
    if(keyLen>64) sha256(key,keyLen,keyBlock);
    else if(keyLen) std::memcpy(keyBlock,key,keyLen);
    unsigned char ipad[64],opad[64];
    for(int i=0;i<64;++i) {ipad[i]=static_cast<unsigned char>(keyBlock[i]^0x36);opad[i]=static_cast<unsigned char>(keyBlock[i]^0x5c);}
    std::vector<unsigned char> inner(64+dataLen);
    std::memcpy(inner.data(),ipad,64);
    if(dataLen) std::memcpy(inner.data()+64,data,dataLen);
    unsigned char innerHash[32];
    sha256(inner.data(),inner.size(),innerHash);
    unsigned char outer[96];
    std::memcpy(outer,opad,64);
    std::memcpy(outer+64,innerHash,32);
    sha256(outer,sizeof outer,out);
}

// Protocol 2 room lock. The host supplies a fresh nonce; the mac covers that nonce and the lobby room.
inline void roomProof(std::string_view password,const unsigned char nonce[32],std::uint32_t roomId,unsigned char out[32]) {
    unsigned char message[32+4+14];
    std::memcpy(message,nonce,32);
    message[32]=static_cast<unsigned char>(roomId);
    message[33]=static_cast<unsigned char>(roomId>>8);
    message[34]=static_cast<unsigned char>(roomId>>16);
    message[35]=static_cast<unsigned char>(roomId>>24);
    std::memcpy(message+36,"eflink-join-v2",14);
    const auto* key=reinterpret_cast<const unsigned char*>(password.data());
    hmacSha256(key,password.size(),message,sizeof message,out);
}

inline bool sameBytes(const unsigned char* left,const unsigned char* right,std::size_t size) {
    unsigned char diff=0;
    for(std::size_t i=0;i<size;++i) diff=static_cast<unsigned char>(diff|(left[i]^right[i]));
    return diff==0;
}

class Writer {
public:
    std::vector<unsigned char> bytes;
    template<class T> Writer& put(T value) {
        static_assert(std::is_trivially_copyable_v<T>);
        const auto* raw=reinterpret_cast<const unsigned char*>(&value);bytes.insert(bytes.end(),raw,raw+sizeof(T));return *this;
    }
    Writer& u8(std::uint8_t v) {return put(v);}
    Writer& u16(std::uint16_t v) {return put(v);}
    Writer& u32(std::uint32_t v) {return put(v);}
    Writer& u64(std::uint64_t v) {return put(v);}
    Writer& f32(float v) {return put(v);}
    Writer& str(std::string_view text,std::size_t limit=255) {
        if(text.size()>limit) {
            // Never cut a UTF-8 sequence in half: step back to the start of the cut sequence.
            std::size_t n=limit;while(n>0&&(static_cast<unsigned char>(text[n])&0xC0)==0x80) --n;
            text=text.substr(0,n);
        }
        if(limit<=255) u8(std::uint8_t(text.size()));else u16(std::uint16_t(text.size()));
        bytes.insert(bytes.end(),text.begin(),text.end());return *this;
    }
    Writer& raw(const void* data,std::size_t size) {
        if(!size) return *this;
        const auto* p=static_cast<const unsigned char*>(data);bytes.insert(bytes.end(),p,p+size);return *this;
    }
};
class Reader {
    const unsigned char* data;std::size_t size,at=0;
public:
    Reader(const void* d,std::size_t n):data(static_cast<const unsigned char*>(d)),size(n) {}
    template<class T> T get() {
        if(size-at<sizeof(T)) throw std::runtime_error("truncated message");
        T value;std::memcpy(&value,data+at,sizeof(T));at+=sizeof(T);return value;
    }
    std::uint8_t u8() {return get<std::uint8_t>();}
    std::uint16_t u16() {return get<std::uint16_t>();}
    std::uint32_t u32() {return get<std::uint32_t>();}
    std::uint64_t u64() {return get<std::uint64_t>();}
    float f32() {return get<float>();}
    std::string str(std::size_t limit=255) {
        std::size_t n=limit<=255?u8():u16();
        if(n>limit||size-at<n) throw std::runtime_error("truncated string");
        std::string text(reinterpret_cast<const char*>(data+at),n);at+=n;return text;
    }
    const unsigned char* take(std::size_t n) {
        if(size-at<n) throw std::runtime_error("truncated payload");
        const auto* p=data+at;at+=n;return p;
    }
    std::size_t remaining() const {return size-at;}
};

inline Writer udpHeader(Udp type,std::uint32_t session) {
    Writer w;w.u32(kMagic).u16(kProtocol).u8(std::uint8_t(type)).u8(0).u32(session);return w;
}
struct UdpHeader {Udp type;std::uint32_t session;};
inline UdpHeader readUdpHeader(Reader& r) {
    if(r.u32()!=kMagic) throw std::runtime_error("foreign datagram");
    if(r.u16()!=kProtocol) throw std::runtime_error("protocol mismatch");
    UdpHeader h{Udp(r.u8()),0};r.u8();h.session=r.u32();return h;
}

struct Beacon {
    std::uint32_t roomId=0;
    std::uint16_t gamePort=kDefaultPort;
    std::uint8_t players=0,maxPlayers=0;
    bool password=false;
    std::string roomName,hostName,mapId,version;
};
inline std::vector<unsigned char> encode(const Beacon& b) {
    auto w=udpHeader(Udp::Beacon,b.roomId);
    w.u16(b.gamePort).u8(b.players).u8(b.maxPlayers).u8(b.password?1:0).str(b.roomName,64).str(b.hostName,64).str(b.mapId,64).str(b.version,32);
    return std::move(w.bytes);
}
inline Beacon decodeBeacon(Reader& r,std::uint32_t session) {
    Beacon b;b.roomId=session;b.gamePort=r.u16();b.players=r.u8();b.maxPlayers=r.u8();b.password=r.u8()!=0;
    b.roomName=r.str(64);b.hostName=r.str(64);b.mapId=r.str(64);b.version=r.str(32);return b;
}

// Everything another client needs to draw this player. Sent when it changes.
struct PlayerInfo {
    std::uint16_t id=0;
    std::string name,modelPath,mapId,levelId;
    std::uint16_t skeleton=0;              // increments whenever bones changes
    std::vector<std::string> bones;        // names, in the order of PlayerState::bones
    std::string weaponModel;               // equipped weapon's model path (empty: none / unknown)
};
inline void write(Writer& w,const PlayerInfo& p) {
    w.u16(p.id).str(p.name,64).str(p.modelPath,512).str(p.mapId,64).str(p.levelId,64).u16(p.skeleton);
    const auto count=std::uint8_t(std::min<std::size_t>(p.bones.size(),kMaxBones));w.u8(count);
    for(unsigned i=0;i<count;++i) w.str(p.bones[i],96);
    w.str(p.weaponModel,512);
}
inline PlayerInfo readPlayerInfo(Reader& r) {
    PlayerInfo p;p.id=r.u16();p.name=r.str(64);p.modelPath=r.str(512);p.mapId=r.str(64);p.levelId=r.str(64);p.skeleton=r.u16();
    const auto count=r.u8();if(count>kMaxBones) throw std::runtime_error("too many bones");
    for(unsigned i=0;i<count;++i) p.bones.push_back(r.str(96));
    p.weaponModel=r.str(512);
    return p;
}

enum StateFlags:std::uint8_t {StateValid=1,StateAlive=2,StateCinematic=4,StateHidden=8};
// One sampled pose of a player's model, sent unreliably about twenty times a second.
struct PlayerState {
    std::uint16_t id=0;
    std::uint32_t sequence=0,timeMs=0;
    std::uint8_t flags=0;
    std::uint32_t mapHash=0;
    std::uint16_t skeleton=0;
    float position[3]{},rotation[4]{0,0,0,1};
    float rootOffset[3]{};                 // local position of the first bone (hips)
    std::vector<std::int16_t> bones;       // 4 per bone: quantized local rotation
};
inline std::int16_t quantize(float v) {
    if(!(v==v)) return 0;
    v=v<-1?-1:v>1?1:v;return std::int16_t(v*32767.f);
}
inline float dequantize(std::int16_t v) {return float(v)/32767.f;}
inline void write(Writer& w,const PlayerState& s) {
    w.u16(s.id).u32(s.sequence).u32(s.timeMs).u8(s.flags).u32(s.mapHash).u16(s.skeleton);
    for(float v:s.position) w.f32(v);
    for(float v:s.rotation) w.f32(v);
    for(float v:s.rootOffset) w.f32(v);
    const auto count=std::uint8_t(std::min<std::size_t>(s.bones.size()/4,kMaxBones));w.u8(count);
    w.raw(s.bones.data(),std::size_t(count)*4*sizeof(std::int16_t));
}
inline PlayerState readPlayerState(Reader& r) {
    PlayerState s;s.id=r.u16();s.sequence=r.u32();s.timeMs=r.u32();s.flags=r.u8();s.mapHash=r.u32();s.skeleton=r.u16();
    for(float& v:s.position) v=r.f32();
    for(float& v:s.rotation) v=r.f32();
    for(float& v:s.rootOffset) v=r.f32();
    for(float v:s.position) if(!std::isfinite(v)||std::abs(v)>1000000.f) throw std::runtime_error("invalid position");
    for(float v:s.rotation) if(!std::isfinite(v)||std::abs(v)>1.01f) throw std::runtime_error("invalid rotation");
    for(float v:s.rootOffset) if(!std::isfinite(v)||std::abs(v)>1000.f) throw std::runtime_error("invalid root offset");
    const auto count=r.u8();if(count>kMaxBones) throw std::runtime_error("too many bones");
    s.bones.resize(std::size_t(count)*4);std::memcpy(s.bones.data(),r.take(s.bones.size()*sizeof(std::int16_t)),s.bones.size()*sizeof(std::int16_t));
    return s;
}

inline std::vector<unsigned char> frame(Tcp type,const Writer& payload) {
    Writer w;w.u32(std::uint32_t(payload.bytes.size()+1)).u8(std::uint8_t(type));
    w.bytes.insert(w.bytes.end(),payload.bytes.begin(),payload.bytes.end());return std::move(w.bytes);
}
}
