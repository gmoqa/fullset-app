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

### Fase 1 — Las reglas salen a `domain/` · ✅ **hecha el 2026-08-14**

Extraer a funciones puras, **cada una con su test**:

| qué decide | dónde vive ahora |
|---|---|
| fusiona catálogo con tu colección, marca lo tuyo y ordena por lanzamiento | `domain/Completitud.kt` |
| agrupa en estanterías, ordenando dentro de cada una | `domain/Estanterias.kt` |
| qué juego ya está registrado y cuál marca gana | `domain/AltaDeJuego.kt` |
| dónde corta cada región en la lista unida | `domain/AltaDeJuego.kt` |
| qué entra al Backlog según el modo | `domain/Backlog.kt` |
| agrupa el diario por año | `domain/LineaDeTiempo.kt` |

**Resultado:** 212 líneas de reglas con nombre, **18 tests** que antes no existían, y los
filtros/ordenamientos sueltos en la UI bajaron de **17 a 8** (los que quedan son de presentación:
qué plataformas tienen catálogo, qué juegos están jugándose). `CatalogMark` se mudó a `domain/`
porque es un concepto del dominio, no de una pantalla.

**Por qué esta primero:** es la única que agrega red de seguridad en vez de gastarla. Al terminar,
la regla de completitud —el corazón de la app, ese «148 of 1893»— se puede probar sin levantar
Compose. Hoy no.

**Verificado en dispositivo el 2026-08-15**: la ficha de Nintendo DS muestra **«1 of 6092 · by
release»** —el conteo correcto, con la lista en orden cronológico y los no poseídos en gris—, que es
justo lo que calcula `completitudDe()`.

### Fase 2 — Un estado por pantalla · ✅ **hecha el 2026-08-14**

| | antes | ahora |
|---|---|---|
| `SettingsScreen` | 30 | **2** — `SettingsUiState` + `SettingsActions` |
| `HomeContent` | 24 | **8** — `Preferencias` + `PreferenciasActions` + `Navegacion` |
| `AppRoot` | 16 | **6** — los mismos dos paquetes |

Los doce parámetros de preferencias —seis valores y sus seis setters— aparecían **sueltos y
repetidos** en `AppRoot` y otra vez en `HomeContent`, solo para llegar a Settings y a las listas. Un
solo concepto (`Preferencias` / `PreferenciasActions`) colapsó las dos firmas a la vez.

La pantalla pasa a leerse como **«esto es lo que muestro, esto es lo que puedo hacer»**, que es la
pregunta que uno se hace al abrir un archivo que no escribió.

**Trampa que apareció dos veces:** al prefijar los usos en el cuerpo, el reemplazo también tocaba
las **etiquetas de los argumentos con nombre** (`installedModel = installedModel` quedaba como
`state.installedModel = state.installedModel`). La etiqueta nunca se prefija, solo el valor.

**Verificado en dispositivo el 2026-08-15**: Settings intacta, y los toggles y controles segmentados responden y **se aplican** —apagar «Show game titles» sacó los nombres en Collection—, o sea que la acción atraviesa `SettingsActions` → `Preferencias` → `HomeContent`.

### Fase 3 — Partir `App.kt` · ✅ **hecha el 2026-08-14**

- `navigation/Screen.kt` (21 líneas) — el `sealed interface` de destinos y `AddTarget`
- `navigation/BackStack.kt` (77) — la pila, "atrás" y la dirección de la animación
- `ui/HomeScreen.kt` (286) — `HomeContent`, las pestañas y `Navegacion`
- `App.kt`: **606 → 349 líneas**, arranque + destinos

**Corrección al plan, hecha al mirarlo de cerca.** Decía sacar el `when(current)` a un `Router.kt` y
dejar `App.kt` en ~60 líneas. **Eso habría sido un error:** cada rama del `when` usa el contexto de
la app —vm, catálogo, juegos, marcas, preferencias— así que moverla a otro archivo habría recreado
exactamente el prop drilling que la fase 2 acababa de eliminar.

Lo que sí se puede separar es el **mecanismo**: qué hay apilado, cómo se entra y se sale, hacia
dónde anima. Eso no depende de qué pantallas existan y ahora tiene nombre (`BackStack`, `NavHost`).
Los destinos se quedan donde vive su contexto.

De paso, `Screen` estaba declarado 200 líneas **por debajo** de su primer uso, en medio del archivo.

**Verificado en dispositivo el 2026-08-15**: navegación apilada (franja → ficha de plataforma) y «atrás» desapilando de vuelta a Collection, con su animación.

### Fase 4 — Fachadas por rol · ✅ **hecha el 2026-08-15**

**El plan decía otra cosa y estaba mal.** Decía agrupar los 69 miembros en sub-objetos
(`vm.diario.addNote(…)`). Al medirlo, eso tocaba **81 llamadas** y **ninguna pantalla recibía menos**:
seguían tomando el ViewModel entero. Ceremonia con forma de orden.

Lo que la medición mostró es otra cosa. Solo tres archivos usan el ViewModel, tocan 22–26 miembros
cada uno, y **86–92% de lo que usa cada uno es exclusivo suyo**:

| pantalla | usa | exclusivo |
|---|---|---|
| `GameDetailScreen` | 26 | **92%** |
| `HomeScreen` | 24 | **87%** |
| `App` | 22 | **86%** |

Entre pares comparten 1–3 miembros. No hay un ViewModel desordenado: hay **tres contratos distintos
disfrazados de uno**. Eso es una violación del *Interface Segregation Principle*, y el patrón que la
resuelve es **Facade aplicado por rol** — `DiaryViewModel` ya es una fachada sobre el repositorio,
SteamGridDB, el grabador y Whisper; el defecto es que hay *una sola* para tres audiencias.

**Patrones descartados, para que no se vuelvan a proponer:** *Command* (paga con undo/cola/log, y no
hay ninguno), *Strategy* (no hay algoritmo que varíe; el único caso real, `SortOrder`, ya está
resuelto así), *Adapter* (es para interfaces **incompatibles**; acá son compatibles, solo grandes) y
*Proxy* (controla acceso, no forma).

**Por qué en Kotlin sale elegante:** el ViewModel implementa los roles, así que **las 81 llamadas no
se tocan** — `vm.addNote(…)` sigue siendo `vm.addNote(…)`.

Hecho: **`DiarioDeUnJuego`** (26 miembros) para `GameDetailScreen`. Verificado que la restricción es
real: agregar `vm.exportArchive()` en esa pantalla ahora **no compila**
(`Unresolved reference`). Antes era legal.

De paso apareció que `stopVoiceNote()` y `cancelVoiceNote()` devolvían un `Job` que nadie usaba, y
que **`photoCount()` es código muerto** — la UI lo calcula de `games.sumOf { it.photoCount }`.

Los roles, **agrupados por concepto y no por pantalla** —agruparlos por pantalla los volvería el
mismo cajón, repartido en tres—:

| rol | qué es |
|---|---|
| `DiarioDeUnJuego` | todo lo que hacés parado en un juego (26) |
| `Coleccion` | qué tenés, qué querés tener, y cómo se agrega |
| `Ajustes` | las preferencias |
| `Respaldo` | sacar y devolver tus datos |
| `ModeloDeVoz` | bajar y borrar el modelo de Whisper |
| `BuscadorDeCaratulas` | SteamGridDB, para las consolas sin catálogo |

Una pantalla que cruza conceptos declara el compuesto: `PantallaHome : Coleccion, Ajustes,
Respaldo, ModeloDeVoz`.

**Lo que el compilador enseñó, y no estaba en el plan: la raíz no lleva rol.** Al ponerle uno a
`AppRoot`, falló — porque construye *todas* las pantallas, así que la unión de lo que necesita **es**
el ViewModel entero. Un `PantallaRaiz` que herede de los cinco roles es escribir "todo" con más
palabras.

La segregación se cobra en las **hojas**. El trabajo de la raíz es conocerlo todo, y pelearse con eso
es ceremonia.

Verificado que la restricción es real en las dos pantallas nuevas: `vm.startVoiceNote(…)` dentro de
`HomeScreen` **no compila**.

**Costo conocido:** pasar una interfaz a un `@Composable` la vuelve *unstable*. Con Kotlin 2.0.21 el
*strong skipping* está activo por defecto y el ViewModel es siempre la misma instancia, así que
compara igual y la pantalla sigue saltando recomposición. **Verificar con métricas** antes de dar la
fase por cerrada.

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
