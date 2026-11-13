package dev.betterendfield.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File

@Composable
internal fun EflinkOverlay(preview: Boolean) {
    if (preview) {
        OverlaySection("联机") {
            Text("预览不连接房间。进入游戏后可在这里开房、发现局域网房间、直接连接，并看到其他玩家的位置。",
                color = Be.Colors.textSecondary, fontSize = 11.sp)
        }
        return
    }
    val context = LocalContext.current
    var status by remember { mutableStateOf(EflinkSnapshot.missing()) }
    var room by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var chat by remember { mutableStateOf("") }
    var sequence by remember { mutableLongStateOf(0L) }
    fun nextSequence(): Long {
        val now = System.currentTimeMillis()
        sequence = if (now > sequence) now else sequence + 1
        return sequence
    }
    LaunchedEffect(Unit) {
        while (true) {
            status = EflinkSnapshot.read(File(context.filesDir, "eflink/status.json"))
            delay(400)
        }
    }
    OverlaySection("联机") {
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp)) {
            Text(status.headline(), color = Be.Colors.textPrimary, fontSize = 12.sp)
            if (status.error.isNotEmpty()) {
                Text(status.error, color = Be.Colors.textSecondary, fontSize = 11.sp)
            }
            OverlayField(room, { room = it }, "房间名", 64)
            OverlayField(password, { password = it }, "密码，不保存", 64)
            OverlayActionRow(
                "开放房间",
                {
                    send(context, nextSequence(), JSONObject()
                        .put("op", "host")
                        .put("room_name", room)
                        .put("password", password))
                    password = ""
                },
                "离开",
                { send(context, nextSequence(), JSONObject().put("op", "leave")) },
            )
            OverlayField(address, { address = it }, "IP:端口", 64, KeyboardType.Uri)
            OverlayAction("直接连接") {
                send(context, nextSequence(), JSONObject()
                    .put("op", "join")
                    .put("address", address)
                    .put("password", password))
                password = ""
            }
            OverlayAction("刷新局域网房间") {
                send(context, nextSequence(), JSONObject().put("op", "refresh"))
            }
            status.rooms.forEach { found ->
                val label = found.name + " · " + found.host + " · " + found.players + "/" + found.max +
                    (if (found.locked) " · 有密码" else "")
                OverlayAction(label) {
                    address = found.address
                    send(context, nextSequence(), JSONObject()
                        .put("op", "join")
                        .put("address", found.address)
                        .put("password", password))
                    password = ""
                }
            }
            if (status.players.isEmpty()) {
                Text("还没有玩家。", color = Be.Colors.textSecondary, fontSize = 11.sp)
            }
            status.players.forEach { player ->
                Text(player.line(), color = Be.Colors.textPrimary, fontSize = 11.sp)
            }
            status.chat.takeLast(6).forEach { line ->
                Text(line, color = Be.Colors.textSecondary, fontSize = 11.sp)
            }
            OverlayField(chat, { chat = it }, "聊天", 200, KeyboardType.Text)
            OverlayAction("发送") {
                val text = chat.trim()
                if (text.isNotEmpty()) {
                    send(context, nextSequence(), JSONObject().put("op", "chat").put("text", text))
                    chat = ""
                }
            }
        }
    }
}

@Composable
private fun OverlayField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    limit: Int,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.take(limit)) },
        singleLine = true,
        textStyle = TextStyle(color = Be.Colors.textPrimary, fontSize = 12.sp),
        cursorBrush = SolidColor(Be.Colors.textPrimary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth()
            .background(Be.Colors.overlayField, RoundedCornerShape(3.dp))
            .border(1.dp, Be.Colors.overlayOutline, RoundedCornerShape(3.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(hint, color = Be.Colors.textSecondary, fontSize = 11.sp)
            inner()
        },
    )
}

private fun send(context: android.content.Context, sequence: Long, body: JSONObject) {
    val directory = File(context.filesDir, "eflink")
    if (!directory.isDirectory && !directory.mkdirs()) return
    body.put("seq", sequence)
    val temporary = File(directory, "command.json.tmp")
    temporary.writeText(body.toString())
    val target = File(directory, "command.json")
    if (!temporary.renameTo(target)) {
        target.writeText(temporary.readText())
        temporary.delete()
    }
}

private data class EflinkPlayer(
    val name: String,
    val local: Boolean,
    val host: Boolean,
    val position: String,
    val distance: String,
) {
    fun line(): String {
        val who = (if (local) "我" else name.ifEmpty { "玩家" }) + if (host) " · 房主" else ""
        return listOf(who, position, distance).filter { it.isNotEmpty() }.joinToString(" · ")
    }
}

private data class EflinkRoom(
    val name: String,
    val host: String,
    val address: String,
    val players: Int,
    val max: Int,
    val locked: Boolean,
)

private data class EflinkSnapshot(
    val present: Boolean,
    val role: String,
    val room: String,
    val error: String,
    val poseReady: Boolean,
    val udp: Int,
    val tcpOnly: Int,
    val players: List<EflinkPlayer>,
    val rooms: List<EflinkRoom>,
    val chat: List<String>,
) {
    fun headline(): String {
        if (!present) return "联机模块还没运行。在工具页开启后重启游戏。"
        val where = when (role) {
            "host" -> "房主"
            "client" -> "已加入"
            "connecting" -> "连接中"
            else -> "未连接"
        }
        val path = when {
            udp > 0 -> "UDP"
            tcpOnly > 0 -> "仅 TCP"
            else -> "等待 UDP"
        }
        val pose = if (poseReady) "已采样姿态" else "尚未采样到角色"
        val title = if (room.isEmpty()) where else "$where · $room"
        return "$title · $path · $pose"
    }

    companion object {
        fun missing() = EflinkSnapshot(false, "offline", "", "", false, 0, 0, emptyList(), emptyList(), emptyList())

        fun read(file: File): EflinkSnapshot {
            if (!file.isFile) return missing()
            return try {
                val json = JSONObject(file.readText())
                val players = buildList {
                    val array = json.optJSONArray("players")
                    if (array != null) for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val position = if (item.has("x")) {
                            "x ${item.optDouble("x").toInt()}  y ${item.optDouble("y").toInt()}  z ${item.optDouble("z").toInt()}" +
                                "  朝向 ${item.optDouble("yaw").toInt()}°  骨骼 ${item.optInt("bones")}"
                        } else ""
                        val distance = if (item.has("distance")) String.format("%.1f m", item.optDouble("distance")) else ""
                        add(EflinkPlayer(item.optString("name"), item.optBoolean("local"),
                            item.optBoolean("host"), position, distance))
                    }
                }
                val rooms = buildList {
                    val array = json.optJSONArray("rooms")
                    if (array != null) for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        add(EflinkRoom(item.optString("name"), item.optString("host"), item.optString("address"),
                            item.optInt("players"), item.optInt("max"), item.optBoolean("password")))
                    }
                }
                val chat = buildList {
                    val array = json.optJSONArray("chat")
                    if (array != null) for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val speaker = item.optString("name")
                        val text = item.optString("text")
                        add(if (item.optBoolean("system") || speaker.isEmpty()) text else "$speaker: $text")
                    }
                }
                EflinkSnapshot(true, json.optString("role"), json.optString("room_name"), json.optString("error"),
                    json.optBoolean("pose_ready"), json.optInt("udp"), json.optInt("tcp_only"), players, rooms, chat)
            } catch (_: Throwable) {
                missing()
            }
        }
    }
}
