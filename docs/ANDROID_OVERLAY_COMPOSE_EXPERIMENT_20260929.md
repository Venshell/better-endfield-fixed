# Android 游戏内悬浮窗 Compose 实验

状态：PJX110 上已验证游戏直接冷启动时悬浮 Handle 可见；展开、拖动、触摸透传和实际关卡仍待验收。

## 范围与既有证据

本轮只替换游戏内悬浮 Handle 和面板的 View 构造层。此前 3.3.21 的 Compose 迁移曾在游戏进程导致启动崩溃，见 [`ANDROID_R8_MINIFY_PLAN_20260929.md`((ANDROID_R8_MINIFY_PLAN_20260929.md) 第 6.5 节。用户指定当前分支用于实验，因而允许再次试验；构建、App 内预览、游戏进程加载是三个不同的验收级别。

首次 composition 必须等宿主和两个 ComposeView 附着到窗口后再触发，并写入 `RuntimeLog`；同步创建失败在延后任务中记录并移除宿主。后续异步重组或设备端 classloader 问题仍可能导致崩溃。

`GameOverlay.java` 仍拥有 `Application.ActivityLifecycleCallbacks`、Activity 内 `addContentView`、`setContentView` 后重挂载、z-order 复查、pause 时释放 held keys、热键文件中继、运行日志导出与 Activity result 链路。`OverlaySurface.kt` 给悬浮窗 host 及两个 `ComposeView` 提供同一组 lifecycle、saved-state、view-model owner；`OverlayPanel.kt` 和 `OverlayControls.kt` 只渲染功能开关与调用 controller callback。Compose 不读取配置文件，不调用 JNI 或 Xposed。

PJX110 首次复现日志只有 WebView 的 `onPackageReady`，没有游戏主包的 ready 回调。`XposedEntry` 现从 `onPackageLoaded` 也注册同一套目标进程入口，并由原子门禁避免重复注册；`Application.attach` 时取实际 `Context` 的 classloader。首次实机复测随后证实 native runtime 已进入游戏主进程，但悬浮窗仍未出现。即时前台恢复日志给出明确异常：`ViewTreeLifecycleOwner not found from android.widget.FrameLayout`。原因是 ComposeView 附着时向父树查找 owner，而原实现只在 ComposeView 自身设置。将 owner 同时设置在悬浮窗 host 后，前台恢复日志依次出现 `overlay Compose first composition created`、`overlay panel attached`、`host attached=true` 和 `overlay host re-raised above the game view`。

再次按用户指出的“直接启动游戏”复测，确认另一个启动时序错误：首次 `onActivityResumed` 时 Activity 的窗口尚未附着，构造函数立即 `createComposition()` 抛出 `createComposition requires ... the View to be attached to a window`；回前台时窗口已附着，因此之前截图不能证明冷启动可用。现在 ComposeView 的 `setContent` 与 `createComposition` 都延至宿主附着后的主线程任务，异常仍进入运行日志并清理宿主。冷启动日志在 `21:39:45.552` 先记录 `overlay panel attached` 和 `host attached=true`，`21:39:45.755` 再记录 `overlay Compose first composition created`；未出现创建异常。

触摸边界是全屏无监听的 `FrameLayout` host 和两个有界 `ComposeView` 子视图。Handle 用 Compose 手势计算窗口坐标拖动，Java controller 保留原有比例位置、WindowInsets 与边界 clamp。展开面板按可视高度测量并在左右侧选择可见位置。此方案移动有界 `ComposeView` 的位置，以避免全屏 `ComposeView` 吞掉面板外的游戏触摸；与需求中的“优先只移动 Compose 内部内容”有意不同，须用实机触摸验证。

## 本轮本机门禁

- `:app:compileDebugKotlin :app:compileDebugJavaWithJavac`：通过。
- `:app:assembleDebug :app:assembleRelease`：通过；release 包含既有 `verifyReleaseEntryPoints` 门禁。
- Git diff check：通过。
- Java 编译仍提示 `GameOverlay.java` 使用已弃用的 Android API：保留原有 WindowInsets 读取与 `startActivityForResult` 日志保存链路，未在这次 UI 实验中改写已验证的结果中继；后续若调整应单独验收。
- release APK Manifest 复核：`applicationId=dev.betterendfield.android`、minSdk 29、targetSdk 35、compileSdk 37；未见 `SYSTEM_ALERT_WINDOW` 或新增 Service。
- PJX110 已安装 `3.3.22-alpha.1` 预发布候选 APK，安装后对游戏执行 `am force-stop` 并从 launcher 直接冷启动；干净 `assembleRelease` 通过。候选 APK SHA-256：`D2068BD8E5E8AB005EFC16F45545129BAC07CBB4B1E1BED3C2A1AB2E869CEE1C`，包内 `versionName=3.3.22-alpha.1`、`versionCode=30322`。本机签名证书 SHA-256 `62713BA05E66F3A7E05747F3E63F04C60F05B339D7DD4CFCA5834DAF46B9A096` 与公开 `v3.3.21` 证书不同。
- 实机日志显示 `21:51:39.140` 首次 Activity resume 时附着宿主，`21:51:39.685` 创建 Compose；`dumpsys activity top` 显示游戏 `U8UnityContext` 内容树中，UnityPlayer 上方有悬浮 host。`D:\codexdata\bem-alpha-cold-launch.png` 截图显示 BE Handle 在直接冷启动后的游戏启动画面左侧可见；无需切出游戏再返回。

## 实机停止条件

直接冷启动的启动画面 Handle 显示和一次回前台重挂载已由日志、View 树及截图验证。仍需在实际关卡依次验证：收起与展开时面板外游戏触摸、Handle 拖动与边缘 clamp、横竖屏与 Insets、所有按键点击与按住/释放、pause 释放、日志复制及 SAF 保存、Activity `setContentView` 重建、单实例和长期 z-order。除前述两项外，这些均标为**未验证**。

任何一项失败，先记录复现环境与日志，再判断是 Compose classloader、view ownership、触摸分发还是现有 controller 链路；不得把构建成功记成实机通过。
