package com.gmoqa.fullset.ui

import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler as AndroidBackHandler

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) = AndroidBackHandler(enabled, onBack)
