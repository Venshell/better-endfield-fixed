package dev.betterendfield.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
internal fun FloatingHandle(
    onClick: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var lastWindowPoint by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { coordinates = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { point ->
                        lastWindowPoint = coordinates?.localToWindow(point) ?: point
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                ) { change, _ ->
                    change.consume()
                    // The Android touch target is moved by the controller. Local
                    // deltas would lose that movement and make the handle lag.
                    val windowPoint = coordinates?.localToWindow(change.position)
                        ?: change.position
                    val delta = windowPoint - lastWindowPoint
                    lastWindowPoint = windowPoint
                    onDrag(delta.x, delta.y)
                }
            }
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Better Endfield 控制面板：点击展开，拖动可移动" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.width(42.dp).height(42.dp)
                .background(Be.Colors.overlayHandle, CircleShape)
                .border(1.dp, Be.Colors.overlayHandleOutline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.overlay_settings_gear), null,
                modifier = Modifier.width(26.dp).height(26.dp), contentScale = ContentScale.Fit)
        }
    }
}
