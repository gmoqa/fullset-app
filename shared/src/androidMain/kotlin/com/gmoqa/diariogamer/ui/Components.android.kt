package com.gmoqa.diariogamer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@Composable
actual fun isCompactWidth(): Boolean = LocalConfiguration.current.screenWidthDp < COMPACT_WIDTH_DP
