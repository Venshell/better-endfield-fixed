package dev.betterendfield.next

import androidx.compose.runtime.Composable

/** The app palette is reused only from Compose code, never from the Java hook path. */
@Composable
internal fun OverlayTheme(content: @Composable () -> Unit) = BetterEndfieldTheme(content)
