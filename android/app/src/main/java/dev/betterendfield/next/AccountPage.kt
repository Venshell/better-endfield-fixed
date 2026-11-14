package dev.betterendfield.next

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** The profile frame's meter is 76 of 304, which is the free cap of 2 against 8. */
private const val FreeRoomCap = 2
private const val BenefitMeter = 76f / 304f

@Composable
fun AccountPage(state: SettingsState) {
    val settings = stringResource(R.string.account_settings)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Be.Space.stack)) {
        Row(
            Modifier.fillMaxWidth().padding(top = Be.Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.page_user),
                color = Be.Colors.textPrimary,
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            val gear = rememberPressed(true) { state.openPage(SettingsPage.ABOUT) }
            Box(
                Modifier
                    .size(32.dp)
                    .semantics { contentDescription = settings }
                    .then(gear.modifier),
                contentAlignment = Alignment.Center,
            ) {
                GearMark(Modifier.size(18.dp), Color(0xFFDDDDDD))
            }
        }
        if (state.accountUsername.isEmpty()) SignIn(state) else SignedIn(state)
    }
}

@Composable
private fun SignIn(state: SettingsState) {
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
            val response = withContext(Dispatchers.IO) { EflinkAccount.call(payload, state.lobbyEndpoint()) }
            busy = false
            val error = response.optString("error")
            if (error.isNotEmpty()) {
                state.showAccountStatus(error)
                return@launch
            }
            if (onSession && response.optString("op") == "session") {
                state.applyAccount(
                    response.optString("token"),
                    response.optString("username"),
                    response.optString("email"),
                    response.optLong("expires"),
                )
            } else if (response.optString("op") == "sent") {
                state.showAccountStatus("验证码已发送")
            } else {
                state.showAccountStatus("连不上联机服务器")
            }
        }
    }
    PanelCard {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Be.Space.m)) {
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
    LaunchedEffect(state.accountUsername) {
        if (state.accountPastExpiry()) {
            state.expireAccount()
            return@LaunchedEffect
        }
        val token = state.accountToken()
        if (token.isEmpty()) return@LaunchedEffect
        val response = withContext(Dispatchers.IO) {
            EflinkAccount.call(JSONObject().put("op", "list").put("account", token), state.lobbyEndpoint())
        }
        if (response.optString("error") == "login-required") state.expireAccount()
    }
    ProfileCard(state.accountUsername, maskEmail(state.accountEmail))
    BenefitsCard()
    CommunityCard()
    AccountRow(
        title = stringResource(R.string.account_security),
        minHeight = 62.dp,
        radius = Be.Radius.row,
        onClick = state::clearAccount,
        icon = { ShieldMark(Modifier.size(22.dp), Color(0xFFDDDDDD)) },
    )
    AccountRow(
        title = stringResource(R.string.account_help),
        minHeight = 48.dp,
        radius = Be.Radius.compact,
        onClick = { state.openPage(SettingsPage.ABOUT) },
        icon = { HelpMark(Modifier.size(18.dp), Color(0xFFDDDDDD)) },
    )
}

@Composable
private fun ProfileCard(name: String, email: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.card))
            .background(Be.Colors.panel)
            .padding(horizontal = 21.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(58.dp).clip(CircleShape).background(Be.Colors.field),
            contentAlignment = Alignment.Center,
        ) {
            PersonMark(Modifier.size(28.dp), Color(0xFFDDDDDD))
        }
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Be.Colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (email.isNotEmpty()) {
                Text(
                    email,
                    color = Be.Colors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = stringResource(R.string.account_verified),
                    color = Be.Colors.accent,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(Be.Radius.chip))
                        .background(Color(0xFF292900))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Chevron(Modifier.size(16.dp), Color(0xFFDDDDDD))
    }
}

@Composable
private fun BenefitsCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.card))
            .background(Be.Colors.panel)
            .padding(21.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.account_benefits),
                color = Be.Colors.textPrimary,
                fontSize = Be.Type.rowTitle,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.account_tier_free),
                color = Be.Colors.textSecondary,
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(Be.Radius.badge))
                    .background(Be.Colors.field)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Row(
            Modifier.padding(top = 22.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = FreeRoomCap.toString(),
                color = Be.Colors.textPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.account_cap_suffix),
                color = Be.Colors.textSecondary,
                fontSize = Be.Type.body,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            )
        }
        Box(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(Be.Radius.progress))
                .background(Be.Colors.track),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(BenefitMeter)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(Be.Radius.progress))
                    .background(Be.Colors.accent),
            )
        }
        Text(
            text = stringResource(R.string.account_free_host),
            color = Be.Colors.textSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = stringResource(R.string.account_free_join),
            color = Be.Colors.textSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(Be.Radius.button))
                .background(Be.Colors.accent)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CrownMark(Modifier.size(16.dp), Be.Colors.accentInk)
            Text(
                text = stringResource(R.string.account_benefits_more),
                color = Be.Colors.accentInk,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
            ExternalMark(Modifier.size(14.dp), Be.Colors.accentInk)
        }
    }
}

@Composable
private fun CommunityCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Be.Radius.card))
            .background(Be.Colors.panel)
            .padding(horizontal = 21.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.account_community),
                color = Be.Colors.textPrimary,
                fontSize = Be.Type.rowTitle,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.account_community_all),
                color = Be.Colors.textSecondary,
                fontSize = 11.sp,
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            CommunityItem(stringResource(R.string.account_posts), Modifier.weight(1f)) { PostsMark(Modifier.size(24.dp), Color(0xFFDDDDDD)) }
            CommunityItem(stringResource(R.string.account_replies), Modifier.weight(1f)) { ChatMark(Modifier.size(24.dp), Color(0xFFDDDDDD)) }
            CommunityItem(stringResource(R.string.account_notices), Modifier.weight(1f)) { BellMark(Modifier.size(24.dp), Color(0xFFDDDDDD)) }
        }
    }
}

@Composable
private fun CommunityItem(label: String, modifier: Modifier, icon: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        icon()
        Text(
            text = label,
            color = Be.Colors.textPrimary,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun AccountRow(
    title: String,
    minHeight: androidx.compose.ui.unit.Dp,
    radius: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val press = rememberPressed(true, onClick)
    Row(
        Modifier
            .pressScale(press.value)
            .fillMaxWidth()
            .height(minHeight)
            .clip(RoundedCornerShape(radius))
            .background(pressedFill(press.value, Be.Colors.panel, Be.Colors.rowHigh))
            .then(press.modifier)
            .padding(horizontal = 21.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(
            text = title,
            color = Be.Colors.textPrimary,
            fontSize = Be.Type.body,
            modifier = Modifier.padding(start = 14.dp).weight(1f),
        )
        Chevron(Modifier.size(16.dp), Color(0xFFDDDDDD))
    }
}

private fun maskEmail(email: String): String {
    val at = email.indexOf('@')
    if (at <= 0) return email
    val local = email.substring(0, at)
    val domain = email.substring(at)
    val keep = if (local.length <= 6) 1 else 3
    val tail = if (local.length > keep * 2) local.takeLast(keep) else ""
    return local.take(keep) + "****" + tail + domain
}

@Composable
private fun GearMark(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round)
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color, radius = size.minDimension * 0.18f, center = c, style = stroke)
        val ray = size.minDimension * 0.34f
        val gap = size.minDimension * 0.22f
        for (index in 0 until 8) {
            val angle = Math.toRadians(index * 45.0)
            val dx = kotlin.math.cos(angle).toFloat()
            val dy = kotlin.math.sin(angle).toFloat()
            drawLine(color, c + Offset(dx * gap, dy * gap), c + Offset(dx * ray, dy * ray), stroke.width, StrokeCap.Round)
        }
    }
}

@Composable
private fun Chevron(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(size.width * 0.35f, size.height * 0.2f)
            lineTo(size.width * 0.68f, size.height * 0.5f)
            lineTo(size.width * 0.35f, size.height * 0.8f)
        }
        drawPath(path, color, style = stroke)
    }
}

@Composable
private fun PersonMark(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(color, radius = size.minDimension * 0.16f, center = Offset(size.width / 2f, size.height * 0.34f), style = stroke)
        drawArc(
            color,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(size.width * 0.18f, size.height * 0.52f),
            size = Size(size.width * 0.64f, size.height * 0.42f),
            style = stroke,
        )
    }
}

@Composable
private fun ShieldMark(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(size.width / 2f, size.height * 0.12f)
            lineTo(size.width * 0.82f, size.height * 0.28f)
            lineTo(size.width * 0.82f, size.height * 0.52f)
            quadraticTo(size.width * 0.82f, size.height * 0.78f, size.width / 2f, size.height * 0.9f)
            quadraticTo(size.width * 0.18f, size.height * 0.78f, size.width * 0.18f, size.height * 0.52f)
            lineTo(size.width * 0.18f, size.height * 0.28f)
            close()
        }
        drawPath(path, color, style = stroke)
        val check = Path().apply {
            moveTo(size.width * 0.34f, size.height * 0.5f)
            lineTo(size.width * 0.46f, size.height * 0.64f)
            lineTo(size.width * 0.68f, size.height * 0.38f)
        }
        drawPath(check, color, style = stroke)
    }
}

@Composable
private fun HelpMark(modifier: Modifier, color: Color) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(color, radius = size.minDimension / 2f - stroke.width, style = stroke)
        }
        Text("?", color = Be.Colors.textPrimary, fontSize = 12.sp)
    }
}

@Composable
private fun CrownMark(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(size.width * 0.15f, size.height * 0.72f)
            lineTo(size.width * 0.28f, size.height * 0.28f)
            lineTo(size.width * 0.5f, size.height * 0.58f)
            lineTo(size.width * 0.72f, size.height * 0.28f)
            lineTo(size.width * 0.85f, size.height * 0.72f)
        }
        drawPath(path, color, style = stroke)
        drawLine(color, Offset(size.width * 0.28f, size.height * 0.72f), Offset(size.width * 0.72f, size.height * 0.72f), stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun ExternalMark(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(size.width * 0.72f, size.height * 0.28f)
            lineTo(size.width * 0.28f, size.height * 0.72f)
            moveTo(size.width * 0.28f, size.height * 0.28f)
            lineTo(size.width * 0.72f, size.height * 0.28f)
            lineTo(size.width * 0.72f, size.height * 0.72f)
        }
        drawPath(path, color, style = stroke)
    }
}

@Composable
private fun PostsMark(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            color,
            topLeft = Offset(size.width * 0.22f, size.height * 0.12f),
            size = Size(size.width * 0.56f, size.height * 0.76f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            style = stroke,
        )
        drawLine(color, Offset(size.width * 0.34f, size.height * 0.34f), Offset(size.width * 0.66f, size.height * 0.34f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.34f, size.height * 0.5f), Offset(size.width * 0.66f, size.height * 0.5f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.34f, size.height * 0.66f), Offset(size.width * 0.56f, size.height * 0.66f), stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun ChatMark(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(size.width * 0.16f, size.height * 0.22f)
            lineTo(size.width * 0.84f, size.height * 0.22f)
            lineTo(size.width * 0.84f, size.height * 0.62f)
            lineTo(size.width * 0.48f, size.height * 0.62f)
            lineTo(size.width * 0.28f, size.height * 0.82f)
            lineTo(size.width * 0.28f, size.height * 0.62f)
            lineTo(size.width * 0.16f, size.height * 0.62f)
            close()
        }
        drawPath(path, color, style = stroke)
        drawLine(color, Offset(size.width * 0.3f, size.height * 0.38f), Offset(size.width * 0.7f, size.height * 0.38f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.3f, size.height * 0.5f), Offset(size.width * 0.56f, size.height * 0.5f), stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun BellMark(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(size.width / 2f, size.height * 0.14f)
            quadraticTo(size.width * 0.18f, size.height * 0.28f, size.width * 0.18f, size.height * 0.48f)
            lineTo(size.width * 0.18f, size.height * 0.68f)
            lineTo(size.width * 0.82f, size.height * 0.68f)
            lineTo(size.width * 0.82f, size.height * 0.48f)
            quadraticTo(size.width * 0.82f, size.height * 0.28f, size.width / 2f, size.height * 0.14f)
        }
        drawPath(path, color, style = stroke)
        drawArc(
            color,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(size.width * 0.36f, size.height * 0.64f),
            size = Size(size.width * 0.28f, size.height * 0.22f),
            style = stroke,
        )
    }
}
