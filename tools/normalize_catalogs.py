#!/usr/bin/env python3
"""
Normaliza TODOS los catálogos a la misma forma canónica (mismo esquema de 9 claves, ordenado por
slug, un objeto por línea, overrides aplicados) SIN regenerar el set de juegos desde Wikipedia.

Para los que ya están horneados (NES, Master System, N64) es solo un reformateo + overrides.
Para los viejos (SNES, Genesis, PSX) además hornea `serial` y `coverUrl` faltantes desde el DAT de
libretro-database y el índice de carátulas de libretro-thumbnails (emparejando por título).

Al terminar escribe `catalogs/manifest.json` con conteo + cobertura por plataforma (sin timestamps,
para que el diff refleje solo avance real). Requiere internet. Uso: python3 tools/normalize_catalogs.py
"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_common import normalize

CAT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "catalogs")

# (archivo, DAT de seriales, repo de carátulas de libretro-thumbnails)
CATALOGS = [
    ("nes-usa.json",           "Nintendo - Nintendo Entertainment System",       "Nintendo_-_Nintendo_Entertainment_System"),
    ("snes-usa.json",          "Nintendo - Super Nintendo Entertainment System", "Nintendo_-_Super_Nintendo_Entertainment_System"),
    ("master-system-usa.json", "Sega - Master System - Mark III",                "Sega_-_Master_System_-_Mark_III"),
    ("genesis-usa.json",       "Sega - Mega Drive - Genesis",                    "Sega_-_Mega_Drive_-_Genesis"),
    ("psx-usa.json",           "Sony - PlayStation",                             "Sony_-_PlayStation"),
    ("n64-usa.json",           "Nintendo - Nintendo 64",                         "Nintendo_-_Nintendo_64"),
]


def coverage(rows, key):
    n = len(rows)
    return (sum(1 for e in rows if str(e.get(key) or "").strip()) * 100 // n) if n else 0


def main():
    for fname, dat, repo in CATALOGS:
        print(f"== {fname} ==")
        normalize(os.path.join(CAT, fname), dat, repo)

    # El manifest se arma leyendo el **directorio**, no la lista de arriba: esa lista son solo los
    # catálogos legacy que este script rehornea. Armarlo desde ella dejaba fuera a los 31 que
    # construyen los builders regionales, y como el archivo se reescribe entero, correr esto los
    # borraba del manifest.
    manifest = []
    for fname in sorted(f for f in os.listdir(CAT) if f.endswith(".json") and f != "manifest.json"):
        rows = json.load(open(os.path.join(CAT, fname), encoding="utf-8"))
        manifest.append({
            "file": fname,
            "platform": rows[0]["platform"] if rows else "",
            "games": len(rows),
            "coverage": {k: coverage(rows, k) for k in ("year", "publisher", "serial", "coverUrl")},
        })
    with open(os.path.join(CAT, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"\nmanifest.json actualizado ({len(manifest)} catálogos)")


if __name__ == "__main__":
    main()
