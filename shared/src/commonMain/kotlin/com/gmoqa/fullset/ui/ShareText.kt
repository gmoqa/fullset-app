package com.gmoqa.fullset.ui

import androidx.compose.runtime.Composable

/**
 * Comparte un texto por el share sheet del sistema (para pegarlo en un LLM, guardarlo, mandarlo…).
 * Devuelve la lambda a invocar con el contenido a compartir.
 */
@Composable
expect fun rememberTextSharer(): (String) -> Unit
