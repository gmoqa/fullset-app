# fullset

App Android para llevar tu **colección de videojuegos retro** y un **diario por juego**.
Armás tu estantería eligiendo consolas y títulos de catálogos offline, marcás lo que estás
jugando o tenés pendiente, y en cada juego podés escribir **notas** fechadas, grabar **notas de
voz** (transcritas en el dispositivo), sacar **fotos** y anotar **cuándo lo jugaste por primera
vez**. Todo local, sin cuentas ni backend.

UI en **inglés**. Licencia **MIT**.

> **[MAPA.md](MAPA.md)** — módulos, funcionalidades y flujos en un solo lugar. Empezá por ahí si
> querés entender qué hace la app sin leer el código.

## Stack (KMP-ready)
- Kotlin 2.0 + Jetpack Compose (Material 3) · minSdk 26 · target/compileSdk 35
- Librerías **multiplataforma** (pensadas para migrar a Compose Multiplatform → iOS):
  - **SQLDelight** (BD; driver Android hoy, Native en iOS) — `games`/`notes`/`photos`/`wishlist`
  - **kotlinx.serialization** (JSON) · **kotlinx-datetime** · **Ktor** (HTTP)
  - **Coil 3** (imágenes) · **multiplatform-settings** (preferencias)
- **whisper.cpp** (código nativo vendored) para transcribir notas de voz sin conexión
- Fotos/carátulas: la imagen se cachea local; en la BD se guarda solo la ruta/URL. Al guardarlas se
  **redimensionan a 1600px** (JPEG 85): una foto de cámara pasa de ~3,5 MB a ~300 KB
- Fotos vía **Photo Picker** o **cámara** — ambas sin permisos runtime (la captura la hace la app de
  cámara del sistema, así que no hace falta declarar `CAMERA`)

El paquete y el nombre del producto son **fullset** (`com.gmoqa.fullset`).

## Qué llevás: colección + diario, o solo diario
En la primera apertura la app pregunta qué querés llevar. **«Diary only»** esconde *Collection* y
*Wishlist* —las dos secciones sobre *poseer*— y deja Backlog, Playing y Settings. Se cambia cuando
quieras desde **Settings → What you keep**.

Es un filtro de **presentación, no de datos**: no borra nada y volver al modo completo devuelve todo
tal cual estaba. Las funciones de diario (notas, fotos, notas de voz, primera vez jugado) andan
sobre cualquier juego y no dependen del modo.

## Secciones (bottom nav: Collection · Backlog · Playing · Wishlist · Settings)
- **Collection**: tu colección **física**, en estanterías por plataforma, con buscador y un punto
  de color por juego que indica su estado de conservación (loose / loose+manual / boxed / complete).
  Cada estante se ordena **por fecha de lanzamiento** (del más antiguo al más nuevo, así recorrer
  una fila recorre la historia de esa consola); también se puede ordenar A–Z o por lo último cargado.
- **Backlog**: lo pendiente por jugar (bandera `games.backlog`).
- **Playing**: lo que estás jugando ahora (`games.playing`), en cards a todo el ancho. Desde acá
  se agregan los juegos **digitales** (no cuentan como poseídos: no entran a Collection, llevan
  un badge *DIGITAL*).
- **Wishlist**: lo que querés conseguir. Se elige del catálogo; dedup por plataforma+juego.
- **Settings**: tema (System/Light/Dark), región (NTSC-U / NTSC-J / PAL), opciones de vista de
  Collection, export a CSV, **respaldo y restauración**, idioma de la transcripción, y créditos.

**Timeline** (ícono del reloj en el header de Collection): tus juegos ordenados por **cuándo los
jugaste por primera vez**, agrupados por año. Es una pantalla aparte y no un modo de Collection
porque el alcance es distinto — Collection es la colección física y deja afuera los digitales a
propósito, pero un juego digital se jugó igual y le corresponde su lugar en la línea de tiempo.

**Compartir una foto hacia la app.** fullset aparece en el menú Compartir del sistema para
imágenes: al mandarle una captura o una foto, pregunta a cuál de los juegos de **Playing**
adjuntarla y confirma dónde quedó. Se limita a Playing porque compartir pasa mientras jugás, y
ofrecer la colección entera convertiría un gesto de dos toques en una búsqueda. Android por ahora;
en iOS hace falta una Share Extension (ver `docs/IOS-PENDIENTE.md`).

## Plataformas por configuración
Se declaran en `data/config/platforms.json`: cada una con `id`, `name`, `libretroRepo` (repo de
carátulas), `enabled` y sus catálogos. Agregar una consola = una entrada en ese JSON + su catálogo,
sin tocar código (lo carga `PlatformRegistry`). Las consolas sin catálogo aparecen bloqueadas
("Soon") o, si son current-gen (PS5), se cargan a mano.

**Un catálogo por región.** La consola mapea región → archivo; `catalogFor()` cae a NTSC-U cuando
esa región no tiene lista propia, y a cualquiera disponible si la consola salió en una sola (la
SG-1000 es JP only). Sigue aceptándose el formato viejo `"catalog": "…"` como NTSC-U.

```jsonc
"catalogs": { "NTSC-U": "catalogs/genesis-usa.json",
              "NTSC-J": "catalogs/genesis-jp.json",
              "PAL":    "catalogs/genesis-eu.json" }
```

**Esquema normalizado**, 11 claves siempre presentes (aunque vacías), validado por
`tools/catalog_lint.py`:
```jsonc
{ "title", "platform", "region", "year", "releaseDate", "publisher",
  "genre", "slug", "serial", "coverUrl", "rating" }
```
`region` = `"NTSC-U"` | `"NTSC-J"` | `"PAL"` · `year` = número o `null` ·
`releaseDate` = ISO de **precisión variable** (`"1991"` | `"1991-06"` | `"1991-06-11"`) ·
desconocidos = `""`.

Todo el dataset vive en **`data/` en la raíz** —`catalogs/`, `config/` y `seed/`— compartido por
las dos plataformas: Android lo suma a sus assets con `sourceSets` y el proyecto de Xcode lo
referencia como carpeta. Antes vivía dentro del módulo de Android y iOS lo alcanzaba cruzando
hasta `../app/src/main/assets/`.

Hoy son **37 catálogos con 26.884 juegos**. De dónde sale cada dato, con qué fuente y con qué
cobertura: **[docs/CATALOGS.md](docs/CATALOGS.md)**.

```bash
python3 tools/catalog_lint.py                       # valida el esquema
python3 tools/enrich_meta_libretro.py <cat> "<DAT>" # editora/género/fecha desde libretro
python3 tools/enrich_from_segaretro.py <cat> <src>  # fechas/serial/rating desde Sega Retro
```

## iOS
La app comparte UI y lógica vía Compose Multiplatform, pero **iOS todavía no está terminado**: falta
cámara, restaurar respaldos, el ZIP y la transcripción de voz. El plan detallado, con el estado de
cada frontera `expect/actual`, está en **[docs/IOS-PENDIENTE.md](docs/IOS-PENDIENTE.md)** — escrito
para que se pueda retomar desde una Mac (el código iOS no compila en Linux).

## Estilos centralizados
Todo el estilo vive en dos archivos de `shared/src/commonMain/kotlin/com/gmoqa/fullset/ui/`, al modo
de las variables de un SASS: se cambia ahí y se propaga a toda la app.

- **`Theme.kt` → `Palette`**: los colores de marca. Cambiar `primaryLight`/`primaryDark` recolorea
  botones, chips seleccionados, acentos y la barra de estado. El resto del `ColorScheme` (fondos,
  superficies, texto) lo deriva Material 3 solo, que ya garantiza el contraste entre sus roles.
- **`Tokens.kt`**: `Space` (escala de espaciado), `Shape` (pill y esquinas), `Overlay` (las capas
  sobre carátula: velos del hero, chips, texto) y `Size` (tile de carátula, hero, íconos).

Van ahí los valores que se **repiten o expresan una decisión de diseño**; una medida que existe una
sola vez se queda en su pantalla, porque darle nombre global no aclara nada. Dos cosas quedan
deliberadamente afuera: los colores y el aspecto de carátula de cada consola (`PlatformLogos.kt`, son
datos de la plataforma) y los overlays no dependen del tema claro/oscuro porque van sobre una imagen.

## Carátulas
- La **búsqueda** es 100% offline (lee los JSON de `data/catalogs/`).
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

La app arranca **sin juegos**: el seed (`data/seed/collection.json`) viene vacío. Agregá los
tuyos desde el botón **+** de Collection.

### Un APK para pasarle a alguien
```bash
./gradlew :app:assembleRelease   # app/build/outputs/apk/release/app-release.apk (~16 MB)
```
Queda **firmado e instalable**: si existe un `keystore.properties` (fuera del repo) usa esa firma;
si no, cae al keystore de debug. Sirve para compartir a mano — **no para publicar**, porque la Play
Store exige una firma propia y una app firmada con la clave de debug no se puede actualizar después
con otra distinta.

Frente al APK de debug pesa menos y no muestra la sección *Developer* de Settings. Quien lo reciba
tiene que permitir la instalación desde orígenes desconocidos. Ojo: la API key de SteamGridDB de
`local.properties` **queda dentro del APK**; para uno sin ella, buildeá con esa línea vacía.

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
