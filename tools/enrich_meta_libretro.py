#!/usr/bin/env python3
"""
Rellena `publisher`, `genre` y `releaseDate` desde **libretro-database** (CC BY-SA 4.0), que
publica esos metadatos por plataforma en `metadat/`. La fecha viene partida en `releaseyear/` y
`releasemonth/`; se compone al mismo ISO de precisión variable del resto ("1995" o "1995-08").

Consciente de la región: las entradas del DAT vienen etiquetadas con su mercado —"Sonic (USA)",
"Sonic (Japan)"— así que se prefiere la que corresponde al catálogo (un juego puede tener otra
editora según dónde se publicó: en Brasil casi todo lo distribuyó Tec Toy). Si no hay entrada de esa
región se usa la de otra, porque la editora suele coincidir y es mejor que dejar el campo vacío.

Solo completa lo que esté **vacío**: nunca pisa lo que ya tenga el catálogo. Reproducible: volver a
correrlo cuando libretro se actualice.

Uso:  python3 tools/enrich_meta_libretro.py <catalogo.json> <nombre-del-DAT> [--dry-run]
      python3 tools/enrich_meta_libretro.py data/catalogs/genesis-usa.json \
          "Sega - Mega Drive - Genesis"
"""
import argparse
import json
import os
import re
import sys
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_common import core, canonical  # noqa: E402

BASE = "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat"
USER_AGENT = "fullset-catalog/0.1 (+https://github.com/gmoqa/fullset-app)"

# Región del catálogo -> etiquetas que usa el DAT, en orden de preferencia.
REGION_TAGS = {
    "NTSC-U": ["USA", "World", "Europe", "Japan"],
    "NTSC-J": ["Japan", "World", "USA", "Europe"],
    "PAL": ["Europe", "Brazil", "Australia", "World", "USA", "Japan"],
}


def fetch(kind: str, dat: str) -> str:
    url = f"{BASE}/{kind}/{urllib.parse.quote(dat)}.dat"
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read().decode("utf-8", "replace")


def parse(text: str, field: str) -> dict[str, list[tuple[str, str]]]:
    """`{título normalizado: [(región, valor), …]}` — un juego puede estar en varias regiones."""
    out: dict[str, list[tuple[str, str]]] = {}
    for block in re.findall(r"game\s*\((.*?)\n\)", text, re.S):
        name = re.search(r'comment\s+"([^"]+)"', block)
        value = re.search(rf'{field}\s+"([^"]+)"', block)
        if not (name and value):
            continue
        full = name.group(1)
        # Descartar volcados que no son el lanzamiento comercial.
        if any(bad in full for bad in ("(Beta", "(Proto", "(Demo", "(Sample", "(Pirate", "(Unl")):
            continue
        title = re.sub(r"\s*\(.*$", "", full)
        region = (re.search(r"\(([^)]+)\)", full) or [None, ""])[1].split(",")[0].strip()
        out.setdefault(core(title), []).append((region, value.group(1).strip()))
    return out


def pick(entries: list[tuple[str, str]], region: str) -> str:
    """El valor de la región del catálogo; si no está, el de la más cercana."""
    for tag in REGION_TAGS.get(region, ["USA"]):
        for entry_region, value in entries:
            if entry_region == tag:
                return value
    return entries[0][1] if entries else ""


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("catalog")
    ap.add_argument("dat", help='nombre del DAT, p. ej. "Sega - Mega Drive - Genesis"')
    ap.add_argument("--dry-run", action="store_true", help="solo reportar")
    args = ap.parse_args()

    catalog = json.load(open(args.catalog, encoding="utf-8"))
    region = catalog[0]["region"] if catalog else "NTSC-U"
    publishers = parse(fetch("publisher", args.dat), "publisher")
    genres = parse(fetch("genre", args.dat), "genre")
    years = parse(fetch("releaseyear", args.dat), "releaseyear")
    months = parse(fetch("releasemonth", args.dat), "releasemonth")

    filled_pub = filled_genre = filled_date = 0
    for entry in catalog:
        key = core(entry["title"])
        if not entry["publisher"].strip() and key in publishers:
            value = pick(publishers[key], region)
            if value:
                entry["publisher"] = value
                filled_pub += 1
        if not entry["genre"].strip() and key in genres:
            value = pick(genres[key], region)
            if value:
                entry["genre"] = value
                filled_genre += 1
        # Fecha de lanzamiento: libretro la guarda partida en año y mes. Se compone al mismo ISO de
        # precisión variable del resto ("1995" o "1995-08"); sin día, que este DAT no trae.
        if not entry["releaseDate"].strip() and key in years:
            year = pick(years[key], region).strip()
            if year.isdigit():
                month = pick(months.get(key, []), region).strip()
                iso = year
                if month.isdigit() and 1 <= int(month) <= 12:
                    iso = f"{year}-{int(month):02d}"
                entry["releaseDate"] = iso
                entry["year"] = int(year)
                filled_date += 1

    total = len(catalog) or 1
    have_pub = sum(1 for e in catalog if e["publisher"].strip())
    have_genre = sum(1 for e in catalog if e["genre"].strip())
    name = os.path.basename(args.catalog)
    have_date = sum(1 for e in catalog if e["releaseDate"].strip())
    print(f"{name}: {len(catalog)} juegos · +{filled_pub} editoras, +{filled_genre} géneros, "
          f"+{filled_date} fechas")
    print(f"   editora {have_pub}/{total} ({have_pub * 100 // total}%) · "
          f"género {have_genre}/{total} ({have_genre * 100 // total}%) · "
          f"fecha {have_date}/{total} ({have_date * 100 // total}%)")

    if args.dry_run:
        print("   (dry-run: no se escribió)")
        return
    rows = sorted((canonical(e) for e in catalog), key=lambda x: x["slug"])
    body = ",\n".join(json.dumps(x, ensure_ascii=False) for x in rows)
    open(args.catalog, "w", encoding="utf-8").write("[\n" + body + "\n]\n")


if __name__ == "__main__":
    main()
