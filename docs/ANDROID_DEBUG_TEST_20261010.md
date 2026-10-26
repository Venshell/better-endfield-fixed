# Android Debug 构建与测试（2026-10-10，S0）

本文产物及结论属于 S0 时点；2026-10-11 S1 开发包、条件提交合同和新设备测试见 [S1 记录](ANDROID_INDEX_CONDITIONAL_SAVE_20261011.md)。下方“F05 未修复”不能覆盖 S1 当前源码接线，也不表示 S1 已取得设备验收。

受众：本轮调试包测试者。仅覆盖 S0 门禁和当前 Android 回归；不表示 S1 索引冲突已修复，也不表示热切换可用。

## 本轮构建结果

工作树 `D:/CodexData/bem-android-thirdparty-alignment`，已提交基线 `55f3e16f` 加现有未提交内容。版本 `3.4.2` / `30402`，Android ARM64，最低 Android 10。执行 `:app:verifyPortHost :app:assembleDebug :app:assembleDebugAndroidTest` 成功，75 个任务中 6 个执行、69 个复用；属于增量构建。S0 不修改 APK 产品代码，因此本轮主 APK 与此前已核对 APK 的哈希相同。

交付目录 `D:/codexdata/bem-debug-20261010-s0`：

| 文件 | 字节数 | SHA-256 |
| --- | --- | --- |
| `app-debug.apk` | 81741637 | `3F211815B21B851ABA9A500E854CC8AD09C7DBB554D56B3F7EE0D16BCDF007FE` |
| `app-debug-androidTest.apk`（设备测试兼容修正后） | 133553 | `6C430D370FC5632D9AA1DF6BEEFD4E002D071F3E94ECF368BC21C1FF5847F5C7` |

主 APK 核对 834 个 `.behw`、1 份 `assets/headwear-v3/manifest.tsv`（835 行，包含表头），3 个原生库且均为 ARM64。主包和测试包签名验证通过且证书一致，证书 SHA-256 为 `4EDD10B98AC4A239A78B64931509A43FA0F328FA77EFB8D43D37E57358FA826F`。

## 电脑端 S0 自动门禁

在仓库根目录用 PowerShell 7 执行：

```powershell
Set-Location D:/CodexData/bem-android-thirdparty-alignment
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
& 'D:/CodexData/better-endfield-bem-env-20260926/Scripts/python.exe' -B tools/AndroidPort/run_host_gate.py
```

本轮期望末行：`PASS Android port host gate: 5 Java suites, 74 Java checks; 4 Python modules, 26 Python tests`。失败须非零退出，不能把 `NO-SOURCE` 算测试通过。本轮实际重新执行得到该结果；无需手机。完整合同见[宿主门禁](../tools/AndroidPort/README.md)。

## 手机端定向自动测试

手机开启 USB 调试并连接电脑。以下命令在 PowerShell 7 执行；多设备时在所有 adb 命令中增加 `-s <序列号>`。

```powershell
$adb = 'C:/Users/Vens_/AppData/Local/Android/Sdk/platform-tools/adb.exe'
& $adb devices -l
& $adb install -r D:/codexdata/bem-debug-20261010-s0/app-debug.apk
& $adb install -r D:/codexdata/bem-debug-20261010-s0/app-debug-androidTest.apk
```

两次安装都需显示 `Success`。Debug 与 Release 签名不同；如果覆盖安装提示签名不匹配，先保留现有数据并决定备份/切换方式，不要直接以卸载解决。

先跑三项，分别检查索引镜像、损坏索引恢复和无框架预览关闭：

```powershell
& $adb shell am instrument -w -r -e method testBemIndexDurableMirror dev.betterendfield.android.test/dev.betterendfield.android.BemInstallerTest
& $adb shell am instrument -w -r -e method testModelPageRecoversFromBrokenIndexToEmptyList dev.betterendfield.android.test/dev.betterendfield.android.BemInstallerTest
& $adb shell am instrument -w -r -e method testOverlayPreviewLifecycleWithoutFramework dev.betterendfield.android.test/dev.betterendfield.android.BemInstallerTest
& $adb logcat -d -s BetterEndfield.InstallTest:I '*:S'
```

本项目使用自定义 instrumentation runner，选择参数是 `method`。每项应有 `INSTRUMENTATION_RESULT: stream=PASS BEM installation tests`、`INSTRUMENTATION_CODE: -1`，且日志必须出现对应精确方法名的 `PASS`。runner 当前不会拒绝拼错的方法名，因此仅有整体 PASS 不能证明指定方法执行。出现 `FAIL` 或 code 0 为失败；adb 本身退出成功不代表测试成功。

如要执行全部内置测试，可省略 `-e method`，但其中有真实 BEM 输入测试，默认无输入会跳过；日志中的该项 PASS 不能当作真实包转换已验。该项需要另行提供实际包，不在本轮三项定向测试范围。

2026-10-10 设备推进：用户选定 PJX110（`b992bd53`，ARM64，Android 16）。主包和最初测试包均覆盖安装成功。首次执行 `testBemIndexDurableMirror` 得到 `NoSuchMethodError: Files.readString`，失败发生在测试读取镜像内容处，未到达内容断言；不能计为镜像事务通过或产品事务失败。

测试改为 `Files.readAllBytes` 加显式 UTF-8 解码，保留原内容相等和停用状态断言。测试 APK 重编译成功（57 个任务，8 个执行），签名验证通过，交付目录测试 APK 已更新至上表新哈希。复测前 PJX110 曾掉线，用户重新连接后在同一设备覆盖安装修正后的测试包成功，三项定向测试均通过：

| 方法 | 设备本地日志时间 | 结果 |
| --- | --- | --- |
| `testBemIndexDurableMirror` | 10-10 21:22:08.810 | 精确方法 PASS，instrumentation code -1 |
| `testModelPageRecoversFromBrokenIndexToEmptyList` | 10-10 21:22:10.041 | 精确方法 PASS，instrumentation code -1 |
| `testOverlayPreviewLifecycleWithoutFramework` | 10-10 21:22:13.015 | 精确方法 PASS，instrumentation code -1 |

首次失败与重编译日志保存在交付目录 `device-PJX110/`；复测输出及本轮开始时间之后的精确方法日志保存在 `device-PJX110/retest/`。复测用 `-w -r`，读取原始 instrumentation 状态；没有清空设备已有日志。最后附加的 `pidof` 查询未取得进程号并返回 1，不改变以上三项独立记录的测试结果，也不能据此声明 instrumentation 结束后应用进程仍存活。未取得页面视觉或游戏内验收。

## 游戏内人工回归

1. 在 LSPosed 启用模块并确认游戏作用域；完全退出后重启游戏。设置应用应能读取框架状态，无持续错误提示。
2. 打开设置应用与悬浮窗，检查加载、空列表/已有列表、关闭预览后应用仍正常。FOV 改动需同时核对游戏画面和实际应用日志；页面保存提示本身不是应用回执。
3. 用已知有效 BEM 包从文件管理器“打开/分享”进入导入，核对名称与启用项。保存后完全重启游戏，再检查 world / 角色详情 UI 的外观；禁用后再次重启确认恢复。无效包应显示失败且保留已有包。
4. 进入/退出第一人称、换角色，检查头饰与头发、阴影、视角和恢复行为。运行中即时换模尚未开放，不把“无需重启立即生效”列为预期。

F05 安装页与悬浮窗交错保存仍有已知 P1，当前不要把该流程作为已修复验收；下一轮 S1 专门处理。

## 边界

本轮取得 PJX110 安装成功及上述三项定向自动测试通过证据；初次测试兼容失败已修正并复测。不扩展为完整 instrumentation、页面视觉、游戏、lint 或远端 CI 通过结论；既有资源目录/NDK 弃用告警仍为范围外债务。未暂存、提交、合并、推送或发布。构建日志在交付目录 `debug-build-20261010.log`。
