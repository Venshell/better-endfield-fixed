#include "../../modules/media_screen/video_orientation.h"
#include <array>
#include <iostream>
#include <stdexcept>
#include <string>
using BetterEndfieldNext::MediaScreen::VideoOrientation;
int main() {
    int checks=0;
    auto check=[&](bool value,const char* reason){++checks;if(!value)throw std::runtime_error(reason);};
    try {
        const std::array<int,4> angles{0,90,180,270};
        const std::array<std::string,4> expected{"DEFABC","FCEBDA","CBAFED","ADBECF"};
        for(size_t index=0;index<angles.size();++index) {
            VideoOrientation frame{3,2,angles[index]};
            std::string output(6,'?');
            for(int y=0;y<2;++y)for(int x=0;x<3;++x) {
                const size_t pixel=frame.UnityPixel(x,y);
                check(pixel<output.size(),"pixel outside oriented output");
                output[pixel]=char('A'+y*3+x);
            }
            check(output==expected[index],"clockwise rotation and Unity row flip mismatch");
            check(frame.Width()*frame.Height()==6,"rotation changed pixel count");
        }
        VideoOrientation recording{720,1584,270};
        check(recording.Width()==1584 && recording.Height()==720,"phone recording display dimensions");
        check(!VideoOrientation::Valid(45) && !VideoOrientation::Valid(-1),"unsupported metadata angle accepted");
        std::cout<<"PASS video orientation: "<<checks<<" checks\n";
    } catch(const std::exception& error) {std::cerr<<"FAIL video orientation: "<<error.what()<<'\n';return 1;}
}
