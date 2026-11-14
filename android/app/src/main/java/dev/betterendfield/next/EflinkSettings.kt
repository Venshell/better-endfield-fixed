package dev.betterendfield.next

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun EflinkSettingsCard(state: SettingsState) {
    var name by remember(state.eflinkName) { mutableStateOf(state.eflinkName) }
    var room by remember(state.eflinkRoom) { mutableStateOf(state.eflinkRoom) }
    var port by remember(state.eflinkPort) { mutableStateOf(state.eflinkPort.toString()) }
    var players by remember(state.eflinkMaxPlayers) { mutableStateOf(state.eflinkMaxPlayers.toString()) }
    PanelCard {
        CardTitle(stringResource(R.string.eflink_title))
        BodyText(stringResource(R.string.eflink_hint), modifier = Modifier.padding(top = Be.Space.m))
        SwitchRow(
            title = stringResource(R.string.eflink_lan),
            description = stringResource(R.string.eflink_lan_hint),
            checked = state.eflinkLan,
            onCheckedChange = state::updateEflinkLan,
        )
        if (state.eflinkLan) {
            TextFieldRow(
                value = name,
                onValueChange = { name = it },
                hint = stringResource(R.string.eflink_name),
                keyboardType = KeyboardType.Text,
                maxLength = 64,
                onCommit = { state.updateEflinkName(name) },
            )
            TextFieldRow(
                value = room,
                onValueChange = { room = it },
                hint = stringResource(R.string.eflink_room),
                keyboardType = KeyboardType.Text,
                maxLength = 64,
                onCommit = { state.updateEflinkRoom(room) },
            )
            TextFieldRow(
                value = port,
                onValueChange = { port = it.filter { ch -> ch.isDigit() }.take(5) },
                hint = stringResource(R.string.eflink_port),
                keyboardType = KeyboardType.Number,
                maxLength = 5,
                onCommit = { port.toIntOrNull()?.let(state::updateEflinkPort) },
            )
            TextFieldRow(
                value = players,
                onValueChange = { players = it.filter { ch -> ch.isDigit() }.take(2) },
                hint = stringResource(R.string.eflink_players),
                keyboardType = KeyboardType.Number,
                maxLength = 2,
                onCommit = { players.toIntOrNull()?.let(state::updateEflinkMaxPlayers) },
            )
        }
        SwitchRow(
            title = stringResource(R.string.eflink_internet),
            description = stringResource(R.string.eflink_internet_hint),
            checked = state.eflinkInternet,
            onCheckedChange = state::updateEflinkInternet,
        )
        BodyText(
            if (state.accountUsername.isEmpty()) stringResource(R.string.eflink_internet_signed_out)
            else state.accountUsername,
            modifier = Modifier.fillMaxWidth().padding(top = Be.Space.m),
        )
    }
}
