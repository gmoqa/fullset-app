# Third-party notices

fullset se apoya en software y datos de terceros. Sus licencias son propias y
prevalecen sobre la de este proyecto para las partes correspondientes.

## Código incluido en el árbol (vendored)

- **whisper.cpp / ggml** — `app/src/main/cpp/whisper/`
  Transcripción de voz en el dispositivo (backend CPU). MIT License,
  Copyright (c) 2023-2024 The ggml authors. Ver `app/src/main/cpp/whisper/LICENSE`.
  `app/src/main/cpp/whisper_jni.c` es un binding JNI adaptado del ejemplo oficial
  `examples/whisper.android`.

## Íconos

- **Controllercons** — `shared/src/commonMain/composeResources/drawable/ic_pad_*.xml`
  Íconos de control por consola (Atari 2600, NES, SNES, N64, GameCube, Genesis, Master System,
  Saturn, Dreamcast, PlayStation 1/2/3/5), convertidos del SVG **solid** de la v2.1 a vector
  drawables. Copyright (c) 2023 Kieran McClung, bajo **SIL Open Font License 1.1**. Ver
  `LICENSES-controllercons.txt` y https://controllercons.github.io. Reemplazan a los logos de marca
  de las consolas, que son marca registrada.

  **`ic_pad_3ds.xml` no es de Controllercons.** El paquete no trae 3DS: revisadas sus cuatro
  versiones y los 30 glifos que declara el CSS de la v2.1, no hay ninguna mención a `3ds`, `game
  boy` ni `nintendo-ds` en sus 201 archivos. Ese lo dibujamos nosotros con
  `tools/draw_3ds_glyph.py`, en el mismo estilo —silueta maciza, detalles en negativo, viewBox 64—
  para que conviva con los otros. Va bajo la **MIT** del repo, no bajo la OFL.

  El set trae **30 íconos** y usamos 13. Las consolas sin ícono propio reusan el de su familia
  —Sega CD y 32X el del Genesis; Game Gear, SG-1000 y las dos TurboGrafx el del Master System—
  porque compartían el control o eran accesorios de la misma máquina. Antes de dar por sentado que
  falta uno, conviene mirar el inventario: `atari-2600`, `gamecube` y `ps3` estuvieron ahí todo el
  tiempo mientras la app dibujaba un mando genérico en su lugar.

## Dependencias (resueltas por Gradle, no incluidas en el repo)

Todas permisivas; ninguna copyleft.

| Componente | Licencia |
|---|---|
| AndroidX, Jetpack Compose, Material 3 (Google) | Apache-2.0 |
| Coil 3 | Apache-2.0 |
| kotlinx.serialization / kotlinx-datetime / kotlinx-coroutines (JetBrains) | Apache-2.0 |
| Ktor (JetBrains) | Apache-2.0 |
| SQLDelight (Cash App) | Apache-2.0 |
| multiplatform-settings (Russell Wolf) | Apache-2.0 |

## Datos

Los **catálogos** (`data/catalogs/*.json`) son datos factuales —título, fecha de
lanzamiento, catalog number, editora, género, clasificación— compilados de las fuentes de abajo.
Los títulos de los juegos son marcas de sus respectivos dueños. La procedencia campo por campo
está en [`docs/CATALOGS.md`](docs/CATALOGS.md); las correcciones puntuales anotan su fuente en
`tools/overrides/`.

- **[Sega Retro](https://segaretro.org)** — fechas de lanzamiento, catalog numbers y
  clasificaciones de **las 8 consolas Sega en sus 3 regiones** (6226 juegos), más las editoras de
  Saturn, Sega CD y Dreamcast. Contenido bajo **GNU Free Documentation License 1.2**, que declara
  el propio sitio en su `siteinfo`. Se consultó su API de MediaWiki/Cargo.
- **[libretro-database](https://github.com/libretro/libretro-database)** — catalog numbers
  (`metadat/serial/`), editoras (`metadat/publisher/`), géneros (`metadat/genre/`) y fechas
  (`metadat/releaseyear/`, `releasemonth/`). **CC BY-SA 4.0**. Deriva a su vez de los DAT de
  **No-Intro**.
- **[SNES Central](https://snescentral.com)** (Evan G.) — catalog numbers de SNES que
  libretro-database no cubre, tomados de la tabla de etiquetas de cartucho de cada ficha. Se
  consultaron solo las fichas de los títulos faltantes, respetando el `Crawl-delay` de su
  robots.txt.
- **Wikipedia** — listas de títulos de NES, SNES, N64, PlayStation, PlayStation 2, PlayStation 3,
  GameCube, Master System y Dreamcast, y las editoras de las consolas de cartucho. **CC BY-SA 4.0**.
- **[Redump](http://redump.org)**, vía los DAT que republica libretro-database — catalog numbers de
  las consolas de disco (PlayStation, PS2, PS3, GameCube, Saturn, Sega CD, Dreamcast).
- **[SteamGridDB](https://www.steamgriddb.com)** — carátulas de las plataformas que
  libretro-thumbnails no cubre (de PlayStation 3 publica solo 67). La app también la consulta en
  vivo al agregar juegos de PS5. Requiere una clave de API propia, que no viaja en el repo.
- **Carátulas**: NO se incluyen en el repo ni en el APK. Se cargan en tiempo de
  ejecución por URL desde [libretro-thumbnails](https://github.com/libretro-thumbnails),
  SteamGridDB y archive.org. Son propiedad de sus respectivos dueños; su uso aquí es para
  identificación de cada juego.

## Marcas (trademarks)

Los nombres de consolas y juegos (Nintendo, Super Nintendo, Nintendo 64, Sony, PlayStation,
Sega, Genesis, etc.) son marcas de sus respectivos dueños. fullset no está afiliado ni
respaldado por ninguna de esas empresas; los nombres se usan de forma nominativa, solo para
identificar la plataforma de cada juego. **No se usan los logotipos de marca**: la identidad
visual de cada plataforma es un ícono de control (Controllercons, OFL) + su nombre en texto.
