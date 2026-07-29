# Catálogos — procedencia, normalización y mejora continua

Los catálogos (`app/src/main/assets/catalogs/*.json`) **no son una lista estática** de juegos
retro. Son un **dataset vivo** que se corrige y mejora con el tiempo: títulos, fechas, editoras,
carátulas y regiones se refinan a medida que aparecen mejores fuentes (una fecha que pasa de solo
año a año-mes confirmada por una revista/base de datos, una carátula bajo el nombre correcto, un
título mal traducido que se arregla). Este documento fija **de dónde sale cada dato**, **cómo se
mejora**, y **hacia dónde va** la calidad. No aspira a ser perfecto ya; aspira a tener los cimientos
para mejorar sin perder el rastro de por qué.

## Esquema canónico

Cada catálogo es un array JSON, **un objeto por línea**, **ordenado por `slug`**, con estas 9 claves
SIEMPRE presentes (aunque vacías) — lo valida `tools/catalog_lint.py`:

```
{title, platform, region, year, publisher, genre, slug, serial, coverUrl}
```

- `slug`: identidad estable del juego dentro de su plataforma (clave para overrides y merges).
- `region`: hoy siempre `"NTSC-U"` (ver [Región](#región)).
- `year`: entero o `null`. **Limitación actual:** solo año; ver [Precisión de fechas](#precisión-de-fechas-y-confianza-dirección).
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
| `genesis-usa.json` | **⚠️ ninguno (legacy)** | **no documentada** | — | **90/0/49/92** | — (fixes inline) |
| `master-system-usa.json` | `build_mastersystem_catalog.py` | Wikipedia MS (col 4) | libretro DAT | 100/100/40/92 | — |
| `psx-usa.json` | `build_psx_catalog.py` | Wikipedia PS (A–L / M–Z) | sin DAT | 100/100/0/87 | — |
| `dreamcast-usa.json` | `build_dreamcast_catalog.py` | Wikipedia DC (col 5) | sin DAT | 100/100/0/99 | `dreamcast-usa.json` |

Repos de carátula por plataforma (libretro-thumbnails): NES `Nintendo_-_Nintendo_Entertainment_System`,
SNES `Nintendo_-_Super_Nintendo_Entertainment_System`, N64 `Nintendo_-_Nintendo_64`, Genesis
`Sega_-_Mega_Drive_-_Genesis`, Master System `Sega_-_Master_System_-_Mark_III`, PSX
`Sony_-_PlayStation`, Dreamcast `Sega_-_Dreamcast`.

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

## Región

Hoy **todos los catálogos son NTSC-U** (América). `region` es un string fijo. La app tiene un
`RegionFilter` con NTSC-U soportado y NTSC-J / PAL marcados "Soon".

La **dirección** es que región sea un **eje de primera clase**: poder declarar, por plataforma, qué
regiones tenemos y cuánto está **confirmado** de cada una (NA/JP/PAL), con fechas de lanzamiento por
región. Hoy la estructura no lo soporta; es parte del roadmap.

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
