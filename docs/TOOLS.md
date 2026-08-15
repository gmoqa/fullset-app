# `tools/` — mapa y auditoría

35 scripts, 4.956 líneas de Python que construyen y validan los 57 catálogos. Este documento es el
mapa que faltaba: **qué hay, cuál usar y qué está muerto**. Auditado el **2026-08-15**, antes de
abrir el repositorio.

---

## Los patrones que hay, y son cuatro

La organización es buena en el fondo: hay un motor compartido, un script por consola y un
enriquecedor por campo. El problema no es la forma, es que **conviven tres generaciones del mismo
patrón sin que nada diga cuál es la vigente**.

### 1. Motor común + config por consola (generación 1)

`catalog_common.build(page, platform, out, dat_name, repo, na_col)` y cuatro scripts de 12–16 líneas
que solo aportan configuración:

    build_nes_catalog.py · build_mastersystem_catalog.py · build_n64_catalog.py · build_dreamcast_catalog.py

**Es la generación más elegante y la más peligrosa.** `build()` escribe **un solo archivo, el
NTSC-U** —su parámetro es `na_col`, la columna de Norteamérica— pero esas cuatro consolas hoy tienen
**las tres regiones**. Correr `build_nes_catalog.py` regenera `nes-usa.json` y deja `nes-jp.json` y
`nes-eu.json` intactos, sin avisar. Quien llegue nuevo va a asumir que reconstruyó la NES.

### 2. Genérico parametrizado por CLI (generación 2)

`build_catalog_from_wikipedia.py`, con `--platform`, `--region`, `--format-cell`… Es el que
realmente construyó la mayoría de los catálogos regionales. No tiene un script por consola: la
consola es un argumento, y la receta vive en `docs/CATALOGS.md`.

### 3. DAT de No-Intro + builder por consola (generación 3)

`nointro.py` como motor y un script por consola: `build_gba_catalog.py`, `build_ds_catalog.py`,
`build_3ds_catalog.py`. Es la generación **más nueva y la mejor documentada** — cada builder explica
en su docstring por qué esa consola no puede salir de Wikipedia.

### 4. Casos propios

`build_psx_catalog.py`, `build_snes_catalog.py`, `build_atari2600_catalog.py`,
`build_catalog_from_segaretro.py`. Cada uno con su motivo escrito. No son deuda: son consolas cuya
fuente no encaja en ningún molde.

**Los enriquecedores, en cambio, sí son un patrón limpio y consistente**: ocho scripts
`enrich_<campo>_<fuente>.py`, casi todos con la misma firma `<catálogo> <fuente> [--dry-run]`. Esa
familia no necesita nada.

---

## Lo que sobra

| qué | por qué |
|---|---|
| `fix_titulos_pegados.py` (159) | reparación **de un solo uso**, ya consumida: arregló 261 títulos que quedaron con el nombre alternativo pegado. El defecto que los causó está corregido en `regional_title()` **y cubierto por tests**, así que no puede volver. |
| `fix_covers_symlink.py` (110) | ídem: resolvió 185 carátulas que apuntaban a symlinks. El defecto está corregido en el desempate de `enrich_covers_libretro` y no reaparece. |
| `catalog_common.build()` + sus 4 builders | superados por la generación 2, y **activamente engañosos**: prometen reconstruir una consola y solo tocan su tercio americano. |
| `DiaryViewModel.photoCount()` | *(fuera de tools, pero del mismo barrido)* código muerto: la UI lo calcula de `games.sumOf { it.photoCount }`. |

Los dos `fix_` valen como **registro histórico** —explican un defecto real y cómo se reparó— pero no
como herramienta ofrecida a quien clone. Su lugar natural es `docs/CATALOGS.md`, que ya cuenta esas
dos historias, no un script ejecutable que pisa 57 archivos.

---

## Lo que falta para abrir

1. **Este mapa**, enlazado desde el README. Hoy hay 13 scripts `build_*` con tres arquitecturas y
   nada que diga cuál corresponde.
2. **Un aviso en los cuatro builders de la generación 1**, o borrarlos. Un script que hace un tercio
   de lo que su nombre promete es peor que no tenerlo.
3. **`build_collection.py`** (314 líneas) importa la colección **desde el Excel personal del autor**.
   Es la única herramienta del repo que no le sirve a nadie más. O se documenta como "así se armó la
   colección de ejemplo", o se va.
4. **`tools/local/`** está gitignoreado y contiene los clientes de Sega Retro y SNES Central. Ningún
   script versionado lo importa —verificado—, así que el repo clona y funciona. Conviene decirlo
   explícitamente para que nadie lo busque.

---

## Lo que **no** hay que hacer

- **Unificar los 13 builders en uno.** Las fuentes son genuinamente distintas: Wikipedia con
  columnas de región, Wikipedia sin ellas, DAT de No-Intro, Sega Retro por API. Forzar un molde
  único produciría un script con quince banderas que nadie entiende.
- **Reorganizar en subcarpetas** (`build/`, `enrich/`, `check/`). Es cosmético: el prefijo del nombre
  ya agrupa, y mover archivos rompe las recetas escritas en `docs/CATALOGS.md`.
- **Tocar los enriquecedores.** Son la parte mejor resuelta.
