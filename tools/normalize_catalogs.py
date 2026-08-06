#!/usr/bin/env python3
"""
Normaliza TODOS los catálogos a la misma forma canónica (mismo esquema de 11 claves, ordenado por
slug, un objeto por línea, overrides aplicados) SIN regenerar el set de juegos desde Wikipedia.

Para los que ya están horneados (NES, Master System, N64) es solo un reformateo + overrides.
Para los viejos (SNES, Genesis, PSX) además hornea `serial` y `coverUrl` faltantes desde el DAT de
libretro-database y el índice de carátulas de libretro-thumbnails (emparejando por título).

Al terminar delega en `catalog_manifest.py` para rehacer el manifest. Requiere internet.
Uso: python3 tools/normalize_catalogs.py
"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_common import normalize

CAT = os.path.join(os.path.dirname(__file__), "..", "data", "catalogs")

# (archivo, DAT de seriales, repo de carátulas de libretro-thumbnails)
CATALOGS = [
    ("nes-usa.json",           "Nintendo - Nintendo Entertainment System",       "Nintendo_-_Nintendo_Entertainment_System"),
    ("snes-usa.json",          "Nintendo - Super Nintendo Entertainment System", "Nintendo_-_Super_Nintendo_Entertainment_System"),
    ("master-system-usa.json", "Sega - Master System - Mark III",                "Sega_-_Master_System_-_Mark_III"),
    ("genesis-usa.json",       "Sega - Mega Drive - Genesis",                    "Sega_-_Mega_Drive_-_Genesis"),
    ("psx-usa.json",           "Sony - PlayStation",                             "Sony_-_PlayStation"),
    ("n64-usa.json",           "Nintendo - Nintendo 64",                         "Nintendo_-_Nintendo_64"),
]


def main():
    for fname, dat, repo in CATALOGS:
        print(f"== {fname} ==")
        normalize(os.path.join(CAT, fname), dat, repo)

    # El manifest lo arma `catalog_manifest.py`, que además calcula hashes y versión. Se llama desde
    # acá para que quien rehornea no tenga que acordarse, pero vive aparte: regenerar el manifest no
    # tiene por qué obligar a correr un script que necesita internet y pisa los catálogos.
    from catalog_manifest import main as rehacer_manifest
    print()
    rehacer_manifest()


if __name__ == "__main__":
    main()
