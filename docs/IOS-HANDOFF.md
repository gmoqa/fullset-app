# iOS — Handoff (cierre de sesión Mac)

Nota corta para la otra instancia. El detalle largo, frontera por frontera, está en
[`IOS-PENDIENTE.md`](IOS-PENDIENTE.md); acá va solo el estado y lo que falta.

## Estado: las 6 tareas de la lista están hechas y pusheadas

Todo en `main` (`origin/main`), commits de esta tanda (autor único Guillermo):

```
8189dc4  iOS: recibir fotos compartidas con una Share Extension (tarea 6)
8cc5cd8  iOS: capturar foto con la cámara (tarea 1)
829f62e  iOS: transcripción de notas de voz con whisper.cpp (tarea 5)
ab90e15  iOS: import de ZIP stored — restore iOS<->iOS (tarea 2)
8621531  iOS: respaldo completo a ZIP con fotos (tarea 3)
054a3af  iOS: restaurar respaldo desde un .json (tarea 2)
743d922  iOS: clave de SteamGridDB desde local.properties (tarea 4)
```

| Tarea | Estado | Verificado |
|---|---|---|
| 1 · Cámara | ✅ `UIImagePickerController(.camera)` | compila/linkea/corre; captura real **falta en dispositivo** |
| 2 · Restore backup | ✅ JSON + ZIP *stored* | round-trip iOS↔iOS; **DEFLATE de Android: pendiente** (ver abajo) |
| 3 · Export ZIP | ✅ ZIP *stored* real (JSON + fotos) | writer validado byte-a-byte vs `zipfile`/`unzip` |
| 4 · SteamGridDB key | ✅ generada desde `local.properties` | una sola fuente para las dos plataformas |
| 5 · Whisper | ✅ whisper.cpp real (cinterop) | compila/linkea/corre; transcripción real **falta en dispositivo** (modelo + audio) |
| 6 · Fotos compartidas | ✅ Share Extension + App Group | recepción probada end-to-end; **share sheet real desde Fotos: falta** |

## Lo que queda (todo son pruebas manuales, no código)

- **Cámara en dispositivo real.** El simulador no tiene cámara: ahí `available` da false y la UI no
  ofrece la opción. Hay que sacar una foto en un iPhone real y ver que entra al diario.
- **Transcripción de whisper en dispositivo.** Falta descargar un modelo (~60 MB el chico) y grabar
  una nota real. El link y que la app arranque con las estáticas ya están verificados en simulador.
- **Share sheet real.** Se probó el lado receptor (dejando un archivo en el contenedor del App Group
  aparece `AttachSharedPhotoDialog`). Falta compartir una foto de verdad desde la app Fotos.
- **`copyImage` (resize) y foreign keys de la DB**: seguían anotados como "escrito, sin probar a
  fondo". No los toqué en esta tanda.

## Límite conocido que sí es código pendiente

- **Import de ZIP con DEFLATE** (los que exporta Android). iOS lee ZIP *stored* (los que exporta iOS)
  pero no DEFLATE: descomprimir necesita cinterop y el intento con `libcompression` dio bindings
  vacíos. Hoy muestra un alert que sugiere restaurar desde el `.json`. Para cerrarlo: depurar el
  cinterop de `libcompression`/`zlib`, o inflar del lado de Swift y pasar el resultado a Kotlin.
  El patrón de cinterop con header propio (el de whisper) funcionó — puede servir de guía.

## Notas de build iOS

- El framework Kotlin lo compila un pre-build de Xcode (`embedAndSignAppleFrameworkForXcode`).
- whisper.cpp se compila con `iosApp/whisper/build-whisper.sh` (pre-build, se saltea si el `.a` ya
  existe). Las estáticas van gitignoreadas: se regeneran del fuente.
- App Group `group.com.gmoqa.fullset` en los entitlements de app y extensión; en simulador resuelve
  sin firma con team.
- El proyecto Xcode se genera con `xcodegen` desde `iosApp/project.yml`.
