#!/usr/bin/env python3
"""
Rellena `releaseDate` desde las listas "List of … games" de **Wikipedia** (CC BY-SA 4.0), que en
varias consolas traen una columna de fecha **por región** con precisión de día.

El orden de las columnas se lee de la propia cabecera de la tabla, no se asume: en la lista de
PlayStation es `Japan | Europe/PAL | North America`, y tomar la que no es asignaría a cada juego la
fecha de otro mercado.

Solo completa lo que esté **vacío**; alinea `year` con la fecha que pone.

Uso:  python3 tools/enrich_dates_wikipedia.py <catalogo.json> "<Página 1>" ["<Página 2>" …] [--dry-run]
      python3 tools/enrich_dates_wikipedia.py app/src/main/assets/catalogs/psx-usa.json \
          "List of PlayStation (console) games (A–L)" "List of PlayStation (console) games (M–Z)"
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

API = "https://en.wikipedia.org/w/api.php"
USER_AGENT = "fullset-catalog/0.1 (personal retro game catalog; +https://github.com/gmoqa/fullset-app)"

# Región de nuestro catálogo -> cómo nombra Wikipedia esa columna.
REGION_HEADERS = {
    "NTSC-U": ("north america", "na", "united states"),
    "NTSC-J": ("japan", "jp"),
    "PAL": ("europe/pal", "europe", "pal", "eu"),
}


def wikitext(page: str) -> str:
    url = f"{API}?" + urllib.parse.urlencode({
        "action": "parse", "page": page, "prop": "wikitext",
        "format": "json", "formatversion": "2",
    })
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=120) as r:
        data = json.load(r)
    if "error" in data:
        raise SystemExit(f"Wikipedia: {data['error'].get('info', 'página no encontrada')} — {page}")
    return data["parse"]["wikitext"]


def region_column(text: str, region: str) -> int | None:
    """
    Índice (0-based) de la columna de esa región entre las de "Regions released".

    Se saca de la sub-cabecera real de la tabla. Si cambiara el orden en Wikipedia, esto lo sigue;
    hardcodearlo sería asignarle a cada juego la fecha del mercado equivocado.
    """
    # La fila del `colspan` sigue con otras cabeceras (Ref., etc.) hasta un `|-`; las sub-cabeceras
    # de región vienen recién en la fila siguiente.
    span = re.search(r'colspan="3"\s*\|', text)
    if not span:
        return None
    rest = text[span.end():]
    sub = re.search(r"\n\|-\n(.*?)\n\|-", rest, re.S)
    if not sub:
        return None
    names = [n.strip().lower() for n in re.findall(r"^!.*?\|(.+)$", sub.group(1), re.M)]
    wanted = REGION_HEADERS.get(region, ())
    for i, name in enumerate(names):
        clean = re.sub(r"\{\{[^}]*\}\}|\s", "", name).replace("&nbsp;", "")
        if any(w.replace(" ", "") in clean for w in wanted):
            return i
    return None


def parse_page(text: str, column: int) -> dict[str, str]:
    """`{título normalizado: fecha ISO}` leyendo la columna de región indicada."""
    out: dict[str, str] = {}
    for block in text.split("\n|-")[1:]:
        title_cell = re.search(r"\n\|(?:id=\"[^\"]*\"\|)?\s*(.+)", block)
        if not title_cell:
            continue
        title = re.sub(r"\[\[(?:[^\]|]*\|)?([^\]]*)\]\]", r"\1", title_cell.group(1))
        title = re.sub(r"''|<[^>]+>", "", title).strip()
        if not title:
            continue
        # Las tres celdas de región, en orden: fecha, "sin lanzar" o "n/d".
        cells = re.findall(r"(\{\{unreleased\}\}|\{\{dts\|[^}]+\}\}|\{\{n/a[^}]*\}\})", block)
        if len(cells) <= column:
            continue
        m = re.match(r"\{\{dts\|(\d{4})(?:\|(\d{1,2}))?(?:\|(\d{1,2}))?", cells[column])
        if not m:
            continue
        year, month, day = m.group(1), m.group(2), m.group(3)
        iso = year
        if month:
            iso += f"-{int(month):02d}"
            if day:
                iso += f"-{int(day):02d}"
        out.setdefault(core(title), iso)
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("catalog")
    ap.add_argument("pages", nargs="+", help="títulos de las páginas de Wikipedia")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    catalog = json.load(open(args.catalog, encoding="utf-8"))
    region = catalog[0]["region"] if catalog else "NTSC-U"

    dates: dict[str, str] = {}
    for page in args.pages:
        text = wikitext(page)
        column = region_column(text, region)
        if column is None:
            raise SystemExit(f"no encontré la columna de {region} en '{page}' — revisá la cabecera")
        found = parse_page(text, column)
        print(f"   {page}: columna {column} · {len(found)} fechas", file=sys.stderr)
        dates.update(found)

    filled = day = 0
    for entry in catalog:
        if entry["releaseDate"].strip():
            continue
        iso = dates.get(core(entry["title"]))
        if not iso:
            continue
        entry["releaseDate"] = iso
        entry["year"] = int(iso[:4])
        filled += 1
        if len(iso) == 10:
            day += 1

    total = len(catalog) or 1
    have = sum(1 for e in catalog if e["releaseDate"].strip())
    print(f"{os.path.basename(args.catalog)}: {len(catalog)} juegos · +{filled} fechas ({day} al día)")
    print(f"   releaseDate {have}/{total} ({have * 100 // total}%) · región {region}")

    if args.dry_run:
        print("   (dry-run: no se escribió)")
        return
    rows = sorted((canonical(e) for e in catalog), key=lambda x: x["slug"])
    body = ",\n".join(json.dumps(x, ensure_ascii=False) for x in rows)
    open(args.catalog, "w", encoding="utf-8").write("[\n" + body + "\n]\n")


if __name__ == "__main__":
    main()
