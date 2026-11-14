package dev.betterendfield.next

import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket

/** One JSON line to the fixed internet lobby. The app does not check the mail address. */
object EflinkAccount {
    private const val HOST = "game.venshell.top"
    private const val PORT = 47617

    fun call(payload: JSONObject): JSONObject {
        payload.put("game", "endfield")
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(HOST, PORT), 8000)
                socket.soTimeout = 8000
                socket.getOutputStream().apply {
                    write((payload.toString() + "\n").toByteArray(Charsets.UTF_8))
                    flush()
                }
                val line = socket.getInputStream().bufferedReader(Charsets.UTF_8).readLine()
                if (line.isNullOrEmpty()) JSONObject().put("error", "连不上联机服务器")
                else JSONObject(line)
            }
        } catch (_: Exception) {
            JSONObject().put("error", "连不上联机服务器")
        }
    }
}
