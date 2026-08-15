#!/usr/bin/env python3
"""Catálogo de Nintendo 64 — **solo la lista americana** (`n64-usa.json`).

**Esta consola tiene tres catálogos y este script toca uno.** `catalog_common.build()` lee la
columna de Norteamérica (`na_col`) y escribe un único archivo, así que correr esto deja
`n64-jp.json` y `n64-eu.json` **intactos**, sin avisar. Es la generación más vieja de
builders del repo; los regionales se hicieron después con `build_catalog_from_wikipedia.py`.

Antes de correrlo conviene leer `docs/TOOLS.md`, que explica las tres generaciones que conviven.
"""

Las carátulas con nombre idiosincrático en libretro (prefijos "007 - ", "RR64 - ", artículo al
final, "&"→"_") viven en tools/overrides/n64-usa.json y se aplican solas al generar/normalizar."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_common import build

OUT = os.path.join(os.path.dirname(__file__), "..", "data", "catalogs", "n64-usa.json")

if __name__ == "__main__":
    print("  OJO: esto regenera solo n64-usa.json; n64-jp y n64-eu no se tocan.")
    # Columnas: Title(0) Developer(1) Publisher(2) First released(3) JP(4) NA(5) PAL(6).
    build(page="List of Nintendo 64 games", platform="Nintendo 64", out=OUT,
          dat_name="Nintendo - Nintendo 64", repo="Nintendo_-_Nintendo_64",
          na_col=5, year=(1996, 2002))
