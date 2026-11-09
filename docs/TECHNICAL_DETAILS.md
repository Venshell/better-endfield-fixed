# 技术实现与配置参考

本文保存从项目首页移出的实现与配置细节。当前项目仅开发和发行 Android 端，见[产品边界](PRODUCT_BOUNDARY.md)；下文 Windows 控制器、注入器、配置与构建内容只供历史追溯及理解共用原生源码，不是当前交付或验收要求。版本与验收状态以当前源码、[更新日志](../CHANGELOG.md)和对应专题文档为准；日期性的探针结论不作为新版本的自动验收。

## 架构

```text
BetterEndfield.exe
  runtime/BetterEndfield.Host.dll
  modules/BetterEndfield.Model.dll
  modules/BetterEndfield.CustomModel.dll
  modules/BetterEndfield.Voice.dll
  modules/BetterEndfield.Music.dll
  modules/BetterEndfield.CombatStats.dll
  modules/BetterEndfield.UiModule.dll
  modules/BetterEndfield.Camera.dll
  modules/BetterEndfield.Actions.dll
  modules/BetterEndfield.Gacha.dll
  loaders/BetterEndfield.Injector.exe
  payloads/xinput1_4.dll
```

- `BetterEndfield.Host.dll`：唯一的进程内宿主、动态解析器和 HookBroker。
- `BetterEndfield.Model.dll`：开屏视觉、登录演员、模型资源和动画功能模块。
- `BetterEndfield.CustomModel.dll`：自定义角色外观（BEM）装配、材质与纹理绑定和 LOD 锁定模块。
- `BetterEndfield.Voice.dll`：语音语言、Wwise 媒体和口型功能模块。
- `BetterEndfield.Music.dll`：OmniMix PCM、Wwise Audio Input 和原游戏音乐回退模块。
- `BetterEndfield.CombatStats.dll`：伤害数字隐藏、战斗伤害统计、快捷键会话和本地结果模块。
- `BetterEndfield.UiModule.dll`：移动端界面布局与鼠标转触控输入模块。
- `BetterEndfield.Camera.dll`：自由相机、视场角缩放、第一人称与近景抖动处理模块。
- `BetterEndfield.Actions.dll`：持续冲刺与分角色动作外观模块，默认关闭。
- `BetterEndfield.Gacha.dll`：寻访记录查询与本地统计模块。
- `BetterEndfield.Injector.exe`：默认加载方式，Host 和模块均从软件目录加载。
- `payloads/xinput1_4.dll`：可选的 XInput 自启动代理，仅在用户确认后部署到游戏目录。

「显示增强」页不提供原生模块，它部署并配置 OptiScaler（DLSS/FSR/XeSS 超分与帧生成），改动直接写入游戏目录，下次启动客户端生效。

## 源码布局

```text
ui/BetterEndfield.UI/          WinUI 控制器与按领域分类的内嵌资源
native/modules/model/          开屏视觉、角色模型与动画模块
native/modules/custom_model/   自定义角色外观（BEM）装配与解析模块
native/modules/voice/          配音语言、Wwise 媒体与口型模块
native/modules/music/          OmniMix 音乐集成模块
native/modules/combat_stats/   战斗数据与伤害显示模块
native/modules/ui/             移动端界面与触控输入模块
native/modules/camera/         自由相机与第一人称视角模块
native/modules/actions/        持续冲刺与角色动作外观模块
native/modules/gacha/          寻访记录查询模块
native/loaders/injector/       外部启动注入器
native/loaders/xinput/         XInput 代理与进程内 Bootstrap
native/shared/                 Host、公共 ABI 头文件与第三方原生依赖
native/research/music_probe/   不进入发布包的音乐诊断模块
native/research/touch_probe/   不进入发布包的触控注入探针
manifests/model/               模型与动作资源清单
manifests/voice/               语音 Event/Media 映射清单
manifests/shared/              跨模块资源生成报告
resources/voice/               语音映射生成器的维护输入
android/                       Android/LSPosed 正式版本
scripts/                       公共构建、清单生成与资源扫描工具
tools/CustomModel/             BEM 转换、校验与角色资料工具链
tools/                         本地分析工具和工具链（不进入发布包）
docs/                          运行时接口、研究结论与集成交接文档
```

发布目录仍使用 `runtime/modules/loaders/payloads`，源码归类不会改变现有安装与加载路径。`artifacts`、`runs`、反编译结果和本地工具输出属于工作产物，不参与源码层级整理。

本地存在 `.codegraph/` 索引时，可用 `codegraph explore "符号或调用链问题"` 辅助定位静态代码关系；动态 IL2CPP 解析、Hook 行为及跨平台运行结果仍以源码、测试和实机证据为准。

## 模块 ABI

第一人称开发与验证记录：[S0–S8 路线图](CAMERA_FIRST_PERSON_ROADMAP_20260928.md)、[执行合同与验收记录](CAMERA_FIRST_PERSON_EXECUTION.md)、[上游源码复核](CAMERA_UPSTREAM_SOURCE_AUDIT_20260928.md)、[陀螺仪接口探针结果](CAMERA_FIRST_PERSON_LOOK_PROBE_RESULT_20261001.md)。这些文档记录各自时点的阶段状态，已发布功能以当前源码与发行说明为准。

相机设置可编辑眼位、高度、近裁剪、俯仰范围、朝向跟随、动画模式与强度等参数；运行中修改相机参数可热加载，首次启用相机模块仍需重启游戏。外部缩头会失去头部阴影。GPU 网格读取与封口属于阶段性开发合同，不能从旧探针结论推断当前已完成。

第一人称视角会在终结技施放、剧情镜头或游戏切换到非主关卡相机时临时交还原生视角，场景结束后自动恢复第一人称，开关状态不变。

第一人称头部隐藏也识别附着于头骨的头饰和发饰（即使部件名不含 `head` 或 `hair`），由“隐藏头部”设置控制，退出第一人称时恢复原渲染模式；混合躯干骨板的部件保守保留。

模块 ABI 使用纯 C 接口。模块通过程序集、命名空间、类、方法、参数和字段描述符动态解析 IL2CPP；Hook 入口由当前进程的 IL2CPP ABI 与 PE 可执行区间共同验证，不保存客户端地址或文件哈希条件。

## 加载方式

### 内置注入器

这是默认方式。UI 启动 `loaders/BetterEndfield.Injector.exe`，注入器启动目标游戏并加载 `runtime/BetterEndfield.Host.dll`。游戏目录不写入任何 Better Endfield 文件。

### XInput 自启动

当需要与其他加载器共同使用，或者希望通过官方启动器、桌面快捷方式直接启动时，可以安装 XInput 自启动代理。UI 会把 `payloads/xinput1_4.dll` 和一份归属记录写入 `Endfield.exe` 所在目录；游戏加载代理后，代理从 `%LocalAppData%\BetterEndfield\BetterEndfield.ini` 找到软件目录中的 Host。

安装器和设置页都提供卸载。卸载前会验证文件哈希和归属记录，不会覆盖或删除未知的同名 `xinput1_4.dll`；如果其他工具也占用该文件名，请改用内置注入器。项目不包含任何反作弊停用、规避或对抗逻辑。

## 路径与启动参数

UI 会优先验证已保存的游戏路径，再检查 Windows 卸载信息、常见安装目录和固定磁盘根目录下的有限候选，不递归扫描整块磁盘。设置页可随时重新扫描或手动选择 `Endfield.exe`；注入器固定使用软件目录下的 `loaders/BetterEndfield.Injector.exe`，不可另行指定。

游戏启动参数会同时用于“保存并启动”和一键启动快捷方式。例如填写 `-force-d3d11` 可要求 Unity 使用 Direct3D 11。内置注入器会把这些参数放在自身 `--` 分隔符之后再传给游戏。

## B 服兼容

B 服不通过官服 `GameAssembly.dll` 哈希判定。Host 在运行时解析 IL2CPP 元数据，模块只验证自己声明的类、方法、字段和资源契约。登录 SDK 或登录资源差异不会被当作全局失败条件。

模型和语音资源目录由当前游戏目录生成，PCK、BNK/HIRC 和 `AudioDialog` 不编译进 DLL。开屏模块分别解析模型替换、Logo 与登录色带契约；某个视觉契约缺失只停用对应能力，不会阻断其他功能，也不会套用官服地址。

音乐模块同样不验证 `GameAssembly.dll` 身份。它按完整 IL2CPP 元数据签名解析 `AudioMusicSystem`、`AkAudioInputManager` 与 Unity 主线程入口；官服/B 服登录 SDK 和登录资源差异不参与音乐契约。

战斗数据模块默认关闭。启用后按 `hotkey_toggle`（默认 F11）开始或停止一次统计会话，动态挂接
`BattleRecorder.RecordDamage(ref AbilitySystem.Modifier)`，从普通地图和关卡共用的结算后路径读取
攻击者、技能、伤害类型、伤害值和暴击字段；隐藏数字只挂接最终 UI 层的
`DamageTextCtrl/DamageTextCtrlV2._OnHpChanged`，不会阻断伤害计算、生命值或韧性流程。
结果写入 `%LocalAppData%\\BetterEndfield\\combat-sessions`，UI 的“战斗数据”页可刷新历史文件并显示总伤害排行。
模块会按需启动随软件分发的 `BetterEndfield.CombatOverlay.exe`，通过当前进程专属共享内存展示
角色头像、伤害排行、DPS 和按普攻、战技、终结技、连携技等技能分类分色的横向柱状图；F12（可配置）显示或隐藏，按住 Ctrl
并用鼠标左键拖动可保存相对游戏窗口的位置。「悬浮窗初始可见性」决定悬浮窗随模块启动后是直接显示还是先隐藏；
该项只在改变时生效，游戏内按热键切换的状态不会被其他设置的保存动作覆盖。悬浮窗不依赖 Better Endfield 主界面常驻，也不会联网读取头像。
伤害数字从一万起按每 10 倍切换“万、×10万、×100万、×1000万、亿”等显示单位。每次会话还会保存
0.25 秒粒度的技能分类与角色双维度时间桶；历史页默认显示最近三条，可按日期和最多四名参战角色筛选、删除记录，
并在角色排行与可拖动双端点的时间轴柱状图之间切换。时间轴可按技能类型或角色显示，并随模式显示对应图例。
开启 rDPS 口径后，模块按单次伤害实际扣血量守恒分配“直伤、攻击力、增伤、增幅、脆弱、承伤易伤、
减防/减抗、连携增益、法术强度、其他”十类贡献。跨乘区按乘数对数权重分配，同一乘区内按实际观测增量分配；
角色自身效果保留在直伤，只有其他角色提供且语义已验证的效果才转移贡献。随版本发布的
`modules/combat-semantics.besem` 提供 Buff、技能、元素和乘区语义，运行时不读取独立更新目录；软件升级时随模块一并更新。
每条新记录保存目录版本、验证覆盖率和有界未解析项审计，历史页可直接查看，无法验证的候选项不会参与 rDPS。
当前战斗记录使用 schema 11，只保存可验证的操作、原子结果、队伍快照和会话摘要；历史排行、技能统计、Buff 区间与时间轴均在读取时派生，不兼容更早的开发格式。64 位实例 ID 使用十进制字符串，避免浏览器解析时丢失精度。
字段和方法均按 IL2CPP 元数据描述解析，契约缺失时只停用该模块。schema 11 不按时间或 ID 前缀猜测归属，无法唯一验证的来源明确记录为未知。

## 自定义角色外观（BEM）

自定义外观默认关闭。正式扩展名为 `.bem`，一个包对应一个角色，可包含多种固定外观；玩家只需导入包，不需要 Python、原 Mod 注入框架、角色数据库或手写 `runtime.ini`。

自 3.3.0 起该功能在 Windows 与 Android 双端可用，两端使用**同一个标准 BEMv1 包**。Android 直接编译 `native/modules/custom_model` 的桌面源码，不存在第二套实现，也不引入任何游戏偏移；包的解析与校验路径两端一致。

桌面端的包与状态位于软件配置目录，发布包不携带任何外观资源：

```text
%LocalAppData%\BetterEndfield\catalog\custom-model\
  runtime.ini
  packages\*.bem
```

`runtime.ini` 由「角色外观」页写入，运行时只读：

```ini
[CustomModel]
standalone_lod=false

[Mod.<package_id>]
enabled=true
package=packages/<文件名>.bem
appearance=<外观 ID>
```

同一角色同时只允许一个启用包；出现重复启用时界面会全部停用并提示重新选择。包与外观的选择在下次启动游戏时生效，导入、更新和删除请在关闭游戏后进行，避免延迟加载读取正在变动的文件。更新使用相同 `package_id`，保留本机启用状态与仍存在的外观 ID，被移除的外观会提示并回退默认项。启用任意包时运行时会锁定 LOD；全部停用后恢复独立 LOD 偏好。

「其他来源 Mod 转换」可直接读取已解压目录及 ZIP、RAR、7z 源包，不需要预先解压或安装解压软件，也不执行包内程序。工具用源资源身份匹配随工具发布的角色资料，核对索引数、顶点流、骨骼与材质，完整检查通过后才允许导出 BEM；未适配的源包会输出待适配原因报告。RAR 与 7z 读取使用随工具附带的 7-Zip，许可证见工具目录下的 `7zip/NOTICE.txt`。

能力边界：支持部件替换/保留/隐藏、分离骨骼与材质来源、合并骨骼 palette、按绘制段使用游戏材质、替换指定原生纹理，以及 UInt16/UInt32 几何索引。每部件最多 256 个局部骨骼和 256 个 draw，每个选定外观最多 32 个纹理绑定、512 MiB 上传数据预算，超限直接报告失败。不执行源热键脚本与任意 Shader，不支持运行时形态切换、形态键、自动低模生成或自动分片。转换成功不等于实机验证，仍需在世界、详情页等场景核对。

Android 端在「第三方模型」页管理同样的包，导入时同样逐个外观校验并保留原始包字节。差异在于纹理：手机 GPU 的纹理格式与桌面不同，包内纹理在实机显示异常时可对该包执行「转换手机纹理」，成功后发布新一代并保留启用状态与已选外观，失败或取消则保持当前包不变；缺少已验证法线编码信息的包可以导入，但无法转换。两端都需要在切换包或外观后重启游戏。

创作者流程、转换自动化边界与完整字段见 [BEM 创作者指南](custom_model/BEM_CREATOR_GUIDE.md) 与 [BEM v1 规范](custom_model/history/bem-1.0/BEM_V1_SPEC.md)。

## 移动端界面与触控输入

移动端界面默认关闭。启用后模块挂接 `DeviceInfo` 的输入类型与设备类型访问器，让客户端按触屏布局构建 UI：虚拟摇杆、技能轮盘和触控专用控件会出现在 PC 客户端上。该模块主要面向串流到手机、平板或掌机的场景。

界面和输入是两条互不相通的链路，只改布局并不会让触控控件响应。客户端的触控读取全部经过 `EnhancedTouch.Touch.activeTouches`，而该集合只由 Unity 的 `Touchscreen` 设备填充，普通鼠标事件永远不会进入。因此模块同时提供鼠标转触控：通过 `CreateSyntheticPointerDevice` / `InjectSyntheticPointerInput` 注入合成触点，由 Unity 的 Windows 后端识别为真实 `Touchscreen`。鼠标左键即手指，按下、拖动、抬起对应触点的按下、移动和抬起；`Ctrl+Alt+T` 随时开关转换，关闭时立即释放当前触点。转换只在游戏窗口处于前台时生效，其余时间鼠标行为不变。

合成注入需要 Windows 10 1809 或更新版本；系统不支持时模块只记录一条日志并保持转换关闭，不影响界面部分。注入的输入受 UIPI 约束，Better Endfield 与游戏同进程运行，因此不存在完整性级别不匹配的问题。转换按 `dwExtraInfo` 的触控签名过滤自身回声，但不过滤 `LLMHF_INJECTED`——串流客户端正是通过 `SendInput` 投递鼠标事件的，那些才是需要转换的输入。

已知限制：注入使用屏幕绝对坐标，串流客户端需要工作在绝对坐标或触控透传模式，相对鼠标模式会让触点落在错误位置。触屏布局下客户端会改写键盘绑定掩码，除 WASD 移动外的键盘按键不生效——移动是唯一不经过触控链路的输入，由摇杆自带的键盘回退字段直接读取。向账号声明 Android/云游戏平台身份是独立于布局的能力，默认关闭且不由 UI 写入配置。

## Android 端（LSPosed）

Android 端由同一份模块源码编译（`android/`），经 LSPosed 挂接到游戏进程，不维护第二套实现。配套应用提供模型、第三方模型（BEM）、语音、画面增强与诊断页面；游戏内另有一个 Compose 悬浮控制面板，把桌面端热键变成可按的按钮：隐藏/恢复 HUD、自由视角、时间冻结、第一人称，以及自由相机移动与「运镜/关键帧」操作（运镜播放/停止、视角回正、视场角增减、滚转、关键帧记录/回放/清除）。设置屏为四个 tab（首页/体验/角色/工具），Kotlin + Jetpack Compose 工业黄黑白配色。

模块按 LSPosed 作用域附加到主进程，是否介入由证据决定（`UnityPlayer.nativeRender` 存在、原生 Hook 全部经 `libil2cpp.so` 导出按名解析），因此官服、国际服与渠道包共用同一份构建。按键与运行时命令经游戏文件目录中的文件中继（不经 JNI），原生侧轮询读入。

关键行为：相机参数热加载（首次启用相机模块仍需重启一次）；`.vmd` 镜头导入；面板「镜头转向」拖拽与第一人称陀螺仪共用同一条视角输入通路；第一人称头部隐藏分 GPU 网格补丁与「仅投射阴影」两条路径。当前 Android 端版本为 3.4.1（versionCode 30401），桌面端仍为 3.3.0，两端版本号暂不统一。

平台实现细节与「桌面热键 ↔ 面板按钮」对照见 [`android/README.md`](../android/README.md)。

## OmniMix 音乐集成

音乐集成默认关闭。Better Endfield 只保存用户选择的 `OmniMixPlayer.Backend.exe` 绝对路径，并从该后端的 `native\x64` 目录动态加载兼容的 `OmniPcmShared.dll`；不会复制曲库、音频或 OmniMix 程序。注册和运行时都会验证 OmniPcmShared ABI `2.x`、共享协议 `2` 与交错 `float32` 能力。后端路径缺失、ABI 不兼容、心跳中断或 PCM 缓冲不足时，模块保持或恢复原游戏音乐。

正式链路为：

```text
OmniMix instance shared memory
  -> BetterEndfield.Music 工作线程
  -> 48 kHz 立体声 SPSC 缓冲
  -> Wwise Audio Input Event
  -> 游戏 Music Bus
```

只有共享流、预缓冲、格式回调和采样回调全部健康后，模块才按登录、主界面/基地、游戏内三个独立范围暂停对应原生 Playing ID。它不会静音全局 Music Bus；Audio Input 暂态失败会退避重试，可闻游标额外保留 100 ms 输出队列余量。OmniMix 项目组的完整对接契约见 [集成交接文档](music/OMNIMIX_INTEGRATION_HANDOFF.md)。

Better Endfield 在实例握手中声明播放队列管理和 Seek 能力，因此可以直接在 OmniMix 中向该游戏实例添加、插入、移动和清空队列。

## 资源目录

UI 在保存配音规则时会从本机 PCK 选择性生成所需 Catalog。生成物位于
`%LocalAppData%\BetterEndfield\catalog`，发布包不会携带 PCK、BNK、WEM 或
`.becat`。角色规则写入配置前，UI 会先完成对应语言 Catalog 的原子更新；已删除
规则所对应的旧文件只会在 UI 自己的生成记录范围内清理。

开发或诊断时也可以手工生成：

```powershell
py -3 .\scripts\BuildVoiceCatalog.py `
  --game-path 'E:\Endfield Game' `
  --language Japanese `
  --character-id chr_0013_aglina `
  --output "$env:LOCALAPPDATA\BetterEndfield\catalog\voice.japanese.chr_0013_aglina.becat"
```

Catalog 只包含目标角色需要的 WEM，重复目标 Media 只存储一次。运行时会把所有已配置角色的 Catalog 合并为一张常驻路由表，并在第一条已配置角色语音到达、Wwise 已就绪时通过 `SetMedia` 一次性注册；其他角色发声不会触发卸载或重新读取。嵌入 UI 的索引只包含 Media ID、语言包指纹和相对路径，不包含音频内容；若官服与 B 服的 PCK 内容相同，即使 `GameAssembly.dll` 不同也复用同一映射，路径变化时会按 PCK 大小和解密后头部哈希定位。

## 构建

环境要求：Windows 10/11 x64、Visual Studio 2022 C++ 工具集、CMake、.NET SDK 9.0 和 Inno Setup 6。

```powershell
pwsh -File .\scripts\BuildBetterEndfield.ps1
pwsh -File .\scripts\BuildInstaller.ps1
```

原生构建入口是 `native/CMakeLists.txt`。MinHook 只由 Host 链接，模块不得自行初始化或卸载 Hook 引擎。

Android debug APK 由 `.github/workflows/android-build.yml` 在 GitHub Actions 上构建，推送与拉取请求触发，产物为 `better-endfield-debug-apk`。环境为 JDK 21、`platforms;android-37.0`、build-tools 36.0.0、NDK 27.2.12479018 与 CMake 3.22.1；构建前从 BepInEx/Dobby v1.0.5 取源码（该依赖不入库），并去掉其 `example/` 子目录——它需要 Better Endfield 已停用的 `DobbyInstrument`、`DobbySymbolResolver`。

## 配置

主配置位于 `%LocalAppData%\BetterEndfield\BetterEndfield.ini`，使用 UTF-16LE BOM 以保证 Windows Profile API 能无损读取中文路径；UI 设置位于同目录的 `ui-settings.json`。配置按模块分节：

```ini
[betterendfield.model]
enabled=false
model_replacement_enabled=false
logo_theme_enabled=false
logo_theme_color=#FFC928

[betterendfield.voice]
enabled=false
voice_router_enabled=false
voice_language_rules=*:Japanese

[betterendfield.music]
enabled=false
music_replacement_enabled=false
backend_exe=C:\Path\To\OmniMixPlayer.Backend.exe
client_id=better-endfield-example
replace_login=true
replace_meta=true
replace_gameplay=true
target_latency=0.4
prebuffer_ms=150
fallback_to_native=true

[betterendfield.combat_stats]
enabled=false
combat_stats_enabled=false
hide_damage_numbers=false
overlay_enabled=true
overlay_visible=true
hotkey_toggle=F11
overlay_hotkey=F12
rdps_display=false
auto_dungeon_session=true

[betterendfield.ui]
enabled=false
mobile_ui_enabled=false

[Loader]
install_root=C:\Path\To\Better Endfield
load_host=true
```

自定义角色外观不在主配置中。包文件与启用状态位于 `%LocalAppData%\BetterEndfield\catalog\custom-model`，由「角色外观」页维护，格式见上文「自定义角色外观（BEM）」。

## Android 实现与诊断

### 设置 UI 与悬浮面板

设置屏为四个 tab：**首页 / 体验 / 角色 / 工具**，另有第一人称、角色外观、运行日志、关于四个子页。Kotlin + Jetpack Compose，工业黄黑白配色（唯一色相来源 `UiTokens.kt`）。界面文案以操作名称、当前状态和必要的生效条件为主；详细实现说明保留在文档与日志中。启动时不显示通用设置提示，保存结果与错误仍在页面底部显示。

游戏内悬浮面板（Compose 版 handle + panel）提供需按键触发的控制：自由相机、时间冻结、第一人称、自由相机移动/视角 pad、FOV 缩放、运镜、VMD 回放；音量键作为「退出隐藏覆盖层 / 停止运镜」的中继。面板只显示实际加载模块对应的控制。面板 footer 内嵌运行日志，可「保存日志到文件」。

运行日志由游戏进程的 `RuntimeLog` 保存最近 150 行，悬浮窗直接读取该内存环。`Application.attach` 后，游戏进程通过 `RuntimeJournalProvider` 将有上限的快照发送给模块 App，App 在私有存储中保留最新快照，运行日志页、诊断页及导出功能读取它。框架提供给被注入进程的远程偏好设置只用于读配置，不能作为游戏到 App 的日志写入通道。Provider 仅接受 Endfield 包 UID 的发布调用，单条日志截取到 512 字符，总快照不超过 80,000 字符，后台单队列合并待发送快照。游戏未运行或尚未到达 `Application.attach` 时，App 只显示上次收到的快照或空态。

App 运行日志页使用固定高度的终端式视窗，只渲染最近 40 行，进入或刷新时定位到末尾；视窗内可向上滚动查看这 40 行。导出仍使用收到的完整 150 行快照，不受视窗裁剪影响。

### 构建

要求：JDK 21、Android SDK platform 37、build-tools 36.0.0、NDK 27.2.12479018、CMake 3.22.1。工具链在 `tools/android-toolchain`（含 Dobby v1.0.5）。

```powershell
# 在仓库根目录执行；debug 与 release 都需要完整头饰资源目录
$catalog = 'D:\path\to\catalog-bundled'
.\android\gradlew.bat -p android :app:assembleDebug "-PheadwearCatalogDir=$catalog" --offline --no-daemon

# release 另需签名凭据
.\android\gradlew.bat -p android :app:assembleRelease :app:verifyReleaseEntryPoints "-PheadwearCatalogDir=$catalog" --offline --no-daemon
```

release 构建开 R8（`isMinifyEnabled=true`），优化器阶段必须保持关闭（`-dontoptimize`）：R8 的类合并会把游戏进程内被轮询的静态类折进 Compose 持有者，导致游戏进程无堆栈崩溃。`:app:verifyReleaseEntryPoints` 挂在 `packageRelease` 上，校验 libxposed 入口、manifest 组件及游戏路径类，并按 Next 包名双向核对 Java native 声明和 APK 内的 JNI 导出。仅对明确登记的旧导出允许没有 Java 声明。R8 必须保留 NativeCommandBridge 的原生方法和由原生层按名称调用的 MMD、模型动效音频回调，发行门禁会检查这些名称。

MMD 导入在读取 ZIP 条目数据前拒绝 Zstandard 压缩方法 20 和 93。Commons Compress 的可选解码类 `ZstdInputStream` 仅由此格式使用，发行构建对该类单独配置 R8 缺失类规则。

GitHub Actions 的 `android-release` 可手动选择 `build_only=true`：使用 CI 中的发行签名构建并上传 `better-endfield-release-apk` artifact，仍检查版本号和证书，但不创建标签或 GitHub Release。默认 `build_only=false` 保留正式发布流程；已存在同版本 Release 时，单纯请求构建应选构建模式。artifact 只证明构建与签名门禁，游戏内行为仍需实机验收。

AGP 9 内置 Kotlin：只 apply Compose 编译器插件（`org.jetbrains.kotlin.plugin.compose`），**不得** apply `org.jetbrains.kotlin.android`。

### 签名

|  | debug | release |
|---|---|---|
| Keystore | `keystore/bem-debug.keystore`（tracked） | `keystore/bem-release.keystore`（gitignored） |
| 凭据 | `keystore/debug.properties`（tracked） | `keystore/release.properties`（gitignored） |
| Alias | `bemdebug` | `bemrelease` |
| 证书 SHA-256 | `4E:DD:10:B9:8A:C4:A2:39:A7:8B:64:93:15:09:A4:3F:A0:F3:28:FA:77:EF:B8:D4:3D:37:E5:73:58:FA:82:6F` | `8C:D6:FD:C1:50:38:53:0E:10:16:68:AB:4B:3C:CD:00:30:AE:88:AE:37:15:3E:6D:66:AA:45:93:0C:7B:8E:FD` |

均为 PKCS12 + RSA 2048 + SHA256withRSA。debug 身份入库，保证连续 debug 包可原位覆盖；release 身份只进 CI（三个 Secret 物化），本仓库是公开的，持有 release 密钥即可签名覆盖安装。`v3.3.20`、`v3.3.21`、`v3.3.22-alpha.1` 之前是 CI 临时密钥，跨这些版本升级需卸载重装（备份数据）。

### 限制

- ARM64 only，运行于 user 0。
- 目标包不硬编码：模块按 LSPosed scope 附加，需通过 Unity 检查（`UnityPlayer.nativeRender` 存在、原生 hook 全部按名解析，无固定偏移），客户端更新只要托管类型/方法名不变就不会失效。
- 角色/语言规则变更需 force-stop 后重启。
- 首次启用相机模块需重启一次（模块集在启动时决定）；同会话内的后续配置变更可热重载。
- 内置头饰资源首次部署需额外磁盘空间，后续启动核对缓存复用；旧外部资源目录不参与加载。

### LSPosed 排查

- Endfield 不出现在 scope 列表：打开 scope 页溢出菜单 → Hide → 关闭 `Games` 过滤器（LSPosed 全局过滤，Endfield 被归类为 game）。
- 推荐 scope 声明在 `META-INF/xposed/scope.list`。
