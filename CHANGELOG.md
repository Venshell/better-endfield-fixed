# 更新日志

本仓库派生自 [Dr-hydra/Better-Endfield](https://github.com/Dr-hydra/Better-Endfield)，独立维护，不是该项目的官方版本。3.3.20 是本仓库维护的第一条，记录相对上游的独立修改；3.3.0 及更早的条目来自上游。上游改动仍可能在需要时被有选择地并入。来源说明与许可见 [README.md](README.md) 的「来源与许可」。

## 2026-11-07 上游合并（开发分支）

接入上游 `ee441da9`（4.1.8 发布后的修复），处理 Next 命名、BEM/Android/原生模块与文档迁移冲突。保留本分支 Android 产品边界及内置第一人称、陀螺仪等本地功能。此条不是发布或游戏设备验收声明；处理原则和实际检查结果见 [合并记录](docs/ANDROID_UPSTREAM_MERGE_20261107.md)。上游发行变化另见 [上游历史更新日志](docs/archive/UPSTREAM_CHANGELOG_20261107.md)。

## 4.1.8

- Android ARM64 采用 Next 安装身份 `dev.betterendfield.next`，版本号 `40108`。Android 10 及以上使用 LSPosed/libxposed API 102。原 3.x 设置和作用域需在 Next 中重新设置。
- 首页加入游戏管理，将启用功能一览放在游戏管理下方。恢复管理员武器外观、BEM 组合与预览、模型动效控制、MMD 导入确认与槽位配置。
- 修复第一人称启用配置丢失，恢复会客室媒体屏投放并修正视频方向。这两项已在拆分前的调试包实测，正式 APK 的设备验收另计。
- 内置 [v3/v61 头饰目录](https://github.com/Venshell/better-endfield-fixed/releases/tag/headwear-v3-v61)，共 898 份资源，适用于 Android 游戏 1.6.4（versionCode 61）。
- 正式版保留原有持续冲刺。手动和自动缓降随 [实验构建](https://github.com/Venshell/better-endfield-fixed/actions/workflows/android-build.yml?query=branch%3Aexperimental%2Fandroid-glide) 单独提供。

## 3.4.2（未发布）

接入上游第三方模块的 Android ZIP 导入、私有代际部署、原生 Host/Hook 合同、网页桥与 Compose 管理入口。管理界面、包路径校验和部署已在 Android 10 设备测试；独立 ARM64 Host 已验证认证、消息、配置变更与停用。真实游戏进程中的第三方库、网页交互和长期资源清理仍待验收，本条不代表已发布。

新增普通游戏镜头的全局 FOV 开关与角度设置。原生层只在透视主相机的 Cinemachine 状态提交期间覆盖角度，随后恢复原状态；自由镜头和第一人称沿用各自设置。Android 10 设置页和配置测试通过，游戏内镜头画面仍待验收。

自由镜头增加跟随人物位移开关，角色身份切换、无法读取姿态或瞬间大幅移动时重新定位，运镜播放时暂停跟随。设置页在 Android 10 设备上核对，原生位移边界测试通过；设备未安装游戏，游戏内镜头行为待验收。

移植上游 BEM 1.3 的体型参数、形变帧与创作者工具链。Android 模型安装页可选择离散滑条值，安装索引保存并恢复选择，游戏启动配置传给共用原生模型读取器；参数选择进入缓存身份，避免重复使用旧形变。创作者工具、原生解析器与设备安装器测试已通过。当前设备没有游戏资源，形变画面仍待游戏内验收；模型热切换、低峰值加载和 MMD 仍在后续接入范围。

继续接入上游 3.5.0 的按资源区分 BEM 骨骼别名：场景只接受 world 别名，详情只接受 UI 别名；场景查找可从 UI 骨骼名回退到包声明的标准名。Android Debug 构建和资源别名隔离回归已通过，游戏内替换仍待验收。

Android 版本递进至 `versionName=3.4.2`、`versionCode=30402`，桌面端版本不变。选择性移植上游 3.4.4 的悬浮窗透明度、拖动后靠边吸附和 BEM“关闭全部模型”。沿用本仓库 Compose 悬浮层与现有安装索引；模型批量关闭保留包文件与外观选择，重启游戏后生效。第三方模型加入按角色筛选、保留本地副本和清理未使用数据，并接入 Android 游戏进程清理链路。本轮未接入上游模型热切换。验收范围见 [Android 上游 3.4.4 移植记录](docs/ANDROID_UPSTREAM_344_PORT_20261004.md)。

修复设置应用结束悬浮窗预览时误加载 LSPosed 游戏进程类导致的闪退。预览已在设备上核对；当前设备没有游戏资源，模型及游戏内 Hook 链路尚不能实测。本条仅记录开发分支，未发布 GitHub Release。

选择性移植上游 3.4.3 的 Android BEM 兼容性：替换网格使用包自身的顶点声明和步长，并在构建后按包验证；世界场景可接受完整后缀一致的原生 LOD 材质对应，同一纹理对象绑定多个材质属性时不再误拒绝。保留现有身份、骨骼和资源边界校验；不引入上游的模型热切换或关闭校验入口。此条仅记录代码进展，游戏内画面与发行包尚未验收。

PC 布局下的相对鼠标已完成并在真机验收。游戏切到桌面输入类型后改为读 `Mouse X` / `Mouse Y` 两个轴，而 Android 的绝对触摸路径永远不喂这两个轴，原先视角一移到屏幕边缘就停住；现在由模块把悬浮面板上的滑动与点击转成相对位移投给原生输入层。滑动连续转向与点击均已实机确认（PJX110，外接鼠标）。未接外接鼠标时该捕获路径会自动抑制，触摸操作不受影响。该功能的原生入口与帧派发链一并补齐，详见[上游 v3.5.2 分类与第 11 项](docs/ANDROID_DELTA_VS_UPSTREAM_3510FA7.md) §十一。

悬浮窗日志头新增一行运行时状态摘要：由游戏进程内的原生层写入，经 JNI 交给模块读取后在悬浮窗日志头部渲染。生成侧刻意放在本仓自己的模块启动序列里，避免把没有真正启动的模块报成就绪。上游第二个消费点（设置页）本仓是 Compose 实现，尚未接入；`camera.speed` / `camera.fov` 也尚未被本仓界面读取。

修复 MMD 作品在 Android 上没有配乐的问题。原生 MMD 导演经 `BE_LocalMusicApiV1` 取本地音轨，而 Android 侧的实现只在拿到 JVM 之后才生效，此前没有任何代码把 JVM 交给它，于是音轨接口恒为空、MMD 音乐的功能开关也一直关着。本次接入 Java 侧单曲播放实现（`MmdAudio`，`MediaPlayer` 加独立工作线程，支持播放、暂停、跳转、音量与进度查询、错误上报），并复用 PC 鼠标桥已经解析到的那份模块类完成绑定；绑定失败只失去音乐，其余功能不受影响。播放传输层已用设备外宿主夹具验证 23 项断言全过，游戏内听声仍待真机验收。

## 3.4.1

Android 补丁版：`versionName=3.4.1`、`versionCode=30401`，桌面端版本仍为 3.3.0。修复游戏进程日志只在悬浮窗显示、设置应用运行日志页为空的问题：游戏进程将有界日志快照发送到应用私有存储；日志页以终端式视窗显示最近 40 行，导出保留收到的完整快照。精简 Android 设置页的重复功能说明，移除通用“当前设置已加载”提示框；三份 README 改为快速入口，技术细节和旧 Android 开发记录迁入专题文档。

日志显示和滑动行为已由用户验收。本版本的 Release 构建、签名和产物校验以发布工作流结果为准；此条不扩大为对所有游戏包、角色和故障分支的实机验收。

## 3.4.0

Android 正式版：`versionName=3.4.0`、`versionCode=30400`，桌面端版本仍为 3.3.0。基于用户已验收的 3.3.22-alpha.21 合成 APK，保留陀螺仪与 834 份内置头饰资源；本次版本递进和 CI 资源获取不改变游戏内功能。正式版构建从已验收的 `v3.3.22-alpha.21` Release 获取固定的 `headwear-catalog-v3-834.zip`，校验归档 SHA-256 `BEAF2135063C962D382129098B65A3779D18ADF515EBDAC1FBD292E7B4644A78`，再执行已有 834 份资源校验。用户反馈合成包“验收通过”；未提供新的 3.4.0 实机日志或逐角色画面，不扩大为所有角色和故障分支都已复核。

## 3.3.22-alpha.21：修「第一人称陀螺仪像方向键一样跳固定角度」——输入单位错了

2026-10-02 本地发行整合：将陀螺仪修复与第一人称头饰网格资源合入同一 Android APK。包内含 834 份 `.behw` 头饰资源和校验清单；首次启动时在游戏私有目录原子部署，后续逐文件核验缓存。资源内容与先前实机接受的内置资源包逐份一致。合成包的 Android Release 构建、入口检查、签名、资源解压/缓存/834 份字节比对和 Windows Camera 定向测试通过；用户反馈本合成包实机“验收通过”。本轮未收到新的设备日志或逐角色画面，不扩大为所有角色及失败分支均通过。萤石保留已接受的小三角边；噗切娜、大潘暂缓，卡缪、利诺、伊冯尚未逐项验收。构建必须显式提供已验证的头饰资源目录；缺少目录时拒绝生成 APK。

alpha.20 把注入点改对了（`CameraManager.OnInput`），实机**有反应了**，但转动是**离散跳变**：用户描述「像上下左右的按钮，点一下转向固定角度，没有平滑移动」。这不是限位，是输入被量化成了固定步长。

**根因：`OnInput(inputX, inputY)` 的参数是「屏幕百分比增量」，不是像素；而且前一版把像素量级压得太低，落进了控制器的最小速度阈值量化区。** 决定性证据来自 IL2CPP 反编译（`legacy/.../IL2CPP_Dump_Normal/Gameplay.Beyond.dll.cs`）：`CustomFreeLookCameraController.DragCameraHorizontal(System.Single deltaScreenPercentageX)` / `DragCameraVertical(System.Single deltaScreenPercentageY)` 的参数名明示单位是**屏幕百分比**；同一控制器的 `CameraInputCtrlConfig` 里有 `_xDragSpeed` / `_yDragSpeed` 与 `_xAccelerationConfig` / `_yAccelerationConfig`（后者含 `_speedMinThreshold` 最小速度阈值）。玩家手指在屏幕上滑动的像素被换算成「滑动距离 ÷ 屏幕尺寸」的百分比后喂进这条链，再由 DragSpeed 缩放成角度。前一版做错了两处：

1. **单位错**：把「像素增量」原样喂给要吃「屏幕百分比」的 `OnInput`。
2. **量级错**：`PIXELS_PER_RADIAN=30` 太小，慢速转动时 Java 侧 `(int) residualX` 截断只吐 ±1 像素，落在 `_speedMinThreshold` 阈值边缘，被游戏吸附量化为「点一下跳一格」的离散动作。

n12.log 实测证据：55 条 `applied` 行里 `dx`/`dy` 全部只有 0/±1，从未超过 1——增量在进 `OnInput` 之前就被量化成了 ±1。

**修复（两层，不改自由视角路径）：**

- **单位换算**：新增 `unity.screen.width.get` / `unity.screen.height.get` 合同（`UnityEngine.Screen` 静态属性），在 `ApplyFirstPersonLook()` 里把像素增量 ÷ 实时渲染分辨率 → 屏幕百分比，再喂 `OnInput`。屏幕分辨率拿不到时**丢弃增量**而非按像素误喂（宁可慢一拍也不重新引入吸附）。方向符号不变：`look_x = dx / screen_w`、`look_y = -dy / screen_h`。
- **量级校准**：Java 侧 `GyroscopeController.PIXELS_PER_RADIAN` 从 30 调到 **1100**——锚点是「1 弧度转动 ≈ 1 屏幕宽拖拽」。约 1080–1440px 的横屏下，1 rad/s 转动产出约 1.0 屏幕百分比/秒，与手指快速拖拽（约 0.8 百分比/秒）同量级，灵敏度滑杆（0.2–5.0）在此范围内微调；同时让增量远离阈值量化区。

`versionName=3.3.22-alpha.21`、`versionCode=30322`。用户已反馈合成 APK 实机验收通过；百分比→角度的精确系数（DragSpeed 数值）与极慢转动下的最小速度阈值没有独立设备日志，仍作为后续标定边界。

## 3.3.22-alpha.20：修「陀螺仪在第一人称下依旧无动作」——注入对象用错了

alpha.19 改了门控，实机依旧没有 `applied` 行，但根因和上一轮**不同**：门控这次放行了（用户按了模块第一人称热键，`module_fp=1`），卡在**注入对象不存在**。

**根因：`SnapshotCameraController` 是拍照/快照相机，不是模块第一人称的 look 控制器。** 探针枚举（`fp_look_probe`）把它的方法列全了，签名是决定性证据——`ActivateSnapshotCamera` / `DeactivateSnapshotCamera` / `SetAperture` / `SetFocusDistance` / `GetCameraParamFullSnapshot` / `GetCameraRoll` / `_HideChar` / `_ShowChar`，全是摄影语义。它只在 `ActivateSnapshotCamera()` 时才被激活进场景，玩家常规游玩（含模块第一人称）时 `FindObjectOfType` 找不到实例，于是 alpha.16–19 的 `RotateCamera*` 注入拿到了空指针、直接静默 return。

**正确的注入点是 `CameraManager::OnInput(float, float)`。** 模块第一人称（0xBD 热键）走 Cinemachine CameraState 改写（`ApplyFirstPersonState` 只动 position/FOV、朝向原样保留游戏值），它的朝向由**游戏主相机控制器** `Beyond.Gameplay.View.CameraManager` 驱动。探针枚举出 `CameraManager` 共 134 个方法，其中 `OnInput(float,float)` + `get/set_curFrameInput(Vector2)` 就是主相机的 look 输入入口——玩家触摸转动视角走的就是它，把陀螺仪增量喂进去和手指拖动是同一条路径。

- **新增方法合同** `camera_manager.on_input`（`CameraManager.OnInput(float,float)->Void`），删除走错方向的 `snapshot.rotate_horizontal` / `snapshot.rotate_vertical`。
- **`DetourTailLateTick` 缓存 `CameraManager` 实例**：模块本就 hook 了 `CameraManager::TailLateTick`，每帧白拿实例，加 gchandle 钉住存入 `g_first_person.camera_manager`。不用 `FindObjectOfType` 去找。
- **`ApplyFirstPersonLook()` 注入改为**：门控只认 `g_first_person_active`（模块第一人称），取增量后 `InvokeVoid("camera_manager.on_input", manager, {dx*scale, dy*scale})`。删除了读 `snapshot.is_first_person` 的 `FirstPersonLookAvailable()`——那条路径在模块第一人称下同样走不通（模块第一人称不设置游戏的原生第一人称标志）。
- **就绪判定** `g_first_person_gyro_contract_ready` 改为依赖 `camera_manager.on_input` + `camera_manager.tail_late_tick`。
- **探针扩展**（只读）：`android/.../first_person_look_probe.cpp` 新增枚举 `CameraManager` / `CameraMono`，这正是本轮定位正确注入点的依据。`CameraMono` 全是 dither（网格淡出）方法，与 look 无关，排除。

`versionName=3.3.22-alpha.20`、`versionCode=30322`。**构建与门禁通过，待实机验收**：`OnInput` 参数的单位与符号（增量像素 vs 弧度、x/y 方向）需实机标定，预期 1–2 轮。

## 3.3.22-alpha.19：修「陀螺仪在第一人称下依旧无动作」——门控用错了状态

alpha.16 把消费端接上了，实机仍然毫无反应。alpha.17/18 是加诊断的迭代，本版是真正的原因。

**根因：门控读的是模块自己的第一人称状态，而玩家处在游戏自己的第一人称里。** `ApplyFirstPersonLook()` 的第一道门是 `g_first_person_active`，而这个变量只在 `EnterFirstPerson()` 里被置真——它由**模块自己的第一人称热键**（`VK_OEM_MINUS`，189）经 `PumpFirstPerson()` 触发，含义是「本模块把游戏切进了第一人称相机」。玩家实际所在的第一人称是**游戏原生的视角**，用游戏自己的操作走进去，从头到尾不碰模块热键。于是门永远是关的，而增量的唯一痕迹是诊断行里的 `active=0`。

实机日志（alpha.18，用户已进第一人称并晃手机）逐字为证：

```
Camera tick heartbeat: source=render loop ticks=900 game_fp=0 module_fp=0 fp_enabled=1 free=0 gyro_look=1 pending=-1,-1
First person gyro: deltas present but gated by camera state active=0 free=0 pending=-53,-324 seq=840
```

`pending=-53,-324`（并随晃动增大）证明陀螺仪增量确实在累加器里等待消费；`gyro_look=1`、`fp_enabled=1`、`free=0`、心跳在跑，其余四个门全开；**只有 `active=0` 是零**。日志里没有任何 `First person camera enabled` 行，说明模块的 `EnterFirstPerson()` 从未被调用。

- **新增 `FirstPersonLookAvailable()`**：读**游戏自己的** `snapshot.is_first_person` 标志（`SnapshotCameraController` 的 `<isFirstPerson>k__BackingField`，字段合同早已存在，此前只被 `photo_mode_exit` 的可用性判定用到）。控制器实例没找到、或字段读不出来时**返回 false**——这是正确答案：没有实例就没有可调的旋转方法，应用不了任何东西，增量必须留给自由视角。
- **门控改为「自由视角 armed 则让路，否则看两人称状态」**：`if (g_free_camera_active) return;` 先行（与 `PumpFromEngineTick` 里两个消费端的先后顺序一致），随后 `game_first_person || module_first_person` 任一成立即消费。保留 `module_first_person` 一档，是因为模块热键仍然是一条合法路径。
- **拿不准时不取走增量**：读不到游戏标志就让它留在累加器里，自由视角 arming 时照旧找得到——与改动前完全一致。因此这次改动对自由视角是**零风险**的，它对增量的可见性只增不减。
- **心跳行字段重命名**：`fp_active` 拆成 `game_fp` 与 `module_fp` 两个字段，使「玩家在不在第一人称」与「模块有没有切第一人称」不再共用一个数字——上一版正是这个混淆让诊断多花了两轮。
- **拦截行措辞改为「held, no first person camera」**：它现在表达的是「两个消费端都没认领」，而不再是「被相机状态挡住」。

`versionName=3.3.22-alpha.19`、`versionCode=30322`。**构建与门禁通过，待实机验收**：`RotateCamera*` 的参数单位（度/帧 vs 度/秒）尚未标定，因此灵敏度手感预计还需一到两轮实机调整。

## 3.3.22-alpha.18：诊断版本（无功能变更）

心跳行与拦截行已经证明陀螺仪增量确实到达累加器，但被门控拦下。本版把「哪一档门是关的」拆得更细：心跳行加打 `fp_enabled`、`gyro_look`，拦截行改为每次取用前无条件打 `active/free/pending/seq`（此前是 `% 40` 采样，无法区分「路径没跑」与「手机静止」，且用 `static bool` 锁存——只有增量为零才复位，而晃手机时永远不回到零，于是只报前几次就沉默）。同时把 `g_first_person_gyro_contract_ready` 的赋值移到 `Camera feature contracts:` 汇总日志**之前**：它原本在之后，导致每次启动都打 `first_person_gyro=unavailable`，而路径实际是 armed 的——日志在撒谎，而这条正是操作者用来判断功能是否存在的依据。

## 3.3.22-alpha.17：诊断版本（无功能变更）

在 `PumpFromEngineTick` 末尾加一条无条件、限流的心跳行（`% 900`）。此前的每一条候选日志都被某个条件门控（「第一人称激活」「陀螺仪开着」），而一份「门从没开过的日志」无法区分「功能坏了」与「泵根本停了」。这一行只回答这一个问题。同时修掉 Java 侧的一处键名错误：`FirstPersonGyro.toIniLines()` 发的是 `first_person_gyro_horizontal_sensitivity` / `_vertical_sensitivity`，而原生**根本不解析**这两个名字——这是「开关打开却无动作」的另一半原因，原生因此无法区分陀螺仪与手指。改为发 `first_person_gyro_look` / `_horizontal` / `_vertical`。

## 3.3.22-alpha.16：陀螺仪接上第一人称（增量注入游戏自己的旋转方法）

上一版定位到第一人称视角的真实入口：`Beyond.Gameplay.View.SnapshotCameraController`，它提供 `RotateCameraHorizontal(float)` / `RotateCameraVertical(float)` 两个增量旋转方法。这一版把它们接上。

**问题本来不在链路，而在消费端。** 陀螺仪从 α 版起就一路通畅：传感器 → `GyroscopeController` → 输入中继 → `FoldPanelLookInput` → `g_mouse_dx/dy`。但这条增量此前**只有一个消费者**——自由视角的 `g_free_target`（`free_camera_runtime.inc:772`，只在自由视角 armed 时读取）。第一人称侧从来没接过它，所以「开了陀螺仪、第一人称下毫无反应」。自由视角的行为本身是对的，本版**不改动它**，只是**新增**一条第一人称消费分支。

- **原生新增 `ApplyFirstPersonLook()`**：在 `PumpFromEngineTick` 中于 `FoldPanelLookInput()` 之后、自由视角取用之后调用。仅当第一人称激活且自由视角未激活时 `exchange(0)` 取增量，换算后经 `InvokeVoid` 调用游戏的 `RotateCameraHorizontal/Vertical`。两相机互斥——它们共用同一个累加器，同时消费会各偷走对方一半的动作。
- **新增方法合同** `snapshot.rotate_horizontal` / `snapshot.rotate_vertical`，并引入独立就绪标志 `g_first_person_gyro_contract_ready`：这两个方法解析不到时只静默停用陀螺仪路径，**不会**把第一人称相机一起拖下水。
- **解除一处既有耦合**：`ModuleSettings.setCameraSettings()` 此前写着「陀螺仪开 ⇒ 强制打开自由视角」。那是上一版陀螺仪只能被自由视角消费时的权宜。接上第一人称后它变成有害（会误开自由视角并让两条路径抢同一份增量），已改为「陀螺仪开 ⇒ 打开第一人称」。
- **新增原生配置键**：`first_person_gyro_look`、`first_person_gyro_horizontal`、`first_person_gyro_vertical`。此前 `first_person_gyro_*` 全部只是「给人看」的字符串，原生一行都不解析——这正是「开关打开却无动作」的另一半原因：原生无法区分陀螺仪和手指。现在 `first_person_gyro_look` 决定增量投给哪个相机，两个 scale 是每中继像素换算给 `RotateCamera*` 的系数。
- 灵敏度走陀螺仪**自己**的一组值，不复用自由视角的 `mouse_sensitivity`（后者是为屏幕拖拽调的，且会被运镜页覆写）。原生夹取放宽到 ±1000，因为 `RotateCamera*` 的单位无法静态确定，需要实机标定；真正的夹取在应用层（0.2–5.0）。
- 绕开了设计文档 §3.1 警告的问题：走的是游戏自己的旋转方法，身体跟随、pitch 夹取等下游逻辑照常生效；模块不持有任何朝向真值，因此没有累积漂移、退出也不跳变；玩家触摸输入与之叠加而非被覆盖。
- 顺带记录：本版把两处文档的表述由「陀螺仪终点错了」更正为「自由视角无需改动，第一人称缺的是消费端」；探针结果文档的方法计数由 41 更正为该控制器实际的 35 个。

`versionName=3.3.22-alpha.16`、`versionCode=30322`。

## 3.3.22-alpha.15：探针闸门改用设置项（设备无 root，前两种通道都走不通）

实机部署时发现探针的前两种启动方式在这台设备上**都不可用**，探针因此仍然开不起来。

- **设备实测**：PJX110、Android 16、**未 root**（`adb shell` 是 `uid=2000`，无 `su`）。这直接否掉两条路：① 我上一轮写进文档的 `su -c 'VAR=1 am start'` ——没有 `su`；② `setprop debug.betterendfield.fp_look_probe 1` ——属性本身能写（shell 有权限），但 **release 包读不到**：`if (!BuildConfig.DEBUG) return false;` 是编译期常量，R8 把整个分支连同属性名字符串一起折掉了（实测 release dex 里 `debug.betterendfield.fp_look_probe` 计数为 **0**）。而 `setprop` 本来也对已在运行的进程无效——模块集是在 `JNI_OnLoad` 里定的。
- **改用设置项做闸门**：`ModuleSettings.DEBUG_FP_LOOK_PROBE = "debug_first_person_look_probe"`。这条通道**不需要 root**，而且是本仓唯一已经打通「设置进程 → 游戏进程」的既有设施——`FrameworkSettings.publish()` 把本地 `module_settings` 整份镜像到框架远程首选项，游戏侧读的正是那份快照（alpha.12 修跨进程读错文件时确立的机制）。实测 release dex 里该键字符串计数为 **1**，存活。
- **设置页加了开关**：第一人称页新增「诊断」分组 +「接口探针」开关（`SettingsState.firstPersonLookProbe` + `updateFirstPersonLookProbe`，字符串资源 `camera_fp_look_probe` / `_hint` / `fp_group_diagnostics`），文案明确写清它是研究开关不是功能。改动需**重启游戏**生效（读一次，在库加载时）。
- 环境变量通道保留为后备（`BETTER_ENDFIELD_FP_LOOK_PROBE=1`），两者任一为真即启用。
- `versionName=3.3.22-alpha.15`、`versionCode=30322`。

## 3.3.22-alpha.14：修「探针日志到不了日志文件」+ 日志环丢行

实机跑 alpha.13 的探针，日志里 **一条 `fp_look_probe` 都没有**，但 `[runtime] Android module runtime started` 和三个真模块的启动行都在。定位到两个独立缺陷。

### 缺陷一：诊断文件路径被无条件覆写，调用方指定的一律作废

native 日志有三个去向：logcat、内存环（喂设备内日志）、以及 `BETTER_ENDFIELD_DIAGNOSTICS_PATH` 指向的文件。第三处是 release 包**唯一可靠**的落盘通道——注入进程的 logcat 常被系统压掉。而 `RuntimeBootstrap.load()` 只在一件事上设过它：`if (BuildConfig.DEBUG)`，然后**无条件覆盖**。

两个后果叠加：release 构建**从来没有**诊断文件（该行被编译掉）；而按说明从进程环境传进 `BETTER_ENDFIELD_DIAGNOSTICS_PATH` 的做法**同样无效**——调用方设的值会被这行覆盖掉（debug 构建）或根本没人写（release 构建）。所以「探针应该写进指定文件」这件事在任何构建上都不成立。

修法：只在变量**未被设置**时才填默认值。调用方先设的路径优先，这正是「探针往哪写」能生效的前提。默认值同时改为：debug 构建写 `cache/betterendfield-diagnostics.log`，release 构建只在探针被请求时写 `cache/betterendfield-fp-look-probe.log`，其余情况不设——避免给正常会话加一条逐行 fopen 的写路径。

### 缺陷二：日志环丢行后不承认，尾部内容整段消失

`CopyNativeLogSince` 的陈旧游标规则是 `if (cursor > g_ring_total) cursor = 0`。该规则只在「环的总计数只会前进」时成立，而同一进程内模块库重载会让计数**从 0 重开**：此时旧会话留下的游标远大于新计数，于是回绕到 0，把新会话**全部**行重新投递一遍；日志记录侧的单调序号过滤把它们**全部当作重放丢弃**——结果就是面板连着看到的第一屏永远是空的。

同时 `Remember()` 覆盖环形槽位时**从未回退已投递游标**。模块初始化是 305 行的突发（用模块一行一个合约），而中继约 0.5s 才排空一次，一次突发完全可能超过 512 行容量；被挤掉的早期行（正是探针那几行）中继从未见过，却因为游标已经越过它们而被永久跳过。

修法：新增 `g_ring_read` 记录中继已拷出的最高序号；`Remember()` 在写满一圈后把游标顶到仍驻留的最老行，`CopyNativeLogSince` 只在游标落后于驻留窗口（或超出总计数）时才重置，并且不再把「游标领先」误判为需要回绕到 0。环仍然只保留 512 行——丢最老的本来就该丢；不能接受的是**声称它送出去过**。

### 附带：让「探针没跑」和「探针跑了但没结果」不再长得一样

- 模块全部启动后追加一行 `modules started: <id> <id> ...`，未注册任何模块则打印 `(none)`。两种情况需要完全相反的修法，没有这行就区分不开。
- 探针组件名改用 `fp_look_probe`（原为 `betterendfield.camera`），避免被相机模块自己的大量运行时行淹没——也正是这次差点被淹没的那批行。

`versionName=3.3.22-alpha.14`、`versionCode=30322`。探针仍未取得枚举结果，第一人称陀螺仪仍未实现。

## 3.3.22-alpha.13：陀螺仪方向默认对调 + 第一人称陀螺仪接口探针

- **陀螺仪两个轴默认对调**（实机验收反馈「xy 方向都是反的」）：`first_person_gyro_invert_horizontal` / `first_person_gyro_invert_vertical` 的默认值由 `false` 改为 `true`。原始传感器轴到相机期望的映射在横竖屏下差一个符号，把校正放进默认值而不是改传感器映射，两个开关才仍然有意义——它们依然表示「翻转这个轴」，设备不同的用户可以单独关掉任一个。`FirstPersonGyro.disabled()` 的默认值同步改为 `true`：关闭态与默认态保持同一种指向，重新打开时不会静默翻轴。
- **为第一人称加入陀螺仪的接口探针**（只读，默认不运行）。本轮先确认了一件与设计文档不符的事实：文档假设的第一人称 look 控制器（`localYaw` / soft_limit / hard_limit）**在本仓库不存在**（全仓 0 命中），且 `ApplyFirstPersonState` 的注释明确写着 orientation **原样保留游戏给出的值**、只把位置移到眼锚点。也就是说陀螺仪没有可直接喂入的入口，把增量直接折进 `CameraState` 又正是文档 §3.1 明确不推荐的（会绕过身体跟随、与瞄准输入抢朝向、退出时跳变）。定位入口需要先知道类里有什么，因此先做只读枚举。
- 探针本体是 Android 侧模块 `android/app/src/main/cpp/modules/camera/first_person_look_probe.cpp`：枚举 `SnapshotCameraController`、`PlayerController`、`MovementComponent`、`MoveInput` 的方法与候选字段，只打日志、不改任何状态。为此在 Android 运行时里新增 `Il2CppRuntime::DescribeClass`——`ResolveMethod` 只能回答「某个成员在不在」，而定位未记录入口需要的是「有哪些成员」；实现遍历 `class_get_methods_` 并逐条输出签名、入口地址与是否可执行。字段只按候选名逐个询问是否存在，**不做字段枚举**：该运行时没有接字段名 getter，靠猜偏移报出来的字段位置等于伪造证据。
- 探针默认关闭，不进入正常会话：只有设置进程把 `BETTER_ENDFIELD_FP_LOOK_PROBE=1` 传进游戏进程时才注册，且 `JNI_OnLoad` 的模块判定也会把它算作一次请求（否则没有任何真模块配置时 IL2CPP worker 根本不会启动）。设置侧只有 debug 构建读 `debug.betterendfield.fp_look_probe`，release 构建则接受进程环境里的同名变量，因此验收用的 release 包也能探，不必为探针单独装一个 debug 包。探针运行时把枚举写到游戏 `cache` 目录下的 `betterendfield-fp-look-probe.log`。
- 桌面侧同名研究目标 `native/research/fp_look_probe/` 保留（走 `EXCLUDE_FROM_ALL`，不进 Android 产物），但它依赖桌面宿主的模块目录与 `.module.ini` 扫描，**该部署方式对 Android 完全不适用**——Android 不编译 `native/shared/host/`，模块是静态编入并按环境变量选择的。
- `versionName=3.3.22-alpha.13`、`versionCode=30322`。反转对调部分待实机确认；第一人称陀螺仪本身尚未实现，取决于探针结果。

## 3.3.22-alpha.12：修「陀螺仪仍无反应」与「停步后人物朝向固定方向」

### 陀螺仪仍无反应：设置读的是另一个包的文件

- alpha.11 把 `free_camera_mouse_look` 写成了 `true`（该修复确实进了包，日志里能看到），但实机依旧没有任何 `gyroscope` 日志行。零日志本身就是线索：`startGyroscope` 每条路径都会打印，**唯一不打印的分支就是 `!gyro.enabled()` 的提前 `return`**。
- **根因（实测）**：设置读的是 `ModuleSettings.getFirstPersonGyro(context)` → `preferences(context)` → `FrameworkSettings.open(context)` → `context.getSharedPreferences("module_settings", MODE_PRIVATE)`。`MODE_PRIVATE` 打开的是**该 context 自己所属包**的文件。在游戏进程里 context 是**游戏的** Application，于是打开的是游戏自己的 `module_settings`——那个文件没有任何设置页面会写，七个键全部读成默认值，`enabled=false`，静默返回。
- 设置进入游戏进程的**唯一**通道是框架远程首选项 `XposedService.getRemotePreferences("module_settings")`（`FrameworkSettings.publish()` 已把本地快照镜像过去）。跨进程读私有文件在设计上不成立：两个包 UID 不同，`/data/user/0/<pkg>` 是 0700。
- **修法**：`ModuleSettings` 增加 `readFirstPersonGyro(SharedPreferences)` 重载，读取调用方已持有的快照；`XposedEntry` 把 `getRemotePreferences("module_settings")` 这份快照沿 `prepare` → `load` 一路传到 `startGyroscope` / `refreshGyroscope`。保留 `getFirstPersonGyro(Context)` 供设置进程使用，其语义在那里是正确的。

### 停步后人物朝向固定方向：`held_yaw` 被冻在世界坐标里

- **根因（实测，非推测）**：面朝状态机站立分支的原文是
  `held_yaw = view_yaw - clamp(remainder(view_yaw - held_yaw, 360), -limit, limit)`，
  然后只**反推** `lateral_yaw = held_yaw - view_yaw`，并重置 `target = 0`。
  问题在于它**从不把裁剪后的值写回 `lateral_yaw`**。于是站立期间视角继续转动时，`lateral_yaw` 停在原地不动，`held_yaw` 的参考点随视角一起漂走，直到偏置饱和、`held_yaw` 被彻底钉死在一个绝对世界朝向上。
- 用本机 MSVC 编译原生测试直接复现（视角每帧 +5°，走完后停止）：

  ```
  walk end   view= 80.0  yaw=125.000
  stop f0    view= 85.0  yaw=125.000     <- 之后每一帧都是 125.000
  stop f5    view=110.0  yaw=125.000
  ```

  人物**冻结在停步瞬间的世界朝向**、完全不再跟随视角——这就是「自动朝向一固定方向」。`target = 0` 让下一次起走从那个冻结值缓动回来，于是又表现为起步时的突跳。
- **修法**：站立分支改为让偏置保持在**相对视角**的坐标系里，并按与起走相同的 0.35 s 时间常数**释放到零**。这样停步会保持走路结束时的姿态，再平滑回到视角朝向，下一步从零开始、无值可突跳。
- 释放速率不能沿用起走分支的 16/s：60 Hz 下它在**第一个 16 ms 帧就抹掉 45° 偏置里的 34.8°**，是把突跳换了个方向。已改为 `1/0.35`，实测首帧只移动 2.0°。
- **测试**：原生 `first_person_facing_tests` 有两条断言其实在**固化这个缺陷**（`standing side look within the limit must keep body yaw` 期望绝对 `yaw==0`、`crossing the yaw wrap` 期望相对 `179` 的差值为 0）。二者都只在视角为 0 / 恰好 179 时成立，本质是在断言「身体冻结在世界坐标」。已改为断言**相对当前视角的偏置**，并新增走路→停止→继续转视角的回归序列。全部通过（见下）。

### 验证

- 原生 `first_person_facing_tests`：全部通过（含新增的「停步必须衰减偏置并跟随视角，不得冻结」与「起步不得从陈旧偏置突跳」）。本机用 `F:/code` 的 MSVC 14.51 编译运行，`BUILD_OK` / `EXIT=0`。
- Android `:app:compileReleaseJavaWithJavac` 通过。
- `versionName=3.3.22-alpha.12`、`versionCode=30322`。**仍未实机验收**：陀螺仪轴符号、灵敏度手感、无设备降级路径，以及停步释放的手感（0.35 s 是否过长）都需实机确认。

## 3.3.22-alpha.11：修「陀螺仪开着却没反应」

- **根因（实测）**：`camera_config` 里写的是 `free_camera_mouse_look=false`。这个键**同时**管两件事——Windows 上挂不挂低级鼠标钩子，以及 `StepFreeCamera` 要不要把 `g_mouse_dx/dy` 折进视角。手机上没有光标可挂，所以当初按「不挂钩子」的意图把它写成了 `false`，但代价是连面板拖拽与陀螺仪的增量一起被丢掉了：增量经中继正确到达替身层累计器、也被 `FoldPanelLookInput` 折进了 `g_mouse_dx/dy`，最后在 `StepFreeCamera` 里被跳过。**写 `true` 不会挂出任何钩子**——钩子只在 `_WIN32` 下编译（`#if defined(_WIN32)` 包住 `FreeCameraMouseHook` 与整个 capture 分支），Android 上那个指针项本来就是零。
- 交付版 alpha.10 的实机日志即为此证：`Free camera enabled` 已在（自由相机确实起来了），但按下播放键后没有任何视角变化，因为开关一开走的就是这条被跳过的路。
- **副根因**：陀螺仪开着而自由相机没开时，`StepFreeCamera` 根本不被调用（`ApplyFreeCamera` 由 `g_free_camera_active` / `CameraManager::TailLateTick` 守卫），改配置也不会把 `free_camera_enabled` 置真。现改为：陀螺仪开关打开时，同一次写入把自由相机一并置真，`any` 也随之成立，配置才会真的落到磁盘上（否则 `any=false` 会把整段配置清空）。设置页的开关门控同步放宽为 `freeCamera || firstPerson || gyroscopeEnabled`，否则「要用它得先开另一个开关」而那个开关又依赖它。
- 无陀螺仪设备的降级路径不变：只写一行日志。`versionName=3.3.22-alpha.11`、`versionCode=30322`。**构建与门禁通过，仍未实机验收**。

## 3.3.22-alpha.10：陀螺仪视角输入

- **新增陀螺仪作为视角输入源**（「第一人称」页面新增「陀螺仪」分组）。在**游戏进程内**注册 `SensorManager` 的 `TYPE_GYROSCOPE`（约 200 Hz，`maxReportLatencyUs=0` 不做 batching），把转动手机变成视角输入，与触摸/拖拽走**同一条**增量通路。按文档要求「陀螺仪只是输入源」——它不新建第二套相机状态，也不直接写 `CameraState`。
- 必须说明的一点：本仓库的第一人称**没有**模块自持的 `localYaw`/`soft_limit`/`hard_limit`/身体跟随代码（全仓检索 0 命中）。第一人称是**游戏原生**的 `snapshot.is_first_person` 相机模式，模块在 `PushStateToUnityCamera` 钩子里**只改眼位与视野、朝向原样保留**（注释原文：orientation is kept exactly as the game produced it）。因此陀螺仪复用的不是一套模块侧限位，而是既有的**输入通路**：它产出的增量与面板拖拽完全同源，下游的灵敏度、反转、俯仰夹取、播放期间忽略转向全部是原桌面代码路径。**这样做也意味着陀螺仪与触摸共用同一份视角状态**，不会出现文档 §3.1 警告的两套角度失配。当前该输入驱动的是**自由视角**；原生第一人称的 Look 输入注入点是后续独立课题，未在本次改动内。
- **积分用传感器时钟，不用帧时钟**：传感器约 200 Hz 而游戏 60–120 FPS，一帧内有 3–4 个样本。若按帧 `deltaTime` 积分，既会丢掉读数之间的样本，又会让响应随帧率变化。每个样本按 `SensorEvent.timestamp` 积分，只把累计量交出去；单样本 `dt` 上限 50 ms，兜住挂起恢复后的时间戳跳变。
- **亚像素累积**：200 Hz 下缓慢转动每样本不足一个像素，逐样本取整会把输入抹平。累计在浮点像素域，只输出整像素、小数留给下一次；超过 5 ms 的间隔不会让画面跳。
- 坐标映射按**屏幕旋转**选择（横屏 `ROTATION_90`/`ROTATION_270` 各一套轴对应），并按文档要求提供**水平/垂直反转**作为设备差异的出口；另有**死区**（默认 0.002 rad/s）与**稳定处理**（一阶低通，默认 0.08，作用于角速度而非累计角度）。
- **生命周期**：传感器只在**游戏进程**、且原生运行时已加载后才注册（此前没有相机模块可驱动、也没有中继承载增量）；关闭开关会 `unregisterListener` 并清零累计量与时间戳。设置页每次写相机配置（陀螺仪与相机同屏同事务提交）都会重新读取存档并**热启停**传感器，无需重启游戏。无陀螺仪的设备会写一行日志说明，而不是静默失效。
- 启动配置补 `first_person_gyro_*` 七个键；`ModuleSettings.FirstPersonGyro` 单一记录类承载归一化与默认值，夹取范围与 `GyroscopeController` 接受的区间一一对应（灵敏度 0.2–5.0、死区 0–0.25、平滑 0–0.9）。陀螺仪**不**单独触发相机模块加载（它驱动的是已有相机模式的输入），页面据同一条件置灰。
- Android `versionName=3.3.22-alpha.10`、`versionCode=30322`（与 alpha.1 ~ alpha.9 同值）；桌面端仍为 3.3.0。**构建与门禁通过，尚未实机验收**：陀螺仪轴符号、灵敏度手感、无设备降级路径均需实机确认。

## 3.3.22-alpha.9

- **相机参数不再需要重启游戏才生效**（方案里原本列为「可选」的 P4，本轮立项并完成）。此前整份相机配置是**启动时读一次**的：设置应用把它写进 `BETTER_ENDFIELD_CAMERA_CONFIG` 环境变量，游戏进程在加载原生库之前读一次，之后改任何滑杆都要退出游戏重进。现在保存设置页的任何相机项都会**当场投递给正在运行的游戏进程**——运镜速度、视野、转向灵敏度这类需要看着画面调的参数终于能边看边调。
- 通路复用 alpha.6 打通的跨进程链路：设置应用写 LSPosed 远程文件空间 `command.next` → 游戏进程每 250 毫秒轮询 → 单槽命令泵 → 引擎 tick。新增的只是「相机配置」这一类命令：整段配置文本作为一个 payload 投递，游戏进程在下一个主线程 tick 把它交给原生模块**整段重放**。
- 之所以整段重放而不是逐键命令：原生配置入口 `ConfigurationChanged` 本来就是全量、幂等的——设置页保存时生成的就是一份完整 ini，逐键下发反而多一层状态同步，还会漏掉那两处「非赋值转换」（自由相机 / 第一人称由 true→false 是请求退出，不是赋值）。整段重放复用的就是启动路径本身，因此不存在「热改的键集与启动的键集不一致」这种漂移。改动全部在非 Windows 分支，**共享桌面源一行未改**，Windows 侧行为完全不变。
- 首次启用相机功能仍要重启一次，这是不可消除的边界：模块是否被载入游戏进程由**启动配置**决定，功能从全关变成开启时原生库根本没进进程。所以只有「从无到有」那一次要重启；此后所有改动都即时生效，包括运行中导入 `.vmd`。
- 运行中导入 `.vmd` 也走同一条链路：游戏进程在把配置交给原生之前，先把远程文件空间里的 `vmd.current` 物化到自己的目录（长度一致则跳过）。物化只能由游戏进程做——设置应用与游戏是两个 UID，前者写不进后者的数据目录。大文件物化会占住轮询线程一小段时间，其间其它命令排队。
- 顺带修了三处此前只有静态证据、这次才暴露的问题：① 命令泵是**单槽**的，谁先读谁取走，两个模块同载时新命令会被旧消费者吞掉并 Ack——改为按名取用；② 远程文件空间写入**不截断**，短 payload 会留下旧尾巴，对配置这类文本是致命的（旧的 `key=value` 排在后面会胜出）——写入前截断到 0；③ `movement_speed` / `field_of_view` / `first_person_fov` 三个顶层标量此前**没有夹取**，非有限值会被原样渲染成字面量 `NaN` 写进 ini——现按原生上下限夹取（0.5–100 / 20–120）。
- 命令载荷上限从 512 字节放宽到 4096 字节：一份完整相机配置约 1.3 KiB；与游戏进程侧新的「按名取用」配套。
- Android `versionName=3.3.22-alpha.9`、`versionCode=30322`（与 alpha.1 ~ alpha.8 同值）；桌面端仍为 3.3.0。本预发布 APK 由本机构建、固定 release 身份签名，体积 8,702,192 字节，SHA-256 `854DE97ADD1E1A50A58125310ED577724D2CB03DB63417B787B4ADDD28E98EF3`；相比 alpha.8 大 3,836 字节。签名身份与已发布的 alpha.3 一致（证书 SHA-256 `8CD6FDC15038530E101668AB4B3CCD0030AE88AE37153E6D66AA45930C7B8EFD`），可原位覆盖 alpha.2 及之后的任何版本——**从 alpha.8 升到这版不需要卸载**。
- 验证：release 构建通过含五类断言的 `verifyReleaseEntryPoints` 门禁；`assembleDebugAndroidTest --rerun-tasks` 通过；本版**有原生改动**，逐项核对到产物里：APK 内 `libbetterendfield_android.so` 含 `AcquirePanelCommand` / `AcknowledgePanelCommand` / `AcquireRuntimeCommand` 与 `camera_config` / `reloaded from the settings app` / `without restarting the game`，dex 含 `camera_config`，资源表含 `camera_settings_live` / `camera_settings_not_delivered` / `camera_settings_unchanged` 三条新字符串及改写后的「保存即生效 / 首次启用需重启」文案。**设备外证据**（`F:/tmp/p4verify`，真实 `ModuleSettings` 跑在 JVM 上）：三组配置各 53 行、最长 1347 字节、投递 payload 1334 字节（对 4096 上限余量充足），键名差集（写了但原生不认）为**空**，重复写同一份配置时 `changed=false delivered=false`（不投递）。
- 未上机：热重载本体（保存 → 游戏内参数当场变化）尚无设备级证据；「相机功能全关」的会话里引擎 tick 是否仍跑，只有静态推断；大 `.vmd` 运行中物化对轮询线程的阻塞时长未测。

### 2026-09-30 第一人称相机收回实验验收（非 alpha.9 发布物）

- 第一人称头部隐藏扩展到名称未命中的头骨附着配件：蒙皮部件要求骨板全部属于头骨子层级，普通 Renderer 要求对象在该层级；混合躯干骨板保守跳过。复用原有隐藏开关、仅投射阴影租约及退出恢复路径，不依赖 GPU 网格回读。离线测试与 Android release 构建通过，具体造型和恢复待实机验收。
- 终结技施放、剧情镜头与非主关卡相机期间临时交还游戏原生视角；结束后自动恢复第一人称，保留用户开关。使用当前技能与终结技 ID、角色剧情状态以及相机管理器当前/主关卡控制器的元数据合同采样，不改游戏相机状态。终结技与角色界面的收回和自动恢复已有用户侧实机通过反馈，具体运行时触发路径尚无日志独立复核。
- 离线策略测试、Windows Camera Release 与 Android debug APK 编译通过；用户于 2026-09-30 回复“验证通过”。设备、所装 APK 哈希、画面、运行日志及重复轮次尚未提供，不将此反馈扩大为其他功能或完整 S0–S8 验收。当时构建含工作树中其他尚未提交的 alpha.9 修改，不作为独立发布版本。

## 3.3.22-alpha.8

- **触摸转向（原方案缺口 C）补齐**：自由相机在手机上不再只能平移——面板新增「镜头转向（拖动）」区，按住拖动即转视角，右滑右转、上滑抬头。此前 `g_mouse_dx/dy` 在 Android 恒为 0（鼠标钩子只在 Windows 下编译），相机能移动、升降、滚转、变焦但**不能转向**，运镜构图实际上没法用。
- 转向走三段链路，**共享的桌面源一行未改**：面板拖拽增量 → 中继新行 `m <dx> <dy>`（屏幕像素，x 向右、y 向下，与钩子产出的坐标同向同单位）→ 替身层两个原子累计器求和 → 相机模块在每个主线程 tick 把它们折进 `g_mouse_dx/g_mouse_dy`。所以下游全是原本的桌面代码路径：灵敏度、Y 轴反转、俯仰夹取、播放运镜期间忽略转向，都没动。Windows 侧行为完全不变（那条折叠在 `#if !defined(_WIN32)` 里）。
- 增量**求和而不是排队**：面板每 16 毫秒把这段时间的拖拽合并成一行写入（原生侧每 10 毫秒读一次），一次拖拽的总量不变，文件写入从每秒上百次降到几十次；合并时**只取走整数部分、小数留下继续累**，否则比「每 16 毫秒半像素」更慢的拖拽会被反复四舍五入抹平，按多久都不动。进入自由视角本来就调用的 `ClearMouseInput()` 顺手清掉累计器，进入前随手拖的几下不会被当成开机第一下。
- 「体验 → 运镜与镜头」页新增**转向设置**组：灵敏度滑杆 0.02–0.5 °/像素（默认 0.1，与配置记录的夹取范围一致）与 **Y 轴反转**开关。它们写的就是桌面端那两个键 `mouse_sensitivity` / `mouse_invert_y`，此前 Android 侧只写默认值、没有入口可调。
- Android `versionName=3.3.22-alpha.8`、`versionCode=30322`（与 alpha.1 ~ alpha.7 同值：versionCode 只编码到 3.3.22 这一档，不编码预发布序号）；桌面端仍为 3.3.0。本预发布 APK 由本机构建、固定 release 身份签名，体积 8,698,356 字节，SHA-256 `B3D4E98D770EEA87C19A853D2F6C32C8E53B9BF28FE69A59A422E7582D5E1C0E`；相比 alpha.7 大 7,960 字节（这一档第一次动原生：替身层累计器 + 中继新行 + 相机模块的折叠调用，其余为新组件与文案）。签名身份与已发布的 alpha.3 一致（证书 SHA-256 `8CD6FDC15038530E101668AB4B3CCD0030AE88AE37153E6D66AA45930C7B8EFD`），可原位覆盖 alpha.2 及之后的任何版本——**从 alpha.7 升到这版不需要卸载**。
- 验证：release 构建通过含五类断言的 `verifyReleaseEntryPoints` 门禁；`assembleDebugAndroidTest` 通过；本版**有原生改动**，因此逐项核对到产物里：中间产物 `libbetterendfield_android.so` 由本次构建重编（07:01:31），APK 内该库含 `AddVirtualMouseDelta` / `DrainVirtualMouseDelta` 两个全局符号（`FoldPanelLookInput` 是内部函数，被 strip 掉属预期，未 strip 的中间产物里在），dex 含 `look deltas rejected (relay not configured)`、`look delta failed:` 与面板文案「镜头转向（拖动）」，资源表含「转向灵敏度」「°/像素」「Y 轴反转」。**实机可核对的判据**：拖动转向区时相机连续转动（右滑右转、上滑抬头），时间冻结中仍可转向（此时靠 `Cinemachine is not pushing; writing the camera transform` 心跳写回），播放运镜期间拖动无效，进自由相机前随手拖的几下不会在开机瞬间甩镜头。

## 3.3.22-alpha.7

- 悬浮窗的三个「播放」按键改为**先收起面板、等一秒再触发**：预设运镜（播放 / 停止）、回放关键帧、VMD 镜头播放。这三个键的意义全在相机接下来怎么动，而面板占掉约三分之一画面，点击又发生在手指尚未离开屏幕之时——按键在点击瞬间发出，运镜就会带着控件一起开拍。等待固定 1 秒，从点击那一刻算起（面板淡出约 100 毫秒，其余留给手离开屏幕）。
- 这一秒里 **handle 也一起消失**：它是浮在游戏画面上的一个 50 dp 方块，同样会被录进镜头。收起之后屏幕上不再有任何悬浮窗元素。
- **音量键是隐藏期间唯一能按的东西**，也是唯一的打断手段：按音量键会把还没发出的按键丢掉（运镜不开拍），或把原生模块报告「正在播放」的那次运镜停掉，然后把 handle 唤回。三个播放键在原生侧本来就是**开关**（再按同一个键即停止），所以「停止」用的就是当初发出去的那个键；而预设运镜、关键帧循环与 VMD 循环都可能永远不结束——没有这个出口，一次循环运镜就只能重启游戏才能脱身。反过来，没有运镜在跑时，音量键只把 handle 唤回。
- **运镜播完 handle 自己回来**，不用再按音量键。判据是原生模块的 stopped 行（播完、被切换、被自己的热键停掉、退出自由相机都记），handle 淡回原处、面板保持收起——展开面板仍然是用户自己的一次点击。
- 「正在播放」是**照原生模块自己的日志判定的，不是猜的**：模块开始播放记一行 started、结束记一行 stopped（播完、被切换、被自己的热键停掉都记 stopped），悬浮窗读到 started 才认为一次运镜在跑。按键发出但原生没能开拍时（例如 VMD 打不开、不在自由相机里）不记 started，因此音量键不会去「停」一次从未开始的播放，也不会把一次已经播完的运镜重新启动。
- 按键发出后 **3 秒内原生一句 started 都没有**，就当作「它没东西可播」把 handle 唤回：不在自由相机里按预设、关键帧不足两帧、VMD 装载失败这几条路都**只返回、不记任何行**，没有这个兜底，悬浮窗就会一直挂在隐藏态，而用户并没有理由去猜音量键在这里有用。原生日志由本进程每 250 毫秒尾随一次，3 秒是这条通路延迟的十二倍。
- 音量键由 Xposed 钩住 `Activity.dispatchKeyEvent`（外加宿主 Activity 自己的那次覆写，因为覆写未必调 super）转发给悬浮窗。事件**不被吞掉**：手机音量照常变化，转发只做加法，永远不会从游戏手里抢走一个键；同一次按键被两个钩子各看到一次也安全——第二遍已经没有状态可处理。
- 其余控件一律保持按下即生效。它们是需要看着画面实时微调的调整项（移动、升降、滚转、变焦、记录关键帧、清除关键帧、视角回正），延迟只会让它们难用。
- 等待挂在前台控制器自己的主线程 Handler 上，**不挂在面板的 Compose 作用域上**。面板收起正是「这份组合是否还活着」不再值得依赖的时刻：把等待交给一个随面板一起被取消的作用域，按键会被无声吞掉，日志里也不会留下任何痕迹。放在控制器上还顺手得到取消语义——一秒内再次点击三个播放键中的任意一个会**替换**待发的那个键而不是排队两次；重新展开面板、按下音量键、游戏切到后台、悬浮窗随 Activity 销毁被拆掉，都会取消待发的键。handle 的归位现在有四条路：原生报告运镜结束、按键发出后 3 秒内原生没有开播、按音量键、把游戏切到后台再回来；前两条是自动的，后两条在用户手里。四者都走同一个幂等的「结束隐藏」出口，重复触发不会重复动作。
- Android `versionName=3.3.22-alpha.7`、`versionCode=30322`（与 alpha.1 ~ alpha.6 同值：versionCode 只编码到 3.3.22 这一档，不编码预发布序号）；桌面端仍为 3.3.0。本预发布 APK 由本机构建、固定 release 身份签名，体积 8,690,396 字节，SHA-256 `1628D2AB01F49AAB187EAC14F3E1774BEE198D534D5813B63CA904C5E2BEABF4`；相比 alpha.6 大 6,696 字节。签名身份与已发布的 alpha.3 一致（证书 SHA-256 `8CD6FDC15038530E101668AB4B3CCD0030AE88AE37153E6D66AA45930C7B8EFD`），可原位覆盖 alpha.2 及之后的任何版本。
- 验证：release 构建通过含五类断言的 `verifyReleaseEntryPoints` 门禁；`assembleDebugAndroidTest --rerun-tasks` 通过；dex 中 14 条新字面量（三条 started、两条 stopped 判据、`no take reported within`、`overlay handle restored` 等）全部检出。本次改动只有 Java/Kotlin（日志观察者、音量键转发、控制器状态机），原生段未动。延迟、超时与按键都是交互时序，没有可静态验证的产物证据，只能上机看；alpha.6 的 VMD 链路已由用户实机验收通过（导入 → 远程投递 → 游戏进程物化 → 播放），本版只改按键发出的时机、隐藏期间的出口与 handle 的归位时机。

## 3.3.22-alpha.6

- VMD 镜头投递链打通：设置应用的「体验 → 运镜与镜头」子页现在可以导入 `.vmd`（MMD 相机轨道）文件，游戏进程在加载原生库之前把它放到自己的数据目录，配置里的路径随之生效。此前 `vmd_camera_file` 只能写空值，`vmd_camera_scale` / `vmd_camera_fov_bias` / `vmd_camera_loop` 三项虽可编辑却没有作用对象。
- 导入在设置应用内完成并**先校验后发布**，校验规则与原生装载器 `LoadVmdCamera` 完全一致：文件非空且不超过 64 MiB，前 30 字节以 `Vocaloid Motion Data 0002` 或 `Vocaloid Motion Data file` 开头。这样选错文件会当场说明原因，而不是进了游戏再拿到一句不知道是哪一步拒绝的日志。
- 跨进程投递走 **LSPosed 框架的远程文件空间**（名称 `vmd.current`），与 BEM 包管理器同一条通道。这里更正了本阶段方案文档最初的一处错误假设：原计划写的是「设置应用存进自己的 filesDir，游戏进程再从中拷贝」。这在 Android 上不成立——设置应用与游戏是两个 UID，`/data/user/<uid>/<pkg>` 是 0700，游戏进程拿到的只是路径字符串，读不到文件；`getFilesDir()` 返回的路径本身没有任何跨应用能力。方案文档第 2 节已就地更正并保留记录。
- 游戏侧物化与路径展开：`RuntimeBootstrap` 在加载原生库之前把 `vmd.current` 拷到 `files/betterendfield/camera/current.vmd`（长度与设置侧登记值相同则跳过，写入走 `.tmp` → `Os.rename`，超过 64 MiB 即中断），随后在 `setenv("BETTER_ENDFIELD_CAMERA_CONFIG")` 之前把配置里的 `%files%` 展开为游戏自己的 files 目录。路径只能由游戏进程拼：设置应用既不知道游戏会以哪个用户或分身身份启动，也无法在另一个 UID 的数据目录里创建文件；而原生侧 `CreateFileW` 在 Android 上就是 `open(path, O_RDONLY)`，相对路径按进程工作目录解析，因此必须是绝对路径。
- 清除导入会把 `vmd_camera_file` 写回空值，原生侧因此走既有的「未配置文件」分支，而不会去打开一个必然不存在的路径。
- 悬浮窗「运镜 / 关键帧」区新增「VMD 镜头 播放 / 停止」按钮，**只在已导入 `.vmd` 后出现**（`OverlayFeatures.vmdCamera = 自由视角已启用 && 已导入`）。这是 alpha.5 有意推迟的那一项：此前的版本里这个按钮唯一的效果是打印「未配置文件」。按下该键会在自由视角未开启时自动进入自由视角（原生已有行为）。
- 顺带说明一处未改动的地方：`mouse_invert_y` 与 `mouse_sensitivity` 仍写默认值且无 UI。手机仍无转向输入，`g_mouse_dx/dy` 恒为 0，这两项在下一阶段（触摸转向）之前写什么都无效果。
- 本机验证（不含实机）：release 构建通过含五类断言的 `verifyReleaseEntryPoints` 门禁；`assembleDebugAndroidTest` 通过；产物 dex 中按名检出全部新增字面量（`%files%/betterendfield/camera/current.vmd`、`vmd.current`、`camera_vmd_imported`、`camera_vmd_bytes`、两种 VMD 文件头、目标路径）；用真实的 `ModuleSettings` 在 JVM 上直接生成 ini，默认（空值）与已导入（占位符）两种状态以及越界夹取、非有限回退的输出均与原生默认一致，`isVmdMotion` 对 `0002` / legacy `file` / `0001` / 缺 `0002` 的 `Vocaloid Motion Data` / 随机内容 / 29 字节 / `null` 七组输入的判定结果与 `LoadVmdCamera` 一致。
- 未上机：远程文件空间的实际投递、`%files%` 展开后 `open()` 是否成功、以及 VMD 朝向/缩放/FOV 在 Endfield 场景的标定（0.07 / +5° 取自 MMD 场景，预期需微调）都还只有静态证据。instrumented 断言（新增占位符、清空、元数据与文件头）**编译通过但未执行**。
- Android `versionName=3.3.22-alpha.6`、`versionCode=30322`（与 alpha.1 ~ alpha.5 同值：versionCode 只编码到 3.3.22 这一档，不编码预发布序号）；桌面端仍为 3.3.0。本预发布 APK 由本机构建、固定 release 身份签名，体积 8,683,700 字节，SHA-256 `49A783BDA944B30EC05B80A65EBF6069AF25CA1225EA12172CA3CB823E0012E5`；相比 alpha.5 大 6,632 字节，其中含新类与新文案，也含本机包与 CI 包原生段必然存在的差异（内嵌源码路径不同，两者 `.so` 从不逐字节相同）。签名身份与已发布的 alpha.3 一致（证书 SHA-256 `8CD6FDC15038530E101668AB4B3CCD0030AE88AE37153E6D66AA45930C7B8EFD`），可原位覆盖 alpha.2 及之后的任何版本。

## 3.3.22-alpha.5

- 运镜（高级运镜预设 / 关键帧 / VMD 镜头）在 Android 上补齐配置面。此前 Android 的相机配置只写开关与热键，13 个运镜参数一个都没写：原生模块只能按默认值跑「环绕」，`dolly_zoom`（希区柯克变焦）/`crane`（升降）/`truck`（横移）三种预设没有任何入口，关键帧段长、VMD 位移缩放与视野偏置也无从修改。现在这些值由设置页写入，并新增「体验 → 运镜与镜头」子页集中编辑。
- 实现方式是为 `ModuleSettings` 增加一个 `CameraMotion` 记录，与既有的 `FirstPersonAdvanced` 同构：构造时按原生模块的同一组上下限夹取（速度 ±20、环绕角速度 ±180°/s、时长 0–600 秒、锚点高度 ±5、关键帧段长 0.2–60 秒、VMD 缩放 0.001–10、VMD 视野偏置 ±60°），非有限值回退默认，预设名归一到 `ParseMotionPreset` 认得的四个名字，再统一序列化成 ini 行与偏好键。夹取范围与 `native/modules/camera/module.cpp` 的 `std::clamp` 一一对应，因此设置页接受的值不会在进游戏时被二次改动。预设名同时接受原生解析器认的两个短别名 `dolly` / `pan`——不认它们的话，任何从别处写进偏好的等价预设在设置页保存时都会被静默改写成「环绕」。
- 移动端仍不提供转向输入，因此 `mouse_invert_y` 与 `mouse_sensitivity` 继续写默认值、暂不暴露到设置页。`g_mouse_dx/dy` 在 Android 恒为 0（鼠标钩子整段只在 Windows 下编译），自由相机因此能移动、升降、滚转、变焦，唯独不能转视角；先放出没有实际作用的滑杆只会被读成「坏了」。屏幕拖拽转向待后续阶段。
- `vmd_camera_file` 现在显式写成空值而不是省掉该键。Android 还没有把 `.vmd` 送进游戏进程可读路径的通道，写空值会让原生侧走「未配置文件」这条既有分支并留下日志，而不是沿用桌面端可能残留的路径。VMD 的三项参数已进设置页，导入入口待后续阶段。
- 悬浮窗「运镜 / 关键帧」区的广角/长焦由 180 毫秒脉冲改为按住。桌面端这两个键是按住生效，180 毫秒只步进约 3.6°，与键的语义不符，也取不了景。
- 顺带修复仓库自 3.3.22 Compose 重写以来一直无法编译的 instrumented 测试源集：`CameraSettingsTest` 仍在调用那一次重写中删除的 `ValueSlider`。该源集不在 CI 的构建路径上，所以一直没有暴露。同时为运镜块补上往返、边界与预设别名断言。
- 本机验证（不含实机）：release 构建通过含五类断言的 `verifyReleaseEntryPoints` 门禁；产物 dex 中按名检出全部 13 个 ini 键与 4 个预设名，并与 `module.cpp` 的解析器逐键交叉核对，无一处拼写差异；用真实的 `ModuleSettings.CameraMotion` 在 JVM 上直接生成 ini（同目录仅桩替换 `Hotkeys` 与 `FrameworkSettings` 两个与 ini 生成无关的协作类），默认值、越界夹取、非有限回退与预设归一化的输出均与原生默认一致。
- 未上机：设备侧只完成签名与版本核对，instrumented 断言未执行——`HLK-AL00` 上装的是 3.3.21（旧的临时 debug 身份，证书 SHA-256 `62713BA05E66F3A7E05747F3E63F04C60F05B339D7DD4CFCA5834DAF46B9A096`），与原位覆盖不相容，需先卸载；本机 release 身份与已发布的 alpha.3 完全一致（`8CD6FDC15038530E101668AB4B3CCD0030AE88AE37153E6D66AA45930C7B8EFD`），可原位覆盖 alpha.2 及之后的任何版本。运镜本体在 Windows 侧同样尚未实机验证，因此这是双平台首次上机验证；逐阶段验收判据见 [docs/CAMERA_MOTION_ANDROID_PLAN_20260930.md](docs/CAMERA_MOTION_ANDROID_PLAN_20260930.md)。
- Android `versionName=3.3.22-alpha.5`、`versionCode=30322`（与 alpha.1 ~ alpha.4 同值：versionCode 只编码到 3.3.22 这一档，不编码预发布序号）；桌面端仍为 3.3.0。本预发布 APK 由本机构建、固定 release 身份签名，体积 8,667,068 字节，SHA-256 `2962EB3600EB0F92341304BEB7353BD05AADC73ACBC147D4849D3C37222BA7F7`；相比已发布的 alpha.3 包大 27,484 字节，其中含新子页与文案，也含本机包与 CI 包原生段必然存在的差异（内嵌源码路径不同，两者 `.so` 从不逐字节相同）。

## 3.3.22-alpha.4

- 修复设置界面换页后不回到顶部：在任一页面向下滚动后切到另一页，新页面会停在上一个页面留下的同一滚动偏移处（也就是页面中下段），而不是从顶部开始。根因是整个设置界面只有**一个**滚动容器（`SettingsShell.kt` 的 `SettingsBody`），八个页面只是在该容器内部按 `state.page` 换内容；容器的滚动状态 `rememberScrollState()` 的调用点从不改变，因此它跨页面存活，把上一个页面停留的偏移量交给了新页面。现在滚动状态以页面为键（`rememberSaveable(state.page, saver = ScrollState.Saver)`），换页即得到一个偏移为 0 的新滚动状态；同一页面内的滚动不受影响，旋转屏幕后的位置恢复也照旧。
- 同一缺陷在 BEM 包管理器与游戏内悬浮窗面板中不存在：前者是独立 Activity 的单页内容，后者不切换页面，各自的滚动容器都不会跨页面复用，因此未改动这两个文件。
- Android `versionName=3.3.22-alpha.4`、`versionCode=30322`（与 alpha.1 ~ alpha.3 同值：versionCode 只编码到 3.3.22 这一档，不编码预发布序号）；桌面端仍为 3.3.0。本预发布 APK 使用固定 release 身份签名，证书 SHA-256 为 `8CD6FDC15038530E101668AB4B3CCD0030AE88AE37153E6D66AA45930C7B8EFD`，可从 3.3.22-alpha.2 起原位覆盖安装。

## 3.3.22-alpha.3

- 修复时间冻结（世界暂停）无法恢复：冻结后同一热键（面板「时间冻结」按钮）再也解不开，只能重启游戏。根因是热键请求只在游戏主线程的泵上排空，而把 `Time.timeScale` 置 0 会让游戏自身的相机更新停摆——`CameraMono._ProcessDitherByPitch` 与 `CameraManager.TailLateTick` 两个泵随之停止，`Time.get_unscaledDeltaTime` 心跳又只在游戏仍读该属性时才有脉冲，于是解冻请求一直排在队里、世界永远停在冻结态（退出自由视角的恢复走同一条泵，因此同样救不回来）。现在模块把同一个泵挂在引擎每帧调用的 `UnityEngine.Rendering.RenderPipelineManager.DoRenderLoop_Internal` 上：该回调由引擎在把帧交给渲染管线时触发，不受游戏时间缩放影响，解冻因此不依赖游戏是否还在跑自己的相机逻辑。该合同按可选处理，解析或挂钩失败时退回原有两个泵，不会让整个相机模块变成不可用。
- 为「按了没反应」补可读判据。输入线程若发现暂停请求超过 1.5 秒仍无人排空，会在日志里记下「no camera pump is running」；每次请求被排空时会记下是由哪一个泵排空的（`render loop` / `unscaled time heartbeat`），设备日志因此能直接判定是「请求没发出去」还是「没有泵在跑」。
- 相机合同汇总行新增 `render_loop=ready|unavailable`，挂钩成功与失败各自留一行日志。
- 本机验证：用 NDK 同配置（`native/shared/android_compat` 的 `<Windows.h>` 替身、`_WIN32` 未定义）对改动后的 `native/modules/camera/module.cpp` 做 clang 语法与类型检查，`-Wall` 无告警；并用同一套检查反向验证过它会报错，确认检查本身有效。游戏内解冻行为仍须实机确认。
- Android `versionName=3.3.22-alpha.3`、`versionCode=30322`（与 alpha.1 / alpha.2 同值）；桌面端仍为 3.3.0。本预发布 APK 由 CI 构建并使用固定 release 身份签名，证书 SHA-256 为 `8CD6FDC15038530E101668AB4B3CCD0030AE88AE37153E6D66AA45930C7B8EFD`，可从 alpha.2 原位覆盖安装。

## 3.3.22-alpha.2

- 发行签名改为固定身份，替代此前「release 复用 debug 密钥」的做法。此前 release 变体直接引用 AGP 内置的 `debug` 签名配置，而仓库里没有提交任何密钥库，所以每次在 CI 上构建都由该 runner **现场生成**一份新的 `~/.android/debug.keystore`——这正是「每个版本的签名证书都不一样、用户只能卸载重装」的根因。现在 debug 与 release 各有一个固定身份：debug 密钥库随仓库提交（debug 证书不构成信任边界，而固定它才能让 debug 包互相原位覆盖）；release 密钥库不进仓库，由 CI 在构建前从仓库 Secret 还原，并在打包后按固定证书指纹断言签发身份，不匹配即报错退出，确保发行包不会悄悄签成别的身份后发出去。
- 发行流水线补上预发布标记。`gh release create` 此前不带 `--prerelease`，会把 `-alpha` 版本登记为正式版并抢占 Latest，使稳定版 `v3.3.21` 从「最新版」入口消失。现在按版本号后缀（`-alpha` / `-beta` / `-rc`）自动标记为预发布。
- 本版为发行基建改动，不含功能改动：代码内容与 `3.3.22-alpha.1` 相同。
- Android `versionName=3.3.22-alpha.2`、`versionCode=30322`（与 alpha.1 同值：versionCode 只编码到 3.3.22 这一档，不编码预发布序号）；桌面端仍为 3.3.0。本预发布 APK 由 CI 构建并使用固定 release 身份签名，证书 SHA-256 为 `8CD6FDC15038530E101668AB4B3CCD0030AE88AE37153E6D66AA45930C7B8EFD`。
- 安装限制：固定身份自本版起生效，但 alpha.2 与此前的 `v3.3.22-alpha.1`、`v3.3.21`、`v3.3.20` 签名证书互不相同，**首次升级仍须先卸载旧版**；从 alpha.2 起，其后的版本可原位覆盖升级。卸载前请自行备份应用数据。

## 3.3.22-alpha.1

- Android 悬浮 Handle 和面板在实验分支改用 Jetpack Compose；保留 Activity 生命周期、热键文件中继、日志导出与原生运行时合同。此版为预发布，不代表游戏实际关卡的交互验收完成。
- 修复直接冷启动时悬浮窗不显示：主游戏包可从 `onPackageLoaded` 注册入口；Compose 的 ViewTree owner 设在宿主上；首次 composition 等宿主和子视图真正附着到窗口后执行。PJX110 上通过强停并直接从启动器冷启动，日志、View 树与截图证实启动画面出现 BE Handle。展开、拖动、触摸透传及实际关卡仍待验证。
- Android `versionName=3.3.22-alpha.1`、`versionCode=30322`；桌面端仍为 3.3.0。本预发布 APK 使用本机构建的调试签名，证书 SHA-256 为 `62713BA05E66F3A7E05747F3E63F04C60F05B339D7DD4CFCA5834DAF46B9A096`；与已发布 `v3.3.21` APK 的证书不同，不能直接覆盖安装。卸载旧版前须自行备份应用数据。

## 3.3.22

- 修复 hook 后游戏闪退（无日志）的第一处根因。3.3.21 把游戏内控制面板从 Java/View 改写为 Kotlin/Jetpack Compose，而面板代码运行在被 hook 的游戏进程里，该进程无法加载任何 Compose 类，一进游戏即崩。面板现已恢复为纯 Java/View 实现（沿用 3.3.20 的实现），配色与文案同步到新版工业黄黑白 token；配套应用与 BEM 包管理器仍为 Compose，两者运行在不同进程，互不影响。
- 修复同类根因的第二处，它在 release 构建里独立存在：R8 优化器把游戏进程内的静态工具类横向合并进一个共享宿主类，宿主类的静态初始化因此带上 `androidx.compose.ui.*` 的构造；游戏进程第一次调用该桥即崩溃，且不留 Java 堆栈。`proguard-rules.pro` 现关闭优化器（`-dontoptimize`，裁剪与混淆保留），release 体积由约 7.0 MB 增至 8.60 MB。该问题与面板是否 Compose 无关——即便面板是纯 View，优化器仍会把依赖塞回游戏进程。
- 构建门禁 `verifyReleaseEntryPoints` 由四类检查扩为五类：新增「游戏进程内执行的类未被优化器折叠」断言。判据取 `mapping.txt` 的足迹形状——被折叠的类有成员行但无类头行，被合法删除的类一行都没有——避免把常量已被 javac 内联的死类误判为缺陷。
- 配套应用、BEM 包管理器与设置页重写为 Kotlin + Jetpack Compose，采用单一底色、面板色与强调色的扁平工业配色；手机用底部标签、≥720 dp 用侧栏，两种布局由同一套组合驱动。
- 发行构建开启 R8（`isMinifyEnabled = true`），为框架入口类、JNI 导出符号与 JNI 回调方法补 keep 规则，并新增 `:app:verifyReleaseEntryPoints` 门禁在打包后核对产物。
- 版本说明：本次仅有 Android 端递进（3.3.21 → 3.3.22，versionCode 30321 → 30322），Windows 桌面端仍为 3.3.0。
- 安装限制：GitHub Actions 的发行包沿用临时调试签名，各版本签名证书互不相同。普通 Android 设备不能直接覆盖升级；卸载旧版前须自行备份应用数据。

## 3.3.21

- 双端设置接入眼位、近裁剪、朝向、动画、对话/战斗让出、过渡和显式外部改模缩头选项；新增能力默认关闭。Android 保存后须强停并重启游戏。
- 原生 Camera 增加有界朝向/动画状态、同步 GPU 读取、独立封口顶点与上传、战斗/对话让出策略、相机过渡和可条件恢复的缩头租约。战斗 getter 依本机元数据核对声明并在运行时校验；带 BlendShape 的网格保守跳过扩点封口。
- 卸载时对同步回读待释放对象和缩头租约增加有界恢复重试；失败会记录原因并释放 GC 引用，真实游戏内的资源/缩放恢复仍待验收。
- 朝向恢复改为精确核对本模块写入的四元数，避免把其他动画随后施加的小角度变化误当成自己的 pose；卸载失败有界重试并记录原因。
- 按当前 Windows 客户端元数据修正朝向跟随的方法声明：移动轴由已不存在的 `get_rawMoveAxis` 改为现有 `get_moveAxis`，角色控制器 getter 的返回类型由 `CharacterController` 修正为 `BaseController`，仍在运行时检查实际派生类型。输入锁定场景与 Android 客户端仍待实机核验。
- 头部部件的仅投射阴影兜底改为条件恢复：第三方更改渲染模式后不覆盖其值；读写失败保留租约并有界重试。
- 封口新增顶点时合并原网格与封口 Bounds，避免只用颈部小范围裁剪身体网格；无效范围会拒绝上传。
- 客户端元数据复核发现当前 Windows 游戏缺少同步读回路径依赖的 `InternalGetData`、`GetIndexBuffer` 和 indexBufferTarget 方法；S4/S5 保持合同阻断，离线构建与测试不计作游戏内功能通过。
- 对话状态采样改用已验证静态字段上的 `il2cpp_field_static_get_value`；管理器确实为空时判为非对话，读取、GC 根或 getter 失败时仍让出游戏相机。修复启用“对话时让出相机”后第一人称持续被 `DialogueUnavailable` 抑制、退出时 `patched frames=0` 的问题。
- 用户在 Android 3.3.21 实机确认除“外部模型缩头兼容”以外的可操作第一人称项目通过；该项尚未测试。S4 同步 GPU 回读与依赖它的 S5 网格封口仍受已记录的接口门禁约束，不能据此声称 GPU 网格路径通过；用户反馈是游戏侧操作结果，不替代逐项运行日志和恢复证据。阶段状态见 [执行记录](docs/CAMERA_FIRST_PERSON_EXECUTION.md)。
- 安装限制：GitHub Actions 的发行包沿用临时调试签名，`v3.3.21` 与 `v3.3.20` 的签名证书不同。普通 Android 设备不能直接覆盖升级；卸载旧版前须自行备份应用数据。用户的实机反馈来自本地 debug APK，GitHub Release APK 已通过独立构建与签名核验，但尚未在游戏中复测。

- 第一人称眼位改用参考实现（RenoDX Endfield Enhancer）的公式：前向偏移沿视线方向的水平投影施加，高度偏移保持世界竖直，因此低头时眼睛向前贴近面部而不再随视线一起下沉。眼睛前移、眼睛高度与近裁剪面三项由硬编码改为配置项 `first_person_eye_forward` / `first_person_eye_height` / `first_person_near_clip`，默认值取参考实现的 0.03 / 0.05 / 0.03。
- 第一人称新增「放宽俯仰范围」（`first_person_extend_look_range`，默认关闭）。开启后俯仰角在游戏自身限制之外继续跟随输入，上/下倍率取参考实现的 1.10 / 1.50，并按 ±89° 夹取；关闭时推给相机的朝向修正保持单位四元数，与改动前完全一致。
- 头部部件判定新增参考实现的角色名规则（`s_actor_` 前缀、`_lod` 段、部件 token，并排除 `shadowproxy`），用于整块隐藏；同时新增身体网格（`_body_`）判定，按顶点权重隐藏环绕颈部开口的一圈三角形——这圈身体几何此前只能靠颈部封口掩盖。原有按名字子串匹配的判定保留，覆盖范围只增不减。
- 以上移植自 RenoDX Endfield Enhancer（作者 ItsTheSewerRat，MIT 许可）。移植范围与署名见 `THIRD_PARTY_NOTICES.md`，差异清单见 [`docs/CAMERA_EE_ADDON_DIFF_20260928.md`](docs/CAMERA_EE_ADDON_DIFF_20260928.md)。
- 版本说明：原生实现是两端共用源码，本次改动同时作用于 Android 与桌面端；版本号仅 Android 端递进（3.3.20 → 3.3.21，versionCode 30320 → 30321），Windows 桌面端仍为 3.3.0。

## 3.3.20

- 修复 Android 端增强功能无法载入或载入后失效。三层根因分别是 JNI 桥的类加载器失配、枚举字面量在部分客户端无法装箱、静态字段的读取结果被共享成功标志一票否决。现在桥在失配时可自愈，第二份模块库副本只提供 JNI 符号、不再重复安装 Hook；枚举常量改走 `System.Enum.Parse` 托管反射而不依赖装箱；静态字段直接读取存储，每个值单独判定。
- 修复面板按键在 Android 上的投递路径。JNI 无法承担该职责：模块库由游戏类加载器加载，而面板的桥接类属于 LSPosed 模块类加载器，Android 禁止同一 `.so` 路径在不同类加载器下二次打开。键码、运行时命令与状态改经游戏自身文件目录中的普通文件中继，原生侧以 10 ms 轮询读入，状态只在变化时重写。
- 游戏内控制面板新增「运镜 / 关键帧」操作区：运镜播放/停止、视角回正、视场角增减、滚转、关键帧记录/回放/清除。面板按下的是与桌面模块相同的键码，并把 `NUMPAD0`–`NUMPAD9` 键名显式写进相机配置——原生解析器本已接受该写法，这里显式钉定是为了不让原生默认值变动悄悄脱开面板按钮——相机配置因此升级为 `schema_version=3`；移动端关闭鼠标视角钩子，因为手机没有可挂的光标。
- 修复切出再切回游戏后悬浮窗被压在游戏视图之下。客户端重建视图时可能既不摘除也不置顶我们的宿主，摘除看门狗因此不会触发；现在按 z-order 判定，并在 `onActivityResumed` 之后延迟复查，必要时重新挂载或置顶。
- 控制面板与配套应用「诊断」页新增运行日志。游戏进程的载入与执行轨迹写入 150 条环形缓冲并经远程 SharedPreferences 暴露，面板提供「保存日志到文件」——使用系统文件选择器写入用户选定位置，不需要存储权限；选择器不可用时退化为分享文本。
- 第一人称新增头发隐藏：GPU 网格补丁覆盖不到的部位（非蒙皮渲染器，或补丁重试已耗尽）改用仅投射阴影的渲染器模式——相机不再绘制该部位，影子保留。该属性经元数据契约读写，因为 Android 的引擎 icall 表只有部分实现、直接调用并不存在；网格补丁引擎初始化失败也不再阻断这条兜底路径。每次进入第一人称会话会记录一次部位树，便于诊断未被命中的部件命名。
- 修复持续冲刺在移动端的断链：`StartSpDash` 的每个失败出口都记录原因；两个冲刺 hash 值逐次独立判定，共享标志不再否决已经读到的健康值；客户端音频 hash 契约与桌面不同时降级为无操作，而不是让整个契约初始化失败；契约偏差一次性汇总上报，不再只暴露第一条。
- 界面模块的契约解析与 Hook 安装日志改为单行汇总，失败项仍逐条记录，避免启动刷屏把关键日志挤出日志环。
- 新增 Android 构建流水线：推送与拉取请求会构建 debug APK 并上传为构建产物。
- 版本说明：本次仅有 Android 端递进（版本号 3.3.0 → 3.3.20，versionCode 30300 → 30320），Windows 桌面端仍为 3.3.0。

## 3.3.0

- 自定义角色外观（BEM）在 Windows 与 Android 双端上线。两端使用同一份标准 BEMv1 包，Android 直接编译 `native/modules/custom_model` 的桌面源码，不存在第二套实现，也不引入游戏偏移。
- 新增「角色外观」页：导入、更新、删除 BEM 包，按角色选择外观，并提供独立 LOD 偏好。同一角色同时只允许一个启用包，重复启用会在界面停用并提示重新选择；包与外观的选择在下次启动游戏时生效。
- 「其他来源 Mod 转换」支持直接读取已解压目录及 ZIP、RAR、7z 源包，无需预先解压或安装解压软件。转换通过完整检查后才允许导出 BEM；未适配的源包输出待适配原因报告。
- 修复登录色带在图集打包 sprite 上的换色失效。Sprite 矩形使用纹理坐标（原点在左下），而 `ReadPixels` 寻址的是活动渲染目标，其纵向原点随图形 API 而定；两者仅在 sprite 占满整张纹理时一致，因此图集子矩形读到的是无关填充区，返回整块不透明色而没有可中和的主题色。
- 补齐 README 的模块与功能清单。此前文档停留在早期版本，缺少自定义外观、相机增强、动作模块与寻访查询四个模块。
- 面向创作者的 BEM 说明同步双端现状，并从 README 直接给出入口。
- Android 移除 legacy API 82 构建变体，libxposed API 102 成为唯一框架入口。
- Android 端新增「转换手机纹理」：手机 GPU 纹理格式与桌面不同，实机显示异常时可按包转换，成功后发布新一代并保留启用状态与已选外观，失败或取消保持当前包不变；缺少已验证法线编码信息的包可导入但无法转换。
- 战斗数据页新增「悬浮窗初始可见性」：悬浮窗随模块启动后可以先隐藏，不必每次进游戏按 F12 关掉。该项只在改变时下发，游戏内用热键切换的状态不会被其他设置的保存动作覆盖。
- 修复安装到非系统盘后，用 Setup.exe 更新可能不覆盖原安装的问题。升级时安装器不再显示目录页，直接沿用上次的安装路径，避免新文件装到默认目录而快捷方式仍指向旧副本、「关于」中版本号不变。
- 安装器版本号改为从 `Directory.Build.props` 推导，与程序集版本不一致时直接中止构建；「关于」页不再保留会过期的硬编码版本号回退值。
- 已知限制：两端切换包或外观后都需要重启游戏，暂不支持运行时热切换。
- Windows 与 Android 版本号统一为 3.3.0。

## 3.2.2

- 修复单文件解包后错误地从临时目录查找注入器，导致注入器与 XInput 代理检测失败的问题；固定路径以启动程序 EXE 所在目录为准。
- 第一人称相机新增头部网格隐藏、颈部缺口填补及对应设置，切换角色或场景后会自动恢复正确状态。
- 修复梨诺悬浮冲刺耗尽后机甲与光效状态未及时恢复的问题。
- 内置注入器固定使用安装目录下的 `loaders/BetterEndfield.Injector.exe`；设置页不再允许覆盖路径，启动、快捷方式、部署和卸载统一使用随软件安装的注入器。
- 发布构建会在打包前清理原生暂存目录，避免其他分支或旧目标的残留文件混入安装包。
- Windows 与 Android 版本号统一为 3.2.2。

## 3.2.1

- 战斗解析和寻访统计使用独立 WebView2 窗口，页面加载后自动接收桌面数据并进入对应页面，无需浏览器访问本地回环地址。
- 使用共享 WebView2 Runtime，保留登录状态；大记录分块传输并逐块确认，原始数据不经过远端中转。
- 冲刺优化各功能默认关闭，包括梨诺“隐藏机甲与光效”；保留用户显式保存的开关选择。
- Windows 与 Android 版本号统一为 3.2.1。

## 3.1.3

- 修复战斗悬浮窗普通 DPS 的技能分类。伤害现在结合运行时 `damageDecorateMask` 与技能 ID 归入普攻、战技、终结技、连携技、被动或其他，并正确处理终结技强化普攻等保留普攻标记的转换形态。
- 寻访界面的“打开网页”按钮改名为“上传云端”，英文同步为“Upload to Cloud”，使按钮行为更明确。

## 3.1.2

- Android 不再硬编码目标包名。模块附着到 LSPosed 作用域里选中的应用（只进主进程），是否真正介入改由证据决定：`UnityPlayer.nativeRender` 必须存在，原生 Hook 全部经 `libil2cpp.so` 导出按名字解析。官服 `com.hypergryph.endfield`、国际服 `com.gryphline.endfield.gp` 与 B 服等渠道包由此共用同一份构建，客户端更新只要托管层类名方法名不变就无需重新适配。
- Android 清单新增 `xposedscope`，LSPosed 作用域页预选官服与国际服包名；渠道包仍需手动勾选。
- 桌面端「在网页中解析」把回环地址改用查询串传递。Toy 正式页把应用装在 iframe 里，只有查询串会被转发进去，URL fragment 到不了服务端也无人转发。
- 网页端分享改为不信任 `isSupport("share")`：该接口在浏览器中返回 true 但调用必定抛出，导致复制链接的兜底路径从未执行。新增分享弹窗，二维码在本地生成（不依赖 SDK），复制依次尝试剪贴板 API、`execCommand`，都失败则选中链接。
- 网页端不再于启动时调用 `getUserProfile`。宿主桥对该接口的守卫是"手机 UA 且不在 B 站 App 内"，命中时会先拉起 App 再抛错，导致手机上打开分享链接直接跳出页面。
- 修复手机端三处：iframe 内 viewport meta 不生效导致浏览器自行放大字号（补 `text-size-adjust`）；时间轴画布 `touch-action` 由 `none` 改为 `pan-y`，纵向滑动不再被吞掉；分享链接的 `?r=` 参数在消费后从 URL 移除，底部导航不再被它一直劫持回记录页。
- 云函数放宽 BEC 快照版本门至 1–2（仅解析 HEAD 层，两版编码一致）。

## 3.1.1

- 修复替换语音的时长修正：Lua 侧调用经 IL2CPP 委托快路径内联，绕过了原入口 Hook；现改挂唯一的时长叶子 `_GetVoDurationFromVoData`，按角色规则直接读取目标语言时长列。角色档案、电台、对话回放、剧情与 Bark 的时长查询全部覆盖。
- 口型加载补充按对白 ID 直接选规则的路由来源，Timeline 剧情不再依赖语音先行触发；移除没有调用方的 `GetLipSyncTrackPath` 重载 Hook。
- 恢复 Windows 端登录角色模型替换：`InitMainPathHash` 在当前客户端被整体内联，改挂 `StringPathHashBinary.InitMain/InitInit`，桌面端与 Android 同源。
- 登录 Logo、色带与两侧色块改为运行时生成去色副本后再套用主题色，最终颜色与所选颜色一致；移除相关已知问题说明。
- 语音页移除"时长修正与口型同步存在问题"的提示。
- Android 编译当前桌面模型源码，新增 `betterendfield.enhancement`（隐藏 UID 水印、关闭近镜头角色抖动）与模型页 HSV 调色盘；Windows 与 Android 统一版本号为 3.1.1。

## 3.0.1

- 更新 Windows 桌面端资源数据以适配当前游戏版本，并统一桌面端与 Android 版本号。
- 当前游戏版本的模型替换存在生命周期兼容问题，本版强制关闭该功能，保留 Logo 与色带主题色设置。
- 配音替换可继续使用，但语音时长修正与口型同步仍存在已知问题，界面已明确提示。
- 发布 Better Endfield Android/LSPosed 正式版，并与后续 Windows 桌面版统一版本号。
- 更新 Android 1.5.3 资源清单，新增提弗洛斯模型、118 个动作和四语角色语音目录。
- Android 模型配置补齐原生循环、强制循环和双 Playable 交叉混合，支持自定义循环区间与混合时长。
- 新增登录 Logo 与色带主题色调色板，同时保留精确 `#RRGGBB` 输入。
- Android 模型和语音资源改为独立生成产物，避免被尚未适配的桌面客户端资源覆盖。

## 2.3.1

- 新增第五个独立模块 `BetterEndfield.UiModule.dll`：在 PC 客户端启用移动端触屏布局，提供虚拟摇杆、技能轮盘和触控控件，面向串流到手机、平板与掌机的场景。
- 移动端布局配套鼠标转触控输入。客户端的触控读取只认 `EnhancedTouch.Touch.activeTouches`，普通鼠标事件不会进入该集合；模块改为注入合成触点，由 Unity 的 Windows 后端识别为真实 `Touchscreen` 设备。鼠标左键即手指，`Ctrl+Alt+T` 开关转换，只在游戏窗口前台时生效。
- 转换按触控签名过滤自身回声，但不过滤 `LLMHF_INJECTED`，因此通过 `SendInput` 投递鼠标的串流客户端同样可用；合成注入需要 Windows 10 1809 或更新版本，系统不支持时只停用转换、保留布局。
- 向账号声明 Android/云游戏平台身份拆分为独立开关，与触屏布局解耦，默认关闭且不由 UI 写入配置。
- 战斗数据新增 rDPS 口径：按单次伤害实际扣血量守恒分配直伤、攻击力、增伤、增幅、脆弱、承伤易伤、减防/减抗、连携增益、法术强度和其他十类贡献。跨乘区按乘数对数权重分配，同一乘区内按实际观测增量分配；角色自身效果保留在直伤，只有其他角色提供且语义已验证的效果才转移贡献。
- 随版本发布 `modules/combat-semantics.besem` 与 `modules/buff-sources.bemap`，提供 Buff、技能、元素和乘区语义。运行时不读取独立更新目录，语义随模块一并升级；构建脚本会校验目录与构建报告的哈希一致性，过期时直接失败。
- 战斗记录升级到 schema 11：只保存可验证的操作、原子结果、队伍快照和会话摘要，排行、技能统计、Buff 区间与时间轴均在读取时派生。不再按时间或 ID 前缀猜测归属，无法唯一验证的来源明确记录为未知；不兼容更早的开发格式。
- 每条记录保存目录版本、验证覆盖率和有界未解析项审计，历史页可直接查看；无法验证的候选项不参与 rDPS。
- 新增副本自动会话开关，进出副本时自动开始与结束统计。
- 战斗统计配置节升级到 schema 2，移除 `record_all_damage`、`include_overkill`、`minimum_damage`、`group_by_*` 和 `save_raw_events`。
- Host 动态解析器：类型名比较统一 `/`、`+` 与 `.` 的嵌套分隔写法；托管字符串读取加上结构化异常保护；静态字段读取不再要求实例指针。
- 纳入独立 Android/LSPosed 工程的角色语音与登录模型模块。该工程仍在开发中，不包含在 Windows 安装器内。

## 2.2.1

- 完成第四个独立模块 `BetterEndfield.CombatStats.dll`：支持隐藏伤害数字、F11 统计会话和 F12 悬浮窗开关。
- 新增独立 Win32 战斗悬浮窗，显示角色头像、总伤害、DPS 与按技能分类堆叠的横向柱状图，可相对游戏窗口拖动定位。
- 伤害分类改为普攻、战技、终结技、连携技、被动和其他，并按实际 `originSkillId` 归类。
- 会话格式升级到 schema 3，以 0.25 秒为桶同时保存技能分类与逐角色时间轴；原始逐击事件仍可独立关闭。
- 重做战斗历史界面：最近三条折叠、日期与最多四角色筛选、记录删除、头像排行、双端点时间范围选择，以及按技能类型/按角色切换的时间轴图表与图例。
- 修复时间轴页在 WinUI `Pivot` 中不显示、切换堆叠方式或操作图表时页面自动滚回顶部的问题。
- 数值单位改为每 10 倍切换一级，提升中等伤害区间的可读性。

## 2.1.1

- 新增第四个独立模块 `BetterEndfield.CombatStats.dll`，提供伤害数字隐藏、快捷键统计会话、可配置聚合规则和本地结果可视化。
- Host 新增 IL2CPP 嵌套类型动态解析，战斗数据模块继续保持无固定 RVA、无客户端哈希依赖。
- 修复退出登录场景时色带换色 Tick 访问已销毁 `Graphic` 导致的崩溃，释放期间改用幂等标记和延迟清理。
- 修复无打包 WinUI 窗口在任务栏、标题栏和 Alt+Tab 中显示默认图标的问题。
- 纳入独立 Android/LSPosed 开发工程；该工程仍在开发中，不包含在 Windows 安装器内。

## 2.0.1

- 发布开屏视觉、模型、语音与 OmniMix 集成的当前稳定版本。
- 已知问题（不会修复）：Logo、色带和色块的颜色可能与选择值存在偏差。原因是主题颜色会与游戏源资源自身的颜色数据叠加；本版本保留原始材质改色逻辑，不再继续处理该偏差。

## 2.0.0

- 重建为 Better Endfield Host + 独立模型模块 + 独立语音模块。
- 统一 C# 与 C++ 命名空间、模块 ABI、配置和发布目录。
- 移除固定 RVA、客户端哈希白名单和旧客户端布局回退。
- 新增 IL2CPP 动态方法/字段解析与 Host 独占 HookBroker。
- 新增内置注入器和可安装、可安全卸载的 `xinput1_4.dll` 自启动方式。
- 删除外部加载器启动编排、`version.dll` 与 `dinput8.dll` 发布路径。
- 优化游戏及注入器路径自动发现，并支持向游戏传递自定义启动参数。
- 新增本机 PCK 语音 Catalog 生成器，支持按角色和目标语言生成。
- B 服通过运行时契约和本机资源目录适配，不依赖官服二进制样本。
