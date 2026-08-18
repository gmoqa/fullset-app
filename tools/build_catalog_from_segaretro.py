#!/usr/bin/env python3
"""
Construye un catálogo canónico **nuevo** desde una fuente de Sega Retro (`segaretro_source.py`).
Para catálogos regionales (JP/EU/país) donde NO partimos de una lista de Wikipedia: la fuente ya trae
título, fecha (a día), serial (catalog number) y rating. `genre`/`coverUrl` quedan vacíos
(segunda pasada: Wikipedia para editora/género, libretro para carátulas de esa región).

Uso:  python3 tools/build_catalog_from_segaretro.py <fuente.json> <salida.json> --platform "Sega Genesis" --region NTSC-J
"""
import json, os, sys, argparse
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_common import slug, write_catalog


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("source")
    ap.add_argument("out")
    ap.add_argument("--platform", required=True)
    ap.add_argument("--region", required=True)
    a = ap.parse_args()

    src = json.load(open(a.source, encoding="utf-8"))
    entries, seen = [], {}
    for g in src["games"]:
        title = g["title"].strip()
        if not title:
            continue
        s = slug(title)
        # slug colisionado (dos títulos romanizados iguales): desambiguamos con el serial.
        if s in seen and g.get("serial"):
            s = f"{s}-{slug(g['serial'])}"
        seen[s] = title
        rd = g.get("releaseDate", "")
        entries.append({
            "title": title, "platform": a.platform, "region": a.region,
            "year": int(rd[:4]) if rd[:4].isdigit() else None,
            "releaseDate": rd, "developer": g.get("developer", ""),
            "publisher": g.get("publisher", ""), "genre": "",
            "slug": s, "serial": g.get("serial", ""), "coverUrl": "", "rating": g.get("rating", ""),
        })
    write_catalog(a.out, entries)


if __name__ == "__main__":
    main()
