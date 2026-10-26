# Android 移植进度快照（2026-10-05）

> 分支 `codex/android-thirdparty-alignment` @ `5e62a34`；`versionCode 30402` / `versionName 3.4.2`。
> 上游基线 `upstream/main` = `e8c1246`（v3.5.0）；共同基线 `9b1e895`。
>
> **口径**：每个功能块分三档独立记，**不合并**——
> **① 代码**（文件在不在）、**② 构建**（能不能编译进 Android 目标）、**③ 设备**（有没有真机证据）。
> 「构建通过」不构成「能用」的结论。

## 0. 总览

| # | 功能块 | ① 代码 | ② 构建 | ③ 设备证据 | 归属 |
| --- | --- | --- | --- | --- | --- |
| 1 | 第一人称（含陀螺仪接入） | 已落地 | 通过 | 部分（3.4.4 报告已验；陀螺仪校准未闭环） | 我方扩展 |
| 2 | 自由镜头 + VMD **相机**轨迹 | 已落地 | 通过 | 已验（导入路径已接通） | 双方共有 |
| 3 | 悬浮窗 UI（透明度/吸附/关闭全部模型） | 已落地 | 通过 | 已验 | 我方扩展 |
| 4 | 自定义模型（体型/骨骼别名/贴图/通用匹配） | 部分 | 通过 | BEM 1.3 参数已验，其余未验 | 双方 |
| 5 | 三方模块宿主（导入→dlopen→HTTP 桥） | 全链路已落地 | 通过 | **仅独立测试程序**，游戏内零验收 | 我方新增 |
| 6 | Hook 链 / 动作诊断 | 部分 | 通过 | 未验 | 双方 |
| 7 | MMD 播放（作品库 + 导演 + 相机路径） | **原生层已接线**（`cfee7c2` + 本轮：热键 / 配置下发 / 面板命令入口）；管理页 0 文件 | 通过；`.so` 48,712,256 B，含 `AndroidMmdCommand`/`AndroidMmdStatus` | 无 | 上游已有，UI 待重写 |
| 8 | EIEM 身体动作（DirectVmd） | **已落地并进入产物**（`cfee7c2`） | 5/5 TU OK（`-Werror`）；`.so` 含 42 个 `EiemBody` 符号、0 未解析 | 夹具：单槽 PASS；四槽 1 项子断言失败（见 §6） | 上游已有，已集成 |
| 9 | 本地音轨时间轴 | **已落地**（`local_music_android.cpp` 进 Android 目标） | 通过；`.so` 含 `AndroidLocalMusicApi` | 无 | 上游已有，已集成 |
| 10 | 数据与版本资源（F 块） | 未开始 | — | 无 | 双方 |

## 1. 已落地（代码 + 构建 + 设备三档齐全）

**第一人称**：`native/modules/camera/first_person_*.{h,inc}` 共 **31 件**（我方独有，上游无此集合），加 Android 侧 `android/app/src/main/cpp/modules/camera/first_person_look_probe.{cpp,h}`（5,391 B）、`GyroscopeController.java`（12,688 B）、`FirstPersonPage.kt`（14,651 B）。
证据：`assembleDebug` + `assembleDebugAndroidTest` BUILD SUCCESSFUL（本次实跑），APK 79,769,895 B / SHA-256 `e7a36a76…a6a2` / 834 个 `.behw`。
遗留：陀螺仪灵敏度标定（`PIXELS_PER_RADIAN`、像素→百分比换算）尚未在真机闭环。

**自由镜头 + VMD 相机轨迹**：`free_camera_runtime.inc`（含 `LoadVmdCamera`，64 MiB 上限、30 fps、相机段解析）、`camera_follow.h`（两端都有）。UI 门控见 `OverlayFeatures.java` 的 `vmdCamera` 字段（要求 `CAMERA_FREE && CAMERA_VMD_IMPORTED`）。
**边界**：这是**相机轨迹**回放，与 MMD 骨骼动作（第 8 块）是两条独立链路，不可混为一谈。

**悬浮窗**：`GameOverlay.java`（`autoSnap` / `snapToEdge()`）、`OverlayFeatures.java`、`OverlayControls.kt`、`OverlayPanel.kt`、`OverlaySurface.kt`、`OverlayTheme.kt`、`OverlayViewOwners.java`、`FloatingHandle.kt`。

**自定义模型（部分）**：已落地 `bem.cpp`（骨骼别名作用域）、BEM 1.3 体型参数、通用匹配（67 条件宿主测试）、`android_mesh_builder.cpp`、`world_resource_adapter.inc`。
缺口：低峰值加载、场景原网格身份与阴影代理、分批贴图上传、场景热切换与原版恢复。

**三方模块宿主（`35e92c8`）**：`ThirdPartyModuleStore/Package/Materializer/Activity(s).java|kt` + `native/shared/third_party_modules/third_party_host.cpp` + 宿主测试 `native/tests/third_party_host_android/`。链路已逐行核实：导入 ZIP → 私有目录 → ARM64 `dlopen` → 本地 HTTP 桥（`127.0.0.1` 已在 `module_network_security.xml` 白名单）。
**未验**：LSPosed 框架服务真实 ZIP 导入、游戏进程 `dlopen`、网页桥端到端、Release 签名环境。

## 2. 待移植（上游已有实现，我方 0 文件）

差异口径：`git ls-tree -r --name-only HEAD/upstream -- <dir>` 直接 `comm` 对比文件清单。

| 块 | 上游文件 | 我方状态 |
| --- | --- | --- |
| MMD 原生 | `mmd_library.h`、`camera_path.h`、`camera_file_worker.h`、`mmd_director_runtime.inc`、`character_motion_runtime.inc` | 全部缺失 |
| MMD Java 引擎 | `MmdInstaller`、`MmdImportArchive`、`MmdImportPlan`、`MmdImportSession`、`MmdInstalledResources`、`MmdAudio`、`MmdVmdParser`（7 件，UI 无耦合） | 全部缺失 |
| MMD 页面 | `MmdLibraryActivity`、`MmdImportActivity`（View/XML，五语言资源） | 缺失，须用 Compose 重写 |
| EIEM | `native/modules/camera/eiem/**` **54 文件 / 1,546,488 B**（AGPL-3.0，`LICENSE.EIEM` + `UPSTREAM.md`） | 0 文件 |
| motion 数据层 | `native/shared/motion/{vmd,character_pose,character_mapping,pose_lease_registry}.h`、`native/shared/host/pose_lease.cpp` | 整目录缺失 |
| 本地音轨 | `native/modules/music/local_track.inc`、`android/app/src/main/cpp/core/local_music_android.{cpp,h}` | 缺失（我方 `music` 模块只有 `module.cpp`/`music_bank.h`/`omni_pcm_abi.h`） |
| 输入 | `native/shared/input/hotkey.h` | 缺失 |
| 构建接线 | `android/app/src/main/cpp/core/jni_binding.h`、`runtime_status.h` | 缺失（我方有 `panel_commands.cpp`） |

**编译可行性（已实测，非推断）**：补齐上游兼容层缺件后，NDK r27c clang `--target=aarch64-linux-android21 -U_WIN32 -fno-char8_t -std=c++20 -fsyntax-only` 编译真实 TU：`eiem_body.cpp` + `eiem_slot0..3.cpp` **5/5 OK**；MMD/motion 8 个头 **8/8 OK**。
→ **该块没有已证实的编译阻断**，属定向文件级移植。

兼容层缺件（我方无、上游有）：`android_frame.{cpp,h}`、`android_camera.h`、`loaded_il2cpp.h`。其中 `loaded_il2cpp.h` 被 `eiem/compat/android_slot.inc` 以**相对路径**引用，必须落在 `native/shared/android_compat/loaded_il2cpp.h`，加 `-I` 无效。

## 3. 已排除的阻断与不移植项

1. `LONG` 未定义 → **已修**（`android_win32.h`）。修复前后 NDK clang 实测：`mmd_overlay_protocol.h` 5 error → 0 error。
2. `module.cpp` 顶部 include 无 `__ANDROID__` 守卫：仅在把 Windows 专有头拖进共享 TU 时成立；eiem 独立 TU 不受影响。
3. **不移植**：Windows 伴生悬浮窗 `native/modules/camera/overlay/{main.cpp, mmd_overlay.manifest, mmd_overlay.rc}`、`mmd_overlay_protocol.h`、`HookInlineScan`。
4. 上游**无 `.github/`（无 CI）**，其 Android 能力只有作者自述，**不得作为验收依据**。

## 4. 采样口径告诫

`eiem/**` 的 `.h` **不能**按单头 `-fsyntax-only` 逐个判定：它是「按固定顺序 include 的片段集合」，单测会因缺前置声明报 `unknown type name 'VmdVec3'/'Quat'`（实测 34 头中 21 个此类假失败）。
**唯一有效门禁 = 编译 `eiem_slot*.cpp` / `eiem_body.cpp` 真实 TU**，外加设备侧 `eiem/compat/tests/{android_slot_tests,android_multislot_tests}`（含 4 份 fixture VMD）。

## 5. EIEM 原生层导入实测（`ba18649`，2026-10-05）

**做法**：只新增、不覆盖。73 个新文件，0 个既有文件被改。严格不导入 Windows 伴生悬浮窗（`overlay/**`、`mmd_overlay_protocol.h`）。
**不得覆盖的原因**：我方 `android_win32.cpp` / `android_virtual_keys.h` 是重写过的更强版本，含 `AddVirtualMouseDelta` / `DrainVirtualMouseDelta`（陀螺仪与面板 look pad 的注入链路），上游版本没有。整文件覆盖会静默删掉这条链路。

| 验证 | 结果 |
| --- | --- |
| 上游自带严格门禁（`--target=aarch64-linux-android24 -std=c++20 -fno-char8_t -Wall -Wextra -Werror`） | `eiem_body` + `eiem_slot0..3` **5/5 OK** |
| `assembleDebug` + `assembleDebugAndroidTest`（834 份头饰目录） | **BUILD SUCCESSFUL**，0 error |
| 静态库成员 | `libbetterendfield_desktop_features.a`（40,976,446 B）含全部 5 个 eiem 成员，`EiemBody::Initialize/LoadStatus/SetOptions` 为 `T` |
| **最终 `.so`** | **未变**——`libbetterendfield_android.so` SHA-256 `8922461c…`，与本改动前**逐字节相同** |

**根因**：`betterendfield_desktop_features` 是 `STATIC` 库，链接器只拉取被引用的成员。没有 TU 引用 `EiemBody` 符号时 5 个目标文件全被丢弃。
**∴ `module.cpp` 集成是"代码进入产物"的前置条件。**

## 6. EIEM 设备夹具实测（HLK-AL00 / arm64-v8a）

设备无宿主编译器（无 `g++`），改为把上游夹具**交叉编译成 ARM64 可执行文件**并推到设备运行——比语法检查硬。

| 夹具 | 结果 |
| --- | --- |
| `android_slot_tests`（单槽） | **PASS（EXIT=0）**：Avatar 自然绑定、衣物根锚点、SMC 姿态+快照还原、扭转骨、独立腿/趾 IK、盒装地形查询+可行走性+高度响应+暂停+异常能力状态、Stable/Freeze 衣物还原、异常门控、GC pin、CP932/UTF16 |
| `android_multislot_tests`（四槽） | 四槽 VMD 加载/采样、导演 seek、FinalIK 抑制与放行、SMC、Stop/Start、GC 清理**全部通过**；**唯一失败项**：`floorQueries>100 && y > flatHeight+0.10` 的高度半边 |

**四槽失败项的实测定位**（诊断副本，未改仓库源码）：

```
warmup pumps=1 baselineValid=1 baseline=0.000      ← 基线在首帧即建立于 y=0
fq=6052 dy=0.0000 flat=1.0000 y=1.0000 avail=1 reason=[] 
rootOffset=0.0000 baseline=0.300 baseValid=1 pelvisY=1.0000
```

- `floorQueries=6012~6052` → 地形探针**大量执行**，`>100` 半边通过。
- `baseline` 由 **0.000 变为 0.300**：`baselineValid` 的**唯一**清零点就是 `ResetTerrain()`（`compat/android_terrain.inc:79-80`，同时清零 `rootOffset`）——∴ 90 次 seek 中**确实发生了一次地形重置**，基线按新地面 0.3 重新锚定。
- 重置后相对偏移为 0 → `rootOffset=0` → 骨骼 Y 停在作者姿态 1.0000，未抬升。
- 触发条件：`ApplyTerrain` 的 `step<-.001 || step>8`（`android_terrain.inc:115`，即帧号回跳或前跳超过 8 帧），以及显式 Stop/Start、`options.terrain` 变更路径。

**结论边界**：适配层的地形**查询**链路在 ARM64 上工作正常（探针 6000+ 次、采到正确地面 0.300、契约解析无 reason）；夹具的抬升断言隐含「地面变更后不再发生重置」，本机运行不满足。
**未决**：无宿主编译器 → 无法做 Windows 侧 A/B，**不能断言"仅设备侧复现"**。此项在集成 `module.cpp` 后须重测。

## 7. module.cpp 集成实测（`cfee7c2`，2026-10-05）

**做法**：不碰任何双方分叉的第一人称代码，只做**加法**——补 include、配置字段、全局量、调用点。

| 项 | 结果 |
| --- | --- |
| 兼容层缺口 | `android_win32.h` 补 11 个虚拟键（`VK_MENU/LWIN/RWIN/RETURN/SPACE/TAB/ESCAPE/MULTIPLY/ADD/DECIMAL/DIVIDE`）。补齐后**上游整个 `module.cpp` 在我方兼容层上 0 error** |
| `free_camera_runtime.inc` | **前向移植**（非并集）。先测：我方增量 +73/−1、上游 +282/−298，且我方那 4 处（`g_free_follow_anchor`、`FollowCharacterTranslation`、`ScopedGlobalFovState`、鼠标钩子 `_WIN32` 守卫）**上游全有** → 取上游版不丢东西；唯一实质差异 `first_person_transition` 形参以默认值补回 |
| `module.cpp` | +135 行，**无一处既有代码被改写** |
| 构建 | `assembleDebug + assembleDebugAndroidTest` **BUILD SUCCESSFUL**，且 `stripDebugDebugSymbols` / `packageDebug` **实跑**（上一轮是 UP-TO-DATE，导致 APK 里是旧 `.so`） |
| `.so` | 29,836,360 → **48,587,344** 字节（未 strip）；APK 79,769,895 → **80,434,013**，sha256 `d4c5901d7422…` |
| 符号 | `.so` 中 **42 个 `EiemBody` 符号**，`Initialize`/`LoadStatus`/`SetOptions`/`EnsureActor` 均为 `T`，**0 个 `U`** |
| 回归 | 同一 `.so` 中我方链路仍在：`AddVirtualMouseDelta`、`DrainVirtualMouseDelta`、`AndroidRestoreHeadwearFixture`、相机模块导出、`Mmd::Stop`、`AndroidLocalMusicApi` |

**合并规模实测（三方合并，基线 `9b1e895`）**：`module.cpp` 我方 +882/−90、上游 +1414/−217 → **37 处冲突**，其中约 10 处属深度语义分叉（上游重构了头部部件探针为多渲染器向量版、重写了第一人称状态块与 `ReadPartComponents`/`FindHeadBoneRecursive` 签名、重写了输入线程的键盘钩子）。
**决策**：这些区域**一律取我方**——上游那侧是另一套实现，与 EIEM/MMD 无依赖关系，取上游等于删掉本 fork 的第一人称成果。当时留下的代价是「输入循环里的 MMD 热键未接线」，**本轮已补齐**（见下）。

**关键教训（本轮最大的一个）**：不要假设"分叉 = 对抗"。本仓库 151 个双方共同改动文件里，绝大多数是**纯增量**；正确做法是**先量增量规模，再把小的那侧前向移植到大改动的那侧**，而不是盲目并集。只有两侧都大改的文件（`module.cpp`、`first_person_runtime.inc`、`native_bridge.cpp`、Java UI）才需要逐 hunk 判决。

### MMD 原生接线（本轮）

`cfee7c2` 只把 `mmd_director_runtime.inc` 挂进了编译单元，**没有任何调用面**：热键不产生请求、配置不下发到全局量、Android 侧无入口。本轮补的是这三段，实测 `module.cpp` **+242 / −3**，既有逻辑里只有 `focused` 一处被刻意放宽（其余绑定各自带开关，不受影响；另两处 − 是注释与 `{}` 块的改写）：

| 段 | 内容 |
| --- | --- |
| 输入线程 | `mmd_keys[]` 绑定表（play/stop/camera_mode/seek_back/seek_forward/overlay）+ 按下沿派发到 `g_mmd_requests` / `g_mmd_seek_steps`；`focused` 纳入 `mmd_enabled`（其余绑定各自带开关，不会提前触发）；`PumpMmdOverlayHost(mmd_enabled)` 归位 |
| 配置 | `CameraConfiguration` 补 `mmd_overlay_enabled` / `mmd_overlay_visible` / `keyframe_file`；17 个 `mmd_*` INI 键（键名与上游逐字一致）；`ConfigurationChanged` 补 MMD 全局量下发，含此前**从未被赋值**的 `g_vmd_motion_file` / `g_mmd_music_file` / `g_mmd_face_file` / `g_mmd_work`（+ 代际递增）/ `g_keyframe_file`（Android 下由 `BETTER_ENDFIELD_MMD_ROOT` 派生 `camera-path.becam`）/ `g_asset_config_generation` |
| Android 入口 | 实现 `android_camera.h` 里**只有声明、全仓无实现**的 `betterendfield::AndroidMmdCommand` / `AndroidMmdStatus`（定义在模块命名空间外，经 `QueueMmdCommandForAndroid` / `MmdStatusForAndroid` 转发进入匿名命名空间）+ 面板命令 `mmd`（`<verb> [arg]`：`play_pause` / `stop` / `loop` / `seek <s>` / `seek_absolute <s>` / `camera 0|1|2|next` / `work <folder>`），由 `PumpFromEngineTick` 每帧排空 |

顺手修掉两个**上游有、我方缺**的隐患：inc 引用的 `g_mmd_overlay_enabled` / `g_mmd_overlay_initial_visible` / `g_mmd_overlay_toggle_request` 全仓无定义（Android 因 `#if defined(_WIN32)` 屏蔽而侥幸编过，**Windows 构建必断**）；`playback_keys[]` 缺 `keyframe_save/load` 两项绑定。

**证据**：NDK r27c `aarch64-linux-android24-clang++ -fsyntax-only -Wall -Wextra` 退出码 0（告警 8 → 6，消除的正是上述两个死全局量）；`:app:externalNativeBuildDebug` **BUILD SUCCESSFUL**；`.so` 48,587,344 → **48,712,256 B**，`AndroidMmdCommand` / `AndroidMmdStatus` / `QueueMmdCommandForAndroid` / `MmdStatusForAndroid` 四个符号均为 `T`，我方 `AddVirtualMouseDelta` / `DrainVirtualMouseDelta` 仍在。

**Java 侧现状**：`ModuleCommandRouter.issue(context, command, value)` 已是通用发送口（命令名须匹配 `[a-z][a-z0-9_]{1,31}`），故 `issue(ctx, "mmd", "play_pause")` 即可驱动；回执经 `readStatus()` 读回。缺口是**没有页面去调它**——MMD 管理页仍是 0 文件，且 `AndroidMmdStatus()` 尚无 JNI 暴露面（面板目前读不到播放状态/作品列表，只有命令回执）。

## 8. 下一步（顺序不变）

1. 三方模块设备闭环（代码已在，路径最短）
2. ~~MMD/EIEM 原生层~~ **已完成**：导演 + EIEM + 本地音轨 + 热键 + 配置下发 + 面板命令入口，全部进 `.so`
3. MMD Java 引擎 + Compose 页面（命令词表已定：`mmd` 命令的 7 个动词见 §7；页面调用 `ModuleCommandRouter.issue`），并在 `native_bridge.cpp` 暴露 `AndroidMmdStatus` —— 面板要看播放状态与作品列表就绕不开这一步
4. 模型剩余链路
5. Hook 诊断复核 + 设置页逐页回归
6. 数据资源单独提交
7. 全面 Android 验收 + Release 签名环境

---

*本文是**状态快照**；要做的事、合同与执行顺序见 `ANDROID_UPSTREAM_SYNC_PREP_20261005.md`。*
