# Android 移植规划与完成状态（2026-10-10）

本文是本轮审计后的执行总表。详细技术依赖见[定向移植计划](ANDROID_UPSTREAM_PORT_PLAN_20261008.md)，逐项上游差异见[功能对账](ANDROID_UPSTREAM_FUNCTION_MATRIX_20261008.md)，历次证据见[状态记录](ANDROID_UPSTREAM_STATUS_20261008.md)。后续按本文的顺序推进；旧文档中的阶段记录不能覆盖本表的当前结论。

## 范围与基线

- 产品目标仅为 Android ARM64：LSPosed 模块、设置应用、游戏内悬浮面板及所需创作者工具。
- 主线 `F:/bem`：`main` / `5e09128c`，本轮审计时工作区干净，比本地 `origin/main` 多 25 个提交。
- 移植工作树 `D:/CodexData/bem-android-thirdparty-alignment`：`codex/android-thirdparty-alignment` / `55f3e16f`，包含主线，额外 22 个提交，与其远端跟踪分支一致。
- 审计前已有 50 个已跟踪文件修改、45 个未跟踪条目，暂存区为空。该数量不包含本次新增规划文档；近期移植不能仅按已提交分支判断或交付。
- 本轮使用的本地上游引用为 `c2f8eafc`；本轮审计没有重新 fetch，不宣称该引用为远端最新状态。
- Windows 产品、Better Endfield Next 包名/安装身份、下载站与 Workshop 不进入本轮主线。角色/语音与 LOD 生成数据必须来自同版本可信输入。

## 完成口径

| 状态 | 含义 | 允许的结论 |
| --- | --- | --- |
| 已有实现，待回归 | 本地已有相应能力，本轮主要保护既有行为 | 不因上游类名不同而重写；不能声称本轮实机通过 |
| 源码已接线，待验收 | 生产调用链已接入，部分构建或独立测试已有证据 | 只能声明具体源码/测试层完成 |
| 前置已完成，功能未完成 | 有合同、组件或夹具，生产更新/交付链未闭合 | 不能作为用户可用功能计入完成 |
| 未完成 | 缺核心实现、已知缺陷未修复或验收门禁未闭合 | 继续实施或验证 |
| 不纳入 | 当前 Android 产品边界之外 | 不列为待移植功能，不占主线排期 |

本轮没有新增任何完整的设备验收结论。源码接线、宿主测试、APK 编译、APK 内容核对、instrumentation 执行、游戏行为验收分别记账，不计算混合完成百分比。

## 功能完成与未完成总表

| 编号 | 功能/工作域 | 已完成的具体部分 | 尚未完成/风险 | 当前状态 | 唯一 owner / 下一阶段 |
| --- | --- | --- | --- | --- | --- |
| F01 | FOV 与页面生效提示 | 事件驱动命令链已有；模型保存提示改为重启生效；FOV 提示以日志确认 | 游戏侧实际应用回执、真实渲染和交互未验 | 源码已接线，待验收 | `ModuleSettings`、设置 Provider、原生命令 owner；S3 |
| F02 | 第一人称、运镜、MMD | 本地配置、原生入口和 MMD 曲库/音频等已有实现 | 本轮设备回归、MMD 音画同步、与新模型资源共存未验 | 已有实现，待回归 | 相机/音频 owner；S3、S4、S5 的并行回归线 |
| F03 | BEM/ZIP 导入与文件入口 | 有界读取、逐包验包、混合包选择、无效项报告、取消、忙时单条待处理请求已接入 | VIEW/SEND、旋转、部分成功、框架断连与取消后的设备事务未验 | 源码已接线，待验收 | `BemInstaller`、`BemImportArchive`、Activity 薄接线；S2 |
| F04 | 安装索引与镜像 | AtomicFile 镜像、偏好提交、失败回滚、清理引用并集已有；15 项提交检查通过 | 真实偏好监听、磁盘失败、写成镜像后读回失败的注入场景未验 | 源码已接线，待验收 | `BemInstaller` / `BemIndexCommit`；S1、S2 |
| F05 | 安装页草稿与跨入口保存 | S1：installer 统一读取修订/条件提交；无编辑读最新，有编辑保留草稿/拒绝保存，确认放弃后读最新；六组宿主 90 项及双 APK 编译通过 | 六项新设备用例仅编译；PJX110 暂不可用，真实双入口、提示、按钮/确认未验，不能声明 P1 完整收口 | 源码已接线，S1 设备门禁待验 | `BemInstaller` / `BemIndexRevision`；先补 S1 验收，不进 S2 |
| F06 | BEM 移除与清理 | 索引先停用、不可变代际清理、索引损坏时拒绝清理等保护已有 | 失败/取消/部分清理、两份索引暂不一致时的数据保留未实测 | 源码已接线，待验收 | `BemInstaller`、框架文件适配；S2 |
| F07 | 框架等待与悬浮窗恢复 | 有界服务等待、包替换接收器、读取重连策略、保留失败目录视图已接入 | 晚连接、APK 更新、拉起/焦点、写入不重放等设备场景未验 | 源码已接线，待验收 | 框架连接 owner、Provider、目录状态 owner；S3 |
| F08 | 第三方模块运行更新 | 私有代际、发布 ID、宿主应用回执、依赖/启停、二进制升级需重启的合同已有 | 真实 LSPosed 加载、网页桥、失败保留旧代际与启停排序未验 | 源码已接线，待验收 | `ThirdPartyModuleStore`、物化器、native host；S3 |
| F09 | BEM 1.4 格式与创作者工具 | 多资源解析、选择、冲突注册表、目标构造、上传估算、LOD 候选生成已有；本轮相关 Python 26 项通过 | 缺可信同版本 Android 元数据/LOD 输入；未交付已验证目标数据或 Android Blender 导出 | 格式/工具独立层已有完成证据，产品供给未完成 | `bem`、registry、CustomModel 工具；S4、S7 |
| F10 | BEM 启动换模 | 资源级启动物化、静态/蒙皮分流、精确 renderer 路径、同步 NPC 包装根、局部事务恢复已接线 | BEM 1.0–1.4 真实包 world/UI、LOD、NPC、禁用及失败恢复未验；world/UI 矩阵差异放行需明确合同并实测 | 源码已接线，待验收 | `custom_model`、Android world/UI adapter；S4 |
| F11 | 完成记录生命周期 | 弱引用、选择身份和 pump 清理已有 | **P2：仅原共享材质存活时仍保留完成记录；缺总量上界和长会话回收夹具** | 未完成，设计风险 | `custom_model`；S4，热切换前收口 |
| F12 | 运行中热切换 | 候选解析/差集、adapter 存活、原绑定保存、局部恢复、Java 候选事务已有；Java 15 项通过 | 空 registry 激活、Unity 线程世代安装、模板/克隆重绑、失败回滚、关闭清理、Android 投递未闭合；`hot_switch=true` 仍拒绝，配置更新返回 `NotReady` | 前置已完成，功能未完成 | `custom_model` 核心先行，Android 更新器后接；S5 |
| F13 | 候选准备内存预算 | 候选保留量已有 128 MiB 检查 | **P2：检查位于完整解码之后，不能约束准备峰值；需要把剩余额度传入解码** | 未完成，接线前设计风险 | `custom_model` / BEM 解码预算；S5 前置 |
| F14 | 异步加载与帧预算 | CPU 队列/帧预算组件、延迟纹理载荷已有；历史独立调度测试通过 | 内容身份、文件租约、订阅/Poll、跨帧提交尚未进入生产 `resource.finish`，当前仍同步 | 前置已完成，功能未完成 | `custom_model` 作业与交付 owner；S6 |
| F15 | Android 纹理预算与诊断 | policy/runtime、指标读取、pump 调节/恢复、LOD observer 已接线；历史独立夹具通过 | getter/hook 成功率、外部改写、画质切换、内存压力、停用读回未实测 | 源码已接线，待验收 | 原生 Android 预算 owner；S4、S6 |
| F16 | 自动验收入口 | S0 门禁已接线；S1 扩展后本地通过：六组 Java 90 项检查、四组 Python 26 项测试；Gradle `check` / `test*UnitTest` 依赖门禁；CI APK job 依赖宿主 job | 远端 CI 尚未执行；instrumentation 与游戏设备验收仍未完成；既有工具链弃用告警列为范围外债务 | S0 本地门禁完成，CI 待运行 | `tools/AndroidPort/run_host_gate.py`；之后逐阶段增补 |
| F17 | 角色/语音资源快照 | 既有生成器与 manifest 合同保留 | 缺同版本游戏输入，不能直接复制上游生成物冒充本地生成 | 未完成，输入受阻 | 资源生成器；S7，可独立准备输入 |

## 推荐执行顺序

| 阶段 | 目的与收益 | 依赖 / 范围 | 交付与验收入口 | 停止条件 |
| --- | --- | --- | --- | --- |
| S0 自动门禁 | 使现有回归测试可重复执行，消除空测试任务的误判 | 独立测试接线；不改产品行为 | 将五组 Java 宿主测试及选定 Python 测试接入统一命令/CI；命令必须返回真实测试数、失败非零退出；接线后登记精确命令 | 全部门禁真实执行；不得以 `NO-SOURCE` 或旧编译产物计通过 |
| S1 索引条件提交 | 阻止跨页面丢失用户选择，闭合当前 P1 | S0；索引修订/条件提交 owner、真实草稿与可见冲突 | 双入口交错测试：初始读取→Provider 更新→刷新/保存；新包压制旧包→刷新；同版本参数/开关冲突；无冲突保存 | 不静默覆写，不丢草稿；页面、Provider 共同消费 owner 的修订合同 |
| S2 导入/清理事务 | 保护已安装包与失败恢复 | S1；单包、ZIP、索引镜像、移除/清理、VIEW/SEND | 宿主失败注入与设备 instrumentation；混合 ZIP、取消、部分成功、晚连接、镜像故障、旋转/连续分享 | 原包与引用文件保留正确；部分失败如实可见；设备安装门禁未解则保持待验 |
| S3 页面/第三方设备链 | 对齐页面承诺与真实运行状态 | S2；既有 FOV、框架恢复、第三方 host | 真实页面加载/失败/恢复、游戏日志、LSPosed 加载、依赖启停、网页桥、升级重启状态 | 发布回执与模块运行状态分别核对；无错误成功提示；设备链真实通过 |
| S4 启动换模与资源恢复 | 先建立可靠的静态运行基础 | S2；F09–F11、F15，保护相机与头饰 | 完成记录回收夹具；真实 BEM 1.0–1.4 world/UI、LOD、NPC、静态/蒙皮、第一人称进出、换角、禁用/失败保留、长会话；预算外部改写与停用恢复 | 不积累无用记录；变形与骨骼放行策略有证据；资源恢复与设备表现通过 |
| S5 热切换核心与投递 | 运行中启用/关闭无需重启 | S0–S4；先解码预算，再世代事务、实例追踪，最后 Java 投递 | 空→有、有→新、禁用、重复/快速更新、候选错误、失败保留、模板/克隆重绑与关闭清理；组合第一人称/头饰设备验收 | Unity 线程提交/恢复完整，失败保留旧代际，接受请求与实际完成分开；全部门禁通过后解除拒绝 |
| S6 异步交付与性能 | 降低加载峰值和帧卡顿 | S4、S5；内容身份、租约、CPU 作业、帧预算/提交 | 首次加载、重复进入、换角、重入、取消/失败、画质切换的帧时间/内存峰值；保持恢复正确 | 生产调用链真实使用队列/预算；资源有上界与释放；保护机制介入不视为稳态通过 |
| S7 资源供给与发布收口 | 形成可交付的 Android 产物 | 同版本输入准备可独立进行；发布依赖所选交付功能的门禁 | 输入→生成器→manifest/LOD 覆盖率；本轮范围提交、最终 Android 构建、签名/版本/SHA-256、834 头饰与清单、设备回归 | 待交付功能无 P1；P2 已收口或明确范围外；未提交源/文档/fixture 均审计，所有门禁与 Git 边界明确 |

S0 本地门禁已完成；2026-10-11 本轮仅推进 S1 / F05 源码与构建，详见 [S1 合同与验收](ANDROID_INDEX_CONDITIONAL_SAVE_20261011.md)。设备不可用，下一步先补六项设备测试及真实页面交互，不进入 S2。S2–S4 设备结果保持未验。缺同版本输入时不生成虚假资源快照，按阶段停止条件推进。

## 验收命令与证据

以下为本轮实际证据，不代表已执行后续阶段的新增测试：

| 验证入口 | 本轮结果 | 证据限制 |
| --- | --- | --- |
| `git diff --check`（主线与移植工作树） | 通过 | 不证明未跟踪文件全部合规或产品正确 |
| 在 `tools/CustomModel` 执行 `python -B -m unittest test_bem_v14 test_bem_upload_report test_build_bem14_target test_generate_android_lod_relations` | 26 项通过；使用现有 `D:/CodexData/better-endfield-bem-env-20260926` 环境 | 最初选取的通用 Python 缺 `zstandard`；通过结果来自具备依赖的现有环境，不是新装环境 |
| JDK `javac -encoding UTF-8` 编译当前纯 Java owner 与 `src/testHost/java` 对应测试；再以 `java -cp <输出目录> dev.betterendfield.android.<测试类>` 执行 | `BemHotSwitchUpdateTest` 15 项、`BemImportArchiveTest` 27 项、`BemIndexCommitTest` 15 项；`FrameworkServiceWaitTest`、`OverlayReconnectPolicyTest` 通过 | 输出位于 `D:/CodexData/bem-audit-20261010-java`；不模拟 Android 偏好监听、设备磁盘与页面，不证明热切换已投递 |
| 读取现有 `android/app/build/outputs/apk/debug/app-debug.apk` | 834 个 `.behw`、1 份 `manifest.tsv`、ARM64 原生库；SHA-256 `3F211815B21B851ABA9A500E854CC8AD09C7DBB554D56B3F7EE0D16BCDF007FE` | 本轮没有重建；上轮 APK 构建记录与本轮内容读取分别计证据 |
| `adb devices -l`、项目包 `pm path` 查询 | 本轮列出 HLK-AL00；项目包安装路径查询为空 | 没有安装、instrumentation、页面或游戏行为验收 |

后续 Android 构建必须使用：

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
# 在移植工作树的 android 目录执行。
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -PheadwearCatalogDir=D:/CodexData/headwear-audit/all-characters/catalog-bundled
```

构建后重新核对 APK 头饰清单/834 资源、ARM64 库和哈希。设备安装成功后执行 `:app:connectedDebugAndroidTest` 并记录具体测试及结果，再进行游戏进程验收。此前 `:app:lintDebug` 没有取得有效诊断结果，仍需在可用工具环境闭合，不能算通过。

### S0 本轮推进（2026-10-10）

仅推进 F16 / S0，不修改产品行为。唯一门禁入口及详细合同见 [Android 移植宿主门禁](../tools/AndroidPort/README.md)。独立入口和以下 Gradle 命令均实际通过：

```powershell
# 在本移植工作树 android 目录执行。
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:GRADLE_USER_HOME = 'F:/CodexData/gradle-user-home'
./gradlew.bat :app:verifyPortHost -PportHostPython=D:/CodexData/better-endfield-bem-env-20260926/Scripts/python.exe --offline --console=plain --no-daemon
```

- 五组 Java 本轮重新编译执行，共 74 项检查（15 / 27 / 15 / 10 / 7）；四组 Python 共 26 项测试。临时 class 与夹具退出后释放。
- 隔离副本注入缺失测试、Java 断言失败、Python 测试失败，均以退出码 1 阻断，未留下旧编译输出，也未打印整体 PASS。
- Gradle `check` / `test*UnitTest` 接入同一门禁；CI 宿主 job 与 APK 前置依赖已接线，远端 job 尚未执行。AGP `NO-SOURCE` 本身仍不构成测试证据。
- 既有 `assets.srcDir` 和本机 `ndk.dir` 弃用警告记录为本轮范围外工具链债务；不计 lint 通过。
- 本轮未构建 APK、未执行设备验收；没有暂存、提交、合并或推送，保留此前全部未提交内容。下一轮进入 S1 / F05。

## 本轮范围与交付边界

后续 Debug 编译请求已于 2026-10-10 执行：宿主门禁、Debug APK、测试 APK 增量构建成功；主包重新核对 834 个头饰资源、清单与 ARM64 库，双方签名验证通过。主 APK SHA-256 仍为 `3F211815B21B851ABA9A500E854CC8AD09C7DBB554D56B3F7EE0D16BCDF007FE`，符合 S0 不改变产品代码的范围。产物与测试方法见 [S0 Debug 构建与测试](ANDROID_DEBUG_TEST_20261010.md)。没有新增设备验收，仍不推进 S1。

随后用户选定 PJX110 实测：两份 APK 覆盖安装成功；镜像测试首次因测试代码调用设备不存在的 `Files.readString` 失败。已保留断言并修正为字节读取加 UTF-8 解码，重编译测试包成功。指定设备重新连接后安装修正测试包成功，镜像持久化、损坏索引恢复为空列表、无框架预览关闭三项定向测试均有本轮精确方法 PASS 日志和 instrumentation code -1。详细证据与当前测试包哈希见上述测试说明。仅计这三项设备自动测试通过，不改变 S1 未开始或其他完整设备/游戏功能状态。

- 最初审计只整理规划和证据；后续本轮仅完成 S0 本地门禁与 CI 接线，不修复 F05 的 P1、不开放热切换、不接异步队列、不修改资源快照。
- 本轮更新规划、状态摘要、计划导航和文档索引，不提交、合并或推送。
- 后续修改只 stage 本阶段相关源、测试和文档；禁止吸入 `.workbuddy/`、构建输出或其他无关未提交内容。
- 每完成一阶段，更新本表状态、真实验收结果及 Git 交付状态；没有运行的门禁继续标未验。
