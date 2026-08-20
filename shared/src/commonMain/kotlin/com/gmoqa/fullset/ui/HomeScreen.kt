package com.gmoqa.fullset.ui

import com.gmoqa.fullset.roles.PantallaHome
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gmoqa.fullset.data.GameCatalog
import com.gmoqa.fullset.navigation.AddTarget
import com.gmoqa.fullset.navigation.NavHost
import com.gmoqa.fullset.navigation.rememberBackStack
import com.gmoqa.fullset.navigation.Screen
import com.gmoqa.fullset.data.PlatformImage
import com.gmoqa.fullset.data.Platform
import com.gmoqa.fullset.data.PlatformRegistry
import com.gmoqa.fullset.data.RegionFilter
import com.gmoqa.fullset.data.SortOrder
import com.gmoqa.fullset.data.ThemeMode
import com.gmoqa.fullset.data.TrackingMode
import com.gmoqa.fullset.resources.Res
import com.gmoqa.fullset.resources.ic_eye_search
import com.gmoqa.fullset.resources.ic_shelf
import com.gmoqa.fullset.domain.CatalogMark
import com.gmoqa.fullset.domain.pendientes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * A dónde puede ir el home. Son siete callbacks que solo existen para llegar desde una pestaña a una
 * ruta; sueltos ocupaban un tercio de la firma de [HomeContent].
 */
internal data class Navegacion(
    val onOpenGame: (Long) -> Unit,
    val onOpenTimeline: () -> Unit,
    val onOpenPlatform: (String) -> Unit,
    val onAddLibrary: () -> Unit,
    val onAddWishlist: () -> Unit,
    val onAddDigital: () -> Unit,
    val onAddPlaying: () -> Unit,
)

/**
 * Las secciones del bottom nav. Se identifican por **nombre**, no por posición: el juego de
 * pestañas cambia con el modo (en `DIARY_ONLY` no están Collection ni Wishlist), y guardar el índice
 * hacía que al cambiar de modo el número apuntara a otra sección — el 2 es Playing con cinco
 * pestañas y Settings con tres.
 */
internal enum class HomeTab(val label: String) {
    COLLECTION("Collection"), BACKLOG("Backlog"), PLAYING("Playing"),
    WISHLIST("Wishlist"), SETTINGS("Settings");
}

@Composable
internal fun HomeTab.icon(contentDescription: String?) = when (this) {
    HomeTab.COLLECTION -> Icon(painterResource(Res.drawable.ic_shelf), contentDescription)
    HomeTab.BACKLOG -> Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription)
    HomeTab.PLAYING -> Icon(Icons.Filled.SportsEsports, contentDescription)
    HomeTab.WISHLIST -> Icon(painterResource(Res.drawable.ic_eye_search), contentDescription)
    HomeTab.SETTINGS -> Icon(Icons.Filled.Settings, contentDescription)
}

/** Las pestañas visibles con este modo. Collection y Wishlist son las dos sobre *poseer*. */
internal fun tabsFor(mode: TrackingMode): List<HomeTab> = when (mode) {
    TrackingMode.COLLECTION_AND_DIARY -> HomeTab.entries
    TrackingMode.DIARY_ONLY -> listOf(HomeTab.BACKLOG, HomeTab.PLAYING, HomeTab.SETTINGS)
}


@Composable
internal fun HomeContent(
    vm: PantallaHome,
    platforms: List<Platform>,
    tab: HomeTab,
    onTabChange: (HomeTab) -> Unit,
    prefs: Preferencias,
    prefsActions: PreferenciasActions,
    nav: Navegacion,
    isDebug: Boolean,
) {
    // Estado reactivo: agregar/borrar un juego o wishlist refresca la lista sin navegar.
    val allGames by vm.games.collectAsStateWithLifecycle()
    val allWishlist by vm.wishlist.collectAsStateWithLifecycle()
    // Flag de debug para previsualizar los estados vacíos sin borrar nada (ver Settings → Developer).
    val previewEmpty by vm.previewEmpty.collectAsStateWithLifecycle()
    // Juego recién agregado: Collection lo enfoca (scroll) una sola vez.
    val lastAdded by vm.lastAdded.collectAsStateWithLifecycle()
    val games = if (previewEmpty) emptyList() else allGames
    val wishlist = if (previewEmpty) emptyList() else allWishlist
    // Collection y Backlog son tu colección **física**: los digitales no cuentan como poseídos y
    // viven solo en Playing (donde sí se muestran, con su badge).
    val physical = games.filter { !it.digital }
    // Modelo de transcripción (sección Voice notes en Settings).
    val installedModel by vm.installedModel.collectAsStateWithLifecycle()
    val modelDownload by vm.modelDownload.collectAsStateWithLifecycle()
    val transcriptionLanguage by vm.transcriptionLanguage.collectAsStateWithLifecycle()
    val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()
    // Cuántas fotos hay en total: sale de la lista reactiva (cada juego ya trae su conteo), así que
    // se actualiza sola y no hace falta consultar la BD para decidir si preguntar el alcance.
    val photoCount = remember(games) { games.sumOf { it.photoCount } }

    val compact = isCompactWidth()

    // El pager es la fuente de verdad del tab. Se puede deslizar entre páginas (swipe) y la
    // bottom nav anima hacia la página elegida. Se persiste el índice para restaurarlo.
    val tabs = remember(prefs.trackingMode) { tabsFor(prefs.trackingMode) }
    // La pestaña guardada es una **identidad**, no un número: al cambiar de modo la lista se achica
    // y un índice viejo apuntaría a otra sección. Si la guardada ya no está visible, se cae a la
    // primera del modo actual en vez de quedar fuera de rango.
    val pagerState = rememberPagerState(
        initialPage = tabs.indexOf(tab).coerceAtLeast(0),
        pageCount = { tabs.size },
    )
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState.currentPage) { onTabChange(tabs[pagerState.currentPage]) }
    // Cambiar de modo con una pestaña que desaparece: el pager se reencuadra solo.
    LaunchedEffect(tabs) {
        if (tab !in tabs) pagerState.scrollToPage(0)
    }

    Scaffold(
        bottomBar = {
            Column {
                // Filete en vez de una superficie más clara: la barra se separa de la página con
                // una línea, no con un bloque de otro color. Es lo mismo que hacen las listas.
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            // El nombre va siempre como descripción accesible, se muestre o no.
                            icon = { item.icon(item.label) },
                            // En pantallas angostas, solo iconos: cinco etiquetas no entran sin apretarse.
                            label = if (compact) null else ({ Text(item.label) }),
                            // Sin la pastilla rellena detrás del ícono activo. Es la firma de
                            // Material You y fecha la app en su año; el estado activo se dice con
                            // el color, que es como se viene diciendo desde que existen las barras
                            // de pestañas.
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Solo inset inferior (nav bar): cada pantalla lleva su header con statusBarsPadding,
        // así el status bar se cuenta una sola vez (antes se duplicaba con el TopAppBar).
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
            key = { it },
        ) { page ->
            when (tabs[page]) {
                HomeTab.COLLECTION -> LibraryScreen(
                    games = physical,
                    onOpenGame = nav.onOpenGame,
                    onAddPhysical = nav.onAddLibrary,
                    // Al agregar un juego, Collection sube hasta él en vez de dejarte donde estabas.
                    focusGameId = lastAdded,
                    onFocusConsumed = { vm.consumeLastAdded() },
                    // Tocar una franja abre la vista propia de esa plataforma.
                    onOpenPlatform = nav.onOpenPlatform,
                    // Opciones de vista (Settings → Collection): ocultar labels y/o franjas de consola.
                    showLabels = prefs.showLabels,
                    showConsoleTitles = prefs.showConsoleTitles,
                    sortOrder = prefs.sortOrder,
                    onSortChange = prefsActions.onSortChange,
                )
                HomeTab.BACKLOG -> {
                    // La regla —y por qué el modo importa— vive en `domain/Backlog.kt`.
                    val delBacklog = remember(games, physical, prefs.trackingMode) {
                        pendientes(games, physical, prefs.trackingMode)
                    }
                    GameListScreen(
                    title = "Backlog",
                    subtitle = "${delBacklog.size} to play",
                    games = delBacklog,
                    emptyIcon = Icons.Filled.Bookmarks,
                    emptyTitle = "Backlog is empty",
                    emptySubtitle = "Mark games as backlog from their details.",
                    onOpenGame = nav.onOpenGame,
                    onAddGame = null,
                    sortOrder = prefs.sortOrder,
                    onSortChange = prefsActions.onSortChange,
                    )
                }
                HomeTab.PLAYING -> PlayingScreen(
                    onOpenTimeline = nav.onOpenTimeline,
                    onAddPhysical = nav.onAddPlaying,
                    collectionEnabled = prefs.trackingMode == TrackingMode.COLLECTION_AND_DIARY,
                    games = games.filter { it.playing },
                    onOpenGame = nav.onOpenGame,
                    onAddDigital = nav.onAddDigital,
                )
                HomeTab.WISHLIST -> WishlistScreen(
                    items = wishlist,
                    onAddWishlist = nav.onAddWishlist,
                    onRemove = { vm.removeFromWishlist(it) },
                    onClear = { vm.clearWishlist() },
                )
                HomeTab.SETTINGS -> SettingsScreen(
                    state = SettingsUiState(
                        trackingMode = prefs.trackingMode,
                        themeMode = prefs.themeMode,
                        regionFilter = prefs.regionFilter,
                        showLabels = prefs.showLabels,
                        showConsoleTitles = prefs.showConsoleTitles,
                        deleteAudioAfterTranscription = vm.deleteAudioAfterTranscription(),
                        photoCount = photoCount,
                        syncStatus = syncStatus,
                        installedModel = installedModel,
                        modelDownload = modelDownload,
                        transcriptionLanguage = transcriptionLanguage,
                        previewEmpty = previewEmpty,
                    ),
                    actions = SettingsActions(
                        onTrackingModeChange = prefsActions.onTrackingModeChange,
                        onThemeChange = prefsActions.onThemeChange,
                        onRegionChange = prefsActions.onRegionChange,
                        onShowLabelsChange = prefsActions.onShowLabelsChange,
                        onShowConsoleTitlesChange = prefsActions.onShowConsoleTitlesChange,
                        onDeleteAudioChange = { vm.setDeleteAudioAfterTranscription(it) },
                        exportCsv = { vm.exportCsv() },
                        backupJson = { vm.exportSnapshotJson() },
                        backupArchive = { vm.exportArchive() },
                        onRestore = { vm.importBackup(it) },
                        onClearSyncStatus = { vm.clearSyncStatus() },
                        onDownloadModel = { vm.downloadModel(it) },
                        onCancelModelDownload = { vm.cancelModelDownload() },
                        onDeleteModel = { vm.deleteModel(it) },
                        onDismissModelError = { vm.dismissModelError() },
                        onLanguageChange = { vm.setTranscriptionLanguage(it) },
                        onCheckCatalogs = { vm.buscarCatalogosNuevos(forzar = true) },
                        // La sección Developer solo aparece en builds debug (callback null → oculta).
                        onPreviewEmptyChange = if (isDebug) ({ vm.setPreviewEmpty(it) }) else null,
                    ),
                )
            }
        }
    }
}
