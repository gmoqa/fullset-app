#!/usr/bin/env python3
"""Catálogo de Sega Master System — **solo la lista americana** (`master-system-usa.json`).

**Esta consola tiene tres catálogos y este script toca uno.** `catalog_common.build()` lee la
columna de Norteamérica (`na_col`) y escribe un único archivo, así que correr esto deja
`master-system-jp.json` y `master-system-eu.json` **intactos**, sin avisar. Es la generación más vieja de
builders del repo; los regionales se hicieron después con `build_catalog_from_wikipedia.py`.

Antes de correrlo conviene leer `docs/TOOLS.md`, que explica las tres generaciones que conviven.
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_common import build

OUT = os.path.join(os.path.dirname(__file__), "..", "data", "catalogs", "master-system-usa.json")

if __name__ == "__main__":
    print("  OJO: esto regenera solo master-system-usa.json; master-system-jp y master-system-eu no se tocan.")
    build(page="List of Master System games", platform="Sega Master System", out=OUT,
          dat_name="Sega - Master System - Mark III", repo="Sega_-_Master_System_-_Mark_III",
          na_col=4, year=(1985, 1997))
