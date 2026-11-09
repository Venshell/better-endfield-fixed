# 2026-11-09 Android 功能入口续接

## 范围与当前证据

本轮只接入上游 Next Android 已有能力，保留本分支 Compose和第一人称。不增加桌面独有的 OmniMix、战斗数据、寻访查询、独立 LOD 或开屏高级参数。

基线为 `codex/android-thirdparty-alignment` / `7c3c0bc2`，开始时有 38 条未提交状态，原有工作已保留。上游核查点为 `ee441da9`。用户最初报告会客室仍显示原画面。续接触控投屏并修复视频方向后，用户确认画面正常。

## Owner 与推理闸

需要解决的是 Compose 替换后未续接的 Android 用户操作。配置仍归既有 ModuleSettings/FrameworkSettings，BEM 安装和索引仍归 BemInstaller，MMD 导入仍归 MmdImportSession/Plan 与当前 MmdLibraryInstaller；页面仅消费这些 owner，不恢复退役的平行安装器。

- 管理员外观：复用现行配置接口与 native weapon_appearance，不创建第二套配置。
- BEM：五个实验设置同一区；组合/预览复用现有 Activity；纹理与法线变换必须经过不可变代际、框架副本回退及条件索引提交；作者默认参数恢复进入当前草稿。
- 动效：当前游戏内 Compose 模型页接入实际 native driver 状态与控制，明确失败反馈，不能以本地按钮状态假装游戏已执行。
- MMD：接入压缩包、目录和导入前作品/槽位确认，发布到当前作品库。
- 自由相机：平滑值进入既有配置合同，范围沿用原 owner。
- 首页：恢复逐功能配置摘要与可达入口，区分配置启用和游戏实际生效。
- 媒体屏：先建立可复现反馈、收集配置/资源准备/模块启动/目标屏幕日志，再按证据修复。

最大回归面是不可变安装索引、跨进程资源读取和游戏线程控制。采用相关宿主/契约检查、Debug APK 编译与签名、设备日志和真实交互分别阻断；后者不可执行时单列原因，不声称实机通过。

## 待办与停止条件

- [x] 管理员外观与导航（源码与 APK 编译；武器/角色视觉效果仍需分别实测）。
- [x] BEM 五开关、组合/预览、参数恢复、法线与 ASTC（已接线；真实模型处理及渲染不以宿主测试替代）。
- [x] 游戏内动效控制（真实 native 状态与 owner 令牌；带驱动包的设备播放仍待验收）。
- [x] MMD 完整导入与自由相机平滑（真实 ZIP/7z 宿主检查与新导入页面实机打开）。
- [x] 首页功能总览（实机观察到已保存功能摘要）。
- [x] 媒体屏墙屏 MP4 播放与方向修复，用户确认正常。桌屏接收器未找到，仍待单独验收。
- [x] 宿主检查、APK 编译和签名、定向设备验证。
- [x] 文档回写与 git 范围复核，`git diff --check` 通过。

## 配置与资源 owner 的处理

模型变换复用不可变代际合同，发布新包、条件提交索引后才清理旧代际。索引回滚结果不明确时保留两代，避免仍被索引引用的文件消失。预览和组合复用 `BemInstalledSource` 从本地或框架副本读取，源长度必须与索引 `bytes` 完全一致；通用 `BemPackageCopy.copyExact` 拒绝短读与超长。`index()` 不再用截断本地副本的长度覆盖已有预期长度。

游戏内动效面板只展示原生当前 owner 的实际驱动，控制令牌包含 owner epoch；切包后旧令牌不能控制另一个包的同名驱动。命令提交后重新读取原生状态，不把提交成功当作播放成功。媒体与动效的状态采样仅在面板可见且 Activity 为 RESUMED 时运行，隐藏和离开取消。

## 个人入口与项目身份

按用户要求移除原作者个人社交、群号及赞助入口，删除专用页面、二维码、drawable、颜色和文案；必要许可证与源码署名保留。关于页的项目主页和许可证统一由 `ProjectLinks` 指向 `NukumizuKazuhiko/better-endfield-fixed`，软件和资源更新协议本轮未改。

## 媒体屏：实机反馈与修复

1. 用户报告进入会客室仍是原画面。设备配置确有启用的 MP4，文件也存在游戏私有媒体目录。旧原生日志仅显示模块启动，没有 Manual bind/playing；源码确认原生为手动启停，当前 Compose 缺少实际调用入口。新增触控启停、停止恢复、暂停/继续及视频跳转，并读取真实原生状态。
2. 初版新包冷启动出现 `Resources.NotFoundException`，堆栈定位 `MediaScreenControls.kt`。这是本轮引入的资源归属错误：悬浮层在游戏进程中读取模块资源 ID。由 `OverlayResourceContext` 在统一 Compose 边界提供模块资源，保留游戏身份、通信和数据目录。实机回归测试先复现外部资源表不可读取模块 ID，再验证包装后可读取媒体/动效/模型文案；已通过。修复后游戏冷启动并持续运行，用户回复“正常”。
3. 随后原生日志记录 `Manual bind: ReceptionRoomImagePoster(Clone)`、两个墙屏接收器、`playing:video`、纹理核验及暂停/继续；截图显示墙屏已经替换为用户视频。桌屏日志为 `displays=0`，桌屏效果不能声称通过。
4. 用户进一步确认画面顺时针偏 90°。从设备以二进制方式读取同一 MP4，实际长度 15,063,885 字节，`ffprobe` 显示编码帧 720×1584、display matrix rotation=90（FFmpeg 表示的逆时针角度）。正确播放器按标记显示为 1584×720。Android [MediaFormat.KEY_ROTATION](https://developer.android.com/reference/android/media/MediaFormat#KEY_ROTATION) 为顺时针方向，且只在输出 Surface 模式自动应用；现有 ByteBuffer 解码只做了 Unity 上下翻转，没有处理方向标记。
5. `AndroidVideoDecoder` 与 `VideoOrientation` 读取并校验 0/90/180/270 标记，在 YUV 转 RGBA 写入时先旋转，再转换为 Unity 底行起始布局。90/270 同时交换输出尺寸。方向测试先在旧映射上失败，修复后 34 项通过。新包已安装并冷启动，用户在会客室复测后回复“确认正常”。收尾时 ADB 已断开，最终方向以该实机反馈验收。

## 本轮实际验收

- `tools/AndroidPort/run_host_gate.py`：9 组 Java、169 项检查及 29 项 Python 测试通过。
- `enhancement_navigation_fixture.py`：24 项静态连线检查通过；包含管理员、MMD 导入、项目许可链接和个人入口移除，不替代真实交互。
- `scripts.tests.test_next_identity` / `test_distribution_index`：共 7 项通过。
- 生产 MMD ZIP/7z、提案与 VMD 分类宿主检查：9 cases passed。
- `native/tests/media_screen_android/Run.ps1`：媒体文件缓存/边界检查通过，媒体控制 13 项通过。
- `video_orientation_tests.cpp`：MSVC 编译执行，34 项通过（全部方向、非对称像素、Unity 行布局和手机录屏尺寸）。
- `:app:assembleDebug`、`:app:assembleDebugAndroidTest` 通过；测试包已安装，`testOverlayResourcesOnForeignHost` 设备执行通过。仅执行这项定向回归，没有声称整个 instrumentation 套件通过。
- 最后方向修复 `:app:assembleDebug` 通过，`apksigner verify` 通过，覆盖安装 PJX110 后冷启动成功；包名 `dev.betterendfield.next`，4.1.8 / 40108，ARM64。本轮产物均为 Debug 签名。
- 存在本轮未改动的原生 `codecvt` 弃用/未使用符号、Java 过时 API 和 `ndk.dir` 弃用告警，记录为既有构建债务，不宣称无警告。

上述实机结果来自分支拆分前的调试包。原验收证据保存在 `D:/codexdata/bem-android-ui-20261109`，原源码节点为 `0c1f5d68`。主线候选的独立验证见下方记录。

## 第一人称启用配置回归

PJX110 上设置开关已开启，但旧相机配置漏写 `first_person_enabled`，游戏原生日志记录 `first_person=false`。由现有 `ModuleSettings` 统一恢复该字段，启动设置应用时会按保存的偏好重发相机配置。没有新增配置键或角色资源。

`testCameraSettingsRoundTrip` 在旧包复现缺字段，在修复包上通过，并覆盖其他相机功能启用时第一人称仍可独立关闭。Debug APK 与测试包构建通过，APK 签名校验和覆盖安装成功。游戏冷启动后记录 `first_person=true`，首人称相关契约为 `ready`。包内为既定的 898 份 v3/v61 头饰资源及 1 份清单，游戏版本为 1.6.4 / versionCode 61，启动日志确认资源可用。头饰逐角色视觉覆盖需独立验收。

后续实机日志记录第一人称已进入、头部锚点已绑定，并持续应用相机姿态。女管理员的部分网格匹配了头饰补丁并完成赋值。用户确认能切换且视野正常。APK 中 898 份资源的 SHA-256 均与 v61 源目录一致。

## 主线候选独立验收

主线候选从 `56e07a1e` 重放安卓界面和修复提交。Actions 的原有持续冲刺源码与该基线一致。洁尔佩塔跨空中和落地的视觉连续性与手动、自动缓降一起维护在 `codex/android-glide`。

对候选独立执行 `:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`:app:verifyPortHost`，构建和 169 项 Java、29 项 Python 检查通过。导航连线 24 项通过。Debug APK 签名通过，包内包含 898 份 v61 头饰资源，DEX 和原生库扫描未发现 Android 缓降模块或配置标识。APK SHA-256 为 `BA38B72F46506CDCADFFF7798CDA5BF0A2A04266725C598A3986B00A9797FD7F`。此候选尚未安装到设备，既有构建告警按原记录保留。
