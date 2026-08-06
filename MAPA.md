# Mapa de fullset — módulos, funcionalidades y flujos

Inventario de **qué hace la app y por dónde**, para razonar sobre ella sin leer el código.
Complementa a los otros documentos: [`README.md`](README.md) explica qué es, este dice cómo está
armada, [`docs/CATALOGS.md`](docs/CATALOGS.md) cubre el dataset y
[`docs/IOS-PENDIENTE.md`](docs/IOS-PENDIENTE.md) lo que falta en iOS.

Se escribió leyendo el código, no de memoria. Si algo acá no coincide con la app, el documento está
viejo y hay que corregirlo.

---

## 1. La idea en una frase

Llevás tu **colección física** de videojuegos retro y un **diario por juego** (notas, fotos, notas de
voz, cuándo lo jugaste por primera vez). Todo local, sin cuentas ni servidor.

Dos ejes que explican casi todas las decisiones:

- **Poseer vs. jugar.** Collection y Wishlist son sobre *tener*; Backlog y Playing sobre *jugar*. Un
  juego **digital** se juega pero no se posee: por eso no entra a Collection y lleva su badge.
- **Región.** El mismo juego es un producto distinto en cada mercado: otro título, otra fecha, otro
  catalog number, otra caja. El dataset y la app tratan la región como parte de la identidad.

---

## 2. Secciones

Barra inferior de 5, o de 3 según el modo (ver §6).

| Sección | Qué muestra | Alta |
|---|---|---|
| **Collection** | Tu colección **física**, en estanterías por consola | Botón directo al catálogo — acá siempre es física |
| **Backlog** | Lo pendiente por jugar (`games.backlog`) | No se agrega desde acá; se marca en el detalle |
| **Playing** | Lo que estás jugando (`games.playing`), como lista de catálogo | Modal: **física** (del catálogo) o **digital** (SteamGridDB) |
| **Wishlist** | Lo que querés conseguir | Mismo botón que las otras; vaciarla vive en el menú "⋮" |
| **Settings** | Preferencias, respaldo, créditos | — |

Pantallas que se abren **encima** de las secciones:

| Pantalla | Desde | Qué hace |
|---|---|---|
| **Detail** | Tocar cualquier juego | El diario del juego: notas, fotos, voz, estado, primera vez jugado |
| **Add** | Collection y Playing | Elegir consola —solo las que tienen catálogo— → elegir del catálogo. Tres destinos: colección, colección+jugando, wishlist |
| **AddDigital** | Playing | Título a mano + carátula buscada en SteamGridDB |
| **Platform** | Tocar la franja de una consola | Ficha técnica + sus juegos por lanzamiento |
| **Timeline** | Ícono del reloj en **Playing** | Los juegos por *primera vez jugado*, agrupados por año. **Incluye digitales** |
| **Onboarding** | Primer arranque | Elegir qué llevás (ver §6) |

---

## 3. Qué se puede hacer con un juego

Todo esto vive en **Detail** salvo donde se aclare.

**Estado**
- Marcar/desmarcar *playing* y *backlog*.
- **Condición** (solo físicos): `LOOSE` · `LOOSE_MANUAL` · `BOXED` · `COMPLETE`. Se ve como punto de
  color en Collection.
- **Región y catalog number** se muestran como chips junto al año: son la identidad de *esa* copia,
  no un detalle. Dos ediciones del mismo juego se veían idénticas en pantalla.
- **Primera vez jugado**: fecha ISO de precisión variable (`1994`, `1994-06`, `1994-06-08`). Es lo
  que alimenta el Timeline. Al marcar un juego como *playing*, si todavía no tiene fecha la app
  **ofrece la de hoy** — un toque. Nunca la pone sola: si decís que no, el dato queda vacío.

**Diario**
- **Notas** de texto, fechadas, editables.
- **Notas de voz**: se graban en el dispositivo y se **transcriben localmente** con whisper.cpp
  (nada sale del teléfono). Modelo descargable (Base o Small) con verificación de sha256.
- **Fotos**: cámara o galería. También **compartidas desde afuera**: la app figura en el menú
  Compartir del sistema para imágenes y pregunta a qué juego de Playing adjuntarla.
  Todas se redimensionan a 1600px / JPEG 85 al guardarse.

**Carátula**
- Automática del catálogo, o propia (foto/galería), o buscada en SteamGridDB.
- La propia **gana** sobre la automática al mostrar.
- **Tocarla la abre a pantalla completa**, con pellizco y doble toque para acercar (hasta 5×). En el
  estante mide 140dp: alcanza para reconocer el juego, no para leer el catalog number del lomo ni el
  sello. Anda desde el detalle y desde las cards de Playing.

**Salidas**
- Compartir las notas como texto.
- Borrar el juego (arrastra sus notas y fotos, y limpia los archivos de disco).

---

## 4. De dónde salen los juegos

Tres fuentes, y la diferencia importa:

1. **Catálogos propios** — 37 listas por consola y región, 26.884 juegos, empaquetadas en la app.
   Búsqueda **100% sin conexión**. Es lo que se usa para todo lo retro.
2. **SteamGridDB** — en vivo, para lo que ningún catálogo retro tiene: juegos modernos, digitales y
   las carátulas de PS3. Requiere clave de API propia.
3. **A mano** — el título lo escribís vos. Es el camino de los digitales, desde Playing; una consola
   sin catálogo (la PS5) va por acá y **no aparece** en el alta de la colección.

Los catálogos son **datos versionados** con procedencia por campo y validación semántica; eso es
territorio de [`docs/CATALOGS.md`](docs/CATALOGS.md) y no se repite acá.

---

## 5. Modelo de datos

Cuatro tablas, SQLDelight, local.

```
games     id · name · platform · region · slug · digital
          cover_url · cover_path
          playing · backlog · condition · first_played
          release_year · release_date · genre · publisher · serial · created_at

notes     id · game_id → games · text · created_at · audio_path · duration_ms
photos    id · game_id → games · path · caption · created_at
wishlist  id · platform · game · slug · cover_url · added_at   [UNIQUE platform+game]
```

Tres cosas que no se deducen del esquema:

- **`slug` es el vínculo con el catálogo.** Si cambia, el juego deja de recibir actualizaciones. El
  refresco tiene respaldo por título normalizado justamente para reparar eso solo.
- **`digital = 1` significa "no lo poseés"**: no entra a Collection, sí a Playing y al Timeline.
- **Las fotos viajan por nombre, nunca por ruta**, porque las rutas son propias de cada dispositivo.

---

## 6. Modos: qué llevás

Se elige en el primer arranque y se cambia en **Settings → What you keep**.

| Modo | Secciones | Alta desde Playing |
|---|---|---|
| `COLLECTION_AND_DIARY` | las 5 | pregunta física o digital |
| `DIARY_ONLY` | Backlog · Playing · Settings | **directo a digital**, sin preguntar |

Es un filtro de **presentación, no de datos**: `DIARY_ONLY` esconde Collection y Wishlist pero no
borra nada, y volver devuelve todo. Sin colección que llevar, "física" no significa nada y
preguntarlo sería una decisión de más.

---

## 7. Preferencias

| Grupo | Opciones |
|---|---|
| Qué llevás | modo (§6) |
| Apariencia | tema: System · Light · Dark |
| Collection | mostrar títulos bajo la carátula · mostrar franjas de consola |
| Library | región por defecto: NTSC-U · NTSC-J · PAL |
| Notas de voz | modelo (Base/Small) · idioma · borrar el audio tras transcribir |
| Datos | exportar CSV · respaldo y restauración |
| Developer | solo en build de debug |

**Orden de las listas**: por lanzamiento (por defecto, del más viejo al más nuevo), A–Z, o por lo
último cargado.

---

## 8. Respaldo

- **JSON** — solo los datos: juegos, notas, wishlist.
- **ZIP** — los datos más las fotos (`backup.json` + `photos/<nombre>`).

Al restaurar, el formato se detecta por los **primeros bytes**, no por la extensión. El merge es
**unión por clave natural**: nunca borra ni pisa, y **los borrados no se propagan** entre
dispositivos — decisión explícita de la v1.

---

## 9. Estructura del proyecto

```
:shared      commonMain  → dominio, UI (Compose Multiplatform), esquema SQLDelight
             androidMain → actual de cada frontera
             iosMain     → actual de cada frontera (incompleto, ver docs/IOS-PENDIENTE.md)
:app         MainActivity, FullsetApp, whisper (JNI/NDK)
data/        catalogs/ (los 37 JSON + platforms.json + manifest.json) · seed/   ← Android e iOS
tools/       generadores y enriquecedores de catálogo (Python) + lint
iosApp/      proyecto Xcode (project.yml, xcodegen)
```

**Fronteras `expect`/`actual`** — lo que cada plataforma tiene que implementar:

`createSqlDriver` · `createSettings` · `createHttpClient` · `readTextAsset` · `FileStore` ·
`PlatformImage` · `localCoverModel` · `ioDispatcher` · `isCompactWidth` · `BackHandler` ·
`SystemBarsEffect` · `rememberImagePicker` · `rememberCameraCapture` · `rememberMicPermission` ·
`rememberTextSharer` · `rememberCollectionExporter` · `rememberBackupExporter` ·
`rememberBackupImporter` · `rememberArchiveExporter` · `AudioClip`

---

## 10. Flujos principales

**Agregar un juego físico**
```
Collection → "Add game" → elegir consola → (selector de región) → elegir del catálogo
  → entra a la colección, marcado según su condición

La grilla muestra solo las consolas con lista. El conteo bajo cada cubo sale de `platforms.json`,
precalculado: contarlo abriendo los catálogos congelaba la pantalla medio segundo.
```

**Agregar algo que estás jugando**
```
Playing → "Add game" → ¿física o digital?
  física  → catálogo → entra a la colección Y queda marcada como jugando
  digital → título + plataforma + carátula de SteamGridDB → solo Playing, con badge
```
En modo `DIARY_ONLY` el botón salta la pregunta y va directo a la digital.

**Escribir en el diario**
```
tocar un juego → Detail → nota escrita | nota de voz (graba → transcribe local) | foto (cámara/galería)
```

**Adjuntar una foto desde afuera**
```
cualquier app → Compartir → fullset → elegir juego de Playing → se copia y redimensiona
```

**Ver tu historia**
```
Playing → ícono del reloj → Timeline, por año de primera vez jugado (incluye digitales)
```

---

## 11. Dónde están los huecos

Lo que hoy **no** está, para no buscarlo:

- **Género vacío** en 7 consolas —las tres PlayStation con catálogo, GameCube, Dreamcast, Saturn y
  Sega CD—: 22.526 juegos, el 83% del dataset. Es la fuente, no el enriquecedor.
- **iOS incompleto**: cámara, restaurar respaldo, ZIP, transcripción, la clave de SteamGridDB y la
  Share Extension para recibir fotos. Seis tareas, detalladas en [`docs/IOS-PENDIENTE.md`](docs/IOS-PENDIENTE.md).
- **CI parcial**: `.github/workflows/ci.yml` corre en cada push los 84 tests, la compilación de
  Kotlin y el lint de catálogos con sus 12 casos. **No arma el APK**: eso dispara la compilación de
  whisper.cpp por CMake, así que una regresión en `app/src/main/cpp/` o en los recursos de Android
  no se detecta sola.
- **Sin fusionar** las colecciones de dos dispositivos.
- **`first_played` de lo ya cargado sigue vacío.** Marcar un juego como *playing* ahora ofrece la
  fecha, así que el Timeline se llena con el uso, pero los juegos que ya estaban no se completan
  solos: para esos hay que entrar al detalle uno por uno.
