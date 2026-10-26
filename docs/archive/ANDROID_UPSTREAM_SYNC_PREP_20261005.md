# Android 上游同步与移植准备（2026-10-05）

> ⚠️ **清点部分已被取代**：本文基线为上游 `e8c1246`（v3.5.0），上游其后又推进 6 个提交到 `3510fa7`（**v3.5.1**）。最新清点见 `docs/ANDROID_UNPORTED_FEATURES_20261005.md`（冲突面 118 → **196**，我方独有 58 → **68**，上游独有 33 → **39**）。本文保留作为迁移合同与顺序参考。

> 对象：上游 `upstream` = `Dr-hydra/Better-Endfield`（只读）；我方 = `codex/android-thirdparty-alignment`。
> 本文只做**清点、合同与顺序**，不含合并动作。

## 0. 摘要

1. 上游 `main` 停在 `e8c1246`（`v3.5.0`，2026-10-04 21:48；2026-10-05 重新 fetch 复核，无更新）。相对共同基线 `9b1e895`：我方改 301 个文件、上游改 530 个，**双方都改 118 个**（2026-09-30 清单时只有 12 个，冲突面已扩大近十倍）。
2. **关键发现（更正报告口径）**：上游 **Android 侧的 MMD 播放与 EIEM DirectVmd 身体动作已经实现，并且已经接进 Android 原生构建**——`android/app/src/main/cpp/CMakeLists.txt` 直接编译 `eiem/eiem_body.cpp` 与 `eiem_slot0..3.cpp`，并带 ARM64 专用适配层 `eiem/compat/android_*`。本仓库这部分当前是 **0 文件**。因此「MMD 播放」不是从零设计，而是**移植上游已有的 Android 实现**。
3. 移植路径：**不做整树合并**（118 文件冲突面不可控），按功能块做文件级定向移植，保留我方 Compose 设置层与第一人称扩展。

## 1. 基线与量化

| 项 | 值 |
| --- | --- |
| 共同基线 | `9b1e895` Expand BEM v1.1/v1.2 runtime and tooling |
| 上游 HEAD | `e8c1246` chore: release Better Endfield 3.5.0 |
| 我方 HEAD | `35e92c8` Port Android third-party module foundation |
| 我方独有提交 | 58 |
| 上游独有提交 | 33 |
| 我方改动文件 | 301 |
| 上游改动文件 | 530 |
| 双方都改（冲突面） | **118** |
| 上游独有文件 | **412**（`native/modules` 111、`native/tests` 67、`android/app` 44、`ui` 30、`tools/CustomModel` 29、`native/shared` 21、`docs/archive` 23 …） |

判据：`git merge-base HEAD upstream/main` 定基线；两侧 `git diff --name-only <基线>..<ref>` 做交集/差集。

## 2. 上游 Android 现状（实测证据）

### 2.1 MMD + EIEM 身体动作已实现

| 证据 | 内容 |
| --- | --- |
| Android CMake | `betterendfield_desktop_features` 编译 `eiem/eiem_body.cpp`、`eiem/eiem_slot0..3.cpp`，include `eiem/`、`eiem/compat/`、`eiem/upstream/`，slot 文件加 `-fno-char8_t`；另编译 `native/shared/host/pose_lease.cpp` |
| 原生用户层 | `android/app/src/main/cpp/core/local_music_android.cpp`（本地音轨 JNI） |
| Java 引擎 | `MmdInstaller` / `MmdImportArchive` / `MmdImportPlan` / `MmdImportSession` / `MmdInstalledResources` / `MmdAudio` / `MmdVmdParser`（7 件，UI 无耦合，可直接移植） |
| Java 页面 | `MmdImportActivity` / `MmdLibraryActivity`（View/XML，需重写为 Compose） |
| 上游文档 | `docs/ANDROID_CAMERA_MMD_20261001.md`：Android 用**有界命令队列 + JSON 状态**替代 Windows 共享内存与伴生窗口；本地音轨用 `MediaPlayer` 实现同一 `BE_LocalMusicApiV1` 时间轴接口（会与游戏 BGM 叠加）；地形贴合用 `ComputeFloorDist` / `FindFloor` 按名解析 |
| 体量 | `native/modules/camera/eiem/` **54 文件 / 1,546,488 字节**（`ghost_rig.h` 420 KB、`trojan.h` 157 KB、`android_cp932_table.h` 127 KB、`direct_vmd_pose.h` 124 KB） |

许可：`eiem/LICENSE.EIEM` 为 **AGPL-3.0**（来源 `Sasye/EIEM` @ `1bc9baa`，含 `UPSTREAM.md` 的裁剪与补丁清单）。我方 `THIRD_PARTY_NOTICES.md` 已有 EIEM 条目（`camera_player.h` 采样），移植后须扩展为完整文件与义务声明。

### 2.2 两端 UI 形态不同（最大的适配成本）

- 上游 Android = **View/XML**：`SettingRow.java`、`SectionCard.java`、`MmdLibraryActivity`，资源 `res/values{,-en,-ja,-ko,-zh-rTW}/model_management.xml`、`overlay_settings.xml`。
- 我方 Android = **Compose**：`SettingsPages/SettingsShell/ToolPages/ExperiencePage/FirstPersonPage/BemInstallScreen` 等 20 个 `.kt`。
- 结论：**Java 引擎类与原生层可直接移植；页面必须用 Compose 重写**，且键名/默认值/取值范围必须与上游一致，否则存量配置与迁移路径会漂移。

### 2.3 上游没有 CI

上游仓库**不存在 `.github/`**（`git ls-tree upstream/main -- .github` 为空）。其 Android 能力只有作者自述，没有可复现构建证据，**不能作为验收依据**。

## 3. 我方已完成与缺口

已落地且有证据（沿用 2026-10-04 报告，本轮复核代码存在）：悬浮窗透明度/靠边吸附/关闭全部模型、预览闪退修复、BEM 1.3 体型参数、骨骼别名作用域、全局 FOV、自由镜头跟随、三方模块基础（`35e92c8`）。

缺口按块列在下一节。**判定口径**：代码在不在、能不能构建、有没有设备证据，三者分开记，不混用。

## 4. 分块移植计划

### A. MMD 播放与 EIEM 身体动作（最大块，优先做原生层）

| 项 | 内容 |
| --- | --- |
| 上游来源 | `e5e7dde`、`d1660db`、`617b197`、`4a13f2a`、`2c0cc12`(资源) |
| 原生 owner | `native/modules/camera/eiem/**`、`character_motion_runtime.inc`、`mmd_director_runtime.inc`、`mmd_library.h`、`camera_path.h`、`camera_file_worker.h`、`native/shared/motion/{vmd,character_pose,character_mapping,pose_lease_registry}.h`、`native/shared/host/pose_lease.cpp`、`native/modules/music/local_track.inc` |
| Android 专属 | `android/app/src/main/cpp/core/local_music_android.cpp`；Windows 伴生悬浮窗（`overlay/*`、`.rc`、`.manifest`、`mmd_overlay_protocol.h`）**不得进 Android 目标** |
| 构建接线 | `android/app/src/main/cpp/CMakeLists.txt`（eiem 目标 + include + `-fno-char8_t` + `pose_lease`） |
| **编译可行性（已实测）** | 补齐上游兼容层（`android_frame.cpp/h`、`android_camera.h`、`loaded_il2cpp.h`，我方缺这 4 件）并加 `-fno-char8_t` 后，用 NDK r27c clang 以 `--target=aarch64-linux-android21 -U_WIN32 -std=c++20 -fsyntax-only` 编译：**`eiem_body.cpp`、`eiem_slot0..3.cpp` 全部 5/5 OK**；MMD/motion 头 `mmd_overlay_protocol.h` / `mmd_library.h` / `camera_path.h` / `camera_file_worker.h` / `shared/motion/{vmd,character_pose,character_mapping,pose_lease_registry}.h` **8/8 OK**。 |
| 阻断判定 | 该块**没有已证实的编译阻断**；`module.cpp` 顶部 include 守卫问题（§5.2）只在把 Windows 头拖进共享 TU 时才成立，eiem 独立 TU 不受影响。 |
| **落地阻断（2026-10-05 实测，已推翻"分阶段"假设）** | **EIEM 无法以"死代码"形式先落地。** `betterendfield_desktop_features` 是 `STATIC` 库，链接器只拉取被引用的成员；没有任何 TU 引用 `EiemBody` 符号时，5 个目标文件全被丢弃。实测：只加源文件与 CMake 目标后，`libbetterendfield_android.so` 与本改动前**逐字节相同**（SHA-256 `8922461c…`），而 `libbetterendfield_desktop_features.a` 里 5 个 eiem 成员与 `EiemBody::Initialize/LoadStatus/SetOptions` 均在。**∴ `module.cpp` 集成是"代码进入产物"的前置条件，不是后续可选项。** |
| 采样口径 | `eiem/**` 的 `.h` **不能**按单头 `-fsyntax-only` 逐个判定：它是「按固定顺序 include 的片段集合」，单测会因缺少前置声明报 `unknown type name 'VmdVec3'/'Quat'` 等（实测 34 头中 21 个此类假失败）。**唯一有效门禁是编译 `eiem_slot*.cpp` / `eiem_body.cpp` 这几个真实 TU**（上表即按此口径）。 |
| 验收门槛 | ① ARM64 编译通过；② 设备上跑 `eiem/compat/tests`（`android_slot_tests`、`android_multislot_tests`，含 4 份 fixture VMD）；③ 真机 1/2/4 人同台、播放/暂停/跳转/循环、镜头模式、衣物物理三模式、地形贴合；④ 帧时与显存采样 + 切换回滚 |
| 风险 | AGPL 合规；32k 行 EIEM 依赖大量按名解析的游戏接口，缺接口只能降级不能崩溃 |

### B. 模型剩余链路

| 项 | 内容 |
| --- | --- |
| 上游来源 | `5f82b3c`（低峰值加载、世界内热切换、通用匹配）、`df44b59`、`510e83b`、`5eeb06d` |
| owner 文件 | `native/modules/custom_model/{generic_model_matcher.h, texture_binding_policy.h, mod_registry.*, module.cpp, bem.cpp/h}`、`android/.../modules/custom_model/{android_mesh_builder.cpp, world_resource_adapter.inc}`、`native/tests/{generic_model_matching_tests.cpp, android_world_binding_tests.cpp}` |
| 现状 | 我方已有通用匹配（67 条件宿主测试）与 3.4.4 的资源识别；**未接**：低峰值加载、场景原网格身份与阴影代理、分批贴图上传、场景热切换与原版恢复 |
| 验收门槛 | 宿主测试全绿 + 真机显存/帧时/切换/回滚四项分别取证 |

### C. Hook 链与动作诊断

| 项 | 内容 |
| --- | --- |
| 上游来源 | `368edd7`、`8285071` |
| owner 文件 | `native/shared/hooks/hook_chain.h`、`native/shared/include/BetterEndfield/HookChain.h`、`android/app/src/main/cpp/core/hook_broker.*` |
| 现状 | 内置模块同目标顺序 Hook 已移植；`HookInlineScan` 工具未移植（Windows 专用，属报告已声明的不移植项）；待复核 `8285071` 的「中断 Hook 缺失不阻断其他动作 Hook」是否已在 Hook broker 层覆盖 |
| 验收门槛 | Android Hook 链测试 + 模拟 Dobby broker 测试；真机 Hook 与内置模块并存 |

### D. 设置与页面适配（View → Compose）

| 项 | 内容 |
| --- | --- |
| 上游来源 | `3b28574`（热键统一与可选模块门控）、`4a13f2a`、`8424422`、`5eeb06d`、`3601444`、`34e8513`、`a4ef685` |
| 落点 | 我方 `ToolPages.kt` / `SettingsPages.kt` / `ExperiencePage.kt` / `CameraMotionPage.kt` |
| 前置 | 先产出**键位对照表**（上游 ini/xml 键 → 我方 `ModuleSettings` 常量，含默认值与范围），再写 Compose 页，避免配置漂移 |
| 验收门槛 | 真机逐页回归 + 持久化检查（含非法步长/越界输入拒绝） |

### E. 三方模块收尾

我方 `35e92c8` 已移植基础（导入 ZIP → 私有目录 → ARM64 dlopen → 本地 HTTP 桥，`127.0.0.1` 明文白名单）。待办：LSPosed 框架服务的真实 ZIP 导入、游戏进程 `dlopen`、网页桥端到端、旧代际存储治理；随后核对与内置模块 Hook 并存。

### F. 数据与版本

`manifests/combat/combat-dictionary.json`、`buff-sources.bemap`、`manifests/model/action-manifest.json`、`manifests/voice/voice-event-media-manifest.json`、`web/public/icons/icon-manifest.json` + 4 张 PNG、角色预设。纯数据、无代码耦合，单独一次提交独立验证。

## 5. 硬阻断与风险

1. **`LONG` 未定义——已修**（`native/shared/android_compat/include/android_win32.h`）。NDK clang `--target=aarch64-linux-android21` 复核：修复前 `mmd_overlay_protocol.h` **5 处 error**（第 98/99/100/104/105 行），修复后 **0 error**。
2. `module.cpp` 顶部 include 块无守卫：仍是把 Windows 专有头拖进共享翻译单元时最可能直接炸的点；eiem 独立 TU 不受影响（见 §4.A 实测）。
3. **兼容层缺件**：上游 `native/shared/android_compat/` 有 `android_frame.cpp`、`android_frame.h`、`android_camera.h`、`loaded_il2cpp.h`，我方没有（我方多出 `android_panel_commands.h`）。其中 `loaded_il2cpp.h` 是 `eiem/compat/android_slot.inc` 用**相对路径**引用的，位置必须是 `native/shared/android_compat/loaded_il2cpp.h`，放进 `-I` 无效。
4. `android/app/src/main/cpp/CMakeLists.txt` 双方都改：我方有 `third_party_host.cpp` 等，上游有 eiem 目标；合并必须人工处理，且**上游 MMD 伴生悬浮窗目标不得进 Android**。
5. View→Compose 重写量：`MmdLibraryActivity` 含 Spinner/EditText/Button 混合控件与多组即时保存语义，等于重做一页设置。
6. AGPL-3.0 义务：源码可得性与声明必须随移植同步。
7. 上游无 CI、无设备画面/性能证据；我方设备侧也尚无游戏资源，**任何「已验收」结论都必须由真机证据支撑**。

## 6. 建议执行顺序

1. 三方模块设备闭环（代码已在，路径最短）
2. MMD/EIEM **原生层导入**（文件 + CMake，**只增不覆**，**不得覆盖 `android_win32.*` / `android_virtual_keys.h`**）——已完成于 `ba18649`
3. **`module.cpp` 集成**（把 EIEM 真正接进相机模块；这是第 2 步生效的前置条件，二者必须视为一件事）
4. MMD **Java 引擎 + Compose 页面**（先出键位对照表）
5. 模型剩余链路（低峰值、热切换、阴影代理、分批贴图、原版恢复）
6. Hook 诊断复核 + 设置页逐页回归
7. 数据资源（F 块）单独提交
8. 全面 Android 验收 + Release 签名环境

每步固定门槛：`assembleDebug` + `assembleDebugAndroidTest` + 相关宿主/Python 测试 + 真机证据；D 盘固定 834 份头饰目录构建。

## 7. 未决问题

1. 实测设备（用户指明改用另一台）的型号、Android 版本、LSPosed 与游戏是否已在位。
2. 本 fork 是否保留上游 View 层（作为 MMD 页的临时载体）还是坚持全 Compose。
3. MMD 是否纳入本 fork 的发布范围（影响 AGPL 声明与包体）。
4. 委派边界：编码类杂活交 `zcode` CLI 的范围与验收责任划分。

## 附录：可复现命令

```bash
# 差距量化
BASE=$(git merge-base HEAD upstream/main)                 # 9b1e895
git diff --name-only $BASE..HEAD | sort > /tmp/our.txt
git diff --name-only $BASE..upstream/main | sort > /tmp/up.txt
comm -12 /tmp/up.txt /tmp/our.txt                         # 冲突面 118
comm -13 /tmp/our.txt /tmp/up.txt                         # 上游独有 412

# 体量
git ls-tree -r -l upstream/main -- native/modules/camera/eiem \
  | awk '{n++; s+=$4} END {print n" files, "s" bytes"}'

# 导出上游文件（保留相对布局，相对 include 才解析得到）
git archive upstream/main native/modules/camera/eiem \
  native/shared/android_compat native/shared/motion native/shared/host \
  native/shared/include/BetterEndfield | tar -x -C /tmp/up

# Android 目标编译校验（TU 口径，不是单头口径）
CLANG=/d/android-toolchain/android-ndk-r27c/toolchains/llvm/prebuilt/windows-x86_64/bin/clang++.exe
SYSROOT=/d/android-toolchain/android-ndk-r27c/toolchains/llvm/prebuilt/windows-x86_64/sysroot
INC="-I /tmp/up/native/shared/android_compat/include -I /tmp/up/native/shared/android_compat \
     -I /tmp/up/native/shared/include -I /tmp/up/native/shared/include/BetterEndfield \
     -I /tmp/up/native/modules/camera/eiem -I /tmp/up/native/modules/camera/eiem/compat \
     -I /tmp/up/native/modules/camera/eiem/upstream -I android/app/src/main/cpp"
for tu in eiem_body eiem_slot0 eiem_slot1 eiem_slot2 eiem_slot3; do
  "$CLANG" --target=aarch64-linux-android21 --sysroot="$SYSROOT" -U_WIN32 -fno-char8_t \
    -std=c++20 -fsyntax-only $INC "/tmp/up/native/modules/camera/eiem/$tu.cpp" && echo "$tu OK"
done
```

注意：`-U_WIN32` 必须加（否则替身层 `Windows.h` 的 `#if defined(_WIN32) #error` 会全部假失败）；`eiem/compat/android_slot.inc` 用相对路径引 `../../../../shared/android_compat/loaded_il2cpp.h`，所以兼容层必须落在与上游一致的相对位置。
