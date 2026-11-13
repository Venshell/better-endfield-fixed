// Ported from EF Link (AGPL-3.0).
// Upstream: https://github.com/Venshell/EFLink @ 956a0d5d4b900f17ba06d7b30bc15d24770eb3a1 (1.6.6),
// fork of https://github.com/guchang233/EFLink.
// Wire layout is unchanged. Platform calls live in eflink_platform.h.
#pragma once
#include <array>
#include <cstddef>

namespace eflink {
// Match physical press/release pairs across menu visibility changes. Separate
// IDs are used for window messages and raw input, because Unity can use either.
class InputOwnership {
    enum class Owner : unsigned char { None, Game, Menu };
    std::array<Owner,1024> owners{};
public:
    bool route(std::size_t id,bool down,bool menu) {
        if(id>=owners.size())return !menu;
        auto& owner=owners[id];
        if(down) {
            if(owner==Owner::None)owner=menu?Owner::Menu:Owner::Game;
            return owner==Owner::Game;
        }
        const bool forward=owner==Owner::Game||(owner==Owner::None&&!menu);
        owner=Owner::None;
        return forward;
    }
    void seedGame(std::size_t id) {if(id<owners.size())owners[id]=Owner::Game;}
    void focusLost() {owners.fill(Owner::None);}
};
}
