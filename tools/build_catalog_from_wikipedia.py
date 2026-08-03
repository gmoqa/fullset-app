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
from enrich_dates_wikipedia import wikitext, region_column, region_base, row_cells  # noqa: E402


def clean_cell(text: str) -> str:
    """
    Quita el wikitext de una celda y deja el texto visible.

    Dos cosas que no son obvias y rompen si faltan:
      - Las celdas pueden llevar atributos HTML antes del contenido, separados por `|`
        (`data-sort-value="Legend of Zelda…"|The Legend of Zelda…`, `id="0–9"|…`). Sin quitarlos, el
        atributo termina dentro del título.
      - Una fila puede separar celdas con `||` en la misma línea, así que se corta ahí: si no, la
        desarrolladora se pega al nombre del juego.
    """
    s = re.sub(r"<ref[^>]*>.*?</ref>|<ref[^>]*/>", "", text, flags=re.S)
    s = s.split("||")[0]
    s = re.sub(r'^\s*(?:[\w-]+="[^"]*"\s*)+\|', "", s)
    # Las plantillas se resuelven ANTES que los enlaces, porque pueden ir adentro:
    # `[[{{Not a typo|Med|iEvil}}]]`. Si se procesara el enlace primero, su regex cortaría por el
    # primer `|` de la plantilla y el título quedaría partido. Las de texto (Not a typo, sic…)
    # concatenan sus argumentos, así que se unen; las de marcado (unreleased, dts…) desaparecen.
    s = re.sub(r"\{\{(?:not a typo|sic|nowrap|small)\|([^}]*)\}\}",
               lambda m: m.group(1).replace("|", ""), s, flags=re.I)
    s = re.sub(r"\{\{[^}]*\}\}", "", s)
    s = re.sub(r"\[\[(?:[^\]|]*\|)?([^\]]*)\]\]", r"\1", s)
    s = re.sub(r"''+|<[^>]+>", "", s)
    # Algunas filas listan el título alternativo de otra región tras un "•"
    # ("The Adventures of Lomax•Lomax^PAL"): nos quedamos con el principal.
    s = s.split("•")[0]
    return re.sub(r"\s+", " ", s).strip(" |")


def publisher_column(text: str) -> int | None:
    """
    Índice de la columna "Publisher" entre las celdas de una fila, o None si la tabla no la trae.

    No todas las listas la tienen: PlayStation y GameCube ponen `Título | Desarrolladora | Editora`,
    pero PS3 va directo de la desarrolladora a las fechas. Asumir un índice fijo hacía que la editora
    de PS3 saliera del contenido de una celda de región.
    """
    # Se ancla a la tabla de JUEGOS, que es la que tiene las columnas de región: varias páginas
    # abren con otra tabla antes (GameCube empieza con una leyenda de códigos de región), así que
    # tomar la primera `{|` de la página leería las cabeceras equivocadas.
    span = re.search(r'colspan="3"', text)
    if not span:
        return None
    start = text.rfind("{|", 0, span.start())
    if start < 0:
        return None
    # El primer bloque de líneas `!` es la cabecera principal; el segundo son las sub-cabeceras de
    # región, que no nos interesan.
    names = []
    for line in text[start:].split("\n")[1:]:
        line = line.strip()
        if line.startswith("!"):
            names.append(line.lstrip("!").split("|")[-1])
        elif line.startswith("|-") and names:
            break
    for i, cell in enumerate(names):
        if "publisher" in cell.lower():
            return i
    return None


def parse(text: str, column: int, publisher_at: int | None) -> list[dict]:
    """Filas con fecha en la columna pedida: título, editora y fecha ISO de precisión variable."""
    out = []
    # Se indexa por **celda real**, no por "la enésima plantilla de fecha de la fila": varias listas
    # meten una columna `First released` antes de las de región (ver `region_base`).
    base = region_base(text)
    for block in text.split("\n|-")[1:]:
        cells = row_cells(block)
        if len(cells) <= base + column:
            continue
        m = re.search(r"\{\{dts\|(\d{4})(?:\|(\d{1,2}))?(?:\|(\d{1,2}))?",
                      cells[base + column], re.I)
        if not m:
            continue  # `unreleased` en esta región: el juego no salió acá
        year, month, day = m.group(1), m.group(2), m.group(3)
        iso = year + (f"-{int(month):02d}" if month else "") + (f"-{int(day):02d}" if month and day else "")

        # Las celdas de la fila, en orden: título | desarrolladora | editora | …
        fields = [c for c in re.split(r"\n\|(?!\|)", block) if c.strip()]
        title = clean_cell(fields[0]) if fields else ""
        if not title:
            continue
        publisher = ""
        if publisher_at is not None and len(fields) > publisher_at:
            publisher = clean_cell(fields[publisher_at])
        # Varias editoras separadas por coma: nos quedamos con la primera, como el resto del dataset.
        publisher = publisher.split(",")[0].strip()
        out.append({"title": title, "publisher": publisher, "releaseDate": iso, "year": int(year)})
    return out


# Etiqueta de región tal como la escribe la columna "First released" de la lista de PS2.
FIRST_RELEASED_TAG = {"NTSC-U": "NA", "NTSC-J": "JP", "PAL": "EU"}


def parse_checkmarks(text: str, region: str) -> list[dict]:
    """
    Layout de la lista de **PS2**: `Título | Desarrolladora | Editora | First released | JP | EU | NA`,
    donde las tres últimas son solo una tilde de "salió acá" y la fecha es **una sola**, la del
    primer lanzamiento, etiquetada con su mercado (`2005-11-23{{sup|JP}}`).

    Dos cosas de esta tabla que hay que respetar:

    - Las filas **omiten las celdas vacías del final**: un juego solo europeo escribe `|||{{Ya}}`, que
      son seis celdas, no siete. Por eso no se exige un largo fijo — que descartaría justamente a los
      exclusivos de EU y JP — sino que exista la celda de *esta* región; si no llega hasta ahí, es
      que no salió acá.
    - La fecha se copia **solo si su etiqueta coincide con la región del catálogo**. Un juego que
      debutó en Japón en 2003 y llegó a Europa en 2005 tiene una sola fecha, la japonesa: ponérsela
      al europeo le inventaría dos años de antigüedad. Se prefiere entrar sin fecha y que la complete
      después otra fuente.
    """
    tag = FIRST_RELEASED_TAG.get(region)
    col = {"JP": 4, "EU": 5, "NA": 6}.get(tag)
    if col is None:
        return []
    out = []
    for block in text.split("\n|-")[1:]:
        cells = row_cells(block)
        # `> col` y no `>= 7`: ver arriba. El mínimo de 4 descarta la fila de cabecera y las notas.
        if len(cells) < 4 or len(cells) <= col or "{{ya}}" not in cells[col].lower():
            continue
        title = clean_cell(cells[0])
        if not title:
            continue
        m = re.match(r"\s*(\d{4})-(\d{2})-(\d{2})\s*\{\{sup\|([A-Z]+)", cells[3])
        ours = bool(m) and m.group(4) == tag
        iso = f"{m.group(1)}-{m.group(2)}-{m.group(3)}" if ours else ""
        # La editora va en un índice fijo porque este layout lo define esta misma función; el
        # `publisher_column()` genérico se ancla a un `colspan="3"` que esta tabla no tiene.
        publisher = clean_cell(cells[2])
        out.append({"title": title, "publisher": publisher.split(",")[0].strip(),
                    "releaseDate": iso, "year": int(m.group(1)) if ours else None})
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("pages", nargs="+")
    ap.add_argument("--platform", required=True)
    ap.add_argument("--region", required=True)
    ap.add_argument("--cache", help="carpeta donde cachear el wikitext (evita volver a pedirlo)")
    ap.add_argument("--layout", choices=("regions-dated", "checkmarks"), default="regions-dated",
                    help="'regions-dated': una fecha por región (PlayStation, GameCube, PS3). "
                         "'checkmarks': una fecha de primer lanzamiento + tildes por región (PS2).")
    args = ap.parse_args()

    rows = []
    for page in args.pages:
        # Caché por página: se construyen varios catálogos regionales de la MISMA tabla, así que sin
        # esto se le pediría a Wikipedia lo mismo una vez por región.
        cached = os.path.join(args.cache, re.sub(r"[^\w]+", "_", page) + ".wikitext") if args.cache else None
        if cached and os.path.exists(cached):
            text = open(cached, encoding="utf-8").read()
        else:
            text = wikitext(page)
            if cached:
                open(cached, "w", encoding="utf-8").write(text)
        column = None
        if args.layout == "checkmarks":
            found = parse_checkmarks(text, args.region)
        else:
            column = region_column(text, args.region)
            if column is None:
                raise SystemExit(f"no encontré la columna de {args.region} en '{page}'")
            found = parse(text, column, publisher_column(text))
        where = f"columna {column} · " if column is not None else ""
        print(f"   {page}: {where}{len(found)} juegos", file=sys.stderr)
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
    dated = sum(1 for e in entries if e["releaseDate"])
    pub = sum(1 for e in entries if e["publisher"])
    print(f"{os.path.basename(args.out)}: {len(entries)} juegos · "
          f"fecha {dated * 100 // n}% ({day} al día) · editora {pub * 100 // n}%")


if __name__ == "__main__":
    main()
