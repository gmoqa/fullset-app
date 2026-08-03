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
from catalog_common import core, canonical, clean_cell  # noqa: E402

API = "https://en.wikipedia.org/w/api.php"
USER_AGENT = "fullset-catalog/0.1 (personal retro game catalog; +https://github.com/gmoqa/fullset-app)"

# Región de nuestro catálogo -> cómo nombra Wikipedia esa columna.
REGION_HEADERS = {
    "NTSC-U": ("north america", "na", "united states"),
    "NTSC-J": ("japan", "jp"),
    "PAL": ("europe/pal", "europe", "pal", "eu"),
}


def wikitext(page: str, _depth: int = 0) -> str:
    """
    Wikitext de una página, siguiendo redirecciones.

    Varias listas existen con guion y con raya larga en el título ("(A-C)" y "(A–C)"), y una de las
    dos es solo un `#REDIRECT` a la otra. Sin seguirlo se cachearía una página de 381 bytes creyendo
    que es la lista.
    """
    url = f"{API}?" + urllib.parse.urlencode({
        "action": "parse", "page": page, "prop": "wikitext",
        "format": "json", "formatversion": "2",
    })
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=120) as r:
        data = json.load(r)
    if "error" in data:
        raise SystemExit(f"Wikipedia: {data['error'].get('info', 'página no encontrada')} — {page}")
    text = data["parse"]["wikitext"]
    target = re.match(r"\s*#REDIRECT\s*\[\[:?([^\]|]+)", text, re.I)
    if target and _depth < 3:
        return wikitext(target.group(1).strip(), _depth + 1)
    return text


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
    # Los atributos de la celda son **opcionales**: PlayStation escribe `!style="width:12%;"|Japan`
    # pero N64 escribe `! Japan` a secas. Exigir la barra dejaba la lista de N64 sin ninguna columna
    # reconocida, y el enriquecedor abortaba sobre ella.
    names = [n.strip().lower() for n in re.findall(r"^!\s*(?:[^|\n]*\|)?(.+)$", sub.group(1), re.M)]
    wanted = REGION_HEADERS.get(region, ())
    for i, name in enumerate(names):
        # La lista de PS3 titula las columnas con `{{abbr|NA|North America}}`: hay que quedarse con
        # el primer argumento, no borrar la plantilla entera (dejaría la cabecera vacía).
        name = re.sub(r"\{\{abbr\|([^|}]+)[^}]*\}\}", r"\1", name, flags=re.I)
        # Las `<ref>` van fuera **antes** de comparar. Los códigos buscados son de dos letras, así
        # que se pegan a cualquier cosa: `<ref name=JGL/>` contiene "na", y con eso la columna de
        # Japón de la lista de N64 se hacía pasar por la de Norteamérica.
        clean = re.sub(r"<[^>]+>|\{\{[^}]*\}\}|\s", "", name).replace("&nbsp;", "")
        if any(w.replace(" ", "") in clean for w in wanted):
            return i
    return None


def region_base(text: str) -> int:
    """
    Índice de columna absoluto donde arranca el bloque de regiones: cuántas cabeceras hay antes.

    Hace falta porque varias listas meten una columna **`First released`** entre la editora y las
    tres de región. Contar "la enésima plantilla de fecha de la fila" se corría justo en esas: la
    de NES escribe ese campo como `{{dts|1985|09|13}}`, así que *Super Mario Bros.* americano se
    llevaba la fecha japonesa. Y no alcanza con mirar el nombre de la cabecera, porque GameCube
    tiene la misma columna pero escrita en texto plano (`2002-07-19<sup>JP</sup>`), que el regex de
    plantillas no ve — ahí los índices calzaban de casualidad.
    """
    span = re.search(r'colspan="?3"?\s*\|', text)
    if not span:
        return 0
    start = text.rfind("{|", 0, span.start())
    # Hasta el principio de la línea del `colspan`, no hasta el `colspan` mismo: esa línea también
    # abre con `!` y se contaba a sí misma, corriendo todo un lugar.
    end = text.rfind("\n", 0, span.start())
    return sum(1 for line in text[start:end].split("\n") if line.strip().startswith("!"))


def row_cells(block: str) -> list[str]:
    """Celdas de una fila: cada línea que abre con `|` es una celda y `||` separa varias en la misma."""
    out: list[str] = []
    for line in block.split("\n"):
        line = line.strip()
        if not line.startswith("|") or line.startswith("|-"):
            continue
        out.extend(line[1:].split("||"))
    return out


def parse_page(text: str, column: int, region: str | None = None) -> dict[str, str]:
    """`{título normalizado: fecha ISO}` leyendo la columna de región indicada."""
    out: dict[str, str] = {}
    base = region_base(text)
    for block in text.split("\n|-")[1:]:
        # Se indexa por **celda real** de la fila, no por "la enésima plantilla de fecha": ver
        # `region_base`. La celda puede traer `{{dts|…}}`, `{{unreleased}}` o `{{n/a}}`.
        cells = row_cells(block)
        if len(cells) <= base + column:
            continue
        # El título sale del **mismo** `clean_cell` que usa el builder. Cuando cada uno tenía su
        # versión, el enriquecedor no reconocía los títulos con plantilla anidada o con atributo de
        # ordenamiento, y esas filas se quedaban con la fecha vieja aunque la lista tuviera una
        # mejor: 21 juegos de SNES sobrevivían a un `--overwrite` contra su propia fuente.
        title = clean_cell(cells[0], region)
        if not title:
            continue
        m = re.search(r"\{\{dts\|(\d{4})(?:\|(\d{1,2}))?(?:\|(\d{1,2}))?",
                      cells[base + column], re.I)
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
    ap.add_argument("--overwrite", action="store_true",
                    help="pisar también las fechas ya cargadas cuando la lista dice otra cosa. Para "
                         "los catálogos legacy (NES, SNES, N64), cuya fecha no tiene procedencia "
                         "documentada y a veces es la de otra región.")
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
        found = parse_page(text, column, region)
        print(f"   {page}: columna {column} · {len(found)} fechas", file=sys.stderr)
        dates.update(found)

    filled = day = changed = 0
    for entry in catalog:
        old = entry["releaseDate"].strip()
        if old and not args.overwrite:
            continue
        iso = dates.get(core(entry["title"]))
        if not iso:
            continue
        # Una es prefijo de la otra = el mismo dato con distinta precisión; no es un desacuerdo.
        if old and (iso.startswith(old) or old.startswith(iso)):
            if len(iso) <= len(old):
                continue
        entry["releaseDate"] = iso
        entry["year"] = int(iso[:4])
        if old:
            changed += 1
        else:
            filled += 1
        if len(iso) == 10:
            day += 1

    total = len(catalog) or 1
    have = sum(1 for e in catalog if e["releaseDate"].strip())
    print(f"{os.path.basename(args.catalog)}: {len(catalog)} juegos · +{filled} fechas"
          + (f" · {changed} corregidas" if changed else "") + f" ({day} al día)")
    print(f"   releaseDate {have}/{total} ({have * 100 // total}%) · región {region}")

    if args.dry_run:
        print("   (dry-run: no se escribió)")
        return
    rows = sorted((canonical(e) for e in catalog), key=lambda x: x["slug"])
    body = ",\n".join(json.dumps(x, ensure_ascii=False) for x in rows)
    open(args.catalog, "w", encoding="utf-8").write("[\n" + body + "\n]\n")


if __name__ == "__main__":
    main()
