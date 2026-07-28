package com.gmoqa.fullset.ui

import androidx.compose.runtime.Composable

// iOS no tiene un botón de retroceso del sistema; la navegación la maneja la UI. No-op.
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
}
