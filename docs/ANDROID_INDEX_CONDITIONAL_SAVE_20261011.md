# S1 安装索引条件提交（2026-10-11）

本文是内部合同与验收记录。范围仅为 F05：安装页与悬浮窗交错修改时的索引修订、真实草稿和可见冲突。执行真源为[移植路线图](ANDROID_PORT_ROADMAP_20261010.md)。只开发 Android ARM64，不进入 S2、热切换或 Windows 产品。

## 问题与 owner

原安装页保存从最新索引直接套用旧页面值，没有读取修订检查。刷新又把所有旧包值视作草稿，包括未编辑值，导致旧开关遮住外部修改和新包的冲突压制。只添加修订参数会让这些旧值借用刷新后的新修订，无法解决问题。

`BemInstaller` 是模型索引读取与提交唯一 owner：`snapshot` 在锁内返回不可变索引文本及 SHA-256 修订；`saveAll`、`saveChanges`、`disableAll` 均必须提供读取修订，在同一锁内校验后执行原镜像/偏好事务。修订不符在写入前抛出专门的 `IndexConflict`；成功返回实际提交快照。Provider 只映射 IPC、权限及大小限制，将冲突映射为 `index_conflict`，不重试写入。没有保留不带修订的保存重载；删除无调用者的旧 `select` 入口。

`BemIndexRevision.Draft` 持有读取修订和已接受选择的防御性副本，比较当前编辑值并提供 `UNCHANGED` / `LOAD_LATEST` / `KEEP_DRAFT`。页面消费结果，不私造冲突结论。

## 页面合同

1. 无真实编辑时读最新索引；开关、选项、参数跟随外部变化，新包压制的旧包显示禁用。
2. 有编辑且索引变化时保留**整份旧读取快照上的草稿**，不更新其读取修订、不自动合并。提示下方是草稿，禁用保存和全部停用；草稿开关不表示外部当前状态。
3. 选择“读取最新设置”需确认“放弃草稿并读取”；取消保持草稿。只有明确确认才替换基线。
4. 自己保存成功后立即接纳实际提交结果。全部停用只提交开关，更新基线后仍保留未保存的外观/选项/参数，不隐式保存它们。
5. 编辑后改回原值不视为草稿；移除代际的旧草稿不自动消失，也不能重新保存使代际复活。

保守规则在任何外部索引变化时阻止脏页面保存，即使修改对象不同；本轮不引入跨包/资源冲突的自动合并。草稿仅覆盖页面状态生命周期，跨 Activity 重建的草稿持久化不属于本轮已有承诺。新增文案进入现有五套语言资源，复用现有组件和主题，不新增颜色。

## 本轮实际验证

在移植工作树 `android` 目录执行：

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:GRADLE_USER_HOME = 'F:/CodexData/gradle-user-home'
./gradlew.bat :app:verifyPortHost :app:assembleDebug :app:assembleDebugAndroidTest -PheadwearCatalogDir=D:/CodexData/headwear-audit/all-characters/catalog-bundled -PheadwearPython=D:/CodexData/better-endfield-bem-env-20260926/Scripts/python.exe -PportHostPython=D:/CodexData/better-endfield-bem-env-20260926/Scripts/python.exe --offline --console=plain --no-daemon
```

- 六组 Java 90 项检查，其中新修订/草稿合同 16 项；四组 Python 26 项，实际通过。
- 双 APK 编译通过（75 个任务，25 个执行）。补充无假草稿用例后重编译测试包通过（57 个任务，8 个执行）。
- 主包核对 834 个 `.behw`、manifest 和 3 个 ARM64 原生库；双方签名验证通过。
- 五套新增字符串完整性、XML 解析、唯一键检查和 `git diff --check` 通过。
- 独立只读审查未发现修订绕过或自动丢草稿的源码阻断；不代替运行验收。
- 既有 `assets.srcDir` 与本机 `ndk.dir` 弃用告警为本轮范围外工具链债务，不计 lint 通过。

产物与日志目录：`D:/codexdata/bem-s1-20261011`。

| 产物 | SHA-256 |
| --- | --- |
| `app-debug.apk` | `4AFD59A252A0B8A29CCC682A16210381A8EAC41847C26915EF5AD092DA627435` |
| `app-debug-androidTest.apk` | `01B546DA9422D1F3D4C8587E1AC2D662DE0E3D67CF9F15CC4ABDE8B12FC25FAC` |

## 尚未执行的设备门禁

ADB 本轮仅列出 HLK-AL00；用户指定 PJX110 暂不可用，明确“先做源码与构建验证”。未安装到其他设备，未声明设备或页面交互通过。以下六项使用独立偏好和 filesDir，不写正式安装索引，均仅编译：

| 方法 | 主要断言 |
| --- | --- |
| `testIndexRevisionRejectsStalePageAndOverlay` | 旧页面/悬浮窗/全部停用修订拒绝，最新偏好与镜像保留 |
| `testIndexDraftRefreshRetainsAndBlocks` | 刷新不借新修订提交，草稿保留，确认放弃后读最新 |
| `testIndexCleanRefreshAndImportConflict` | 无编辑开关/选项/参数读新值，新包压制旧包显示正确 |
| `testIndexNormalSaveAndDisableRetainsDraft` | 正常保存、停用保留参数草稿、非法 tick 不修改偏好或镜像 |
| `testIndexRemovedGenerationRetainsDraft` | 移除后保留旧草稿但拒绝保存，不复活代际 |
| `testIndexRevertedDraftLoadsLatest` | 编辑改回原值及无草稿全部停用后，不产生假冲突 |

PJX110 恢复后逐项运行（每项必须核对本轮精确方法 PASS 及 code -1）：

```powershell
$adb = 'C:/Users/Vens_/AppData/Local/Android/Sdk/platform-tools/adb.exe'
& $adb -s b992bd53 install -r D:/codexdata/bem-s1-20261011/app-debug.apk
& $adb -s b992bd53 install -r D:/codexdata/bem-s1-20261011/app-debug-androidTest.apk
& $adb -s b992bd53 shell am instrument -w -r -e method testIndexRevisionRejectsStalePageAndOverlay dev.betterendfield.android.test/dev.betterendfield.android.BemInstallerTest
# 将 method 替换为上表其余五项，逐项执行。
& $adb -s b992bd53 logcat -d -s BetterEndfield.InstallTest:I '*:S'
```

页面验收：页面编辑→游戏悬浮窗保存其他值→返回页面，检查冲突文案、旧草稿和保存/全部停用禁用；确认窗口取消保持草稿，确认放弃显示最新值。还需无编辑刷新、新包冲突压制、正常保存和错误状态的真实渲染检查。宿主检查仅证明草稿合同，编译不证明这些交互。

## 检查清单与停止边界

- [x] 当前 dirty 工作树、调用链与 owner 审计。
- [x] 共享合同、两个入口和可见冲突接线。
- [x] 宿主回归、双 APK 构建、内容/签名核对。
- [x] 设备用例编译、五套文案检查、独立审查和文档回写。
- [x] 不暂存/提交/合并/推送，保留此前 dirty 内容。
- [ ] 六项设备用例和真实页面交互：用户设备暂不可用。

S1 源码与构建已接线，设备门禁未闭合。下一步先补 S1 设备/页面验收，不进入 S2，不声称 F05 完整完成或 P1 已通过完整验收。
