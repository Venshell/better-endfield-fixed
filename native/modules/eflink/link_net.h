// Ported from EF Link (AGPL-3.0).
// Upstream: https://github.com/Venshell/EFLink @ 956a0d5d4b900f17ba06d7b30bc15d24770eb3a1 (1.6.6),
// fork of https://github.com/guchang233/EFLink.
// Wire layout is unchanged. Platform calls live in eflink_platform.h.
#pragma once
// EF Link networking: LAN discovery, rooms and relaying. Runs on its own thread and has no
// game dependencies, so it can be exercised by a plain test program.
#include "eflink_platform.h"
#include "link_protocol.h"
#include <poll.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <deque>
#include <functional>
#include <iterator>
#include <map>
#include <memory>
#include <mutex>
#include <optional>
#include <random>
#include <set>
#include <thread>

namespace eflink {
enum class Role {Offline=0,Connecting=1,Host=2,Client=3};

// IPv4 addresses of this device's active adapters, for telling friends where to connect.
inline std::vector<std::pair<std::string,std::string>> localAddresses() {
    return platform::LocalAddresses();
}

struct HostOptions {
    std::string roomName;
    std::string password;
    unsigned maxPlayers=8;
    std::uint16_t port=proto::kDefaultPort;
};
struct Room {
    proto::Beacon beacon;
    std::string address;          // ip:port to join
    std::uint64_t seenMs=0;
};
struct TimedState {proto::PlayerState state;std::uint64_t receivedMs=0;};
struct PeerView {
    std::uint16_t id=0;
    bool local=false,host=false,hasInfo=false;
    proto::PlayerInfo info;
    std::deque<TimedState> states;  // newest last
    std::uint32_t rttMs=0;
    std::string address;
};
struct ChatLine {std::uint64_t serial=0;std::uint16_t from=0;std::string name,text;bool system=false;std::uint64_t atMs=0;};
struct Snapshot {
    Role role=Role::Offline;
    std::uint16_t localId=0;
    std::uint32_t roomId=0;
    std::string roomName,lastError,address;
    unsigned maxPlayers=0;
    bool password=false;
    std::uint16_t port=0;
    std::vector<PeerView> peers;    // includes the local player
    std::vector<Room> rooms;
    std::uint32_t rttMs=0;
    std::uint64_t bytesIn=0,bytesOut=0,statesIn=0,statesOut=0;
    std::uint64_t pongs=0;
    // Links whose UDP path is open / that run over TCP only (UDP blocked or a
    // TCP-only tunnel), and unreliable module data carried or dropped on TCP.
    std::uint32_t udpLinks=0,tcpOnlyLinks=0;
    std::uint64_t unreliableOverTcp=0,unreliableDropped=0;
};

class Net {
public:
    // All callbacks run on the network thread.
    std::function<void(std::uint16_t from,std::uint32_t channel,const unsigned char* data,std::size_t size)> onData;
    std::function<void(std::uint16_t peer,bool joined)> onPeer;
    std::string version="1.0.0";

    ~Net() {stop();}
    bool start(std::uint16_t listenPort=proto::kDiscoveryPort) {
        if(thread.joinable()) return true;
        discoveryPort=listenPort;stopping=false;
        thread=std::thread([this]{loop();});
        return true;
    }
    void stop() {
        if(thread.joinable()) {
            post([this]{closeSession("module stopped",true);});
            // Give the goodbye frames a moment to leave before the sockets close.
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
            stopping=true;thread.join();
        }
    }
    void host(HostOptions value) {post([this,value]{startHost(value);});}
    void join(std::string address,std::string password) {post([this,address,password]{startJoin(address,password);});}
    // A TCP socket already bridged by the lobby. The network thread owns it after this call.
    void adoptIncoming(int connected,std::string address) {
        post([this,connected,address]{
            if(role.load()!=Role::Host||connected<0) {if(connected>=0) closesocket(connected);return;}
            nonBlocking(connected);int on=1;setsockopt(connected,IPPROTO_TCP,TCP_NODELAY,reinterpret_cast<const char*>(&on),sizeof on);
            if(pending.size()>=16) {closesocket(connected);return;}
            auto member=std::make_unique<Member>();member->tcp.socket=connected;member->tcp.lastHeard=nowMs();member->address=std::move(address);
            pending.push_back(std::move(member));
        });
    }
    void adoptServer(int connected,std::string password,std::string label) {
        post([this,connected,password,label]{
            if(connected<0) {notice("大厅连接失败",true);return;}
            closeSession("left the room",true);
            server.socket=connected;nonBlocking(server.socket);
            int on=1;setsockopt(server.socket,IPPROTO_TCP,TCP_NODELAY,reinterpret_cast<const char*>(&on),sizeof on);
            joinPassword=password;joinStarted=nowMs();joinAddress=label;
            role.store(Role::Connecting);udpWelcomed=false;udpNoticed=false;
            {std::lock_guard lock(viewMutex);view.lastError.clear();}
            resetView();publishPeers();
            notice("正在通过大厅连接…");
            proto::Writer hello;
            hello.u32(proto::kMagic).u16(proto::kProtocol).str(version,32).str(name(),64).u64(proto::passwordProof(0,password)).u16(0);
            auto bytes=proto::frame(proto::Tcp::Hello,hello);server.out.insert(server.out.end(),bytes.begin(),bytes.end());
            server.lastHeard=nowMs();
        });
    }
    void leave() {post([this]{closeSession("left the room",true);});}
    void refresh() {post([this]{sendDiscover();});}
    void kick(std::uint16_t id) {post([this,id]{kickPeer(id);});}
    void chat(std::string text) {post([this,text]{sendChat(text);});}
    void setName(std::string name) {
        std::lock_guard lock(localMutex);localInfo.name=name;localInfoDirty=true;
    }
    void setLocalInfo(proto::PlayerInfo info) {
        std::lock_guard lock(localMutex);
        info.name=localInfo.name;
        const bool changed=info.modelPath!=localInfo.modelPath||info.mapId!=localInfo.mapId||info.levelId!=localInfo.levelId||
            info.skeleton!=localInfo.skeleton||info.bones!=localInfo.bones||info.weaponModel!=localInfo.weaponModel;
        localInfo=std::move(info);if(changed) localInfoDirty=true;
    }
    std::string name() {std::lock_guard lock(localMutex);return localInfo.name;}
    void setLocalState(proto::PlayerState state) {
        std::lock_guard lock(localMutex);localState=std::move(state);localStateDirty=true;
    }
    bool sendData(std::uint32_t channel,std::uint16_t to,const void* data,std::size_t size,bool reliable) {
        if(reliable?size>proto::kMaxReliableData:size>proto::kMaxUnreliableData) return false;
        if(role.load()!=Role::Host&&role.load()!=Role::Client) return false;
        if(!data&&size) return false;
        std::vector<unsigned char> copy;
        if(size) copy.assign(static_cast<const unsigned char*>(data),static_cast<const unsigned char*>(data)+size);
        post([this,channel,to,reliable,copy=std::move(copy)]{sendDataNow(channel,to,copy,reliable);});
        return true;
    }
    // Tests: behave as if this end's UDP were blocked (TCP-only path).
    void simulateTcpOnly(bool value) {tcpOnlyForTest=value;}
    Role currentRole() const {return role.load();}
    Snapshot snapshot() {
        std::lock_guard lock(viewMutex);
        Snapshot s=view;s.rooms.clear();
        const auto now=nowMs();
        for(const auto& [key,room]:rooms) if(now-room.seenMs<4500&&room.beacon.roomId!=view.roomId) s.rooms.push_back(room);
        return s;
    }
    std::vector<ChatLine> chatSince(std::uint64_t serial) {
        std::lock_guard lock(viewMutex);std::vector<ChatLine> out;
        for(const auto& line:chatLog) if(line.serial>serial) out.push_back(line);
        return out;
    }

private:
    struct Connection {
        SOCKET socket=INVALID_SOCKET;
        std::vector<unsigned char> in,out;
        std::uint64_t lastHeard=0,lastSent=0;
    };
    struct Member {
        std::uint16_t id=0;
        Connection tcp;
        sockaddr_in udp{};bool udpKnown=false;std::uint64_t joinedAt=0;
        std::uint32_t token=0;
        bool welcomed=false,tcpNoticed=false;
        std::string address;
    };
    std::thread thread;
    std::atomic<bool> stopping=false;
    std::uint16_t discoveryPort=proto::kDiscoveryPort;
    std::mutex commandMutex;
    std::vector<std::function<void()>> commands;
    std::mutex localMutex;
    proto::PlayerInfo localInfo;bool localInfoDirty=true;
    proto::PlayerState localState;bool localStateDirty=false;
    std::mutex viewMutex;
    Snapshot view;
    std::map<std::string,Room> rooms;
    std::deque<ChatLine> chatLog;std::uint64_t chatSerial=0;
    std::atomic<Role> role=Role::Offline;
    std::atomic<bool> tcpOnlyForTest=false;

    // Network-thread state.
    SOCKET discovery=INVALID_SOCKET,udp=INVALID_SOCKET,listener=INVALID_SOCKET;
    std::vector<sockaddr_in> broadcasts;std::uint64_t broadcastsAt=0;
    HostOptions options;
    std::uint32_t roomId=0,token=0;
    std::uint16_t localId=0;
    std::map<std::uint16_t,Member> members;          // host: connected clients
    std::vector<std::pair<std::uint16_t,std::string>> departing; // host: said goodbye this pass
    std::vector<std::unique_ptr<Member>> pending;     // host: accepted, no HELLO yet
    std::map<std::uint16_t,PeerView> peers;           // everyone but the local player
    Connection server;sockaddr_in serverUdp{};        // client: link to the host
    bool udpWelcomed=false;std::string joinPassword;std::uint64_t joinStarted=0;
    // Without a UDP path (TCP-only port forwarding) player states travel over TCP.
    static constexpr std::uint64_t kUdpGraceMs=3000;
    bool tcpStates() const {return role.load()==Role::Client&&!udpWelcomed&&joinStarted&&nowMs()-joinStarted>kUdpGraceMs;}
    // Unreliable module data (squad poses) follows the same rule: to a link whose
    // UDP path never opened it goes over TCP, dropped while that link is backed up
    // (a newer sample supersedes it anyway). A client marks such a TCP data frame
    // so the host relays it as unreliable data, not as reliable.
    static constexpr std::uint16_t kUnreliableMark=0xFFFF;
    static constexpr std::size_t kUnreliableBacklog=64*1024;
    std::uint64_t unreliableOverTcp=0,unreliableDropped=0;bool udpNoticed=false;
    bool tcpFallback(const Member& m) const {return !m.udpKnown&&m.welcomed&&nowMs()-m.joinedAt>kUdpGraceMs;}
    void queueUnreliable(Connection& c,const proto::Writer& frame) {
        if(c.out.size()>kUnreliableBacklog) {++unreliableDropped;return;}
        queue(c,proto::Tcp::Data,frame);++unreliableOverTcp;
    }
    // Host: unreliable data from `from` to every other member it is meant for.
    void relayUnreliable(std::uint16_t from,std::uint16_t to,std::uint32_t channel,const unsigned char* bytes,std::size_t size) {
        auto datagram=proto::udpHeader(proto::Udp::Data,roomId);datagram.u16(from).u16(to).u32(channel).u16(std::uint16_t(size)).raw(bytes,size);
        proto::Writer frame;frame.u16(from).u16(to).u32(channel).u32(std::uint32_t(size)).raw(bytes,size);
        for(auto& [id,other]:members) {
            if(id==from||(to!=proto::kEveryone&&to!=id))continue;
            if(other.udpKnown)sendTo(udp,other.udp,datagram.bytes);
            else if(tcpFallback(other))queueUnreliable(other.tcp,frame);
        }
    }
    std::uint64_t lastBeacon=0,lastDiscover=0,lastPing=0,lastUdpHello=0,lastInfoSent=0,discoverReplyAt=0;
    std::uint32_t rtt=0,stateSequence=0;
    std::uint64_t bytesIn=0,bytesOut=0,statesIn=0,statesOut=0;
    std::uint64_t pongs=0;
    std::string lastError,joinAddress;
    std::mt19937 random{std::random_device{}()};

    void post(std::function<void()> command) {std::lock_guard lock(commandMutex);commands.push_back(std::move(command));}
    static void closeSocket(SOCKET& s) {if(s!=INVALID_SOCKET) {closesocket(s);s=INVALID_SOCKET;}}
    static void nonBlocking(SOCKET s) {platform::NonBlocking(s);}
    static std::string text(const sockaddr_in& a) {
        char ip[32]{};inet_ntop(AF_INET,&a.sin_addr,ip,sizeof ip);return std::string(ip)+":"+std::to_string(ntohs(a.sin_port));
    }
    void notice(const std::string& message,bool error=false) {
        std::lock_guard lock(viewMutex);
        if(error) view.lastError=message;
        chatLog.push_back({++chatSerial,0,"",message,true,nowMs()});
        while(chatLog.size()>200) chatLog.pop_front();
    }
    void addChat(std::uint16_t from,const std::string& name,const std::string& text) {
        std::lock_guard lock(viewMutex);
        chatLog.push_back({++chatSerial,from,name,text,false,nowMs()});
        while(chatLog.size()>200) chatLog.pop_front();
    }

    SOCKET udpSocket(std::uint16_t port,bool reuse) {
        SOCKET s=socket(AF_INET,SOCK_DGRAM,IPPROTO_UDP);if(s==INVALID_SOCKET) return s;
        int on=1;setsockopt(s,SOL_SOCKET,SO_BROADCAST,reinterpret_cast<const char*>(&on),sizeof on);
        if(reuse) setsockopt(s,SOL_SOCKET,SO_REUSEADDR,reinterpret_cast<const char*>(&on),sizeof on);
        // Linux does not reset an unconnected UDP socket on ICMP port-unreachable
        // the way Winsock does, so the Windows SIO_UDP_CONNRESET ioctl is omitted.
        sockaddr_in a{};a.sin_family=AF_INET;a.sin_port=htons(port);a.sin_addr.s_addr=htonl(INADDR_ANY);
        if(bind(s,reinterpret_cast<sockaddr*>(&a),sizeof a)!=0) {closesocket(s);return INVALID_SOCKET;}
        nonBlocking(s);return s;
    }
    void refreshBroadcasts() {
        broadcasts.clear();
        auto add=[&](std::uint32_t hostOrder){
            sockaddr_in a{};a.sin_family=AF_INET;a.sin_port=htons(discoveryPort);a.sin_addr.s_addr=htonl(hostOrder);
            for(const auto& b:broadcasts) if(b.sin_addr.s_addr==a.sin_addr.s_addr) return;
            broadcasts.push_back(a);
        };
        add(INADDR_BROADCAST);add(INADDR_LOOPBACK);
        // Limited broadcast leaves through one adapter only; VPN LANs need the
        // directed broadcast of each adapter. Tun interfaces stay in the list.
        for(const auto& adapter:platform::Adapters()) {
            if(adapter.prefix<=0||adapter.prefix>=31||(adapter.ip_host>>24)==127) continue;
            const std::uint32_t mask=adapter.prefix>=32?~0u:~0u<<(32-adapter.prefix);
            add(adapter.ip_host|~mask);
        }
    }
    void sendTo(SOCKET s,const sockaddr_in& to,const std::vector<unsigned char>& bytes) {
        if(s==INVALID_SOCKET||bytes.size()>proto::kMaxDatagram) return;
        if(tcpOnlyForTest.load()&&s==udp) return;
        if(sendto(s,reinterpret_cast<const char*>(bytes.data()),int(bytes.size()),0,reinterpret_cast<const sockaddr*>(&to),sizeof to)>0) bytesOut+=bytes.size();
    }
    void broadcast(const std::vector<unsigned char>& bytes) {
        if(nowMs()-broadcastsAt>10000||broadcasts.empty()) {refreshBroadcasts();broadcastsAt=nowMs();}
        for(const auto& b:broadcasts) sendTo(discovery,b,bytes);
    }
    void sendDiscover() {
        if(discovery==INVALID_SOCKET) return;
        lastDiscover=nowMs();broadcast(proto::udpHeader(proto::Udp::Discover,0).bytes);
    }
    proto::Beacon beacon() {
        proto::Beacon b;b.roomId=roomId;b.gamePort=options.port;b.players=std::uint8_t(peers.size()+1);b.maxPlayers=std::uint8_t(options.maxPlayers);
        b.password=!options.password.empty();b.roomName=options.roomName;b.version=version;
        std::lock_guard lock(localMutex);b.hostName=localInfo.name;b.mapId=localInfo.mapId;return b;
    }

    // ---- TCP framing ----
    void queue(Connection& c,proto::Tcp type,const proto::Writer& payload) {
        auto bytes=proto::frame(type,payload);c.out.insert(c.out.end(),bytes.begin(),bytes.end());flush(c);
    }
    bool flush(Connection& c) {
        while(!c.out.empty()&&c.socket!=INVALID_SOCKET) {
            const int sent=platform::Send(c.socket,c.out.data(),int((std::min)(c.out.size(),std::size_t(64*1024))));
            if(sent>0) {bytesOut+=sent;c.out.erase(c.out.begin(),c.out.begin()+sent);c.lastSent=nowMs();continue;}
            if(platform::Busy(platform::LastError())) break;
            return false;
        }
        // A peer that stops reading must not grow our memory without bound.
        return c.out.size()<=8*proto::kMaxFrame;
    }
    // Reads whatever arrived; returns false when the connection closed or broke.
    bool receive(Connection& c,const std::function<void(proto::Tcp,proto::Reader&)>& handle,std::size_t preHelloLimit=0) {
        char buffer[16384];bool closed=false;
        for(;;) {
            const int n=recv(c.socket,buffer,sizeof buffer,0);
            if(n>0) {bytesIn+=n;c.in.insert(c.in.end(),buffer,buffer+n);c.lastHeard=nowMs();if(c.in.size()>2*proto::kMaxFrame) return false;continue;}
            // Frames that arrived together with the close (a goodbye) are still handled below.
            if(n==0||!platform::Busy(platform::LastError())) closed=true;
            break;
        }
        std::size_t at=0;
        while(c.in.size()-at>=5) {
            std::uint32_t length;std::memcpy(&length,c.in.data()+at,4);
            if(length==0||length>proto::kMaxFrame||(preHelloLimit&&length>preHelloLimit)) return false;
            if(c.in.size()-at-4<length) break;
            proto::Reader r(c.in.data()+at+5,length-1);
            const auto type=proto::Tcp(c.in[at+4]);at+=4+length;
            try {handle(type,r);} catch(const std::exception&) {return false;}
            if(c.socket==INVALID_SOCKET) return false;
        }
        c.in.erase(c.in.begin(),c.in.begin()+at);
        return !closed;
    }

    // ---- session lifecycle ----
    void resetView() {
        std::lock_guard lock(viewMutex);
        view.role=role.load();view.localId=localId;view.roomId=roomId;view.roomName=options.roomName;view.maxPlayers=options.maxPlayers;
        view.password=!options.password.empty();view.port=options.port;view.address=joinAddress;
    }
    void closeSession(const std::string& reason,bool polite,bool error=false) {
        const auto old=role.load();
        if(old==Role::Offline) return;
        if(polite) {
            proto::Writer bye;bye.str(reason,128);
            for(auto& [id,m]:members) {queue(m.tcp,proto::Tcp::Bye,bye);}
            if(server.socket!=INVALID_SOCKET) queue(server,proto::Tcp::Bye,bye);
        }
        for(auto& [id,m]:members) closeSocket(m.tcp.socket);
        for(auto& p:pending) closeSocket(p->tcp.socket);
        members.clear();pending.clear();
        closeSocket(server.socket);server=Connection{};
        closeSocket(udp);closeSocket(listener);
        auto departed=peers;peers.clear();
        role.store(Role::Offline);localId=0;roomId=0;token=0;udpWelcomed=false;rtt=0;
        resetView();publishPeers();
        for(const auto& [id,peer]:departed) if(onPeer) onPeer(id,false);
        if(onPeer&&(old==Role::Host||old==Role::Client)) onPeer(0,false);
        notice(reason,error);
    }
    void startHost(HostOptions value) {
        closeSession("left the room",true);
        value.maxPlayers=(std::clamp)(value.maxPlayers,2u,proto::kMaxPlayers);
        if(value.roomName.empty()) value.roomName=name()+" 的房间";
        options=value;
        udp=udpSocket(options.port,false);
        listener=socket(AF_INET,SOCK_STREAM,IPPROTO_TCP);
        sockaddr_in a{};a.sin_family=AF_INET;a.sin_port=htons(options.port);a.sin_addr.s_addr=htonl(INADDR_ANY);
        // POSIX refuses a second bind of this port unless SO_REUSEADDR is set.
        // Leaving it unset is the exclusive bind Winsock asked for with SO_EXCLUSIVEADDRUSE.
        if(udp==INVALID_SOCKET||listener==INVALID_SOCKET||bind(listener,reinterpret_cast<sockaddr*>(&a),sizeof a)!=0||listen(listener,16)!=0) {
            closeSocket(udp);closeSocket(listener);
            notice("无法在端口 "+std::to_string(options.port)+" 开放房间（端口被占用或被防火墙阻止）",true);return;
        }
        nonBlocking(listener);
        roomId=random()|1u;localId=proto::kHostId;role.store(Role::Host);
        {std::lock_guard lock(localMutex);localInfoDirty=true;}
        {std::lock_guard lock(viewMutex);view.lastError.clear();}
        joinAddress.clear();resetView();publishPeers();lastBeacon=0;
        if(onPeer) onPeer(localId,true);
        notice("房间已开放：「"+options.roomName+"」，端口 "+std::to_string(options.port));
    }
    void startJoin(const std::string& address,const std::string& password) {
        closeSession("left the room",true);
        std::string hostName=address;std::uint16_t port=proto::kDefaultPort;
        if(auto colon=address.rfind(':');colon!=std::string::npos&&address.find(':')==colon) {
            hostName=address.substr(0,colon);
            try {auto raw=address.substr(colon+1);std::size_t consumed=0;const auto value=std::stoul(raw,&consumed);if(value==0||value>65535||consumed!=raw.size()) throw 0;port=std::uint16_t(value);}
            catch(...) {notice("地址格式错误："+address,true);return;}
        }
        addrinfo hints{};hints.ai_family=AF_INET;hints.ai_socktype=SOCK_STREAM;addrinfo* found=nullptr;
        if(hostName.empty()||getaddrinfo(hostName.c_str(),nullptr,&hints,&found)!=0||!found) {notice("找不到主机："+hostName,true);return;}
        sockaddr_in target=*reinterpret_cast<sockaddr_in*>(found->ai_addr);freeaddrinfo(found);target.sin_port=htons(port);
        udp=udpSocket(0,false);
        server.socket=socket(AF_INET,SOCK_STREAM,IPPROTO_TCP);
        if(udp==INVALID_SOCKET||server.socket==INVALID_SOCKET) {closeSocket(udp);closeSocket(server.socket);notice("无法创建网络连接",true);return;}
        int on=1;setsockopt(server.socket,IPPROTO_TCP,TCP_NODELAY,reinterpret_cast<const char*>(&on),sizeof on);
        nonBlocking(server.socket);
        const int result=connect(server.socket,reinterpret_cast<sockaddr*>(&target),sizeof target);
        if(result!=0&&!platform::Busy(platform::LastError())) {closeSocket(udp);closeSocket(server.socket);notice("无法连接到 "+address,true);return;}
        serverUdp=target;joinPassword=password;joinStarted=nowMs();joinAddress=text(target);
        role.store(Role::Connecting);udpWelcomed=false;udpNoticed=false;
        {std::lock_guard lock(viewMutex);view.lastError.clear();}
        resetView();publishPeers();
        notice("正在连接 "+joinAddress+" …");
        // The HELLO is queued now and leaves as soon as the connection completes.
        proto::Writer hello;
        sockaddr_in local{};socklen_t length=sizeof local;getsockname(udp,reinterpret_cast<sockaddr*>(&local),&length);
        hello.u32(proto::kMagic).u16(proto::kProtocol).str(version,32).str(name(),64).u64(proto::passwordProof(0,password)).u16(ntohs(local.sin_port));
        auto bytes=proto::frame(proto::Tcp::Hello,hello);server.out.insert(server.out.end(),bytes.begin(),bytes.end());
        server.lastHeard=nowMs();
    }
    void kickPeer(std::uint16_t id) {
        if(role.load()!=Role::Host) return;
        auto found=members.find(id);if(found==members.end()) return;
        proto::Writer reason;reason.str("kicked",128);queue(found->second.tcp,proto::Tcp::Reject,reason);
        dropMember(id,"被房主移出房间");
    }
    void dropMember(std::uint16_t id,const std::string& reason) {
        auto found=members.find(id);if(found==members.end()) return;
        closeSocket(found->second.tcp.socket);members.erase(found);
        std::string who="玩家 "+std::to_string(id);
        if(auto p=peers.find(id);p!=peers.end()) {if(p->second.hasInfo) who=p->second.info.name;peers.erase(p);}
        proto::Writer leave;leave.u16(id).str(reason,128);
        for(auto& [other,m]:members) if(m.welcomed) queue(m.tcp,proto::Tcp::PlayerLeave,leave);
        publishPeers();if(onPeer) onPeer(id,false);
        notice(who+" 离开了房间（"+reason+"）");
    }

    // ---- host message handling ----
    void hostAccept() {
        for(;;) {
            sockaddr_in from{};socklen_t length=sizeof from;
            SOCKET s=accept(listener,reinterpret_cast<sockaddr*>(&from),&length);
            if(s==INVALID_SOCKET) break;
            nonBlocking(s);int on=1;setsockopt(s,IPPROTO_TCP,TCP_NODELAY,reinterpret_cast<const char*>(&on),sizeof on);
            if(pending.size()>=16) {closesocket(s);continue;}
            auto m=std::make_unique<Member>();m->tcp.socket=s;m->tcp.lastHeard=nowMs();m->address=text(from);
            m->udp=from;pending.push_back(std::move(m));
        }
    }
    void reject(Connection& c,const std::string& reason) {
        proto::Writer w;w.str(reason,128);queue(c,proto::Tcp::Reject,w);
    }
    void hostHello(Member& m,proto::Reader& r) {
        if(r.u32()!=proto::kMagic) throw std::runtime_error("bad hello");
        const auto protocol=r.u16();const auto clientVersion=r.str(32);const auto clientName=r.str(64);
        const auto proof=r.u64();const auto udpPort=r.u16();
        if(protocol!=proto::kProtocol) {reject(m.tcp,"version:"+version);return;}
        if(proof!=proto::passwordProof(0,options.password)) {reject(m.tcp,"password");return;}
        if(members.size()+1>=options.maxPlayers) {reject(m.tcp,"full");return;}
        std::uint16_t id=2;while(members.contains(id)) ++id;
        m.id=id;m.token=random();m.welcomed=true;m.udp.sin_port=htons(udpPort);m.udpKnown=false;m.joinedAt=nowMs();
        proto::Writer welcome;welcome.u16(id).u32(roomId).u32(m.token).str(options.roomName,64).u8(std::uint8_t(options.maxPlayers)).u16(proto::kHostId);
        queue(m.tcp,proto::Tcp::Welcome,welcome);
        // Bring the newcomer up to date with everyone already in the room.
        {proto::Writer self;std::lock_guard lock(localMutex);auto info=localInfo;info.id=localId;proto::write(self,info);queue(m.tcp,proto::Tcp::PlayerInfo,self);}
        for(const auto& [pid,peer]:peers) if(peer.hasInfo) {proto::Writer w;proto::write(w,peer.info);queue(m.tcp,proto::Tcp::PlayerInfo,w);}
        PeerView entry;entry.id=id;entry.address=m.address;entry.info.id=id;entry.info.name=clientName;peers[id]=entry;
        notice(clientName+" 加入了房间"+(clientVersion!=version?"（模组版本 "+clientVersion+"）":""));
        publishPeers();if(onPeer) onPeer(id,true);
    }
    void relayFrame(std::uint16_t except,proto::Tcp type,const proto::Writer& payload) {
        for(auto& [id,m]:members) if(id!=except&&m.welcomed) queue(m.tcp,type,payload);
    }
    // Host: send a player's state to every other member, by UDP when its path is
    // known, otherwise over TCP once the member is past the UDP grace period.
    void relayState(std::uint16_t from,const proto::PlayerState& state) {
        auto datagram=proto::udpHeader(proto::Udp::State,roomId);proto::write(datagram,state);
        proto::Writer frame;proto::write(frame,state);
        for(auto& [id,other]:members) {
            if(id==from)continue;
            if(other.udpKnown)sendTo(udp,other.udp,datagram.bytes);
            else if(other.welcomed&&nowMs()-other.joinedAt>kUdpGraceMs)queue(other.tcp,proto::Tcp::State,frame);
        }
    }
    void hostFrame(Member& m,proto::Tcp type,proto::Reader& r) {
        switch(type) {
        case proto::Tcp::PlayerInfo: {
            auto info=proto::readPlayerInfo(r);info.id=m.id;
            auto& peer=peers[m.id];peer.id=m.id;peer.info=info;peer.hasInfo=true;peer.address=m.address;
            proto::Writer w;proto::write(w,info);relayFrame(m.id,proto::Tcp::PlayerInfo,w);publishPeers();break;
        }
        case proto::Tcp::State: {
            auto state=proto::readPlayerState(r);state.id=m.id;storeState(state);++statesIn;
            relayState(m.id,state);break;
        }
        case proto::Tcp::Chat: {
            auto line=r.str(1024);const auto who=peers[m.id].info.name;
            proto::Writer w;w.u16(m.id).str(line,1024);relayFrame(m.id,proto::Tcp::Chat,w);addChat(m.id,who,line);break;
        }
        case proto::Tcp::Data: {
            const bool unreliable=r.u16()==kUnreliableMark;const auto to=r.u16();const auto channel=r.u32();const auto size=r.u32();
            if(size>proto::kMaxReliableData) throw std::runtime_error("data too large");
            const auto* bytes=r.take(size);
            if(unreliable) {
                if(size>proto::kMaxUnreliableData) break;
                relayUnreliable(m.id,to,channel,bytes,size);
                if((to==proto::kEveryone||to==localId)&&onData) onData(m.id,channel,bytes,size);
                break;
            }
            proto::Writer w;w.u16(m.id).u16(to).u32(channel).u32(size).raw(bytes,size);
            if(to==proto::kEveryone) relayFrame(m.id,proto::Tcp::Data,w);
            else if(auto target=members.find(to);target!=members.end()&&to!=m.id) queue(target->second.tcp,proto::Tcp::Data,w);
            if((to==proto::kEveryone||to==localId)&&onData) onData(m.id,channel,bytes,size);
            break;
        }
        case proto::Tcp::Bye: departing.push_back({m.id,"主动离开"});break;
        case proto::Tcp::KeepAlive: break;
        default: break;
        }
    }
    void hostDatagram(const sockaddr_in& from,proto::Reader& r,proto::Udp type) {
        auto sender=[&]()->Member* {
            for(auto& [id,m]:members) if(m.udpKnown&&m.udp.sin_addr.s_addr==from.sin_addr.s_addr&&m.udp.sin_port==from.sin_port) return &m;
            return nullptr;
        };
        if(type==proto::Udp::Hello) {
            const auto id=r.u16();const auto value=r.u32();
            auto found=members.find(id);if(found==members.end()||found->second.token!=value) return;
            found->second.udp=from;found->second.udpKnown=true;
            sendTo(udp,from,proto::udpHeader(proto::Udp::Welcome,roomId).bytes);return;
        }
        auto* m=sender();if(!m) return;
        if(type==proto::Udp::Ping) {
            auto pong=proto::udpHeader(proto::Udp::Pong,roomId);pong.u64(r.u64());sendTo(udp,from,pong.bytes);return;
        }
        m->tcp.lastHeard=nowMs();
        if(type==proto::Udp::State) {
            auto state=proto::readPlayerState(r);state.id=m->id;storeState(state);
            relayState(m->id,state);
        } else if(type==proto::Udp::Data) {
            r.u16();const auto to=r.u16();const auto channel=r.u32();const auto size=r.u16();
            if(size>proto::kMaxUnreliableData) return;
            const auto* bytes=r.take(size);
            relayUnreliable(m->id,to,channel,bytes,size);
            if((to==proto::kEveryone||to==localId)&&onData) onData(m->id,channel,bytes,size);
        }
    }

    // ---- client message handling ----
    void clientFrame(proto::Tcp type,proto::Reader& r) {
        switch(type) {
        case proto::Tcp::Welcome: {
            localId=r.u16();roomId=r.u32();token=r.u32();options.roomName=r.str(64);options.maxPlayers=r.u8();r.u16();
            role.store(Role::Client);resetView();
            {std::lock_guard lock(localMutex);localInfoDirty=true;}
            notice("已加入房间「"+options.roomName+"」");
            publishPeers();if(onPeer) onPeer(localId,true);break;
        }
        case proto::Tcp::Reject: {
            const auto reason=r.str(128);
            std::string message=reason=="password"?"密码错误":reason=="full"?"房间已满":reason=="kicked"?"你被房主移出了房间":
                reason.rfind("version:",0)==0?"联机协议版本不同（房主模组版本 "+reason.substr(8)+"），请双方使用相同版本":"连接被拒绝："+reason;
            closeSession(message,false,true);break;
        }
        case proto::Tcp::PlayerInfo: {
            auto info=proto::readPlayerInfo(r);if(info.id==localId) break;
            auto& peer=peers[info.id];
            const bool fresh=!peer.hasInfo&&peer.states.empty();
            peer.id=info.id;peer.host=info.id==proto::kHostId;peer.info=info;peer.hasInfo=true;
            publishPeers();if(fresh&&onPeer) onPeer(info.id,true);break;
        }
        case proto::Tcp::PlayerLeave: {
            const auto id=r.u16();const auto reason=r.str(128);
            if(auto p=peers.find(id);p!=peers.end()) {
                const auto who=p->second.info.name;peers.erase(p);publishPeers();if(onPeer) onPeer(id,false);
                notice(who+" 离开了房间（"+reason+"）");
            }
            break;
        }
        case proto::Tcp::State: {
            auto state=proto::readPlayerState(r);if(state.id!=localId) {storeState(state);++statesIn;}break;
        }
        case proto::Tcp::Chat: {
            const auto from=r.u16();const auto line=r.str(1024);
            addChat(from,peers.contains(from)?peers[from].info.name:"玩家 "+std::to_string(from),line);break;
        }
        case proto::Tcp::Data: {
            const auto from=r.u16();r.u16();const auto channel=r.u32();const auto size=r.u32();
            if(size>proto::kMaxReliableData) throw std::runtime_error("data too large");
            const auto* bytes=r.take(size);if(onData) onData(from,channel,bytes,size);break;
        }
        case proto::Tcp::Bye: closeSession("房主关闭了房间",false,true);break;
        default: break;
        }
    }
    void clientDatagram(const sockaddr_in& from,proto::Reader& r,proto::Udp type,std::uint32_t session) {
        if(session!=roomId) return;
        // Behind a relay (frp and similar) the reply can come from another address
        // than the one connected to. The room's random ID identifies the host's
        // welcome; its source becomes the UDP path from then on.
        if(type==proto::Udp::Welcome&&!udpWelcomed) {udpWelcomed=true;serverUdp=from;return;}
        if(from.sin_addr.s_addr!=serverUdp.sin_addr.s_addr||from.sin_port!=serverUdp.sin_port) return;
        if(type==proto::Udp::Welcome) return;
        if(type==proto::Udp::Pong) {const auto sent=r.u64();const auto now=nowMs();if(now>=sent&&now-sent<10000) {rtt=std::uint32_t(now-sent);++pongs;}return;}
        if(type==proto::Udp::State) {auto state=proto::readPlayerState(r);if(state.id!=localId) storeState(state);return;}
        if(type==proto::Udp::Data) {
            const auto sender=r.u16();r.u16();const auto channel=r.u32();const auto size=r.u16();
            if(size>proto::kMaxUnreliableData) return;
            const auto* bytes=r.take(size);if(onData) onData(sender,channel,bytes,size);
        }
    }
    void storeState(const proto::PlayerState& state) {
        if(!peers.contains(state.id)) return;
        auto& peer=peers[state.id];peer.id=state.id;
        if(!peer.states.empty()&&std::int32_t(state.sequence-peer.states.back().state.sequence)<=0) return; // stale or duplicate
        peer.states.push_back({state,nowMs()});while(peer.states.size()>8) peer.states.pop_front();
        ++statesIn;
        std::lock_guard lock(viewMutex);
        for(auto& p:view.peers) if(p.id==state.id) {p.states=peer.states;return;}
    }
    void publishPeers() {
        std::lock_guard lock(viewMutex);
        std::optional<PeerView> previousSelf;
        if(!view.peers.empty()&&view.peers.front().local&&view.peers.front().id==localId)previousSelf=view.peers.front();
        view.peers.clear();
        if(role.load()==Role::Host||role.load()==Role::Client) {
            PeerView self;self.id=localId;self.local=true;self.host=role.load()==Role::Host;self.hasInfo=true;
            {std::lock_guard l(localMutex);self.info=localInfo;}self.info.id=localId;self.rttMs=rtt;
            // Remote joins/info refreshes must not erase the host's sampled state
            // and revoke its native realm binding. Preserve the original freshness
            // timestamp only when the local operator and scene still match.
            if(previousSelf&&previousSelf->info.modelPath==self.info.modelPath&&
               previousSelf->info.mapId==self.info.mapId&&previousSelf->info.levelId==self.info.levelId)
                self.states=std::move(previousSelf->states);
            view.peers.push_back(self);
        }
        for(const auto& [id,peer]:peers) {auto copy=peer;copy.host=id==proto::kHostId;copy.local=false;view.peers.push_back(copy);}
        view.rttMs=rtt;
    }

    // ---- outgoing local updates ----
    void sendDataNow(std::uint32_t channel,std::uint16_t to,const std::vector<unsigned char>& data,bool reliable) {
        if(role.load()!=Role::Host&&role.load()!=Role::Client) return;
        if(reliable) {
            proto::Writer w;w.u16(localId).u16(to).u32(channel).u32(std::uint32_t(data.size())).raw(data.data(),data.size());
            if(role.load()==Role::Host) {
                if(to==proto::kEveryone) relayFrame(0,proto::Tcp::Data,w);
                else if(auto m=members.find(to);m!=members.end()) queue(m->second.tcp,proto::Tcp::Data,w);
            } else queue(server,proto::Tcp::Data,w);
        } else {
            if(role.load()==Role::Host) {relayUnreliable(localId,to,channel,data.data(),data.size());return;}
            if(udpWelcomed) {
                auto w=proto::udpHeader(proto::Udp::Data,roomId);w.u16(localId).u16(to).u32(channel).u16(std::uint16_t(data.size())).raw(data.data(),data.size());
                sendTo(udp,serverUdp,w.bytes);
            } else if(tcpStates()) {
                proto::Writer frame;frame.u16(kUnreliableMark).u16(to).u32(channel).u32(std::uint32_t(data.size())).raw(data.data(),data.size());
                queueUnreliable(server,frame);
            }
        }
    }
    void sendChat(const std::string& line) {
        if(line.empty()) return;
        proto::Writer w;
        if(role.load()==Role::Host) {w.u16(localId).str(line,1024);relayFrame(0,proto::Tcp::Chat,w);}
        else if(role.load()==Role::Client) {w.str(line,1024);queue(server,proto::Tcp::Chat,w);}
        else return;
        addChat(localId,name(),line);
    }
    void sendLocal() {
        const auto now=nowMs();
        proto::PlayerInfo info;bool infoDirty=false;proto::PlayerState state;bool stateDirty=false;
        {
            std::lock_guard lock(localMutex);
            if(localInfoDirty) {info=localInfo;infoDirty=true;localInfoDirty=false;}
            if(localStateDirty) {state=localState;stateDirty=true;localStateDirty=false;}
        }
        if(infoDirty) {
            info.id=localId;proto::Writer w;proto::write(w,info);
            if(role.load()==Role::Host) relayFrame(0,proto::Tcp::PlayerInfo,w);
            else if(role.load()==Role::Client) queue(server,proto::Tcp::PlayerInfo,w);
            publishPeers();lastInfoSent=now;
        }
        if(stateDirty) {
            state.id=localId;state.sequence=++stateSequence;
            auto w=proto::udpHeader(proto::Udp::State,roomId);proto::write(w,state);
            if(w.bytes.size()>proto::kMaxDatagram) return;
            if(role.load()==Role::Host) {relayState(localId,state);++statesOut;}
            else if(role.load()==Role::Client&&udpWelcomed) {sendTo(udp,serverUdp,w.bytes);++statesOut;}
            else if(tcpStates()) {proto::Writer frame;proto::write(frame,state);queue(server,proto::Tcp::State,frame);++statesOut;}
            std::lock_guard lock(viewMutex);
            if(!view.peers.empty()&&view.peers.front().local) {
                view.peers.front().states.push_back({state,now});
                while(view.peers.front().states.size()>2) view.peers.front().states.pop_front();
            }
        }
    }

    // ---- main loop ----
    void loop() {
        discovery=udpSocket(discoveryPort,true);
        if(discovery==INVALID_SOCKET) notice("局域网发现端口 "+std::to_string(discoveryPort)+" 不可用，只能直接输入地址连接",true);
        else sendDiscover();
        while(!stopping.load()) {
            std::vector<std::function<void()>> work;
            {std::lock_guard lock(commandMutex);work.swap(commands);}
            for(auto& command:work) {try {command();} catch(const std::exception& e) {notice(e.what(),true);}}
            std::vector<pollfd> polled;
            auto watch=[&](SOCKET s,bool write){
                if(s==INVALID_SOCKET) return;
                short events=POLLIN;
                if(write) events=static_cast<short>(events|POLLOUT);
                polled.push_back(pollfd{s,events,0});
            };
            watch(discovery,false);watch(udp,false);watch(listener,false);
            for(auto& [id,m]:members) watch(m.tcp.socket,!m.tcp.out.empty());
            for(auto& p:pending) watch(p->tcp.socket,!p->tcp.out.empty());
            watch(server.socket,!server.out.empty());
            int ready=0;
            if(polled.empty()) platform::SleepMs(10);
            else ready=::poll(polled.data(),static_cast<nfds_t>(polled.size()),10);
            if(ready>0) {
                IoReady io;
                for(const auto& item:polled) if(item.revents) io.events.emplace(item.fd,item.revents);
                service(io);
            }
            timers();
            sendLocal();
        }
        closeSession("module stopped",true);
        for(auto& [id,m]:members) flush(m.tcp);
        closeSocket(discovery);
    }
    void readDatagrams(SOCKET s,bool discoverySocket) {
        unsigned char buffer[2048];
        for(;;) {
            sockaddr_in from{};socklen_t length=sizeof from;
            const int n=recvfrom(s,reinterpret_cast<char*>(buffer),sizeof buffer,0,reinterpret_cast<sockaddr*>(&from),&length);
            if(n<=0) break;
            bytesIn+=n;
            try {
                proto::Reader r(buffer,std::size_t(n));const auto header=proto::readUdpHeader(r);
                if(discoverySocket) {
                    if(header.type==proto::Udp::Beacon) {
                        auto b=proto::decodeBeacon(r,header.session);
                        char ip[32]{};inet_ntop(AF_INET,&from.sin_addr,ip,sizeof ip);
                        Room room{b,std::string(ip)+":"+std::to_string(b.gamePort),nowMs()};
                        std::lock_guard lock(viewMutex);
                        // A room heard on loopback and on the LAN is the same room: prefer the LAN address.
                        const auto key=std::to_string(b.roomId);
                        if(auto old=rooms.find(key);old!=rooms.end()&&std::string(ip)=="127.0.0.1"&&old->second.address.rfind("127.0.0.1",0)!=0&&nowMs()-old->second.seenMs<3000)
                            old->second.seenMs=nowMs();
                        else rooms[key]=room;
                    } else if(header.type==proto::Udp::Discover&&role.load()==Role::Host&&nowMs()-discoverReplyAt>250) {
                        discoverReplyAt=nowMs();broadcast(proto::encode(beacon()));
                    }
                } else if(role.load()==Role::Host&&header.session==roomId) hostDatagram(from,r,header.type);
                else if(role.load()==Role::Client||role.load()==Role::Connecting) clientDatagram(from,r,header.type,header.session);
            } catch(const std::exception&) {}
        }
    }
    // poll(2) instead of select(2): a game process can hand out descriptors past
    // FD_SETSIZE, and FD_SET would then write past the fd_set bitmap.
    struct IoReady {
        std::map<SOCKET,short> events;
        bool test(SOCKET socket,short flags) const {
            if(socket==INVALID_SOCKET) return false;
            auto found=events.find(socket);
            return found!=events.end()&&(found->second&flags)!=0;
        }
        bool readable(SOCKET socket) const {return test(socket,POLLIN);}
        bool writable(SOCKET socket) const {return test(socket,POLLOUT);}
        bool failed(SOCKET socket) const {
            auto found=events.find(socket);
            if(socket==INVALID_SOCKET||found==events.end()) return false;
            const short bits=found->second;
            if((bits&POLLIN)!=0) return false;
            return (bits&(POLLERR|POLLNVAL|POLLHUP))!=0;
        }
    };
    void service(const IoReady& io) {
        if(io.readable(discovery)) readDatagrams(discovery,true);
        if(io.readable(udp)) readDatagrams(udp,false);
        if(io.readable(listener)) hostAccept();
        // Pending connections: wait for HELLO.
        for(std::size_t i=0;i<pending.size();) {
            auto& m=*pending[i];bool keep=true;
            if(io.failed(m.tcp.socket)) keep=false;
            else if(io.readable(m.tcp.socket)) keep=receive(m.tcp,[&](proto::Tcp type,proto::Reader& r){if(type==proto::Tcp::Hello&&!m.welcomed) hostHello(m,r);},4096);
            if(keep&&!m.tcp.out.empty()) keep=flush(m.tcp);
            if(m.welcomed) {auto id=m.id;members[id]=std::move(m);pending.erase(pending.begin()+i);continue;}
            if(!keep||nowMs()-m.tcp.lastHeard>8000) {
                // A rejected client still gets its REJECT frame before the socket closes.
                flush(m.tcp);closeSocket(m.tcp.socket);pending.erase(pending.begin()+i);continue;
            }
            ++i;
        }
        std::vector<std::uint16_t> dead;
        for(auto& [id,m]:members) {
            bool ok=true;
            if(io.failed(m.tcp.socket)) ok=false;
            if(ok&&io.readable(m.tcp.socket)) ok=receive(m.tcp,[&,id=id](proto::Tcp type,proto::Reader& r){auto f=members.find(id);if(f!=members.end()) hostFrame(f->second,type,r);});
            if(!members.contains(id)) continue;
            if(ok&&io.writable(m.tcp.socket)) ok=flush(m.tcp);
            if(!ok) dead.push_back(id);
        }
        for(auto id:dead) dropMember(id,"连接断开");
        for(auto& [id,reason]:departing) dropMember(id,reason);
        departing.clear();
        if(server.socket!=INVALID_SOCKET) {
            if(io.failed(server.socket)) {closeSession(role.load()==Role::Connecting?"无法连接到房间（对方未开房或被防火墙阻止）":"与房主的连接中断",false,true);return;}
            if(io.writable(server.socket)&&!flush(server)) {closeSession("与房主的连接中断",false,true);return;}
            if(io.readable(server.socket)&&!receive(server,[&](proto::Tcp type,proto::Reader& r){clientFrame(type,r);})) {
                if(role.load()!=Role::Offline) closeSession(role.load()==Role::Connecting?"连接被关闭（版本不兼容或对方拒绝）":"与房主的连接中断",false,true);
            }
        }
    }
    void timers() {
        const auto now=nowMs();
        if(role.load()==Role::Host) {
            if(now-lastBeacon>1000) {lastBeacon=now;broadcast(proto::encode(beacon()));}
            std::vector<std::uint16_t> silent;
            for(auto& [id,m]:members) {
                if(!m.tcpNoticed&&tcpFallback(m)) {
                    m.tcpNoticed=true;
                    notice("玩家 "+(peers.contains(id)?peers[id].info.name:std::to_string(id))+" 的 UDP 未连通，动作与队伍同步改走 TCP（延迟更高）。请同时转发 UDP 端口 "+std::to_string(options.port));
                }
                if(now-m.tcp.lastSent>2000) queue(m.tcp,proto::Tcp::KeepAlive,{});
                if(now-m.tcp.lastHeard>10000) silent.push_back(id);
            }
            for(auto id:silent) dropMember(id,"超时");
        } else if(role.load()==Role::Connecting) {
            if(now-joinStarted>8000) closeSession("连接超时：确认对方已开房、地址端口正确，且防火墙允许终末地联网",false,true);
        } else if(role.load()==Role::Client) {
            if(!udpWelcomed&&now-lastUdpHello>500) {
                lastUdpHello=now;auto w=proto::udpHeader(proto::Udp::Hello,roomId);w.u16(localId).u32(token);sendTo(udp,serverUdp,w.bytes);
            }
            if(udpWelcomed&&now-lastPing>1000) {lastPing=now;auto w=proto::udpHeader(proto::Udp::Ping,roomId);w.u64(now);sendTo(udp,serverUdp,w.bytes);}
            if(!udpNoticed&&tcpStates()) {udpNoticed=true;notice("与房主的 UDP 未连通，动作与队伍同步改走 TCP（延迟更高）。请让房主同时转发 UDP 端口");}
            if(now-server.lastSent>2000) queue(server,proto::Tcp::KeepAlive,{});
            if(now-server.lastHeard>10000) closeSession("房主无响应（超时）",false,true);
        }
        // Re-send the full player info now and then: cheap, and heals any missed update.
        if((role.load()==Role::Host||role.load()==Role::Client)&&now-lastInfoSent>5000) {std::lock_guard lock(localMutex);localInfoDirty=true;}
        if(now-lastDiscover>3000&&role.load()!=Role::Host) sendDiscover();
        std::lock_guard lock(viewMutex);
        view.rttMs=rtt;view.bytesIn=bytesIn;view.bytesOut=bytesOut;view.statesIn=statesIn;view.statesOut=statesOut;view.pongs=pongs;
        view.udpLinks=0;view.tcpOnlyLinks=0;view.unreliableOverTcp=unreliableOverTcp;view.unreliableDropped=unreliableDropped;
        if(role.load()==Role::Host) {for(const auto& [id,m]:members) {if(m.udpKnown)++view.udpLinks;else if(tcpFallback(m))++view.tcpOnlyLinks;}}
        else if(role.load()==Role::Client) {if(udpWelcomed)view.udpLinks=1;else if(tcpStates())view.tcpOnlyLinks=1;}
        if(!view.peers.empty()&&view.peers.front().local) view.peers.front().rttMs=rtt;
        for(auto it=rooms.begin();it!=rooms.end();) it=now-it->second.seenMs>6000?rooms.erase(it):std::next(it);
    }
};
}
