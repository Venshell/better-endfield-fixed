package dev.betterendfield.next

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

private const val WORKSHOP_DIRECT_URL = "https://146.235.16.65:8443/endfield/"
private const val WORKSHOP_DOMAIN_URL = "https://endfield.03250799.xyz/endfield/"

/** Two addresses for the same workshop, kept visible and copyable while offline. */
@Composable
fun WorkshopPage() {
    val context = LocalContext.current
    var status by remember { mutableStateOf("") }

    fun open(url: String) {
        status = if (runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addCategory(Intent.CATEGORY_BROWSABLE))
            }.isSuccess) "" else context.getString(R.string.next_workshop_failed)
    }

    fun copy(url: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        status = if (clipboard != null && runCatching {
                clipboard.setPrimaryClip(ClipData.newPlainText("workshop", url))
            }.isSuccess) context.getString(R.string.next_workshop_copied)
            else context.getString(R.string.next_workshop_copy_failed)
    }

    PanelCard {
        CardTitle(stringResource(R.string.next_workshop))
    }
    WorkshopEndpointCard(stringResource(R.string.next_workshop_direct), WORKSHOP_DIRECT_URL,
        ::open, ::copy)
    WorkshopEndpointCard(stringResource(R.string.next_workshop_domain), WORKSHOP_DOMAIN_URL,
        ::open, ::copy)
    if (status.isNotBlank()) BodyText(status, Modifier.padding(top = Be.Space.m))
}

@Composable
private fun WorkshopEndpointCard(
    title: String,
    url: String,
    onOpen: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    PanelCard {
        CardTitle(title)
        SelectionContainer {
            BodyText(url, Modifier.padding(top = Be.Space.m))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = Be.Space.l),
            horizontalArrangement = Arrangement.spacedBy(Be.Space.m),
        ) {
            GhostButton(stringResource(R.string.next_workshop_open), { onOpen(url) }, Modifier.weight(1f))
            GhostButton(stringResource(R.string.next_workshop_copy), { onCopy(url) }, Modifier.weight(1f))
        }
    }
}
