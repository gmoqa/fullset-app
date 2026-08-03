# Migración a Kotlin Multiplatform (iOS)

> **Documento histórico.** Registra cómo se hizo la migración y los problemas que aparecieron.
> Las secciones "Lo que falta" y "Verificar en la Mac" quedaron **desactualizadas**: la rama
> `kmp-multiplatform` ya se mergeó a `main`, la UI ya vive en `commonMain` y el proyecto Xcode
> existe. Para lo que falta hoy, ver **[IOS-PENDIENTE.md](IOS-PENDIENTE.md)**.
>
> Lo que sigue vigente y vale la pena leer: **Notas / gotchas**. La tabla de fronteras quedó a
> medias —varias que figuran pendientes ya están hechas—; el estado real, frontera por frontera,
> está en [IOS-PENDIENTE.md](IOS-PENDIENTE.md).

Estado de la reestructuración para soportar iOS.

## Estructura

- **`:shared`** — módulo Kotlin Multiplatform (`androidTarget` + `iosX64/iosArm64/iosSimulatorArm64`).
  - `commonMain` — dominio y lógica portable: `Game`, `Condition`, `RegionFilter`, `Platform`,
    `PlatformInfo`, `CatalogEntry`, `GameSearch`, `AppJson`, `CoverArt`, `SteamGridDb`,
    `GameCatalog`, `PlatformRegistry`, el esquema **SQLDelight** (`sqldelight/…/*.sq` + migraciones)
    y las declaraciones `expect`.
  - `androidMain` / `iosMain` — los `actual` de cada frontera.
- **`:app`** — la app Android. Depende de `:shared`. Hoy le quedan solo `MainActivity`,
  `FullsetApp` y **whisper** (JNI/NDK): la UI (28 archivos), el `DiaryViewModel` y el file IO de
  fotos/carátulas ya se mudaron a `commonMain`/`androidMain` de `:shared`.

## Fronteras resueltas con `expect/actual` (en `:shared`)

| `expect` (commonMain) | Android (`androidMain`) | iOS (`iosMain`) |
|---|---|---|
| `createHttpClient()` | Ktor OkHttp | Ktor Darwin |
| `readTextAsset(path)` | AssetManager vía `AndroidApp` | lee de `NSBundle.mainBundle.resourcePath` |
| `createSqlDriver()` | `AndroidSqliteDriver` | `NativeSqliteDriver` |
| `createSettings()` | `SharedPreferencesSettings` | `NSUserDefaultsSettings` |

`AndroidApp` (androidMain) es un holder del `Context`, inicializado en `FullsetApp.onCreate()`, para
que el código común lea assets/DB/prefs en Android sin recibir `Context`.

## Verificar en la Mac

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64   # compila el .klib iOS (dominio + red + DB + settings)
./gradlew :app:assembleDebug                        # la app Android debe seguir verde
```

## Lo que falta (por fase)

3. **UI → Compose Multiplatform** (el paso grande). Aplicar el plugin `org.jetbrains.compose`, mover
   las pantallas a `commonMain`, migrar **recursos** (`R.drawable` de `ic_pad_*`/`ic_shelf`/… y los
   **assets** de `catalogs/`/`config/` → recursos de Compose Multiplatform, accesibles con
   `Res.readBytes`), resolver **Photo Picker** con `expect/actual`, y pasar `DiaryViewModel` de
   `AndroidViewModel` al ViewModel multiplataforma. Al hacerlo, implementar el `actual` de
   `readTextAsset` en iOS (leer del bundle / recursos CMP) y el `Game.coverModel` común (Coil 3 MP).
4. **`iosApp`** — proyecto Xcode + entry point (`MainViewController` en `iosMain` + Swift `App`),
   linkear el framework de `:shared`. Primer build que levanta la UI en el simulador.
5. **whisper** — recompilar `whisper.cpp` como framework iOS + micrófono (`AVAudioEngine`), o dejar
   las notas de voz **stub** en iOS al principio (hay una frontera natural: `WhisperLib`/`VoiceRecorder`).

## Notas / gotchas encontrados

- **Smart-cast entre módulos**: una propiedad nullable de `:shared` (p. ej. `platform.info`) no
  admite smart-cast a no-nulo desde `:app`; usar un `val` local antes del check.
- **`Dispatchers.IO` no existe en `commonMain`** (es JVM). Ktor ya es async, así que se puede quitar
  el `withContext(Dispatchers.IO)` alrededor de llamadas Ktor.
- El **esquema SQLDelight vive en `:shared/commonMain/sqldelight`**; el plugin y las deps también.
  `:app` conserva `coroutines-extensions` (usa `asFlow`/`mapToList` en `DiaryRepository`).
- Los `actual` de iOS **no se compilan en Linux** (Kotlin/Native iOS necesita macOS): revisar el
  `Database.ios.kt` (`onConfiguration` de foreign keys) y `Assets.ios.kt` (stub) al buildear en Mac.
