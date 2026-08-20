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
| 2 · Restore backup | ✅ JSON + ZIP *stored* + DEFLATE | round-trip iOS↔iOS y lectura de ZIP de Android (DEFLATE) verificada en simulador |
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
- **`copyImage` (resize)**: ✅ **verificado** en el simulador ejecutando el path Kotlin/Native real
  (el marshaling de `useContents` sobre `CGSize`/`CGRect` era el riesgo): 1200x800 con maxEdge 512
  da 512x342, JPEG válido. Ya no es una incógnita.
- **Foreign keys de la DB** (`onConfiguration`): el path corre al arrancar sin crashear, pero no probé
  la restricción con una violación deliberada. Menor.

## Import de ZIP con DEFLATE — cerrado

- iOS ahora lee ZIP *stored* (los de iOS) **y DEFLATE** (los de Android). El inflate usa
  `platform.zlib` —el zlib del sistema, que Kotlin/Native expone en targets Apple sin cinterop
  extra— con `windowBits = -MAX_WBITS` (deflate crudo, sin cabecera zlib). No hizo falta
  `libcompression` ni pasar por Swift. Verificado con un ZIP real hecho por `zipfile.ZIP_DEFLATED`
  (método 8): `readZip` restaura el `backup.json` y las fotos, corriendo en el simulador.

## Antes de correr en un iPhone real

- **Firma / provisioning sin configurar** (`project.yml` no tiene `DEVELOPMENT_TEAM` ni estilo de
  firma). En simulador no hace falta; en device la app no instala sin team + perfiles. Lo más simple:
  abrir el `.xcodeproj` una vez en Xcode con "Automatically manage signing".
- **El App Group (tarea 6) necesita cuenta de pago** (Apple Developer Program). Las cuentas
  personales gratis instalan en device pero **no habilitan App Groups**, así que en un iPhone con
  cuenta gratis la Share Extension no provisiona. Hay que registrar los dos bundle IDs
  (`com.gmoqa.fullset`, `com.gmoqa.fullset.ShareExtension`) y el grupo en el portal.
- **App icon: pendiente en las dos plataformas** (todavía no hay arte). No es un hueco de iOS;
  `ASSETCATALOG_COMPILER_APPICON_NAME` queda vacío hasta que exista el ícono.

## Notas de build iOS

- El framework Kotlin lo compila un pre-build de Xcode (`embedAndSignAppleFrameworkForXcode`).
- whisper.cpp se compila con `iosApp/whisper/build-whisper.sh` (pre-build, se saltea si el `.a` ya
  existe). Las estáticas van gitignoreadas: se regeneran del fuente.
- App Group `group.com.gmoqa.fullset` en los entitlements de app y extensión; en simulador resuelve
  sin firma con team.
- El proyecto Xcode se genera con `xcodegen` desde `iosApp/project.yml`.
