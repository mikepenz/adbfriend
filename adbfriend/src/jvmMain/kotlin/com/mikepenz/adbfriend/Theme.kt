package com.mikepenz.adbfriend

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable


@Composable
fun AdbFriendTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (!useDarkTheme) {
        lightColorScheme()
    } else {
        darkColorScheme()
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}