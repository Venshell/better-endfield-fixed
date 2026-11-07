#include "../modules/camera/first_person_transition.h"
#include <iostream>
using namespace BetterEndfieldNext::FirstPerson;
bool Near(float a,float b) { return std::abs(a-b)<.001f; }
int main() {
    TransitionState state;
    PerspectivePose third{{0,0,0},{0,0,0,1},60,.1f};
    PerspectivePose first{{10,0,0},{0,0,0,1},80,.03f};
    StepTransition(state,third,false,.016f,.4f);
    auto out=StepTransition(state,first,true,.016f,.4f);
    if (!Near(out.position.x,0)) return 1;
    StepTransition(state,first,true,.1f,.4f);
    out=StepTransition(state,first,true,.1f,.4f);
    if (!Near(out.position.x,5)||!Near(out.fov,70)) return 2;
    const float before=out.position.x;
    out=StepTransition(state,third,false,.1f,.4f);
    if (!Near(out.position.x,before)) return 3;
    for(int i=0;i<4;++i) out=StepTransition(state,third,false,.1f,.4f);
    if (!Near(out.position.x,0)||state.blending) return 4;
    out=StepTransition(state,first,true,.1f,0);
    if (!Near(out.position.x,10)||state.blending) return 5;
    TransitionState bounded;
    StepTransition(bounded,third,false,.1f,.4f);
    StepTransition(bounded,first,true,.1f,.4f);
    out=StepTransition(bounded,first,true,100,.4f);
    if (!Near(out.position.x,1.5625f)) return 6;
    std::cout<<"first_person_transition: endpoints, midpoint, reversal continuity, zero duration and bounded time passed\n";
}
