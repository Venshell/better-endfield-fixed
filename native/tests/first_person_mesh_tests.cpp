#include "../modules/camera/first_person_mesh.h"
#include "../modules/camera/first_person_shadow.h"
#include "../modules/camera/first_person_math.h"
#include "../modules/camera/first_person_retry.h"
#include <iostream>
#include <stdexcept>
using namespace BetterEndfieldNext::FirstPersonMesh;
void Check(bool ok,const char* message) {if(!ok)throw std::runtime_error(message);}
Vertex V(double x,double y,double z,uint32_t bone=0) {Vertex v;v.position={x,y,z};v.bone[0]=bone;v.weight[0]=1;return v;}
int main() {
    try {
        BetterEndfieldNext::FirstPerson::RetryBudget retries;
        int renderer_a=0,renderer_b=0,source_a=0,source_b=0;
        Check(retries.Try(&renderer_a,&source_a)&&retries.Try(&renderer_a,&source_a)&&
            !retries.Try(&renderer_a,&source_a),"unchanged binding exceeded retry limit");
        Check(retries.Try(&renderer_b,&source_a),"shared source consumed another renderer's retries");
        retries.Forget(&renderer_a);
        Check(retries.Try(&renderer_a,&source_a),"stale patch did not renew retries");
        Check(retries.Try(&renderer_a,&source_b)&&retries.Try(&renderer_a,&source_a),
            "source replacement or return did not renew retries");
        retries.Prune([&](void* renderer){return renderer==&renderer_b;});
        Check(retries.Try(&renderer_a,&source_a)&&retries.Try(&renderer_a,&source_a),
            "removed renderer retained retry history");
        retries.Clear();
        Check(retries.Try(&renderer_a,&source_a),"new session retained retry history");
        std::vector<Vertex> v;
        for(int layer=0;layer<2;++layer)for(int j=0;j<8;++j) {
            double a=j*6.283185307179586/8;v.push_back(V(.05*std::cos(a),layer?0:-.3,.05*std::sin(a)));
        }
        Part sides;
        for(uint32_t j=0;j<8;++j){uint32_t n=(j+1)%8;sides.indices.insert(sides.indices.end(),{j,j+8,n+8,j,n+8,n});}
        Frame neck{{0,0,0},{0,1,0},.1};
        auto r=Build(v,{sides},{2},neck,false,true);
        Check(r.error.empty()&&r.rings==1&&r.cap_triangles==6,"open neck must produce n-2 triangles");
        Check(std::equal(sides.indices.begin(),sides.indices.end(),r.parts[0].indices.begin()),"existing triangles changed");
        for(auto id:r.parts[0].indices)Check(id<v.size(),"cap introduced a new vertex");
        auto off=Build(v,{sides},{2},neck,false,false);
        Check(off.parts[0].indices==sides.indices,"disabled cap changed geometry");
        // UV seam: same position and weights, different index. Topology must weld it.
        auto seam=v;seam.push_back(v[8]);auto seam_part=sides;
        for(size_t i=0;i<6;++i)if(seam_part.indices[i]==8)seam_part.indices[i]=16;
        auto welded=Build(seam,{seam_part},{2},neck,false,true);
        Check(welded.rings==1&&welded.cap_triangles==6,"UV seam mistaken for an extra hole");
        seam.back().bone[0]=1;
        auto map=Weld(seam,1e-5);Check(map.back()!=8,"different skin weights welded");
        // Two disconnected components: collapse only the head-weighted component.
        std::vector<Vertex> split{V(0,0,0,0),V(1,0,0,0),V(0,1,0,0),V(2,0,0,1),V(3,0,0,1),V(2,1,0,1)};
        Part components{{0,1,2,3,4,5}};
        auto hidden=Build(split,{components},{0,1},{},false,false);
        Check(hidden.hidden_triangles==1&&hidden.parts[0].indices==std::vector<uint32_t>({0,1,2,3,3,3}),"component hide damaged body");
        auto all=Build(split,{components},{0,1},{},true,false);Check(all.hidden_triangles==2,"named head renderer not hidden");
        // Concave neck outlines require ear clipping, not a centre triangle fan.
        std::vector<Vertex> concave{V(0,0,0),V(2,0,0),V(2,2,0),V(1,1,0),V(0,2,0)};
        std::vector<uint32_t> triangles;
        Check(Triangulate({0,1,2,3,4},concave,triangles)&&triangles.size()==9,"concave triangulation failed");
        double area=0;for(size_t i=0;i<triangles.size();i+=3)area+=Length(Cross(concave[triangles[i+1]].position-concave[triangles[i]].position,concave[triangles[i+2]].position-concave[triangles[i]].position))*.5;
        Check(std::abs(area-3)<1e-10,"concave cap overlaps or covers outside polygon");
        std::vector<Vertex> bow{V(0,0,0),V(1,1,0),V(0,1,0),V(1,0,0)};
        Check(!Triangulate({0,1,2,3},bow,triangles),"self-intersection accepted");
        auto invalid=components;invalid.indices[2]=999;
        Check(!Build(split,{invalid},{0,1},{},false,true).error.empty(),"invalid index accepted");
        split[0].weight[0]=std::numeric_limits<double>::quiet_NaN();
        Check(!Build(split,{components},{0,1},{},false,true).error.empty(),"invalid weight accepted");
        // Part roles, ported from the upstream enhancer's camera_mesh.hpp.
        Check(IsDedicatedHeadMesh("s_actor_aglina_face_lod0"),"dedicated head mesh not recognised");
        Check(IsDedicatedHeadMesh("s_actor_aglina_hairshadow_lod0"),"hair shadow mesh not recognised");
        Check(IsDedicatedHeadMesh("s_actor_aglina_eyeshadow_lod0"),"eye shadow mesh not recognised");
        Check(IsDedicatedHeadMesh("s_actor_aglina_eyebrow_lod0"),"eyebrow mesh not recognised");
        Check(!IsDedicatedHeadMesh("s_actor_aglina_body_lod0"),"body mesh classified as a head mesh");
        Check(!IsDedicatedHeadMesh("s_actor_aglina_face_lod0_shadowproxy"),"shadow proxy accepted as a head mesh");
        Check(!IsDedicatedHeadMesh("aglina_face_lod0"),"role without the actor prefix accepted");
        Check(!IsDedicatedHeadMesh("s_actor_aglina_face"),"role without a lod suffix accepted");
        Check(IsBodyMesh("s_actor_aglina_body_lod0"),"body mesh not recognised");
        Check(!IsBodyMesh("s_actor_aglina_hair_lod0"),"head mesh classified as a body mesh");
        Check(!IsBodyMesh("s_actor_aglina_shadowproxy_body_lod0"),"body shadow proxy accepted");
        // Body skin hides only the triangles whose three vertices are dominated
        // by head/neck weights, and only when the mesh was classified as a body.
        std::vector<Vertex> skin_body{V(0,0,0,2),V(1,0,0,2),V(0,1,0,2)};
        Part skin_tri{{0,1,2}};
        Check(Build(skin_body,{skin_tri},{0,1,2},{},false,false,1.0,true).hidden_triangles==1,
            "body-skin triangle over the neck opening not hidden");
        Check(Build(skin_body,{skin_tri},{0,1,2},{},false,false,1.0,false).hidden_triangles==0,
            "body-skin hide applied without the body flag");
        std::vector<Vertex> mixed_skin{V(0,0,0,2),V(1,0,0,2),V(0,1,0,0)};
        Part mixed_tri{{0,1,2}};
        Check(Build(mixed_skin,{mixed_tri},{0,1,2},{},false,false,1.0,true).hidden_triangles==0,
            "partially skinned triangle hidden");
        // Camera math, ported from the upstream enhancer's camera_math.hpp.
        using namespace BetterEndfieldNext::FirstPersonMath;
        Check(ExpandLookPitch(Quat{0,0,0,1},1.f,1.f)==0.f,"disabled look range produced an offset");
        Check(std::abs(ExpandLookPitch(AxisAngle({1,0,0},-30.f),1.5f,1.f)+15.f)<1e-3f,
            "upward look range not extended by 1.5");
        Check(std::abs(ExpandLookPitch(AxisAngle({1,0,0},30.f),1.f,1.5f)-15.f)<1e-3f,
            "downward look range not extended by 1.5");
        Check(std::abs(ExpandLookPitch(AxisAngle({1,0,0},-80.f),2.f,2.f)+9.f)<1e-3f,
            "extended pitch not clamped to 89 degrees");
        const auto pitch_degrees=[](Quat q) {
            const auto forward=Rotate(q,{0,0,1});
            return -std::atan2(forward.y,std::hypot(forward.x,forward.z))*57.295779513f;
        };
        const Quat tilted=AxisAngle({1,0,0},-75.f);
        Check(LimitViewPitch(tilted,89.f,89.f).x==tilted.x,
            "default limits changed the original view");
        Check(pitch_degrees(LimitViewPitch(tilted,60.f,89.f))>-60.f,
            "upward limit did not contain the final pitch");
        Check(pitch_degrees(LimitViewPitch(AxisAngle({1,0,0},75.f),89.f,50.f))<50.f,
            "downward limit did not contain the final pitch");
        Check(std::abs(pitch_degrees(LimitViewPitch(tilted,0.f,89.f)))<1e-3f,
            "zero upward limit did not level the view");
        Check(std::abs(pitch_degrees(LimitViewPitch(AxisAngle({1,0,0},75.f),89.f,0.f)))<1e-3f,
            "zero downward limit did not level the view");
        const auto yawed=AxisAngle({0,1,0},45.f)*tilted;
        const auto limited_yaw=Rotate(LimitViewPitch(yawed,60.f,89.f),{0,0,1});
        Check(std::abs(limited_yaw.x-limited_yaw.z)<1e-4f,
            "pitch limit changed horizontal view direction");
        const auto near_top=AxisAngle({0,1,0},45.f)*AxisAngle({1,0,0},-89.f);
        const auto top_limited=Rotate(LimitViewPitch(near_top,30.f,89.f),{0,0,1});
        Check(std::abs(top_limited.x-top_limited.z)<1e-3f &&
            pitch_degrees(LimitViewPitch(near_top,30.f,89.f))>-30.f,
            "near-vertical upward look rotated sideways");
        Check(pitch_degrees(LimitViewPitch(AxisAngle({1,0,0},-70.f),60.f,89.f))>
            pitch_degrees(LimitViewPitch(tilted,60.f,89.f)),
            "reversing at the upward limit did not move immediately");
        Check(std::abs(LateralFacingYaw({1,0,0},{0,0,1})-45.f)<1e-3f,"strafe right did not turn 45");
        Check(std::abs(LateralFacingYaw({-1,0,0},{0,0,1})+45.f)<1e-3f,"strafe left did not turn -45");
        Check(LateralFacingYaw({0,0,1},{0,0,1})==0.f,"forward input turned the body");
        Check(LateralFacingYaw({0,0,-1},{0,0,1})==0.f,"backpedal turned the body");
        Check(std::abs(LateralFacingYaw({1,0,-1},{0,0,1})+45.f)<1e-3f,"backwards strafe sign inverted");
        Check(LateralFacingYaw({0,0,0},{0,0,1})==0.f,"zero movement produced a facing yaw");
        const Quat quarter=AxisAngle({0,1,0},90.f);
        Check(std::abs(BlendRotation(Quat{0,0,0,1},quarter,0.f).w-1.f)<1e-6f,"blend at zero changed rotation");
        Check(std::abs(BlendRotation(Quat{0,0,0,1},quarter,1.f).y-quarter.y)<1e-5f,"blend at one did not reach the target");
        Check(Unit(BlendRotation(Quat{0,0,0,1},quarter,.5f)),"blend produced a non-unit quaternion");
        Quat facing{};
        Check(FacingRotation({0,0,1},{0,1,0},&facing)&&std::abs(Rotate(facing,{0,0,1}).z-1.f)<1e-5f,
            "identity facing rotation not reconstructed");
        Check(FacingRotation({1,0,0},{0,1,0},&facing)&&std::abs(Rotate(facing,{0,0,1}).x-1.f)<1e-5f,
            "turned facing rotation not reconstructed");
        Check(!FacingRotation({0,0,0},{0,1,0},&facing),"degenerate facing frame accepted");
        using ShadowLease = BetterEndfieldNext::FirstPerson::ShadowModeLease;
        ShadowLease shadow;
        Check(shadow.Acquire(1) && shadow.Observe(3)==ShadowLease::Refresh::Keep,
            "shadow lease did not retain the original mode");
        Check(shadow.Observe(1)==ShadowLease::Refresh::Reassert,
            "game reset to the original mode should be eligible for reassertion");
        Check(shadow.Observe(2)==ShadowLease::Refresh::Relinquish &&
            shadow.PlanRestore(2,true)==ShadowLease::Restore::Done && !shadow.Acquire(2),
            "an external mode change must not be overwritten in this session");
        shadow={};
        Check(shadow.Acquire(1) && shadow.PlanRestore(3,true)==ShadowLease::Restore::Write,
            "our shadow-only mode should restore the saved mode");
        shadow.RestoreResult(false);
        Check(shadow.PlanRestore(-1,true)==ShadowLease::Restore::Retry,
            "unavailable mode read should retain the restoration lease");
        shadow.RestoreResult(false);shadow.RestoreResult(false);
        Check(shadow.blocked && shadow.PlanRestore(3,true)==ShadowLease::Restore::Retry,
            "repeated restore failure should exhaust the retry budget");
        shadow={};shadow.Acquire(1);
        Check(shadow.PlanRestore(3,false)==ShadowLease::Restore::Done && !shadow.owned,
            "destroyed renderer should release its lease");
        std::cout<<"first_person_mesh: retry lifecycle, geometry, seam, skin, body skin, roles, camera math, and invalid-input tests passed\n";
    }catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}
}
