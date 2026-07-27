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

- **Controllercons** — `app/src/main/res/drawable/ic_pad_*.xml`
  Íconos de control por consola (NES, SNES, N64, Genesis, Master System, PlayStation, etc.),
  convertidos de SVG a vector drawables de Android. Copyright (c) 2023 Kieran McClung, bajo
  **SIL Open Font License 1.1**. Ver `LICENSES-controllercons.txt` y
  https://controllercons.github.io. Reemplazan a los logos de marca de las consolas.

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

- **Catálogos de juegos** (`app/src/main/assets/catalogs/*.json`): listas de títulos,
  años y editoras. Son datos factuales derivados de fuentes públicas (listados de
  Wikipedia y la base de datos de No-Intro/libretro). Los títulos de los juegos son
  marcas de sus respectivos dueños.
- **Carátulas**: NO se incluyen en el repo ni en el APK. Se cargan en tiempo de
  ejecución por URL desde [libretro-thumbnails](https://github.com/libretro-thumbnails)
  y archive.org. Son propiedad de sus respectivos dueños; su uso aquí es para
  identificación de cada juego.

## Marcas (trademarks)

Los nombres de consolas y juegos (Nintendo, Super Nintendo, Nintendo 64, Sony, PlayStation,
Sega, Genesis, etc.) son marcas de sus respectivos dueños. fullset no está afiliado ni
respaldado por ninguna de esas empresas; los nombres se usan de forma nominativa, solo para
identificar la plataforma de cada juego. **No se usan los logotipos de marca**: la identidad
visual de cada plataforma es un ícono de control (Controllercons, OFL) + su nombre en texto.
