# Android 上游功能对账（2026-10-08）

原始逐文件底账对照 `upstream/main` = `b67a2f4`（v3.5.3）与本工作树已提交基线 `55f3e16`；2026-10-10 重新获取的上游 `main` 为 `c2f8eafc`，`a6562eb5` 的 Android ZIP/BEM 入口和随后新增的包装 NPC 模型均在下文单列。产品边界为 Android ARM64。状态按**用户或工具行为**划分，不能从同名文件有无直接推断。原始逐文件底账位于 `D:/CodexData/bem-upstream-diff-20261008/`（3,697 行；Android/原生/工具/资源候选 540 行），其文件计数不能代表最新上游的全量差异。当前执行顺序以 [Android 页面功能移植计划](ANDROID_UPSTREAM_PORT_PLAN_20261008.md) 为准。

| v3.5.4 draft 新增功能域 | 我方状态 | 上游证据与下一步 |
| --- | --- | --- |
| 热切换失败重绑与资源状态恢复 | 我方已有旧版运行链，新增稳定性合同未有 | `ad33357` 修改 `model_job_runtime.inc`、`module.cpp`；阶段 3 在真实快速切换、失败及卸载场景核对原资源恢复。 |
| Android 纹理预算、流式加载诊断及 LOD 观察 | 策略、运行时和只读诊断已接线，设备效果未验 | 预算协调、纹理流式指标与有界 mip 采样已定向接入；LOD offset 观察通过可选原生入口转发原写入。宿主测试与 ARM64 调试构建通过，仍须设备日志、压力、外部写入、画质切换和停用恢复验收。诊断值不是 GPU 驻留证明。 |
| 设置断连恢复、模块包替换与模型目录状态 | 我方已有定向接线，设备待验 | 包替换接收器至多等待 8 秒；前台模型/FOV 读取发生授权或传输失败时，策略至多每 30 秒唤醒一次模块进程，并由单 worker 最多重读 4 次。模型目录状态已接入 Compose 页：失败保留上次列表但清除可写修订，旧回调不得覆盖新请求，真实空列表单独显示。APK 与测试 APK 构建通过；设备安装遇到断连，测试未执行。 |
| 3.5.4 发行资产 | 我方不同发行合同 | `63c4c76` 为上游发布草稿；本仓 Android 签名、版本及资产目录独立管理，不直接复制。 |

## 状态定义

- **我方未有**：上游存在可识别的行为链，本地无对应实现。
- **我方已有旧版**：本地有同一功能入口，但缺上游后续能力或数据。
- **我方已有不同实现**：同一目标用不同架构和接口实现；不以文件差异判欠账。
- **我方已有类似实现**：覆盖核心用途，触发条件、边界或失败语义不同，需按需求决定是否补齐。
- **我方扩展/已对齐**：本地满足该功能或多出能力；仍单列设备证据。
- **不适用**：超出 Android ARM64 产品边界。**待证实**：静态证据不足以给出功能等价结论。

## 一、模型、BEM 与第三方模块

| 功能 | 分类 | 上游与本地证据 | 差异及处理 |
| --- | --- | --- | --- |
| BEM 1.4 多资源包格式 | 我方已有类似实现 | 上游 `native/modules/custom_model/bem.cpp`、`tools/CustomModel/bem_v14.py`；本工作树同职责文件、`bem_targets.inc`、`BetterEndfield.BemV14Tests` | 格式解析、建包和测试已接入；不把它等同于运行时可用。 |
| BEM 1.4 资源感知加载、静态网格、武器/形态目标 | 我方已有初步源码接线，设备行为待证实 | Android 共享入口使用资源级注册表；静态 `MeshRenderer`/`MeshFilter` 的精确路径、构造、提交和恢复已接入；安装器逐资源验包。独立观察入口仍拒绝 1.4 | 合成夹具及 Android 构建已通过；真实包换模、热切换、卸载和失败恢复仍须游戏进程/实机验收。 |
| 模型低峰值作业、内容同一性、活体缓存 | 我方已有 CPU 调度内核、生产路径逐张纹理解码和可选渲染同步，运行链仍同步 | 上游 `async_loading.h` 已定向接入本地原生层，独立调度/预算/纹理流式测试通过；生产 `AcquirePayload` 已改为延迟纹理载荷并在缓存复用前核对文件代际；纹理上传加入 4 MiB / fast 128 MiB 阈值的可选 Unity 同步；`model_content_identity.h`、`model_asset_cache.inc`、`model_job_runtime.inc` 仍无，`resource.finish` 仍同步载入与上传 | 完整绑定夹具覆盖延迟纹理及同步阈值，80 项调度检查与 Android 编译通过；尚不能声称游戏加载降峰值。后续将异步订阅、真实帧预算和资源提交接入本地静态/蒙皮绑定链，再用设备日志与峰值证据验收。 |
| 包装 NPC 旧式 BEM 接收根 | 我方已有同步 Android 入口，异步交付未有 | 上游 `c2f8eafc` 的 `model_receiver_root.inc`；本地 `module.cpp` 的 Android `ProcessResource` 现按唯一同名子根与 `Mesh_all` 解析，保留外层 prefab 交付 | 双 APK 编译通过；合同缺失或结构歧义退回原根，显式路径不变。仍需目标 NPC 游戏进程验收、关闭恢复验收，并在异步作业链接入时复用此解析。 |
| 模型热切换与快速加载 | 我方已有旧版配置、完成记录选择身份及只读旧代实例扫描，运行时未有 | 本地 `mod_registry.cpp` 解析开关，模块现明确拒绝 `hot_switch=true`；完成记录及 Android UI donor 复用已按 `selection_key` 区分；已登记模板根和旧代生成网格场景克隆可有界识别但未接线；上游 `BemInstalledResources.java` 发布开关、`BemHotSwitchUpdater.java` 轮询，`module.cpp` 消费 | 已补 adapter 持有、原始状态弱引用与原网格 pin；Android 候选事务已实现过期索引拒绝和失败重试，但未接入投递。首次启用实例发现、原生世代安装、失败保持旧绑定的重绑与清理未闭合；不能称功能可用。 |
| 通用模型匹配和 Android LOD | 我方已有不同实现，行为与设备效果待核 | 本地 `generic_model_matcher.h` 已有资源根路径、显式接收者和有界 Android LOD1 counterpart 合同，`module.cpp` 有 Android `LodState` 与 LOD 偏置；上游将部分接线分到 `generic_model_matcher.inc`、`android_lod_relations.generated.h`、`model_lod_state.inc` | 同名文件缺失不能判定功能缺失；需以相同 BEM/接收者夹具和设备 LOD 行为对照。上游生成的关系表带特定资源快照，未取得对应本地输入前不复制。 |
| 世界资源绑定与 BEM 安装 | 我方已有不同实现，1.4 资源冲突合同已补 | 双方均有 `world_resource_adapter.inc`、`BemInstaller.java`，本地保留 Compose 安装页；Android 安装器已逐资源验包，包索引、设置页及悬浮窗按包 ID/Android 资源键处理冲突 | 合成包及索引测试已加入；设备安装器与真实游戏内恢复仍待验，不整文件覆盖上游页面。 |
| 第三方模块导入与运行时更新 | 我方已有不同实现 | 上游 `ThirdPartyRuntimeUpdater.java`；本地未提交的 `ThirdPartyModuleStore.java`、`ThirdPartyRuntimeMaterializer.java`、`XposedEntry.java`、`third_party_host.cpp` 使用发布 ID 与回执 | 本地已通过构建和宿主夹具；仍需 LSPosed/游戏进程设备验收。缺上游同名类不等于缺更新功能。 |
| 创作者定向建包、Blender 导出、上传报告 | Android 资源目标草稿与上传量报告已接入；上游 Blender 插件不适用当前 1.4 Android 目标 | `build_bem14_target.py` 消费明确的资源路径、LOD 和同版本原生元数据，输出未验 profile 与 keep-only 项目；`bem_upload_report.py` 从有效包元数据估算所选字节量，不声称 GPU 驻留。共 5 项宿主测试通过。上游 Blender 插件只生成旧版固定外观 `windows-x64` 目标、限 16/12/12 蒙皮布局；不能当作 Android 1.4 导出能力。 |
| 独立 BEM Tools 打包 | 我方已有旧版且打包输入失配；属于独立 Windows 作者工具线 | 本地 `package_toolchain.py` 引用不存在的 `blender_addon`，还依赖本机尚无的 `artifacts/bem-archive-backend/7zip`；`scripts/BuildBemTools.ps1` 调用它 | 当前 Android ARM64 主线不以补旧版 Windows 插件来伪修打包；若单独交付 Windows 作者工具，须先核对格式能力、后端来源与产物。 |

## 二、Android 页面、配置、相机与媒体

| 功能 | 分类 | 上游与本地证据 | 差异及处理 |
| --- | --- | --- | --- |
| 主导航、BEM/模块管理页面 | 我方已有不同实现 | 上游 Java Activity/View/XML；本地 `MainActivity.kt`、`BemInstallScreen.kt`、`ThirdPartyModulesActivity.kt` 等 Compose 页面 | 按交互功能核对，不复活旧 View/XML。 |
| 模型管理悬浮窗 | 我方已有不同实现 | 上游 `OverlaySettingsPage.java` 等；本地 `OverlayModelPage.kt`、`GameOverlay.java` | 本地通过 provider 读写模型和 FOV；设备行为仍应单验。 |
| 跨进程设置写入和授权 | 我方已有不同/扩展实现 | 本地 `OverlaySettingsProvider.java`、`OverlaySettingsClient.java`、`OverlayModelPage.kt`；上游也有同职责通道 | 核对身份、写入范围和失败返回；不能按页面类名判断缺失。 |
| 全局 FOV 热下发 | 我方已有类似实现 | 上游 `GlobalFovUpdater.java` 定时读偏好并下发；本地 `ModuleSettings.applyGlobalFov`、`OverlaySettingsProvider`、`ModuleCommandRouter` 与原生 `global_fov` 命令 | 本地设置变更可事件驱动热下发；上游的外部偏好轮询语义不同。需确定是否要求外部写入自动发现，并实机验收。 |
| PC 布局与鼠标捕获 | 我方已对齐/扩展 | 本地 `PcUiMouseBridge.java`、`XposedEntry.java`、`ModuleSettings.java`；历史真机记录见 `ANDROID_DELTA_VS_UPSTREAM_3510FA7.md` §11 | 已有代码与既往设备证据；新版本仍需回归。 |
| 第一人称、陀螺仪、热键 | 我方扩展 | 本地 `FirstPersonPage.kt`、`GyroscopeController.java`、`ModuleSettings.java`、`Hotkeys.java`，上游没有同规模配置 | 保留本地 owner 与行为，不能反向覆盖。 |
| MMD 作品导入、安装、移除和播放页面 | 我方已有不同实现 | 上游 `MmdInstaller.java`、`MmdLibraryActivity.java` 等；本地 `MmdLibraryInstaller.java`、`MmdSlotFiles.java`、`MmdPage.kt` | 资源世代、归档预算、失败回滚需逐行为核对；页面结构差异不是缺功能。 |
| MMD 音频与动作/相机同步 | 我方已有实现，设备待证实 | 本地 `MmdAudio.java`、`MmdLibraryInstaller.java`、`MmdPage.kt`、原生 MMD/EIEM；上游有相应播放链 | 配置和导入不能证明游戏内音画同步，本轮不宣称设备通过。 |
| 框架服务等待/取消策略 | 我方已有定向实现，设备待证实 | 已接入有界 `FrameworkServiceWait`，连接回调唤醒，BEM 发布/清理/本地副本恢复与第三方 ZIP 发布会等服务；BEM 安装等待复用取消检查 | 宿主时序测试覆盖已连接、超时、延迟连接、虚假唤醒、取消和中断；设备侧服务晚连接与超时提示仍待验。包替换广播已作为独立入口接入。 |
| 可选动作 hook 与链式调用 | 我方已对齐/部分扩展 | 本地 `native/modules/actions/module.cpp` 已注册 `_TickStatePerformInterrupt` 且有不可用降级；`HookChain.h` 已存在 | 上游 Windows `hook_diagnostics.*` 不自动进入 Android；动作实际回归另验。 |
| 悬浮窗透明度、边缘吸附和模型关闭 | 我方已有不同/扩展实现 | 本地 `GameOverlay.java` 有 `autoSnap`/`snapToEdge`，Compose `OverlayPanel.kt` 提供控制 | 上游 View 入口差异不计缺功能；保留设备侧交互验收。 |
| 本地化设置与模型/模块页面 | 我方已有不同快照 | 本地 `res/values`、`values-en`、`values-ja`、`values-ko`、`values-zh-rTW` 均存在；上游同语种但资源键及页面结构不同 | 逐键及用户可见文案核对；旧文档所述“缺四套 values-*”已失效。 |

## 三、资源、工具与平台边界

上游 `a6562eb5` 新增 ZIP 内多个 BEM 的选择导入与系统 VIEW/SEND 入口。本地原为单包 BEM 导入，现已实现归档有界解包、逐包验包与独立发布、部分失败报告及 Compose 选择页；宿主归档测试和 Android 构建通过，设备交易与界面尚未验收。上游 Better Endfield Next 的包名/安装身份变更属于不同发行边界，不能覆盖本地签名、包名或特有功能。

文件管理器请求现按上游独立入口合同处理：只接受一个 `content` URI，拒绝多个不同 URI、批量或文本分享；相应 Android instrumentation 测试已编译但未在设备执行。

上游导入页还提供实验性「跳过校验」开关，本地尚未提供。当前上游原生路径在该模式下将 choices、rules 与预算上限放宽到 `SIZE_MAX`/`UINT64_MAX`；本地 Agent 宪法要求外部输入有界，不能直接搬入 Android。若后续确有兼容包需求，须先给出仍有上界的解析合同与失败隔离，再决定是否暴露该开关。本地严格验包路径继续保留。

| 功能 | 分类 | 上游与本地证据 | 差异及处理 |
| --- | --- | --- | --- |
| 角色/语音资源快照 | 我方已有旧版 | `android/resources/character-presets.json` 本地 32 角色、上游 33，新增 `chr_0038_purrche`；`voice-catalog-index.json` 本地 132 catalogs/7 packages，上游 136/4 且含 `provenance` | 生成器需要当前游戏目录与 `research/current-inputs`；本工作树和 `F:/bem` 均缺输入，不能直接复制上游 JSON。取得同版本输入后重新生成并检查 manifest/覆盖率。 |
| 资源来源与生成证据 | 我方未有部分报告 | 上游 `android/resources/manifests/model/action-manifest.json`、`manifests/voice/voice-event-media-manifest.json`、`resource-source.json`、`voice-index-android-evidence.json` 本地无 | 确认本地生成链与实际资源，再决定哪些报告属于当前内部真源。 |
| 模型/模块下载站与赞助、社区入口 | 我方未有或不同产品决定 | 上游 Android/网页入口与 `web/` mod-center；本地 Compose 页面无同一入口 | 属产品决策，不把裸地址或外部供应链自动带入 Android。 |
| Android 模型存储清理 | 我方已有类似实现 | 本地 `BemInstaller.java` 安装后处理旧代际清理并报告失败；上游另有清理流程 | 需按所有存储入口、孤儿文件和失败恢复比较，不能仅凭一个清理调用判完全对齐。 |
| Android 发行与签名 | 我方已有不同实现 | 本地 `.github/workflows/android-*.yml` 与自己的签名身份；上游 3.5.x 发布脚本及证书 pin 属其发行链 | 保留本仓签名和版本规则，上游证书不可复制；发布验收独立进行。 |
| 上游独立 BEM Tools GUI、Steam CN、XInput、Windows 模型悬浮窗和桌面发布 | 不适用 Android 主线 | 上游 `BemTools`、Steam/XInput、Windows overlay/installer 及发布脚本；本地产品边界为 Android ARM64 | 记录差异，不列 Android 待移植功能。 |
| 上游文档、研究目录与历史样本 | 非功能差异 | 完整逐文件底账中 `research/` 2,751、`docs/` 267 个差异路径 | 保留来源追溯；不按文件数计算功能完成度。 |

## 验收与结论

本轮对账以文件、调用链和已有测试为静态证据。上一轮本工作树的 Android 调试/测试 APK 构建成功，包内 834 个 `.behw` 与一份 manifest；BEM 1.4 格式测试、CustomModel Python 测试及第三方更新宿主夹具通过。**这些不构成 BEM 1.4 运行时、模型热切换、MMD 音画同步或第三方模块游戏进程验收。**

按当前证据，优先级最高的真实 Android 缺口是模型/BEM 运行时与热切换；资源快照和作者工具属于另两条独立交付线。其余“类似/不同实现”项须以功能合同及设备行为决定是否继续移植，不按上游文件名强行对齐。
