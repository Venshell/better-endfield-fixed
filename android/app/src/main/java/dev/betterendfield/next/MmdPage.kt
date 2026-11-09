package dev.betterendfield.next

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The MMD sub-page: what to play, how to play it, and what the character does
 * while it plays.
 *
 * MMD is the one feature in this app whose *transport* is worth putting on a
 * settings page. Everywhere else the panel is the only place a feature can be
 * driven, because the panel is the only surface that is on screen while the game
 * runs - but MMD is exactly the case where looking at the panel means not
 * looking at the dancer, so the buttons belong on a screen the user can hold
 * while watching. They travel the same way the panel's own buttons do: a runtime
 * command the native director drains on the game's frame thread.
 *
 * The loose slots and imported library works share the native playback owner.
 * Library imports are inspected and reviewed in MmdImportActivity before any
 * work is published through the framework file space.
 *
 * Everything in the parameter cards writes the camera configuration, which the
 * running game replays live. The two halves therefore behave differently and the
 * page marks the difference: a switch takes effect immediately, a transport tap
 * needs the game to be running.
 */
@Composable
fun MmdPage(state: SettingsState) {
    // One launcher for all three slots: the contract is the same, and the slot it
    // is filling is remembered for the duration of the pick.
    var pendingSlot by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && pendingSlot.isNotEmpty()) state.importMmdSlot(pendingSlot, uri)
        pendingSlot = ""
    }
    val choose = { id: String ->
        pendingSlot = id
        picker.launch(arrayOf("*/*"))
        Unit
    }

    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        state.refreshMmdLibraryAfterImport()
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Be.Space.l)) {
        SectionCard(
            eyebrow = "MMD · CAMERA",
            title = "MMD 舞蹈与运镜",
            subtitle = "把动作、表情与镜头文件交给角色播放。" +
                "片源选好后，播放控制由运行时命令通道发给游戏进程。",
            status = state.cameraCardStatus,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
                SwitchRow(
                    title = "启用 MMD",
                    description = "MMD 由相机模块驱动，并占用同一个引擎节拍。" +
                        "首次开启需要重启游戏：加载哪些模块是进程启动时决定的。",
                    checked = state.mmdEnabled,
                    onCheckedChange = state::updateMmdEnabled,
                )
                BodyText("当前片源：${state.mmdSourceSummary}")
                if (!state.mmdMaterialAvailable && state.mmdEnabled) {
                    BodyText(
                        "还没有可播放的文件。在下面的「片源」里导入动作文件即可开始；" +
                            "只导入镜头文件也可以，那样只会运镜。",
                    )
                }
            }
        }

        TransportCard(state)

        LibraryCard(
            state = state,
            busy = state.mmdLibraryBusy || state.mmdLibraryImporting,
            onImportFolder = { importLauncher.launch(android.content.Intent(context, MmdImportActivity::class.java)) },
        )

        SourceCard(
            state = state,
            busy = state.mmdImporting.isNotEmpty(),
            importingSlot = state.mmdImporting,
            onChoose = choose,
        )

        SectionCard(
            eyebrow = "PLAYBACK",
            title = "播放参数",
            subtitle = "改动会立即写进相机配置，游戏运行时由原生模块热重载。",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
                SwitchRow(
                    title = "循环播放",
                    description = "作品结束或动作播完后从头再来一遍。",
                    checked = state.mmdLoop,
                    onCheckedChange = state::updateMmdLoop,
                    enabled = state.mmdEnabled,
                )
                SliderRow(
                    label = "跳转步长",
                    value = state.mmdSeekSeconds,
                    onValueChange = state::updateMmdSeekSeconds,
                    valueRange = 0.5f..60f,
                    unit = "秒",
                    // One stop is one tenth of a second over a 0.5-60 s range.
                    steps = 595,
                    decimals = 1,
                    enabled = state.mmdEnabled,
                )
                BodyText("「后退」「前进」每次跳这么远，不填满整段。")
            }

            GroupLabel("MUSIC")
            Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
                SwitchRow(
                    title = "播放音轨",
                    description = "作品里带音乐文件时才有效。安卓是叠加播放，" +
                        "和游戏自己的背景音乐同时响，建议先把游戏 BGM 关掉。",
                    checked = state.mmdMusic,
                    onCheckedChange = state::updateMmdMusic,
                    enabled = state.mmdEnabled,
                )
                SliderRow(
                    label = "音量",
                    value = state.mmdGain,
                    onValueChange = state::updateMmdGain,
                    valueRange = 0f..1f,
                    steps = 100,
                    decimals = 2,
                    enabled = state.mmdEnabled && state.mmdMusic,
                )
                SliderRow(
                    label = "音轨偏移",
                    value = state.mmdAudioOffset,
                    onValueChange = state::updateMmdAudioOffset,
                    valueRange = -600f..600f,
                    unit = "秒",
                    // One second per stop, so the readout is the stored value.
                    steps = 1200,
                    decimals = 1,
                    enabled = state.mmdEnabled && state.mmdMusic,
                )
                BodyText("音乐位置 = 动作时间 + 偏移，用来对齐留白或掐掉前奏。")
            }
        }

        BodyCard(state)
    }
}

/**
 * The transport. Every button is one verb on the command channel, and the strip
 * is disabled until both halves of the precondition hold: the module is on (the
 * native director stops and stays stopped while it is off) and something is
 * loaded (there is nothing to play otherwise).
 */
@Composable
private fun TransportCard(state: SettingsState) {
    val live = state.mmdTransportAvailable
    SectionCard(
        eyebrow = "TRANSPORT",
        title = "播放控制",
        subtitle = null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
            PrimaryButton(
                text = "播放 / 暂停",
                onClick = { state.sendMmdCommand("play_pause") },
                enabled = live,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Be.Space.m),
            ) {
                GhostButton(
                    text = "后退 ${trimmed(state.mmdSeekSeconds)} 秒",
                    onClick = { state.sendMmdCommand("seek -${trimmed(state.mmdSeekSeconds)}") },
                    enabled = live,
                    modifier = Modifier.weight(1f),
                )
                GhostButton(
                    text = "前进 ${trimmed(state.mmdSeekSeconds)} 秒",
                    onClick = { state.sendMmdCommand("seek ${trimmed(state.mmdSeekSeconds)}") },
                    enabled = live,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Be.Space.m),
            ) {
                GhostButton(
                    text = "循环开关",
                    onClick = { state.sendMmdCommand("loop") },
                    enabled = live,
                    modifier = Modifier.weight(1f),
                )
                GhostButton(
                    text = "镜头模式",
                    onClick = { state.sendMmdCommand("camera") },
                    enabled = live,
                    modifier = Modifier.weight(1f),
                )
                GhostButton(
                    text = "停止",
                    onClick = { state.sendMmdCommand("stop") },
                    enabled = live,
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.mmdCommandStatus.isNotEmpty()) {
                StatusBlock(state.mmdCommandStatus, monospace = true)
            }
            Notice(
                if (live) {
                    "这些按钮通过运行时命令通道发给游戏进程，游戏必须正在运行；" +
                        "参数类改动则不需要重启。"
                } else if (!state.mmdEnabled) {
                    "先在上面启用 MMD，命令在没有启用时会被原生导演直接丢弃。"
                } else {
                    "先导入至少一个片源文件，或导入镜头文件。"
                },
            )
        }
    }
}

/**
 * The library: whole works, imported as folders and played as a unit.
 *
 * This is the form the desktop module is built around and the reason it is
 * offered first: one work folder carries its own motion, face, camera, music
 * and - up to four - additional dancers, so a squad playback is a property of
 * the work rather than three files that have to be matched by hand.
 *
 * Selecting a work takes over from the loose slots below rather than combining
 * with them. That is not a limitation of this page but of the native director:
 * it resolves one source or the other, because a work already answers the
 * question the slots would answer a second time.
 */
@Composable
private fun LibraryCard(state: SettingsState, busy: Boolean, onImportFolder: () -> Unit) {
    SectionCard(
        eyebrow = "SOURCE · LIBRARY",
        title = "作品库",
        subtitle = stringResource(R.string.mmd_alignment_library_hint),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
            if (state.mmdWorks.isEmpty()) {
                BodyText(stringResource(R.string.mmd_alignment_library_empty))
            } else {
                state.mmdWorks.forEach { work ->
                    WorkRow(
                        title = work.name,
                        subtitle = state.mmdWorkLabel(work),
                        selected = work.generation == state.mmdWork,
                        enabled = !busy,
                        onSelect = { state.selectMmdWork(work.generation) },
                        onRepublish = { state.republishMmdWork(work.generation) },
                        onRemove = { state.removeMmdWork(work.generation) },
                    )
                }
                if (state.mmdWork.isNotEmpty()) {
                    GhostButton(
                        text = "切回散文件播放",
                        onClick = { state.selectMmdWork("") },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            GhostButton(
                text = stringResource(R.string.mmd_alignment_open_import),
                onClick = onImportFolder,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.mmdLibraryStatus.isNotEmpty()) {
                BodyText(state.mmdLibraryStatus)
            }
            BodyText(
                "导入的文件先发布到框架的共享空间，游戏进程在下次启动时复制到自己目录，" +
                    "所以导入之后要重启游戏才能在游戏里切换作品。",
            )
        }
    }
}

/** One installed work: what it is, how big it is, and what can be done to it. */
@Composable
private fun WorkRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onRepublish: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.row))
            .background(Be.Colors.row)
            .padding(horizontal = Be.Space.xl, vertical = Be.Space.l),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = Be.Colors.textPrimary,
                fontSize = Be.Type.rowTitle,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Text(
                    text = "播放中",
                    color = Be.Colors.accent,
                    fontSize = Be.Type.caption,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Text(
            text = subtitle,
            color = Be.Colors.textSecondary,
            fontSize = Be.Type.bodySmall,
            modifier = Modifier.padding(top = 3.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Be.Space.m),
            horizontalArrangement = Arrangement.spacedBy(Be.Space.m),
        ) {
            GhostButton(
                text = if (selected) "已选择" else "选择",
                onClick = onSelect,
                enabled = enabled && !selected,
                modifier = Modifier.weight(1f),
            )
            GhostButton(
                text = "重新发布",
                onClick = onRepublish,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            GhostButton(
                text = "移除",
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The three loose file slots, which are what a playback is assembled from. */
@Composable
private fun SourceCard(
    state: SettingsState,
    busy: Boolean,
    importingSlot: String,
    onChoose: (String) -> Unit,
) {
    SectionCard(
        eyebrow = "SOURCE",
        title = "片源",
        subtitle = "散文件形式：动作、表情、音乐各一个槽位。" +
            "动作文件常常自带镜头数据，只导入动作也能运镜。",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
            SlotRow(
                title = "动作（.vmd）",
                description = "身体动作。EIEM 用它驱动全身骨架，需要打开下面的「身体动作」。",
                value = state.mmdSlotLabel(ModuleSettings.MMD_SLOT_MOTION),
                imported = ModuleSettings
                    .mmdSlotName(state.mmdSlots, ModuleSettings.MMD_SLOT_MOTION).isNotEmpty(),
                busy = busy,
                importing = importingSlot == ModuleSettings.MMD_SLOT_MOTION,
                onImport = { onChoose(ModuleSettings.MMD_SLOT_MOTION) },
                onClear = { state.clearMmdSlot(ModuleSettings.MMD_SLOT_MOTION) },
            )
            SlotRow(
                title = "表情（.vmd）",
                description = "面部键。和身体动作同一份也不冲突。",
                value = state.mmdSlotLabel(ModuleSettings.MMD_SLOT_FACE),
                imported = ModuleSettings
                    .mmdSlotName(state.mmdSlots, ModuleSettings.MMD_SLOT_FACE).isNotEmpty(),
                busy = busy,
                importing = importingSlot == ModuleSettings.MMD_SLOT_FACE,
                onImport = { onChoose(ModuleSettings.MMD_SLOT_FACE) },
                onClear = { state.clearMmdSlot(ModuleSettings.MMD_SLOT_FACE) },
            )
            SlotRow(
                title = "音乐",
                description = "任意 MediaPlayer 认得的音频格式。",
                value = state.mmdSlotLabel(ModuleSettings.MMD_SLOT_MUSIC),
                imported = ModuleSettings
                    .mmdSlotName(state.mmdSlots, ModuleSettings.MMD_SLOT_MUSIC).isNotEmpty(),
                busy = busy,
                importing = importingSlot == ModuleSettings.MMD_SLOT_MUSIC,
                onImport = { onChoose(ModuleSettings.MMD_SLOT_MUSIC) },
                onClear = { state.clearMmdSlot(ModuleSettings.MMD_SLOT_MUSIC) },
            )
            if (state.mmdImportStatus.isNotEmpty()) {
                BodyText(state.mmdImportStatus)
            }
            BodyText(
                "文件先由本应用发布到框架的共享文件空间，游戏进程在下次启动时复制到自己目录，" +
                    "所以导入之后要重启游戏。",
            )
            Notice(
                if (state.mmdWork.isNotEmpty()) {
                    "当前按作品库播放：上面的散文件槽位会被忽略，" +
                        "要恢复散文件播放请回到作品库切回去。"
                } else {
                    "散文件适合只有一段动作的场合；" +
                        "带 set.ini 的完整作品（含四人同台）请用上面的作品库导入。"
                },
            )
        }
    }
}

@Composable
private fun SlotRow(
    title: String,
    description: String,
    value: String,
    imported: Boolean,
    busy: Boolean,
    importing: Boolean,
    onImport: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.row))
            .background(Be.Colors.row)
            .padding(horizontal = Be.Space.xl, vertical = Be.Space.l),
    ) {
        Text(
            text = title,
            color = Be.Colors.textPrimary,
            fontSize = Be.Type.rowTitle,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = description,
            color = Be.Colors.textSecondary,
            fontSize = Be.Type.bodySmall,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
        Text(
            text = if (importing) "正在导入…" else value,
            color = if (imported) Be.Colors.accent else Be.Colors.textMuted,
            fontSize = Be.Type.bodySmall,
            modifier = Modifier.padding(top = Be.Space.s),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Be.Space.m),
            horizontalArrangement = Arrangement.spacedBy(Be.Space.m),
        ) {
            GhostButton(
                text = if (imported) "替换文件" else "选择文件",
                onClick = onImport,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
            if (imported) {
                GhostButton(
                    text = "清除",
                    onClick = onClear,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The EIEM body block.
 *
 * Dimmed rather than hidden when body motion is off, for the reason the motion
 * page gives: the native session reads none of these values while it is
 * disabled, so rows that stayed live would write settings nothing reads - and a
 * row that silently does nothing reads as broken.
 */
@Composable
private fun BodyCard(state: SettingsState) {
    val body = state.mmdEnabled && state.mmdBody
    SectionCard(
        eyebrow = "EIEM · BODY",
        title = "身体动作",
        subtitle = "由 EIEM 驱动角色的全身骨架；关闭时 MMD 只运镜、只放音乐。",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
            SwitchRow(
                title = "身体动作",
                description = "需要角色姿态租约可用；不可用时日志会写明原因，" +
                    "镜头与音乐不受影响。",
                checked = state.mmdBody,
                onCheckedChange = state::updateMmdBody,
                enabled = state.mmdEnabled,
            )
            SwitchRow(
                title = "表情",
                description = "表情与眼睛挂在身体会话上，所以要有身体动作才生效。",
                checked = state.mmdFace,
                onCheckedChange = state::updateMmdFace,
                enabled = body,
            )
            SwitchRow(
                title = "地形贴合",
                description = "按脚下的实际地面高度调整角色，防止悬空或陷进地面。",
                checked = state.mmdTerrain,
                onCheckedChange = state::updateMmdTerrain,
                enabled = body,
            )
            SwitchRow(
                title = "动作循环",
                description = "身体动作单独循环，和上面的「循环播放」是两回事。",
                checked = state.mmdMotionLoop,
                onCheckedChange = state::updateMmdMotionLoop,
                enabled = body,
            )
            SliderRow(
                label = "动作幅度",
                value = state.mmdMotionScale,
                onValueChange = state::updateMmdMotionScale,
                valueRange = 0.05f..5f,
                unit = "×",
                steps = 495,
                decimals = 2,
                enabled = body,
            )
            FieldLabel("衣物物理")
            SelectField(
                options = listOf("跟随游戏", "稳定（推荐）", "冻结"),
                selectedIndex = state.mmdClothMode.coerceIn(0, 2),
                onSelect = state::updateMmdClothMode,
                enabled = body,
            )
            BodyText("「跟随游戏」用原版布料，「稳定」接管成固定解算，出现穿插时用「冻结」。")
        }
    }
}

/** One decimal, and no trailing ".0" for a whole number of seconds. */
private fun trimmed(value: Float): String =
    if (value == value.toInt().toFloat()) {
        value.toInt().toString()
    } else {
        String.format(java.util.Locale.ROOT, "%.1f", value)
    }
