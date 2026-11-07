# Android 移植宿主门禁

本入口归属 Android 移植验收工具层，执行当前生产纯 Java owner 的六组宿主检查，以及 BEM 创作者工具的四组 Python 测试。合并上游 Next 后，当前为 Java 118 项、Python 29 项；下方保留 S0 时点五组 74 项证据。产品语义仍由被测 owner 拥有；门禁不模拟 Android 偏好监听、磁盘事务、LSPosed 或 Unity，也不证明热切换已可用。

当前生产包路径为 `dev.betterendfield.next`。宿主执行真实 `Text.java` 文案表；`fixtures/AppLanguage.java` 仅提供固定中文的语言适配，不模拟业务或 JSON。该门禁不证明 Android 偏好存储、系统语言选择或英文渲染。ZIP 容器探测现在先验证所有成员路径；含不安全路径时拒绝整个 ZIP，而不是部分导入。LOD 测试已迁到当前 header 生成合同；缺失目标不猜测，prefab 索引快照必须与策略匹配。旧 JSON 候选 API 的“禁止 Windows UI 源资料”断言不再适用：新策略显式使用历史 UI 身份，并以 Android prefab 证据解析目标，不把来源资料当 Android 渲染验收。

## 合同与执行

统一 owner 为 `run_host_gate.py`，Gradle 与 CI 只调用它。需要 JDK 17+、Python 3.10+、`zstandard==0.25.0` 及真实 `org.json:json:20240303` JAR。JAR 由调用方通过 `--json-jar` 或 `BEM_PORT_JSON_JAR` 显式提供；门禁不下载依赖，并校验固定 SHA-256 `3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed`。不得用 Android SDK 的 JSON stub 执行测试。每个子进程最多执行 180 秒，CI job 最多 15 分钟。任一必需文件缺失、编译失败、检查失败、超时或缺少非零计数均返回非零退出状态。检查数来自本轮 Java 输出计数器和 Python unittest 报告。

每次在独立临时目录编译全部所选 owner 与测试，绝不复用旧 class。Java/Python 夹具临时路径均下传到同一目录，退出自动释放。Windows 默认工作目录为 `D:/codexdata/bem-port-host-work`；Gradle 使用当前工作树 `android/app/build/portHost`；CI 使用 runner 临时目录。工作目录保留为空，不保存编译产物。

在仓库根目录执行（PowerShell 7）：

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:BEM_PORT_JSON_JAR = 'D:/CodexData/dependencies/json-20240303.jar'
python -B tools/AndroidPort/run_host_gate.py
```

在 `android` 目录执行统一 Gradle 门禁：

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:GRADLE_USER_HOME = 'F:/CodexData/gradle-user-home'
./gradlew.bat :app:verifyPortHost -PportHostPython=D:/CodexData/better-endfield-bem-env-20260926/Scripts/python.exe --offline --console=plain --no-daemon
```

`portHostPython` 默认值为 `python`，须指向具备测试依赖的解释器；Gradle 同样读取 `BEM_PORT_JSON_JAR`。`:app:check` 和各 `test*UnitTest` 任务依赖同一个门禁；AGP 的 JUnit 任务仍可能 `NO-SOURCE`，只有出现宿主门禁具体计数及 PASS 才能计入宿主测试通过。`:app:verifyPortHost` 不构建 APK，不需要头饰目录；执行含 APK 编译的其他任务仍须传入验证过的头饰 catalog。

CI `.github/workflows/android-build.yml` 的 `port-host` job 使用同一入口；APK job 依赖它。新增门禁与 CustomModel 文件也纳入路径触发条件。未推送前不能声称远端 job 已执行。

## 2026-10-10 验收

- 独立命令及 `:app:verifyPortHost` 均重新编译执行通过：`BemHotSwitchUpdateTest` 15、`BemImportArchiveTest` 27、`BemIndexCommitTest` 15、`FrameworkServiceWaitTest` 10、`OverlayReconnectPolicyTest` 7，共 74 项 Java 检查；四组 Python 共 26 项测试。
- 隔离源码副本分别注入缺失 Java 测试、Java 断言失败、Python 导入断言失败，三次均返回 1，无整体 PASS，临时编译目录释放；没有修改实际产品或原测试断言。
- `:app:testDebugUnitTest :app:check --dry-run` 确认任务图包含 `:app:verifyPortHost` 前置依赖；这里只验证接线，dry-run 的 SKIPPED 不计为测试执行。
- Gradle 配置仍报告既有 `assets.srcDir` 与 `ndk.dir` 弃用警告，属于本轮范围外工具链债务。本轮门禁接线没有新增编译警告；不宣称全项目无告警或 lint 已通过。
- 未构建/安装 APK，未执行 instrumentation、游戏设备验收或远端 CI；未提交、合并、推送。

以上是 S0 的停止边界；S1 源码与构建结果见 [S1 记录](../../docs/ANDROID_INDEX_CONDITIONAL_SAVE_20261011.md)，当前先补其设备门禁，不进入 S2。

本轮检查清单：

- [x] 当前路线图与 dirty 工作树审计。
- [x] 单一门禁 owner、Gradle / CI 接线。
- [x] 本地真实计数、失败注入与临时目录释放。
- [x] 文档索引与当前状态回写；Git 暂存区为空，主线保持干净。
- [ ] 远端 CI 执行：未推送，保持待运行。
