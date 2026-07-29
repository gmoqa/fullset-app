#!/usr/bin/env python3
"""
Parser de una lista de Sega Retro guardada como HTML → JSON de **fuente de registro** (facts:
título, fecha de lanzamiento con precisión de mes, catalog number = serial, rating).

Sega Retro (segaretro.org) es la fuente más certera para las consolas Sega: fechas precisas,
catalog numbers y ratings, por región. Bloquea bots, así que se trabaja desde el HTML descargado.

Uso:  python3 tools/segaretro_source.py <archivo.html> <salida.json> [--region NTSC-U] [--url URL]

La salida NO es un catálogo: es el registro de lo que dice la fuente, para aplicarlo después al
catálogo con procedencia (ver docs/CATALOGS.md). Guardar facts, no la página con copyright.
"""
import re, sys, html, json, argparse

# Fecha ISO de precisión variable: YYYY, YYYY-MM, YYYY-MM-DD (descarta cosas raras tipo "201x").
DATE_RE = re.compile(r"^(19|20)\d\d(-\d\d(-\d\d)?)?$")


def strip_footnote(s: str) -> str:
    return re.sub(r"\s*\[.*", "", s).strip()


def normalize_rating(s: str) -> str:
    """'Videogame Rating Council: MA-13' -> 'VRC: MA-13'; 'ESRB: Kids to Adults' -> igual."""
    s = strip_footnote(s)
    if not s:
        return ""
    s = s.replace("Videogame Rating Council", "VRC")
    return re.sub(r"\s+", " ", s).strip()


def cell_text(cell_html: str) -> str:
    return html.unescape(re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", cell_html))).strip()


def cell_rating(cell_html: str) -> str:
    """El rating es un icono: su valor está en alt/title (p. ej. 'ESRB: Kids to Adults')."""
    m = re.search(r'(?:alt|title)="([^"]+)"', cell_html)
    return normalize_rating(m.group(1) if m else cell_text(cell_html))


def parse(html_text: str):
    table = max(re.findall(r"<table.*?</table>", html_text, re.S), key=len)
    games = []
    for tr in re.findall(r"<tr\b.*?</tr>", table, re.S):
        cells = re.findall(r"<t[hd]\b[^>]*>(.*?)</t[hd]>", tr, re.S)
        if len(cells) < 4:
            continue
        title = cell_text(cells[0])
        if not title or title == "Title":
            continue
        date = strip_footnote(cell_text(cells[1]))
        games.append({
            "title": title,
            "releaseDate": date if DATE_RE.match(date) else "",
            "serial": strip_footnote(cell_text(cells[3])),
            "rating": cell_rating(cells[4]) if len(cells) > 4 else "",
        })
    return games


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("html")
    ap.add_argument("out")
    ap.add_argument("--region", default="NTSC-U")
    ap.add_argument("--url", default="")
    a = ap.parse_args()

    text = open(a.html, encoding="utf-8", errors="ignore").read()
    games = parse(text)
    games.sort(key=lambda g: g["title"].lower())

    out = {
        "source": "Sega Retro",
        "url": a.url,
        "region": a.region,
        "games": games,
    }
    with open(a.out, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
        f.write("\n")

    withdate = sum(1 for g in games if g["releaseDate"])
    month = sum(1 for g in games if len(g["releaseDate"]) == 7)
    serial = sum(1 for g in games if g["serial"])
    rating = sum(1 for g in games if g["rating"])
    print(f"{len(games)} juegos -> {a.out}")
    print(f"  con fecha {withdate} · de esas a MES {month} · serial {serial} · rating {rating}")


if __name__ == "__main__":
    main()
