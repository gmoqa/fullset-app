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
import com.gmoqa.fullset.data.CatalogSync
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
import com.gmoqa.fullset.ui.AttachSharedPhotoDialog
import com.gmoqa.fullset.resources.Res
import com.gmoqa.fullset.resources.ic_eye_search
import com.gmoqa.fullset.resources.ic_shelf
import com.gmoqa.fullset.ui.AddDigitalGameScreen
import com.gmoqa.fullset.ui.AddGameScreen
import com.gmoqa.fullset.ui.BackHandler
import com.gmoqa.fullset.domain.CatalogMark
import com.gmoqa.fullset.domain.pendientes
import com.gmoqa.fullset.ui.DiarioGamerTheme
import com.gmoqa.fullset.ui.GameDetailScreen
import com.gmoqa.fullset.ui.TimelineScreen
import com.gmoqa.fullset.ui.GameListScreen
import com.gmoqa.fullset.ui.LibraryScreen
import com.gmoqa.fullset.ui.PlatformScreen
import com.gmoqa.fullset.ui.PlayingScreen
import com.gmoqa.fullset.ui.HomeContent
import com.gmoqa.fullset.ui.HomeTab
import com.gmoqa.fullset.ui.Navegacion
import com.gmoqa.fullset.ui.Preferencias
import com.gmoqa.fullset.ui.PreferenciasActions
import com.gmoqa.fullset.ui.SettingsActions
import com.gmoqa.fullset.ui.SettingsScreen
import com.gmoqa.fullset.ui.SettingsUiState
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
    // Se rehace cuando `CatalogSync` aplica una actualización: `GameCatalog` cachea por archivo, y
    // sin esto los catálogos nuevos no se verían hasta reiniciar la app.
    val revisionCatalogos by CatalogSync.revision.collectAsStateWithLifecycle()
    val catalog = remember(revisionCatalogos) { GameCatalog() }
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
                    prefs = Preferencias(
                        themeMode = themeMode,
                        regionFilter = region,
                        showLabels = showLabels,
                        showConsoleTitles = showConsoleTitles,
                        sortOrder = sortOrder,
                        trackingMode = trackingMode,
                    ),
                    // Cada setter guarda **y** actualiza el estado local: la preferencia se aplica en
                    // el acto, sin esperar a releer la base.
                    prefsActions = PreferenciasActions(
                        onThemeChange = { themeMode = it; vm.setThemeMode(it) },
                        onRegionChange = { region = it; vm.setRegionFilter(it) },
                        onShowLabelsChange = { showLabels = it; vm.setShowCollectionLabels(it) },
                        onShowConsoleTitlesChange = { showConsoleTitles = it; vm.setShowConsoleTitles(it) },
                        onSortChange = { sortOrder = it; vm.setSortOrder(it) },
                        onTrackingModeChange = { trackingMode = it; vm.setTrackingMode(it) },
                    ),
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
    prefs: Preferencias,
    prefsActions: PreferenciasActions,
    isDebug: Boolean,
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.COLLECTION) }
    // El mecanismo —pila, atrás y dirección de la animación— vive en `navigation/BackStack.kt`.
    // Acá quedan los **destinos**, que son los que necesitan el contexto de la app.
    val stack = rememberBackStack()
    fun open(next: Screen) = stack.abrir(next)
    fun back() = stack.atras()

    NavHost(stack) { current ->
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
                                    developer = entry.developer, rating = entry.rating,
                                    playing = current.target == AddTarget.PLAYING,
                                    // Sin colección no se afirma posesión: ver `addGame(digital=)`.
                                    digital = prefs.trackingMode == TrackingMode.DIARY_ONLY,
                                )
                            AddTarget.WISHLIST ->
                                vm.addToWishlist(platform.name, entry.title, entry.slug, coverUrl)
                        }
                        // No cerramos: quedás en la lista, el juego pasa a "Added" y podés seguir.
                    },
                    marks = marks,
                    region = prefs.regionFilter,
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
                    region = prefs.regionFilter,
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
                            developer = entry.developer, rating = entry.rating,
                        )
                    },
                )
            }

            Screen.Home -> HomeContent(
                vm = vm,
                platforms = platforms,
                tab = tab,
                onTabChange = { tab = it },
                prefs = prefs,
                prefsActions = prefsActions,
                nav = Navegacion(
                    onOpenGame = { open(Screen.Detail(it)) },
                    onOpenTimeline = { open(Screen.Timeline) },
                    onOpenPlatform = { open(Screen.Platform(it)) },
                    onAddLibrary = { open(Screen.Add(AddTarget.LIBRARY)) },
                    onAddWishlist = { open(Screen.Add(AddTarget.WISHLIST)) },
                    onAddDigital = { open(Screen.AddDigital) },
                    onAddPlaying = { open(Screen.Add(AddTarget.PLAYING)) },
                ),
                isDebug = isDebug,
            )
        }
    }
}
