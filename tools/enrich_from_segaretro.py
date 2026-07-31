#!/usr/bin/env python3
"""
Aplica una fuente de Sega Retro a un catálogo: rellena `releaseDate` (precisión de mes/día),
`serial` (catalog number) y `rating`, y alinea `year` con la fecha. Reproducible: re-correr cuando
la fuente se actualice.

Match por título normalizado (`core`), aceptando también los **títulos alternativos** que traiga la
fuente (`altTitles`): un mismo juego se llama distinto según la región — "Air Buster" es la página
"Air Buster: Trouble Specialty Raid Unit" y "Warsong" es "Langrisser" — y el catálogo usa el nombre
con el que se vendió acá.

No toca título/slug/carátula/editora/género. Sega Retro es la fuente autoritativa de fechas, seriales
y ratings para las consolas Sega (ver docs/CATALOGS.md).

Uso:  python3 tools/enrich_from_segaretro.py <catalogo.json> <fuente.json>
"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_common import core, canonical


def subtitle_match(key, keys, by):
    """
    Último recurso: el mismo juego con distinto subtítulo ("Teddy Boy" = "Teddy Boy Blues",
    "Railroad Tycoon II: Gold Edition" = "Railroad Tycoon II"). Solo si uno es prefijo del otro y el
    nombre es largo (>= 8 normalizado), para no unir "Sonic" con cualquier cosa; ante varios
    candidatos gana el más corto, que es el que menos agrega. Se reporta aparte para poder auditarlo.
    """
    if len(key) < 8:
        return None
    cands = [k for k in keys if k.startswith(key) or key.startswith(k)]
    return by[min(cands, key=len)] if cands else None


def main():
    if len(sys.argv) != 3:
        sys.exit("uso: enrich_from_segaretro.py <catalogo.json> <fuente.json>")
    cat_path, src_path = sys.argv[1], sys.argv[2]
    cat = json.load(open(cat_path, encoding="utf-8"))

    # Índice por título y por cada alternativo. El principal pisa a un alternativo homónimo: si dos
    # juegos distintos comparten un nombre, gana el que lo lleva como título propio.
    by = {}
    for g in json.load(open(src_path, encoding="utf-8"))["games"]:
        for alt in g.get("altTitles", []):
            by.setdefault(core(alt), g)
    for g in json.load(open(src_path, encoding="utf-8"))["games"]:
        by[core(g["title"])] = g

    keys = list(by)
    matched = date = month = serial = rating = publisher = 0
    unmatched, by_subtitle = [], []
    for e in cat:
        k = core(e["title"])
        s = by.get(k)
        if not s:
            s = subtitle_match(k, keys, by)
            if s:
                by_subtitle.append(f"{e['title']} = {s['title']}")
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
        # La editora **solo si falta**: a diferencia de fecha/serial/rating, donde Sega Retro es la
        # autoridad, acá puede haber un dato mejor de otra fuente o cargado a mano.
        if s.get("publisher") and not e["publisher"].strip():
            e["publisher"] = s["publisher"]
            publisher += 1

    rows = sorted((canonical(e) for e in cat), key=lambda x: x["slug"])
    body = ",\n".join(json.dumps(x, ensure_ascii=False) for x in rows)
    open(cat_path, "w", encoding="utf-8").write("[\n" + body + "\n]\n")

    print(f"{os.path.basename(cat_path)}: {len(cat)} juegos · match {matched}")
    print(f"  releaseDate {date} (a mes {month}) · serial {serial} · rating {rating}"
          + (f" · editora +{publisher}" if publisher else ""))
    if by_subtitle:
        print(f"  por subtítulo ({len(by_subtitle)}): {by_subtitle}")
    print(f"  sin match ({len(unmatched)}): {unmatched}")


if __name__ == "__main__":
    main()
