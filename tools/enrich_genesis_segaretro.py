#!/usr/bin/env python3
"""
Enriquece `genesis-usa.json` con la fuente de Sega Retro (`tools/sources/genesis-segaretro.json`):
rellena `releaseDate` (precisión de mes), `serial` (catalog number) y `rating`, y alinea `year` con
la fecha. Match por título normalizado (core). Reproducible: re-correr tras actualizar la fuente.

No toca título/slug/carátula/editora/género. Sega Retro es la fuente autoritativa de fechas, seriales
y ratings para las consolas Sega (ver docs/CATALOGS.md).

Uso:  python3 tools/enrich_genesis_segaretro.py
"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_common import core, canonical

HERE = os.path.dirname(os.path.abspath(__file__))
CAT = os.path.join(HERE, "..", "app", "src", "main", "assets", "catalogs", "genesis-usa.json")
SRC = os.path.join(HERE, "sources", "genesis-segaretro.json")


def main():
    cat = json.load(open(CAT, encoding="utf-8"))
    src = json.load(open(SRC, encoding="utf-8"))
    by_core = {core(g["title"]): g for g in src["games"]}

    matched = date = month = serial = rating = 0
    unmatched = []
    for e in cat:
        s = by_core.get(core(e["title"]))
        if not s:
            unmatched.append(e["title"])
            continue
        matched += 1
        if s["releaseDate"]:
            e["releaseDate"] = s["releaseDate"]
            e["year"] = int(s["releaseDate"][:4])   # year consistente con la fecha precisa
            date += 1
            if len(s["releaseDate"]) == 7:
                month += 1
        if s["serial"]:          # Sega Retro = catalog number autoritativo
            e["serial"] = s["serial"]
            serial += 1
        if s["rating"]:
            e["rating"] = s["rating"]
            rating += 1

    rows = sorted((canonical(e) for e in cat), key=lambda x: x["slug"])
    body = ",\n".join(json.dumps(x, ensure_ascii=False) for x in rows)
    open(CAT, "w", encoding="utf-8").write("[\n" + body + "\n]\n")

    print(f"Genesis: {len(cat)} juegos · matchean con Sega Retro {matched}")
    print(f"  releaseDate {date} (de esas a mes {month}) · serial {serial} · rating {rating}")
    print(f"  sin match en Sega Retro ({len(unmatched)}): {unmatched}")


if __name__ == "__main__":
    main()
