# iOS — lo que falta

**Para un agente trabajando en macOS.** El código iOS **no compila en Linux** (Kotlin/Native para
iOS necesita macOS y Xcode), así que todo lo de acá se escribió a ciegas: compila conceptualmente
pero **nunca se ejecutó**. Asumí que hay errores de cinterop esperando.

El dataset creció bastante desde que se escribió esto: **37 catálogos con 26.884 juegos** en 16
plataformas, **9,3 MB** de assets, que ahora viven en **`data/` en la raíz** y ya no dentro del
módulo de Android: `project.yml` apunta a `../data/catalogs` y no a `../app/src/main/assets/…`.

**Ojo con esto si venías de una copia anterior:** `data/config/` ya no existe. `platforms.json` se
mudó a `data/catalogs/platforms.json`, así que `project.yml` declara **dos** carpetas y no tres, y
`readTextAsset` pide `catalogs/platforms.json`. Si el `.xcodeproj` está generado de antes, regenerarlo
con `xcodegen`; si no, la app arranca sin ninguna consola y sin un error claro.

Fuera de eso no hay nada que agregar al proyecto —los referencia como `type: folder`, así que los
archivos nuevos entran solos— pero sí conviene mirar el
**tiempo del primer arranque**, que es cuando `DiarySeeder` los recorre entero. En Android eso hizo
falta envolverlo en `NonCancellable` porque el `viewModelScope` se cancelaba al bloquear la pantalla
y las migraciones quedaban a medias.

Un detalle del entorno: `project.yml` fija `JAVA_HOME` al JBR que trae Android Studio
(`/Applications/Android Studio.app/…`). Si en esa Mac no está instalado ahí, el script de pre-build
falla antes de compilar nada y el error no dice mucho.

La app Android está completa y es la referencia de comportamiento. Cuando haya duda sobre qué debe
hacer algo, mirá el `actual` de `androidMain` equivalente.

## Antes de tocar nada

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64   # ¿compila el .klib?
./gradlew :app:assembleDebug                        # Android no se debe romper
./gradlew :shared:testDebugUnitTest                 # 84 tests, deben seguir verdes
cd iosApp && xcodegen generate                      # regenerar el .xcodeproj desde project.yml
```

El primer comando es el que importa: **es la primera vez que este código se compila para iOS**.
Arreglar lo que salga de ahí es el paso 0 y probablemente lleve más tiempo que las tareas de abajo.

Sospechas concretas, por orden de probabilidad:

- **`PlatformIo.ios.kt` → `copyImage`**: el redimensionado con `UIGraphicsBeginImageContextWithOptions`
  usa `useContents` sobre `CGSize`/`CGRect`. Es la parte más nueva y la más propensa a fallar por
  cómo Kotlin/Native maneja los structs de CoreGraphics.
- **`Database.ios.kt`**: el `onConfiguration` que activa foreign keys.
- **`ImagePicker.ios.kt`**: retiene el delegate en una lista global porque `PHPicker.delegate` es
  weak. ~~Fuga al cancelar~~ **verificado en la Mac: no hay fuga.** `picker:didFinishPicking:` se
  dispara también al cancelar (con `results` vacío) y ahí se saca el delegate de `activeDelegates` y
  se llama `onPicked(null)`. El caso cancelado ya está cubierto.

## Estado actual, frontera por frontera

| Frontera | iOS | Nota |
|---|---|---|
| `createHttpClient` | ✅ Ktor Darwin | |
| `createSqlDriver` | ✅ NativeSqliteDriver | verificar foreign keys |
| `createSettings` | ✅ NSUserDefaults | |
| `readTextAsset` | ✅ lee del bundle | los JSON van como *folder reference* en Xcode |
| `rememberImagePicker` | ✅ PHPicker | ver fuga del delegate arriba |
| `rememberTextSharer` | ✅ UIActivityViewController | |
| `rememberCollectionExporter` | ✅ share sheet | |
| `copyImage` (resize) | ⚠️ escrito, sin probar | |
| `BackHandler` | ⚪ no-op **a propósito** | iOS no tiene botón atrás global |
| `rememberMicPermission` | ⚠️ pasa directo | AVAudioSession pide el permiso al grabar |
| `rememberCameraCapture` | ✅ UIImagePickerController (.camera) | tarea 1 hecha — probar en dispositivo real (simulador no tiene cámara) |
| `rememberBackupImporter` | ✅ JSON + ZIP *stored* (DEFLATE de Android: pendiente) | tarea 2 |
| `rememberArchiveExporter` | ✅ ZIP real (stored, JSON + fotos) | tarea 3 hecha |
| `IosWhisperModelStore` / `IosTranscriber` | ✅ whisper.cpp real (cinterop) | tarea 5 hecha — falta probar transcripción con modelo+audio en dispositivo |
| clave de SteamGridDB | ✅ generada desde `local.properties` | tarea 4 hecha |
| recibir fotos compartidas | ✅ Share Extension + App Group | tarea 6 hecha — probar el share sheet real desde Fotos |

---

## Tarea 1 — Cámara — ✅ HECHA

**Archivo:** `shared/src/iosMain/kotlin/com/gmoqa/fullset/ui/CameraCapture.ios.kt`

`rememberCameraCapture` usa `UIImagePickerController` con `sourceType = .camera`. El delegate
(`UIImagePickerControllerDelegateProtocol` + `UINavigationControllerDelegateProtocol`) toma el
`UIImage`, lo escribe a `NSTemporaryDirectory()` como JPEG (0.9) y devuelve `PlatformImage(ruta)`
—el mismo patrón de `ImagePicker.ios.kt`, reteniendo el delegate mientras el picker está abierto—.
`available` es `isSourceTypeAvailable(.camera)`. Se agregó `NSCameraUsageDescription` al Info.plist.

Compila, linkea y la app arranca. **Falta probar en un dispositivo real:** el simulador no tiene
cámara, así que ahí `available` da false y la UI no ofrece la opción (comportamiento correcto). No
hace falta redimensionar acá: la foto pasa por `FileStore.copyImage`, que ya lo hace.

## Tarea 2 — Restaurar respaldo ⚠️ JSON + ZIP stored hechos, falta DEFLATE

`rememberBackupImporter` (`FileBackup.ios.kt`): `UIDocumentPickerViewController(asCopy = true)` deja una
copia temporal en el sandbox (sin el baile de `startAccessingSecurityScopedResource`), y se detecta el
formato por los primeros bytes (`PK` = ZIP).

- **`.json`** (solo datos): se restaura entero. ✅
- **`.zip` *stored*** (los que exporta iOS, tarea 3): se parsea por el **central directory** —así los
  data descriptors no importan— y se extraen `backup.json` + `photos/<nombre>` (con protección
  anti *zip slip*). Round-trip iOS↔iOS de respaldo completo. ✅ *(lógica del parser validada contra
  `zipfile`)*.
- **`.zip` DEFLATE** (los que exporta Android): ❌ **pendiente**. Descomprimir DEFLATE en iOS necesita
  cinterop, y el intento con `libcompression` (`compression_decode_buffer`/`COMPRESSION_ZLIB`) generó
  **bindings vacíos** (el paquete `libcompression` queda sin símbolos, incluso con `headerFilter`).
  Por ahora muestra un alert que sugiere restaurar desde el respaldo de solo datos (`.json`). Para
  cerrarlo: depurar la config del cinterop de `libcompression`/`zlib`, o hacer la descompresión del
  lado de Swift y pasar el resultado a Kotlin. **Falta probar el restore a mano** (Settings → *Restore
  from a file*).

<details><summary>Notas originales de la tarea</summary>

**Archivo:** `shared/src/iosMain/kotlin/com/gmoqa/fullset/ui/FileBackup.ios.kt`
**Referencia:** `androidMain/.../FileBackup.android.kt` → `readBackup()`

Hoy `rememberBackupImporter` es `{}`: en iOS se puede exportar pero **no importar**, así que no hay
forma de traer una colección desde otro dispositivo. Es lo más útil de esta lista.

1. `UIDocumentPickerViewController(forOpeningContentTypes:)` con `UTType.json` y `UTType.zip`.
2. Llamar `startAccessingSecurityScopedResource()` antes de leer y `stop…` después — sin eso la
   lectura falla silenciosamente en archivos fuera del sandbox.
3. Detectar el formato por los **primeros bytes** (`PK` = ZIP), no por la extensión — igual que
   Android, para que un archivo renombrado siga funcionando.
4. Si es JSON: `RestoredBackup(texto)`.
5. Si es ZIP: descomprimir (ver tarea 3), extraer las fotos a `FileStore.photosDir` y devolver
   `RestoredBackup(json, mapa nombre→ruta local)`.
6. **Al extraer, usá solo el nombre del archivo** y descartá cualquier ruta que traiga la entrada. Un
   ZIP hostil puede incluir `../../` y escribir fuera del directorio (*zip slip*). Android lo hace con
   `name.substringAfterLast('/')`.

El merge en sí ya está en `commonMain` (`importSnapshot`) y tiene 5 tests: no hay que tocarlo.

</details>

## Tarea 3 — Respaldo completo (ZIP) ✅ HECHA (export)

`rememberArchiveExporter` ya arma un **ZIP real** (`backup.json` + `photos/<nombre>`) y lo comparte
por el share sheet. Se eligió **escribir el ZIP a mano en formato *stored*** (sin compresión), no
ZIPFoundation: una lib de Swift no se puede llamar desde Kotlin/Native, y el `actual` vive en
iosMain. Stored es válido —los JPEG ya vienen comprimidos y el JSON es chico—, `ZipInputStream` de
Android lo restaura igual, y los nombres de entrada coinciden con los que escribe Android. El writer
(`StoredZip` + CRC-32, en `FileBackup.ios.kt`) se validó byte-a-byte contra `zipfile`/`unzip`.

Dos límites conocidos: se construye **en memoria** (alcanza para los tamaños actuales de iOS; si más
adelante hay backups de cientos de MB conviene streamear a archivo) y **no lee** ZIPs con DEFLATE
(los que escribe Android) — eso es el import-ZIP de la tarea 2, que sí necesita `inflate`.

<details><summary>Notas originales de la tarea</summary>

**Archivo:** el mismo. Hoy `rememberArchiveExporter` exporta **solo el JSON**, así que en iOS "Todo"
y "Solo datos" dan el mismo archivo. No se pierden datos, pero las fotos no se respaldan.

Foundation no trae escritura de ZIP, así que hay que elegir:

- **`ZIPFoundation`** vía SPM/CocoaPods, con un `expect/actual` fino. Es la opción sana.
- Escribir ZIP a mano (formato *stored*, sin compresión — los JPEG ya están comprimidos). Evita la
  dependencia pero es bastante código de bajo nivel; solo si sumar una dep es un problema.

El contenido está definido en `commonMain` (`BackupArchive`): entrada `backup.json` más
`photos/<nombre>`. **El JSON es idéntico al del respaldo liviano** — esa fue una decisión deliberada
para que restaurar sea un solo camino de código. No inventar un formato distinto en iOS.

</details>

## Tarea 4 — Clave de SteamGridDB ✅ HECHA

Resuelta con la **opción 2** (una sola fuente para las dos plataformas): un task de Gradle
(`generateSteamGridConfig` en `shared/build.gradle.kts`) lee `STEAMGRIDDB_API_KEY` de la **misma
`local.properties`** que usa Android y genera `SteamGridConfig.kt` en `build/generated/` (gitignored,
la clave no se versiona). `MainViewController` usa `STEAMGRIDDB_API_KEY` en vez de `""`. Con la clave
vacía el buscador queda off, igual que antes; al ponerla en `local.properties` entra en ambas plataformas.

<details><summary>Notas originales de la tarea</summary>

**Archivo:** `shared/src/iosMain/kotlin/com/gmoqa/fullset/MainViewController.kt:33`

Está cableada a `steamGridKey = ""`, así que **en iOS no funciona el buscador de carátulas**. Se usa
en un solo lugar: el alta de un juego **digital** desde Playing (título a mano + carátula buscada).

Antes también intervenía en el alta física de la PS5, pero esa consola salió de la grilla de "Add
game": sin catálogo no hay nada que elegir, así que ahora se carga desde Playing como digital. El
paso "a mano" de `AddGameScreen` se borró con ella.

En Android la clave sale de `local.properties` → `BuildConfig.STEAMGRIDDB_API_KEY`
(ver `app/build.gradle.kts:15,34`), que es un mecanismo de AGP y no existe del lado de Xcode.
Opciones, de menos a más trabajo:

1. Una entrada en `Info.plist` poblada desde un `.xcconfig` que no vaya a git, leída con
   `NSBundle.mainBundle.objectForInfoDictionaryKey`. Es lo más parecido al mecanismo de Android.
2. Generar un archivo Kotlin en `iosMain` desde Gradle a partir de la misma `local.properties`, para
   tener una sola fuente para las dos plataformas.

**No la hardcodees en el fuente**: el repo es público y la clave es personal. `local.properties` está
en `.gitignore` justamente por eso.

</details>

## Tarea 6 — Recibir fotos compartidas — ✅ HECHA

En Android la app figura en el menú Compartir para `image/*` y `App()` muestra
`AttachSharedPhotoDialog`. En iOS eso es un **Share Extension**: un target aparte con su propio
proceso. Como la extensión no comparte sandbox con la app, la foto viaja por un **App Group**.

Cómo quedó armado:

1. **Target `ShareExtension`** (`iosApp/ShareExtension/`, app-extension) declarado en `project.yml`,
   embebido en la app. `ShareViewController.swift` toma la imagen compartida (URL/UIImage/Data), la
   escribe como `shared_incoming.jpg` en el contenedor del App Group `group.com.gmoqa.fullset` y
   despierta la app con `fullset://shared` (recorriendo la responder chain, porque `UIApplication`
   no está disponible en una extensión). No muestra UI: procesa y cierra.
2. **App Group** `group.com.gmoqa.fullset` en los entitlements de ambos targets
   (`iosApp.entitlements`, `ShareExtension.entitlements`). En simulador el contenedor resuelve sin
   firma con team.
3. **Lado app** (`SharedImageInbox.kt`, iosMain): `checkPending()` lee el contenedor, mueve la foto a
   un temporal de la app (dejando el App Group limpio) y la publica en un `StateFlow`.
   `MainViewController` lo observa y se lo pasa a `App(sharedImage = ...)`; `onSharedImageHandled`
   lo limpia. Swift dispara `checkPendingSharedImage()` en `onAppear`, `onOpenURL` y
   `didBecomeActive` (URL scheme `fullset` declarado en el Info.plist del host).

Compila, linkea, la extensión queda embebida y el App Group resuelve en runtime. Probado el camino
de recepción end-to-end (dejando un archivo en el contenedor → aparece `AttachSharedPhotoDialog`).
**Falta probar el share sheet real** compartiendo una foto desde la app Fotos.

## Tarea 5 — Notas de voz (la grande) — ✅ HECHA

`IosVoiceRecorder` (AVAudioRecorder) ya estaba. Ahora la transcripción corre con whisper.cpp real en
el dispositivo, vía cinterop sobre un shim C. Compila, linkea y la app arranca con las estáticas
adentro (~86 MB el `.app` de simulador en Debug).

Cómo quedó armado:

1. **whisper.cpp + ggml** se compilan como estáticas por SDK (`iphonesimulator` + `iphoneos` arm64)
   reutilizando el mismo código vendido que usa Android (`app/src/main/cpp/whisper/`). Lo hace
   `iosApp/whisper/build-whisper.sh` (CMake, sin Metal/BLAS/OpenMP; combina con `libtool` en
   `lib/<sdk>/libwhisper_all.a`). El script es un pre-build en Xcode y se saltea si el `.a` ya existe.
   Las estáticas están gitignoreadas (`iosApp/whisper/lib/`, `build/`): se regeneran del fuente.
2. **Shim C** (`iosApp/whisper/whisper_shim.{h,c}`): API chica y estable
   (`fullset_whisper_init/transcribe/n_segments/segment_text/free`) que el cinterop bindea. Se hizo
   con header propio a propósito — bindear el header del sistema no funcionó (ver nota de
   `libcompression` en tarea 2).
3. **cinterop** `whispercpp` (`shared/src/nativeInterop/cinterop/whispercpp.def`), enganchado en
   `shared/build.gradle.kts`.
4. **`IosWhisperModelStore`**: descarga el modelo a `Documents/models` con Ktor/Darwin, **verifica el
   sha256** (SHA-256 en Kotlin puro), escribe a `.part` y recién renombra si el checksum coincide.
5. **`IosTranscriber`**: lee el WAV (PCM16 mono, saltea el header de 44 bytes → floats), mantiene el
   modelo cargado entre notas y llama a whisper.
6. Link en `iosApp/project.yml`: `LIBRARY_SEARCH_PATHS` por `$(PLATFORM_NAME)` + `-lwhisper_all -lc++`.

**Falta probar en vivo:** descargar un modelo (~60 MB el chico) y transcribir una nota real. El link y
que la app arranque ya están verificados en simulador; la transcripción end-to-end necesita ese paso
manual (red + micrófono).

`rememberMicPermission` sigue pasando directo: AVAudioSession pide el permiso al grabar. Conviene
pedirlo explícito para que el diálogo salga en un momento entendible.

---

## Reglas que no hay que romper

Cosas que costaron encontrar en Android y que conviene no re-descubrir:

- **Los overlays de `Tokens` no cambian con el tema.** Van sobre una carátula, donde el contraste lo
  da la imagen.
- **`fillFromCatalog` solo completa lo vacío.** Nunca pisa lo que el usuario cargó a mano.
- **El merge de respaldo nunca borra ni pisa.** Es unión por clave natural; los borrados no se
  propagan entre dispositivos (decisión explícita de v1).
- **Las fotos viajan por nombre, nunca por ruta.** Las rutas son absolutas y propias de cada
  dispositivo.
- **Permisos mínimos.** Android tiene solo INTERNET y RECORD_AUDIO; la cámara se delega a la app del
  sistema justamente para no pedir CAMERA. Mantener ese criterio en iOS: agregar una clave a
  Info.plist solo cuando la función realmente la necesite.

## Al terminar

- `./gradlew :app:assembleDebug` y los 84 tests deben seguir verdes: si algo compartido cambió para
  que iOS funcione, Android no se puede romper.
- Actualizar la tabla de estado de este archivo.
- `docs/KMP-MIGRATION.md` ya está marcado como documento histórico y remite acá. Si al implementar
  algo descubrís que una de sus **notas / gotchas** dejó de aplicar, corregila ahí: es la parte que
  todavía se lee.
