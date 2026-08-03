#!/usr/bin/env python3
"""Catálogo Sega Master System USA (NTSC-U). Ver tools/catalog_common.py para el método."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_common import build

OUT = os.path.join(os.path.dirname(__file__), "..", "data", "catalogs", "master-system-usa.json")

if __name__ == "__main__":
    build(page="List of Master System games", platform="Sega Master System", out=OUT,
          dat_name="Sega - Master System - Mark III", repo="Sega_-_Master_System_-_Mark_III",
          na_col=4, year=(1985, 1997))
