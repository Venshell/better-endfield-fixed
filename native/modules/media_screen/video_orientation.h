#pragma once
#include <cstddef>

namespace BetterEndfieldNext::MediaScreen {
// MediaFormat rotation is clockwise in top-left image coordinates. Unity's
// raw Texture2D rows start at the bottom, so rotation precedes the row flip.
struct VideoOrientation {
    int width, height, clockwise;
    static bool Valid(int degrees) { return degrees==0 || degrees==90 || degrees==180 || degrees==270; }
    int Width() const { return clockwise==90 || clockwise==270 ? height : width; }
    int Height() const { return clockwise==90 || clockwise==270 ? width : height; }
    size_t UnityPixel(int x, int y) const {
        int output_x=x, output_y=y;
        switch(clockwise) {
            case 90: output_x=height-1-y; output_y=x; break;
            case 180: output_x=width-1-x; output_y=height-1-y; break;
            case 270: output_x=y; output_y=width-1-x; break;
            default: break;
        }
        return static_cast<size_t>(Height()-1-output_y)*Width()+output_x;
    }
};
}
