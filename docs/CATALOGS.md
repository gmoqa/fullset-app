# Catálogos — procedencia, normalización y mejora continua

Los catálogos (`app/src/main/assets/catalogs/*.json`) **no son una lista estática** de juegos
retro. Son un **dataset vivo** que se corrige y mejora con el tiempo: títulos, fechas, editoras,
carátulas y regiones se refinan a medida que aparecen mejores fuentes (una fecha que pasa de solo
año a año-mes confirmada por una revista/base de datos, una carátula bajo el nombre correcto, un
título mal traducido que se arregla). Este documento fija **de dónde sale cada dato**, **cómo se
mejora**, y **hacia dónde va** la calidad. No aspira a ser perfecto ya; aspira a tener los cimientos
para mejorar sin perder el rastro de por qué.

## Esquema canónico

Cada catálogo es un array JSON, **un objeto por línea**, **ordenado por `slug`**, con estas 11 claves
SIEMPRE presentes (aunque vacías) — lo valida `tools/catalog_lint.py`:

```
{title, platform, region, year, releaseDate, publisher, genre, slug, serial, coverUrl, rating}
```

- `slug`: identidad estable del juego dentro de su plataforma (clave para overrides y merges).
- `region`: hoy siempre `"NTSC-U"` (ver [Región](#región)).
- `year`: entero o `null` — lo que usa la app para mostrar/ordenar.
- `releaseDate`: fecha ISO de **precisión variable** `""` | `"1991"` | `"1991-06"` | `"1991-06-11"`. Es
  la versión precisa y con fuente del año; **mejora con el tiempo** cuando aparece un dato más fino.
- `rating`: clasificación normalizada `""` | `"VRC: GA"` | `"ESRB: Teen"` … (VRC = sistema Sega pre-1994).
- El resto: texto (vacío `""` si falta).

## Procedencia — de dónde obtenemos cada dato

| Campo | Fuente | Cómo |
|---|---|---|
| `title` / `year` / `publisher` | **Wikipedia** "List of \<consola\> games", columna **North America** | scrape del wikitext (`catalog_common.py`), filtrando lanzamientos NA = NTSC-U |
| `serial` | **libretro-database** DAT (`metadat/serial/…`) | match por título/región; no todas las plataformas tienen DAT |
| `coverUrl` | **libretro-thumbnails** (`Named_Boxarts/`) | match por título, región más cercana a NTSC-U, evitando Beta/Proto |
| `genre` / `condition` | manual / catálogo | se completa a mano o desde el catálogo oficial |

### Estado por catálogo (auditado 2026-07-29)

| Catálogo | Builder | Fuente títulos | Serial | Cobertura año/ed/serial/cover | Overrides |
|---|---|---|---|---|---|
| `nes-usa.json` | `build_nes_catalog.py` | Wikipedia NES (col 5) | libretro DAT | 100/100/93/95 | — |
| `snes-usa.json` | **⚠️ ninguno (legacy)** | **no documentada** | — | 100/100/95/92 | — |
| `n64-usa.json` | `build_n64_catalog.py` | Wikipedia N64 (col 5) | libretro DAT | 100/100/96/100 | `n64-usa.json` |
| `genesis-usa.json` | `segaretro_source.py` + `enrich_from_segaretro.py` | Excel del usuario (base Sega Retro) | **Sega Retro** (lista US) | 99/0/**97**/92 · +releaseDate 99% (545 a mes), rating 48% | fixes inline |
| `master-system-usa.json` | Wikipedia + `segaretro_source.py`/`enrich_from_segaretro.py` | Wikipedia MS | **Sega Retro** (dates/serial; incl. Sega Cards) | 100/100/**92**/92 · +releaseDate 91% (98 a mes) | — |
| `psx-usa.json` | `build_psx_catalog.py` | Wikipedia PS (A–L / M–Z) | sin DAT | 100/100/0/87 | — |
| `dreamcast-usa.json` | Wikipedia + `segaretro_source.py`/`enrich_from_segaretro.py` | Wikipedia DC | **Sega Retro** (fecha al día/serial/rating) | 100/100/**88**/99 · +releaseDate 88% (al día), rating 88% | `dreamcast-usa.json` |

Repos de carátula por plataforma (libretro-thumbnails): NES `Nintendo_-_Nintendo_Entertainment_System`,
SNES `Nintendo_-_Super_Nintendo_Entertainment_System`, N64 `Nintendo_-_Nintendo_64`, Genesis
`Sega_-_Mega_Drive_-_Genesis`, Master System `Sega_-_Master_System_-_Mark_III`, PSX
`Sony_-_PlayStation`, Dreamcast `Sega_-_Dreamcast`.

## Sega Retro — la fuente insignia (y el patrón por consola)

**Genesis es el catálogo modelo** del dataset con procedencia. Sus fechas (con mes, a veces día),
seriales (catalog number) y ratings salen de **[Sega Retro](https://segaretro.org)**, la fuente más
certera para las consolas Sega.

**Recolección: por API, automática.** Sega Retro corre MediaWiki con la extensión **Cargo**, o sea
que expone los datos *estructurados* (`action=cargoquery`). Lo que antes obligaba a bajar el HTML a
mano era el rechazo al User-Agent por defecto; con un UA descriptivo la API responde normal. El
recolector vive en `tools/local/segaretro_api.py` (**fuera del repo**, ver `tools/local/README.md`):

```bash
python3 tools/local/segaretro_api.py fetch --console MD --region JP \
    --out tools/sources/genesis-jp-segaretro.json
```

Reproduce la **lista curada** de la wiki replicando el filtro de `Template:GameList` (categoría
`<REGIÓN> <Consola> games` menos accesorios/hardware/aftermarket/download-only) y cruza `releases`
(fecha ISO, catalog number, rating) con `localisednames` (título de la región). Contrastado contra
las fuentes hechas a mano: **434/434** fechas y seriales idénticos en Mega Drive JP, **696/696** en
Mega Drive US, **102/102** en Master System US — y además cubre más títulos.

Luego se aplica con:

- `tools/enrich_from_segaretro.py <catalogo> <fuente>` — aplica la fuente a un catálogo existente
  (match por título; rellena `releaseDate`/`serial`/`rating`, alinea `year`). Ya aplicado a
  **Genesis**, **Master System** (cartuchos + Sega Cards) y **Dreamcast**.
- `tools/build_catalog_from_segaretro.py` — construye un catálogo regional nuevo desde cero.

El parser viejo de HTML (`tools/segaretro_source.py`) queda como respaldo para material que no esté
en las tablas Cargo.

Este es **el patrón a replicar: cada consola con su mejor fuente**, siempre trazada — Sega Retro para
las Sega, una fuente japonesa para los catálogos JP, un foro/base de datos confiable donde Wikipedia no
alcance (p. ej. PSX-US). El norte: fullset dice **de dónde sacó cada dato**.

## De dónde mejoramos — la capa de overrides

`tools/overrides/<catálogo>.json` = `{slug: {campo: valor, …}}`. Se aplican **al final** de la
generación y **pisan** el valor auto-derivado, así las correcciones a mano sobreviven un rebuild desde
cero. Es la vía canónica para mejorar la calidad sin perderla cuando se regenera.

**Convención de procedencia en overrides (nueva):** cada corrección debería documentar de dónde salió,
con claves con guion bajo (que el generador ignora al aplicar):

```json
{
  "disneys-aladdin": {
    "coverUrl": "https://…/Aladdin%20%28USA%29%20%28Final%20Cut%29.png",
    "_source": "libretro-thumbnails (boxart bajo 'Aladdin (USA) (Final Cut)')",
    "_note": "el catálogo lo tenía vacío por desajuste de nombre",
    "_date": "2026-07-29"
  }
}
```

Así, cuando dentro de un año revisemos por qué un dato es lo que es, la respuesta está al lado del dato.

## Región (multi-región: implementado)

**Modelo:** un **archivo de catálogo por (plataforma × región)**, linkeado por `slug`. `platforms.json`
mapea la consola a sus catálogos: `"catalogs": { "NTSC-U": "…usa.json", "NTSC-J": "…jp.json" }`
(o el legacy `"catalog": "…"` = NTSC-U). `Platform.catalogFor(region)` elige el archivo, con **fallback
a NTSC-U** cuando esa región no tiene lista. `GameCatalog` carga/cachea por archivo; `RegionFilter`
(Settings) elige la región activa y la app muestra ese catálogo en Add-game y el timeline.

**Estado:** **NTSC-U** en todas; **NTSC-J** real en **Genesis** (`genesis-jp.json`, 435 juegos desde
Sega Retro JP, fechas al día). El resto cae a NTSC-U en modo JP hasta que tenga su catálogo.

**Dirección — datos por país, selector por región.** El `catalogs` map tiene **key de texto libre**, así
que soporta granularidad de **país** sin cambiar el modelo (Sega Retro separa Europa en UK/Francia/
Alemania/España/Australia/Brasil, cada uno con otra fecha/serial). El plan: los **datos** por país
(`genesis-uk.json`, `genesis-germany.json`…) y el **selector** por región agrupando países (PAL → [UK,
Francia…]), con drill-down a país como refinamiento. Japón es 1:1 con NTSC-J, por eso entró directo.

## Precisión de fechas y confianza (dirección)

Dos evoluciones de esquema que habilitan la "mejora en el tiempo" (a confirmar antes de implementar):

1. **Precisión de fecha:** poder pasar de `year: 1993` → `1993-09` → `1993-09-15`, por región, con
   fuente. Ej.: una revista/base de datos confirma la fecha NA exacta → el dato mejora y queda
   registrado de dónde. (Opción backward-compatible: mantener `year` para la app y agregar un
   `releaseDate` ISO de precisión variable + procedencia.)
2. **Confianza / confirmación:** distinguir lo **auto-derivado** (scrape) de lo **confirmado** (revisado
   contra una fuente), por campo. Permite reportar "cuánto tenemos confirmado" por catálogo y región.

Ambas son **aditivas**: la app usa kotlinx.serialization con `ignoreUnknownKeys`, así que agregar
claves nuevas no rompe nada.

## Roadmap (cimientos → calidad)

- **A. Documentar procedencia** — *este documento*. ✅
- **B. Registro machine-readable** — por catálogo: región, fuentes por campo, builder, overrides,
  cobertura, estado de revisión. Reporta "confirmado por región". El lint/report lo consumen.
- **C. Evolución de esquema** — precisión de fecha + confianza (aditivo, backward-compatible).
- **D. Pipeline de corrección con procedencia** — overrides con `_source`/`_note`/`_date`; lint valida;
  report muestra confirmado vs auto.
- **E. Cerrar huecos** — documentar/reconstruir la procedencia de **SNES** y **Genesis**; mejorar
  **Genesis** (editora 0%); catálogos faltantes (GameCube, Saturn, PS2, PS3).
- **F. Eje de región** — estructura para datos por región (NA/JP/PAL), fechas y cobertura por región.

## Herramientas

- `tools/catalog_lint.py` — valida la forma canónica.
- `tools/catalog_report.py` — cobertura por campo, por catálogo (solo lectura).
- `tools/normalize_catalogs.py` — normaliza y regenera `manifest.json` (**pisa los catálogos**: correr
  solo con confirmación; los catálogos se mantienen a mano).
- `tools/catalog_common.py` — motor de scrape/normalización compartido por los builders.

> **Nota:** los catálogos son documentación curada a mano. No se regeneran a la ligera (el script los
> pisa). Las mejoras van por override (con procedencia) o edición puntual documentada.
