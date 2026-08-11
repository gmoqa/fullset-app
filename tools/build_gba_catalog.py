#!/usr/bin/env python3
"""
Catálogo de **Game Boy Advance**, las tres regiones, desde el DAT de No-Intro.

A diferencia de la DS y la 3DS, acá el DAT no se usa para **separar** lo físico de lo digital —la
GBA no tuvo tienda, todo salió en cartucho— sino simplemente porque es la lista más completa y la
única que trae el `serial` de la etiqueta (`AGB-AXVE-USA`).

Y a diferencia de esas dos, **no hace falta Wikipedia**: libretro-database sí publica metadatos para
GBA (género 3.110, editora 3.188, año 2.088 entradas, contra ~100 y 0 para DS/3DS), así que año,
editora y género salen todos de `enrich_meta_libretro.py`, que además elige la entrada de la región
que corresponde. Un builder de una sola fuente es menos superficie donde equivocarse.

    python3 tools/build_gba_catalog.py            # escribe los tres catálogos
    python3 tools/build_gba_catalog.py --dry-run  # solo reporta

Después:

    python3 tools/enrich_covers_libretro.py data/catalogs/gba-usa.json Nintendo_-_Game_Boy_Advance --prefer "(USA"
    python3 tools/enrich_meta_libretro.py   data/catalogs/gba-usa.json "Nintendo - Game Boy Advance"
"""

from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from catalog_common import core, fetch, slug, write_catalog  # noqa: E402
from nointro import cartuchos as cartuchos_dat  # noqa: E402

DAT = ("https://raw.githubusercontent.com/libretro/libretro-database/master/"
       "metadat/no-intro/Nintendo%20-%20Game%20Boy%20Advance.dat")
PLATAFORMA = "Game Boy Advance"
SALIDA = os.path.join(os.path.dirname(__file__), "..", "data", "catalogs")
ARCHIVOS = {"NTSC-U": "gba-usa.json", "NTSC-J": "gba-jp.json", "PAL": "gba-eu.json"}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--dry-run", action="store_true", help="no escribe; solo reporta")
    a = ap.parse_args()

    texto = fetch(DAT)
    if isinstance(texto, bytes):
        texto = texto.decode("utf-8", "replace")

    for region, lista in cartuchos_dat(texto, core).items():
        entradas, vistos = [], set()
        for c in lista:
            s = slug(c["title"])
            # Dos volcados con títulos distintos pueden colapsar al mismo slug.
            if not s or s in vistos:
                continue
            vistos.add(s)
            entradas.append({
                "title": c["title"], "platform": PLATAFORMA, "region": region,
                "year": None, "releaseDate": "", "publisher": "", "genre": "", "slug": s,
                "serial": c["serial"], "coverUrl": "", "rating": "",
            })
        print(f"  {ARCHIVOS[region]:13} {len(entradas):4} cartuchos · serial "
              f"{sum(1 for e in entradas if e['serial']) * 100 // (len(entradas) or 1)}%")
        if not a.dry_run:
            write_catalog(os.path.join(SALIDA, ARCHIVOS[region]), entradas)
    return 0


if __name__ == "__main__":
    sys.exit(main())
