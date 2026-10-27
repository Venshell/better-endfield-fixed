# Android 上游差距对账（2026-10-25）

本文是纯对账记录，不改任何代码。核查点从路线图（2026-10-10）使用的上游 `c2f8eafc` 推进到本次实际 fetch 的 `upstream/main`（`a617b158`，2026-10-25）。期间上游共 **66 个提交**、发布 v4.0.4 → v4.1.0 并开启 4.1.1 开发。全仓 591 个文件变更（+75118/−15195），其中移植相关目录（`android/`、`native/modules/custom_model/`、`tools/CustomModel/`）145 个文件。

结论先行：

1. 上游 **BEM 格式已推进到 1.5（随 v4.0.7 发布）并在仓库中存在 1.6 规范草稿**；本地移植线只到 1.4。F09/F10 的格式合同需要重新对齐。
2. 上游 custom_model 新增了**动画驱动系统**（morph 顶点流驱动、驱动音效、overlay 动画面板、三态控制、GUI 热键）—— 这是全新的功能域，本地完全没有对应实现。
3. 上游在 `actions` 模块为 **Jsspsi 加了轻量持续冲刺**（10-25 `dc8fbf29`），与本地实验分支（`codex/destructive-experiments`）正在迭代的 Aglina 持续冲刺同域，实现思路值得对照，但二者互不依赖。
4. 路线图的 S 阶段顺序仍然成立；建议不因上游更新打断 S1 设备验收 → S2 的既有顺序，但 F09 的格式范围决定要提前做（见 §5）。

## 1 对账方法与证据

```powershell
git -C F:/bem fetch upstream        # 实际取得 v4.0.5–v4.0.8、v4.1.0 新 tag
git -C F:/bem rev-list --count c2f8eafc..upstream/main   # 66
git -C F:/bem diff --stat c2f8eafc upstream/main          # 591 files, +75118/−15195
git -C F:/bem diff --name-only c2f8eafc upstream/main -- android native/modules/custom_model tools/CustomModel
```

本地上游引用更新后，`upstream/main = a617b158`。本地 main（`5e09128c`，10-05）与移植分支（`55f3e16f`）均未包含这些提交；此前 fetch 于 2026-10-10。

## 2 上游 66 提交分类

### 2.1 Android 产品域（影响移植范围）

| 主题 | 提交（日期） | 内容 | 与本地关系 |
| --- | --- | --- | --- |
| BEM 1.5 格式 | `9e093a85`（10-18，随 v4.0.7） | 材质覆盖、根骨骼偏移、NPC 嵌套 receiver 解析 | 本地停在 1.4（F09）；格式合同需扩容 |
| BEM 1.5 内部化驱动 | `1aa5e3ec`、`f879321b`、`6af7d6cf`（10-22/23） | 内部化时钟驱动、overlay 动画面板、结构化目标；BEM Tools 1.6.x | 全新功能域，本地无对应 |
| ZIP 容器跨端 | `3ff10a02`、`2fa4fe8e`（10-22） | BEM/ZIP 容器桌面/Android/工具三端读写 | 本地 S2 导入事务的相关输入格式，需对齐 |
| 动画驱动系统 | `20b2ccc8`、`56630969`、`b622fc02`、`04a76a5b`、`5eb81729`、`83828b19`（10-25） | 流 0 子区间顶点更新、`SetNativeArrayForChannelImpl` 驱动 morph、驱动音效、一次性播放/三态 overlay、GUI 驱动热键 | 全新功能域；`56630969` 是 Android 原生路径 |
| BEM 1.4 静态武器 | `1038af19`（10-20） | 跨平台静态武器 + 兄弟 LOD 广播 | 本地 F09/F10 的 1.4 范围之外的新能力 |
| LOD/命名修复 | `996d6272`、`30c5e1ec`、`9dc5c9b9`、`a617b158`（10-25） | Purrche 帽 lod0、按角色 prefab 证据推导 LOD1、克隆领养后 shadow proxy、Aurora 旧骨骼重映射保护 | 与本地 LOD 关系生成（`generate_android_lod_relations.py`）同域，具体规则需 diff |
| Android UI 对齐 | `d6fe80c7`、`e76a7cd5`、`bf6478d8`（10-25） | 首页改功能总览、管理员外观页桌面对齐 | 上游包名 `dev.betterendfield.next` 与本地 `dev.betterendfield.android` 不同，实现参考而非直接 cherry-pick |
| 纹理/资源 | `9116c7ec`、`96a40305`（10-16） | 逐模型 ASTC 转换、法线 Z 轴修复 + Zstd + 载荷自愈 | 与本地安装器 `texture_install` 域重叠 |
| VFS | `49403db1`（10-25） | code version 4 无扩展字段块解析 | 本地实验分支 10-23 也有「Parse Android VFS v5 tag widths」（`56dbce46`），两版各自演进，需对照 |
| 更新检查 | `2c642403`（10-25） | 启动自动检查更新（桌面+Android） | 本地无；产品边界内可选项 |
| 语音 | `285a9e55`（10-19） | 预解析 native 卸载保护防启动挂锁 | F17 相关 |
| 3.4.x 时代的 MMD 相机 | `22963ac1`（10-10） | MMD 相机取景对齐 PC 绑定头部高度 | 本地 F02 已有 MMD 实现，回归项 |

### 2.2 Windows 产品域（按产品边界不纳入移植主线）

Steam overlay 注入（`e8a07f55`）、MediaCodec/媒体投影/客房投影（`c12b22bf`、`2fa12fc4`）、多启动器选择与绕过（`4c58cb37`）、mod-center 评论/点赞/上传（`04250b90`、`95ebd9b6`）、source 游戏控制（`44612aa5`）、XInput 所有权保持（`e92c70eb`）、E Mod Loader 迁移（`ca9ae20c`）。这些是 Better Endfield Next（Windows 产品）的功能，按[产品边界](PRODUCT_BOUNDARY.md)不进入 Android 主线排期；其中媒体投影的 Android 部分（`MediaScreenActivity`）属于上游 Next Android 应用，本地产品不含该页面。

### 2.3 工具链/发布

v4.0.4–v4.1.0 七次发布、BEM Tools 1.5.3→1.7.0、4.1.1 开发分支开启。发布说明本身不改变移植合同，但 BEM Tools 的版本号标记了格式能力的演进点。

## 3 移植相关目录的具体规模

| 目录 | 文件数 | 说明 |
| --- | --- | --- |
| `android/` | 59 | +29220/−14733，大头是 `resource-source.json`（−6096）与 custom_model 模块 |
| `native/modules/custom_model/` | 86 处变更（含 tools 合并计数 145） | 新文件：`animation_driver.*`、`driver_audio*`、`material_overrides.h`、`aurora_legacy_compat.h`、`bem_targets` 演进等 |
| `tools/CustomModel/` | 含 `bem_v15`、`test_bem_v15.py`（251 行）等 | 1.5 工具链已落地 |

上游还包含 `docs/custom_model/BEM_V1_6_SPEC.md`（骨骼绑定空间与轴向骨长缩放，明确标注 TBD 未实测）——1.6 是设计草稿，不是已发布格式。

## 4 与本地三条工作线的关系

1. **移植工作树（本仓库）**：[功能对账](ANDROID_UPSTREAM_FUNCTION_MATRIX_20261008.md)与[差距清点](ANDROID_DELTA_VS_UPSTREAM_3510FA7.md)基于旧核查点，BEM 1.5/1.6、动画驱动系统、静态武器三块为新增差距。S 阶段顺序不需要推翻：S1 设备验收 → S2 仍是最短路径；但 F09 的格式范围决策（是否把 1.5 纳入本轮合同）应在上游合并决策之前做，避免按 1.4 合同实现后再返工。
2. **main（`F:\bem`）**：本地 main 停在 10-05，与上游差距同为 66 提交。上游 `dc8fbf29`（Jsspsi 轻量持续冲刺）与本地 actions 模块（Aglina 冲刺）同在 `native/modules/actions/module.cpp`，将来合并 main 时这是最可能冲突的文件之一（本地 +335 行 vs 本地实验分支也大改该文件）。
3. **实验工作树（Aglina）**：不依赖上游；但上游 Jsspsi 的「轻量持续冲刺」取舍（不做跨腾空连续性）可作为 Aglina 目标合同的对照参考——上游选择了更保守的功能子集。

## 5 建议的登记项（不改变 S 顺序）

- [ ] F09 格式范围决策：本轮合同停在 1.4 还是纳入 1.5；做出决策前不把上游 `bem.cpp`/`bem_v15.py` 直接搬入。
- [ ] 功能对账矩阵增补三行：BEM 1.5、动画驱动系统、BEM 1.4 静态武器（均为「上游已有、本地未有」）。
- [ ] LOD 命名规则对照：上游 Purrche/LOD1 证据推导 vs 本地 `generate_android_lod_relations.py`，确认规则是否需要同步。
- [ ] VFS code version 4（上游）与 v5 tag widths（本地实验分支）对照。
- [ ] Jsspsi 冲刺实现阅读（`dc8fbf29`），登记为 Aglina 线的参考资料。
- [ ] 上游合并时机：不早于 S1 设备验收闭合；合并时优先处理 `actions/module.cpp` 与 `custom_model` 目录冲突。

## 6 边界声明

- 本文只重新 fetch 了上游引用并读取提交/diff 统计，没有合并、cherry-pick、rebase 或修改任何本地分支。
- 上游 66 提交未逐个人工审读；分类基于提交标题与目录级 diff 统计，个别提交的实际内容可能与标题归类有出入，实施前应以具体 diff 为准。
- 本地 main 与移植分支的未提交内容保持原样。

## 7 cherry-pick 执行记录（2026-10-27/28）

采用「只 cherry-pick 选定功能」方案。基线：105 条未提交改动先落成基线提交 `ce726823`（`.workbuddy/` 未吸入），备份分支 `backup/pre-upstream-pick-20261027`。

### 7.1 已移植（4 个，均通过宿主门禁 + Debug 构建）

| 上游提交 | 本地提交 | 冲突处理 |
| --- | --- | --- |
| `49403db1` VFS code version 4 无扩展字段解析 | `081d06ac` | 保留本地 v3 布局分支，合并出 `has_tag`(v≥4)/`has_extension`(v≥5) 双标志；接受版本集扩为 (3,4,5) |
| `285a9e55` voice 预解析 native 卸载保护 | `b0c0c645` | 干净合入 |
| `22963ac1` MMD 相机取景对齐 PC 绑定头部高度 | `67816a15` | 丢弃上游 4.0.1 发布文档（`docs/workspace/releases/`、CHANGELOG/README hunk）；测试文件保留本地 `BetterEndfield::` 命名空间，采纳 `assert(naturalHeight==0)` 新断言 |
| `30fba648` 日志句柄常开 + 逐行 flush | `f65dddf6` | **部分采纳**：只取 `native/shared/host/logging.*`（句柄在 Initialize 打开、Write 逐行 flush，文件名保持本地 `BetterEndfield.log`）；module.cpp 的交易级聚合遥测是上游帧切片架构（F14/S6 域）的一部分，与本地同域另一套实现结构性冲突（15 处冲突），整体放弃，留待 S6 按本地架构重设计 |

验证：`:app:verifyPortHost` 6 组 Java 90 项 + Python 26 项通过（改用系统 Python 3.14.6，见 §7.3）；`:app:assembleDebug` 成功（44 任务）。

### 7.2 评估后跳过（依赖本地不存在的上游基座，不作为独立修复移植）

| 上游提交 | 依赖缺口 | 处理 |
| --- | --- | --- |
| `996d6272`、`30c5e1ec` LOD 命名/LOD1 证据推导 | 本地 `generate_android_lod_relations.py` 是与上游不同的离线候选工具（无 `config/compatibility/`、workspace-config、`android_lod_relations.generated.h` 接线）；上游改为 1.5.3/1.6.3 双版本配置 | 规则差异（Purrche lod0 命名、per-character prefab LOD1 证据）登记为本地工具的待同步规则，不搬代码 |
| `9dc5c9b9` 克隆领养 shadow proxy | 依赖 `model_clone_lineage.inc`（上游克隆继承体系，本地未有） | 归入将来的克隆继承功能域 |
| `a617b158` Aurora 旧骨骼重映射保护 | 依赖 `aurora_legacy_compat.h` 与 `aurora_runtime_checks.inc` | 同上 |

### 7.3 环境注记

- 原 BEM Python 环境 `D:/CodexData/better-endfield-bem-env-20260926` 已不存在（D 盘清理），S1 文档中的门禁命令需改用系统 Python（`C:\Users\Vens_\AppData\Local\Microsoft\WindowsApps\python.exe`，3.14.6，四套件纯标准库可直接跑）；`-PportHostPython` 参数照旧传入新路径。
- `D:/codexdata/bem-s1-20261011` 产物目录同样已不存在；S1 文档中的哈希记录仅为历史证据。
- Gradle 用户目录仍为 `F:/CodexData/gradle-user-home`，可用。

## 8 第二轮移植：BEM 1.5 / 驱动 / 武器 / 纹理（2026-10-28）

用户确认「只 cherry-pick 选定功能」后继续。本批把 BEM 1.5 域整体落地：

| 批次 | 本地提交 | 内容 |
| --- | --- | --- |
| 工具层 | `87dae414`、`a97439e7` | BEM Tools 1.6.3：`bem_v15.py`、`zstd_compat.py`、ZIP 容器（`bem_projects.py` pack-zip/inspect-bundle）、`bem_tool.py` 1.5 版本选择/`target-profile`、`bem_v1.py` 浮点白名单树校验、测试全量同步（255 项 OK）；`workspace_config.py` 从 3ef6ed35 恢复并解除 gitignore |
| native 1.5 核心 | `123ed2b6` | `bem.cpp/bem.h` 上游 main 全量（1.5 头部、material_overrides 解析、root_offset、`BemMorphSet` 运行时源、ZIP 读取、`ExtractBemPayload`）+ `miniz` vendored + `mod_registry.cpp/h` 基线超集（RetainAdapter/RegistryUpdatePlan/ParseResourceModRegistry）+ 上游 drivers/zip 回植 + 本地 1.4 资源守卫三处重放 + `animation_driver.*` + 三个新测试文件（bem_v13_morph 扩展 / animation_driver / bem_v14_resource） |
| Android 驱动管线 | `a92ebf47`、`a6d07f5e` | registry 文本 `drivers=` 键、install_jni 包报告 `drivers` 数组；`module.cpp` morph 注册（BuildMeshFromComponent）、`SetNativeArrayForChannelImpl` 通道上传、`ApplyDriverMorph`/`ForEachMorphedMesh`、pump tick（0.016s）+ `EnsureAnimationDriverLoaded`（含 ini `drivers=id:play|pause|stop` 覆写）+ `SetLogger` 初始化 |
| 材质覆盖 | `b0ed2dbe` | 5 个 material 契约、`ApplyMaterialOverrides`/`OverridePropertyCount`、draw 路径（PrepareDrawMaterials）与 keep 路径（CopyMaterials + PreparedBinding.material_overrides 填充） |
| BEM 1.4 武器 | `ee6e579f` | `SiblingLodPathMatches` 匹配器、`BemOptions.java` 武器跨平台判定/冲突抑制、registry windows-x64 武器回退、sibling 静态广播（PreparedBinding.sibling_static_renderers + Apply/Restore）、sibling 匹配下的 mesh 名放行 |
| 纹理修复 | `f127d37e`、`44b6971b`、`510359aa` | BC5 解码 Z 重建、`DetectNormalCandidate` 启发式（semantic/编码/文件名/内容）、`RepairNormalTexture` + `repairNormalsNative` JNI + Java native 声明、死蓝通道自愈 |
| 世界适配器 | `91159fe3` | Purrche 帽 lod0 接收回退（lod1 名→lod0 接收器）+ receiver_names 映射贯通 shadow pass |

验证（每批次后均跑，最终态复验）：`:app:verifyPortHost` **6 组 Java 90 项 + 4 Python 26 项 PASS**；工具层全量 **255 tests OK (5 skipped)**；`:app:assembleDebug` + `:app:assembleDebugAndroidTest` 双包成功（74 任务）。最终 `app-debug.apk` SHA-256 `D871AF293DF31B92FFE40FAB3C9550C597CF065DD108FE635883ECB16BAD7225`（82,647,449 字节）。

### 8.1 本轮明确未移植（登记为缺口）

| 上游能力 | 缺口性质 | 后续入口 |
| --- | --- | --- |
| `root_offset` 运行时应用 | 解析/校验/结构已在 bem.cpp/h；应用需要扩展 `SavedOriginalBinding`（visual_root_path/pristine）与热切换恢复链 —— 深度 F12 域工作 | S5 热切换批次一并落地 |
| 驱动 JNI 控制面（`setCustomModelDriver`/`customModelDriverStatus` + overlay 面板） | 依赖上游 `betterendfieldnext` JNI 命名与 Next 包页面结构；本地 ini `drivers=` 覆写链已可用 | 页面域对齐批次 |
| 逐模型 ASTC/修复按钮 UI（9116c7ec/96a40305 的 BemInstallPage/BemInstalledResources 部分） | 本地安装 UI 为 Compose（BemInstallScreen.kt），页面结构不同 | 安装页 UI 批次 |
| 首页功能总览/管理员外观页（d6fe80c7/e76a7cd5/bf6478d8） | Next 包 UI，本地包名/页面树不同 | 产品决策后再排 |
| 启动自动更新检查（2c642403） | 产品功能取舍未决 | 产品决策 |
| LOD1 证据推导工具（30c5e1ec，config-driven） | 本地生成器是离线候选工具；上游 Purrche/LOD1 语义已通过世界适配器回退落地 | 目录证据批次 |
| ZIP 安装入口 Java 侧（BemImportArchive zip 路径,3ff10a02） | 本地已有 ZIP staging 实现（27 项门禁过）；上游扩展（pack-zip 等）在工具层已带 | S2 导入事务批次核对 |

### 8.2 边界声明

- 本轮所有改动只落在移植分支 `codex/android-thirdparty-alignment`；未合并 upstream/main、未 rebase、未推送。
- `animation_driver.cpp` 的 49 项纯逻辑套件与 `bem_v13_morph_tests` 的 109 项套件仅完成语法/链接验证与工具层等价测试；设备端运行验收仍欠（与 S1 设备门禁同一前提：PJX110 不可用）。
- 本地 `module.cpp` 保持与上游分叉的 Android 适配结构；morph tick 使用固定 0.016s 步长（上游为帧时间驱动），长会话漂移待设备实测后再评估。
