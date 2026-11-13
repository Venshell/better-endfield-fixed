# EF Link protocol port

The headers in this directory are the platform-independent room stack from
[EF Link](https://github.com/Venshell/EFLink) 1.6.6, commit
`956a0d5d4b900f17ba06d7b30bc15d24770eb3a1`, itself a fork of
[guchang233/EFLink](https://github.com/guchang233/EFLink). Both projects are
AGPL-3.0, the same license as this repository.

`link_net.h` keeps the original relay, handshake, and message layout. Socket,
clock, and adapter calls go through `eflink_platform.h` so the same code builds
for the Android NDK and for the host test. The wire protocol number stays 1.

Not imported from EF Link: Dear ImGui, the DirectComposition menu, the Windows
module loader, and the IL2CPP world/combat adapter (`src/link_world.h` and
`src/authority/`). The Android sampler and panel live under
`android/app/src/main/cpp/modules/eflink/` and the Compose overlay.
