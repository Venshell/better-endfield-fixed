# Better Endfield Next for Android

[简体中文](README.md) · [English](README.en.md)

Better Endfield 是面向《明日方舟：终末地》Android 端的 LSPosed 模块，提供设置应用与游戏内悬浮面板。角色外观、语音、相机和 MMD 等能力由独立模块提供。当前项目只开发和发行 Android 端；仓库中的 Windows 代码与构建脚本属于历史遗留，不是当前产品目标或验收范围。完整边界见[产品边界](docs/PRODUCT_BOUNDARY.md)，功能状态以[更新日志](CHANGELOG.md)与发行说明为准。

> 本仓库独立维护，派生自 [Dr-hydra/Better-Endfield](https://github.com/Dr-hydra/Better-Endfield)，并非原项目或游戏发行商的官方版本。

[![Android Release 4.2.0](https://img.shields.io/badge/Android%20Release-4.2.0-26CDCB)](https://github.com/Venshell/better-endfield-fixed/releases/latest)
[![Experimental](https://img.shields.io/badge/Experimental-glide%20%2B%20link-E85327)](https://github.com/Venshell/better-endfield-fixed/tree/experimental/destructive)
[![Changelog](https://img.shields.io/badge/Docs-Changelog-1C4A9C)](CHANGELOG.md)
[![Android Guide](https://img.shields.io/badge/Android-Guide-1C4A9C)](android/README.md)
[![BEM Creator Guide](https://img.shields.io/badge/BEM-Creator%20Guide-1C4A9C)](docs/custom_model/BEM_CREATOR_GUIDE.md)

当前 Android 版本为 **4.2.0**（`dev.betterendfield.next`）。Next 与 3.x 独立安装，安装后需重新启用模块和设置作用域。内置 898 份头饰资源面向原生 Android 游戏 1.6.4（versionCode 61）。需要缓降或联机时，使用实验性分支 [`experimental/destructive`](https://github.com/Venshell/better-endfield-fixed/tree/experimental/destructive)。

## 与上游 Next 的差异

本仓库以 Android ARM64 为发行目标。以下是相对 [上游项目](https://github.com/Dr-hydra/Better-Endfield) 的本仓特有能力和分支安排。

- **第一人称陀螺仪**：第一人称视角可使用手机陀螺仪控制方向，相关开关和灵敏度在 Android 设置应用中。
- **内置头饰资源**：APK 随包提供 898 份 v3/v61 头饰资源，供第一人称隐藏或替换遮挡视野的网格。资源对应游戏 versionCode 61，逐角色效果以实机验收为准。
- **实验性分支**：缓降和联机在 [`experimental/destructive`](https://github.com/Venshell/better-endfield-fixed/tree/experimental/destructive)，实验 APK 从 [该分支的 Actions](https://github.com/Venshell/better-endfield-fixed/actions/workflows/android-build.yml?query=branch%3Aexperimental%2Fdestructive) 下载。正式版 `main` 不含这两项，并保留原有持续冲刺。

Android 设置应用和游戏内悬浮面板沿用本仓库的 Compose 入口，保留第一人称、BEM 管理、MMD 导入和会客室媒体屏操作。具体入口见 [Android 使用说明](android/README.md)，版本变更见 [更新日志](CHANGELOG.md)。

## 功能一览

功能表列出 Android 端的入口和用途。前四行是正式版 `main`；缓降和联机只在实验性分支。实机验收范围见对应发行说明。

| 功能 | Android ARM64 |
| --- | --- |
| 角色模型与外观 | BEM 导入和管理、外观与形态参数、管理员武器外观替换、游戏内模型和动效控制 |
| 相机 | 第一人称与陀螺仪、自由相机、全局 FOV、人物跟随、时间冻结、运镜 |
| MMD 与会客室媒体屏 | 作品导入与槽位确认、游戏内播放、会客室图片和视频投屏 |
| 游戏设置 | 首页游戏管理与启用功能一览、游戏内悬浮面板、界面调整、角色配音与持续冲刺 |
| 缓降 | 不在正式版。空中再次按跳跃展开或收起。见实验性分支 [`experimental/destructive`](https://github.com/Venshell/better-endfield-fixed/tree/experimental/destructive) |
| 联机 | 不在正式版。局域网与互联网房间。见实验性分支 [`experimental/destructive`](https://github.com/Venshell/better-endfield-fixed/tree/experimental/destructive) |

`.bem` 外观包使用标准 BEMv1 格式；具体能力边界与制作流程见 [BEM 创作者指南](docs/custom_model/BEM_CREATOR_GUIDE.md)。

## 开始使用

1. 从 [正式发行页](https://github.com/Venshell/better-endfield-fixed/releases/latest)取得 APK，核对发行说明中的适用版本与校验值。
2. 安装 APK，在 LSPosed 中启用模块并勾选实际游玩的 Endfield 包名。
3. 在设置应用中配置功能，彻底停止游戏后重新启动。首次启用某些模块需要冷启动。

详细步骤、兼容范围和排障入口见 [Android 快速开始](android/README.md)。当前源码分支的内置头饰资源面向原生 Android 游戏 versionCode 61；历史发行包的兼容范围以对应发行说明为准，其他客户端版本需要单独验证。

## 从源码构建

Android 构建需要 JDK 21、Android SDK 37、NDK 27.2.12479018、CMake 3.22.1、Dobby v1.0.5 与完整头饰目录。请按 [Android 构建说明](android/README.md#从源码构建)准备输入。完整目录可从 [Android 头饰资源 v3 / v61](https://github.com/Venshell/better-endfield-fixed/releases/tag/headwear-v3-v61) 获取，缺少目录时构建会失败。

## 文档

| 文档 | 内容 |
| --- | --- |
| [Android 快速开始](android/README.md) | 安装、作用域、构建与排障 |
| [技术实现与配置参考](docs/TECHNICAL_DETAILS.md) | 模块边界、加载方式、配置、日志与构建细节 |
| [文档导航](docs/README.md) | 当前专题文档和历史记录的入口 |
| [更新日志](CHANGELOG.md) | 版本变更与验收边界 |
| [BEM 创作者指南](docs/custom_model/BEM_CREATOR_GUIDE.md) | 外观包制作与验证 |
| [下载站](web/mod-center/README.md) | QQ 邮箱注册登录、境内网络限制与数据库备份 |

遇到问题时可在 [Issues](https://github.com/Venshell/better-endfield-fixed/issues) 描述平台、游戏版本、Better Endfield 版本和复现步骤。Android 端可从设置应用的“运行日志”页导出日志；日志中若有个人信息，请先检查再上传。

## 来源与许可

本项目以 [AGPL-3.0-only](LICENSE) 发布。上游与第三方组件的归属见 [第三方声明](THIRD_PARTY_NOTICES.md)。本项目与游戏发行商无关；使用前请备份配置，并留意游戏更新与其他扩展之间的兼容性。
