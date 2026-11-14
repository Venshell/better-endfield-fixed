package dev.betterendfield.next

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun AccountPage(state: SettingsState) {
    if (state.accountUsername.isNotEmpty()) {
        SignedIn(state)
        return
    }
    var mode by remember { mutableStateOf("login") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun submit(payload: JSONObject, onSession: Boolean) {
        if (busy) return
        busy = true
        state.showAccountStatus("")
        scope.launch {
            val response = withContext(Dispatchers.IO) { EflinkAccount.call(payload) }
            busy = false
            val error = response.optString("error")
            if (error.isNotEmpty()) {
                state.showAccountStatus(error)
                return@launch
            }
            if (onSession && response.optString("op") == "session") {
                state.applyAccount(response.optString("token"), response.optString("username"), response.optString("email"))
            } else if (response.optString("op") == "sent") {
                state.showAccountStatus("验证码已发送")
            } else {
                state.showAccountStatus("连不上联机服务器")
            }
        }
    }
    PanelCard {
        CardTitle("用户")
        Column(Modifier.fillMaxWidth().padding(top = Be.Space.l), verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Be.Space.m)) {
                if (mode == "login") PrimaryButton("登录", { mode = "login" }, Modifier.weight(1f))
                else GhostButton("登录", { mode = "login" }, Modifier.weight(1f))
                if (mode == "register") PrimaryButton("注册", { mode = "register" }, Modifier.weight(1f))
                else GhostButton("注册", { mode = "register" }, Modifier.weight(1f))
            }
            TextFieldRow(username, { username = it }, hint = "用户名", keyboardType = KeyboardType.Text, maxLength = 16)
            TextFieldRow(
                password, { password = it }, hint = "密码", keyboardType = KeyboardType.Password,
                visualTransformation = PasswordVisualTransformation(), maxLength = 128,
            )
            if (mode == "register") {
                TextFieldRow(email, { email = it }, hint = "邮箱", keyboardType = KeyboardType.Email, maxLength = 254)
                PrimaryButton(
                    "发送验证码",
                    onClick = {
                        submit(JSONObject().put("op", "register").put("username", username.trim()).put("password", password).put("email", email.trim()), false)
                    },
                    enabled = !busy && username.isNotBlank() && password.isNotBlank() && email.isNotBlank(),
                )
                TextFieldRow(
                    code, { code = it.filter { ch -> ch.isDigit() }.take(6) },
                    hint = "验证码", keyboardType = KeyboardType.Number, maxLength = 6,
                )
                PrimaryButton(
                    "注册",
                    onClick = { submit(JSONObject().put("op", "verify").put("username", username.trim()).put("code", code), true) },
                    enabled = !busy && code.length == 6 && username.isNotBlank(),
                )
            } else {
                PrimaryButton(
                    "登录",
                    onClick = { submit(JSONObject().put("op", "login").put("username", username.trim()).put("password", password), true) },
                    enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                )
            }
            if (state.accountStatus.isNotEmpty()) BodyText(state.accountStatus)
        }
    }
}

@Composable
private fun SignedIn(state: SettingsState) {
    PanelCard {
        CardTitle(state.accountUsername)
        BodyText(state.accountEmail, modifier = Modifier.padding(top = Be.Space.m))
        GhostButton(
            text = "退出登录",
            onClick = state::clearAccount,
            modifier = Modifier.fillMaxWidth().padding(top = Be.Space.l),
        )
    }
}
