package dev.betterendfield.next

import org.json.JSONObject
import java.net.InetSocketAddress
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** One JSON line to the internet lobby. The certificate hostname must match the saved server. */
object EflinkAccount {
    fun call(payload: JSONObject, endpoint: String): JSONObject {
        payload.put("game", "endfield")
        val host = endpoint.substringBefore(':')
        val port = endpoint.substringAfter(':', "47617").toIntOrNull() ?: return JSONObject().put("error", "大厅地址须为 host:port")
        if (host.isEmpty() || port !in 1..65535) return JSONObject().put("error", "大厅地址须为 host:port")
        return try {
            val socket = SSLSocketFactory.getDefault().createSocket() as SSLSocket
            socket.use {
                it.connect(InetSocketAddress(host, port), 8000)
                val parameters = it.sslParameters
                parameters.endpointIdentificationAlgorithm = "HTTPS"
                it.sslParameters = parameters
                it.soTimeout = 8000
                it.startHandshake()
                it.outputStream.apply {
                    write((payload.toString() + "\n").toByteArray(Charsets.UTF_8))
                    flush()
                }
                val line = it.inputStream.bufferedReader(Charsets.UTF_8).readLine()
                if (line.isNullOrEmpty()) JSONObject().put("error", "连不上联机服务器")
                else {
                    val response = JSONObject(line)
                    if (response.optString("error") == "tls-required") response.put("error", "联机服务器需要加密连接")
                    response
                }
            }
        } catch (_: SSLException) {
            JSONObject().put("error", "无法验证联机服务器")
        } catch (_: CertificateException) {
            JSONObject().put("error", "无法验证联机服务器")
        } catch (_: Exception) {
            JSONObject().put("error", "连不上联机服务器")
        }
    }
}
