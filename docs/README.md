# 文档导航

这里区分当前使用说明、技术参考和历史记录。安装步骤从[项目首页](../README.md)或 [Android 快速开始](../android/README.md)进入；日期性试验记录不能替代当前版本验收。

## 当前文档

| 文档 | 用途 |
| --- | --- |
| [2026-11-09 Android 入口续接](ANDROID_UI_ALIGNMENT_20261109.md) | 管理员外观、BEM/MMD 操作、模型动效与媒体屏运行故障的实施合同和验收证据 |
| [2026-11-07 上游合并](ANDROID_UPSTREAM_MERGE_20261107.md) | 本轮合并合同、冲突取舍与实际验收证据；旧对账仍保留原时点范围 |
| [按模块浏览上游技术资料](INDEX.md) | 模块规范与来源导航，包含上游双端历史，不改变本分支产品边界 |
| [产品边界](PRODUCT_BOUNDARY.md) | Android 唯一目标平台及历史 Windows 代码的处理边界 |
| [EF Link 协议移植](EFLINK_PORT_PLAN.md) | 把 EF Link 局域网房间协议接到 Android 的清单、阶段和本轮已落地范围 |
| [技术实现与配置参考](TECHNICAL_DETAILS.md) | Android 实现与历史桌面实现细节 |
| [BEM 创作者指南](custom_model/BEM_CREATOR_GUIDE.md) | 外观包制作、转换和验证 |
| [Android 第三方模块创作者指南](THIRD_PARTY_MODULE_CREATOR_GUIDE.md) | 第三方 ZIP、Native ABI、网页桥及 Android 验证边界 |
| [BEM 格式规范 1.0–1.3](custom_model/BEM_FORMAT_SPEC.md) | 当前标准包字段、形变参数与版本合同 |
| [BEM 1.4 格式合同](BEM_V1_4_SPEC.md) | 多资源目标、静态网格与 Android 源码接线；游戏设备验收未完成 |
| [第一人称执行记录](CAMERA_FIRST_PERSON_EXECUTION.md) | 相机阶段门禁与验证边界 |
| [OmniMix 集成交接](music/OMNIMIX_INTEGRATION_HANDOFF.md) | 音乐后端协议与回退合同 |
| [项目更新日志](../CHANGELOG.md) | 版本变化与发布状态 |
| [Android 上游 3.4.4 移植记录](ANDROID_UPSTREAM_344_PORT_20261004.md) | 本分支 Android 功能范围与验收证据 |
| [Android 上游移植当前状态](ANDROID_UPSTREAM_STATUS_20261008.md) | 以 2026-10-10 获取的上游 `c2f8eafc` 为当前核查点，区分源码接线、构建与设备验收 |
| [Android 移植规划与完成状态](ANDROID_PORT_ROADMAP_20261010.md) | 审计后的当前执行总表：已完成部分、未完成能力、P1/P2、S0–S7 顺序与验收停止条件 |
| [Android 移植宿主门禁](../tools/AndroidPort/README.md) | F16 / S0 的唯一门禁入口、执行合同、真实检查数与失败注入证据 |
| [S0 Debug 构建与测试](ANDROID_DEBUG_TEST_20261010.md) | 2026-10-10 调试包、内容与签名核对、宿主门禁及手机测试方法 |
| [S1 安装索引条件提交](ANDROID_INDEX_CONDITIONAL_SAVE_20261011.md) | 修订 owner、草稿与确认合同；源码/构建证据及待执行设备门禁 |
| [Android 上游功能对账](ANDROID_UPSTREAM_FUNCTION_MATRIX_20261008.md) | 按功能区分未有、旧版、不同实现、类似实现、已对齐与不适用 |
| [Android 上游 2026-10-25 差距对账](ANDROID_UPSTREAM_DELTA_20261025.md) | 以 2026-10-25 fetch 的上游 `a617b158`（66 个新提交、v4.1.0）为核查点，分类上游新增能力与本地三条工作线的关系 |
| [Android 上游定向移植计划](ANDROID_UPSTREAM_PORT_PLAN_20261008.md) | 以 Android 页面功能为入口，按依赖与规模排序；下方保留模型主链技术阶段记录 |
| [Android 上游同步与移植准备](archive/ANDROID_UPSTREAM_SYNC_PREP_20261005.md) | 上游 3.5.0 差距清点、MMD/EIEM 移植合同与执行顺序（已归档，被路线图与 20261025 对账取代） |
| [Android 移植进度快照](archive/ANDROID_PORT_PROGRESS_20261005.md) | 按功能块三档（代码/构建/设备）记录当前进度（已归档，被路线图取代） |

## 历史记录

| 文档 | 用途 |
| --- | --- |
| [Android 开发记录归档](ANDROID_DEVELOPMENT_HISTORY.md) | 旧 Android README 中的逐版本日志和实现记录，保留原时点上下文 |
| [Android Compose 悬浮层实验](ANDROID_OVERLAY_COMPOSE_EXPERIMENT_20260929.md) | 实验过程与当时的验收证据 |
| [BEM 1.0 规范](custom_model/history/bem-1.0/BEM_V1_SPEC.md)、[BEM 1.1 扩展](custom_model/history/bem-1.1/BEM_V1_1_SPEC.md) | 分版本旧文档；当前完整格式以 1.0–1.3 合并规范为准 |

其他带日期的审计、探针和迁移文档记录其对应时点；阅读时应与当前源码及版本说明核对。

## 已归档（archive/）

2026-10-25 整理：以下 24 份已被后续文档明确取代或推翻的文档移入 `archive/`，不再作为当前依据：

- 被新审计取代：`ANDROID_CUSTOM_MODEL_STATIC_AUDIT` / `ANDROID_MESH_WRITE_METHOD_ENTRYPOINTS` / `ANDROID_REFACTOR_PLAN`（均被 ANDROID_MESH_SUBMISSION 取代）、`ANDROID_UPSTREAM_SYNC_PREP` 与 `ANDROID_PORT_PROGRESS`（被 ANDROID_PORT_ROADMAP_20261010 及 20261025 差距对账取代）、`UPSTREAM_SYNC_BACKLOG_20260930`（上游核查点已过时）、`UI_COMPOSE_MAP`（Compose 回退后仅存历史）。
- 被正式规范取代：`BEM_CREATOR_GUIDE_DRAFT`、`BEM_V1_DESIGN_DRAFT`、`BEM_COMPONENTN_AUTOMATION`、`BEM_EFMI_IDENTITIES`、`BEM_DDS_READING`、`BEM_PER_DRAW_COMPATIBILITY`、`BEM_RABBITFX_COMPATIBILITY`。
- 被后续实现/修正取代的早期 PoC 与样本记录：`CUSTOM_MODEL_POC1_C9_PLAN`、`CUSTOM_MODEL_POC21_RAW_CHANNEL`、`CUSTOM_MODEL_GILBERTA_*`（3 份）、`CUSTOM_MODEL_HASH_LOD_CONVERTER`、`CUSTOM_MODEL_RUNTIME_PROBE*`（2 份）、`CUSTOM_MODEL_RUNTIME_VALIDATION_ENDMIN`、`CUSTOM_MODEL_ZHUANGFANGYI_VALIDATION`。
- 计划已被绕开：`ANDROID_R8_MINIFY_PLAN`（第 6.5 节 Compose 记录另有 20260929 实验文档承载）。

仍留在 `docs/` 的带日期文档属于两种情况：仍是该域的最新结论（如 `CAMERA_UPSTREAM_SOURCE_AUDIT`、`ANDROID_UNPORTED_FEATURES`），或记录尚未闭合的实测证据（如 Gilberta Outfit B、`CUSTOM_MODEL_KNOWN_ISSUES`）。
