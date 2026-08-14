# Plan de refactor

Estado al **2026-08-14**. 12.539 líneas de Kotlin y 5.753 de Python.

Esto **no** propone una arquitectura nueva. Propone terminar de aplicar la que el repo ya usa en
algunos lugares y no en otros, que es de donde viene la sensación de que hay partes que no se leen.

---

## Diagnóstico, con números

Lo bueno primero, porque es lo que hay que **no** romper: los comentarios explican *por qué*, con el
defecto concreto que les dio origen («sin esto `Astérix` daba `ast-rix`… son 7 juegos con el slug
roto»). Eso es lo que hace mantenible un proyecto de una sola persona. Cualquier movimiento de este
plan que pierda un comentario está mal hecho.

Los tres problemas:

**1. Funciones que son el pegamento de todo.**

| | líneas | parámetros |
|---|---|---|
| `App.AppRoot` | 220 | **16** |
| `App.HomeContent` | 190 | **24** |
| `SettingsScreen` | — | **30** |

Con 30 parámetros la firma dejó de ser un contrato y pasó a ser una lista de compras. Agregar un
ajuste obliga a tocar cuatro firmas en cascada.

**2. Las reglas de negocio viven dentro de composables.** 17 filtros/ordenamientos/agrupaciones en
`ui/`, contra 16 en toda la capa de datos. Y **ninguno de los 10 archivos de test toca esas reglas**:
las 1.114 líneas de test cubren `data/`, no lo que decide qué ves.

**3. `data/` es un cajón.** 23 archivos donde conviven el modelo (`Models`, `Platform`), la
persistencia (`Database`, `DiaryRepository`), lo remoto (`SteamGridDb`, `Http`) y los servicios de
plataforma (`Voice`, `Whisper`). Y `DiaryViewModel` expone **69 miembros públicos** planos.

Además: `docs/` tiene 1.341 líneas y ninguna explica el **código** — todas hablan del dataset o de
iOS. Quien llega no sabe por dónde entrar.

---

## El principio: ya lo usás

`diaryFeed(notes, photos)`, `GameSearch.filter(games, query)` y `List<Platform>.groupedByGeneration()`
son funciones puras, fuera de Compose, con nombre y testeables. Son exactamente el patrón correcto.

El problema es que **otras seis reglas del mismo calibre se quedaron adentro de un `remember`**. No
hay una decisión de diseño distinta detrás: son las que se escribieron apurado.

---

## Fases

Ordenadas por retorno sobre riesgo. Cada una es un commit que compila y anda; ninguna cambia el
comportamiento.

### Fase 1 — Las reglas salen a `domain/` · riesgo bajo, retorno alto

Extraer a funciones puras, **cada una con su test**:

| dónde está hoy | qué decide | nombre propuesto |
|---|---|---|
| `PlatformScreen.kt:64` (35 líneas) | fusiona catálogo con tu colección, marca lo que tenés y ordena por lanzamiento | `completitudDe(catalogo, coleccion)` |
| `GameListScreen.kt:122` | agrupa en estanterías y las ordena | `estanterias(juegos, orden)` |
| `AddGameScreen.kt:478` | qué juego ya está registrado y si bloquea | `indiceDeMarcas(marcas, plataforma)` |
| `AddGameScreen.kt:468` | dónde corta cada región en la lista unida | `cortesPorRegion(entradas)` |
| `App.kt` (~542) | qué entra al Backlog según el modo | `pendientes(juegos, modo)` |
| `TimelineScreen.kt:56` | agrupa el diario por año | `porAnio(juegos)` |

**Por qué esta primero:** es la única que agrega red de seguridad en vez de gastarla. Al terminar,
la regla de completitud —el corazón de la app, ese «148 of 1893»— se puede probar sin levantar
Compose. Hoy no.

**Cómo se verifica:** test nuevo por función + la app abre y muestra los mismos números. Los tests
son la prueba de que el refactor no cambió nada.

### Fase 2 — Un estado por pantalla · riesgo medio

`SettingsScreen(30 parámetros)` → `SettingsScreen(state: SettingsUiState, actions: SettingsActions)`.
Después `HomeContent` (24) y `AppRoot` (16), que caen casi solos una vez hecho el primero.

La pantalla pasa a leerse como **«esto es lo que muestro, esto es lo que puedo hacer»**, que es la
pregunta que uno se hace al abrir un archivo que no escribió.

**Cómo se verifica:** compila y las tres pantallas se recorren en el dispositivo.

### Fase 3 — Partir `App.kt` · riesgo bajo

- `navigation/Screen.kt` — el `sealed interface` de destinos
- `navigation/Router.kt` — el `when(current)` que hoy vive dentro de `AppRoot`
- `App.kt` queda como composición raíz, ~60 líneas

**Cómo se verifica:** compila; navegación completa a mano una vez.

### Fase 4 — El ViewModel por facetas · riesgo medio

69 miembros planos → agrupados por área: colección, diario, respaldo, voz, carátulas. Expuestos como
sub-objetos, no como 69 métodos sueltos.

**Ojo:** esto toca todas las pantallas a la vez. Hacerla **después** de la fase 2, porque el estado
por pantalla reduce la superficie que hay que tocar.

### Fase 5 — Renombrar las capas · riesgo alto, va última

```
model/     Game, Platform, CatalogEntry, TrackingMode     datos puros, sin dependencias
domain/    completitud, pendientes, estanterías, seeder   las reglas (fase 1)
store/     Database, DiaryRepository, Settings            persistencia
remote/    SteamGridDb, CoverArt, Http                    lo que sale a la red
platform/  Voice, Whisper, Assets, FileBackup             expect/actual
ui/        pantallas y componentes
```

Regla de dependencias, en una línea: **`ui` → `domain` → `model`**, y `store`/`remote`/`platform`
solo los toca el ViewModel.

**Por qué va última:** mueve archivos de paquete, o sea que toca imports en todo el repo. Si el
agente de macOS está trabajando en iOS al mismo tiempo, cada merge va a doler. Hoy nadie más está
pusheando, pero conviene confirmarlo antes de empezarla.

---

## Reglas del refactor

1. **No cambia comportamiento.** Si algo se ve distinto en pantalla, es un bug del refactor.
2. **Un commit por fase**, que compila y pasa CI.
3. **Los comentarios se mudan con el código.** Son el activo, no el relleno.
4. **No se toca `slug()` ni el `name` de las plataformas.** Los dos están **guardados en la base del
   usuario**: el `slug` vincula sus juegos con el catálogo y el `name` es la clave del color y el
   glifo. Cambiarlos renombra juegos de su colección en silencio. Ya está advertido en
   `catalog_common.slug()` y vale igual para el Kotlin.
5. **Nada de esto toca `data/catalogs/`.** El dataset y su tooling quedan como están.

---

## Lo que NO hay que hacer

- **Introducir una capa de casos de uso o inyección de dependencias.** Para 12.500 líneas y un solo
  desarrollador, agrega ceremonia sin resolver ninguno de los tres problemas medidos.
- **Reescribir la navegación con una librería.** El `sealed interface` + `when` funciona; el problema
  es que está mezclado con el cableado, y eso lo arregla la fase 3.
- **Tocar el Python.** `tools/` ya tiene la forma correcta: un motor común (`catalog_common`,
  `nointro`), un builder por consola y un enriquecedor por campo. Agruparlos en `build/`, `enrich/` y
  `check/` sería cosmético.

---

## Orden sugerido

**Fase 1 primero, y de la fase 1 empezar por `PlatformScreen`**, que es el caso más claro y el que
mejor muestra el antes y el después. Si al verlo el patrón convence, el resto es mecánico.
