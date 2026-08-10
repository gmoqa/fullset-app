package com.gmoqa.fullset

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
import com.gmoqa.fullset.data.PlatformImage
import com.gmoqa.fullset.data.Platform
import com.gmoqa.fullset.data.PlatformRegistry
import com.gmoqa.fullset.data.RegionFilter
import com.gmoqa.fullset.data.SortOrder
import com.gmoqa.fullset.data.ThemeMode
import com.gmoqa.fullset.data.TrackingMode
import com.gmoqa.fullset.ui.AttachSharedPhotoDialog
import com.gmoqa.fullset.resources.Res
import com.gmoqa.fullset.resources.ic_eye_search
import com.gmoqa.fullset.resources.ic_shelf
import com.gmoqa.fullset.ui.AddDigitalGameScreen
import com.gmoqa.fullset.ui.AddGameScreen
import com.gmoqa.fullset.ui.BackHandler
import com.gmoqa.fullset.ui.CatalogMark
import com.gmoqa.fullset.ui.DiarioGamerTheme
import com.gmoqa.fullset.ui.GameDetailScreen
import com.gmoqa.fullset.ui.TimelineScreen
import com.gmoqa.fullset.ui.GameListScreen
import com.gmoqa.fullset.ui.LibraryScreen
import com.gmoqa.fullset.ui.PlatformScreen
import com.gmoqa.fullset.ui.PlayingScreen
import com.gmoqa.fullset.ui.SettingsScreen
import com.gmoqa.fullset.ui.WishlistScreen
import com.gmoqa.fullset.ui.isCompactWidth
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/** Destino del flujo "agregar juego de una plataforma". */
/**
 * A dónde va el juego que se elige del catálogo.
 *
 * [PLAYING] es físico igual que [LIBRARY] —entra a la colección— pero además queda marcado como que
 * lo estás jugando: si el alta salió desde Playing y el juego no apareciera ahí, la pantalla queda
 * igual que antes y parece que no pasó nada.
 */
private enum class AddTarget { LIBRARY, PLAYING, WISHLIST }

/**
 * Raíz de la app, compartida entre Android e iOS. Recibe el [vm] ya construido por cada plataforma
 * (con sus impls de voz/whisper y su API key). [isDebug] habilita la sección Developer de Settings.
 */
@Composable
fun App(
    vm: DiaryViewModel,
    isDebug: Boolean = false,
    /**
     * Imagen que llegó por el menú Compartir del sistema. Cuando no es null se pregunta a qué juego
     * de Playing adjuntarla. En iOS es siempre null hasta que exista una Share Extension.
     */
    sharedImage: PlatformImage? = null,
    onSharedImageHandled: () -> Unit = {},
) {
    val catalog = remember { GameCatalog() }
    val registry = remember { PlatformRegistry() }

    // `remember` (no saveable): en recreación se re-lee el valor persistido en prefs.
    var themeMode by remember { mutableStateOf(vm.themeMode()) }
    var region by remember { mutableStateOf(vm.regionFilter()) }
    var showLabels by remember { mutableStateOf(vm.showCollectionLabels()) }
    var showConsoleTitles by remember { mutableStateOf(vm.showConsoleTitles()) }
    var sortOrder by remember { mutableStateOf(vm.sortOrder()) }
    // Arranca en COLLECTION_AND_DIARY —lo que devuelve `TrackingMode.fromKey(null)`— y se cambia
    // en Settings. **No se pregunta al abrir por primera vez.** Preguntarlo ahí obligaba a decidir
    // antes de haber visto nada: el modo solo esconde secciones, no cambia qué se guarda, así que
    // no hay nada que la app necesite saber de antemano. Mostrar todo y dejar que se recorte
    // después le da a la pregunta el contexto que le faltaba.
    var trackingMode by remember { mutableStateOf(vm.trackingMode()) }
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val ready by vm.ready.collectAsStateWithLifecycle()
    DiarioGamerTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (!ready) {
                // Carga breve mientras siembra (solo perceptible en la primera instalación).
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                AppRoot(
                    vm = vm,
                    catalog = catalog,
                    // Todas: las deshabilitadas se muestran bloqueadas ("Soon") en el paso 1.
                    platforms = registry.all(),
                    themeMode = themeMode,
                    onThemeChange = { themeMode = it; vm.setThemeMode(it) },
                    regionFilter = region,
                    onRegionChange = { region = it; vm.setRegionFilter(it) },
                    showLabels = showLabels,
                    onShowLabelsChange = { showLabels = it; vm.setShowCollectionLabels(it) },
                    showConsoleTitles = showConsoleTitles,
                    onShowConsoleTitlesChange = { showConsoleTitles = it; vm.setShowConsoleTitles(it) },
                    sortOrder = sortOrder,
                    onSortChange = { sortOrder = it; vm.setSortOrder(it) },
                    trackingMode = trackingMode,
                    onTrackingModeChange = { trackingMode = it; vm.setTrackingMode(it) },
                    isDebug = isDebug,
                )
                if (sharedImage != null) {
                    val games by vm.games.collectAsStateWithLifecycle()
                    AttachSharedPhotoDialog(
                        playing = games.filter { it.playing },
                        onPick = { game ->
                            vm.addPhoto(game.id, sharedImage)
                            onSharedImageHandled()
                        },
                        onDismiss = onSharedImageHandled,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRoot(
    vm: DiaryViewModel,
    catalog: GameCatalog,
    platforms: List<Platform>,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    regionFilter: RegionFilter,
    onRegionChange: (RegionFilter) -> Unit,
    showLabels: Boolean,
    onShowLabelsChange: (Boolean) -> Unit,
    showConsoleTitles: Boolean,
    onShowConsoleTitlesChange: (Boolean) -> Unit,
    sortOrder: SortOrder,
    onSortChange: (SortOrder) -> Unit,
    trackingMode: TrackingMode,
    onTrackingModeChange: (TrackingMode) -> Unit,
    isDebug: Boolean,
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.COLLECTION) }
    // Pila de navegación sobre Home. Home es el fondo (el pager con las tabs); cada pantalla que se
    // abre (detalle, plataforma, agregar) se apila encima y "back" desapila UNA. Así volver desde el
    // detalle de un juego regresa a la vista de plataforma desde la que se abrió, no siempre a Home.
    val backStack = remember { mutableStateListOf<Screen>() }
    val screen = backStack.lastOrNull() ?: Screen.Home
    // Dirección de la animación: al apilar (open) la pantalla entra desde la derecha; al desapilar
    // (back) sale hacia la derecha y la anterior reaparece con fundido.
    var goingBack by remember { mutableStateOf(false) }
    fun open(next: Screen) {
        // Doble tap rápido en la misma fila: no apilar dos veces la misma pantalla (back parecería
        // no hacer nada la primera vez).
        if (backStack.lastOrNull() == next) return
        goingBack = false
        backStack.add(next)
    }
    fun back() { goingBack = true; backStack.removeLastOrNull() }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            if (!goingBack) {
                (slideInHorizontally(tween(240)) { it } + fadeIn(tween(240))) togetherWith
                    fadeOut(tween(200))
            } else {
                fadeIn(tween(220)) togetherWith
                    (slideOutHorizontally(tween(240)) { it } + fadeOut(tween(240)))
            }
        },
        label = "nav",
    ) { current ->
        when (current) {
            is Screen.Detail -> {
                BackHandler { back() }
                GameDetailScreen(
                    vm = vm,
                    gameId = current.gameId,
                    onBack = { back() },
                    onDelete = { back() },
                )
            }

            is Screen.Timeline -> {
                BackHandler { back() }
                val todos by vm.games.collectAsStateWithLifecycle()
                TimelineScreen(
                    games = todos,
                    onBack = { back() },
                    onOpenGame = { open(Screen.Detail(it)) },
                )
            }

            is Screen.Add -> {
                BackHandler { back() }
                // Lo ya registrado, para marcarlo en la lista del catálogo. Se recolecta acá adentro
                // para no recomponer todo el root con cada cambio de la colección.
                val allGames by vm.games.collectAsStateWithLifecycle()
                val allWishlist by vm.wishlist.collectAsStateWithLifecycle()
                val marks = remember(allGames, allWishlist, current.target) {
                    val owned = allGames.filter { !it.digital }.map {
                        CatalogMark(
                            platform = it.platform, slug = it.slug, title = it.name,
                            label = if (current.target == AddTarget.WISHLIST) "Owned" else "Added",
                            dot = it.conditionState?.dot,
                            // En la wishlist tenerlo es solo un aviso: podés desear otra copia o región.
                            blocks = current.target != AddTarget.WISHLIST,
                            // La región de tu copia: tener la americana no bloquea agregar la japonesa.
                            region = it.region,
                        )
                    }
                    when (current.target) {
                        AddTarget.LIBRARY, AddTarget.PLAYING -> owned
                        AddTarget.WISHLIST -> owned + allWishlist.map {
                            CatalogMark(it.platform, it.slug, it.game, label = "Added")
                        }
                    }
                }
                AddGameScreen(
                    title = if (current.target == AddTarget.WISHLIST) "Add to wishlist" else "Add game",
                    platforms = platforms,
                    catalog = catalog,
                    onCancel = { back() },
                    onPicked = { platform, entry, coverUrl ->
                        when (current.target) {
                            AddTarget.LIBRARY, AddTarget.PLAYING ->
                                vm.addGame(
                                    entry.title, platform.name, coverUrl,
                                    region = entry.region, releaseYear = entry.year,
                                    // Serial y fecha **también**: el catálogo los trae y el alta los
                                    // estaba tirando, así que el juego nacía sin catalog number y sin
                                    // fecha precisa hasta que corriera una migración del seeder.
                                    releaseDate = entry.releaseDate, serial = entry.serial,
                                    genre = entry.genre,
                                    slug = entry.slug, publisher = entry.publisher,
                                    playing = current.target == AddTarget.PLAYING,
                                    // Sin colección no se afirma posesión: ver `addGame(digital=)`.
                                    digital = trackingMode == TrackingMode.DIARY_ONLY,
                                )
                            AddTarget.WISHLIST ->
                                vm.addToWishlist(platform.name, entry.title, entry.slug, coverUrl)
                        }
                        // No cerramos: quedás en la lista, el juego pasa a "Added" y podés seguir.
                    },
                    marks = marks,
                    region = regionFilter,
                )
            }

            Screen.AddDigital -> {
                BackHandler { back() }
                AddDigitalGameScreen(
                    coverSearchEnabled = vm.coverSearchEnabled,
                    onSearchGames = { vm.searchGames(it) },
                    onCoversFor = { vm.coversFor(it) },
                    onCancel = { back() },
                    onAdd = { platform, gameTitle, coverUrl, cover ->
                        vm.addDigitalGame(gameTitle, platform, coverUrl, cover)
                        back()
                    },
                )
            }

            is Screen.Platform -> {
                BackHandler { back() }
                val allGames by vm.games.collectAsStateWithLifecycle()
                // Tus juegos físicos de esa plataforma (el orden por lanzamiento lo hace la vista).
                val platformGames = remember(allGames, current.platform) {
                    allGames.filter { !it.digital && it.platform == current.platform }
                }
                val platformObj = remember(current.platform) {
                    platforms.firstOrNull { it.name == current.platform }
                }
                // Catálogo completo de la consola —**todas sus regiones**, en orden—: la vista
                // marca cuáles tenés y cuáles faltan. Antes traía solo la región por defecto, así
                // que un juego japonés de tu colección aparecía como "extra" fuera de la lista de
                // su propia consola.
                val catalogEntries = remember(platformObj) {
                    platformObj?.let { catalog.entriesAllRegions(it) } ?: emptyList()
                }
                PlatformScreen(
                    platform = current.platform,
                    info = platformObj?.info,
                    region = regionFilter,
                    games = platformGames,
                    catalog = catalogEntries,
                    // El detalle se APILA sobre la plataforma: back desde el juego vuelve acá.
                    onOpenGame = { open(Screen.Detail(it)) },
                    onBack = { back() },
                    // Agregar directo desde el timeline: mismo alta que el flujo de "Add game".
                    onAddGame = { entry ->
                        vm.addGame(
                            entry.title, current.platform, entry.coverUrl,
                            region = entry.region, releaseYear = entry.year,
                            genre = entry.genre, slug = entry.slug, publisher = entry.publisher,
                        )
                    },
                )
            }

            Screen.Home -> HomeContent(
                onOpenTimeline = { open(Screen.Timeline) },
                trackingMode = trackingMode,
                onTrackingModeChange = onTrackingModeChange,
                vm = vm,
                platforms = platforms,
                tab = tab,
                onTabChange = { tab = it },
                themeMode = themeMode,
                onThemeChange = onThemeChange,
                regionFilter = regionFilter,
                onRegionChange = onRegionChange,
                showLabels = showLabels,
                onShowLabelsChange = onShowLabelsChange,
                showConsoleTitles = showConsoleTitles,
                onShowConsoleTitlesChange = onShowConsoleTitlesChange,
                sortOrder = sortOrder,
                onSortChange = onSortChange,
                onOpenGame = { open(Screen.Detail(it)) },
                onOpenPlatform = { open(Screen.Platform(it)) },
                onAddLibrary = { open(Screen.Add(AddTarget.LIBRARY)) },
                onAddWishlist = { open(Screen.Add(AddTarget.WISHLIST)) },
                onAddDigital = { open(Screen.AddDigital) },
                onAddPlaying = { open(Screen.Add(AddTarget.PLAYING)) },
                isDebug = isDebug,
            )
        }
    }
}

/** Una pestaña del home, en el mismo orden que las páginas del pager. */
/**
 * Las secciones del bottom nav. Se identifican por **nombre**, no por posición: el juego de
 * pestañas cambia con el modo (en `DIARY_ONLY` no están Collection ni Wishlist), y guardar el índice
 * hacía que al cambiar de modo el número apuntara a otra sección — el 2 es Playing con cinco
 * pestañas y Settings con tres.
 */
private enum class HomeTab(val label: String) {
    COLLECTION("Collection"), BACKLOG("Backlog"), PLAYING("Playing"),
    WISHLIST("Wishlist"), SETTINGS("Settings");
}

@Composable
private fun HomeTab.icon(contentDescription: String?) = when (this) {
    HomeTab.COLLECTION -> Icon(painterResource(Res.drawable.ic_shelf), contentDescription)
    HomeTab.BACKLOG -> Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription)
    HomeTab.PLAYING -> Icon(Icons.Filled.SportsEsports, contentDescription)
    HomeTab.WISHLIST -> Icon(painterResource(Res.drawable.ic_eye_search), contentDescription)
    HomeTab.SETTINGS -> Icon(Icons.Filled.Settings, contentDescription)
}

/** Las pestañas visibles con este modo. Collection y Wishlist son las dos sobre *poseer*. */
private fun tabsFor(mode: TrackingMode): List<HomeTab> = when (mode) {
    TrackingMode.COLLECTION_AND_DIARY -> HomeTab.entries
    TrackingMode.DIARY_ONLY -> listOf(HomeTab.BACKLOG, HomeTab.PLAYING, HomeTab.SETTINGS)
}

/** Pantalla principal actual dentro de la navegación. */
private sealed interface Screen {
    data object Home : Screen
    data class Detail(val gameId: Long) : Screen
    data class Add(val target: AddTarget) : Screen
    data object AddDigital : Screen
    /** Vista propia de una plataforma: ficha + juegos por lanzamiento. */
    data class Platform(val platform: String) : Screen
    /** Los juegos por "primera vez que lo jugué", incluidos los digitales. */
    data object Timeline : Screen
}

@Composable
private fun HomeContent(
    vm: DiaryViewModel,
    platforms: List<Platform>,
    tab: HomeTab,
    onTabChange: (HomeTab) -> Unit,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    regionFilter: RegionFilter,
    onRegionChange: (RegionFilter) -> Unit,
    showLabels: Boolean,
    onShowLabelsChange: (Boolean) -> Unit,
    showConsoleTitles: Boolean,
    onShowConsoleTitlesChange: (Boolean) -> Unit,
    sortOrder: SortOrder,
    onSortChange: (SortOrder) -> Unit,
    onOpenGame: (Long) -> Unit,
    onOpenTimeline: () -> Unit,
    trackingMode: TrackingMode,
    onTrackingModeChange: (TrackingMode) -> Unit,
    onOpenPlatform: (String) -> Unit,
    onAddLibrary: () -> Unit,
    onAddWishlist: () -> Unit,
    onAddDigital: () -> Unit,
    onAddPlaying: () -> Unit,
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
    val tabs = remember(trackingMode) { tabsFor(trackingMode) }
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
                    onOpenGame = onOpenGame,
                    onAddPhysical = onAddLibrary,
                    // Al agregar un juego, Collection sube hasta él en vez de dejarte donde estabas.
                    focusGameId = lastAdded,
                    onFocusConsumed = { vm.consumeLastAdded() },
                    // Tocar una franja abre la vista propia de esa plataforma.
                    onOpenPlatform = onOpenPlatform,
                    // Opciones de vista (Settings → Collection): ocultar labels y/o franjas de consola.
                    showLabels = showLabels,
                    showConsoleTitles = showConsoleTitles,
                    sortOrder = sortOrder,
                    onSortChange = onSortChange,
                )
                HomeTab.BACKLOG -> {
                    // En modo diario los juegos se cargan como digitales (no hay Collection), así
                    // que filtrar por físicos dejaría el Backlog siempre vacío. Ahí entran todos.
                    val pendientes = remember(games, physical, trackingMode) {
                        val fuente = if (trackingMode == TrackingMode.DIARY_ONLY) games else physical
                        fuente.filter { it.backlog }
                    }
                    GameListScreen(
                    title = "Backlog",
                    subtitle = "${pendientes.size} to play",
                    games = pendientes,
                    emptyIcon = Icons.Filled.Bookmarks,
                    emptyTitle = "Backlog is empty",
                    emptySubtitle = "Mark games as backlog from their details.",
                    onOpenGame = onOpenGame,
                    onAddGame = null,
                    sortOrder = sortOrder,
                    onSortChange = onSortChange,
                    )
                }
                HomeTab.PLAYING -> PlayingScreen(
                    onOpenTimeline = onOpenTimeline,
                    onAddPhysical = onAddPlaying,
                    collectionEnabled = trackingMode == TrackingMode.COLLECTION_AND_DIARY,
                    games = games.filter { it.playing },
                    onOpenGame = onOpenGame,
                    onAddDigital = onAddDigital,
                )
                HomeTab.WISHLIST -> WishlistScreen(
                    items = wishlist,
                    onAddWishlist = onAddWishlist,
                    onRemove = { vm.removeFromWishlist(it) },
                    onClear = { vm.clearWishlist() },
                )
                HomeTab.SETTINGS -> SettingsScreen(
                    trackingMode = trackingMode,
                    onTrackingModeChange = onTrackingModeChange,
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                    regionFilter = regionFilter,
                    onRegionChange = onRegionChange,
                    showLabels = showLabels,
                    onShowLabelsChange = onShowLabelsChange,
                    showConsoleTitles = showConsoleTitles,
                    onShowConsoleTitlesChange = onShowConsoleTitlesChange,
                    deleteAudioAfterTranscription = vm.deleteAudioAfterTranscription(),
                    onDeleteAudioChange = { vm.setDeleteAudioAfterTranscription(it) },
                    exportCsv = { vm.exportCsv() },
                    backupJson = { vm.exportSnapshotJson() },
                    backupArchive = { vm.exportArchive() },
                    photoCount = photoCount,
                    onRestore = { vm.importBackup(it) },
                    syncStatus = syncStatus,
                    onClearSyncStatus = { vm.clearSyncStatus() },
                    installedModel = installedModel,
                    modelDownload = modelDownload,
                    onDownloadModel = { vm.downloadModel(it) },
                    onCancelModelDownload = { vm.cancelModelDownload() },
                    onDeleteModel = { vm.deleteModel(it) },
                    onDismissModelError = { vm.dismissModelError() },
                    transcriptionLanguage = transcriptionLanguage,
                    onLanguageChange = { vm.setTranscriptionLanguage(it) },
                    // La sección Developer solo aparece en builds debug (callback null → oculta).
                    previewEmpty = previewEmpty,
                    onPreviewEmptyChange = if (isDebug) ({ vm.setPreviewEmpty(it) }) else null,
                )
            }
        }
    }
}
