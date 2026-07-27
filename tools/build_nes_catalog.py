#!/usr/bin/env python3
"""Catálogo NES (Nintendo Entertainment System) USA (NTSC-U). Ver tools/catalog_common.py."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_common import build

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "catalogs", "nes-usa.json")

if __name__ == "__main__":
    # Columnas: Title(0) Developer(1) Publisher(2) First(3) JP(4) NA(5) PAL(6).
    build(page="List of Nintendo Entertainment System games", platform="NES", out=OUT,
          dat_name="Nintendo - Nintendo Entertainment System",
          repo="Nintendo_-_Nintendo_Entertainment_System", na_col=5, year=(1983, 1996))
