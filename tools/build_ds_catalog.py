#!/usr/bin/env python3
"""
Catálogo de **Nintendo DS**, las tres regiones, **solo cartuchos físicos**.

Mismo camino que la 3DS y por el mismo motivo: la lista de Wikipedia mezcla los cartuchos con
DSiWare y demás descargables, que nunca existieron en plástico. La fuente principal es el DAT de
**No-Intro** (ver `nointro.py`), que es la lista de volcados de cartucho y trae el `serial` de la
etiqueta. Wikipedia queda como enriquecedor de año y editora.

Es la biblioteca más grande del dataset: **6.061 cartuchos** entre las tres regiones, más que la
PS2 y la SNES juntas.

**La forma de la tabla es distinta a todas las anteriores.** Las páginas de DS —cuatro, partidas
alfabéticamente— traen `Título | Desarrolladora | Editora | First released | JP | NA | EU | AU`,
donde las cuatro últimas son **tildes** (`{{Ya}}`/`{{Na}}`) y la fecha es **una sola**: la del primer
mercado que lo recibió, con su región adentro de la plantilla
(`{{#invoke:vgrtbl|main|EU|2008-10-31}}`).

Esa fecha se asigna **solo a la región que la plantilla nombra**. Repartirla a las tres daría una
cobertura mucho más linda —casi 100%— pero estaría diciendo que un juego japonés de 2005 salió en
Europa en 2005, y en el resto del dataset `releaseDate` significa "salió en ESTA región en esta
fecha". La consecuencia es que PAL queda con año en el 24%: casi ningún juego debutó en Europa. Es
un hueco honesto y no un dato inventado.

    python3 tools/build_ds_catalog.py            # escribe los tres catálogos
    python3 tools/build_ds_catalog.py --dry-run  # solo reporta
"""

from __future__ import annotations

import argparse
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from catalog_common import clean_cell, core, fetch, slug, write_catalog  # noqa: E402
from enrich_dates_wikipedia import row_cells, wikitext  # noqa: E402
from nointro import cartuchos as cartuchos_dat  # noqa: E402

DAT = ("https://raw.githubusercontent.com/libretro/libretro-database/master/"
       "metadat/no-intro/Nintendo%20-%20Nintendo%20DS.dat")
PAGINAS = [f"List of Nintendo DS games ({t})" for t in ("0–C", "D–I", "J–P", "Q–Z")]
PLATAFORMA = "Nintendo DS"
SALIDA = os.path.join(os.path.dirname(__file__), "..", "data", "catalogs")
ARCHIVOS = {"NTSC-U": "ds-usa.json", "NTSC-J": "ds-jp.json", "PAL": "ds-eu.json"}

# El primer argumento de la plantilla dice de qué mercado es la fecha. Corea (`KR`/`KO`) no tiene
# región nuestra, así que su fecha se descarta en vez de asignarse a la que quede más cerca.
DE_MARCA = {"JP": "NTSC-J", "NA": "NTSC-U", "US": "NTSC-U",
            "EU": "PAL", "PAL": "PAL", "AU": "PAL", "AUS": "PAL"}
FECHA = re.compile(r"\{\{#invoke:vgrtbl\|main\|([^|}]*)\|(\d{4})(?:-(\d{2}))?(?:-(\d{2}))?")


def desde_wikipedia() -> tuple[dict[str, dict], dict[str, str]]:
    """(fechas por región, editoras) indexadas por título normalizado.

    La editora **no** depende de la región en esta tabla: es una sola columna para todo el juego, así
    que se guarda aparte y se aplica a las tres. Las fechas sí van por región, cada una a la suya.
    """
    fechas: dict[str, dict] = {r: {} for r in set(DE_MARCA.values())}
    editoras: dict[str, str] = {}
    for pagina in PAGINAS:
        texto = wikitext(pagina)
        for bloque in texto.split("\n|-")[1:]:
            celdas = row_cells(bloque)
            if len(celdas) < 4:
                continue
            titulo = clean_cell(celdas[0])
            if not titulo or titulo.lower().startswith("title"):
                continue
            clave = core(titulo)
            editora = clean_cell(celdas[2]).split(",")[0].strip()
            if editora:
                editoras[clave] = editora
            m = FECHA.search(bloque)
            if not m:
                continue
            region = DE_MARCA.get(m.group(1).strip().upper())
            if not region:
                continue
            año, mes, dia = m.group(2), m.group(3), m.group(4)
            iso = año + (f"-{mes}" if mes else "") + (f"-{dia}" if mes and dia else "")
            fechas[region][clave] = {"year": int(año), "releaseDate": iso}
    return fechas, editoras


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--dry-run", action="store_true", help="no escribe; solo reporta")
    a = ap.parse_args()

    texto = fetch(DAT)
    if isinstance(texto, bytes):
        texto = texto.decode("utf-8", "replace")
    carts = cartuchos_dat(texto, core)
    fechas, editoras = desde_wikipedia()

    for region, lista in carts.items():
        por_fecha = fechas.get(region, {})
        entradas, vistos = [], set()
        for c in lista:
            s = slug(c["title"])
            # Dos volcados con títulos distintos pueden colapsar al mismo slug.
            if not s or s in vistos:
                continue
            vistos.add(s)
            clave = core(c["title"])
            d = por_fecha.get(clave, {})
            entradas.append({
                "title": c["title"], "platform": PLATAFORMA, "region": region,
                "year": d.get("year"), "releaseDate": d.get("releaseDate", ""),
                "publisher": editoras.get(clave, ""), "genre": "", "slug": s,
                "serial": c["serial"], "coverUrl": "", "rating": "",
            })
        n = len(entradas) or 1
        print(f"  {ARCHIVOS[region]:12} {len(entradas):4} cartuchos · "
              f"año {sum(1 for e in entradas if e['year']) * 100 // n}% · "
              f"editora {sum(1 for e in entradas if e['publisher']) * 100 // n}% · "
              f"serial {sum(1 for e in entradas if e['serial']) * 100 // n}%")
        if not a.dry_run:
            write_catalog(os.path.join(SALIDA, ARCHIVOS[region]), entradas)
    return 0


if __name__ == "__main__":
    sys.exit(main())
