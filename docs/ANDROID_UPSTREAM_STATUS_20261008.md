# Android 上游移植状态（2026-10-08）

> 当前工作树：`codex/android-thirdparty-alignment`，已提交基线 `55f3e16`，另有未提交的定向移植改动。2026-10-10 再次 fetch 后，上游 `upstream/main` 为 `c2f8eafc`；本页原有逐项底账基线为 `b67a2f4`（v3.5.3），之后的增量见下节。不得以旧底账声称已对齐最新上游。

> 2026-10-10 用户明确“不做win”：后续实现和验收仅针对 Android ARM64；下文既有 Windows 宿主夹具记录只是此前阶段的历史证据，不构成继续开发 Windows 功能的计划。

## 结论

### 2026-10-11 S1 定向推进

仅实现 F05：`BemInstaller` 统一读取修订/提交校验，Provider 消费同一合同；无编辑读最新，真实草稿遇外部变化保留并阻断保存，确认放弃后读最新。新宿主合同 16 项，统一门禁六组 Java 90 项、Python 26 项实际通过；双 APK 编译及头饰/ARM64/签名核对通过。六项新 instrumentation 已编译，但 PJX110 暂不可用，用户明确先做源码与构建验证；未安装到 HLK-AL00，未取得新设备或页面验收。详见 [S1 合同与验收](ANDROID_INDEX_CONDITIONAL_SAVE_20261011.md)。F05 保持设备门禁待验；本轮不进 S2，不提交/合并/推送。下方“F05 未修复”是此前时点，不能覆盖当前接线状态，也不能以本轮构建替代设备通过。

### 2026-10-10 审计与规划补充

后续 PJX110（`b992bd53`，Android 16）定向设备验收：主/测试 APK 覆盖安装成功。镜像测试首次因 `Files.readString` 的测试运行库兼容问题失败，改为字节读取加 UTF-8 解码后重编译并安装测试包；镜像持久化、损坏索引恢复为空列表、无框架预览关闭三项复测均取得精确方法 PASS 和 instrumentation code -1。产物新哈希与原始证据见 [Debug 测试说明](ANDROID_DEBUG_TEST_20261010.md)。这是三项设备自动检查，不代表完整 instrumentation、页面视觉或游戏验收，也不修复 F05 / S1 的 P1。以下此前“无新增设备验收”描述保留其历史时点。

后续接管按一次一个功能推进，本轮仅处理 F16 / S0：统一宿主门禁、Gradle `check` / `test*UnitTest` 依赖和 CI APK 前置 job 已接线。独立命令及 `:app:verifyPortHost` 实际执行五组 Java 74 项检查、四组 Python 26 项测试通过；隔离副本的缺失测试、Java 断言和 Python 失败均返回非零。精确命令与限制见 [门禁合同](../tools/AndroidPort/README.md)和[路线图](ANDROID_PORT_ROADMAP_20261010.md)。下面“尚未接统一门禁”是此前审计时点，不覆盖本轮结果。远端 CI、APK、设备未新增验收，F05 的 P1 保持未修复；下一轮仅进入 S1。

当前执行总表见 [Android 移植规划与完成状态](ANDROID_PORT_ROADMAP_20261010.md)。审计确认安装页草稿/索引修订冲突仍为 P1 阻断；原生完成记录因未替换共享材质保留而积累、候选解码预算后置为 P2 风险。world/UI 骨骼矩阵差异放行需要明确合同及变形验收，尚不能断言实际视觉故障。

本轮重新编译当前纯 Java 源码并执行五组宿主测试：热切换候选 15 项、归档导入 27 项、索引提交 15 项，框架等待与重连策略均通过；相关 Python 工具测试 26 项通过。当前没有 `src/test`，新增 `testHost` 未接默认 Gradle 单元测试源集/统一门禁，因此下文历次 `:app:testDebugUnitTest` 任务通过不能证明这些测试运行。本轮未重建 APK，现有包内容读取核对为 834 个头饰资源、一份清单和 ARM64 库。

本轮 ADB 列出 HLK-AL00，但项目包安装路径查询为空，没有新增设备验收。下文的 ADB 离线/短暂在线描述均是历次操作快照；以本轮记录为最近证据，不能将任何一次在线状态视为安装或游戏验收通过。首先建立宿主自动门禁并闭合安装页条件提交，随后继续设备事务与模型主链。

### 此前实施结论与记录

**仍有待移植内容，不能宣布上游 Android 功能已全部对齐。** 此前的 Android 调试/测试 APK 构建、BEM 1.4 格式测试和第三方模块宿主测试是源码与构建证据；它们不等于游戏进程与设备验收。2026-10-10 再次运行 Android 单元测试任务通过，ADB 未列出设备，故本次也没有新增实机证据。

当前实施顺序以 [Android 页面功能移植计划](ANDROID_UPSTREAM_PORT_PLAN_20261008.md) 为准。先核对「体验」「工具」页的生效时机和失败提示，再验「角色外观」的导入/索引/清理事务、「工具」页第三方模块的游戏进程行为，之后收口 BEM 下次启动加载；运行中热切换与异步加载排在这些基础链路之后。上游新提交先入功能差异账，只有满足该依赖顺序才进入实施。

| Android 页面入口 | 已有源码与证据 | 当前阻断 |
| --- | --- | --- |
| 体验：FOV、第一人称、运镜、MMD | 本地已有配置与原生运行入口；FOV 为事件驱动热下发，第一人称等为本地特有能力 | 相关设备交互与 MMD 音画同步未完成本轮实测；不同实现按行为核对。 |
| 角色：登录展示、语音、BEM 外观 | BEM/ZIP 选择、索引、启动物化、1.4 资源绑定及 NPC 同步根解析已接线，Android APK 构建通过 | 安装/清理事务和真实游戏换模未验；角色/语音快照需要同版本游戏输入。 |
| 工具：悬浮窗、诊断、第三方模块 | 页面、跨进程设置、第三方包发布与加载回执已有 | 框架断连、LSPosed 加载、模块界面和失败恢复缺游戏进程证据。 |
| 游戏内模型管理 | 已能写入安装索引；页面已提示保存后重启游戏 | 原生 `[CustomModel]` 更新返回 `NotReady`、`hot_switch=true` 在初始化拒绝；运行中换模仍未实现，页面真实渲染未验。 |

2026-10-10 已核对悬浮窗页面的 Provider 写入：模型操作只调用 `BemInstaller.saveChanges` / `disableAll` 提交安装索引；原生模型配置回调仍返回 `NotReady`，故把页面“立即发布/按热切换状态生效”改为“保存后重启游戏”。FOV 的 `applyGlobalFov` 会尝试发送运行命令，但 Provider 当前没有游戏侧实际应用回执，页面改为提示通过运行日志确认。三处提示均加入当前五套 Android 文案资源。首次构建因遗漏 `stringResource` 导入失败，补齐后 `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest` 通过；该结果仅证明编译和已有单元测试，不证明悬浮窗真实渲染或运行中 FOV 生效。

同次检查中 ADB 曾短暂列出 HLK-AL00，紧接着按设备序列查询报告 `device not found`，再次列举已为空；未取得安装状态或游戏进程信息，也没有进行安装或页面实测。设备验收仍未闭合。

按页面优先顺序审计 BEM/ZIP 导入链时，发现安装索引从损坏内容恢复为合法空列表 `[]` 后，管理页原先只在列表非空时清除错误标志，仍会显示“安装列表读取失败”。现改为任何成功解析都清除错误，并新增 Android instrumentation 检查“损坏索引 → 合法空列表 → 空态无错误”；调试 APK、测试 APK及单元测试任务已编译/运行通过，但该 instrumentation 尚未在设备执行。另发现页面刷新会把同版本旧草稿无条件覆盖到外部已更新的索引视图，且 `saveAll` 未带读取修订；此为下一步独立的索引冲突风险，不能以本次空态修复宣称导入/保存事务完整。

| 范围 | 当前代码证据 | 状态与下一步 |
| --- | --- | --- |
| BEM 1.4 多资源格式 | `bem.cpp`、Python 工具、资源级注册表与 Android 共享运行入口已接线；静态 renderer 构造和回滚有模拟 Unity 测试。Android 安装器现按每个资源验包，纹理转换后也逐资源复验；独立观察入口继续拒绝 1.4 | **代码链已接入，游戏运行证据未完成**。须以真实 Android 目标包、游戏进程日志、切换及还原验收后才能宣布支持。详见 [BEM 1.4 格式合同](BEM_V1_4_SPEC.md)。 |
| 模型加载与热切换 | 上游有 `async_loading.h`、`model_content_identity.h`、`model_job_runtime.inc`、`model_asset_cache.inc`、`generic_model_matcher.inc`、`android_lod_relations.generated.h`；本工作树仍未接入这套完整作业链。本地已完成部分原始绑定恢复前置，但 `hot_switch=true` 仍在初始化时拒绝；上游 `BemHotSwitchUpdater.java` 本地也不存在 | **待移植且设备未验**。以本地 `native/modules/custom_model/module.cpp` 为 owner 做子系统级前向移植，保留第一人称/头饰行为；不能把已做的恢复前置当作运行中热切换完成。 |
| Android 全局 FOV 热下发 | 上游 `GlobalFovUpdater.java` 存在，本地没有同名类；本地 `ModuleSettings.applyGlobalFov`、`OverlaySettingsProvider` 和原生 `global_fov` 命令已形成事件驱动下发链 | **已有类似实现**。上游定时轮询外部偏好，本地在设置变更时下发；是否需要外部写入自动发现及设备行为仍待核对。 |
| 第三方模块更新器 | 本工作树已有未提交的 `ThirdPartyModuleStore`、`ThirdPartyRuntimeMaterializer`、`XposedEntry`、原生宿主及测试改动；上游 `ThirdPartyRuntimeUpdater.java` 不存在于本地 | **本地实现已通过构建和宿主夹具，设备链路待验**。上游类名缺失本身不要求照搬；应按更新发布、加载回执、实际运行状态和失败恢复的行为合同对照。 |
| 其他上游差量 | v3.5.2/v3.5.3 还包含创作者 GUI、Workshop/下载站及发布、桌面运行面改动；本页未逐项完成行为等价审计 | **待分级**。先按 Android 产品边界排除桌面专有项，再核对 Android 可见行为与资源。不得以文件差量直接推断功能缺失或完成。 |

## 本轮可复现证据与边界

### 2026-10-10 包装 NPC 模型增量

上游 `c2f8eafc` 新增旧式角色 BEM 对包装 NPC `*_postmodel` 的适配。本地已在 Android 原生模型入口加入可选 `Transform.get_childCount` / `GetChild` 合同，并在唯一同名子根及唯一 `Mesh_all` 分支得到确认后，以内层模型根运行原有 world/UI 校验、绑定和恢复；原始外层 prefab 继续交给游戏。显式 BEM 1.4 资源路径不参与这次根重定向。合同缺失、非 GameObject、子节点歧义或越界时保持原资源路径。Android 调试 APK 与测试 APK 已构建通过，尚无本地游戏进程中 NPC 与关闭恢复的实机证据；本地异步作业链尚未接线，因此上游延迟交付的相同解析点仍待移植。

### 2026-10-09 上游 `a6562eb5` 增量（历史核查点）

再次获取上游后 HEAD 为 `a6562eb55c8e013234bd1d6b6d81a677fbf0b9c6`。新增的 Android 功能差量为 ZIP 内多个 BEM 的校验、选择与分别导入，含文件管理器 VIEW/SEND 入口；本地已接入有界归档读取、逐包原生验包与独立发布、无效项报告及取消。`BemImportArchiveTest` 宿主 27 项检查和双 APK 构建通过，调试 APK 核对 834 个 `.behw` 及一份清单。设备安装、ZIP 选择页面与框架发布的真实事务尚未验收，不能标记为设备完成。上游 `bde0adfb` 的 Better Endfield Next 包名和独立安装身份属于另一发行产品线，保留本地包名、第一人称及头饰实现；不直接移植包名变更。

再次核对上游 `BemImportRequest` 后，文件管理器入口增加单一 `content` URI、同一地址与非文本分享校验；Android instrumentation 测试已编译，设备未运行。HLK-AL00 当前在线，但调试 APK 的 `adb install -r` 在 30 秒以上无输出、系统前台仍为桌面，手动中断后 `pm path dev.betterendfield.android` 仍无结果。故不能将安装或路由标记为实机通过。上游 HEAD 再次 `fetch` 后仍为 `a6562eb5`。

安装阻断已进一步定位到手机系统安装流程：`dumpsys package` 存在已提交但未接受权限的安装 session；设备息屏时安装器停在后台，唤醒并解锁后显示未知来源风险确认，再进入华为「纯净模式」安全提醒。点击继续安装会触发华为应用市场网络检查，UI 自动报告「网络异常，请检查网络后重试」，`pm path` 仍为空。没有修改手机的纯净模式或安全策略，也没有把该过程计作 APK 安装成功。截图证据位于 Android 构建目录的 `install-screen-main.png`、`install-screen-progress.png`；需要用户在设备上处理系统安装策略/网络后，再跑 instrumentation、ZIP 页面与游戏进程门禁。

新增 Android instrumentation 的 ZIP 顺序索引策略检查：第一项压制旧同资源包，无效 Windows-only 项不改已提交索引，独立资源项共存，最后同资源项仅压制先前同资源项。测试 APK 已编译，但该测试尚未在设备运行，不能替代真实框架发布事务。再次双 APK 构建、`git diff --check` 通过，调试包核对 834 个 `.behw` 与一份清单。

后续对照上游 `BemInstaller.commitIndex` 发现本地 BEM 索引写入缺少持久化镜像：SharedPreferences 磁盘提交失败时，内存值与设置监听器可能已先看到新索引。现将安装、转换、移除、设置页和悬浮窗的索引写入口统一接到 `bem-index.json` 的 AtomicFile 写入/读回、SharedPreferences 提交及失败回滚；清理流程将两份索引引用取并集，镜像损坏时拒绝清理。Android instrumentation 增加镜像与偏好值一致性检查，已编译但未在受纯净模式阻断的设备执行。此改动仍需设备上验证框架监听、安装失败回滚及清理行为。

文件管理器在已有导入任务时再次打开文件，先前本地会静默丢弃 URI；现保留一条待处理请求，页面显示「继续导入待处理文件」，任务结束后可手动重试，新请求替换旧请求。Activity 重建保留待处理 URI，并避免把已处理的启动 Intent 再导入一次。Android 双 APK 编译通过，实际文件管理器路由与屏幕旋转仍待设备验收。上游实验性跳过校验开关未直接移植：其原生模式将部分外部输入上限放宽到无界，当前保留严格验包并列为设计风险。

待处理入口及文件请求拒绝原因已接入当前五套 Android 文案资源。再次双 APK 构建与 `git diff --check` 通过，调试包核对 834 个 `.behw` 和清单；`pm path dev.betterendfield.android` 仍为空，不能声称页面、旋转和分享重试在设备通过。

热切换只读复核再次确认 `module.cpp` 初始化对 `hot_switch=true` 的拒绝仍必要：现有原始绑定恢复和同根过渡不能替代运行时 registry 世代安装、已存在模板/克隆实例重绑和 Android 配置物化投递。上游 `InstallRegistryUpdate`、`ReloadRegistryAtDelivery` 与 rebind 队列不可仅移除保护门来假装对齐。本轮用 VS 2026 在 `build/hot-switch-verify` 重新构建，`BetterEndfield.CustomModelBindingTests --static-binding` 输出 `PASS: static MeshFilter commit, rollback and completed-root restoration`；生成原生合成夹具后 `BetterEndfield.BemV14Tests` 输出 `BEM 1.4 format checks passed`。本地 C4189 未使用变量告警已修正；构建仍出现上游 MinHook 的 C4701 和旧 CMake 最低版本告警，属于其第三方工具链债务，不作为热切换通过证据。

本轮尝试 `:app:lintDebug`：既有 Gradle 缓存离线缺少 `com.android.tools.lint:lint-gradle:32.1.1`；将新 Gradle 用户目录放在 D 盘的在线尝试又分别遇到发行包下载超时及 Kotlin DSL 脚本缓存 `NoSuchMethodError`，因此 lint **没有通过或失败的诊断结果**，不能当作已验收。新建的 D 盘临时 Gradle 缓存清理命令被自动安全策略拒绝，残留 `D:/CodexData/bem-gradle-lint-home` 与 `D:/CodexData/bem-gradle-lint-project-cache`，未触碰其他工作目录。上游再次 fetch 仍为 `a6562eb5`。

BEM 索引持久化镜像的写入顺序与回滚已抽成 `BemIndexCommit`，Android `BemInstaller` 保留 AtomicFile/SharedPreferences 适配。JDK 宿主 `BemIndexCommitTest` 实际通过 15 项检查：持久化先于偏好发布、磁盘失败不触碰偏好、偏好提交失败或读回不符均回滚、偏好异常也回滚、镜像回滚失败仍尝试偏好回滚并保留错误。Android 调试与测试 APK 再次构建通过、`git diff --check` 通过，包内 834 个头饰资源及清单。该宿主测试不模拟真实 SharedPreferences 监听或设备文件系统；对应 instrumentation/实机仍待安装门禁解除。上游 HEAD 再次 fetch 为 `a6562eb5`。

2026-10-09 运行时装载补齐上游索引归一化：`BemOptions.exclusive` 对旧/异常快照按后项优先处理冲突，拒绝重复 generation、非法启用状态与资源键，并在游戏私有目录复制之前校验外观、组件组合及参数。持久化索引不在此处改写。Android 调试/测试 APK 编译通过，调试包含 834 个 `.behw` 和一份清单；新增 instrumentation 覆盖冲突优先级、输入索引不变及非法快照拒绝，但设备安装门禁仍阻止执行。此项不是运行中热切换。

再次 `fetch upstream main` 后 HEAD 仍为 `a6562eb5`。热切换 Java 候选事务 `BemHotSwitchUpdate` 已加入，索引和配置各限 1 MiB UTF-8 字节以匹配原生读取上限，宿主 15 项检查通过，Android 双 APK 编译通过。它防止过期索引发布并保留可重试候选，但尚无原生队列或启动入口接线；`hot_switch=true` 仍被原生模块拒绝，因此不能声称热切换可用。此前设备安装门禁仍未解除。

启动物化再补全候选预检：`BemInstalledResources` 先验证所有启用包的外观、组件/参数选择、版本路径与声明字节数，然后才创建目录和打开远端资源。后序条目元数据无效时不会复制前序包；instrumentation 增加“源读取次数为零、目录未创建”检查，Android 双 APK 已编译，但设备测试未执行。字节流中途失败仍可能留下已复制的早期不可变代际文件；配置不会在异常时发布，清理与恢复需在设备上验收。

原生配置回调现对 `[CustomModel]` 请求明确返回 `BE_Result_NotReady`，避免旧回调忽略热切换内容却回报成功；普通 `StandaloneLod` 路径仍保留。Android ARM64 双 APK 构建与包内 834 个头饰资源检查通过。独立源码审计再次确认，原生阶段还缺空 registry 的 `resource.finish` hook、Unity 交付线程世代安装与已有根/克隆实例重绑；该拒绝返回是失败语义修正，不是热切换实现。

随后 VS 2026 构建 `BetterEndfield.CustomModel` 与 `CustomModelBindingTests`，实际 DLL 回调夹具通过：`[CustomModel]` 返回 NotReady、合法独立 LOD 返回 Ok、非法 LOD 返回 InvalidArgument；`--static-binding` 仍通过。构建出现第三方 MinHook `hde64.c` C4701 告警，本轮未修改该依赖。上游再次 fetch 仍为 `a6562eb5`。

本轮复核 Android 与原生配置体上限发现原生启动读取只接收 1 MiB；Java 候选事务原先的 4 Mi 字符上限既不一致也忽略 UTF-8 扩张，现改为索引/配置各 1 MiB UTF-8 字节。宿主 15 项检查（含多字节超限）和双 APK 构建通过，包内 834 个 `.behw` 与清单。HLK-AL00 在线但未安装本包；重试 `adb install --no-streaming` 后文件推送成功，安装进程停在系统 `com.android.packageinstaller/.InstallStaging`，屏幕捕获为黑屏，取消主机端等待后 `pm path dev.betterendfield.android` 仍为空。没有更改设备安装安全设置，实机验收仍未完成。

热切换原生前置新增资源名差集：`ChangedResourceNames` 对比注册表 `selection_key`，稳定列出新增、移除、改选的 world/UI 资源，且相同资源名不重复。`CustomModelBindingTests --registry-delta` 实际通过，Android ARM64 调试/测试 APK 再构建通过。该差集尚未驱动 Unity 交付线程或场景重绑，不能当作热切换完成证据。

原生候选计划 `PrepareResourceRegistryUpdate` 已把解析、启动锁定开关及诊断检查和受影响资源差集接到同一事务。真实 `legacy-install-fixture.bem` 的 `CustomModelBindingTests --registry-plan` 通过：未变候选不请求重绑；禁用候选列出 world/UI；热切换开关变化及缺包候选被拒绝，既有计划与活动注册表均保留。`--registry-delta` 也复测通过，Android 双 APK 构建通过。该计划尚未用于运行时发布，不能视为原生热切换完成。

同一 `legacy-install-fixture.bem` 若直接用于 `CustomModelBindingTests` 全量默认模式，会在其“同名双 draw 纹理隔离”夹具假设处失败；该 BEM 不是该模式所需的双 draw 合成包，故本轮只将它用作注册表候选测试输入，不以该次失败推断生产解析回归，也不宣称全量默认模式通过。

后续从项目原有 `tools/CustomModel/test_bem_v1.py::fixture()` 生成符合双 draw/同名纹理假设的 `binding-v1.bem`，全量绑定测试随后暴露模拟 renderer 未提供精确类型：新静态/蒙皮分流因此在材质失败注入之前拒绝网格提交。只修测试模拟对象的类型回报后，同一真实生产事务测试从 `partial failure not rolled back` 转为 `PASS: BEMv1 parser, exact donor identity, material isolation, rollback, ownership, appearance/LOD routing`。`--static-binding`、`--registry-plan`、`--registry-delta`、实际 DLL `--config-callback-dll` 也通过；修复未改生产回滚代码。上述旧夹具不适配说明仍有效，但全量默认模式现已有正确夹具的通过证据。

资源快照链再次核对：默认 `E:/Endfield Game`、本工作树及 `F:/bem` 的 `research/current-inputs` 均不存在；`UpdateResourceManifests.ps1` 需要当前 VFS、角色扫描与语音输入，不能把上游生成 JSON 当本地当前输入的重建结果。另将启动物化与热切换候选共用的配置上限收束为 `BemConfigurationLimit`：索引和最终原生配置均限 1 MiB UTF-8，在创建游戏私有目录和打开远端包之前形成并校验完整配置。宿主 15 项检查、Android 双 APK 编译通过；新增 instrumentation 断言超限配置不读源、不建目录，但设备安装门禁使该断言尚未执行。

低峰值加载的 CPU 内核 `async_loading.h` 已从上游选择性接入 `BetterEndfield::CustomModel` 命名空间，并加入本地 `BetterEndfield.AsyncLoadingTests`。真实 BEM 计划/解码、队列合并、公平调度、代际取消、结果寿命、大包独占、帧预算、延迟纹理和纹理流式共 80 项检查通过；Android ARM64 双 APK 编译通过，头饰资源 834 件及清单在包。当前生产 `AcquirePayload` 仍同步执行，作业队列尚未用于游戏内资源交付；这些测试不证明实际内存峰值或帧时间改善。

后续核对发现上游跨帧作业生产接线依赖克隆实例追踪；上游当前也关闭该钩子，并为模板交付采用同步回退。因此本地暂不将 CPU 调度器直接接到 `ResourceFinish`。本轮先修正现有同步缓存的身份：除路径、外观和选择外，还核对校验与加载优化标志，避免配置变化后错误复用旧解码结果。Windows 模块构建和完整绑定夹具通过；这不是异步加载或内存上界的运行时验收。

热切换回调的拒绝边界现覆盖配置中的任意 `[CustomModel]` 段落，包括其前有空行或独立 LOD 设置的情况；配置文本限制为 1 MiB，独立 LOD 值完整校验后才一次写入。DLL 回调夹具覆盖混合配置、无效值和超长输入，避免未执行的模型更新被误报为成功。实际热切换仍未启用。

热切换代际准备：`CompletedResource` 现记录提交时的 `selection_key`，直接根与自然克隆的完成判定均要求与请求选择一致；Android 世界资源复用 UI donor 时也传入当前选择键，不能把旧选择的 UI 完成记录当作新选择。静态绑定夹具覆盖旧/新选择判定，原生模块和 Android 双 APK 构建通过。这只闭合身份条件；旧代实例发现、失败保持旧绑定的重绑队列、Unity 线程注册表安装及 Java 投递仍缺失，`hot_switch=true` 继续拒绝。

完成判定进一步去掉了同根对象的直接成功捷径：即使根弱引用相同，也逐项核对 renderer、网格、骨骼、材质和选择键。夹具证明同根网格被外部改写时不再误报为已完成。该检查本身不负责发现未登记的场景实例；实例扫描与有界重绑队列仍是热切换前置。

旧代实例发现现有一个只读、尚未接线的原生阶段：根据已完成记录的生成网格实例 ID，在 `Resources.FindObjectsOfTypeAll(Renderer)` 的有界结果里寻找同网格的 renderer，最多向上查 64 层，以资源名识别场景根；候选最多 256 个并用强引用保活。场景数量超过 65,536、旧代网格身份超过 4,096、Unity 合同缺失或身份冲突时拒绝整个结果，不交付部分列表。宿主夹具覆盖克隆根去重及缺失合同拒绝；Android 世界绑定专用夹具已随选择键接口修正并通过。此阶段不发现首次启用时无旧网格的实例，也不执行重绑，`hot_switch=true` 仍拒绝。

设备安装复核：ADB 在线时将本轮调试 APK 推入 `/data/local/tmp`，从设备 shell 执行 `pm install -r -t`。约 40 秒后命令仍无结果，`dumpsys activity` 显示 `com.android.packageinstaller/.InstallStaging`，`pm path dev.betterendfield.android` 为空；已中断挂起命令并删除本轮推送的临时 APK。该路径仍不能提供游戏进程验收，也未更改设备安全设置。

旧代发现补上已登记模板根：即使记录中没有可用于场景匹配的生成网格，也可按完整根弱引用取得该模板；同时按实例 ID 去重模板与扫描所得克隆。宿主夹具覆盖模板、序号克隆、重复 renderer、无生成网格以及扫描合同缺失；发现结果仍未接入 Unity 线程重绑。首次启用时从未有完成记录的资源仍待单独验证。

同根改选的过渡判定已把 `selection_key` 纳入：即便 adapter 指针相同，当前选择与完成记录不同也必须进入原始状态恢复路径；宿主夹具覆盖旧/新选择。这是旧模板的必要条件，场景克隆仍没有独立完成记录，不能由此推断克隆可安全重绑。

模型同步生产链现选择 `LoadBem(..., defer_texture_payloads=true)`：缓存仅持有纹理目录与网格载荷，`CreateTextureFromBem` 在上传对应纹理时逐张解码，并核对载荷长度；缓存复用前检查包文件大小和修改时间，文件代际变化时丢弃旧载荷。完整绑定夹具现用延迟纹理载荷运行，覆盖两张纹理的实际解码、上传与绑定；Android 双 APK 构建通过。当前仍在 `resource.finish` 同步执行，且尚无游戏帧上传峰值数据，不能宣称上游异步作业或整体峰值目标完成。

同步纹理上传还接入了上游可选的 `Texture.GetNativeTexturePtr` 渲染线程同步点：默认累计约 4 MiB 上传后调用一次，已配置的 `fast_loading` 模式用 128 MiB 阈值。原生绑定夹具覆盖两种阈值；初始化日志会报告该 Unity 方法是否可解析。方法缺失时继续原有上传，不把阈值误报为已控制内存峰值。设备上的实际同步耗时与峰值仍待测量。

`b67a2f4..ad33357` 包含 `613a50e` Android 纹理/热切换/设置重连修复、`63c4c76` 3.5.4 发布草稿及 `ad33357` 热切换稳定性修复。Android 运行时新增纹理预算、纹理流式诊断和 LOD 观察，模型作业及失败后重新绑定亦有改动；Java 侧新增框架启动、包替换接收、设置重连与模型目录状态。当前本地没有这些上游新合同的完整行为等价证据，分类和依赖见[功能对账](ANDROID_UPSTREAM_FUNCTION_MATRIX_20261008.md)与[移植计划](ANDROID_UPSTREAM_PORT_PLAN_20261008.md)。

本地阶段 2 已把显式资源路径/LOD 匹配送入 Android `PrepareResource`，并增加静态 `MeshRenderer`/`MeshFilter` 的读取、网格构造、提交和恢复代码；`CustomModelBindingTests --static-binding` 的模拟 Unity 事务已覆盖静态接收与混合 renderer 失败回滚。Android 共享入口现在使用 `ParseResourceModRegistry`，显式资源绕开旧 world/UI 配对，安装器原样导入及纹理转换均逐资源验包。独立观察入口继续拒绝 1.4。游戏进程/实机换模证据未完成；当前连接的 ARM64 设备未查询到游戏，不能充当运行时验收。安装器专项测试 APK 已构建，设备安装会话失败，尚无该专项测试结果。

安装索引、设置页与悬浮窗的包冲突判断已改为按包 ID、目标及 Android 资源键：不重叠的 1.4 资源包可以共存，重叠时停用旧包；清理按包 ID 限定。该行为的 Android instrumentation 测试已编译，设备执行仍待成功安装。热切换审计确认本地只在启动时准备包，当前运行中选择变更不会替换场景实例；旧 adapter 持有已接入，但原始绑定恢复、实例重绑和第一人称/头饰恢复顺序仍需完成，见[移植计划](ANDROID_UPSTREAM_PORT_PLAN_20261008.md)。

注册表解析现只在候选完全有效时替换旧结果，畸形配置不再清空原有资源；`BetterEndfield.BemV14Tests` 已用保留旧 adapter 与资源匹配的回归夹具验证。运行中热切换的旧 adapter 生命周期与场景实例重绑仍未实现。
已发布资源记录现在持有旧 adapter 的共享所有权，注册表成功换代也不会立即释放它；原生存活夹具和 Android ARM64 编译通过。旧实例的恢复、重绑及何时释放记录仍待实现，因此不将此项视为热切换完成。
当前同一资源实例若已由其他配置代际提交，运行时会拒绝再次叠加替换；原始 Mesh、材质、骨骼及阴影代理尚不能可靠地跨代际恢复。本轮 Android ARM64 调试构建再次通过，但没有设备行为证据。
已发布记录现在保存这些原始对象的弱引用和状态；跨代际恢复验证与记录释放仍未完成。框架服务晚连接缺口也已定向补齐：BEM 与第三方包发布等入口最多等待 10 秒，BEM 安装可取消；独立宿主测试 `FrameworkServiceWaitTest` 通过，设备晚连接场景待验。
原网格与原阴影网格的防卸载 pin 已按 `hot_switch=true` 条件接入，并要求 Unity 标志可读回；Android 编译通过。恢复事务、关闭模块时的标志清理、内存峰值和设备行为仍未验，运行中热切换未开放。
模块初始化现在明确拒绝 `hot_switch=true` 配置并记录原因：Android 尚无热切换发布入口，且关闭时的 Unity 标记释放未验。pin 代码是恢复链的前置实现，当前正常配置不会启用它。本轮 Android ARM64 调试构建通过。
自身 APK 替换广播现通过非导出接收器短时保留进程，等待已有框架监听回连；双 APK 构建通过，打包清单已核实接收器及 `exported=false`。设备安装失败，真实更新重连未验。
悬浮窗前台只读重连已定向接入；`OverlayReconnectPolicyTest` 验证写入不重放、后台不唤醒、30 秒冷却及重试入口，Android 双 APK 编译通过。模块唤醒 Activity 无用户数据输入，最多 1.5 秒自结束；真实框架断连与焦点交互仍待设备验收。
模型目录状态已接入悬浮窗 Compose 页：首次读取失败与真实空列表分开，失败后仅保留上次列表供查看并禁用写入，较旧回调不能覆盖新请求。调试 APK 和测试 APK 构建通过；设备安装期间两次断连，`OverlayModelCatalogStateTest` 尚未在设备运行，页面交互仍未验收。
创作者定向目标草稿工具 `build_bem14_target.py` 与上传量报告 `bem_upload_report.py` 已接入，共 5 项宿主测试通过；前者只输出未验 profile 与 keep-only 项目，后者只估算包字节量，不把离线身份或字节估算当作游戏运行和实际显存证明。真实 Android 元数据输入仍缺，Blender 导出仍待移植。
上游 Blender 插件复核为旧固定外观、`windows-x64` 目标及 16/12/12 蒙皮布局，不满足 Android BEM 1.4 资源级合同；不能通过复制插件声称 Android 导出已对齐。本地独立 BEM Tools 打包还缺插件目录与 7zip 后端产物，属于 Windows 作者工具线，不作为 Android APK 阻断。
热切换内容身份复核：本地资源注册表的 `selection_key` 已包含包路径、修改时间、大小与选择参数，当前 10 秒 payload 缓存按该键查找；上游 `model_content_identity.h` 的整包 SHA-256 服务于未移植的 CPU 后台作业/活体资产缓存。当前不能将整包哈希插入游戏资源接收线程冒充后台作业移植；热切换仍保持拒绝。
设备再核：HLK-AL00 一度在线且 `/data` 可用约 106 GiB，`pm path dev.betterendfield.android` 无安装路径；尝试安装仅 123 KiB 的测试 APK 时 ADB 在取得设备特征前即断开，说明当前断连不只由 81 MiB 主 APK 传输导致。设备未安装主包，游戏进程及页面专项验收继续待执行。
原始绑定恢复已补一层逆向事务合同 `RestoreResource`：任何接收器恢复失败时，对已触达的接收器重新发布当前模型状态。`PrepareOriginalRestoration` 先核对资源根与 renderer 的弱引用身份、当前网格/材质/可见状态及原对象存活，再构造全部原始绑定；`RestoreCompletedRoot` 在禁用配置的资源再次交付时恢复原绑定，成功后撤销该根的活动记录，保留仍被克隆实例引用的旧资产谱系。模拟 Unity 测试覆盖原始静态网格/材质恢复、外来网格拒绝及记录撤销。`CustomModelBindingTests --static-binding` 经 Visual Studio 2026 编译并运行通过；Android 调试 APK 编译通过。此路径仍受 `hot_switch=true` 初始化拒绝保护，配置更新、场景实例重绑及同一资源的第一人称/头饰恢复顺序未闭合，不能据此宣布热切换可用。
同根新外观过渡现先确认候选包可读取，临时恢复旧根原绑定后复用现有准备/提交链；候选准备或提交失败时尝试重新发布旧外观。宿主夹具覆盖中止过渡的旧网格/材质和活动根恢复，Android 调试 APK 编译通过。当前没有运行时配置发布入口，跨场景实例及第一人称/头饰的一致切换也未验，初始化仍拒绝 `hot_switch=true`。
临时解绑的旧替换网格现于过渡期间短时保活，失败回滚或新外观发布后释放本次添加的防卸载标志。宿主夹具验证了保活与释放；这只覆盖网格生命周期，不证明原材质、骨骼和游戏中克隆实例的恢复行为。
补充夹具验证保活合同缺失时，跨代际过渡在改写 renderer 前拒绝。上游纹理预算与流式诊断已分别接线，但均未进行游戏进程验收；预算写入的所有权/恢复和有界诊断属于不同合同，依赖及门禁见移植计划。
纹理预算 policy 与运行时协调已接入 Android 模块并保持可选；Unity 入口不可用时不接管游戏预算。流式诊断和 LOD offset observer 已接入，前者只读且有界，后者只转发与记录原写入。宿主 policy 测试 Debug/Release 各通过 364 项检查，runtime 测试 Debug/Release 各通过 19 场景、1801 项检查，diagnostics 测试 Debug/Release 各通过 73958 项检查；ARM64 调试 APK 构建通过。设备尚无本轮游戏进程日志，不能声称纹理预算实际已扩大、流式采样可用或停用恢复已通过。
本轮再次查询 ADB 时设备列表为空，`pm path` 返回无设备；预算功能及此前 BEM 1.4/重连功能仍无法以手机上的运行行为验收。
本轮重新查询连接的 HLK-AL00，`adb devices -l` 显示设备在线，但 `adb shell pm path dev.betterendfield.android` 未返回安装路径且以非零状态结束；先前安装尝试仍不能算成功，专项 Android 测试和游戏进程验收没有新证据。
设备安装会话现已返回通用失败，系统安装页关闭，失败前 `pm path dev.betterendfield.android` 仍为空；随后 ADB 设备断开，未能读取安装时系统日志，故不推断失败原因。专项测试与游戏进程验收继续待执行。资源生成所需的当前游戏目录与 `research/current-inputs` 在本工作树及 `F:/bem` 均未找到，角色/语音快照不能声称已对齐，也不能直接复制上游生成物。
本轮重新连接后改用非流式安装，APK 已推送，系统安装页出现后 ADB 再次断开；命令随后结束，但重新连接后的包列表与 `pm path` 均无 `dev.betterendfield.android`，所以不能判安装成功。BEM 1.4 索引专项测试补充旧版同目标互斥、不同目标共存及同包 ID 更新，测试 APK 构建通过，设备执行仍待成功安装。
克隆恢复身份继续收束：Android 原始绑定现在保存从资源根到原骨骼的路径，已完成绑定保存 renderer 和替换骨骼的根相对路径；路径不可取得时清空该组记录。现有完成判定仍可能退回名称，因此不能把这些记录视为严格克隆身份门禁。本轮尚未用这些路径执行场景克隆重绑或恢复，`hot_switch=true` 仍被拒绝。Windows 原生模块与两组绑定测试目标构建无告警，静态绑定、真实 BEM 夹具、注册表差集与候选计划、DLL 回调及 Android world 夹具均通过；Android 双 APK 离线构建通过，主 APK 含 834 个头饰资源及清单。设备安装限制仍使游戏内行为不可验。
自然克隆的已完成判定现在会在 Android 路径记录可用时核对 renderer 根相对路径，并在完整骨骼路径记录可用时逐项核对骨骼路径；同名但不同位置的对象不再仅凭名称复用完成记录。路径缺失时仍沿用既有名称判定，故此改动尚非克隆恢复的严格身份门禁。Android 双 APK 再构建通过，未取得游戏进程证据。
再次核对 `upstream/main=a6562eb5` 的热切换评估与 `model_job_runtime.inc`：上游文档把场景克隆原骨骼路径重绑、失败保留旧包列为实验热切换合同，同时明确不能由队伍切换推断资源即时重载；克隆钩子请求在上游源码仍为 `false`。本地不能将名称回退或上游的实验合同当作 Android 克隆恢复已验收。下一阶段需先完成路径缺失时的安全拒绝与旧模型保持，再接运行时注册表发布和克隆重绑；当前完成判定的宿主路径检查 67 项通过。
资源再次交付前新增旧自定义资产谱系门禁：完整已完成判定未通过、且非同根跨选择过渡时，若当前接收器仍引用已记录的生成网格或替换材质，则保留现场并拒绝将它当作干净 donor 再次套包；读取接收器列表失效或超界也保守拒绝。静态绑定夹具覆盖旧模型克隆被识别与干净接收器可继续，真实 BEM、注册表差集/计划及回调夹具通过；Android 双 APK 编译通过。此门禁不执行克隆恢复或即时更新，`hot_switch=true` 仍拒绝；实际 Unity 场景对象与内存走势待设备验收。
谱系门禁的材质身份改为提交时记录每个槽是否真的替换，避免原始材质弱引用失活后把共享原材质误判为生成物。静态绑定夹具现在覆盖仅材质替换、未替换的共享材质以及干净接收器；Windows 模块/夹具和 Android 双 APK 构建通过。HLK-AL00 当前在线，近期 logcat 只见 Pure Mode 决策记录，未见此次 APK 安装的明确结果码；`pm path` 仍无应用路径，不能据此归因或声称实机通过。
谱系门禁不再仅比较适配器指针：旧完成记录由旧注册表持有，候选注册表会创建新的适配器对象。现在以角色 ID、world/UI 资源名、资源 ID/资产路径、LOD 与显式资源类型核对同一资源身份，再查旧网格/材质谱系；夹具覆盖新旧适配器可识别同一旧克隆，以及无关角色不串联。Windows 原生模块和静态绑定/候选计划夹具、Android 双 APK 均通过；再次拉取的 `upstream/main` 仍为 `a6562eb5`。候选注册表安装、场景重绑及游戏内验收仍未完成。
当前克隆完成判定已取消路径缺失时的名称回退：Android 上若被检查对象不是记录中的同一资源根，renderer 路径必须有效，且骨骼路径数必须与绑定记录的骨骼数一致，否则不能标为已完成。仍带旧生成资产的克隆会随后被谱系门禁保留现场，不会再次以旧包资产充当干净 donor。同根对象保持已有完整绑定检查。通用匹配夹具 70 项、静态绑定夹具和 Android 双 APK 编译通过；这只保证安全拒绝，仍不代表克隆已恢复或新包已发布。
Android 克隆完成判定现进一步枚举克隆根下的 Transform，以保存的根相对路径唯一解析每根骨骼，并与 renderer 当前骨骼数组逐项核对。枚举限 65536、请求限 256；路径缺失、重复、根外对象或枚举失败会整体拒绝，不发布部分结果。通用路径夹具 74 项及 Android 双 APK 构建通过；解析器仅用于当前克隆身份确认，尚未构造克隆的原骨骼恢复数组，设备游戏进程验收仍缺失。
原始绑定恢复准备现增加 Android 克隆分支：先确认克隆根对应旧记录的 world/UI 资源，再唯一解析克隆渲染器；显式资源还核对 static/skinned 接收器类型。原骨骼按克隆自身的 Transform 路径构造新数组，当前旧包骨骼、网格、材质和可见状态仍须吻合。全部绑定准备成功后才交付结果，失败时输出保持空；宿主夹具覆盖同根准备失败清空，通用路径夹具 74 项及 Android 双 APK 构建通过。克隆分支尚未被运行时更新事务调用，也没有 Android 游戏内测试，不能声称克隆恢复或热切换完成。
停用包的资源再次交付路径现可调用克隆恢复准备：同根记录优先；非同根资源必须仅有一个旧完成记录能完整匹配，随后使用现有 `RestoreResource` 提交原始绑定并在失败时回滚旧包。多重候选、原始资产失活或路径不完整均拒绝，不清除模板记录，以便其他克隆仍可恢复。Windows 静态绑定/候选计划夹具和 Android 双 APK 构建通过，但 Android 克隆事务没有运行时测试；`hot_switch=true` 仍在初始化时拒绝，故该路径尚不可作为已启用功能验收。
候选计划的旧实例发现补齐仅材质替换的克隆：除生成网格外，也按提交时标记为已替换的材质实例 ID 找场景 renderer；同一资产对应两个旧 owner、场景枚举超界或读取失败时拒绝整个候选，不返回部分根列表。静态绑定夹具覆盖材质独占克隆、未替换共享材质及跨 owner 冲突；Windows 模块/夹具与 Android 双 APK 构建通过。候选注册表仍未安装，完整场景恢复/重绑、第一人称与头饰状态顺序及设备游戏进程验收仍未闭合。
全场景事务新增只读准备阶段 `PrepareStaleModelRestorations`：先发现候选影响的根，再为每个根唯一确定旧完成记录并准备原始绑定；任何根缺失、多个记录匹配或准备失败都不交付部分计划。发现列表的根去重现在也核对 owner，同一根被不同旧记录声称时拒绝。宿主静态绑定夹具覆盖完整模板计划、克隆无法准备时空输出及根 owner 冲突；Android 双 APK 构建通过。该阶段尚未连接运行时配置回调，也未提交场景状态改变，不能据此宣布热切换开放。
多根恢复提交函数 `RestoreStaleModelRoots` 将已准备的接收器展开为同一个 `RestoreResource` 事务；后续接收器失败时，既有事务语义会对已触达的接收器重新发布旧模型。提交前还核对根强引用、旧记录 owner/索引及 65536 接收器上界。宿主夹具验证单根准备后恢复及旧模型重新发布，Android 双 APK 构建通过；多根真实 Unity 失败回滚、候选注册表安装、新包重绑及设备进程状态仍未验，该函数尚未由配置发布入口调用。
跨根失败夹具现直接模拟第二个根的材质恢复 setter 失败，验证恢复事务把第一个根及失败根都重新发布为旧模型；过期计划的 clone/同根身份不符则在写入前拒绝。Windows 原生模块与静态绑定夹具、Android 双 APK 构建通过。夹具使用模拟 Unity 对象，不能替代真实场景中材质、骨骼、第一人称和头饰状态的联合验收；运行时配置发布入口仍未调用该事务。
Android 候选配置只读预检 `PrepareAndroidRegistryUpdate` 现在把注册表解析、变化资源的完整 BEM 解码/适配器核对、旧场景根恢复准备串成一个门禁，失败不交付部分计划；保留的已解码候选载荷总量限 128 MiB，场景准备后再次检查不可变包代际。此上限属于热切换额外内存预算，超限候选保持旧配置；正常启动载入合同未据此改变。Android 调试/测试 APK 构建通过，但预检尚未接入配置发布入口，设备也未执行该候选事务。

- `git fetch upstream main --prune` 后，上游 HEAD 为 `a6562eb5`；本轮继续按功能对账，不能将生成快照或未接线的热切换前置代码算作已移植。
- `:app:assembleDebug :app:assembleDebugAndroidTest -PheadwearCatalogDir=D:/CodexData/headwear-audit/all-characters/catalog-bundled --offline` 成功；APK 内有 834 个 `.behw` 和一份 `manifest.tsv`。这只证明本次 Android 构建与资源封装。
- 仓库目标 `BetterEndfield.BemV14Tests` 重新构建后输出 `BEM 1.4 format checks passed`；CustomModel Python 测试本轮运行 208 项，通过 206 项、跳过 2 项。第三方更新器的原生宿主夹具此前通过 A→B→A、恢复、快速更新、移除和重启场景。
- 本轮未运行游戏进程、LSPosed 或实机验收；未提交、合并或发布。既有 `.workbuddy/` 不属于本轮修改。

## 历史文档使用方式

`ANDROID_PORT_PROGRESS_20261005.md` 以 v3.5.0 为基线，内部同时保留了当时已落地与“0 文件待移植”的阶段记录；`ANDROID_UNPORTED_FEATURES_20261005.md` 以 v3.5.1 为基线；`ANDROID_DELTA_VS_UPSTREAM_3510FA7.md` 的阶段记录更新到 v3.5.2。它们可用于追溯判据和旧差异，不能替代本页对 v3.5.3 与当前未提交工作树的判断。后续每完成一个功能，应同时更新本页的代码、构建、设备三档证据。

逐功能类别和例外见 [Android 上游功能对账](ANDROID_UPSTREAM_FUNCTION_MATRIX_20261008.md)；它区分旧版、不同实现与类似实现，避免把上游独有文件直接判为功能缺失。
