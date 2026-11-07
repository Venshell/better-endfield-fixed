# Better Endfield

[简体中文](README.md) · [English](README.en.md)

Better Endfield 是面向《明日方舟：终末地》Android 端的 LSPosed 模块，提供设置应用与游戏内悬浮面板。角色外观、语音、相机、音乐与战斗数据等能力由独立模块提供。当前项目只开发和发行 Android 端；仓库中的 Windows 代码与构建脚本属于历史遗留，不是当前产品目标或验收范围。完整边界见[产品边界](docs/PRODUCT_BOUNDARY.md)，功能状态以[更新日志](CHANGELOG.md)与发行说明为准。

> 本仓库独立维护，派生自 [Dr-hydra/Better-Endfield](https://github.com/Dr-hydra/Better-Endfield)，并非原项目或游戏发行商的官方版本。

当前开发分支正在整合上游 4.1.8，安装身份为 `dev.betterendfield.next`，使用 versionCode 61 的 898 份头饰资源；这不是下方历史 3.4.1 发行包。Next 与旧版独立安装，需重新启用模块和设置作用域。合并与验证范围见 [本轮记录](docs/ANDROID_UPSTREAM_MERGE_20261107.md)。

## 平台与功能

| 平台 | 主要能力 | 入口 |
| --- | --- | --- |
| Android ARM64 | 模型与 BEM 外观、角色配音、自由相机、第一人称、运镜、陀螺仪及游戏内悬浮面板 | LSPosed 模块与配套设置应用 |

`.bem` 外观包使用标准 BEMv1 格式；具体能力边界与制作流程见 [BEM 创作者指南](docs/custom_model/BEM_CREATOR_GUIDE.md)。

## 开始使用

1. 从 [Android 3.4.1 发行页](https://github.com/NukumizuKazuhiko/better-endfield-fixed/releases/tag/v3.4.1)取得 APK，核对发行说明中的适用版本与校验值。
2. 安装 APK，在 LSPosed 中启用模块并勾选实际游玩的 Endfield 包名。
3. 在设置应用中配置功能，彻底停止游戏后重新启动。首次启用某些模块需要冷启动。

详细步骤、兼容范围和排障入口见 [Android 快速开始](android/README.md)。当前源码分支的内置头饰资源面向原生 Android 游戏 versionCode 61；历史发行包的兼容范围以对应发行说明为准，其他客户端版本需要单独验证。

## 从源码构建

Android 构建需要 JDK 21、Android SDK 37、NDK 27.2.12479018、CMake 3.22.1、Dobby v1.0.5 与完整头饰目录。请按 [Android 构建说明](android/README.md#从源码构建)准备输入；缺少头饰目录时构建会失败。

## 文档

| 文档 | 内容 |
| --- | --- |
| [Android 快速开始](android/README.md) | 安装、作用域、构建与排障 |
| [技术实现与配置参考](docs/TECHNICAL_DETAILS.md) | 模块边界、加载方式、配置、日志与构建细节 |
| [文档导航](docs/README.md) | 当前专题文档和历史记录的入口 |
| [更新日志](CHANGELOG.md) | 版本变更与验收边界 |
| [BEM 创作者指南](docs/custom_model/BEM_CREATOR_GUIDE.md) | 外观包制作与验证 |

遇到问题时可在 [Issues](https://github.com/NukumizuKazuhiko/better-endfield-fixed/issues) 描述平台、游戏版本、Better Endfield 版本和复现步骤。Android 端可从设置应用的“运行日志”页导出日志；日志中若有个人信息，请先检查再上传。

## 来源与许可

本项目以 [AGPL-3.0-only](LICENSE) 发布。上游与第三方组件的归属见 [第三方声明](THIRD_PARTY_NOTICES.md)。本项目与游戏发行商无关；使用前请备份配置，并留意游戏更新与其他扩展之间的兼容性。
