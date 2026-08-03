#!/usr/bin/env python3
"""
Rellena `releaseDate` leyendo la **ficha del artículo de cada juego** en Wikipedia (CC BY-SA 4.0),
que trae las fechas por región en `{{Video game release|NA|…|JP|…|EU|…}}`.

Para qué, si ya existe `enrich_dates_wikipedia.py`: aquel lee la **tabla de la lista**, que en la
mayoría de las consolas tiene una columna de fecha por región. La de **PlayStation 2** no: trae una
sola fecha, la del primer lanzamiento, así que el catálogo europeo quedaba con 28% de fechas —casi
nada debutó en Europa— y con el orden por lanzamiento (el de por defecto en la app) tres de cada
cuatro juegos caían al fondo de la estantería. La ficha de cada artículo sí las tiene todas.

Dos cosas que hay que hacer bien:

- **A qué artículo ir.** Se saca del enlace de la propia tabla (`[[Ico|Ico]]`), no adivinando la URL
  desde el título: hay desambiguaciones (`Killzone (video game)`) que no se pueden derivar.
- **Qué fecha es de esta consola.** Los multiplataforma agrupan las fechas por sistema
  (`'''PlayStation 2'''{{vgrelease|…}}'''Wii'''{{vgrelease|…}}`). Sin acotar al bloque correcto,
  Ōkami de PS2 se llevaba la fecha de la versión de Wii, cinco años posterior.

Solo completa lo que esté **vacío**.

Uso:  python3 tools/enrich_dates_wikipedia_infobox.py <catalogo.json> --platform "PlayStation 2" \
          "List of PlayStation 2 games (A–K)" "List of PlayStation 2 games (L–Z)" [--dry-run]
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
from enrich_dates_wikipedia import wikitext  # noqa: E402

API = "https://en.wikipedia.org/w/api.php"
UA = {"User-Agent": "fullset-catalog/0.1 (+https://github.com/gmoqa/fullset-app)"}
BATCH = 50  # tope de la API de MediaWiki por consulta

# Región del catálogo -> códigos de la plantilla, **en orden de preferencia**. `EU` antes que `UK` o
# `AU` porque son el lanzamiento continental y el local; quedarse con el británico daría la fecha de
# un solo país. `WW` (mundial) va último: solo si no hay nada regional.
REGION_CODES = {
    "NTSC-U": ["NA", "US", "USA", "AM", "NAM"],
    "NTSC-J": ["JP", "JPN", "JA"],
    "PAL": ["EU", "PAL", "EUR", "UK", "GB", "AU", "AUS", "NZ"],
}
MONTHS = {m: i for i, m in enumerate(
    ["january", "february", "march", "april", "may", "june",
     "july", "august", "september", "october", "november", "december"], 1)}


def article_links(pages: list[str], cache: str | None) -> dict[str, str]:
    """`{título normalizado: artículo}` a partir de los enlaces de la primera celda de cada fila."""
    out: dict[str, str] = {}
    for page in pages:
        path = os.path.join(cache, re.sub(r"[^\w]+", "_", page) + ".wikitext") if cache else None
        if path and os.path.exists(path):
            text = open(path, encoding="utf-8").read()
        else:
            text = wikitext(page)
            if path:
                open(path, "w", encoding="utf-8").write(text)
        for block in text.split("\n|-")[1:]:
            first = next((ln for ln in block.split("\n")
                          if ln.strip().startswith("|") and not ln.strip().startswith("|-")), None)
            if not first:
                continue
            link = re.search(r"\[\[([^\]|#]+)(?:\|([^\]]*))?\]\]", first)
            if not link:
                continue
            target = link.group(1).strip()
            shown = (link.group(2) or target).strip()
            # El texto visible es el título tal como lo guarda el catálogo; el destino, el artículo.
            out.setdefault(core(re.sub(r"''+", "", shown)), target)
    return out


def fetch(titles: list[str]) -> dict[str, str]:
    """`{artículo: wikitext}` de hasta [BATCH] artículos por consulta, siguiendo redirecciones."""
    url = API + "?" + urllib.parse.urlencode({
        "action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main",
        "format": "json", "formatversion": "2", "redirects": "1", "titles": "|".join(titles)})
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=180) as r:
        data = json.load(r)
    query = data.get("query", {})
    # `redirects`/`normalized` dicen con qué nombre pedimos cada página; hay que poder volver.
    alias: dict[str, str] = {}
    for key in ("redirects", "normalized"):
        for item in query.get(key, []):
            alias[item["to"]] = item["from"]
    out = {}
    for page in query.get("pages", []):
        revs = page.get("revisions") or []
        if not revs:
            continue
        body = revs[0].get("slots", {}).get("main", {}).get("content", "")
        name = page["title"]
        out[name] = body
        seen = 0
        while name in alias and seen < 4:      # deshacer la cadena de redirecciones
            name = alias[name]
            out[name] = body
            seen += 1
    return out


def strip_refs(text: str) -> str:
    """
    Saca las `<ref>` del wikitext.

    La alternativa **autocerrada va primero**: `<ref[^>]*>` también acepta `<ref name="x"/>`, así
    que si se prueba antes la forma con cierre, el `.*?</ref>` sigue buscando y borra todo hasta la
    próxima referencia cerrada, a veces párrafos más adelante. Eso se comía la fecha europea de
    *Gran Turismo 4*, que va después de tres refs autocerradas.
    """
    return re.sub(r"<ref[^>]*/>|<ref[^>]*>.*?</ref>", "", text, flags=re.S)


def released_field(text: str) -> str:
    """
    Valor crudo del campo `released` de la ficha, **sin referencias**.

    Se quitan antes de recortar el campo, no después: una `<ref>{{cite web|…}}</ref>` aporta su
    propio `}}` y su propio `|título=`, y ambos cortaban el valor antes de tiempo. Por eso la ficha
    de *Final Fantasy X* daba la fecha norteamericana pero no la europea — la referencia estaba
    justo en el medio.
    """
    m = re.search(r"\|\s*released?\s*=(.*?)\n\s*\|\s*[\w\s]+=", strip_refs(text), re.S | re.I)
    return m.group(1) if m else ""


def release_templates(field: str) -> list[str]:
    """Cuerpos de las plantillas `vgrelease`/`Video game release`, con las llaves balanceadas."""
    out = []
    for m in re.finditer(r"\{\{\s*(?:vgrelease|video game release)\s*\|", field, re.I):
        depth, i = 1, m.end()
        while i < len(field) - 1 and depth:
            if field[i:i + 2] == "{{":
                depth, i = depth + 1, i + 2
            elif field[i:i + 2] == "}}":
                depth, i = depth - 1, i + 2
            else:
                i += 1
        out.append(field[m.end():i - 2])
    return out


def scope_to_platform(field: str, platform: str) -> str:
    """
    El trozo del campo que corresponde a [platform].

    Los multiplataforma rotulan cada bloque con el sistema en negrita
    (`'''PlayStation 2'''{{vgrelease|…}}'''Wii'''{{vgrelease|…}}`). Si un rótulo es el nuestro, se
    devuelve solo su bloque: sin eso, Ōkami de PS2 se llevaba la fecha de la versión de Wii.

    Cuando ningún rótulo coincide todavía puede haber fechas nuestras, porque el lanzamiento
    principal suele ir **sin rotular** y los rótulos marcan las variantes: la ficha de *Final
    Fantasy X* abre con `{{Video game release|JP|…|EU|…}}` y recién después etiqueta
    `'''''International'''''`. En ese caso vale lo que haya antes del primer rótulo. Si ahí tampoco
    hay nada, se devuelve vacío: todas las fechas del campo son de otras consolas.
    """
    # `'{3,5}`, no `'''''?`: esta última exige **cuatro** comillas —son cinco literales con la
    # última opcional— y no reconocía ni un solo `'''PlayStation 2'''`.
    marks = list(re.finditer(r"'{3,5}([^']{2,40}?)'{3,5}", field))
    if not marks:
        return field
    want = core(platform)
    for i, m in enumerate(marks):
        if core(m.group(1)) == want:
            end = marks[i + 1].start() if i + 1 < len(marks) else len(field)
            return field[m.end():end]
    head = field[:marks[0].start()]
    return head if re.search(r"\{\{\s*(?:vgrelease|video game release)", head, re.I) else ""


def parse_date(raw: str) -> str:
    """ISO de precisión variable desde los formatos que usa la plantilla."""
    s = re.sub(r"<ref[^>]*>.*?</ref>|<ref[^>]*/>", "", raw, flags=re.S)
    s = re.sub(r"\{\{[^{}]*\}\}|\[\[|\]\]|<[^>]+>", " ", s).strip()
    m = re.search(r"(\d{1,2})\s+([A-Za-z]+),?\s+(\d{4})", s)          # 2 November 2004
    if m and m.group(2).lower() in MONTHS:
        return f"{m.group(3)}-{MONTHS[m.group(2).lower()]:02d}-{int(m.group(1)):02d}"
    m = re.search(r"([A-Za-z]+)\s+(\d{1,2}),?\s+(\d{4})", s)          # November 2, 2004
    if m and m.group(1).lower() in MONTHS:
        return f"{m.group(3)}-{MONTHS[m.group(1).lower()]:02d}-{int(m.group(2)):02d}"
    m = re.search(r"(\d{4})-(\d{2})-(\d{2})", s)
    if m:
        return m.group(0)
    m = re.search(r"([A-Za-z]+)\s+(\d{4})", s)                        # November 2004
    if m and m.group(1).lower() in MONTHS:
        return f"{m.group(2)}-{MONTHS[m.group(1).lower()]:02d}"
    m = re.search(r"\b(19[7-9]\d|20[0-2]\d)\b", s)
    return m.group(1) if m else ""


def date_for(text: str, platform: str, region: str) -> str:
    """Fecha de [region] para [platform] según la ficha, o "" si el artículo no la trae."""
    field = scope_to_platform(released_field(text), platform)
    if not field:
        return ""
    found: dict[str, str] = {}
    for body in release_templates(field):
        # Los argumentos alternan código de región y fecha. Se parte solo por los `|` de este
        # nivel, para no romper una fecha que venga envuelta en otra plantilla.
        parts = [p.strip() for p in re.split(r"\|(?![^{}]*\}\})", body)]
        for i in range(0, len(parts) - 1, 2):
            code = re.sub(r"[^A-Za-z]", "", parts[i]).upper()
            if code and code not in found:
                found[code] = parts[i + 1]
    for code in REGION_CODES.get(region, []):
        if code in found:
            iso = parse_date(found[code])
            if iso:
                return iso
    if "WW" in found:                       # lanzamiento mundial: vale para cualquier región
        return parse_date(found["WW"])
    return ""


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("catalog")
    ap.add_argument("pages", nargs="+", help="las listas de las que salen los enlaces a artículos")
    ap.add_argument("--platform", required=True, help='rótulo de la consola en la ficha, p. ej. "PlayStation 2"')
    ap.add_argument("--cache", help="carpeta donde cachear el wikitext de las listas")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    catalog = json.load(open(args.catalog, encoding="utf-8"))
    region = catalog[0]["region"] if catalog else "NTSC-U"
    links = article_links(args.pages, args.cache)

    missing = [e for e in catalog if not e["releaseDate"].strip()]
    wanted, unlinked = {}, 0
    for entry in missing:
        art = links.get(core(entry["title"]))
        if art:
            wanted.setdefault(art, []).append(entry)
        else:
            unlinked += 1
    print(f"{len(missing)} sin fecha · {len(wanted)} con artículo · {unlinked} sin enlace en la lista")

    filled = day = 0
    titles = list(wanted)
    for i in range(0, len(titles), BATCH):
        chunk = titles[i:i + BATCH]
        for name, text in fetch(chunk).items():
            for entry in wanted.get(name, []):
                iso = date_for(text, args.platform, region)
                if not iso:
                    continue
                entry["releaseDate"] = iso
                entry["year"] = int(iso[:4])
                filled += 1
                if len(iso) == 10:
                    day += 1
        print(f"   {min(i + BATCH, len(titles))}/{len(titles)} · +{filled} fechas", flush=True)

    total = len(catalog) or 1
    have = sum(1 for e in catalog if e["releaseDate"].strip())
    print(f"{os.path.basename(args.catalog)}: +{filled} fechas ({day} al día) · "
          f"releaseDate {have}/{total} ({have * 100 // total}%)")

    if args.dry_run:
        print("   (dry-run: no se escribió)")
        return
    rows = sorted((canonical(e) for e in catalog), key=lambda x: x["slug"])
    body = ",\n".join(json.dumps(x, ensure_ascii=False) for x in rows)
    open(args.catalog, "w", encoding="utf-8").write("[\n" + body + "\n]\n")


if __name__ == "__main__":
    main()
