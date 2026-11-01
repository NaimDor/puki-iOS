package io.github.tomsadeh.puki.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** Under this many dp tall the screen is a phone on its side: games have to drop what's optional. */
private const val COMPACT_HEIGHT_DP = 440

/** True on a short screen (the tablet isn't; the S25 is). */
@Composable
fun rememberCompact(): Boolean = LocalConfiguration.current.screenHeightDp < COMPACT_HEIGHT_DP
