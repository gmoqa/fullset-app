#!/usr/bin/env python3
"""
Aplica una fuente de Sega Retro (generada por `segaretro_source.py`) a un catálogo: rellena
`releaseDate` (precisión de mes/día), `serial` (catalog number) y `rating`, y alinea `year` con la
fecha. Match por título normalizado (`core`). Reproducible: re-correr cuando la fuente se actualice.

No toca título/slug/carátula/editora/género. Sega Retro es la fuente autoritativa de fechas, seriales
y ratings para las consolas Sega (ver docs/CATALOGS.md).

Uso:  python3 tools/enrich_from_segaretro.py <catalogo.json> <fuente.json>
"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_common import core, canonical


def main():
    if len(sys.argv) != 3:
        sys.exit("uso: enrich_from_segaretro.py <catalogo.json> <fuente.json>")
    cat_path, src_path = sys.argv[1], sys.argv[2]
    cat = json.load(open(cat_path, encoding="utf-8"))
    by = {core(g["title"]): g for g in json.load(open(src_path, encoding="utf-8"))["games"]}

    matched = date = month = serial = rating = 0
    unmatched = []
    for e in cat:
        s = by.get(core(e["title"]))
        if not s:
            unmatched.append(e["title"])
            continue
        matched += 1
        if s["releaseDate"]:
            e["releaseDate"] = s["releaseDate"]
            e["year"] = int(s["releaseDate"][:4])
            date += 1
            if len(s["releaseDate"]) == 7:
                month += 1
        if s["serial"]:
            e["serial"] = s["serial"]
            serial += 1
        if s["rating"]:
            e["rating"] = s["rating"]
            rating += 1

    rows = sorted((canonical(e) for e in cat), key=lambda x: x["slug"])
    body = ",\n".join(json.dumps(x, ensure_ascii=False) for x in rows)
    open(cat_path, "w", encoding="utf-8").write("[\n" + body + "\n]\n")

    print(f"{os.path.basename(cat_path)}: {len(cat)} juegos · match {matched}")
    print(f"  releaseDate {date} (a mes {month}) · serial {serial} · rating {rating}")
    print(f"  sin match ({len(unmatched)}): {unmatched}")


if __name__ == "__main__":
    main()
