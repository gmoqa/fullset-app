# fullset

App Android para llevar tu **colección de videojuegos retro** y un **diario por juego**.
Armás tu estantería eligiendo consolas y títulos de catálogos offline, marcás lo que estás
jugando o tenés pendiente, y en cada juego podés escribir **notas** fechadas, grabar **notas de
voz** (transcritas en el dispositivo) y guardar **fotos**. Todo local, sin cuentas ni backend.

UI en **inglés**. Licencia **MIT**.

## Stack (KMP-ready)
- Kotlin 2.0 + Jetpack Compose (Material 3) · minSdk 26 · target/compileSdk 35
- Librerías **multiplataforma** (pensadas para migrar a Compose Multiplatform → iOS):
  - **SQLDelight** (BD; driver Android hoy, Native en iOS) — `games`/`notes`/`photos`/`wishlist`
  - **kotlinx.serialization** (JSON) · **kotlinx-datetime** · **Ktor** (HTTP)
  - **Coil 3** (imágenes) · **multiplatform-settings** (preferencias)
- **whisper.cpp** (código nativo vendored) para transcribir notas de voz sin conexión
- Fotos/carátulas: la imagen se cachea local; en la BD se guarda solo la ruta/URL
- Selección de fotos vía **Photo Picker** (sin permisos runtime)

El paquete Java es `com.gmoqa.diariogamer` por razones históricas; el nombre del producto es
**fullset**.

## Secciones (bottom nav: Collection · Backlog · Playing · Wishlist · Settings)
- **Collection**: tu colección **física**, en estanterías por plataforma, con buscador y un punto
  de color por juego que indica su estado de conservación (loose / loose+manual / boxed / complete).
- **Backlog**: lo pendiente por jugar (bandera `games.backlog`).
- **Playing**: lo que estás jugando ahora (`games.playing`), en cards a todo el ancho. Desde acá
  se agregan los juegos **digitales** (no cuentan como poseídos: no entran a Collection, llevan
  un badge *DIGITAL*).
- **Wishlist**: lo que querés conseguir. Se elige del catálogo; dedup por plataforma+juego.
- **Settings**: tema (System/Light/Dark), región, export de la colección a CSV, idioma de la
  transcripción de voz, y créditos.

## Plataformas por configuración
Se declaran en `assets/config/platforms.json`: cada una con `id`, `name`, `catalog` (JSON en
`assets/catalogs/`), `libretroRepo` (repo de carátulas) y `enabled`. Agregar una consola = una
entrada en ese JSON + su catálogo, sin tocar código (lo carga `PlatformRegistry`). Las consolas
sin catálogo aparecen bloqueadas ("Soon") o, si son current-gen (PS5), se cargan a mano.

**Esquema normalizado** (mismos campos base en todas las listas, en inglés):
```jsonc
// catalogs/*.json  y  seed → wishlist
{ "title", "platform", "region", "year", "publisher", "genre", "slug" }
// seed → library añade:  "condition", "notes", "cover"
```
`region` = `"NTSC-U"`, `year` = número o `null`, campos desconocidos = `""`.

Los catálogos se generan/mantienen con los scripts de `tools/` (Wikipedia + base No-Intro/libretro):
```bash
python3 tools/build_psx_catalog.py       # requiere internet
python3 tools/catalog_lint.py            # valida el esquema
```

## Carátulas
- La **búsqueda** es 100% offline (lee los JSON de `assets/catalogs/`).
- La **imagen** no se versiona ni se empaqueta: se resuelve por URL desde
  [libretro-thumbnails](https://github.com/libretro-thumbnails) tras elegir el título
  (`CoverArt.resolve`, convención No-Intro con fallback de región) y Coil la cachea.

## Compilar e instalar
```bash
cp local.properties.example local.properties   # y completá sdk.dir
./gradlew assembleDebug                         # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                          # instala por adb en el dispositivo conectado
```
Requiere el Android SDK y NDK (por el módulo nativo whisper.cpp). La API key de SteamGridDB es
**opcional** (ver `local.properties.example`): solo habilita el buscador de carátulas de
plataformas modernas.

La app arranca **sin juegos**: el seed (`assets/seed/collection.json`) viene vacío. Agregá los
tuyos desde el botón **+** de Collection.

## Trademarks
Los nombres de consolas y juegos son marcas de sus respectivos dueños; fullset no está afiliado
ni respaldado por ninguno, y los nombres se usan solo para identificar la plataforma de cada juego.
**No se usan los logotipos de marca.** La identidad visual de cada plataforma es un **ícono de
control** (de [Controllercons](https://controllercons.github.io), SIL OFL 1.1) + su nombre en
texto — ver `THIRD_PARTY.md` y `LICENSES-controllercons.txt`.

## Licencia y atribuciones
Código bajo **MIT** (ver [`LICENSE`](LICENSE)). Componentes de terceros y datos: ver
[`THIRD_PARTY.md`](THIRD_PARTY.md). En resumen: whisper.cpp/ggml es MIT; el resto de dependencias
son Apache-2.0; los catálogos son datos factuales derivados de fuentes públicas; las carátulas se
cargan por URL y pertenecen a sus dueños.
