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
| **`rememberCameraCapture`** | ❌ **stub** | tarea 1 |
| **`rememberBackupImporter`** | ❌ **no-op** | tarea 2 |
| **`rememberArchiveExporter`** | ⚠️ exporta solo el JSON | tarea 3 |
| `IosWhisperModelStore` / `IosTranscriber` | ❌ stubs | tarea 5, la grande |
| clave de SteamGridDB | ✅ generada desde `local.properties` | tarea 4 hecha |
| recibir fotos compartidas | ❌ falta Share Extension | tarea 6 |

---

## Tarea 1 — Cámara

**Archivo:** `shared/src/iosMain/kotlin/com/gmoqa/fullset/ui/CameraCapture.ios.kt`
**Referencia:** `androidMain/.../CameraCapture.android.kt`

Hoy devuelve `CameraCapture(available = false)`, y la UI —que ya está lista— simplemente no ofrece
la opción: el botón de foto abre la galería directo. Al implementarlo aparece solo el menú "Take
photo / Choose from gallery", sin tocar nada compartido.

1. `UIImagePickerController` con `sourceType = UIImagePickerControllerSourceTypeCamera`.
2. `available` = `UIImagePickerController.isSourceTypeAvailable(.camera)` — en el simulador da false,
   así que **hay que probar en un dispositivo real**.
3. El delegate (`UIImagePickerControllerDelegateProtocol` + `UINavigationControllerDelegateProtocol`)
   entrega un `UIImage`; escribirlo a `NSTemporaryDirectory()` como JPEG y devolver un
   `PlatformImage(ruta)`. `ImagePicker.ios.kt` ya hace exactamente eso en `writeTemp()` — copiar ese
   patrón, incluyendo **retener el delegate** mientras el picker está abierto.
4. **`NSCameraUsageDescription` en `iosApp/iosApp/Info.plist`**. Sin esa clave la app **crashea** al
   abrir la cámara, no falla suave. Ya están `NSMicrophoneUsageDescription` y
   `NSPhotoLibraryUsageDescription`, agregar la de cámara al lado con un texto del estilo
   *"Para fotografiar tus juegos y agregarlos a tu diario."*

No hace falta redimensionar acá: la foto pasa por `FileStore.copyImage`, que ya lo hace.

## Tarea 2 — Restaurar respaldo

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

## Tarea 3 — Respaldo completo (ZIP)

**Archivo:** el mismo. Hoy `rememberArchiveExporter` exporta **solo el JSON**, así que en iOS "Todo"
y "Solo datos" dan el mismo archivo. No se pierden datos, pero las fotos no se respaldan.

Foundation no trae escritura de ZIP, así que hay que elegir:

- **`ZIPFoundation`** vía SPM/CocoaPods, con un `expect/actual` fino. Es la opción sana.
- Escribir ZIP a mano (formato *stored*, sin compresión — los JPEG ya están comprimidos). Evita la
  dependencia pero es bastante código de bajo nivel; solo si sumar una dep es un problema.

El contenido está definido en `commonMain` (`BackupArchive`): entrada `backup.json` más
`photos/<nombre>`. **El JSON es idéntico al del respaldo liviano** — esa fue una decisión deliberada
para que restaurar sea un solo camino de código. No inventar un formato distinto en iOS.

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

## Tarea 6 — Recibir fotos compartidas

En Android la app figura en el menú Compartir para `image/*`: llega por `ACTION_SEND`, se resuelve
el `Uri` en `MainActivity.sharedImageOf()` y `App()` muestra `AttachSharedPhotoDialog` para elegir a
qué juego de Playing adjuntarla. **Todo el diálogo está en `commonMain` y no hay que reescribirlo**;
lo que falta es el lado nativo.

En iOS eso es un **Share Extension**, que es un target aparte del proyecto de Xcode con su propio
bundle id y su propio proceso. Dos cosas a tener en cuenta:

1. La extensión no comparte el sandbox con la app: hay que pasar el archivo por un **App Group**
   (`group.com.gmoqa.fullset`) y despertar a la app, o bien resolver todo dentro de la extensión.
2. `App()` ya acepta `sharedImage: PlatformImage?` con default `null`, así que hoy compila sin
   tocar nada; alcanza con pasárselo desde `MainViewController` cuando exista el canal.

## Tarea 5 — Notas de voz (la grande)

`IosVoiceRecorder` **ya está implementado** con `AVAudioRecorder`. Lo que falta es la transcripción:
`IosWhisperModelStore` e `IosTranscriber` devuelven null/no-op, así que se puede grabar pero la nota
queda sin texto.

1. Compilar `whisper.cpp` como XCFramework y linkearlo (en Android es JNI, ver `app/src/main/cpp/`).
2. `IosWhisperModelStore`: descargar el modelo a Documents y **verificar el sha256**, como hace
   Android — es una descarga remota que se ejecuta como código nativo.
3. `IosTranscriber`: llamar a whisper sobre el WAV.
4. `rememberMicPermission`: hoy pasa directo. AVAudioSession pide el permiso al grabar, así que
   funciona, pero conviene pedirlo explícito para que el diálogo aparezca en un momento entendible.

Es la tarea más pesada y la menos urgente: sin ella la app iOS funciona entera salvo la
transcripción automática.

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
