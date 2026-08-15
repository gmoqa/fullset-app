#!/usr/bin/env python3
"""Catálogo de NES (Nintendo Entertainment System) — **solo la lista americana** (`nes-usa.json`).

**Esta consola tiene tres catálogos y este script toca uno.** `catalog_common.build()` lee la
columna de Norteamérica (`na_col`) y escribe un único archivo, así que correr esto deja
`nes-jp.json` y `nes-eu.json` **intactos**, sin avisar. Es la generación más vieja de
builders del repo; los regionales se hicieron después con `build_catalog_from_wikipedia.py`.

Antes de correrlo conviene leer `docs/TOOLS.md`, que explica las tres generaciones que conviven.
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_common import build

OUT = os.path.join(os.path.dirname(__file__), "..", "data", "catalogs", "nes-usa.json")

if __name__ == "__main__":
    print("  OJO: esto regenera solo nes-usa.json; nes-jp y nes-eu no se tocan.")
    # Columnas: Title(0) Developer(1) Publisher(2) First(3) JP(4) NA(5) PAL(6).
    build(page="List of Nintendo Entertainment System games", platform="NES", out=OUT,
          dat_name="Nintendo - Nintendo Entertainment System",
          repo="Nintendo_-_Nintendo_Entertainment_System", na_col=5, year=(1983, 1996))
