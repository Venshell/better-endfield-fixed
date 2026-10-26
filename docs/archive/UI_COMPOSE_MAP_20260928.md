# Android UI 重构 · Hook 接口与入口对照

> 历史快照：此文中的 `GameOverlay.kt` 落地记录已被 3.3.21 游戏启动崩溃后的 Java/View 回退推翻。2026-09-29 的新实验见 [`ANDROID_OVERLAY_COMPOSE_EXPERIMENT_20260929.md`](ANDROID_OVERLAY_COMPOSE_EXPERIMENT_20260929.md)，当前实现以源码为准。

> 本文是**动手前的接口盘点**，目标树的实际界面逻辑见用户提供的层级树（首页 / 体验 / 角色 / 工具）。它负责回答两个问题：
> 1. 树上的每个叶子，背后有没有可驱动的接口？走哪条通道？
> 2. 界面从哪里进入、由谁装配、现状是什么？
>
> **第八节「落地记录」是本轮实现后的决策与偏差说明**，包含一处刻意偏离目标树的地方（默认 FOV）及其原生侧依据。

---

## 一、整体链路

界面不直接接触游戏。所有写入都落在一个 SharedPreferences 文件上，再由三层适配分别送到游戏进程：

```
Compose 界面
   │  读写
SettingsState.kt  ──►  ModuleSettings.java  ──►  SharedPreferences "module_settings"
   │                                                    │
   │                                    FrameworkSettings.publish()
   │                                    （LSPosed 远端快照，schemaVersion=1 + generation）
   │                                                    │
   │                                        RuntimeBootstrap.load()
   │                                        Os.setenv(每个模块一个变量)
   │                                                    ▼
   │                                        原生 betterendfield_android.so
   │                                        （空值 = 该模块不进游戏进程）
   │
   └── 即时动作 ──► NativeCommandBridge ──► <files>/betterendfield/input.latch
                                              （纯文件，跨 classloader）
                                                       │
                                            input_relay.cpp 逐行消费
                                              ├─ "<vk> <action>"  → 虚拟键锁存（模块用 GetAsyncKeyState 轮询）
                                              └─ "c BE_COMMAND_V1…" → command_pump → 模块 Unity 线程消费
```

**关键约束**：`module_settings` 是**下一次启动游戏**才生效的构建期配置；`input.latch` 是**当前进程内**即时生效的动作通道。树上的开关与滑块属于前者，悬浮窗里的按钮属于后者。

---

## 二、UI 入口登记

| 入口 | 承载类 | 触发方式 | 现状 |
|---|---|---|---|
| 设置 App | `MainActivity.kt`（LAUNCHER，exported） | 桌面图标；或 `EXTRA_PAGE = "custom_model" / "first_person" / "enhancement" / "tools" / "diagnostics"` | Kotlin + Compose，四标签 + 四子页 |
| BEM 安装 | `BemInstallActivity.kt`（exported=false） | 角色外观子页按钮；或 VIEW intent（`BemInstallState.startFromViewIntent`） | Kotlin + Compose |
| 游戏内悬浮窗 | `GameOverlay.kt` ← `XposedEntry.java:144` | 游戏进程内注入，随 Activity 生命周期挂载 | Kotlin + Compose（宿主 View + `ComposeView` 面板），已迁移 |
| 悬浮窗预览 | `GameOverlay(activity, preview=true)` ← `MainActivity.showOverlayPreview` | 增强页「预览悬浮窗」按钮 | 复用同一实现，仅不发送按键 |
| 注入装配 | `XposedEntry.java` + `RuntimeBootstrap.java` | LSPosed 作用域 | Java |
| 外部唤起 | `ModuleApplication` → `FrameworkSettings.initialize` | 进程启动 | Java |

> 悬浮窗是**进程内注入**的，不能依赖 Activity 的 `setContent`。面板本体是 `ComposeView`，由自带的 `PanelLifecycleOwner`（lifecycle / view-model store / saved-state registry）提供组合所需的归属，实际状态由游戏 Activity 的 onResume/onPause 驱动（RESUMED / CREATED）。面板内不使用任何 `stringResource` / `painterResource`：游戏进程的 `Resources` 属于游戏，只有字面量与自绘图形保证可解析。

---

## 三、Hook 接口（五条通道）

### 3.1 模块装载开关（env var，空 = 不装载）

由 `RuntimeBootstrap.load()` 逐个 `Os.setenv`，`native_bridge.cpp` 用 `Configured()` 判断：

| 环境变量 | 模块 id | 触发它的设置 |
|---|---|---|
| `BETTER_ENDFIELD_UI_CONFIG` | `betterendfield.ui` | 隐藏 UID / 隐藏 HUD 任一开启 |
| `BETTER_ENDFIELD_CAMERA_CONFIG` | `betterendfield.camera` | 移除虚化 / 自由视角 / 第一人称任一开启 |
| `BETTER_ENDFIELD_ACTIONS_CONFIG` | `betterendfield.actions` | 冲刺持续开启且至少选一名角色 |
| `BETTER_ENDFIELD_MODEL_CONFIG` | 登录模型模块 | 开屏模型启用 |
| `BETTER_ENDFIELD_VOICE_RULES` | 角色配音模块 | 语音规则非空 |
| `BETTER_ENDFIELD_CUSTOM_MODEL_CONFIG` | 第三方模型模块 | 已安装且启用 BEM 包 |
| `BETTER_ENDFIELD_ACTIONS_ASSET_ROOT` | — | 冲刺姿态数据目录（随包附带，自动） |
| `BETTER_ENDFIELD_CUSTOM_MODEL_PROBE` | 资源探针 | 仅 debug |
| `BETTER_ENDFIELD_VOICE_CATALOG_ROOT` / `DIAGNOSTICS_PATH` / `INPUT_FILE` / `STATUS_FILE` / `NATIVE_LOG` | — | 路径参数，自动 |

### 3.2 模块配置体（INI 文本）

每个模块拿到的是自己那一份 `schema_version=N` 起头的键值文本。**空字符串是"不装载"的唯一表达**，因此 UI 必须在零选择时写空。

### 3.3 按键锁存（即时）

协议一行一事件，由 `NativeCommandBridge` 写入 `input.latch`，`input_relay.cpp` 消费：

```
"<vk> <action>\n"   action: 0=release 1=press 2=pulse
"c <payload>\n"     提交运行时命令
"r\n"               释放全部锁存键
```

虚拟键表见 `Hotkeys.java`，与 `ModuleSettings` 写进相机配置的 `*_hotkey` 成对锁定（面板按的键必须是模块轮询的键）。

### 3.4 运行时命令（`BE_COMMAND_V1` 帧）

```
BE_COMMAND_V1\n<generation>\n<command>\n<value>\n
```
- `generation` 单调递增，原生侧拒绝陈旧帧；计数器持久化在 `ModuleSettings.nextCommandGeneration`。
- 传输双路：优先 LSPosed 远端文件 `command.next`，失败回落 `<files>/betterendfield/commands/next.command`。
- **当前词表只有 `overlay_hide` 一条**（由 `custom_model` 与资源探针在 Unity 线程消费并回 `applied`/`supported`）。新增任何"游戏内即时切换"的能力都要在这里扩词表。

### 3.5 状态与日志回传

| 通道 | 生产者 | 消费者 | 内容 |
|---|---|---|---|
| `status.txt`（+ 远端 `command.status`） | `command_pump` / `RuntimeLog.setStatus` | 悬浮窗 | `BE_STATUS_V1` 帧：generation / accepted\|applied\|rejected\|unsupported / command |
| `native.log` | `input_relay` | 设置页诊断（增量 tail） | 原生侧运行轨迹 |
| 远端偏好 `runtime_log` | `RuntimeLog`（游戏进程内 Java，150 行环形） | 设置页诊断 | 加载流水线里程碑 |
| 远端偏好 `module_settings` 快照 | `FrameworkSettings.publish` | 游戏进程 | 全部设置键（Read-only 镜像） |

---

## 四、设置键总表

`SharedPreferences` 键 → 发布到哪个模块 → 原生配置键。

### 界面 `betterendfield.ui`

| 存储键 | INI 键 | 说明 |
|---|---|---|
| `ui_hide_uid` | `hide_uid_enabled` | 常驻，2 秒补扫 |
| `ui_hide_hud` | `hide_hud_enabled` + `hide_hud_hotkey=0` | 由悬浮窗按钮即时切换 |
| （固定） | `mobile_ui_enabled=false` / `platform_spoof_enabled=false` | 手机端恒关 |
| （遗留） | `enhancement_hide_uid` | 迁移源，只读 |

### 相机 `betterendfield.camera`

| 存储键 | INI 键 | 取值范围 |
|---|---|---|
| `camera_disable_dither` | `disable_dither_enabled` | 布尔 |
| `camera_free_enabled` | `free_camera_enabled` | 布尔 |
| `camera_pause_enabled` | `pause_enabled` | 布尔，且必须 `free_camera` 同时为真 |
| `camera_movement_speed` | `movement_speed` | 0.2–60（原生 clamp 0.5–100） |
| `camera_field_of_view` | `field_of_view` | 20–120（**自由镜头专用**） |
| `camera_first_person_enabled` | `first_person_camera_enabled` | 布尔 |
| `camera_first_person_fov` | `first_person_fov` | 20–120 |
| `camera_first_person_eye_forward` | `first_person_eye_forward` | 0–0.5 |
| `camera_first_person_eye_height` | `first_person_eye_height` | −0.5–0.5 |
| `camera_first_person_near_clip` | `first_person_near_clip` | 0.001–1 |
| `camera_first_person_hide_head` | `first_person_hide_head` | 布尔 |
| `camera_first_person_fill_neck` | `first_person_fill_neck_hole` | 布尔 |
| `camera_first_person_extend_look_range` | `first_person_extend_look_range` | 布尔 |
| `camera_first_person_movement` | `first_person_movement` | 布尔 |
| `camera_first_person_side_look_limit` | `first_person_side_look_limit` | 0–90 |
| `camera_first_person_animation_mode` | `first_person_animation_mode` | 0–3（关/身体/头部/真实） |
| `camera_first_person_animation_strength` | `first_person_animation_strength` | 0–1 |
| `camera_first_person_yield_dialogue` | `first_person_yield_dialogue` | 布尔 |
| `camera_first_person_third_person_in_combat` | `first_person_third_person_in_combat` | 布尔 |
| `camera_first_person_transition_seconds` | `first_person_transition_seconds` | 0–1 |
| `camera_first_person_external_head_scale` | `first_person_external_head_scale` | 布尔 |
| （固定） | `free_camera_mouse_look=false`、`mouse_invert_y=false`、`mouse_sensitivity=0.1`、`free_camera_smoothing=0.3`、`keyframe_loop=false`、`vmd_camera_loop=false` | 手机端恒值 |
| （固定） | `toggle_hotkey=9`、`pause_hotkey=8`、`first_person_hotkey=-`、`roll_*/fov_*/view_reset/motion/keyframe_*/vmd_play` | 与悬浮窗按钮成对锁定 |

### 动作 `betterendfield.actions`

| 存储键 | INI 键 |
|---|---|
| `dash_enabled` | `enabled` |
| `dash_character_aglina` / `dash_character_liino` | `characters`（逗号表） |
| `dash_liino_clean` | `liino_clean` |
| （固定） | `external_loop=true`（Android 必须，姿态数据随包） |

### 模型 `登录模型模块`

| 存储键 | INI 键 |
|---|---|
| `model_replacement_enabled` / `model_runtime_enabled` | `enabled` |
| `model_character` | `character` / `character_id` |
| `model_action` | `final_<action>` 序列 |
| `model_final_loop` / `model_force_loop` / `model_crossfade` | `final_native_loop` / `force_loop` / `use_crossfade` |
| `model_loop_start` / `model_loop_end` / `model_crossfade_duration` | `loop_start` / `loop_end` / `crossfade_duration` |
| `model_scale` | `scale` |
| `logo_theme_enabled` / `logo_theme_color` | `logo_theme_enabled` / `logo_theme_color` |

### 语音 / 第三方模型

| 存储键 | 用途 |
|---|---|
| `voice_language_rules` | `speaker:Language;…`，供配音模块与 catalog 生成 |
| `voice_catalogs`（遗留） | 迁移源 |
| `model_configuration`（BEM） | 由 `BemInstalledResources` 生成，非 UI 直写 |
| `overlay_enabled` | 悬浮窗总开关（读回用于预览与 `OverlayFeatures`） |

---

## 五、目标树 → 接口映射

状态图例：**✔ 已有**（映射到现成键）｜**◐ 需改**（键在但语义/位置要调整）｜**＋ 需新增**（无接口或需新回读）

### 首页 `＋`

| 节点 | 说明 |
|---|---|
| 模块运行状态 | 需新回读：只能从远端快照 + `runtime_log` 推断「上次启动装载了哪些模块」，语义要定义 |
| 需要重启的更改 | 可在 UI 层实现：对比「已发布快照」与「当前编辑值」 |
| 当前角色外观 | 需新回读：BEM 索引（`BemInstaller.INDEX`）已有，选中的外观/选项组只在设置侧，游戏侧无回传 |
| 当前登录展示 | 同上，登录模型模块目前不回传实际生效角色 |
| 快捷入口 | 纯导航：游戏内控制 / 第一人称 / BEM 外观 |

### 体验 · 界面 ✔

| 节点 | 键 |
|---|---|
| 隐藏 UID | `ui_hide_uid` ✔ |
| 隐藏 HUD | `ui_hide_hud` ✔（触发在悬浮窗） |

### 体验 · 镜头

| 节点 | 键 | 状态 |
|---|---|---|
| 通用镜头 · 禁用 Dither | `camera_disable_dither` | ✔ |
| 通用镜头 · 默认 FOV | — | ＋/◐ **`field_of_view` 目前只被自由镜头运行时读取**（`free_camera_runtime.inc:787,910`），不是"通用默认 FOV"。要做成通用项必须改原生 |
| 自由镜头 · 启用 | `camera_free_enabled` | ✔ |
| 自由镜头 · 移动速度 | `camera_movement_speed` | ✔ |
| 自由镜头 · FOV | `camera_field_of_view` | ✔ |
| 自由镜头 · 时间冻结 | `camera_pause_enabled` | ✔ |
| 第一人称 · 基础 5 项 | `first_person_*` | ✔ |
| 第一人称 · 角色显示 3 项 | `hide_head` / `fill_neck` / `external_head_scale` | ✔ |
| 第一人称 · 操控 3 项 | `extend_look_range` / `movement` / `side_look_limit` | ✔ |
| 第一人称 · 动画 | `animation_mode` / `animation_strength` | ✔ |
| 第一人称 · 场景行为 3 项 | `yield_dialogue` / `third_person_in_combat` / `transition_seconds` | ✔ |

> 第一人称子树与现有实现**一一对应，零缺口**——现状只是把它们平铺在同一张卡里，树要求收进子页。

### 体验 · 动作 ✔

| 节点 | 键 |
|---|---|
| 持续冲刺 · 启用 | `dash_enabled` ✔ |
| 持续冲刺 · 阿格莱亚 / 黎诺 | `dash_character_aglina` / `dash_character_liino` ✔ |
| 持续冲刺 · 黎诺 Clean | `dash_liino_clean` ✔ |

### 角色

| 节点 | 状态 |
|---|---|
| 角色外观/BEM · 已安装 / 导入 | ✔ `BemInstallState.packages` / `BemInstaller` |
| 模型包详情 · 启用 / 外观选项组 / 纹理状态 / 转换手机纹理 / 删除 | ✔ 全在 `BemInstallScreen.kt`（`BemPackage`：`appearanceIndex` / `options` / `convertedTextures` / `visibleGroups`） |
| 登录展示 · 模型（启用/角色/缩放） | ✔ |
| 登录展示 · 动画（动作/循环/Crossfade） | ✔ |
| 登录展示 · Logo / 主题色 | ✔ |
| 语音 · 每角色语言 | ✔ `voice_language_rules` |

> 树把 BEM 从「第三方模型」页挪进「角色」，并把登录展示拆成 模型/动画/Logo 三段——都是**分组与文案调整**，键不变。

### 工具

| 节点 | 状态 |
|---|---|
| 游戏内控制 · 启用控制面板 | ✔ `overlay_enabled` |
| 游戏内控制 · 预览 | ✔ `GameOverlay(preview=true)` |
| 诊断 · Framework/Hook 状态 | ✔ `diagnosticsOverlay` / `diagnosticsModules` |
| 诊断 · 模块状态 | ✔ `ModuleConfigurations.summary()` |
| 诊断 · 配置状态 | ✔ 远端快照可读 |
| 运行日志 · 查看 | ✔ `RuntimeLog.tail` 已接到诊断页 |
| 运行日志 · 导出 | ◐ 逻辑已存在于 `GameOverlay.saveJournalToFile`（SAF 请求码 `0x1E10`），需搬到设置侧 |
| 关于 | ＋ 纯新增 |

### 不在树上、也**不在 Android 端**的模块

`combat_stats`、`gacha`、`music` 只存在于桌面构建（`native/CMakeLists.txt`），Android 的 `native_bridge.cpp` 未注册。树里没有它们，**不要为它们预留入口**。

---

## 六、缺口汇总

**A. 纯界面层，可直接做，不碰原生**
1. 导航从「五页扁平标签」改成「首页 / 体验 / 角色 / 工具」四段 + 子页下钻。
2. 新增：关于、运行日志导出（搬 `GameOverlay` 的 SAF 逻辑）、首页三块状态 + 三个快捷入口。
3. 第一人称收进子页；登录展示拆成 模型/动画/Logo 三段。
4. 「需要重启的更改」用本地 diff 实现（对比已发布快照与当前值）。

**B. 需要新的回读语义（只在 Java/Kotlin 侧，可做但要先定义）**
5. 「模块运行状态」——远端 `runtime_log` 里已有装载里程碑，但没有结构化字段；要么解析日志文本，要么让 `RuntimeLog` 增写一条结构化记录。
6. 「当前角色外观 / 当前登录展示」——游戏侧目前**不回传**实际生效的角色与外观。要么接受"显示设置值"（诚实但可能与游戏内实际不符），要么新增回传。

**C. 必须动原生的**
7. 「通用镜头 · 默认 FOV」——现有 `field_of_view` 只服务自由镜头。要做通用项需要新的配置键 + 新的应用点。
8. 任何游戏内**即时切换**的新能力（当前 `overlay_hide` 是唯一运行时命令）。

**D. 迁移遗留**
9. ~~`GameOverlay.java`（872 行 Java/XML View）尚未 Compose 化~~ → 已在 2026-09-28 完成：宿主保留框架 View（需扛住游戏 `setContentView` 重建内容视图），面板本体改为 `ComposeView` + 自带生命周期归属；`GameOverlay.java` 已删除，注入端 `XposedEntry.install` 改为调用 Kotlin 的 `@JvmStatic install`。**该面板尚未实机验证**（本机无法启动游戏），Compose 构造失败时降级为可见错误文案。

---

## 七、风格令牌：改为工业黄黑

现有 `UiTokens.kt` 是**青绿扁平**（accent `#2EC8C0`）。按新方向改为**黑/深灰主体 + 黄只作强调**，直接对应用户给出的配色表，不做中间灰调的发明：

| 令牌 | 现值（青绿） | 新值（工业黄黑） |
|---|---|---|
| `background` | `#0B0C0D` | `#0A0A0A` |
| `panel` | `#191B1E` | `#121212` |
| `field` | `#121415` | `#1D1D1D`（对齐 surfaceVariant） |
| `row` | `#23262A` | `#191919`（补的第四档，见下） |
| `rowHigh` | `#32373D` | `#242424` |
| `panelHigh` | `#2C3036` | `#1D1D1D` |
| `track` / `outline` | `#373C41` | `#303030` |
| `accent` | `#2EC8C0` | `#F4E900` |
| `accentPressed` | `#21A8A0` | `#E8DC00` |
| `accentDim` | — | `#B9AE00`（新增：禁用强调） |
| `accentSoft` | `0x1F2EC8C0` | `0x1FF4E900` |
| `accentInk`（按钮上的字） | `#04211F` | `#0A0A0A` |
| `textPrimary` | `#F2F4F5` | `#F2F2EE` |
| `textSecondary` | `#9DA4AB` | `#A8A8A8` |
| `textMuted` | `#6E767E` | `#777777` |
| `danger` | `#F0705F` | `#FF6B5A`，保持**独立错误色**，不复用主黄 |
| `overlayPanel` | `0xF20D0F11` | `0xF20A0A0A`（黑底提不透明度） |
| `logoPresets` | 含青/绿 | 首位换 `#F4E900`，保留其余原样（见「落地记录」的说明） |

配套改动面：`UiTokens.kt`（`object Be` 一处改完即可，语义 token 没有第二份副本）、`UiTheme.kt`（`darkColorScheme` 与 `Shapes` 的绑定值）、`values/colors.xml`（仅 `app_background` / `accent` 两项，窗口首帧用）。原先 `GameOverlay.java:37-45` 那份同调色板硬编码副本**已随悬浮窗 Compose 化消除**，现在整套界面只有一个颜色来源。

比例纪律（来自配色表）：黑/深灰 76%、白/灰 18%、黄 6%——**黄只出现在主按钮、当前选中、关键状态、少量品牌装饰**；大面积背景不用黄。卡片层级优先靠明度差，描边只作补充。

---

## 八、落地记录（2026-09-28，本轮已实现）

代码已按目标树重排并编译通过，未提交。逐项决策与偏差如下。

| 未决项 | 决策 | 落地 |
|---|---|---|
| 1. `row` 第四档 | 取 `#191919` | 面板回退到 `#0A0A0A` 会让卡片与页面同色，层级只能靠留白硬撑，风险更大 |
| 2. 首页状态诚实度 | **显示设置值**，不新增回传 | 首页「模块运行状态」列的是下次启动会装载的模块（读自同一份配置串）；「需要重启的更改」按**本屏打开后写入的项数**计数，措辞停在"下次启动生效"，不声称运行中的进程已生效 |
| 3. 通用镜头 · 默认 FOV | **降级为 UI 归位**，不新增原生键 | 见下「偏差 1」 |
| 4. 运行日志落点 | 独立子页（工具 › 运行日志） | 查看 + 导出（SAF `CreateDocument`，无需存储权限）。导出内容 = 远端快照 + 构建号 + 时间戳 |
| 5. 悬浮窗 Compose 化 | 上一轮已完成，本轮只继承新令牌 | `GameOverlay.java` 已删除，硬编码调色板副本随之消除；`OverlayPanel.kt` 只用语义 token，换色零改动 |
| 6. 包体策略 | 接受增长 | debug APK 33.8 MB（其中 dex ≈30 MB）。`isMinifyEnabled = false` 是上游既有设计，R8 作为独立一轮另做，不与 UI 迁移同轮叠加变量 |

### 偏差 1：默认 FOV 只归位、不新增行为

树把「默认 FOV」放在**通用镜头**下，而同层的自由镜头另有「FOV」。核对原生后确认二者是**同一个键**：

- `native/modules/camera/module.cpp:1782` 解析 `field_of_view`，`2097` 存入 `g_field_of_view`；
- `free_camera_runtime.inc:787,910` 是唯一消费点——`fov_wide/narrow` 热键在运行时推拉，而 `view_reset` 键把 FOV 复位到 `g_field_of_view`。

所以它是"自由镜头的基准/复位目标"，不是通用默认 FOV。要真做常驻 FOV，必须每帧写入 `lens_field_of_view`，会与游戏自身的过场 FOV、瞄准变焦打架——上游那套 `CameraSmoothPerspectiveTransition` 正是为处理这层冲突而存在的。在**本机无可启动游戏的显卡、无法实机验证**的前提下盲改这一处，收益是文案更准，代价是可能破坏过场镜头。

因此本轮：该键**只出现一次**（通用镜头 · 默认视野），自由镜头组放置一行指向说明；文案明确写出"游戏本体镜头（过场、瞄准变焦）不受影响"。真做常驻 FOV 留作独立原生轮次。

### 偏差 2：`logoPresets` 保留原值

配色表"不建议绿色"针对的是**界面 chrome**；`logoPresets` 是登录 Logo 主题色的候选，属于用户可选输出而非界面配色，去掉绿色会让原本可选的品牌色消失。仅把首位换成品牌黄 `#F4E900`。同理，`ModuleSettings` 里 Logo 默认色仍是桌面端既有值 `#FFC928`，**未改**——改它会改变所有未自定义用户的登录界面观感，属行为变更而非 UI 变更。

### 本轮同时清掉的死代码

`EnhancementPage.kt` 已删除（内容拆到 `ExperiencePage.kt` / `FirstPersonPage.kt` / `ToolPages.kt`）；13 个随之失效的字符串资源已从 `strings.xml` 移除。`colors.xml` 的 `app_background` / `accent` 同步为新值（窗口首帧用）。

### 尚未做的发布动作

`CHANGELOG.md` 条目与 `versionCode` / `versionName` 递增**未动**——按本仓约定「一条 CHANGELOG = 一次发布」，这两步属于发布动作，等确认后再做。届时需同步四份文档：`CHANGELOG.md`、`README.md`、`README.en.md`、`android/README.md`（后两者的界面章节本轮已改）。
