# 参考源仓库未同步功能清单（2026-09-30）

> 对象：参考源 `origin` = `Dr-hydra/Better-Endfield`（只读）；本文档只做**清点与风险实测**，不含合并动作。

## 0. 结论摘要

上游 `origin/main` 已从共同基线 `9b1e895` 推进到 **`2c0cc12`（tag `v3.4.0`）**，新增 3 个提交、改动 106 个文件；我方 `main` 同期领先 21 个提交。

三个判断：

1. **这批改动整体是桌面侧新功能**（MMD 播放 + 角色身体动作 + 本地音轨），上游 `CHANGELOG` 明确写「本次仅发布 Windows 版，Android 版本保持 3.3.0」——**上游自己的 Android 侧一行都没接**。因此不存在「上游 Android 已修好、我们没跟」的情况。
2. **12 个文件与我们改过的文件重叠**，其中 **5 个落在 Android 也会编译的共享翻译单元**，合并需要人工处理，不能整树 checkout。
3. **已实测到 1 个合并硬阻断点**：上游 `mmd_overlay_protocol.h` 依赖 `LONG`，我方 Android 替身层 `android_win32.h` 只定义了 `ULONG`。若按现状整文件合并，**Android 构建会在 `camera/module.cpp` 编译期直接失败**。11 个新增头文件中 10 个已在真实 NDK clang 下验证可编译（见 §4.1）。

## 1. 比对基线与方法

| 项 | 值 |
| --- | --- |
| 共同基线 | `9b1e895` Expand BEM v1.1/v1.2 runtime and tooling |
| 上游 HEAD | `2c0cc12` Release desktop 3.4.0 and sync hot-update character resources |
| 上游领先 | 3 个提交 |
| 我方领先 | 21 个提交 |
| 上游相对基线改动 | 106 个文件（native 侧 +37,484 / −308 行） |
| 纯未同步（上游改、我方未动） | **92 个文件** |
| 双方都改（潜在冲突） | **12 个文件** |
| 上游有我方无 | 102 个文件（其中 27 个是我方 3.3.22 改 Compose 时主动删除的旧 View 层） |

判据：`git merge-base main origin/main` 定基线；`git diff --name-only <基线>..main` 与 `<基线>..origin/main` 做差集，区分「纯未同步」与「双方都改」。

## 2. 上游三个提交的功能条目

### 2.1 `e5e7dde` — 共享 VMD + 可选角色动作预览（默认关闭）

上游自述：预览默认关闭；完整 SMC/衣物/腿 IK、标定重定向、身体与镜头同步播放仍未完成；无 CI；Linux Clang ASan/UBSan 与 GCC 宿主测试 7/7 通过；**Windows DLL、WinUI、游戏内验证均待做**。

功能条目：

1. **关键帧保存/读取落盘**：`keyframe_file` + `F6`（保存）/`F7`（读取）热键。
2. **共享 VMD 解析器** `native/shared/motion/vmd.h`（有界解析，纯标准库）。
3. **异步文件 worker** `camera_file_worker.h` + `camera_path.h`：`QueueCameraFile` / `PollCameraFileResults`，把 VMD 装载移出游戏线程。
4. **角色动作预览**：相对上半身/手指/眼睛 FK + 命名 BlendShape；`character_pose.h`、`character_mapping.h`。
5. **姿态租约**：Host 侧写者仲裁（`shared/host/pose_lease.cpp`、`PoseLease.h`、`shared/motion/pose_lease_registry.h`），解决与 actions 模块争抢同一角色骨骼写入。
6. **本地回归测试** `native/tests/camera_playback/**`（11 文件）。
7. 文档 `PC_CAMERA_PROGRESS_20260927.md`、`PC_CHARACTER_PREVIEW_20260927.md`。

### 2.2 `d1660db` — MMD 播放 + EIEM DirectVmd 身体动作

上游自述：身体动作为 **EIEM `1bc9baa` 的源码快照（AGPL-3.0）编译四份**，每位舞者一份私有副本；所有游戏访问按名解析、所有钩子经 Host 安装。

功能条目：

1. **EIEM DirectVmd 全身动作移植**：`native/modules/camera/eiem/**`，**27 个文件 / 1,290,810 字节 / 32,016 行**（`ghost_rig.h` 9,923 行、`trojan.h` 4,104 行、`direct_vmd_pose.h` 3,431 行、`smc_face.h` 1,652 行、`init.h` 1,544 行、`cloth.h` 1,293 行、`vmd_parser.h` 1,238 行等）。
2. **四副本并发**：`eiem_slot.h` + `eiem_slot.inc` + `eiem_slot0..3.cpp`，最多 4 名角色同台，各自独立 clip / ghost rig / worker。
3. **MMD 作品库** `mmd_library.h`：安装目录 `mmd\<作品>\set.ini` 描述 motion、face、camera、music，以及 `motion2`–`motion4`（小队同台，保留 VMD 队形）。
4. **MMD 伴生悬浮窗** `overlay/main.cpp` + `.rc` + `.manifest`（独立 WIN32 exe，与游戏进程通过 `mmd_overlay_protocol.h` 共享内存通信：状态用序列锁、命令用 16 槽环形队列）。
5. **共享时间轴导演** `mmd_director_runtime.inc`（823 行）：身体、镜头、音乐共用一条时钟。
6. **本地音轨** `native/modules/music/local_track.inc`（470 行）+ `LocalMusic.h`：WAV/MP3/M4A/AAC/FLAC/WMA，可设时间偏移；链接 `mfplat mfreadwrite mfuuid ole32`。
7. **衣物物理三模式**：稳定（推荐，播放期完整模拟且不被原生姿态拉回）/ 游戏原样 / 冻结。
8. **表情与口型**：face VMD 驱动面部；新增 MMD 口型别名（「ワ」「口横広げ」「にやり」等）。
9. **Animator 容错**：模型下存在多个 Animator 时，选取唯一处于启用状态的人形 Animator，并列出全部候选。
10. **VMD 镜头免开自由相机**，按角色身高与 EIEM 舞台锚点自动适配。
11. **自定义角色外观**：BEM 1.1/1.2 运行时与转换工具；「角色外观」页组件选项收进可展开区域（`MmdLibraryService.cs`、`CustomModelPage.xaml.cs`）。

### 2.3 `2c0cc12` — Windows 3.4.0 发布 + 热更新资源同步

1. 版本号 `3.3.0` → `3.4.0`（`Directory.Build.props`，仅 Windows）。
2. 热更新资源重生成：动作清单、语音清单与语音目录、角色预设、战斗数据字典（新增角色 `chr_0038_purrche` 噗切娜）、`icon-manifest.json` 与 4 张图标 PNG。

## 3. 未同步功能清单（按与我方 Android 的相关性分级）

### A 类｜桌面专有，Android 端无对应物（上游 Android 亦未接）

| 编号 | 功能 | 关键文件 |
| --- | --- | --- |
| A1 | MMD 作品库 + 共享时间轴导演 + 伴生悬浮窗 | `mmd_library.h`、`mmd_director_runtime.inc`、`overlay/*`、`mmd_overlay_protocol.h`、`MmdLibraryService.cs` |
| A2 | EIEM DirectVmd 身体动作（含四副本、衣物物理、表情口型、Animator 容错） | `eiem/**`（27 文件 32,016 行）、`character_motion_runtime.inc`、`shared/motion/*` |
| A3 | MMD 本地音轨 | `music/local_track.inc`、`LocalMusic.h` |
| A4 | 姿态租约（Host 写者仲裁） | `shared/host/pose_lease.cpp`、`PoseLease.h`、`pose_lease_registry.h` |
| A5 | Win UI 与文档 | `FreeCameraExtras.cs`（+13 键）、`MainWindow.xaml(.cs)`、`ModConfiguration.cs`、`ConfigurationService.cs`、`CustomModelPage.xaml.cs`、`ShortcutService.cs`、`docs/PC_*.md` |
| A6 | 本地回归测试 | `native/tests/camera_playback/**`（11 文件） |

### B 类｜落在 Android 也编译的共享翻译单元（合并必须处理）

| 编号 | 文件 | Android 是否编译 | 上游改动 | 处理要点 |
| --- | --- | --- | --- | --- |
| B1 | `native/modules/camera/module.cpp` | **是**（`betterendfield_desktop_features`） | +446 行 | 新增约 40 个 MMD/EIEM 配置键；`#if !defined(__ANDROID__)` 已包住两个 `.inc` 并提供 5 个 Android 空桩；**但顶部新增 include 块无守卫** → 见 §4.1 阻断点 |
| B2 | `native/modules/camera/free_camera_runtime.inc` | **是**（`module.cpp:1173` 无条件 include，全文仅 1 处 `_WIN32` 守卫） | −270/+191 重构 | VMD 解析外移、异步文件 worker、关键帧路径、MMD 镜头钩子；**我方与上游差异 470 行，人工合并成本最高** |
| B3 | `native/modules/actions/module.cpp`、`pose_overlay.inl` | **是** | +16 / +18 行 | 租约调用点已用 `#if defined(_WIN32) \|\| defined(BE_TEST_PC_POSE)` 包裹 ⇒ 结构上 Android 安全；模块版本 1.13.4→1.13.5，新增导出 `BetterEndfield_ActionsPoseLeaseVersionV1` |
| B4 | `shared/motion/*`、`camera_path.h`、`camera_file_worker.h`、`mmd_library.h`、`LocalMusic.h`、`PoseLease.h` | 可达 | 新增 | 实测 10/11 在 Android 替身层下干净编译 |
| B5 | `native/CMakeLists.txt` | 否（我方用 `android/app/src/main/cpp/CMakeLists.txt`） | +52 行 | 新增 `BetterEndfield.CameraEiem`（OBJECT）、`BetterEndfield.MmdOverlay`（WIN32 exe）目标、`mfplat`/`winmm`/`gdiplus` 链接；**这些目标不得进入 Android 构建** |

### C 类｜数据与版本

- **C1 版本号**：`Directory.Build.props` 3.3.0 → 3.4.0（仅 Windows）。我方 Android 走 `versionCode 30322` / `versionName 3.3.22-alpha.x`，两套版本号并行，互不干扰。
- **C2 热更新资源**：`manifests/combat/combat-dictionary.json`(+950)、`buff-sources.bemap`(+199)、`manifests/model/action-manifest.json`(28)、`manifests/voice/voice-event-media-manifest.json`(**+13,797**)、`ui/…/Assets/{combat,model,voice}` 三份、`web/src/data/*.min.json` 三份、`web/cloudbase/.../stage-map.json`、`tools/CombatDataExporter/id_registry.json`、`web/public/icons/icon-manifest.json` + 4 张图标 PNG。

### D 类｜反向：我方领先、上游没有（合并时不得被覆盖）

- **Android 全套**：Compose UI（27 个旧 View 文件已删）、`android-build.yml` / `android-release.yml`、固定签名（`android/keystore/bem-debug.keystore`、`debug.properties`）、`android/app/src/main/cpp/input_relay.cpp`。
- **第一人称扩展**：我方 20 个 `first_person_*` 文件；上游只有 `first_person_mesh.h` / `first_person_retry.h` / `first_person_runtime.inc` 三件。
- **文档**：我方独有 8 份 `docs/*`（Android 与相机相关）。
- 注：`native/shared/android_compat/**` **上游已有**（Android 替身层是上游基线自带，非我方新增）。

## 4. 合并风险（实测）

### 4.1 已实测硬阻断点：`LONG` 未定义

方法：用 NDK r27c 的真实 clang 对上游新增头文件做 `--target=aarch64-linux-android21 -std=c++20 -fsyntax-only`，包含路径按 Android 目标实际配置（`android_compat/include`、`android_compat`、`shared/include`、`shared/include/BetterEndfield`）。

结果：**11 个里 10 个 OK，1 个 FAIL**。

| 头文件 | 结果 |
| --- | --- |
| `camera_path.h` / `camera_file_worker.h` / `mmd_library.h` / `eiem_body.h` | OK |
| `shared/motion/{vmd,character_pose,character_mapping,pose_lease_registry}.h` | OK |
| `LocalMusic.h` / `PoseLease.h` | OK |
| **`mmd_overlay_protocol.h`** | **FAIL**：`unknown type name 'LONG'; did you mean 'ULONG'?`（5 处：`sequence` 99 行、`visible`、`shutdown_requested`、`command_write`、`command_read`） |

成因：上游 `module.cpp` 顶部把 `#include "mmd_overlay_protocol.h"` 放在**无守卫**位置，而我们的 Android CMake 会编译 `module.cpp`；`android_win32.h` 只提供 `using ULONG = std::uint32_t;`，没有 `LONG`。

修法（二选一，成本都很低）：① 给替身层补 `using LONG = std::int32_t;`；② 把 `mmd_overlay_protocol.h` 的 include 挪进 `#if !defined(__ANDROID__)` 块。**推荐 ①**——该头本身不含任何 Windows API 调用，只是一种类型别名缺失，补齐后 Android 侧可零成本编译通过。

### 4.2 冲突面

12 个双方都改的文件：`CHANGELOG.md`、`THIRD_PARTY_NOTICES.md`、`native/CMakeLists.txt`、`native/modules/actions/module.cpp`、`pose_overlay.inl`、`native/modules/camera/free_camera_runtime.inc`、`native/modules/camera/module.cpp`、`ui/BetterEndfield.UI/{MainWindow.xaml,MainWindow.xaml.cs,Models/ModConfiguration.cs,Services/ConfigurationService.cs}`、`web/public/icons/icon-manifest.json`。

其中 `free_camera_runtime.inc` 最棘手：我方 1,005 行 / 上游 919 行，双方各自改过 470 行左右的重叠区（上游抽出 VMD 解析与异步文件 worker，我方在其上做了 P3 触摸转向接入）。合并后必须重跑一次 Android release 构建 + `:app:assembleDebugAndroidTest`。

### 4.3 结构性风险

- `module.cpp` 顶部 include 块无守卫（§4.1）——**唯一已证实的构建阻断**。
- `.inc` 层已用 `#if !defined(__ANDROID__)` + 空桩隔离，方向是对的，但我们不能假设顶部同样被隔离。
- 上游新增的 `BetterEndfield.CameraEiem`（OBJECT）与 `BetterEndfield.MmdOverlay`（WIN32 exe）在 `native/CMakeLists.txt`，与我方 Android 构建无关；**合并时若误把这两条带进 Android CMakeLists 会立刻失败**（MinHook、gdiplus、.rc 在 Android 不成立）。
- 上游这批改动**未经游戏内验证**（自述 Windows DLL / WinUI / 游戏内验证均待做）；即使合并成功，功能正确性仍需独立验证。

## 5. 建议动作

| 优先级 | 动作 | 理由 |
| --- | --- | --- |
| P0 | 补 `using LONG = std::int32_t;` 到 `native/shared/android_compat/include/android_win32.h` | 单点、零风险，先拆掉唯一已证实的构建阻断 |
| P1 | 只合并 **B 类 5 个共享翻译单元** + `shared/motion/*`（并保持 `.inc` 的 `__ANDROID__` 空桩），**不合并** A 类桌面目标与 `native/CMakeLists.txt` 的 Eiem/MmdOverlay 条目 | 让 Android 侧拿到 VMD 解析与异步文件 worker 的收益，不引入 Windows 专有依赖 |
| P2 | A 类（MMD/EIEM/本地音轨）**暂不同步** | 上游 Android 亦未接；EIEM 移植 32,016 行且依赖大量游戏接口按名解析，Android 侧需单独评估接口可达性 |
| P3 | C2 数据同步（战斗字典、语音清单、角色预设、图标）单独一次提交 | 纯数据、无代码耦合，可独立验证，也是 v3.4.0 唯一与双端都有关系的内容 |
| P4 | 合并后必须补跑 `:app:assembleRelease` 与 `:app:assembleDebugAndroidTest`；Win 侧不可验收（本机无显卡） | 语法检查 ≠ 构建成功 ≠ 设备验收 |

## 附录：可复现命令

```bash
export HTTPS_PROXY=http://127.0.0.1:7897
git fetch origin --prune
git merge-base main origin/main                       # 9b1e895
git log --oneline $(git merge-base main origin/main)..origin/main
git diff --name-only $(git merge-base main origin/main)..origin/main > up.txt
git diff --name-only $(git merge-base main origin/main)..main        > our.txt
comm -23 <(sort up.txt) <(sort our.txt)               # 纯未同步 92 个
comm -12 <(sort up.txt) <(sort our.txt)               # 双方都改 12 个
```

头文件可编译性检查（Android 目标）：

```bash
CLANG=/d/android-toolchain/android-ndk-r27c/toolchains/llvm/prebuilt/windows-x86_64/bin/clang++.exe
SYSROOT=/d/android-toolchain/android-ndk-r27c/toolchains/llvm/prebuilt/windows-x86_64/sysroot
$CLANG --target=aarch64-linux-android21 --sysroot=$SYSROOT -U_WIN32 -fsyntax-only -std=c++20 \
  -I F:/bem/native/shared/android_compat/include -I F:/bem/native/shared/android_compat \
  -I F:/bem/native/shared/include -I F:/bem/native/shared/include/BetterEndfield \
  -x c++ <头文件>
```
