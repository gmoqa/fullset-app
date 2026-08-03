#!/usr/bin/env python3
"""Catálogo Sega Dreamcast USA (NTSC-U). Ver tools/catalog_common.py para el método.

La tabla de Wikipedia trae una fecha por región (JP/NA/PAL) como {{dts|…}}; nos quedamos con las
que tienen lanzamiento en Norteamérica (columna NA), que da la biblioteca US (~248 juegos)."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_common import build

OUT = os.path.join(os.path.dirname(__file__), "..", "data", "catalogs", "dreamcast-usa.json")

if __name__ == "__main__":
    # Columnas: Title(0) Developer(1) Publisher(2) First released(3) JP(4) NA(5) PAL(6).
    build(page="List of Dreamcast games", platform="Dreamcast", out=OUT,
          dat_name="Sega - Dreamcast", repo="Sega_-_Dreamcast",
          na_col=5, year=(1998, 2010))
