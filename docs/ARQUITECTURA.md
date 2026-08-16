# Arquitectura

Por dónde entrar al código. Escrito el **2026-08-15**, cerrando el refactor de `docs/REFACTOR.md`.

Es una app **Compose Multiplatform**: Android funciona y está en uso; iOS está en marcha (ver
`docs/IOS-PENDIENTE.md`). Todo lo que no depende de la plataforma vive en `shared/src/commonMain`.

---

## Los paquetes, y qué es cada uno

```
com.gmoqa.fullset/
├── App.kt              arranque + los destinos de navegación
├── DiaryViewModel.kt   el estado de la app, y quien habla con todo lo de abajo
├── DiarioDeUnJuego.kt  el rol del detalle (ver `roles/`)
│
├── domain/     LAS REGLAS. Funciones puras, sin Compose. Testeables solas.
├── roles/      Qué parte del ViewModel ve cada pantalla.
├── navigation/ La pila de navegación y los destinos posibles.
├── data/       Los datos: el modelo, de dónde salen y dónde se guardan.
└── ui/         Las pantallas y sus piezas.
```

### `domain/` — dónde mirar primero

Si querés entender **qué hace** la app, esto es lo más corto que hay. Seis funciones puras, cada una
con sus tests:

| archivo | responde |
|---|---|
| `Completitud.kt` | cruza el catálogo de una consola con tu colección → el «148 of 1893» |
| `Estanterias.kt` | agrupa por consola, ordena dentro de cada estante |
| `Backlog.kt` | qué entra al Backlog según el modo |
| `LineaDeTiempo.kt` | agrupa el diario por año |
| `AltaDeJuego.kt` | qué juego ya tenés anotado y dónde corta cada región |
| `GameSearch.kt` | la búsqueda difusa: acentos, orden libre, un typo por palabra, siglas |

Ninguna sabe de Compose ni de la base. Se prueban en milisegundos y son la red que sostiene el resto.

### `roles/` — quién puede hacer qué

`DiaryViewModel` expone 69 miembros, pero **ninguna pantalla necesita los 69**. Cada una declara el
rol que usa, y el compilador impide que se pase de ahí: pedirle `exportArchive()` desde el detalle
de un juego **no compila**.

    Coleccion · Ajustes · Respaldo · ModeloDeVoz · BuscadorDeCaratulas · DiarioDeUnJuego

**La raíz (`AppRoot`) no lleva rol, a propósito**: construye todas las pantallas, así que la unión de
lo que necesita *es* el ViewModel entero. La segregación se cobra en las hojas.

### `data/` — el modelo y sus fuentes

Cuatro cosas distintas conviven acá, y conviene saber cuál es cuál:

| | archivos |
|---|---|
| **modelo** | `Models.kt` (Game, SortOrder…), `Platform.kt`, `CatalogEntry.kt` |
| **persistencia** | `Database.kt` (SQLDelight), `DiaryRepository.kt`, `Settings.kt`, `SyncSnapshot.kt` |
| **fuentes externas** | `SteamGridDb.kt`, `CoverArt.kt`, `Http.kt` |
| **servicios de plataforma** | `Assets.kt`, `Voice.kt`, `PlatformIo.kt`, `GameCoverModel.kt` |

Están juntas en un paquete y **no se separaron**; el porqué está abajo.

### `ui/`

Una pantalla por archivo. Las que reciben muchos datos usan **estado y acciones separados**
(`SettingsUiState` / `SettingsActions`, `Preferencias` / `PreferenciasActions`), para que la firma
diga «esto muestro, esto puedo hacer» en vez de ser una lista de treinta parámetros.

La identidad visual de cada consola —color de franja, glifo del mando, aspecto de carátula— está en
**un solo lugar**: `PlatformLogos.kt`.

---

## La regla de dependencias

    ui  →  domain  →  data (modelo)
     ↓
    ViewModel  →  data (persistencia, red, plataforma)

En una frase: **la UI puede llamar a `domain`, y `domain` solo sabe del modelo**. Lo que toca la
base, la red o el sistema operativo lo maneja el ViewModel.

Hoy es una **convención documentada, no algo que el compilador imponga**. Ver abajo.

---

## Lo que NO se hizo, y por qué

El plan (`docs/REFACTOR.md`, fase 5) proponía renombrar los paquetes a `model/ store/ remote/
platform/`, que haría cumplir la regla de dependencias en vez de solo enunciarla. **Se midió y se
descartó**, y el motivo es específico de este repo:

1. **Costo:** 23 archivos a mover en `commonMain`, más sus `actual` en `androidMain` y `iosMain`, y
   **167 imports** a reescribir. Sería el commit más ruidoso de la historia del proyecto, justo
   antes de abrirlo.

2. **Riesgo que no se puede cerrar acá:** de esos 23, **10 tienen `expect`/`actual`** o los referencia
   iOS. Un par `expect`/`actual` tiene que moverse en las tres fuentes a la vez o rompe — y **eso no
   se puede verificar en esta máquina**: Kotlin/Native no compila targets de Apple en Linux
   (`compileKotlinIosSimulatorArm64` sale `SKIPPED`), y **CI tampoco compila iOS**. Se descubriría
   tarde y en la máquina de otro.

3. **Los límites son discutibles.** ¿`Database.kt` es persistencia o plataforma? Tiene `expect`.
   ¿`GameCatalog.kt` es fuente o regla? Lee assets pero también busca. Un split con la mitad de las
   fronteras opinables ordena menos de lo que promete.

Lo que sí se hizo: **`GameSearch.kt` pasó de `data/` a `domain/`**. Era una regla pura —«código
puro, ordenado por relevancia», lo dice su propio comentario— viviendo entre las fuentes de datos.
Un archivo, sin `expect`, verificable de punta a punta.

**Si algún día se hace el renombre completo**, la condición es tener iOS en CI. Sin eso, mover
`expect`/`actual` es escribir un cheque que otro tiene que cubrir.
