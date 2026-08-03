# iOS — lo que falta

**Para un agente trabajando en macOS.** El código iOS **no compila en Linux** (Kotlin/Native para
iOS necesita macOS y Xcode), así que todo lo de acá se escribió a ciegas: compila conceptualmente
pero **nunca se ejecutó**. Asumí que hay errores de cinterop esperando.

La app Android está completa y es la referencia de comportamiento. Cuando haya duda sobre qué debe
hacer algo, mirá el `actual` de `androidMain` equivalente.

## Antes de tocar nada

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64   # ¿compila el .klib?
./gradlew :app:assembleDebug                        # Android no se debe romper
./gradlew :shared:testDebugUnitTest                 # 74 tests, deben seguir verdes
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
  weak. Si el picker se cierra sin elegir nada, **el delegate nunca se saca de `activeDelegates`** —
  hay una fuga ahí que conviene arreglar de paso (implementar también el caso cancelado).

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
| `IosWhisperModelStore` / `IosTranscriber` | ❌ stubs | tarea 4, la grande |

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

## Tarea 4 — Notas de voz (la grande)

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

- `./gradlew :app:assembleDebug` y los 74 tests deben seguir verdes: si algo compartido cambió para
  que iOS funcione, Android no se puede romper.
- Actualizar la tabla de estado de este archivo.
- `docs/KMP-MIGRATION.md` quedó **desactualizado** (habla de una rama ya mergeada y de fases 3 y 4
  como pendientes, cuando la UI ya está en `commonMain` y el proyecto Xcode existe). Conviene
  podarlo y dejar este archivo como la fuente de verdad de lo que falta.
