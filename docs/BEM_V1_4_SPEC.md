# BEM 1.4 格式合同（格式阶段）

BEM 1.4 扩展 1.3 的容器、组合选项和形变，用显式资源表描述角色或武器的多个 prefab，以及蒙皮或静态 renderer。头部 `major=1, minor=4`；40 字节头、Payload 目录、压缩和资源预算沿用 1.3。完整字段校验以 `tools/CustomModel/bem_v14.py` 和 `native/modules/custom_model/bem_targets.inc` 为准。

**产品支持状态（2026-10-09）：**Python 产包、解析、原生资源定向解码、Android 共享运行入口和安装器逐资源验包已接线；独立的旧版观察入口仍拒绝 1.4。源码接线与合成夹具通过不表示能在 Android 游戏中稳定导入、启用或渲染；目前没有 1.4 游戏设备验收。

本补丁已从格式阶段进入**Android 源码接线阶段**：原生 1.4 测试登记到 `native/CMakeLists.txt`，资源级注册与平台筛选、显式资源 ID 加载、renderer 路径/LOD/类型匹配、静态 MeshFilter 提交与恢复，以及安装器逐资源检查均已接入。旧版无资源 ID 的 `ParseModRegistry` 和 Android 独立观察入口继续拒绝 1.4。真实资源的静态/蒙皮混合发布、设备热切换与恢复未验，仍是产品支持门禁。

## 版本和能力

- 1.4 必须有 `composable-options`、`multi-resource-targets`；任一目标组件或 mesh 为静态时，还须 `static-meshes`。贴图槽、形变等仍按原能力声明。
- `option_groups` 必须存在，可以为空；`appearances`、`default_appearance_id` 不适用。
- 1.0–1.3 头部拒绝 1.4 的能力和 target/component/mesh 字段。写入器按内容选择最低可表达版本，不能将新字段降格放入旧头。
- manifest 不超过 4 MiB，目录不超过 16384 项，组件总数不超过 64，文件不超过 2 GiB；既有选中状态预算继续适用。

## Target 和资源

1.4 的 `target` 使用 `kind`（`character` 或 `weapon`）、稳定 `id`、历史格式常量 `platform: "windows-x64"`、`profile_id`、`revision`、`snapshot`、`resources` 和 `components`。不再使用 `character_id`、`world_resource`、`ui_resource`。`platform` 是容器兼容常量；资源实际平台来自每条资源的 `platforms`。

`resources` 为 1–32 项。每项严格包含稳定 `id`、小写精确根名 `name`、小写 `assets/.../<name>.prefab` 的 `asset_path`、非空且不重复的 `platforms`（`windows-x64`、`android-arm64`）和 `lod`（0–3）。同平台下根名及路径各自不得重复。每个资源至少要有一个组件。资源冲突键为 `平台:根名`。

`components` 使用全包连续编号，且每项必须声明所属 `resource`、`renderer_kind`（`skinned` 或 `static`）和大小写敏感的根相对 `renderer_path`。同一资源内网格名及 renderer 路径分别唯一。已知的 `Mesh_all/lod0`–`lod3` 路径必须与资源 `lod` 一致。静态组件的骨骼名表为空；1.4 不允许旧的 `bone_name_aliases`。骨骼及材质 donor 只能来自同一资源；替换 mesh 的 renderer 类型必须与目标相同。全局组件 ID 仅在按资源读取时映射为局部 ID。

## Mesh

每个 1.4 mesh 必须声明 `renderer_kind`。蒙皮 mesh 保留三流与 4/12/32 字节蒙皮声明，骨骼表为 1–256 项。静态 mesh 允许 1–3 条连续非空流，不含语义 12/13 的蒙皮属性，`bones` 必须为空。索引、draw、材质、贴图和位置形变沿用既有验证。跨 prefab 共享字节可复用 Payload ID；mesh 描述符仍须各自引用本资源的 donor。

## 创作者及验收边界

`bem_tool.py` 的 build、pack、unpack、inspect、validate、bundle 可处理 1.4；inspect/validate 的 `--resource` 和 `--platform` 仅筛选选择计划，验证仍覆盖整个包。普通 EFMI convert 的旧角色目标不会自动升级成武器或大招目标。示例 `tools/CustomModel/examples/multi-resource/create_project.py` 是合成夹具，不是可安装的真实游戏 Mod。

格式门禁是 Python 1.0–1.4 回归、原生资源选择/非法包测试以及 1.1/1.3 原生回归。Android 源码接线后还须以游戏进程中的真实资源匹配、LOD、静态网格、热切换和失败恢复验证，才能声明产品支持 1.4。合成包只证明合同与安装路径，不证明游戏内资源存在或可渲染。

**移植接线进度（2026-10-09）：**`mod_registry` 的平台资源筛选与混装合同已由 Android 共享入口调用；原生夹具覆盖资源 ID、LOD、组件 ID、克隆名匹配、重复包、跨包根名冲突和 1.0/1.4 共存。旧注册表入口仍明确拒绝 1.4。阶段顺序及门禁见 [Android 上游定向移植计划](ANDROID_UPSTREAM_PORT_PLAN_20261008.md)。

共享加载侧已传入显式资源 ID 并核对静态/蒙皮类型；Android renderer 枚举、网格构造及提交/恢复已消费显式路径与 LOD。模拟 Unity 事务测试通过，安装器合成多资源转换测试通过。缺少真实游戏资源、进程日志、热切换和设备还原证据，仍不能宣布 1.4 产品支持完成。
空 `renderer_path` 是合法的资源根对象；纯匹配请求额外携带路径已验证状态，不能把“根路径为空”与“未提供验证路径”混为一谈。
