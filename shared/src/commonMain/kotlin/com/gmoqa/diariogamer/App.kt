package com.gmoqa.diariogamer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gmoqa.diariogamer.data.GameCatalog
import com.gmoqa.diariogamer.data.Platform
import com.gmoqa.diariogamer.data.PlatformRegistry
import com.gmoqa.diariogamer.data.RegionFilter
import com.gmoqa.diariogamer.data.ThemeMode
import com.gmoqa.diariogamer.resources.Res
import com.gmoqa.diariogamer.resources.ic_eye_search
import com.gmoqa.diariogamer.resources.ic_shelf
import com.gmoqa.diariogamer.ui.AddDigitalGameScreen
import com.gmoqa.diariogamer.ui.AddGameScreen
import com.gmoqa.diariogamer.ui.BackHandler
import com.gmoqa.diariogamer.ui.CatalogMark
import com.gmoqa.diariogamer.ui.DiarioGamerTheme
import com.gmoqa.diariogamer.ui.GameDetailScreen
import com.gmoqa.diariogamer.ui.GameListScreen
import com.gmoqa.diariogamer.ui.LibraryScreen
import com.gmoqa.diariogamer.ui.PlatformScreen
import com.gmoqa.diariogamer.ui.PlayingScreen
import com.gmoqa.diariogamer.ui.SettingsScreen
import com.gmoqa.diariogamer.ui.WishlistScreen
import com.gmoqa.diariogamer.ui.isCompactWidth
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/** Destino del flujo "agregar juego de una plataforma". */
private enum class AddTarget { LIBRARY, WISHLIST }

/**
 * Raíz de la app, compartida entre Android e iOS. Recibe el [vm] ya construido por cada plataforma
 * (con sus impls de voz/whisper y su API key). [isDebug] habilita la sección Developer de Settings.
 */
@Composable
fun App(vm: DiaryViewModel, isDebug: Boolean = false) {
    val catalog = remember { GameCatalog() }
    val registry = remember { PlatformRegistry() }

    // `remember` (no saveable): en recreación se re-lee el valor persistido en prefs.
    var themeMode by remember { mutableStateOf(vm.themeMode()) }
    var region by remember { mutableStateOf(vm.regionFilter()) }
    var showLabels by remember { mutableStateOf(vm.showCollectionLabels()) }
    var showConsoleTitles by remember { mutableStateOf(vm.showConsoleTitles()) }
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
                    isDebug = isDebug,
                )
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
    isDebug: Boolean,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    // Navegación con transición push/pop: al entrar (detalle/agregar) la nueva pantalla entra
    // desde la derecha; al volver, sale hacia la derecha. Home usa fundido.
    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val goingDeeper = initialState is Screen.Home && targetState !is Screen.Home
            if (goingDeeper) {
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
                BackHandler { screen = Screen.Home }
                GameDetailScreen(
                    vm = vm,
                    gameId = current.gameId,
                    onBack = { screen = Screen.Home },
                    onDelete = { screen = Screen.Home },
                )
            }

            is Screen.Add -> {
                BackHandler { screen = Screen.Home }
                // Lo ya registrado, para marcarlo en la lista del catálogo. Se recolecta acá adentro
                // para no recomponer todo el root con cada cambio de la colección.
                val allGames by vm.games.collectAsStateWithLifecycle()
                val allWishlist by vm.wishlist.collectAsStateWithLifecycle()
                val marks = remember(allGames, allWishlist, current.target) {
                    val owned = allGames.filter { !it.digital }.map {
                        CatalogMark(
                            platform = it.platform, slug = it.slug, title = it.name,
                            label = if (current.target == AddTarget.LIBRARY) "Added" else "Owned",
                            dot = it.conditionState?.dot,
                            // En la wishlist tenerlo es solo un aviso: podés desear otra copia o región.
                            blocks = current.target == AddTarget.LIBRARY,
                        )
                    }
                    when (current.target) {
                        AddTarget.LIBRARY -> owned
                        AddTarget.WISHLIST -> owned + allWishlist.map {
                            CatalogMark(it.platform, it.slug, it.game, label = "Added")
                        }
                    }
                }
                AddGameScreen(
                    title = if (current.target == AddTarget.LIBRARY) "Add game" else "Add to wishlist",
                    platforms = platforms,
                    catalog = catalog,
                    onCancel = { screen = Screen.Home },
                    onPicked = { platform, entry, coverUrl ->
                        when (current.target) {
                            AddTarget.LIBRARY ->
                                vm.addGame(
                                    entry.title, platform.name, coverUrl,
                                    region = entry.region, releaseYear = entry.year, genre = entry.genre,
                                    slug = entry.slug, publisher = entry.publisher,
                                )
                            AddTarget.WISHLIST ->
                                vm.addToWishlist(platform.name, entry.title, entry.slug, coverUrl)
                        }
                        // No cerramos: quedás en la lista, el juego pasa a "Added" y podés seguir.
                    },
                    marks = marks,
                    onAddManual = { platform, gameTitle, coverUrl, cover ->
                        // Alta a mano (PS5…): físico → Collection. El digital va aparte, por Playing.
                        when (current.target) {
                            AddTarget.LIBRARY ->
                                vm.addManualGame(gameTitle, platform.name, coverUrl, cover, digital = false)
                            AddTarget.WISHLIST -> {
                                vm.addToWishlist(platform.name, gameTitle, "", coverUrl)
                                // Volver a Collection dejaría la sensación de que no pasó nada:
                                // aterrizamos en la wishlist, donde se ve lo que acabás de agregar.
                                tab = HOME_TAB_WISHLIST
                            }
                        }
                        screen = Screen.Home
                    },
                    coverSearchEnabled = vm.coverSearchEnabled,
                    onSearchGames = { vm.searchGames(it) },
                    onCoversFor = { vm.coversFor(it) },
                    region = regionFilter,
                )
            }

            Screen.AddDigital -> {
                BackHandler { screen = Screen.Home }
                AddDigitalGameScreen(
                    coverSearchEnabled = vm.coverSearchEnabled,
                    onSearchGames = { vm.searchGames(it) },
                    onCoversFor = { vm.coversFor(it) },
                    onCancel = { screen = Screen.Home },
                    onAdd = { platform, gameTitle, coverUrl, cover ->
                        vm.addDigitalGame(gameTitle, platform, coverUrl, cover)
                        screen = Screen.Home
                    },
                )
            }

            is Screen.Platform -> {
                BackHandler { screen = Screen.Home }
                val allGames by vm.games.collectAsStateWithLifecycle()
                // Tus juegos físicos de esa plataforma (el orden por lanzamiento lo hace la vista).
                val platformGames = remember(allGames, current.platform) {
                    allGames.filter { !it.digital && it.platform == current.platform }
                }
                val platformObj = remember(current.platform) {
                    platforms.firstOrNull { it.name == current.platform }
                }
                // Catálogo completo de la consola: la vista marca cuáles tenés y cuáles faltan.
                val catalogEntries = remember(platformObj) {
                    platformObj?.let { catalog.entries(it) } ?: emptyList()
                }
                PlatformScreen(
                    platform = current.platform,
                    info = platformObj?.info,
                    region = regionFilter,
                    games = platformGames,
                    catalog = catalogEntries,
                    onOpenGame = { screen = Screen.Detail(it) },
                    onBack = { screen = Screen.Home },
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
                onOpenGame = { screen = Screen.Detail(it) },
                onOpenPlatform = { screen = Screen.Platform(it) },
                onAddLibrary = { screen = Screen.Add(AddTarget.LIBRARY) },
                onAddWishlist = { screen = Screen.Add(AddTarget.WISHLIST) },
                onAddDigital = { screen = Screen.AddDigital },
                isDebug = isDebug,
            )
        }
    }
}

/** Una pestaña del home, en el mismo orden que las páginas del pager. */
private class HomeTab(val label: String, val icon: @Composable (contentDescription: String?) -> Unit)

/** Índice de la pestaña Wishlist dentro de [HOME_TABS] (para aterrizar ahí tras agregar). */
private const val HOME_TAB_WISHLIST = 3

private val HOME_TABS = listOf(
    HomeTab("Collection") { Icon(painterResource(Res.drawable.ic_shelf), it) },
    HomeTab("Backlog") { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, it) },
    HomeTab("Playing") { Icon(Icons.Filled.SportsEsports, it) },
    HomeTab("Wishlist") { Icon(painterResource(Res.drawable.ic_eye_search), it) },
    HomeTab("Settings") { Icon(Icons.Filled.Settings, it) },
)

/** Pantalla principal actual dentro de la navegación. */
private sealed interface Screen {
    data object Home : Screen
    data class Detail(val gameId: Long) : Screen
    data class Add(val target: AddTarget) : Screen
    data object AddDigital : Screen
    /** Vista propia de una plataforma: ficha + juegos por lanzamiento. */
    data class Platform(val platform: String) : Screen
}

@Composable
private fun HomeContent(
    vm: DiaryViewModel,
    platforms: List<Platform>,
    tab: Int,
    onTabChange: (Int) -> Unit,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    regionFilter: RegionFilter,
    onRegionChange: (RegionFilter) -> Unit,
    showLabels: Boolean,
    onShowLabelsChange: (Boolean) -> Unit,
    showConsoleTitles: Boolean,
    onShowConsoleTitlesChange: (Boolean) -> Unit,
    onOpenGame: (Long) -> Unit,
    onOpenPlatform: (String) -> Unit,
    onAddLibrary: () -> Unit,
    onAddWishlist: () -> Unit,
    onAddDigital: () -> Unit,
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

    val compact = isCompactWidth()

    // El pager es la fuente de verdad del tab. Se puede deslizar entre páginas (swipe) y la
    // bottom nav anima hacia la página elegida. Se persiste el índice para restaurarlo.
    val pagerState = rememberPagerState(initialPage = tab, pageCount = { HOME_TABS.size })
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState.currentPage) { onTabChange(pagerState.currentPage) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                HOME_TABS.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        // El nombre va siempre como descripción accesible, se muestre o no.
                        icon = { tab.icon(tab.label) },
                        // En pantallas angostas, solo iconos: cinco etiquetas no entran sin apretarse.
                        label = if (compact) null else ({ Text(tab.label) }),
                    )
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
            when (page) {
                0 -> LibraryScreen(
                    games = physical,
                    onOpenGame = onOpenGame,
                    onAddGame = onAddLibrary,
                    // Al agregar un juego, Collection sube hasta él en vez de dejarte donde estabas.
                    focusGameId = lastAdded,
                    onFocusConsumed = { vm.consumeLastAdded() },
                    // Tocar una franja abre la vista propia de esa plataforma.
                    onOpenPlatform = onOpenPlatform,
                    // Opciones de vista (Settings → Collection): ocultar labels y/o franjas de consola.
                    showLabels = showLabels,
                    showConsoleTitles = showConsoleTitles,
                )
                1 -> GameListScreen(
                    title = "Backlog",
                    subtitle = "${physical.count { it.backlog }} to play",
                    games = physical.filter { it.backlog },
                    emptyIcon = Icons.Filled.Bookmarks,
                    emptyTitle = "Backlog is empty",
                    emptySubtitle = "Mark games as backlog from their details.",
                    onOpenGame = onOpenGame,
                    onAddGame = null,
                )
                2 -> PlayingScreen(
                    games = games.filter { it.playing },
                    onOpenGame = onOpenGame,
                    onAddDigital = onAddDigital,
                )
                3 -> WishlistScreen(
                    items = wishlist,
                    onAddWishlist = onAddWishlist,
                    onRemove = { vm.removeFromWishlist(it) },
                    onClear = { vm.clearWishlist() },
                )
                else -> SettingsScreen(
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                    regionFilter = regionFilter,
                    onRegionChange = onRegionChange,
                    showLabels = showLabels,
                    onShowLabelsChange = onShowLabelsChange,
                    showConsoleTitles = showConsoleTitles,
                    onShowConsoleTitlesChange = onShowConsoleTitlesChange,
                    exportCsv = { vm.exportCsv() },
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
