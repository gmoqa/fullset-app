#!/usr/bin/env python3
"""
Construye un catálogo **regional** desde las listas "List of … games" de Wikipedia (CC BY-SA 4.0).

Un juego entra si esa región tiene **fecha** en su columna: la tabla marca `{{unreleased}}` donde no
salió, así que la fecha es a la vez el dato y la prueba de que se publicó ahí. La columna se
identifica leyendo la cabecera real (ver `enrich_dates_wikipedia.region_column`), no por posición.

Deja `serial` y `coverUrl` vacíos: se completan después con
`enrich_serials_redump.py` y `enrich_covers_libretro.py`.

Uso:  python3 tools/build_catalog_from_wikipedia.py <salida.json> --platform "PlayStation" \
          --region NTSC-J "List of PlayStation (console) games (A–L)" "…(M–Z)"
"""
import argparse
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_common import slug, write_catalog  # noqa: E402
from enrich_dates_wikipedia import wikitext, region_column  # noqa: E402


def clean_cell(text: str) -> str:
    """Quita wikitext de una celda: enlaces, cursivas, referencias, plantillas sueltas."""
    s = re.sub(r"<ref[^>]*>.*?</ref>|<ref[^>]*/>", "", text, flags=re.S)
    s = re.sub(r"\[\[(?:[^\]|]*\|)?([^\]]*)\]\]", r"\1", s)
    s = re.sub(r"\{\{[^}]*\}\}", "", s)
    s = re.sub(r"''+|<[^>]+>", "", s)
    s = re.sub(r"^id=\"[^\"]*\"\|", "", s)
    return re.sub(r"\s+", " ", s).strip(" |")


def parse(text: str, column: int) -> list[dict]:
    """Filas con fecha en la columna pedida: título, editora y fecha ISO de precisión variable."""
    out = []
    for block in text.split("\n|-")[1:]:
        cells = re.findall(r"(\{\{unreleased\}\}|\{\{dts\|[^}]+\}\}|\{\{n/a[^}]*\}\})", block)
        if len(cells) <= column:
            continue
        m = re.match(r"\{\{dts\|(\d{4})(?:\|(\d{1,2}))?(?:\|(\d{1,2}))?", cells[column])
        if not m:
            continue  # `unreleased` en esta región: el juego no salió acá
        year, month, day = m.group(1), m.group(2), m.group(3)
        iso = year + (f"-{int(month):02d}" if month else "") + (f"-{int(day):02d}" if month and day else "")

        # Las celdas de la fila, en orden: título | desarrolladora | editora | …
        fields = [c for c in re.split(r"\n\|(?!\|)", block) if c.strip()]
        title = clean_cell(fields[0]) if fields else ""
        if not title:
            continue
        publisher = clean_cell(fields[2]) if len(fields) > 2 else ""
        # Varias editoras separadas por coma: nos quedamos con la primera, como el resto del dataset.
        publisher = publisher.split(",")[0].strip()
        out.append({"title": title, "publisher": publisher, "releaseDate": iso, "year": int(year)})
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("pages", nargs="+")
    ap.add_argument("--platform", required=True)
    ap.add_argument("--region", required=True)
    ap.add_argument("--cache", help="carpeta con el wikitext ya descargado (evita volver a pedirlo)")
    args = ap.parse_args()

    rows = []
    for i, page in enumerate(args.pages):
        cached = os.path.join(args.cache, f"psx_wiki_{i}.txt") if args.cache else None
        text = open(cached, encoding="utf-8").read() if cached and os.path.exists(cached) else wikitext(page)
        column = region_column(text, args.region)
        if column is None:
            raise SystemExit(f"no encontré la columna de {args.region} en '{page}'")
        found = parse(text, column)
        print(f"   {page}: columna {column} · {len(found)} juegos", file=sys.stderr)
        rows.extend(found)

    entries, seen = [], set()
    for r in rows:
        s = slug(r["title"])
        if not s or s in seen:
            continue
        seen.add(s)
        entries.append({
            "title": r["title"], "platform": args.platform, "region": args.region,
            "year": r["year"], "releaseDate": r["releaseDate"], "publisher": r["publisher"],
            "genre": "", "slug": s, "serial": "", "coverUrl": "", "rating": "",
        })
    write_catalog(args.out, entries)

    n = len(entries) or 1
    day = sum(1 for e in entries if len(e["releaseDate"]) == 10)
    pub = sum(1 for e in entries if e["publisher"])
    print(f"{os.path.basename(args.out)}: {len(entries)} juegos · "
          f"fecha 100% ({day} al día) · editora {pub * 100 // n}%")


if __name__ == "__main__":
    main()
