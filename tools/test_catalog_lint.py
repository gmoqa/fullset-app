#!/usr/bin/env python3
"""
Prueba que los invariantes semánticos atrapen los defectos que ya nos pasaron.

Cada caso reproduce un bug **real** de este dataset. La lista de arriba de `catalog_lint.py`
—11 claves, orden, tipos, unicidad— daba OK con todos ellos adentro: son estructuralmente
impecables. Si alguna de estas pruebas empieza a pasar en verde sin detectar nada, quiere decir
que el invariante se aflojó y el bug puede volver sin que nadie se entere.

Uso:  python3 tools/test_catalog_lint.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_lint import lint_semantica  # noqa: E402


def entrada(**campos):
    base = {"title": "Juego", "platform": "PlayStation", "region": "NTSC-U", "year": 1997,
            "releaseDate": "1997-09-07", "publisher": "Sony", "genre": "", "slug": "juego",
            "serial": "", "coverUrl": "", "rating": ""}
    base.update(campos)
    return base


CASOS = [
    # (nombre, entrada, año de lanzamiento de la consola, ¿debe dar error?)
    ("serial japonés en catálogo americano (el SHVC-…-JPN que había en snes-usa)",
     entrada(serial="SHVC-AHZJ-JPN"), 1995, True),
    ("serial europeo en catálogo americano (los BLES de ps3-usa)",
     entrada(serial="BLES-01046"), 1995, True),
    ("serial americano en su catálogo americano",
     entrada(serial="SLUS-00006"), 1995, False),
    ("serial de Sega, que no diagnostica región",
     entrada(platform="Sega Genesis", serial="T-81033"), 1989, False),
    ("serial asiático de PS3, que aparece en las tres regiones",
     entrada(serial="BLAS-50219"), 1995, False),

    ("juego anterior al lanzamiento de su consola (The Three Stooges, 1987 en psx)",
     entrada(year=1987, releaseDate="1987"), 1995, True),
    ("juego posterior a discontinuar la consola: legítimo, Tec Toy en Brasil",
     entrada(platform="Sega Master System", year=2007, releaseDate="2007"), 1986, False),

    ("year desincronizado de releaseDate",
     entrada(year=1998, releaseDate="1997-09-07"), 1995, True),

    ("carátula europea en catálogo americano (el bug del 32X)",
     entrada(coverUrl="https://raw.githubusercontent.com/x/y/Doom%20%28Europe%29.png"), 1995, "cover"),
    ("carátula de cartucho compartido Japón-EE.UU., correcta para NTSC-U",
     entrada(coverUrl="https://raw.githubusercontent.com/x/y/Doom%20%28Japan%2C%20USA%29%20%28En%29.png"), 1995, False),
    ("carátula World, válida en cualquier región",
     entrada(coverUrl="https://raw.githubusercontent.com/x/y/Doom%20%28World%29.png"), 1995, False),
    ("carátula de SteamGridDB, que no lleva región en el nombre",
     entrada(coverUrl="https://cdn2.steamgriddb.com/grid/abc123.png"), 1995, False),
]


def main() -> int:
    fallos = 0
    for nombre, e, lanzamiento, espera in CASOS:
        errs, fuera = lint_semantica([e], lanzamiento)
        if espera == "cover":
            ok = fuera == 1 and not errs
            detalle = f"fuera de región={fuera}"
        elif espera:
            ok = bool(errs)
            detalle = errs[0][:70] if errs else "no detectó nada"
        else:
            ok = not errs and fuera == 0
            detalle = "limpio" if ok else (errs[0][:70] if errs else f"fuera de región={fuera}")
        fallos += not ok
        print(f"  {'OK ' if ok else 'FALLA'} {nombre}\n        {detalle}")
    print(f"\n  {len(CASOS) - fallos}/{len(CASOS)} casos")
    return 1 if fallos else 0


if __name__ == "__main__":
    sys.exit(main())
