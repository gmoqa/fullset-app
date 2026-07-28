package com.gmoqa.fullset.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

// iOS: el ancho sale del contenedor de la ventana (LocalWindowInfo, en píxeles) pasado a dp.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun isCompactWidth(): Boolean {
    val widthPx = LocalWindowInfo.current.containerSize.width
    val widthDp = with(LocalDensity.current) { widthPx.toDp() }
    return widthDp < COMPACT_WIDTH_DP.dp
}
