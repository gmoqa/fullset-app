package com.gmoqa.fullset.ui

import com.gmoqa.fullset.data.RegionFilter
import com.gmoqa.fullset.data.SortOrder
import com.gmoqa.fullset.data.ThemeMode
import com.gmoqa.fullset.data.TrackingMode

/**
 * Las preferencias del usuario, que **viajan juntas por todas las pantallas**.
 *
 * Estos seis valores y sus seis setters aparecían sueltos en `AppRoot` (12 de sus 16 parámetros) y
 * otra vez en `HomeContent` (12 de 24), solo para llegar a Settings y a las listas. Doce parámetros
 * repetidos en cascada no son un contrato: son ruido que hay que atravesar para leer la firma.
 */
data class Preferencias(
    val themeMode: ThemeMode,
    val regionFilter: RegionFilter,
    val showLabels: Boolean,
    val showConsoleTitles: Boolean,
    val sortOrder: SortOrder,
    val trackingMode: TrackingMode,
)

/** Cómo se cambia cada una. Separado del valor, igual que en [SettingsUiState] / [SettingsActions]. */
data class PreferenciasActions(
    val onThemeChange: (ThemeMode) -> Unit,
    val onRegionChange: (RegionFilter) -> Unit,
    val onShowLabelsChange: (Boolean) -> Unit,
    val onShowConsoleTitlesChange: (Boolean) -> Unit,
    val onSortChange: (SortOrder) -> Unit,
    val onTrackingModeChange: (TrackingMode) -> Unit,
)
