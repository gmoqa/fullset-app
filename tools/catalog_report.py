#!/usr/bin/env python3
"""
Tablero de completitud de los catálogos (solo lectura, no toca nada).

Muestra, por plataforma legacy objetivo, si su catálogo existe y qué % de cada campo
"completable" (year/publisher/genre/serial) está lleno. Es la barra de progreso de
"documentación mejorable hasta completar": lo corrés cuando quieras para ver qué falta.

Uso:  python3 tools/catalog_report.py
"""
import json, os

CAT_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "catalogs")

# Las 11 consolas legacy objetivo: (nombre visible, archivo del catálogo, repo de Libretro).
# Legacy = consola cerrada con carátulas en Libretro; se completa hasta el 100%.
TARGETS = [
    ("NES",                "nes-usa.json",           "Nintendo_-_Nintendo_Entertainment_System"),
    ("Super Nintendo",     "snes-usa.json",          "Nintendo_-_Super_Nintendo_Entertainment_System"),
    ("Nintendo 64",        "n64-usa.json",           "Nintendo_-_Nintendo_64"),
    ("GameCube",           "gamecube-usa.json",      "Nintendo_-_GameCube"),
    ("Sega Master System", "master-system-usa.json", "Sega_-_Master_System_-_Mark_III"),
    ("Sega Genesis",       "genesis-usa.json",       "Sega_-_Mega_Drive_-_Genesis"),
    ("Sega Saturn",        "saturn-usa.json",        "Sega_-_Saturn"),
    ("Dreamcast",          "dreamcast-usa.json",     "Sega_-_Dreamcast"),
    ("PlayStation",        "psx-usa.json",           "Sony_-_PlayStation"),
    ("PlayStation 2",      "ps2-usa.json",           "Sony_-_PlayStation_2"),
    ("PlayStation 3",      "ps3-usa.json",           "Sony_-_PlayStation_3"),
]

# Campos que se van completando con el tiempo (los obligatorios de identidad no se miden).
COMPLETABLE = ["year", "publisher", "serial", "coverUrl"]


def pct(filled, total):
    return 100 * filled // total if total else 0


def main():
    print(f"{'Platform':<20} {'Games':>6}   " + "  ".join(f"{c:>9}" for c in COMPLETABLE))
    print("-" * 70)
    grand = 0
    for name, fname, _repo in TARGETS:
        path = os.path.join(CAT_DIR, fname)
        if not os.path.exists(path):
            print(f"{name:<20} {'—':>6}   " + "  ".join(f"{'MISSING':>9}" for _ in COMPLETABLE))
            continue
        data = json.load(open(path, encoding="utf-8"))
        n = len(data)
        grand += n
        cells = []
        for c in COMPLETABLE:
            filled = sum(1 for e in data if str(e.get(c, "")).strip() not in ("", "None"))
            cells.append(f"{pct(filled, n):>7}% ")
        print(f"{name:<20} {n:>6}   " + "  ".join(cells))
    print("-" * 70)
    have = sum(1 for _n, f, _r in TARGETS if os.path.exists(os.path.join(CAT_DIR, f)))
    print(f"{have}/{len(TARGETS)} catálogos presentes · {grand} juegos catalogados")


if __name__ == "__main__":
    main()
