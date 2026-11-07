# 2026-11-07 上游合并与验收记录

## 范围与合同

本轮将 `Dr-hydra/Better-Endfield` 的 `main`（`ee441da967ae2642a91fbddd6b51f8e46739a8df`）合入本地 `codex/android-thirdparty-alignment`，合并前本地 HEAD 为 `34ee95b2`。上游已拉取，原始自动合并报告 273 个冲突文件；本地和上游分别有 108、205 个独有提交。

本分支仍以 [Android 产品边界](PRODUCT_BOUNDARY.md)为准。接入上游修复和共享格式演进，不把上游 Windows 发行范围或第一人称退役决定自动套用于本分支。上游版本号和发布记录也不代表本分支已发行或已通过设备验收。

## Owner 与处理原则

- Android 页面沿用本地 Compose owner；上游同名 Java 页面不得与 Compose 入口并存。包、JNI 与资源标识跟随 Next 命名时，必须同时迁移所有实际调用与门禁。
- 第一人称、陀螺仪、头饰资源、第三方模块入口保留本地有效功能与安全合同；上游退休快照只作来源记录，不成为活跃运行时。
- 安装索引条件提交与导入边界由纯 Java owner 管理，页面消费结构化结果；保留本地宿主门禁，不因合并删去有效断言。
- BEM 解析、纹理预算、异步加载和 LOD 关系采用共享原生 owner；对已演进的生成合同迁移测试，不维护含义冲突的平行 API。
- 创作者工具与 runtime 使用同一现行格式；生成头文件、扫描清单和历史归档保留生成器/来源，不手工拼接数据。
- 文档采用上游模块目录，入口保留本分支产品合同和验收状态。上游更新日志单独保存在 [上游历史记录](archive/UPSTREAM_CHANGELOG_20261107.md)。

## 验收边界

完成合并需要：冲突索引清空、活跃源码无冲突标记、针对性宿主与原生测试通过、Android 构建通过，并检查包入口、头饰目录及签名。若某一项因环境或既有故障无法执行，必须记录具体原因与影响，不得将已通过的其他检查代替该项。

最初的合并验收未安装设备，也未发布或推送。后续用户连接 PJX110 并要求安装 Next Debug 包，已在同一设备安装；应用页可启动不证明游戏内第一人称、模型切换、MMD 或长期运行稳定性。历史实机结论只保留原时点和范围。

## 已发现的既有检查问题

`scripts/migrate_documents.py check` 在检查开始前报 `Reviewed plan is incomplete or has a target collision`。该文件与 `upstream/main` 完全一致，脚本固定要求 126 项迁移计划，属于上游既有文档迁移工具故障。本轮不改写既有迁移历史或绕过其校验；另行核对本轮迁移目标与引用。

## 最新头饰资源与导入合同

用户在本轮明确选择最新资源，使用 `D:/codexdata/bem-headwear-v61-20261017/catalog` 的 898 份 v3 fixture，目标游戏 versionCode 61。`coverage.json` SHA-256 为 `7e4d833ded5b2d96ef0d2408aa62181dc39ebf7d104f7541bff89edf005ec0c6`，原始 fixture 总计 83,308,869 字节；清单自身 `runtime_verified=false`，不得据此声称新资源已逐角色实机验证。

打包 owner 先核对固定 coverage 指纹、文件数、名称、逐文件 SHA-256、96 MiB 总量和已接受的边界 profile，再输出 `BEHWASSETS` versionCode 61 清单。Java materializer 同步更新游戏版本与最大条目数；原子部署、缓存损坏修复、失败保留原代际和路径保护仍保留。

本轮生成的本地交付归档为 `D:/codexdata/bem-merge-20261107/headwear-catalog-v3-898-v61.zip`。导入工具支持 `--archive <zip>` 或 `HEADWEAR_CATALOG_URL` / `--url <https-url>`，所有输入均须经过同一个打包 owner 校验。此前公开的 834 份归档不再作为本轮默认来源。尚未上传最新归档；CI 在仓库变量 `HEADWEAR_CATALOG_URL` 配置可访问地址后才能取得资源，未配置时明确失败，不回退旧资源。

针对性实测：头饰打包/导入边界 5 项测试通过；新 ZIP 导入并逐文件核验通过；Java 存储 owner 重新编译并通过 33 项检查（含符号链接拒绝）。完整 Debug APK 已构建，包内有 899 条头饰路径（898 份资源及 1 份清单）；`aapt` 核对包名 `dev.betterendfield.next`、版本 `4.1.8`、入口 `MainActivity`，`apksigner verify` 通过，包含两份 ARM64 原生库。该签名仅代表本地 Debug 构建。

## 接口迁移准则

用户允许把本地入口迁移到上游现行接口。等价能力优先复用新 owner，避免仅为维持旧名称保存双调用链；若替代方案转为独立 E Mod Loader 安装或依赖额外模块，则先核对 Android 能力、配置和 Hook 生命周期，不能仅凭上游删除记录就移除本地可用功能。

本轮 Java 编译暴露旧 View MMD 页面仍调用已退役的散列设置接口；当前 Compose `MmdPage` 已通过 `SettingsState` 使用 `MmdSettings` 和 `MmdLibraryInstaller`，故移除没有调用入口的旧 MMD 页面及旧安装器，保留当前作品导入链。同样移除被 Compose 增强页取代且没有调用入口的旧 View 增强页；当前相机跟随开关仍由 `FreeCameraFollowCharacter` 负责。跨进程资源读取接到已有 `read_resource` Provider 合同。新增上游会客室媒体屏后，将其入口接到 Compose 工具页；静态导航夹具通过 20 项连线检查，不能替代设备交互验收。

## Android UI 声明与 Compose 续接

用户检查安装后的界面后指出 Next 功能遗漏。对照上游 `MainActivity.java`、`AboutPage.java`、`EnhancementSettingsActivity.java` 和媒体页，原有 Compose 已覆盖模型、语音、外观、界面、相机、冲刺、MMD 与悬浮面板，但缺少关于页的语言、更新、资源管理、项目与社区动作，以及创意工坊入口。现由 Compose 关于页调用既有 `AppLanguage`、`AppUpdateChecker`、`DistributionUpdater` 和 `GameResourceStore`；启动自动检查仍读取原配置 key。资源 ZIP 解包增加 2 GiB 总展开量上界。媒体屏从旧 View Activity 换为 Compose Activity，继续使用 `media_screen_settings`、`media_screen_configuration` 与 `media_screen_files`，文件导入仍限制 256 项、图片 32 MiB、MP4 512 MiB。

PJX110 首次截图显示媒体页继承系统标题栏，并在页面内重复返回和标题，导致内容下移。媒体 Activity 现使用 `AppTheme`、与主设置页相同的系统栏处理和返回行，功能开关并入标题卡。重装 Debug 包后实机截图确认标题栏消失；下拉选单、滚动到页面底部和保存按钮均可见。该检查没有导入真实图片或视频，也没有启动游戏验证投屏画面。当前截图保存在本机 `D:/codexdata/bem-merge-20261107/media-after-fix-stable.png` 与 `media-bottom.png`，不作为仓库生成物提交。

本次续接实际运行 `:app:assembleDebug`、`enhancement_navigation_fixture.py`（20 项接线）、`scripts.tests.test_next_identity` 与 `test_distribution_index`（共 7 项），以及 `run_host_gate.py`（118 项 Java 检查、29 项 Python 测试）；均通过。APK 用 `adb install -r -d` 更新到 PJX110，媒体页进入、下拉、滚动、保存成功提示和进程存活已在设备上观察。资源下载、外部链接、真实媒体导入及游戏内输出未做设备验收，不能由本次页面截图推断通过。

## 宿主门禁与公开 ABI

统一移植门禁实跑：6 组 Java 套件共 118 项检查，4 组 Python 套件共 29 项测试通过。上游安装器新增 JSON 和文本翻译依赖，门禁现在显式接收 `--json-jar` / `BEM_PORT_JSON_JAR`，固定 JSON-java 20240303 SHA-256，并运行真实 JSON 和 Text 实现。测试侧仅提供中文语言 adapter fixture，不替代真实解析器，也不证明 Android 语言配置。CI 显式准备该固定依赖。

Next 是产品/内置模块身份，不改第三方公开 V1 导出名 `BetterEndfield_GetThirdPartyModuleV1`。已用宿主编译与真实第三方动态库加载夹具核对，避免无意义地打断既有 format1/ABI1 包。

独立 E Mod Loader 的 [兼容性说明](https://github.com/Dr-hydra/Endfield-Mod-Loader/blob/main/docs/COMPATIBILITY.md)、[SDK](https://github.com/Dr-hydra/Endfield-Mod-Loader/blob/main/docs/SDK.md)和[第一人称示例](https://github.com/Dr-hydra/Endfield-Mod-Loader/blob/main/examples/first-person/README.md)用于核对替代边界：其 Android 第一人称没有游戏内触控按钮，且实机行为未验收，不能直接替代本地陀螺仪/悬浮面板链路。本轮不安装或部署独立加载器。
