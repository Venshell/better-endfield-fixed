package dev.betterendfield.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
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
            Text("预览不连接房间。进入游戏后可以创建局域网或互联网房间，并看到其他玩家。",
                color = Be.Colors.textSecondary, fontSize = 11.sp)
        }
        return
    }
    val context = LocalContext.current
    var status by remember { mutableStateOf(EflinkSnapshot.missing()) }
    var page by remember { mutableStateOf("home") }
    var scope by remember { mutableStateOf("lan") }
    var room by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var chat by remember { mutableStateOf("") }
    var players by remember { mutableStateOf(8) }
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
    val inRoom = status.role == "host" || status.role == "client" || status.role == "connecting"
    OverlaySection("联机") {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (!status.present) {
                Text("联机还没启动。在工具页开启后重启游戏。",
                    color = Be.Colors.textSecondary, fontSize = 11.sp)
            } else if (inRoom) {
                RoomScreen(status, chat, { chat = it }) {
                    val text = chat.trim()
                    if (text.isNotEmpty()) {
                        send(context, nextSequence(), JSONObject().put("op", "chat").put("text", text))
                        chat = ""
                    }
                }
                OverlayAction("离开房间") {
                    send(context, nextSequence(), JSONObject().put("op", "leave"))
                    page = "home"
                }
            } else when (page) {
                "create" -> CreateScreen(
                    room, { room = it }, password, { password = it }, scope, { scope = it },
                    players, { players = it }, status,
                ) {
                    send(context, nextSequence(), JSONObject()
                        .put("op", "host")
                        .put("network", scope)
                        .put("room_name", room.ifBlank { "房间" })
                        .put("password", password)
                        .put("max_players", players))
                    password = ""
                }
                "browse" -> BrowseScreen(status, scope, { scope = it }, password, { password = it }, address, { address = it }) { found ->
                    if (found.roomId != 0L) {
                        send(context, nextSequence(), JSONObject()
                            .put("op", "join_lobby").put("room_id", found.roomId).put("password", password))
                    } else {
                        send(context, nextSequence(), JSONObject()
                            .put("op", "join").put("address", found.address).put("password", password))
                    }
                    password = ""
                }
                else -> HomeScreen(status, { page = "create" }, { page = "browse" })
            }
            if (!inRoom && page != "home" && status.present) {
                OverlayAction("返回") { page = "home" }
            }
            if (status.error.isNotEmpty()) {
                Text(status.error, color = Be.Colors.textSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun HomeScreen(status: EflinkSnapshot, onCreate: () -> Unit, onJoin: () -> Unit) {
    Text(if (status.lobbyReady) "局域网或互联网" else "当前可创建局域网房间",
        color = Be.Colors.textSecondary, fontSize = 11.sp)
    OverlayAction("创建房间", onClick = onCreate)
    OverlayAction("加入房间", onClick = onJoin)
}

@Composable
private fun CreateScreen(
    room: String, onRoom: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    scope: String, onScope: (String) -> Unit,
    players: Int, onPlayers: (Int) -> Unit,
    status: EflinkSnapshot,
    onCreate: () -> Unit,
) {
    Text("创建房间", color = Be.Colors.textPrimary, fontSize = 13.sp)
    NetworkChoice(scope, onScope, status.lobbyReady)
    OverlayField(room, onRoom, "房间名", 64)
    OverlayField(password, onPassword, "密码，可留空", 64)
    Segmented(players == 4, "4 人", "8 人", { onPlayers(4) }, { onPlayers(8) })
    val internetBlocked = scope == "internet" && !status.lobbyReady
    if (internetBlocked) {
        Text("互联网需要先在工具页填写大厅地址，保存后重启游戏。",
            color = Be.Colors.textSecondary, fontSize = 11.sp)
    }
    OverlayAction("创建", enabled = !internetBlocked) { onCreate() }
}

@Composable
private fun BrowseScreen(
    status: EflinkSnapshot,
    scope: String, onScope: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    address: String, onAddress: (String) -> Unit,
    onJoin: (EflinkRoom) -> Unit,
) {
    val context = LocalContext.current
    var sequence by remember { mutableLongStateOf(0L) }
    Text("加入房间", color = Be.Colors.textPrimary, fontSize = 13.sp)
    NetworkChoice(scope, onScope, status.lobbyReady)
    val rooms = status.rooms.filter { if (scope == "internet") it.roomId != 0L else it.roomId == 0L }
    if (rooms.isEmpty()) {
        Text(if (scope == "internet") "互联网上还没有房间。" else "附近还没有局域网房间。",
            color = Be.Colors.textSecondary, fontSize = 11.sp)
    }
    rooms.forEach { found ->
        val lock = if (found.locked) "  ·  需要密码" else ""
        OverlayAction("${found.name}    ${found.players}/${found.max}$lock") { onJoin(found) }
    }
    if (scope == "lan") {
        OverlayField(address, onAddress, "或输入 IP:端口", 64, KeyboardType.Uri)
        OverlayAction("用地址加入", enabled = address.isNotBlank()) {
            onJoin(EflinkRoom("", "", address, 0, 0, false, 0))
        }
        OverlayAction("刷新附近房间") {
            val now = System.currentTimeMillis()
            sequence = if (now > sequence) now else sequence + 1
            send(context, sequence, JSONObject().put("op", "refresh"))
        }
    }
    if (rooms.any { it.locked } || password.isNotEmpty()) {
        OverlayField(password, onPassword, "房间密码", 64)
    }
}

@Composable
private fun RoomScreen(status: EflinkSnapshot, chat: String, onChat: (String) -> Unit, onSend: () -> Unit) {
    val kind = if (status.network == "internet") "互联网" else "局域网"
    val role = when (status.role) {
        "host" -> "房主"
        "connecting" -> "连接中"
        else -> "已加入"
    }
    Text("${status.room.ifBlank { "房间" }}  ·  $kind  ·  $role",
        color = Be.Colors.textPrimary, fontSize = 13.sp)
    Text(if (status.poseReady) "位置已同步" else "正在同步位置",
        color = Be.Colors.textSecondary, fontSize = 11.sp)
    if (status.players.isEmpty()) {
        Text("等待其他玩家。", color = Be.Colors.textSecondary, fontSize = 11.sp)
    }
    status.players.forEach { player ->
        Text(player.line(), color = Be.Colors.textPrimary, fontSize = 11.sp)
    }
    status.chat.takeLast(4).forEach { line ->
        Text(line, color = Be.Colors.textSecondary, fontSize = 11.sp)
    }
    OverlayField(chat, onChat, "说点什么", 200)
    OverlayAction("发送", onClick = onSend)
}

@Composable
private fun NetworkChoice(scope: String, onScope: (String) -> Unit, internetReady: Boolean) {
    Segmented(scope == "lan", "局域网", "互联网", { onScope("lan") }, { onScope("internet") })
    Text(
        if (scope == "lan") "同一 Wi-Fi 下的房间" else if (internetReady) "通过你的服务器匹配" else "尚未配置服务器",
        color = Be.Colors.textSecondary, fontSize = 11.sp,
    )
}

@Composable
private fun Segmented(
    leftSelected: Boolean,
    left: String,
    right: String,
    onLeft: () -> Unit,
    onRight: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Segment(left, leftSelected, onLeft, Modifier.weight(1f))
        Segment(right, !leftSelected, onRight, Modifier.weight(1f))
    }
}

@Composable
private fun Segment(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier.height(30.dp)
            .background(if (selected) Be.Colors.overlayPressed else Be.Colors.overlayField, RoundedCornerShape(3.dp))
            .border(1.dp, Be.Colors.overlayOutline, RoundedCornerShape(3.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Be.Colors.textPrimary, fontSize = 12.sp)
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
    val roomId: Long = 0,
)

private data class EflinkSnapshot(
    val present: Boolean,
    val role: String,
    val room: String,
    val error: String,
    val poseReady: Boolean,
    val udp: Int,
    val tcpOnly: Int,
    val network: String,
    val lobbyReady: Boolean,
    val players: List<EflinkPlayer>,
    val rooms: List<EflinkRoom>,
    val chat: List<String>,
) {
    companion object {
        fun missing() = EflinkSnapshot(false, "offline", "", "", false, 0, 0, "", false, emptyList(), emptyList(), emptyList())

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
                            item.optInt("players"), item.optInt("max"), item.optBoolean("password"),
                            item.optLong("room_id")))
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
                    json.optBoolean("pose_ready"), json.optInt("udp"), json.optInt("tcp_only"),
                    json.optString("network"), json.optBoolean("lobby_ready"), players, rooms, chat)
            } catch (_: Throwable) {
                missing()
            }
        }
    }
}
