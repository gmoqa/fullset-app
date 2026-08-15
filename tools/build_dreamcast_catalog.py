#!/usr/bin/env python3
"""Catálogo de Dreamcast — **solo la lista americana** (`dreamcast-usa.json`).

**Esta consola tiene tres catálogos y este script toca uno.** `catalog_common.build()` lee la
columna de Norteamérica (`na_col`) y escribe un único archivo, así que correr esto deja
`dreamcast-jp.json` y `dreamcast-eu.json` **intactos**, sin avisar. Es la generación más vieja de
builders del repo; los regionales se hicieron después con `build_catalog_from_wikipedia.py`.

Antes de correrlo conviene leer `docs/TOOLS.md`, que explica las tres generaciones que conviven.
"""

La tabla de Wikipedia trae una fecha por región (JP/NA/PAL) como {{dts|…}}; nos quedamos con las
que tienen lanzamiento en Norteamérica (columna NA), que da la biblioteca US (~248 juegos)."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_common import build

OUT = os.path.join(os.path.dirname(__file__), "..", "data", "catalogs", "dreamcast-usa.json")

if __name__ == "__main__":
    print("  OJO: esto regenera solo dreamcast-usa.json; dreamcast-jp y dreamcast-eu no se tocan.")
    # Columnas: Title(0) Developer(1) Publisher(2) First released(3) JP(4) NA(5) PAL(6).
    build(page="List of Dreamcast games", platform="Dreamcast", out=OUT,
          dat_name="Sega - Dreamcast", repo="Sega_-_Dreamcast",
          na_col=5, year=(1998, 2010))
