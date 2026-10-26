# Android R8 压缩方案与 keep 规则清单

> 后续实验：本文件第 6.5 节记录的 Compose 悬浮窗回退仍是历史实机证据。当前 `codex/window` 分支按用户要求再次试验 Compose 展示层，验收状态见 [`ANDROID_OVERLAY_COMPOSE_EXPERIMENT_20260929.md`](ANDROID_OVERLAY_COMPOSE_EXPERIMENT_20260929.md)。

日期：2026-09-29
状态：**已实施并通过实机验证**（随 Android **3.3.22** 发行）。规则落在 `android/app/proguard-rules.pro`，门禁任务 `verifyReleaseEntryPoints` 在每次 `packageRelease` 后自动校验。第 1–3 节保留决策当时的分析过程（含闪退事件的时间线），第 4 节起为落地后的实际形态。

> 时间线：本方案起因是 Compose UI 迁移后 release 体积 27.95 MB。写作期间新 UI 发行版出现启动闪退，该问题优先处理——**根因与 R8 无关**（`SettingsState` 的 `init` 块写在 `by mutableStateOf` 属性声明之前，委托为 null 时即 NPE，未混淆包同样崩）。闪退修复后 R8 规则落地，release 体积回到 6.69 MB；随后排查出优化器合并导致的游戏进程闪退（见 3.1），关闭优化器，最终 8.60 MB。

## 1. 目标

把 release 变体的 dex 从「未裁剪」打回原体积量级，消除 Compose 迁移带来的包体膨胀，同时不破坏 LSPosed 装载与 JNI 调用链。

## 2. 决策前后配置对照

| 项 | 决策前（上游设计） | 落地后 |
|---|---|---|
| 混淆 | `isMinifyEnabled = false` | `isMinifyEnabled = true` |
| 优化 | 默认开启（`proguard-android-optimize.txt`） | **关闭**（`-dontoptimize`）—— 见 3.1 |
| 规则文件 | 1 行，且指向已不存在的类（见下） | 3 条 keep + 1 条属性保留 + 1 条关闭优化 |
| 门禁任务 | 无 | `verifyReleaseEntryPoints`（`packageRelease` 后自动执行） |
| release 体积 | 27.95 MB | 8.60 MB（8,600,156 B） |

决策前 `android/app/proguard-rules.pro` 只有一行：

```
-keep class dev.betterendfield.android.BetterEndfieldXposed { *; }
```

**该规则指向的类已不存在，属失效规则。**
`BetterEndfieldXposed` 是 legacy Xposed API 82 时代的入口类，由 `legacy/better-endfield-2.3.1/source/android/app/src/main/assets/xposed_init` 按名指定；当前源码树只有 `XposedEntry`（libxposed API 102）。该 `-keep` 自 2.1.1 起从未随入口类改名，因此一旦开启 minify，它保护不了任何东西。

## 3. 实测包体对照（2026-09-28）

| 版本 | 总体积 | dex | 原生库 | assets+资源 |
|---|---|---|---|---|
| 旧 v3.3.20 release（无 R8） | 6.61 MB | 2.30 MB | 1.60 MB | 2.71 MB |
| 本轮 release 未混淆 | 27.95 MB | 23.01 MB | 1.65 MB | 3.29 MB |
| 本轮 debug | 36.34 MB | 29.76 MB | 2.55 MB | 4.03 MB |
| 本轮 release + R8（优化开启，**已废弃**） | 7.00 MB | 2.06 MB | 1.65 MB | 3.29 MB |
| 本轮 release + R8 + `-dontoptimize`（**现行**） | **8.60 MB** | 3.66 MB | 1.65 MB | 3.29 MB |

结论：体积差几乎全在未裁剪的 Compose 合成类与 `kotlin.Metadata`；UI 重写自身不足 1 MB。R8 的**裁剪与混淆**是体积收益的来源（28.0 → 8.6 MB），关掉**优化器**另付约 1.5 MB，换游戏进程的类图与源码一致（见 3.1）。

## 3.1 为什么必须关掉优化器（2026-09-29 追加）

**背景**：设置界面与悬浮窗重写后，实机出现「hook 后游戏闪退且无日志」。已定位主因（悬浮窗被改写为 Compose，见 6.5），但排查中发现**第二个独立缺陷**，由开启 R8 引入。

**R8 的类合并会把模块的静态工具类与 Compose 折进同一个持有类。** 实测（优化器开启的产物）：

- `NativeCommandBridge` 全是静态字段/静态方法、无实例 —— 正是 R8 会折叠的形态。它被并进类 `Lr4;`；该类里既声明着 `NativeCommandBridge` 自己的成员（`Q` = `status()`、`R` = `tailNativeLog(StringBuilder,long[])`，以及 `XposedEntry` 写入的 `inputFile`/`statusFile`/`nativeLogFile` 字段），又有一段 `<clinit>`，其中 `new-instance` 的是 `androidx.compose.ui.BiasAbsoluteAlignment`、`androidx.compose.ui.BiasAbsoluteAlignment$Horizontal`、`androidx.compose.foundation.layout.Arrangement$Absolute$Left$1`。
- 从 libxposed 入口 `XposedEntry` 出发走类引用图：**可达 2463 / 2537 个 dex 类**，其中 **364 个类的 `<clinit>` 会构造 Compose 对象**。

**为什么致命**：游戏进程里的 remote-command 轮询会周期性调用 `NativeCommandBridge.status()`；对上述任一持有类的一次「主动使用」就会触发其 `<clinit>`，在 Unity 进程里构造 Compose 对象。悬浮窗改用纯 View 的全部意义就是让游戏进程永不加载 Compose —— 让优化器通过合并把这条依赖塞回来，等于白改。

**处置**：`proguard-rules.pro` 增加 `-dontoptimize`。裁剪与混淆照常，只有优化（类合并、lambda 池化、内联）关闭。

**验证**（判据不依赖 mapping 反查，因 R8 对合成类会给出误导性代表名）：

| 配置 | 从 `XposedEntry` 可达 | 结论 |
|---|---|---|
| 优化开启 | 2463 类（含 364 个构造 Compose 的 `<clinit>`） | 不可用 |
| 优化关闭 | **91 类**：75 个模块类 + 15 个 `io.github.libxposed.service.*` + 1 个 `java.lang.Record` 反糖化垫片 | **零 androidx / kotlin** |

判据是「可达类必须全部落在 模块包 / Android 与 JDK / libxposed 三界之内」——不需要判断某个混淆名到底是谁，因此不受 R8 重名影响。

审计方法（脚本在本机 `out/verify/audit_closure.py`，`out/` 已 gitignore 故未入库）：解出 APK 内的 `classes.dex` → `dexdump -d` → 从 `LXposedEntry;` 做 BFS（不跟进 `Landroid/`、`Ljava/` 等框架类）→ 断言每个可达类的 mapping 原名以 `dev.betterendfield.android.` 开头或属框架/libxposed。产物 dex 的 SHA-256 已与审计对象比对一致（`a9792107…`）。

## 4. 必须的 keep 规则（按风险排序）

### A. 框架入口 —— 缺失即模块无法装载

`android/app/src/main/resources/META-INF/xposed/java_init.list` 内容为纯文本类名：

```
dev.betterendfield.android.XposedEntry
```

现代 Xposed API（libxposed）用 `META-INF/xposed/java_init.list` 按**类名**定位入口，不再有 `assets/xposed_init`。类名被 R8 重命名或类被裁掉，模块在目标进程里就完全不会加载。

```proguard
-keep class dev.betterendfield.android.XposedEntry { *; }
```

备选（允许入口类混淆，代价是需要 R8 改写文本资源）：

```proguard
-adaptresourcefilecontents META-INF/xposed/java_init.list
```

选前者：一个类不重命名，代价可忽略，且不引入 `adaptresourcefilecontents` 在 AGP 下的行为不确定性。

### B. JNI 正向解析（Java → native 符号名）

JNI 符号名由「类名 + 方法名」拼成，因此声明 `native` 方法的类名与方法名都不可变。落地时用类级 keep 一次覆盖 B 与 C：

```proguard
-keep class dev.betterendfield.android.BemInstaller { *; }
```

选类级而非 `-keepclasseswithmembernames class X { native <methods>; }` 的理由：后者只保护 native 方法，把 C 类（`conversionProgress`，非 native）漏在外面，需要再加一条 `-keepclassmembers`；类级 keep 一条同时回答 B 和 C，且规则文件里能直接读到「这个类整体不许动」。代价是该类全部成员不被裁，实测可忽略（`BemInstaller` 只贡献个位数 KB）。

**本轮实测：本 APK 装了两个导出 JNI 符号的原生库，不是第 3 节假设的一个。**

| 库 | 导出的 `Java_dev_betterendfield_*` 符号 | 状态 |
|---|---|---|
| `libbetterendfield_installer.so` | `BemInstaller_{inspectNative,convertNative,cancelNative}Native` | 活，受上面规则保护 |
| `libbetterendfield_android.so` | `NativeCommandBridge_{key,releaseKeys,status,submit,probeIl2Cpp}` | **死符号**，见下 |

`NativeCommandBridge` 的 JNI 桥早已改为文件中继（`NativeCommandBridge.java` 类头注释：运行库在游戏 classloader 注册，而模块类属 LSPosed classloader，Android 按 classloader 隔离 JNI 符号查找，故每次调用都抛 `UnsatisfiedLinkError`）。因此：

- Java 侧已**没有**任何 `native` 声明，这些导出不会被解析，属预编译库里的历史残留；
- R8 把整个 `NativeCommandBridge` 类**合并进了宿主类**（`mapping.txt` 里该类的类头行消失，成员以 `-> f`、`-> t` 等散落在别处），这是安全的。

计划阶段把 `NativeCommandBridge` 只当作「一个无主死符号」，实际是五个、且分布在第二个库上——门禁任务据此改为**遍历全部 .so 的符号闭合校验**，并保留一份显式的死符号清单（而非静默放过）。

### C. JNI 反向回调（native 按名字查 Java 方法）

`install_jni.cpp:41`：

```cpp
env->GetStaticMethodID(owner,"conversionProgress","(Ljava/lang/String;IIIIF)V")
```

这是**默认规则完全不覆盖**的一类：方法名与签名写在 C++ 字符串里，R8 一旦重命名或内联，`GetStaticMethodID` 返回 null，函数随即 `return nullptr`，转换静默失败（无进度、无异常）。上面的类级 keep 覆盖了它；`mapping.txt` 实测 `conversionProgress` 映射到自身（出现 3 行），未改名。

**另一个方向也不存在**：`native_bridge.cpp:141` 有 `JNI_OnLoad`，但只做启动门（检查 `BETTER_ENDFIELD_RUNTIME_STARTED` 防止二次加载），**不含 `FindClass`／`RegisterNatives`／`GetMethodID`**，故不存在「动态注册把名字交给 R8 之外」的第三类约束。

### D. 清单组件（AGP 自动，无需手写）

`ModuleApplication`、`MainActivity`、`BemInstallActivity`，以及由 `io.github.libxposed:service` AAR 清单合并进来的 `io.github.libxposed.service.XposedProvider` —— 由 AGP 生成的 `aapt_rules.txt` 自动 keep。**验证时仍需在产物 dex 中确认 `XposedProvider` 存在**，因为它是库清单带进来的，不是本工程源码。

## 5. 已排查为「无需规则」的面

| 面 | 结论 |
|---|---|
| `Class.forName` 用法 | 仅 `com.unity3d.player.UnityPlayer`、`android.os.SystemProperties`，均为外部类 |
| `getDeclaredMethod("onActivityResult")` | 目标是被挂钩游戏的 Activity 类，非本模块类 |
| `getDeclaredMethod("nativeLoad")` | `Runtime.class`，JDK 内部类，与方法名一起由 `@SuppressLint("BlockedPrivateApi")` 那处反射调用，不受本项目混淆影响 |
| 设置项枚举 / `valueOf` / `getIdentifier` | 全模块无使用 |
| WebView JS 桥 | 全模块无 `WebView`／`addJavascriptInterface`（这类接口方法名由 WebView 按字符串反射解析，是另一类常见混淆陷阱；本项目不涉及） |
| 资源动态查找 | 无；Compose 一律走 `R` 常量引用，`isShrinkResources` 风险可控但本轮先保持 false |
| Kotlin 反射 | 未引入 `kotlin-reflect`，`kotlin.Metadata` 可裁 |
| R8 缺失类 | `build/outputs/mapping/release/missing_rules.txt` **为空**，即无需任何 `-dontwarn` |
| 被改名的自家类 | `GameOverlay -> xz`、`RuntimeLog -> ku0`（二者只有直接引用，无按名解析方），安全；`XposedEntry`、`BemInstaller` 保持原名 |

## 6. 落地与验证记录

执行内容：写入 A/B/C 规则、删除失效的 `BetterEndfieldXposed` 规则、开启 `isMinifyEnabled = true`（`isShrinkResources` 保持 false），并新增门禁任务（第 8 节）。

### 6.1 静态核验（产物层）

| 核验项 | 结果 |
|---|---|
| `missing_rules.txt` | 空 —— 无需任何 `-dontwarn` |
| dex 中 `Ldev/betterendfield/android/XposedEntry;` | 存在，且 `mapping.txt` 为 `-> 自身` |
| dex 中 `Ldev/betterendfield/android/BemInstaller;` | 存在，且 `mapping.txt` 为 `-> 自身` |
| `io/github/libxposed/service/XposedProvider` | 存在（由库清单合并带进，非本工程源码） |
| `META-INF/xposed/java_init.list` | 内容未变 |
| `BemInstaller` 的 3 个 native 方法名 + `conversionProgress` | 均在 dex 字符串池中可查 |
| 全部 .so 的 JNI 导出 | 3 个活符号受保护，5 个死符号列入显式清单 |
| 游戏进程类图闭包 | 91 类，零 androidx / kotlin（见 3.1） |

### 6.2 实机回归（HLK-AL00 / Android 10 / arm64-v8a）

**首次（7,017,154 B，SHA-256 `0C965DE5…AAEB041`）**：

| 用例 | 结果 |
|---|---|
| 冷启动 `am start -W` | `Status: ok / COLD / TotalTime 1007ms`，无 FATAL |
| 四标签逐页点击（首页/体验/角色/工具） | 全部正常渲染，无 FATAL |
| 悬浮窗预览（当时还是 Compose-in-View） | 日志确认 Compose surface created，面板正常，无 FATAL |

这同时也关闭了先前记录里「Compose／androidx 内部反射未做实机复核」的缺口。**注意**：该次通过只覆盖模块进程内的 Compose 渲染，不能代表游戏进程 —— 这正是后来漏掉 Compose 化的原因（见 6.5）。

**现行（8,600,156 B，SHA-256 `F565C3E4…C9920C`，纯 View 悬浮窗 + `-dontoptimize`）**：

| 用例 | 结果 |
|---|---|
| 安装并冷启动 | `TotalTime 2300ms`，焦点窗口为 `MainActivity` |
| 四标签逐页点击 | 全部正常渲染，本包 FATAL 计数 0 |
| 工具页「预览悬浮窗」（恢复后的 Java/View 实现） | 面板正常渲染，已带新工业黄黑白配色与更新后的文案，本包 FATAL 计数 0 |

注：同一次 logcat 里出现过一条 `FATAL EXCEPTION`，来自无关应用 `com.shxyke.MaaTouch`（PID 22350），与本模块无关。

### 6.3 尚未覆盖的部分

实机只覆盖**设置界面与模块进程内的渲染**。真正的游戏内 hook 路径（`XposedEntry` 在《终末地》进程里装载、`GameOverlay` 在 Unity 帧回调中初始化）在 HLK-AL00 上无法复现（设备上没有该游戏也没有 LSPosed）。这部分的 R8 风险现已由两类证据压到最低：游戏进程类图闭包经审计为零 androidx/kotlin（3.1）；`XposedEntry`/`BemInstaller` 类名由 mapping 与 dex 双向确认未变。但仍应在真机游戏环境里做一次装载验证 —— **这一条至今未被任何实机证据覆盖，不要用界面通过冒充 hook 通过**。

### 6.4 视觉缺陷（非崩溃，未处理）

角色页「第三方模型替换」行右侧的状态 chip「已关闭」压在换行后的描述文字上。

### 6.5 游戏内闪退的根因：悬浮窗被 Compose 化（2026-09-29）

**现象**：新 UI 发行版 hook 后游戏闪退，无日志。

**根因**：UI 重写把 `GameOverlay`（游戏内悬浮窗）从 Java/View 改写为 Kotlin/Compose（711 行），这是 3.3.20 → 3.3.21 之间**唯一进入游戏进程的行为变化**。

三项硬证据：

| 检查 | 结果 |
|---|---|
| HEAD 版 `GameOverlay.java`（重写前，872 行） | 纯 View，零 Compose 引用 |
| 已发布 `v3.3.20` 的 dex | Compose 描述符 **0 个** |
| 本轮 `GameOverlay.kt` + `UiTokens.kt` | import `ComposeView`/`LifecycleRegistry`/`SavedStateRegistryController`；`Be.Colors.*` 每个成员都是 `androidx.compose.ui.graphics.Color` |

**「无日志」的机制** —— 三条路径全部逃出异常保护：

1. `init` 块里的 `Be.Colors.*.toArgb()` 与 `addContentView` 没有任何 catch；
2. `buildPanel()` 的 `catch (Throwable)` 只护住**同步**的 `ComposeView` 创建，真正的 composition 在视图 attach 后的**异步**回调里，已在 try 之外；
3. `onActivityResumed` 只捕获 `RuntimeException` —— `Error`（`NoClassDefFoundError`/`ExceptionInInitializerError`）与 native SIGSEGV 都直接冒泡出生命周期回调，游戏进程即死。

**回归对照**：用户实测 3.3.20（纯 View 悬浮窗）hook 正常、3.3.21（Compose 悬浮窗）一进游戏即崩 —— 变异一致，确认是本轮回归，与 R8 无关。

**处置**：`GameOverlay` 回退为 HEAD 版 Java/View 实现，删除 `GameOverlay.kt` 与 `OverlayPanel.kt`（仅被前者内部引用），并做两处适配：

- 配色对齐 —— 9 个常量 + 4 处常量外硬编码旧色映射到 `Be.Colors` 的 overlay token（`overlayPanel`/`overlayField`/`overlayRow`/`overlayPressed` 等）。Java 侧无法引用 Compose `Color`，故两处各留一份并在注释里互相标注「必须同步改」。
- 文案对齐 —— 过期的「画面增强」页名更新为新的「体验」，`"打开增强设置"`/`"无法打开增强设置"` 同步改为「体验设置」。页名契约本身未断：`openSettings()` 传的 `"enhancement"` 在新 `MainActivity.kt` 仍映射到 `EXPERIENCE`。

**顺带核验的两件事**：

- `GameOverlay` 的三个对外契约与 HEAD 完全一致（`install(Application, ClassLoader, Supplier<OverlayFeatures>)`、构造器 `(Activity, boolean)`、`remove()`），`XposedEntry.java` 与 `MainActivity.kt` 的调用点均无需改动。
- `GameOverlay.java` 里 `MainActivity.EXTRA_PAGE` 与 `RuntimeBootstrap.MODULE_PACKAGE` 两处跨语言常量引用，经 `javap -c` 确认**已被内联为字符串字面量**（`ldc_w`，无 `getstatic`）：前者是 Kotlin `const val`、后者是 Java `static final String` 字面量，javac 均按编译期常量处理。因此游戏进程不会加载已是 Compose 版的 `MainActivity` 类。

**结论**：游戏进程内不得出现任何 Compose 类是硬约束。悬浮窗保持纯 View；3.1 的 `-dontoptimize` 是同一约束在 R8 侧的延伸。

## 7. 发布注意

- release 沿用 debug 签名（上游既有设计），每次 CI 构建证书都不同，普通设备不能原位覆盖升级；换包前须自行备份数据。
- 开启 minify 会改变 CI 发布产物结构，`android-release.yml` 无需改动，但该版本的实机验证要求高于以往。
- 构建产物必须发到对话（用户约定，见 `F:\bem\.workbuddy\memory\MEMORY.md`）。

## 8. 门禁任务 `verifyReleaseEntryPoints`

位置：`android/app/build.gradle.kts`，注册为 `tasks.matching { it.name == "packageRelease" }.configureEach { finalizedBy(...) }`，即**每次打 release 包都会跑**。

设计原则是「不信任 keep 规则，只信任产物」。五类检查：

1. **dex 名字存在性** —— 框架入口类、4 个清单组件的类描述符须以 `L…;` 形式出现在 dex 字符串表中。
2. **JNI 导出闭合** —— 遍历 APK 内**每一个** .so，抽出全部 `Java_dev_betterendfield_android_*` 符号；每一个都必须能对应到 Java 源码里的 `native` 声明（声明集合是**扫源码自动得出**的，不是手写表）或显式的死符号清单。C++ 会把外层函数名 mangle，故用前缀匹配（`..._convertNativeE3` 归到 `convertNative`）。
3. **类名未改名** —— 用 `mapping.txt` 判定，要求 `X -> X:`（identity）。这一层是必要的：某类名有可能作为无关字面量或合成成员签名残留在 dex 字符串表里，只看 dex 会漏判。
4. **方法名未改名** —— 用 dex 字符串表判定。
5. **游戏进程的类未被折叠**（2026-09-29 追加）—— 对 10 个游戏进程内执行的类（`XposedEntry`/`RuntimeBootstrap`/`RuntimeLog`/`GameOverlay`/`OverlayFeatures`/`NativeCommandBridge`/`ModuleConfigurations`/`ModuleSettings`/`Hotkeys`/`FrameworkSettings`），要求它们在 `mapping.txt` 里各自拥有类头。依据见 3.1 与 6.5。

### 8.1 三个反直觉的坑（实测得出，值得记住）

- **R8 不为 `native` 方法输出 mapping 行。** 它的名字已被 JNI 符号契约钉死，R8 不打印 identity 行。实测：`inspectNative`／`convertNative`／`cancelNative` 在 `mapping.txt` 中出现 **0 次**，而同为类级 keep 保护的普通方法 `conversionProgress` 出现 3 次。因此方法名只能用 dex 字符串表作证，用 mapping 会得到 100% 的假失败。
- **解析 Java 源码找 `native` 时不能只剥注释。** 本包内 `native` 大量出现在文档注释与字符串字面量里：`log.accept("native load attempt " + ATTEMPTS.get() + …)` 会被 `\bnative\b[^;()]*?\b(\w+)\s*\(` 读成「native 方法 `get`」。最终判据是「行首修饰符序列之后的 `native`」，比剥离字符串字面量更精确也更易读。
- **`usage.txt` 区分不了「被合并」与「被删除」。** 第 5 类检查最初用「不在 `mapping.txt` 类头里、但在 `usage.txt` 里」判定被折叠，结果**反向测试没失败** —— 因为被合并的类同样会出现在 `usage.txt` 里。真正的判别信号是 `mapping.txt` 的**足迹形状**：被合并的类有成员行（`…NativeCommandBridge.status():80:80 -> Q`）但无类头；被合法删除的类（`Hotkeys`，常量被 javac 内联后成为死类）一行都没有。实测数据：优化器开启时 `Hotkeys` 0 行、`NativeCommandBridge` 48 行、`ModuleSettings` 304 行。

### 8.2 门禁自身的验证

- 正向：真实产物上通过，日志为 `… 1 JNI callback names intact and 10 game-process classes unmerged`。
- 反向（第 5 类检查）：临时注释掉 `-dontoptimize` 重新开启优化器，任务立刻失败并精确报出
  `[dev.betterendfield.android.ModuleSettings, dev.betterendfield.android.NativeCommandBridge]`，随后恢复。
- 反向（第 2 类检查）：临时把死符号清单里的 `submit` 改名，任务立刻失败并精确报出
  `libbetterendfield_android.so!dev.betterendfield.android.NativeCommandBridge.submit`，随后回滚。
- 漂移守卫：源码扫描出的 `native` 集合必须至少包含已知的三个名字，否则报「native-method scan drifted」——防止正则失效后检查静默失效（这一守卫在开发中确实先触发过一次，正是它暴露了 8.1 的第二个坑）。

## 9. 可选后续项

- `libbetterendfield_android.so` 里那 5 个死 JNI 导出可在 native 侧删除（`native_bridge.cpp` 的遗留函数），删掉后第 8 节的死符号清单即可清空；属源码改动，未在本轮做。
- `isShrinkResources`：本轮保持 false。资源裁剪会重命名资源并把 `R` 引用改写为内联常量，需要在有实机游戏环境时另做一轮验证。
- 角色页 chip 压字（6.4）。
- 把游戏进程闭包审计（3.1）从「本地脚本」升级为常驻门禁。当前第 5 类检查只是 10 个类的**金丝雀**，能拦住已证实的失败模式，但不等价于全闭包断言；在 Kotlin 里实现完整 dex 解析成本较高，故先用金丝雀 + `-dontoptimize` 组合。
- 悬浮窗的**外观重构**（用户已明确后期要做）：本轮只是把 Compose 版回退为 View 版并统一配色，版式仍是 3.3.20 的。
