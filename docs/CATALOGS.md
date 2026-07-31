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
- `region`: `"NTSC-U"` | `"NTSC-J"` | `"PAL"` — la de ese archivo (ver [Región](#región)).
- `year`: entero o `null` — lo que usa la app para mostrar/ordenar.
- `releaseDate`: fecha ISO de **precisión variable** `""` | `"1991"` | `"1991-06"` | `"1991-06-11"`. Es
  la versión precisa y con fuente del año; **mejora con el tiempo** cuando aparece un dato más fino.
- `rating`: clasificación normalizada `""` | `"VRC: GA"` | `"ESRB: Teen"` … (VRC = sistema Sega pre-1994).
- El resto: texto (vacío `""` si falta).

## Procedencia — de dónde obtenemos cada dato

| Campo | Fuente | Cómo |
|---|---|---|
| `title` / `year` / `publisher` | **Wikipedia** "List of \<consola\> games", columna **North America** | scrape del wikitext (`catalog_common.py`), filtrando lanzamientos NA = NTSC-U |
| `serial` | **libretro-database** DAT (`metadat/serial/…`, CC BY-SA 4.0) | match por título/región; no todas las plataformas tienen DAT |
| `serial` (SNES, huecos) | **[SNES Central](https://snescentral.com)** | tabla "Cartridge label information", fila Americas/USA; consulta puntual respetando su `Crawl-delay: 10` |
| `coverUrl` | **libretro-thumbnails** (`Named_Boxarts/`) | match por título, región más cercana a NTSC-U, evitando Beta/Proto |
| `publisher` / `genre` | **libretro-database** (`metadat/publisher/`, `metadat/genre/`, CC BY-SA 4.0) | `tools/enrich_meta_libretro.py`, prefiriendo la etiqueta de región del DAT |
| `publisher` (consolas de disco) | **Sega Retro**, tabla `companies` | rol `Publisher(US/JP/EU)` según la región, con respaldo al `Publisher` genérico |
| `condition` | manual | lo carga el usuario desde la app |

### Estado por catálogo (auditado 2026-07-29)

**Sega: cobertura completa.** Las 8 consolas × las 3 regiones (**6226 juegos**), con Sega Retro como
fuente única vía API (`tools/local/segaretro_api.py`). Fechas ISO de precisión variable, catalog
number y rating salen todos de ahí; la carátula de libretro-thumbnails prefiriendo la región.

| Catálogo | Juegos | año/serial/cover | rating |
|---|---|---|---|
| `sg-1000-jp.json` | 79 | 100/98/91 | — |
| `master-system-usa.json` | 114 | 100/94/92 | — |
| `master-system-jp.json` | 84 | 98/96/83 | — |
| `master-system-eu.json` | 363 | 93/83/73 | 26% |
| `genesis-usa.json` | 711 | 99/98/92 | 68% |
| `genesis-jp.json` | 435 | 99/99/87 | 3% |
| `genesis-eu.json` | 747 | 88/84/79 | 41% |
| `game-gear-usa.json` | 234 | 99/99/91 | 61% |
| `game-gear-jp.json` | 199 | 98/97/81 | 23% |
| `game-gear-eu.json` | 208 | 95/97/87 | 32% |
| `sega-cd-usa.json` | 146 | 100/97/93 | 83% |
| `sega-cd-jp.json` | 116 | 99/99/87 | 5% |
| `sega-cd-eu.json` | 109 | 90/97/93 | 68% |
| `32x-usa.json` | 31 | 96/96/87 | 96% |
| `32x-jp.json` | 18 | 94/94/72 | 88% |
| `32x-eu.json` | 26 | 96/96/92 | 96% |
| `saturn-usa.json` | 251 | 99/100/92 | 99% |
| `saturn-jp.json` | 1091 | 99/99/79 | 88% |
| `saturn-eu.json` | 247 | 98/98/94 | 98% |
| `dreamcast-usa.json` | 248 | 100/93/99 | 93% |
| `dreamcast-jp.json` | 545 | 99/94/77 | 91% |
| `dreamcast-eu.json` | 224 | 97/97/94 | 96% |

**Editora y género** salen de `metadat/publisher/` y `metadat/genre/` de libretro-database para las
consolas de cartucho, y de la tabla `companies` de Sega Retro para las de disco (Saturn, Sega CD,
Dreamcast), que libretro no cubre. Ambas fuentes distinguen región: la editora cambia según el
mercado, y en Sega Retro el rol viene etiquetado (`Publisher(US)`, `Publisher(JP)`, `Publisher(EU)`).
Era el hueco más viejo del dataset — los catálogos Sega tenían editora en **0%**.

Dos cosas que sorprenden y son correctas: **Master System PAL (363) triplica al americano (114)** —
en Europa y Brasil fue *la* consola de Sega, no el Genesis; y **Saturn JP (1091) cuadruplica al
americano** — la Saturn se vendió sobre todo en Japón.

**La clasificación depende de quién clasifica.** El mismo código significa cosas distintas por
territorio y época, así que se normaliza con ambos datos: `12` es **USK** en Alemania, **ClassInd**
en Brasil y **ELSPA/PEGI** en el resto de Europa. Ojo con la época: **PEGI recién existe desde 2003**,
o sea después de toda la era que catalogamos (la Dreamcast se discontinuó en 2001), así que lo
europeo es ELSPA salvo lanzamientos tardíos. En Japón, antes de CERO (2002) regía la autorregulación
de Sega: 全年齢 (todas las edades), 18禁 y la marca X. En EE.UU., VRC hasta 1994 y ESRB después.

**No-Sega (pendientes de una fuente con procedencia — ver roadmap E):**

| Catálogo | Builder | Fuente títulos | Serial | año/ed/serial/cover |
|---|---|---|---|---|
| `nes-usa.json` | `build_nes_catalog.py` | Wikipedia NES (col 5) | libretro DAT | 100/100/93/95 |
| `snes-usa.json` | **⚠️ ninguno (legacy)** | **no documentada** | libretro DAT + **SNES Central** | 100/100/**98**/92 |
| `n64-usa.json` | `build_n64_catalog.py` | Wikipedia N64 (col 5) | libretro DAT | 100/100/96/100 |
| `psx-usa.json` | `build_psx_catalog.py` | Wikipedia PS (A–L / M–Z) | sin DAT | 100/100/0/87 |

Repos de carátula (libretro-thumbnails): NES `Nintendo_-_Nintendo_Entertainment_System`, SNES
`Nintendo_-_Super_Nintendo_Entertainment_System`, N64 `Nintendo_-_Nintendo_64`, PSX
`Sony_-_PlayStation`, Genesis `Sega_-_Mega_Drive_-_Genesis`, Master System
`Sega_-_Master_System_-_Mark_III`, Game Gear `Sega_-_Game_Gear`, Sega CD
`Sega_-_Mega-CD_-_Sega_CD`, 32X `Sega_-_32X`, Saturn `Sega_-_Saturn`, Dreamcast `Sega_-_Dreamcast`,
SG-1000 `Sega_-_SG-1000`.

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

**Caso real (2026-07-30):** `snes-usa.json` traía doce catalog number de **otra región** — `SNSP-`
(Europa) y `SHVC-` (Japón) — porque su generador legacy tomaba de libretro la fila equivocada. En una
lista NTSC-U eso no identifica la copia que tenés, así que se verificó cada uno contra la entrada
`(USA)` de libretro-database: seis se corrigieron y **seis quedaron vacíos** porque libretro no tiene
la entrada estadounidense. Un dato equivocado es peor que ninguno. Ver `tools/overrides/snes-usa.json`,
donde cada corrección dice de dónde salió.

Los que libretro no cubría se cerraron con **[SNES Central](https://snescentral.com)** (Evan G.), que
publica la etiqueta del cartucho por región. Se consultaron **solo las fichas de los títulos que
faltaban**, respetando el `Crawl-delay: 10` de su robots.txt — no se replicó el sitio ni se copiaron
sus textos o escaneos: lo que se toma es el código impreso en el cartucho, un hecho, y se integra a
nuestra lista con la fuente anotada juego por juego. La cobertura de serial pasó de 93% a **98%**.

Eso además destapó **cinco pares de juegos que compartían catalog number** por el match difuso del
generador legacy (Brawl Brothers con Rival Turf!, The Lost Vikings con su secuela, Star Fox con el
cartucho de competencia Super Weekend…). Resueltos contra la ficha de cada uno.

## Región (multi-región: implementado)

**Modelo:** un **archivo de catálogo por (plataforma × región)**, linkeado por `slug`. `platforms.json`
mapea la consola a sus catálogos: `"catalogs": { "NTSC-U": "…usa.json", "NTSC-J": "…jp.json" }`
(o el legacy `"catalog": "…"` = NTSC-U). `Platform.catalogFor(region)` elige el archivo, con **fallback
a NTSC-U** cuando esa región no tiene lista. `GameCatalog` carga/cachea por archivo; `RegionFilter`
(Settings) elige la región activa y la app muestra ese catálogo en Add-game y el timeline.

**Estado:** **todas las consolas Sega tienen las tres regiones** (la SG-1000 solo NTSC-J: nunca salió
de Japón — en Europa su lugar lo ocupó el Master System). Las no-Sega (NES, SNES, N64, PSX) siguen
solo con NTSC-U y caen a esa lista en las otras regiones. `catalogFor` cae a cualquier catálogo
disponible cuando la región pedida no tiene lista ni hay default, para que una consola de una sola
región no se vea vacía.

**PAL = unión de territorios, no un país.** Sega Retro separa Europa por país, pero cada país es
casi siempre la distribución local del lanzamiento paneuropeo (95%+ de cada uno cae dentro de `EU`):
un archivo por país sería el mismo listado repetido doce veces. Así que PAL se arma como la **unión**
de `EU` + los países europeos + Australia + Brasil. `EU` aporta el grueso y cada país sus exclusivos:
Australia 55 y sobre todo **Brasil 77**, los Tec Toy en portugués que no salieron en ningún otro lado.
Brasil es técnicamente PAL-M (60 Hz), pero va acá porque sus juegos no existen en ninguna otra lista.

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
- **E. Cerrar huecos** — **Sega cerrado** ✅ (8 consolas × 3 regiones desde Sega Retro). Queda:
  documentar la procedencia de **SNES** (sin builder), editora 0% en los catálogos Sega (Sega Retro
  no la trae en `releases`: hace falta una 2da fuente), y los faltantes **GameCube, PS2, PS3**.
- **F. Eje de región** — **cerrado para Sega** ✅: NTSC-U, NTSC-J y PAL en las 8 consolas. Si alguna
  vez se quiere drill-down por país dentro de PAL, el modelo lo soporta (la key del mapa `catalogs`
  es texto libre); hoy no compensa porque los países son 95% el mismo listado.

## Herramientas

- `tools/catalog_lint.py` — valida la forma canónica.
- `tools/catalog_report.py` — cobertura por campo, por catálogo (solo lectura).
- `tools/normalize_catalogs.py` — normaliza y regenera `manifest.json` (**pisa los catálogos**: correr
  solo con confirmación; los catálogos se mantienen a mano).
- `tools/catalog_common.py` — motor de scrape/normalización compartido por los builders.

> **Nota:** los catálogos son documentación curada a mano. No se regeneran a la ligera (el script los
> pisa). Las mejoras van por override (con procedencia) o edición puntual documentada.
