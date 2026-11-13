# EF Link 协议层移植到 Android

结论：**可以移植，但有明确边界。** 房间、连接、报文和会话状态机可以进 Android NDK，并且本仓库已经按这个边界接上了第一段可玩路径。Windows 上的 ImGui、DirectComposition，以及针对 `GameAssembly.dll` 的 IL2CPP 世界/战斗适配不能原样搬过来，必须按 Android 1.6.4（游戏 versionCode 61）重做。

本文件是移植清单。文末「本轮已经落地的范围」对应当前代码，不是尚未开始的计划。

## 来源

| 项 | 值 |
| --- | --- |
| 研究版本 | [Venshell/EFLink](https://github.com/Venshell/EFLink) `956a0d5d4b900f17ba06d7b30bc15d24770eb3a1`，模块版本 1.6.6 |
| 上游 | [guchang233/EFLink](https://github.com/guchang233/EFLink)，AGPL-3.0 |
| 许可 | 与本仓库同为 AGPL-3.0。归属见 `THIRD_PARTY_NOTICES.md` 与 `native/modules/eflink/UPSTREAM.md` |
| 发行形态 | `module.json` 只提供 `windows-x64` 的 `eflink.core.dll` |
| 适配的 Windows 游戏 | 1.6.3。本仓库 Android 产品对准的是游戏 1.6.4 / versionCode 61 |

`src/` 约 10539 行里，协议和会话大约占 **19%**（其中约 4% 可以原样编译，约 15% 需要套接字/路径垫片）。约 **19%** 是 Win32 界面、加载器和崩溃日志。约 **62%** 是 Windows IL2CPP 游戏接入。按体积算，仓库大头是 Dear ImGui（约 2.9 MB）和官方战斗 protobuf（约 400 KB），这两块都不进入 Android 产品。

分类：

| 标记 | 含义 |
| --- | --- |
| A | 可原样放进 Android NDK C++ |
| B | 逻辑可留，套接字、时钟、网卡、路径要换一层薄垫片 |
| C | Win32 / DirectComposition / DLL 加载，必须重写 |
| D | 依赖 Windows 游戏内部或 IL2CPP 偏移，必须按 Android 客户端重做 |

## 文件分类

### `src/`

| 文件 | 行 | 类 | 原因 |
| --- | --- | --- | --- |
| `link_protocol.h` | 191 | A | 注释写明小端、无游戏依赖。逐字段写入，不用结构体打包，线上没有 `wchar_t` |
| `link_version.h` | 3 | A | 版本字符串。Android 构建写成 `1.6.6-android`，协议号仍是 1 |
| `link_entry.h` | 40 | A | 入场能力事实，只有 JSON，没有 Unity 指针 |
| `link_profile.h` | 25 | A | 角色模板名和成长字段校验 |
| `link_map_consent.h` | 34 | A | 跟随换图的本地选择，不产生报文 |
| `link_input_ownership.h` | 26 | A | 按键占用状态机，本身不含 Win32 |
| `link_native_identity.h` | 16 | A | GC 句柄和实例编号的纯数据 |
| `authority/link_native_op_policy.h` | 37 | A | 出站战斗上报的来源集合，无平台 API。世界下行阶段才用得上 |
| `authority/link_official_view.h` | 82 | A | 可移植的 protobuf 视图切分。用途属于官方消息下行，本轮不接入 |
| `link_net.h` | 834 | B | 房间、中继、握手都在这里。原文件直接调用 Winsock、`GetAdaptersAddresses`、`GetTickCount64`、`SO_EXCLUSIVEADDRUSE` |
| `link_settings.h` | 61 | B | `%LOCALAPPDATA%`、`wchar_t`、`MoveFileExW`、虚拟键码。Android 改走设置项，不搬这个文件 |
| `link_realm.h` | 144 | B | 房主侧身份簿，逻辑与平台无关，但头文件包含 `link_net.h` |
| `link_session.h` | 534 | B | 世界频道 JSON 状态机（快照、传送、PVP 邀请、命中预算）。同样只通过 `link_net.h` 碰到平台 |
| `link_crash.h` | 72 | C | Windows SEH 和 `%LOCALAPPDATA%\EFLink\crash.log` |
| `link_gui.h` / `link_gui.cpp` | 926 | C | DirectComposition + D3D11 + ImGui 覆盖窗口 |
| `link_preview.cpp` | 42 | C | 独立 D3D11 菜单预览 |
| `link_module.cpp` | 988 | C+D | `eflink.core.dll` 导出、`__try`、`__fastcall`、`CameraManager.TailLateTick` 钩子。通道注册表可以重写，游戏钩子不能搬 |
| `runtime_scope.h` | 30 | D | `GetModuleHandleW(L"GameAssembly.dll")` |
| `unity_bridge.h` | 145 | D | 通过 Better-Endfield Host API 调 Unity |
| `link_world.h` | 4235 | D | 采样、远端角色、怪物、技能重放、副本跟随。这是最大的一块，绑定 Windows 1.6.3 的方法解析 |
| `link_probe.cpp` | 120 | D | 读 `GameAssembly` 可执行页的开发探针 |
| `link_recovery.cpp` | 180 | D | 游戏线程上的场景恢复 |
| `authority/link_metadata.h` | 189 | D | `GameAssembly.dll` 的 il2cpp 导出 |
| `authority/link_native_feed.h` | 298 | D | 把房主怪物编成官方 `SC_*` 视野消息 |
| `authority/link_native_wire.cpp` | 187 | D | 同上，并导出 Windows 第三方模块入口 |
| `authority/link_transport_observer.cpp` | 202 | D | 观察官方传输 |
| `authority/link_world_injector.cpp` | 634 | D | 往客户端注入官方世界消息 |
| `authority/native_authority_client.h` | 78 | D | 加载 `eflink-native-authority.dll` |
| `authority/native_proto_codec.h` | 99 | D | 用游戏内 protobuf 解析器 |
| `authority/native_scene_ack_route.h` | 87 | D | 官方换图回执路由，逻辑可留，接入点在游戏里 |

`link_world.h` 里小队通道 `eflink.squad.v1` 的编解码大约一百行，格式可以以后抽出来。整文件不能编译进 Android。

### `include/`

| 文件 | 类 | 原因 |
| --- | --- | --- |
| `EndfieldLink/EndfieldLink.h` | A | 公共 C API。`EFLINK_CALL` 在非 Windows 上是空宏。示例代码使用 `GetModuleHandleW`，那是文档，不是报文 |
| `EndfieldLink/NativeAuthority.h` | A | 实验性 Zig 内核的 C 头。ROADMAP 已把它标成冻结，不进本轮 |
| `BetterEndfield/*.h` | A | 与本仓库 `native/shared/include/BetterEndfieldNext/` 是同一套 ABI。不要再复制一份 |
| `nlohmann/json.hpp` | A | 3.12.0 MIT。本仓库 `native/shared/third_party/nlohmann/json.hpp` 已是同一版本 |

### `server/`

整目录是未完成的 Zig 官方世界内核，EF Link 的 `docs/ROADMAP.md` 已把它冻结：现有 C++ 房间继续负责传输，Zig 内核留给以后的另一条路线。它不接受真实客户端，也不打开套接字。

| 文件 | 行 | 类 | 原因 |
| --- | --- | --- | --- |
| `src/Frame.zig` | 43 | D | 官方 `CSHead` 帧，不是 47615 房间帧 |
| `src/Room.zig` | 424 | D | 内存里的场景所有权 |
| `src/SceneSession.zig` | 97 | D | 换图回执顺序 |
| `src/NativeAuthority.zig` | 212 | D | 导出 Windows DLL `EFLinkNative_GetApiV1` |
| `tests/*.zig` | 500 | D | 合成 protobuf，不能当实机验收 |
| `vendor/lr-proto/` | — | D | LR-S AGPL-3.0，基线来自 `git.xeondev.com/LR/S` `936df33`，战斗字段按 Windows 客户端改过。不要放进 NDK 构建 |

### `tests/`

| 文件 | 类 | 原因 |
| --- | --- | --- |
| `link_input_ownership_test.cpp` | A | 纯状态机 |
| `link_map_consent_test.cpp` | A | 纯状态机 |
| `link_native_op_policy_test.cpp` | A | 纯状态机 |
| `link_official_view_test.cpp` | A | 视图切分 |
| `link_protocol_test.cpp` | B | 协议断言可留；后半段调用 `SetEnvironmentVariableW` |
| `link_net_test.cpp` | B | 单进程回环。本仓库用它改写成 `native/modules/eflink/tests/eflink_tests.cpp` |
| `link_session_test.cpp` | B | 包含 `link_session.h`，垫片之后才能编 |
| `link_api_test.cpp` | C | 加载 DLL / 模块 ABI |
| `link_official_monster_test.cpp` | D | 包含 native feed |
| `native_authority_client_test.cpp` | D | Windows DLL |
| `native_authority_abi_test.py` | D | 加载 Windows DLL |

### `vendor/` 与工具

| 路径 | 类 | 原因 |
| --- | --- | --- |
| `vendor/imgui/**` | C | Dear ImGui 1.91.9b，MIT。后端只有 Win32 和 DX11。Android 悬浮窗是 Compose，不引入 ImGui |
| `vendor/nlohmann-LICENSE.MIT` | A | 与已有副本重复，不复制 |
| `tools/*.cmd` `*.ps1` | C | Visual Studio / Windows 打包 |
| `tools/*.py` | D | 对着已安装的 Windows 游戏做探针 |
| `examples/echo/` | C | `GetModuleHandleW(L"eflink.core.dll")` |
| `module.json` | C | 只有 `windows-x64` |

## 线路协议

传输与游戏、GUI 分开。房主是中心：每个客机只连房主，由房主转发。

| 通道 | 端口 | 用途 |
| --- | --- | --- |
| UDP 发现 | 47616 | `Beacon` / `Discover` 广播 |
| TCP 游戏端口 | 47615（可改，不能占用 47616） | 可靠、有序帧 |
| UDP 游戏端口 | 与 TCP 相同 | 姿态和不可靠模块数据 |

魔数 `0x4B4C4645`（`EFLK`），协议号 `uint16 = 1`。两边都是小端 IEEE-754 时，Android arm64 与 Windows x64 的字节相同。

TCP 帧：`u32` 长度（含 1 字节类型）+ `u8` 类型 + 载荷。单帧最大 1 MiB。可靠模块数据最大 256 KiB，不可靠最大 1024 字节，UDP 数据报最大 1200 字节。

TCP 类型：`Hello=1` `Welcome=2` `Reject=3` `PlayerInfo=4` `PlayerLeave=5` `Chat=6` `Data=7` `Bye=8` `KeepAlive=9` `State=10`。

UDP 类型：`Beacon=1` `Discover=2` `Hello=3` `Welcome=4` `State=5` `Data=6` `Ping=7` `Pong=8`。UDP 头是魔数、协议号、类型、1 字节填充、`u32` 房间号。

握手：

1. 客机 TCP 连接房主，发送 `Hello`：魔数、协议号、版本字符串、名字、`passwordProof(0, password)`、自己的 UDP 端口。
2. 房主只比较协议号。密码错回 `password`，满员回 `full`，协议不同回 `version:` 加房主的版本字符串。模块版本字符串不同只在聊天里提示，不拒绝。
3. `Welcome` 分配客机 id（房主固定为 1，客机从 2 起，id 会复用）、房间号、UDP token。
4. 客机再发 UDP `Hello`（id + token）。房主按房间号认 UDP，并把实际来源地址当成这条链路。这是为端口转发准备的，垫片必须保留。
5. 3 秒没有 UDP 时，姿态和不可靠数据改走 TCP。不可靠 TCP 用 `0xFFFF` 标记，积压超过 64 KiB 就丢最新样本。

房间锁沿用协议 1 的 `passwordProof`：FNV-64(`"0:" + password`)。明文密码不上线，但这不是口令保护。FNV-64 不是密钥学哈希；`Hello` 里的房间号固定写 0，房主也不发随机数，所以这 8 字节对同一密码永远相同。同一局域网里截到一次 TCP `Hello` 的人可以重放这 8 字节进有密码的房间，也可以离线枚举短密码。本轮保持这个算法，是为了和 Windows EF Link 1.6.6 进同一房间。换成安全握手必须把协议号从 1 升上去：房主先发每连接随机 nonce，客机用密码作密钥回 HMAC-SHA256（输入至少包含 nonce 和连接上下文）；需要抗离线猜测时再考虑 PAKE。协议 1 的客户端继续用现在的证明，不能混在同一次握手里。

`PlayerInfo` 走 TCP：id、名字、模型路径、地图、关卡、骨骼名、武器路径，都是带长度的 UTF-8。`PlayerState` 大约每秒二十次：位置三个 `f32`、旋转四个 `f32`、髋部偏移、每根骨骼四个 `int16` 量化旋转（最多 96 根）。字符串按 UTF-8 字节截断，不会切断多字节字符。

命名通道的 id 是名字的 FNV-32。保留名：

| 名字 | 内容 |
| --- | --- |
| `eflink.world.v1` | 可靠 JSON，`schema=2`，`room` 必须等于当前房间。种类包括 `snapshot`、`entry_prepare`、`entry_result`、`location`、`teleport`、`teleport_request`、`teleport_error`、`skill_intent`、`hit`、`player_hit`、`player_cast`、`enemy_cast`、`enemy_state`、`vitals`、`character_growth`、`pvp_duel`、`pvp_duel_event` |
| `eflink.squad.v1` | `u8=1` 加槽位加 `PlayerState`（不可靠）；`u8=2` 加 JSON 描述（可靠） |

世界快照里的 `session.ready` 只表示快照新鲜。真正入场要看 `session.entry.active`。当前 Windows 适配器自己也经常报未完成。

和 Windows 的关系：协议号 1 的姿态、玩家信息和聊天可以进同一个房间。发现广播在 Android 上可能单向失败，直接连接不依赖它。世界下行和 PVP 结算使用 Windows 1.6.3 的官方消息布局，不能假定 Android 1.6.4 的字段号相同。Android 构建的版本字符串是 `1.6.6-android`，Windows 房主会在聊天里记下这个差异，仍然接受连接。

线上没有 `wchar_t`，没有 `#pragma pack`。`Writer::put` 按本机字节序拷贝整数和浮点；两端都是小端，所以这是兼容的。以后如果出现大端设备，必须先改写入器。

## 在本仓库里的位置

产品边界是 Android ARM64：LSPosed 模块、设置应用、游戏内悬浮窗。`native/CMakeLists.txt` 在非 Windows 上直接失败，EF Link 不进入那份构建。

协议源码放在 `native/modules/eflink/`，只由下面两处使用：

- `android/app/src/main/cpp/CMakeLists.txt`（CMake 3.22.1、NDK `27.2.12479018`、`c++_static`、`arm64-v8a`、16 KB 页）把 `eflink_module.cpp` 编进现有的 `libbetterendfieldnext_android.so`
- 同目录的独立 CMake 只编主机测试，不挂到 APK

不放进 `betterendfieldnext_desktop_features`。那份静态库用 `Windows.h` 垫片编译桌面翻译单元，Winsock 头会和它缠在一起。

运行方式：

1. 设置应用「工具」里的联机开关默认关闭。打开后，`ModuleConfigurations` 把 JSON 交给游戏进程的 `BETTER_ENDFIELD_NEXT_EFLINK_CONFIG`。空字符串表示不启动，和相机、语音一样。
2. 游戏进程里的 `EflinkModule` 在 `Gameplay.Beyond.dll` 出现后启动网络线程。
3. 悬浮窗读写游戏私有目录 `files/eflink/` 下的 `command.json` 和 `status.json`。这和现有按键文件中继同一类边界：悬浮窗在模块类加载器里，不能直接调游戏类加载器里的 JNI。
4. 姿态采样挂在已有的 `HookBroker` 链上，模块 id 是 `eflink.core`，目标是 `CameraManager.TailLateTick`。相机模块继续用自己的 id 挂同一地址，链会把两边都叫到。采样使用本仓库已经在 1.6.4 上对过的合同：`PlayerController.GetMainCharacter`、`get_modelCom`、`GetModelGo`、`Transform.get_position` / `get_rotation`。骨骼只收集名字以 `Bip001` 开头的变换。

不生成远端角色模型，不调用官方传送，不注入 `SC_*` 消息。会话状态机编进了进程，网络线程会把 `eflink.world.v1` 放进收件箱，游戏线程调用 `Session::update`，但没有任何代码把结果写回游戏。

## Android 网络

注入代码跑在游戏 uid 里。模块 APK 的权限不会授给游戏。终末地作为在线游戏带有 `INTERNET`；开房和直接连接使用这个权限。模块清单额外声明了 `ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`、`CHANGE_WIFI_MULTICAST_STATE`，它们只覆盖模块自己的进程。

发现是 UDP **广播**（`SO_BROADCAST` 和每个网卡的定向广播），不是 IGMP 组播。部分 Wi-Fi 固件仍然会丢掉入站广播，除非进程持有 `WifiManager.MulticastLock`。游戏进程会尝试获取名为 `eflink` 的锁；游戏包没有 `CHANGE_WIFI_MULTICAST_STATE` 时这次获取失败，开房和直接连接继续可用。不要为了发现去改报文，否则和 Windows 房间不兼容。

其他约束：

- `SO_EXCLUSIVEADDRUSE` 是 Winsock 专有的。POSIX 上不对游戏端口设置 `SO_REUSEADDR`，第二个绑定会失败，效果和独占端口相同。发现套接字仍使用 `SO_REUSEADDR`。
- 非阻塞 `connect` 在 Linux 上返回 `EINPROGRESS`，Windows 上是 `WSAEWOULDBLOCK`。垫片把两者都当成「正在连接」。
- TCP `send` 使用 `MSG_NOSIGNAL`，避免对端关闭时把游戏进程打死。
- 只使用 IPv4，和上游一致。回环和 `169.254.*` 不出现在「告诉朋友的地址」里；`tun` 仍参与定向广播，便于 VPN 局域网。
- 游戏在前台时网络线程可以跑。切到后台后系统可以冻结进程，房主会停。不在游戏进程里另起前台服务。
- LSPosed 作用域仍是 `com.hypergryph.endfield` 和 `com.gryphline.endfield.gp`。房间跑在这个进程里，设置应用自己不开套接字。
- 明文网络策略不管原始套接字。

## 依赖许可

| 依赖 | 许可 | 本轮 |
| --- | --- | --- |
| EF Link 协议源码 | AGPL-3.0 | 已放入 `native/modules/eflink/`，保留上游提交 |
| Better-Endfield ABI | AGPL-3.0 | 使用本仓库已有头，不重复引入 |
| nlohmann/json 3.12.0 | MIT | 使用已有副本 |
| Dear ImGui 1.91.9b | MIT | 不引入 |
| LR-S protobuf | AGPL-3.0 | 不引入 |
| Grasscutter、Endfield-Poser | AGPL-3.0 | EF Link 只把它们当设计参考，没有源码可搬 |

## 阶段和规模

规模按要动的源码估计，不是日历时间。

| 阶段 | 内容 | 规模 | 状态 |
| --- | --- | --- | --- |
| 0 | POSIX 垫片、协议/房间/会话能在主机上编过并跑回环 | 垫片约 120 行；`link_net.h` 约 150 处平台调用；测试约 250 行 | 已完成，本机测试通过 |
| 1 | 两台 Android 进同一房间，悬浮窗看到对方坐标和姿态采样 | 原生模块约 450 行；设置和悬浮窗约 400 行 | 代码已接上。主机回环验证了报文；真机未跑 |
| 2 | 游戏世界里出现对方的干员模型 | 要从 4200 行的 `link_world.h` 重写实例化和插值，大约 1.5k–3k 行，并且要在 1.6.4 上重新对方法 | 未做。线上已经带骨骼四元数，面板能显示骨骼数量 |
| 3 | 小队另外三名和切人 | 抽出 `eflink.squad.v1`，再写 Android 采样，大约 1k–2k 行 | 未做 |
| 4 | 跟随换图、官方传送、副本 | 会话 JSON 已在进程内；执行要接 `TeleportToPosition` 一类 Android 方法，大约 2k 行，且依赖实机 | 未做 |
| 5 | 怪物下行、PVP 伤害、Zig 内核 | 超过 6k 行 Windows IL2CPP，外加未完成的 Zig。协议层移植不包含这一段 | 明确不做，除非另有产品决定 |

第一段可玩标准是：两台手机启用联机并重启游戏，一台开放房间，另一台用 IP 加入，悬浮窗列出双方坐标；进入可移动场景后还能看到朝向和骨骼数。局域网列表能出现更好，出现不了时直接连接仍然算通过。

## 风险

- 真机还没有跑过。`GetMainCharacter` 这些名字来自本仓库相机模块已经在 1.6.4 使用的合同，但是联机采样本身没有设备日志。
- 发现广播依赖机型和游戏是否持有组播锁。验收以直接连接为准。
- 同一进程里多个 UDP 套接字抢发现端口时，Linux 的投递和 Windows 不同。主机测试把发现记为可选，强制通过的是 TCP 加入、UDP 姿态、TCP 回退和聊天。
- 和 Windows 1.6.6 进同一房间在协议上成立，地图路径和模型路径是否一致没有证据。不要用跨端房间验收怪物或 PVP。
- 姿态钩子若安装失败，房间和聊天仍在，面板会写「尚未采样到角色」。钩子安装失败不能拆掉相机已经挂上的链；`HookBroker` 按模块 id 追加，失败时只放弃联机这一环。
- 密码只出现在一次性的 `command.json` 里，读完即删，不进 `status.json`，也不进设置。线上的 `passwordProof` 仍是可重放的静态 FNV-64，见上面的握手说明；本轮不改算法。

## 本轮已经落地的范围

- `native/modules/eflink/`：协议、房间、领域身份、世界频道状态机，以及 POSIX 垫片。
- `native/modules/eflink/tests/eflink_tests.cpp`：UTF-8、信标、世界 JSON 校验、密码、满员、踢人、聊天、可靠/不可靠通道、UDP 姿态、TCP 回退。用独立 CMake 在主机上构建，不进入 APK。
- Android 产品：工具页开关和名称/房间/端口/人数；悬浮窗开房、刷新局域网、直接连接、玩家列表（坐标、朝向、骨骼数、距离）、聊天。默认关闭。
- 游戏线程采样：链式挂上 `TailLateTick`，读取主控角色的位置、旋转和 `Bip001` 骨骼。
- 没有改 `native/CMakeLists.txt`，没有改遗留 Windows 源码，没有把 ImGui 或 `link_world.h` 放进构建。
