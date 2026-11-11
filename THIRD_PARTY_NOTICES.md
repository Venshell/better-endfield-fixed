# Third-Party Notices

## Better Endfield (upstream project)

- Source: https://github.com/Dr-hydra/Better-Endfield
- License: GNU Affero General Public License v3.0 (the same license this
  repository continues to use; see `LICENSE`)
- Relationship: this repository is a derivative of that project and is
  **independently maintained**. It is not an official upstream version and does
  not represent the upstream author. The upstream code, documentation and design
  remain the work of their original authors — principally `Dr-hydra`, with every
  contributor recorded in the preserved upstream commit history.
- Used in: the repository as a whole. The upstream runtime, modules, UI,
  installer and documentation form the base of this work; the modifications
  introduced here are listed in `CHANGELOG.md` and described in `README.md`
  ("Upstream & Project Origin").

## EIEM (Importing Endfield MMD)

- Source: https://github.com/Sasye/EIEM
- License: GNU Affero General Public License v3.0 (the same license as this project)
- Used in: `native/modules/camera/free_camera_runtime.inc`, where the VMD camera
  sampling follows EIEM's `camera_player.h` (Bezier evaluation, Euler signs,
  180 degree basis, 0.07 scale and 5 degree FOV defaults) and `vmd_parser.h`
  (camera record layout).

- Additional use (2026-09-27): `native/shared/motion/vmd.h` follows the
  section layouts and timeline conventions in EIEM `src/vmd_parser.h` and
  `src/camera_player.h`, reviewed at commit
  `447600b9de959d8b709ad84b19e605eeb8a2a0a9`.
  The bounded portable parser, transactional file handling and camera path format
  are adapted/implemented for Better Endfield. No EIEM binaries or assets are included.

### Character semantic mapping reference

`native/shared/motion/character_mapping.h` contains semantic bone/morph-name mappings
reviewed against Sasye/EIEM `src/bone_map.h` and `src/smc_face.h` at
`8b46b76b33e3f61825b6b8c55d983bcf2c1bc94c` (AGPL-3.0).
No upstream executable, private SMC memory layout, hash constant, or game asset is included.

### DirectVmd body playback (2026-09-29)

`native/modules/camera/eiem/upstream/` is a source snapshot of Sasye/EIEM at
commit `1bc9baa` (AGPL-3.0, copy in `native/modules/camera/eiem/LICENSE.EIEM`):
VMD parser, DirectVmd sampling/source rig/PMX reference, ghost rig with FinalIK
leg and knee handling, terrain follow and SkeletalMorphCore face code. It is
compiled into `BetterEndfieldNext.Camera.dll` through `eiem/eiem_body.cpp`, which
replaces EIEM's loader, GUI, audio and hook installation. Local modifications
are listed in `native/modules/camera/eiem/UPSTREAM.md`.

## Endfield-Poser

- Source: https://github.com/honxi1/Endfield-Poser (reviewed read-only)
- Used as a design reference only for squad playback in the Camera module:
  reading the current squad through `GameInstance.get_player` →
  `GamePlayer.squadManager` → `SquadManager.GetMemberBySlot`, and sharing one
  stage origin between dancers. No Endfield-Poser source code is included.

## miniz

- Source: https://github.com/richgel999/miniz, release 3.1.2 amalgamated pair
  (`miniz.c` + `miniz.h`), vendored verbatim under
  `native/shared/third_party/miniz` with its upstream `LICENSE`.
- License: MIT, per the upstream LICENSE file.
- Used by the shared package decoder (`bem.cpp`) to read the BEM ZIP container
  described in format specification section 14. Only the ZIP reader APIs are
  used; no miniz source is modified.

## Android overlay visual assets

- `android/app/src/main/res/drawable-nodpi/overlay_cloud_contours.png` came from `res/sy.png` in the user-provided `EndfieldCloud-1-1.6.0.apk` for local design and validation. Redistribution rights have not been verified.
- `android/app/src/main/res/drawable/overlay_settings_gear.xml` adapts Google's Material Design Icons `settings` 24px SVG from https://github.com/google/material-design-icons under Apache License 2.0. The source path was converted to Android vector XML and recolored.
