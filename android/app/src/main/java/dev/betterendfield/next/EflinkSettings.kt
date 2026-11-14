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
    var server by remember(state.eflinkServer) { mutableStateOf(state.eflinkServer) }
    PanelCard {
        CardTitle(stringResource(R.string.eflink_title))
        BodyText(stringResource(R.string.eflink_hint), modifier = Modifier.padding(top = Be.Space.m))
        SwitchRow(
            title = stringResource(R.string.eflink_enable),
            description = stringResource(R.string.eflink_enable_hint),
            checked = state.eflinkEnabled,
            onCheckedChange = state::updateEflinkEnabled,
        )
        TextFieldRow(
            value = server,
            onValueChange = { server = it },
            hint = stringResource(R.string.eflink_server),
            keyboardType = KeyboardType.Uri,
            maxLength = 128,
            enabled = state.eflinkEnabled,
            onCommit = { state.updateEflinkServer(server) },
        )
        TextFieldRow(
            value = name,
            onValueChange = { name = it },
            hint = stringResource(R.string.eflink_name),
            keyboardType = KeyboardType.Text,
            maxLength = 64,
            enabled = state.eflinkEnabled,
            onCommit = { state.updateEflinkName(name) },
        )
        TextFieldRow(
            value = room,
            onValueChange = { room = it },
            hint = stringResource(R.string.eflink_room),
            keyboardType = KeyboardType.Text,
            maxLength = 64,
            enabled = state.eflinkEnabled,
            onCommit = { state.updateEflinkRoom(room) },
        )
        TextFieldRow(
            value = port,
            onValueChange = { port = it.filter { ch -> ch.isDigit() }.take(5) },
            hint = stringResource(R.string.eflink_port),
            keyboardType = KeyboardType.Number,
            maxLength = 5,
            enabled = state.eflinkEnabled,
            onCommit = { port.toIntOrNull()?.let(state::updateEflinkPort) },
        )
        TextFieldRow(
            value = players,
            onValueChange = { players = it.filter { ch -> ch.isDigit() }.take(2) },
            hint = stringResource(R.string.eflink_players),
            keyboardType = KeyboardType.Number,
            maxLength = 2,
            enabled = state.eflinkEnabled,
            onCommit = { players.toIntOrNull()?.let(state::updateEflinkMaxPlayers) },
        )
        BodyText(
            stringResource(R.string.eflink_restart),
            modifier = Modifier.fillMaxWidth().padding(top = Be.Space.m),
        )
    }
}
